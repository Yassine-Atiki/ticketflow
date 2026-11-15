package com.ticketflow.mcptoolserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.mcptoolserver.tools.McpTool;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpSyncServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Timeout(30)
class McpServerStartupTest {

    private static final List<String> TOOL_NAMES = List.of(
            "search_events",
            "get_event_details",
            "get_upcoming_events",
            "check_ticket_availability",
            "get_my_tickets",
            "create_booking",
            "get_booking_status",
            "cancel_booking",
            "initiate_payment",
            "get_payment_status",
            "get_customer_profile",
            "get_notification_history",
            "resend_booking_confirmation");

    @LocalServerPort
    private int port;

    @Autowired
    private McpSyncServer server;

    @Autowired
    private List<McpTool> tools;

    @Test
    void registersEveryToolAndPublishesThemOverStreamableHttp() {
        assertThat(server.listTools()).extracting(tool -> tool.name()).containsExactlyElementsOf(TOOL_NAMES);

        try (McpSyncClient client = McpTestClient.connect(port, null)) {
            assertThat(client.listTools().tools()).extracting(tool -> tool.name()).containsExactlyElementsOf(TOOL_NAMES);
            assertThat(client.getServerInfo().name()).isEqualTo("ticketflow-mcp");
        }
    }

    @Test
    void unconfirmedEventSearchDoesNotPretendToSucceed() {
        try (McpSyncClient client = McpTestClient.connect(port, null)) {
            var result = client.callTool(McpTestClient.call("search_events", Map.of("keyword", "jazz")));

            assertThat(result.isError()).isTrue();
            assertThat(McpTestClient.body(result))
                    .containsEntry("code", "CONTRACT_NOT_CONFIRMED")
                    .containsEntry("status", "ERROR");
        }
    }

    @Test
    void createBookingRequiresExplicitConfirmation() {
        try (McpSyncClient client = McpTestClient.connect(port, null)) {
            var result = client.callTool(McpTestClient.call("create_booking", Map.of(
                    "eventId", "evt-1",
                    "customerId", "42",
                    "quantity", 1,
                    "userConfirmed", false)));

            assertThat(result.isError()).isTrue();
            assertThat(McpTestClient.body(result)).containsEntry("code", "CONFIRMATION_REQUIRED");
        }
    }

    @Test
    void rejectsAnInvalidEventDateBeforeADownstreamCall() {
        var result = McpTestClient.toolNamed(tools, "search_events")
                .call(null, McpTestClient.call("search_events", Map.of("date", "tomorrow")));

        assertThat(result.isError()).isTrue();
        assertThat(McpTestClient.body(result)).containsEntry("code", "INVALID_ARGUMENTS");
    }

    @Test
    void publishesSwaggerForTheRegisteredTools() throws Exception {
        HttpResponse<String> docs = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> catalog = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/tools")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("TicketFlow MCP Tool Server").contains("/api/tools");
        assertThat(catalog.statusCode()).isEqualTo(200);
        assertThat(catalog.body()).contains(TOOL_NAMES.toArray(String[]::new));
    }

    @Test
    void paymentToolSchemaRejectsCredentials() {
        var schema = McpTestClient.toolNamed(tools, "initiate_payment").definition().inputSchema();

        assertThat(schema).containsEntry("additionalProperties", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).doesNotContainKeys("cardNumber", "cvv", "amount", "pan");
    }
}
