package com.ticketflow.mcptoolserver;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ticketflow.services.event.operations.search.contract-confirmed=true",
        "ticketflow.services.booking.operations.create.contract-confirmed=true",
        "ticketflow.services.payment.operations.initiate.contract-confirmed=true",
        "ticketflow.services.payment.operations.status.contract-confirmed=true",
        "ticketflow.services.payment.response-timeout=500ms",
        "ticketflow.services.customer.operations.profile.contract-confirmed=true"
})
@Timeout(40)
class McpDownstreamIntegrationTest {

    private static final WireMockServer WIREMOCK = new WireMockServer(wireMockConfig().dynamicPort());

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        if (!WIREMOCK.isRunning()) {
            WIREMOCK.start();
        }
        String baseUrl = WIREMOCK.baseUrl();
        registry.add("ticketflow.services.event.base-url", () -> baseUrl);
        registry.add("ticketflow.services.booking.base-url", () -> baseUrl);
        registry.add("ticketflow.services.payment.base-url", () -> baseUrl);
        registry.add("ticketflow.services.customer.base-url", () -> baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        if (WIREMOCK.isRunning()) {
            WIREMOCK.stop();
        }
    }

    @BeforeEach
    void reset() {
        WIREMOCK.resetAll();
    }

    @Test
    void getCustomerProfileReturnsThePublishedCustomerContractAndForwardsAuthorization() {
        WIREMOCK.stubFor(get(urlEqualTo("/api/customers/42"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":42,"firstName":"Sara","lastName":"Idrissi","email":"sara@example.com","phone":"+212600000000","createdAt":"2026-01-01T00:00:00Z"}
                                """)));

        try (McpSyncClient client = McpTestClient.connect(port, "Bearer test-token")) {
            var result = client.callTool(McpTestClient.call("get_customer_profile", Map.of("customerId", "42")));

            assertThat(result.isError()).isFalse();
            assertThat(McpTestClient.body(result)).containsEntry("status", "OK");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) McpTestClient.body(result).get("data");
            assertThat(data).containsEntry("firstName", "Sara").containsEntry("email", "sara@example.com");
        }
    }

    @Test
    void createBookingWithConfirmationFalseDoesNotReachBookingService() {
        try (McpSyncClient client = McpTestClient.connect(port, null)) {
            var result = client.callTool(McpTestClient.call("create_booking", Map.of(
                    "eventId", "evt-1",
                    "customerId", "42",
                    "quantity", 2,
                    "userConfirmed", false)));

            assertThat(result.isError()).isTrue();
            assertThat(McpTestClient.body(result)).containsEntry("code", "CONFIRMATION_REQUIRED");
        }
        WIREMOCK.verify(0, postRequestedFor(urlEqualTo("/api/bookings")));
    }

    @Test
    void paymentFailureStaysAFailure() {
        WIREMOCK.stubFor(post(urlEqualTo("/api/payments"))
                .willReturn(aResponse()
                        .withStatus(402)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Payment was refused\"}")));

        try (McpSyncClient client = McpTestClient.connect(port, null)) {
            var result = client.callTool(McpTestClient.call("initiate_payment", Map.of(
                    "bookingId", "book-1",
                    "userConfirmed", true,
                    "idempotencyKey", "pay-once")));

            assertThat(result.isError()).isTrue();
            Map<String, Object> body = McpTestClient.body(result);
            assertThat(body).containsEntry("code", "DOWNSTREAM_HTTP_ERROR").containsEntry("httpStatus", 402);
            assertThat(body.get("message").toString()).contains("Payment was refused");
            assertThat(body).containsEntry("status", "ERROR");
        }
        WIREMOCK.verify(postRequestedFor(urlEqualTo("/api/payments"))
                .withHeader("Idempotency-Key", equalTo("pay-once"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.equalToJson(
                        "{\"bookingId\":\"book-1\"}")));
    }

    @Test
    void customerAuthenticationFailureIsReportedAsSuch() {
        WIREMOCK.stubFor(get(urlEqualTo("/api/customers/42"))
                .willReturn(aResponse().withStatus(401).withBody("secret-token")));

        try (McpSyncClient client = McpTestClient.connect(port, "Bearer expired")) {
            var result = client.callTool(McpTestClient.call("get_customer_profile", Map.of("customerId", "42")));

            assertThat(result.isError()).isTrue();
            Map<String, Object> body = McpTestClient.body(result);
            assertThat(body).containsEntry("code", "AUTHENTICATION_FAILED");
            assertThat(body.get("message").toString()).doesNotContain("secret-token");
        }
        WIREMOCK.verify(getRequestedFor(urlEqualTo("/api/customers/42"))
                .withHeader("Authorization", equalTo("Bearer expired")));
    }

    @Test
    void paymentStatusTimeoutIsAnError() {
        WIREMOCK.stubFor(get(urlEqualTo("/api/payments/pay-1"))
                .willReturn(aResponse()
                        .withFixedDelay(2_000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"CAPTURED\"}")));

        try (McpSyncClient client = McpTestClient.connect(port, null)) {
            var result = client.callTool(McpTestClient.call("get_payment_status", Map.of("paymentId", "pay-1")));

            assertThat(result.isError()).isTrue();
            assertThat(McpTestClient.body(result)).containsEntry("code", "DOWNSTREAM_TIMEOUT");
        }
    }
}
