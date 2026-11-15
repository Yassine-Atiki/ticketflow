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
                    "eventId", "e4f5a6b7-c8d9-4e0f-a1b2-c3d4e5f6a7b8",
                    "lines", java.util.List.of(Map.of("ticketTierId", "f1e2d3c4-b5a6-4978-8695-a4b3c2d1e0f9", "quantity", 2)),
                    "idempotencyKey", "idem-1",
                    "userConfirmed", false)));

            assertThat(result.isError()).isTrue();
            assertThat(McpTestClient.body(result)).containsEntry("code", "CONFIRMATION_REQUIRED");
        }
        WIREMOCK.verify(0, postRequestedFor(urlEqualTo("/api/v1/bookings")));
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
                    "bookingId", "b3e1f9a0-1c2d-4e5f-8a9b-0c1d2e3f4a5b",
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
                        "{\"bookingId\":\"b3e1f9a0-1c2d-4e5f-8a9b-0c1d2e3f4a5b\"}")));
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
        WIREMOCK.stubFor(get(urlEqualTo("/api/payments/7d09ea60-5ba3-4a39-a13b-d5b15055f39c"))
                .willReturn(aResponse()
                        .withFixedDelay(2_000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"CAPTURED\"}")));

        try (McpSyncClient client = McpTestClient.connect(port, null)) {
            var result = client.callTool(McpTestClient.call(
                    "get_payment_status", Map.of("paymentId", "7d09ea60-5ba3-4a39-a13b-d5b15055f39c")));

            assertThat(result.isError()).isTrue();
            assertThat(McpTestClient.body(result)).containsEntry("code", "DOWNSTREAM_TIMEOUT");
        }
    }

    @Test
    void disabledMappedToolsDoNotHitDownstream() {
        try (McpSyncClient client = McpTestClient.connect(port, "Bearer test-token")) {
            assertThat(McpTestClient.body(client.callTool(McpTestClient.call(
                    "get_customer_preferences", Map.of("customerId", "42")))))
                    .containsEntry("code", "CONTRACT_NOT_CONFIRMED");
            assertThat(McpTestClient.body(client.callTool(McpTestClient.call(
                    "list_my_bookings", Map.of()))))
                    .containsEntry("code", "CONTRACT_NOT_CONFIRMED");
            assertThat(McpTestClient.body(client.callTool(McpTestClient.call(
                    "get_payments_by_booking",
                    Map.of("bookingId", "b3e1f9a0-1c2d-4e5f-8a9b-0c1d2e3f4a5b")))))
                    .containsEntry("code", "CONTRACT_NOT_CONFIRMED");
        }
        WIREMOCK.verify(0, getRequestedFor(urlEqualTo("/api/customers/42/preferences")));
        WIREMOCK.verify(0, getRequestedFor(urlEqualTo("/api/v1/me/bookings")));
        WIREMOCK.verify(0, getRequestedFor(urlEqualTo(
                "/api/payments/booking/b3e1f9a0-1c2d-4e5f-8a9b-0c1d2e3f4a5b")));
    }
}
