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

        assertThat(mine.getMethod()).isEqualTo("GET");
        assertThat(mine.getPath()).isEqualTo("/api/v1/me/tickets");
        assertThat(mine.getQuery()).containsOnlyKeys("page", "size", "status");
        assertThat(mine.isContractConfirmed()).isFalse();
        assertThat(services.getTicket().getOperations()).doesNotContainKey("availability");
    }

    @Test
    void bookingBodyMatchesThePublishedContract() {
        Operation create = services.getBooking().getOperations().get("create");
        Operation cancel = services.getBooking().getOperations().get("cancel");

        assertThat(create.getMethod()).isEqualTo("POST");
        assertThat(create.getPath()).isEqualTo("/api/v1/bookings");
        assertThat(create.getBody()).containsOnlyKeys("eventId", "lines");
        assertThat(create.isContractConfirmed()).isFalse();
        assertThat(services.getBooking().getOperations().get("status").getPath()).isEqualTo("/api/v1/bookings/{id}");
        assertThat(cancel.getPath()).isEqualTo("/api/v1/bookings/{id}/cancel");
        assertThat(cancel.getBody()).containsOnlyKeys("reason");
    }

    @Test
    void paymentInitiationMatchesThePublishedContract() {
        Operation initiate = services.getPayment().getOperations().get("initiate");

        assertThat(initiate.getMethod()).isEqualTo("POST");
        assertThat(initiate.getPath()).isEqualTo("/api/payments");
        assertThat(initiate.getBody()).containsOnlyKeys("bookingId", "provider");
        assertThat(initiate.isContractConfirmed()).isFalse();
        assertThat(services.getPayment().getOperations().get("status").getPath()).isEqualTo("/api/payments/{id}");
        assertThat(services.getPayment().getIdempotencyHeader()).isEqualTo("Idempotency-Key");
    }

    @Test
    void customerProfileRemainsTheOnlyEnabledCall() {
        Operation profile = services.getCustomer().getOperations().get("profile");

        assertThat(profile.getMethod()).isEqualTo("GET");
        assertThat(profile.getPath()).isEqualTo("/api/customers/{customerId}");
        assertThat(profile.isContractConfirmed()).isTrue();
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
