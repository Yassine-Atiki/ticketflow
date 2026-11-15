package com.ticketflow.mcptoolserver.clients;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ticketflow.mcptoolserver.config.DownstreamServicesProperties;
import com.ticketflow.mcptoolserver.config.DownstreamServicesProperties.Operation;
import com.ticketflow.mcptoolserver.config.SecurityProperties;
import com.ticketflow.mcptoolserver.exceptions.ToolFailureException;
import com.ticketflow.mcptoolserver.security.CallerContext;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DownstreamHttpTest {

    private WireMockServer wireMock;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    @Test
    void doesNotCallAServiceWhoseContractIsNotConfirmed() {
        DownstreamHttp http = client(false, Duration.ofSeconds(2));

        assertThatThrownBy(() -> http.invoke("event", "search", Map.of(), Map.of("q", "jazz"), CallerContext.anonymous(), null))
                .isInstanceOf(ToolFailureException.class)
                .extracting(ex -> ((ToolFailureException) ex).failure().code())
                .isEqualTo("CONTRACT_NOT_CONFIRMED");
        wireMock.verify(0, getRequestedFor(urlEqualTo("/api/v1/events?q=jazz")));
    }

    @Test
    void doesNotCallUnconfirmedPreferencesOrTicketDetail() {
        DownstreamServicesProperties properties = new DownstreamServicesProperties();
        properties.getCustomer().setBaseUrl(wireMock.baseUrl());
        properties.getCustomer().getOperations().put(
                "preferencesGet",
                operation("GET", "/api/customers/{id}/preferences", false));
        properties.getTicket().setBaseUrl(wireMock.baseUrl());
        properties.getTicket().getOperations().put(
                "detail",
                operation("GET", "/api/v1/me/tickets/{ticketId}", false));
        DownstreamHttp http = new DownstreamHttp(properties, new SecurityProperties());

        assertThatThrownBy(() -> http.invoke(
                "customer", "preferencesGet", Map.of("id", "42"), Map.of(), CallerContext.anonymous(), null))
                .isInstanceOf(ToolFailureException.class)
                .extracting(ex -> ((ToolFailureException) ex).failure().code())
                .isEqualTo("CONTRACT_NOT_CONFIRMED");
        assertThatThrownBy(() -> http.invoke(
                "ticket",
                "detail",
                Map.of("ticketId", "7d09ea60-5ba3-4a39-a13b-d5b15055f39c"),
                Map.of(),
                new CallerContext("Bearer test-token"),
                null))
                .isInstanceOf(ToolFailureException.class)
                .extracting(ex -> ((ToolFailureException) ex).failure().code())
                .isEqualTo("CONTRACT_NOT_CONFIRMED");
        wireMock.verify(0, getRequestedFor(urlEqualTo("/api/customers/42/preferences")));
        wireMock.verify(0, getRequestedFor(urlEqualTo("/api/v1/me/tickets/7d09ea60-5ba3-4a39-a13b-d5b15055f39c")));
    }

    @Test
    void redactsTicketControlCodeFromASuccessfulBody() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/me/tickets/7d09ea60-5ba3-4a39-a13b-d5b15055f39c"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"ticketId":"7d09ea60-5ba3-4a39-a13b-d5b15055f39c","status":"ISSUED","controlCode":"tkt_secret"}
                                """)));
        DownstreamServicesProperties properties = new DownstreamServicesProperties();
        properties.getTicket().setBaseUrl(wireMock.baseUrl());
        properties.getTicket().setConnectTimeout(Duration.ofMillis(500));
        properties.getTicket().setResponseTimeout(Duration.ofSeconds(2));
        properties.getTicket().getOperations().put(
                "detail",
                operation("GET", "/api/v1/me/tickets/{ticketId}", true));
        DownstreamHttp http = new DownstreamHttp(properties, new SecurityProperties());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) http.invoke(
                "ticket",
                "detail",
                Map.of("ticketId", "7d09ea60-5ba3-4a39-a13b-d5b15055f39c"),
                Map.of(),
                new CallerContext("Bearer test-token"),
                null);

        assertThat(body).containsEntry("status", "ISSUED");
        assertThat(body).containsEntry("controlCode", "[redacted]");
    }

    @Test
    void returnsTheDownstreamPayloadAndForwardsAuthorization() {
        wireMock.stubFor(get(urlEqualTo("/api/customers/42"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":42,"firstName":"Sara","lastName":"Idrissi","email":"sara@example.com","phone":"+212600000000"}
                                """)));
        DownstreamHttp http = client(true, Duration.ofSeconds(2));

        Object body = http.invoke(
                "customer",
                "profile",
                Map.of("customerId", "42"),
                Map.of(),
                new CallerContext("Bearer test-token"),
                null);

        assertThat(body).isEqualTo(Map.of(
                "id", 42,
                "firstName", "Sara",
                "lastName", "Idrissi",
                "email", "sara@example.com",
                "phone", "+212600000000"));
    }

    @Test
    void reportsAuthenticationFailureWithoutTheResponseBody() {
        wireMock.stubFor(get(urlEqualTo("/api/customers/42"))
                .willReturn(aResponse().withStatus(401).withBody("{\"token\":\"super-secret\"}")));
        DownstreamHttp http = client(true, Duration.ofSeconds(2));

        assertThatThrownBy(() -> http.invoke(
                "customer", "profile", Map.of("customerId", "42"), Map.of(), CallerContext.anonymous(), null))
                .isInstanceOf(ToolFailureException.class)
                .satisfies(ex -> {
                    var failure = ((ToolFailureException) ex).failure();
                    assertThat(failure.code()).isEqualTo("AUTHENTICATION_FAILED");
                    assertThat(failure.httpStatus()).isEqualTo(401);
                    assertThat(failure.message()).doesNotContain("super-secret");
                });
    }

    @Test
    void reportsHttpErrorsFromProblemDetails() {
        wireMock.stubFor(post(urlEqualTo("/api/payments"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Conflict\",\"detail\":\"Booking is not payable\"}")));
        DownstreamHttp http = client(true, Duration.ofSeconds(2));

        assertThatThrownBy(() -> http.invoke(
                "payment",
                "initiate",
                Map.of(),
                Map.of("bookingId", "book-1"),
                CallerContext.anonymous(),
                "idem-9"))
                .isInstanceOf(ToolFailureException.class)
                .satisfies(ex -> {
                    var failure = ((ToolFailureException) ex).failure();
                    assertThat(failure.code()).isEqualTo("DOWNSTREAM_HTTP_ERROR");
                    assertThat(failure.httpStatus()).isEqualTo(409);
                    assertThat(failure.message()).contains("Booking is not payable");
                });
        wireMock.verify(postRequestedFor(urlEqualTo("/api/payments"))
                .withHeader("Idempotency-Key", equalTo("idem-9"))
                .withRequestBody(equalToJson("{\"bookingId\":\"book-1\"}")));
    }

    @Test
    void reportsBookingStyleErrorCodeAndMessage() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/bookings"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"INSUFFICIENT_CAPACITY\",\"message\":\"Not enough seats\"}")));
        DownstreamServicesProperties properties = new DownstreamServicesProperties();
        Operation create = operation("POST", "/api/v1/bookings", true);
        create.setBody(Map.of("eventId", "eventId"));
        properties.getBooking().setBaseUrl(wireMock.baseUrl());
        properties.getBooking().setConnectTimeout(Duration.ofMillis(500));
        properties.getBooking().setResponseTimeout(Duration.ofSeconds(2));
        properties.getBooking().setIdempotencyHeader("Idempotency-Key");
        properties.getBooking().getOperations().put("create", create);
        DownstreamHttp http = new DownstreamHttp(properties, new SecurityProperties());

        assertThatThrownBy(() -> http.invoke(
                "booking",
                "create",
                Map.of(),
                Map.of("eventId", "evt-1"),
                CallerContext.anonymous(),
                null))
                .isInstanceOf(ToolFailureException.class)
                .satisfies(ex -> assertThat(((ToolFailureException) ex).failure().message())
                        .contains("INSUFFICIENT_CAPACITY")
                        .contains("Not enough seats"));
    }

    @Test
    void reportsATimeoutWithoutTurningItIntoSuccess() {
        wireMock.stubFor(get(urlEqualTo("/api/payments/pay-1"))
                .willReturn(aResponse()
                        .withFixedDelay(2_000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"CAPTURED\"}")));
        DownstreamHttp http = client(true, Duration.ofMillis(300));

        assertThatThrownBy(() -> http.invoke(
                "payment", "status", Map.of("paymentId", "pay-1"), Map.of(), CallerContext.anonymous(), null))
                .isInstanceOf(ToolFailureException.class)
                .extracting(ex -> ((ToolFailureException) ex).failure().code())
                .isEqualTo("DOWNSTREAM_TIMEOUT");
    }

    @Test
    void redactsCredentialFieldsFromASuccessfulPayload() {
        wireMock.stubFor(get(urlEqualTo("/api/payments/pay-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"paymentId\":\"pay-1\",\"status\":\"PENDING\",\"cardNumber\":\"4242\"}")));
        DownstreamHttp http = client(true, Duration.ofSeconds(2));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) http.invoke(
                "payment", "status", Map.of("paymentId", "pay-1"), Map.of(), CallerContext.anonymous(), null);

        assertThat(body).containsEntry("status", "PENDING");
        assertThat(body).containsEntry("cardNumber", "[redacted]");
    }

    private DownstreamHttp client(boolean contractConfirmed, Duration responseTimeout) {
        DownstreamServicesProperties properties = new DownstreamServicesProperties();
        configure(properties.getCustomer(), "/api/customers/{customerId}", "GET", contractConfirmed, responseTimeout, Map.of(), Map.of());
        configure(properties.getPayment(), "/api/payments/{paymentId}", "GET", contractConfirmed, responseTimeout, Map.of(), Map.of());
        Operation initiate = operation("POST", "/api/payments", contractConfirmed);
        initiate.setBody(Map.of("bookingId", "bookingId"));
        properties.getPayment().getOperations().put("initiate", initiate);
        properties.getPayment().setBaseUrl(wireMock.baseUrl());
        configure(properties.getEvent(), "/api/events", "GET", contractConfirmed, responseTimeout, Map.of("keyword", "q"), Map.of());
        SecurityProperties security = new SecurityProperties();
        security.setPropagateAuthorization(true);
        return new DownstreamHttp(properties, security);
    }

    private void configure(
            DownstreamServicesProperties.Service service,
            String path,
            String method,
            boolean contractConfirmed,
            Duration responseTimeout,
            Map<String, String> query,
            Map<String, String> body) {
        service.setBaseUrl(wireMock.baseUrl());
        service.setConnectTimeout(Duration.ofMillis(500));
        service.setResponseTimeout(responseTimeout);
        service.setIdempotencyHeader("Idempotency-Key");
        String operationName = path.contains("customers") ? "profile" : path.contains("payments/{") ? "status" : "search";
        if ("/api/payments".equals(path)) {
            operationName = "initiate";
        }
        service.getOperations().put(operationName, operation(method, path, contractConfirmed, query, body));
    }

    private static Operation operation(String method, String path, boolean contractConfirmed) {
        return operation(method, path, contractConfirmed, Map.of(), Map.of());
    }

    private static Operation operation(
            String method,
            String path,
            boolean contractConfirmed,
            Map<String, String> query,
            Map<String, String> body) {
        Operation operation = new Operation();
        operation.setMethod(method);
        operation.setPath(path);
        operation.setContractConfirmed(contractConfirmed);
        operation.setQuery(query);
        operation.setBody(body);
        return operation;
    }
}
