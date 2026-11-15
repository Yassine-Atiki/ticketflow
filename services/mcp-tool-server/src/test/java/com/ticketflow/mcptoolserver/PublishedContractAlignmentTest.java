package com.ticketflow.mcptoolserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.mcptoolserver.config.DownstreamServicesProperties;
import com.ticketflow.mcptoolserver.config.DownstreamServicesProperties.Operation;
import com.ticketflow.mcptoolserver.tools.McpTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class PublishedContractAlignmentTest {

    @Autowired
    private DownstreamServicesProperties services;

    @Autowired
    private List<McpTool> tools;

    @Test
    void eventPathsMatchThePublishedContractAndStayDisabled() {
        Operation search = services.getEvent().getOperations().get("search");
        Operation details = services.getEvent().getOperations().get("details");

        assertThat(search.getMethod()).isEqualTo("GET");
        assertThat(search.getPath()).isEqualTo("/api/v1/events");
        assertThat(search.getQuery()).containsOnlyKeys(
                "q", "categoryId", "city", "dateFrom", "dateTo", "page", "size", "sort");
        assertThat(search.isContractConfirmed()).isFalse();
        assertThat(properties("search_events")).containsKey("sort");
        assertThat(details.getPath()).isEqualTo("/api/v1/events/{id}");
        assertThat(details.isContractConfirmed()).isFalse();
        assertThat(services.getEvent().getOperations()).doesNotContainKey("upcoming");
    }

    @Test
    void ticketListUsesTheAuthenticatedMePath() {
        Operation mine = services.getTicket().getOperations().get("mine");
        Operation detail = services.getTicket().getOperations().get("detail");

        assertThat(mine.getMethod()).isEqualTo("GET");
        assertThat(mine.getPath()).isEqualTo("/api/v1/me/tickets");
        assertThat(mine.getQuery()).containsOnlyKeys("page", "size", "status");
        assertThat(mine.isContractConfirmed()).isFalse();
        assertThat(detail.getMethod()).isEqualTo("GET");
        assertThat(detail.getPath()).isEqualTo("/api/v1/me/tickets/{ticketId}");
        assertThat(detail.isContractConfirmed()).isFalse();
        assertThat(services.getTicket().getOperations()).doesNotContainKey("availability");
        assertThat(properties("get_my_ticket")).containsKey("ticketId");
    }

    @Test
    void bookingBodyMatchesThePublishedContract() {
        Operation create = services.getBooking().getOperations().get("create");
        Operation cancel = services.getBooking().getOperations().get("cancel");
        Operation mine = services.getBooking().getOperations().get("mine");

        assertThat(create.getMethod()).isEqualTo("POST");
        assertThat(create.getPath()).isEqualTo("/api/v1/bookings");
        assertThat(create.getBody()).containsOnlyKeys("eventId", "lines");
        assertThat(create.isContractConfirmed()).isFalse();
        assertThat(services.getBooking().getOperations().get("status").getPath()).isEqualTo("/api/v1/bookings/{id}");
        assertThat(cancel.getPath()).isEqualTo("/api/v1/bookings/{id}/cancel");
        assertThat(cancel.getBody()).containsOnlyKeys("reason");
        assertThat(mine.getMethod()).isEqualTo("GET");
        assertThat(mine.getPath()).isEqualTo("/api/v1/me/bookings");
        assertThat(mine.getQuery()).containsOnlyKeys("status", "page", "size");
        assertThat(mine.isContractConfirmed()).isFalse();
        assertThat(properties("list_my_bookings")).containsKeys("status", "page", "size");
    }

    @Test
    void paymentInitiationMatchesThePublishedContract() {
        Operation initiate = services.getPayment().getOperations().get("initiate");
        Operation byBooking = services.getPayment().getOperations().get("byBooking");

        assertThat(initiate.getMethod()).isEqualTo("POST");
        assertThat(initiate.getPath()).isEqualTo("/api/payments");
        assertThat(initiate.getBody()).containsOnlyKeys("bookingId", "provider");
        assertThat(initiate.isContractConfirmed()).isFalse();
        assertThat(services.getPayment().getOperations().get("status").getPath()).isEqualTo("/api/payments/{id}");
        assertThat(services.getPayment().getIdempotencyHeader()).isEqualTo("Idempotency-Key");
        assertThat(byBooking.getMethod()).isEqualTo("GET");
        assertThat(byBooking.getPath()).isEqualTo("/api/payments/booking/{bookingId}");
        assertThat(byBooking.isContractConfirmed()).isFalse();
        assertThat(properties("get_payments_by_booking")).containsKey("bookingId");
    }

    @Test
    void customerProfileRemainsTheOnlyEnabledCall() {
        Operation profile = services.getCustomer().getOperations().get("profile");
        Operation preferencesGet = services.getCustomer().getOperations().get("preferencesGet");
        Operation preferencesPut = services.getCustomer().getOperations().get("preferencesPut");

        assertThat(profile.getMethod()).isEqualTo("GET");
        assertThat(profile.getPath()).isEqualTo("/api/customers/{customerId}");
        assertThat(profile.isContractConfirmed()).isTrue();
        assertThat(preferencesGet.getMethod()).isEqualTo("GET");
        assertThat(preferencesGet.getPath()).isEqualTo("/api/customers/{id}/preferences");
        assertThat(preferencesGet.isContractConfirmed()).isFalse();
        assertThat(preferencesPut.getMethod()).isEqualTo("PUT");
        assertThat(preferencesPut.getPath()).isEqualTo("/api/customers/{id}/preferences");
        assertThat(preferencesPut.getBody()).containsOnlyKeys(
                "language", "notificationChannel", "marketingOptIn");
        assertThat(preferencesPut.isContractConfirmed()).isFalse();
        assertThat(properties("update_customer_preferences")).containsKeys(
                "customerId", "language", "notificationChannel", "userConfirmed", "marketingOptIn");
        assertThat(tools).extracting(tool -> tool.definition().name())
                .doesNotContain(
                        "get_upcoming_events",
                        "check_ticket_availability",
                        "get_notification_history",
                        "resend_booking_confirmation");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> properties(String toolName) {
        return (Map<String, Object>) tools.stream()
                .filter(tool -> tool.definition().name().equals(toolName))
                .findFirst()
                .orElseThrow()
                .definition()
                .inputSchema()
                .get("properties");
    }
}
