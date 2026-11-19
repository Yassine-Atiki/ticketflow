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

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "spring.config.location=classpath:/application-test.yaml")
@Timeout(30)
class McpServerStartupTest {

    static {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
    }

    private static final List<String> TOOL_NAMES = List.of(
            "search_events",
            "get_event_details",
            "get_my_tickets",
            "get_my_ticket",
            "create_booking",
            "list_my_bookings",
            "get_booking_status",
            "cancel_booking",
            "initiate_payment",
            "get_payment_status",
            "get_payments_by_booking",
            "get_customer_profile",
            "get_customer_preferences",
            "update_customer_preferences");

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
            var result = client.callTool(McpTestClient.call("search_events", Map.of("q", "jazz")));

            assertThat(result.isError()).isTrue();
            assertThat(McpTestClient.body(result))
                    .containsEntry("code", "CONTRACT_NOT_CONFIRMED")
                    .containsEntry("status", "ERROR");
        }
    }

    @Test
    void newlyMappedToolsStayDisabledUntilContractsAreConfirmed() {
        try (McpSyncClient client = McpTestClient.connect(port, "Bearer test-token")) {
            assertThat(McpTestClient.body(client.callTool(McpTestClient.call(
                    "get_customer_profile", Map.of("customerId", "42")))))
                    .containsEntry("code", "CONTRACT_NOT_CONFIRMED");
            assertThat(McpTestClient.body(client.callTool(McpTestClient.call(
                    "get_customer_preferences", Map.of("customerId", "42")))))
                    .containsEntry("code", "CONTRACT_NOT_CONFIRMED");
            assertThat(McpTestClient.body(client.callTool(McpTestClient.call(
                    "update_customer_preferences",
                    Map.of(
                            "customerId", "42",
                            "language", "fr",
                            "notificationChannel", "EMAIL",
                            "userConfirmed", true)))))
                    .containsEntry("code", "CONTRACT_NOT_CONFIRMED");
            assertThat(McpTestClient.body(client.callTool(McpTestClient.call(
                    "list_my_bookings", Map.of()))))
                    .containsEntry("code", "CONTRACT_NOT_CONFIRMED");
            assertThat(McpTestClient.body(client.callTool(McpTestClient.call(
                    "get_my_ticket",
                    Map.of("ticketId", "7d09ea60-5ba3-4a39-a13b-d5b15055f39c")))))
                    .containsEntry("code", "CONTRACT_NOT_CONFIRMED");
            assertThat(McpTestClient.body(client.callTool(McpTestClient.call(
                    "get_payments_by_booking",
                    Map.of("bookingId", "b3e1f9a0-1c2d-4e5f-8a9b-0c1d2e3f4a5b")))))
                    .containsEntry("code", "CONTRACT_NOT_CONFIRMED");
        }
    }

    @Test
    void createBookingRequiresExplicitConfirmation() {
        try (McpSyncClient client = McpTestClient.connect(port, null)) {
            var result = client.callTool(McpTestClient.call("create_booking", Map.of(
                    "eventId", "e4f5a6b7-c8d9-4e0f-a1b2-c3d4e5f6a7b8",
                    "lines", List.of(Map.of("ticketTierId", "f1e2d3c4-b5a6-4978-8695-a4b3c2d1e0f9", "quantity", 1)),
                    "idempotencyKey", "idem-1",
                    "userConfirmed", false)));

            assertThat(result.isError()).isTrue();
            assertThat(McpTestClient.body(result)).containsEntry("code", "CONFIRMATION_REQUIRED");
        }
    }

    @Test
    void rejectsAnInvalidEventDateBeforeADownstreamCall() {
        var result = McpTestClient.toolNamed(tools, "search_events")
                .call(null, McpTestClient.call("search_events", Map.of("dateFrom", "tomorrow")));

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

        HttpResponse<String> contract = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/openapi.yaml")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(contract.statusCode()).isEqualTo(200);
        assertThat(contract.body()).contains("/api/tools").contains("/mcp");

        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> swaggerConfig = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs/swagger-config")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        URI swaggerEntry = URI.create("http://127.0.0.1:" + port + "/swagger-ui.html");
        HttpResponse<String> swaggerUi = http.send(
                HttpRequest.newBuilder(swaggerEntry).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(swaggerConfig.statusCode()).isEqualTo(200);
        assertThat(swaggerConfig.body()).contains("\"url\":\"/openapi.yaml\"");
        assertThat(swaggerUi.statusCode()).isEqualTo(302);
        URI swaggerPage = swaggerEntry.resolve(swaggerUi.headers().firstValue("Location").orElseThrow());
        HttpResponse<String> swaggerHtml = http.send(
                HttpRequest.newBuilder(swaggerPage).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(swaggerHtml.statusCode()).isEqualTo(200);
        assertThat(swaggerHtml.body()).contains("swagger-ui");
        assertThat(swaggerHtml.uri().getPath()).contains("swagger-ui");
    }

    @Test
    void composeServiceHostIsAcceptedAndAnUnknownHostIsRejected() throws Exception {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
        String initialize = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"host-check","version":"0.0.1"}}}
                """;
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> allowed = client.send(mcpRequest("mcp-tool-server:" + port, initialize), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> rejected = client.send(mcpRequest("evil.example", initialize), HttpResponse.BodyHandlers.ofString());

        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(rejected.statusCode()).isEqualTo(421);
    }

    private HttpRequest mcpRequest(String host, String body) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Host", host)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    @Test
    void paymentToolSchemaRejectsCredentials() {
        var schema = McpTestClient.toolNamed(tools, "initiate_payment").definition().inputSchema();

        assertThat(schema).containsEntry("additionalProperties", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).doesNotContainKeys("cardNumber", "cvv", "amount", "pan", "customerId");
        assertThat(schema.get("required")).isEqualTo(List.of("bookingId", "userConfirmed", "idempotencyKey"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bookingId = (Map<String, Object>) properties.get("bookingId");
        @SuppressWarnings("unchecked")
        Map<String, Object> idempotencyKey = (Map<String, Object>) properties.get("idempotencyKey");
        assertThat(bookingId).containsEntry("pattern", com.ticketflow.mcptoolserver.tools.ToolDefinitions.UUID_PATTERN);
        assertThat(idempotencyKey).containsEntry("maxLength", 255);
    }

    @Test
    void bookingToolDoesNotAcceptACustomerOwner() {
        var schema = McpTestClient.toolNamed(tools, "create_booking").definition().inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKeys("eventId", "lines", "idempotencyKey");
        assertThat(properties).doesNotContainKey("customerId");
        @SuppressWarnings("unchecked")
        Map<String, Object> eventId = (Map<String, Object>) properties.get("eventId");
        assertThat(eventId).containsEntry("pattern", com.ticketflow.mcptoolserver.tools.ToolDefinitions.UUID_PATTERN);
    }
}
