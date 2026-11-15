package com.ticketflow.mcptoolserver.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketflow.mcptoolserver.exceptions.ToolFailureException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolArgumentsTest {

    @Test
    void rejectsAnImpossibleDate() {
        ToolArguments arguments = new ToolArguments(Map.of("date", "2026-02-31"));

        assertThatThrownBy(() -> arguments.optionalDate("date"))
                .isInstanceOf(ToolFailureException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    void acceptsACalendarDate() {
        ToolArguments arguments = new ToolArguments(Map.of("date", "2026-11-11"));

        assertThat(arguments.optionalDate("date")).isEqualTo("2026-11-11");
    }

    @Test
    void rejectsANonNumericCustomerId() {
        ToolArguments arguments = new ToolArguments(Map.of("customerId", "sara"));

        assertThatThrownBy(() -> arguments.requiredCustomerId("customerId"))
                .isInstanceOf(ToolFailureException.class)
                .hasMessageContaining("numeric");
    }

    @Test
    void rejectsPathShapedIdentifiers() {
        ToolArguments arguments = new ToolArguments(Map.of("eventId", "../admin"));

        assertThatThrownBy(() -> arguments.requiredId("eventId"))
                .isInstanceOf(ToolFailureException.class);
    }

    @Test
    void treatsFalseConfirmationAsNotConfirmed() {
        ToolArguments arguments = new ToolArguments(Map.of("userConfirmed", false));

        assertThatThrownBy(() -> arguments.requireConfirmation("create_booking"))
                .isInstanceOf(ToolFailureException.class)
                .hasMessageContaining("was not executed");
    }

    @Test
    void rejectsAQuantityOutsideTheGuardrail() {
        ToolArguments arguments = new ToolArguments(Map.of("quantity", 0));

        assertThatThrownBy(() -> arguments.requiredQuantity("quantity", 1, 10))
                .isInstanceOf(ToolFailureException.class)
                .hasMessageContaining("between 1 and 10");
    }

    @Test
    void bookingIdempotencyKeyStopsAt100Characters() {
        ToolArguments accepted = new ToolArguments(Map.of("idempotencyKey", "a".repeat(100)));
        ToolArguments rejected = new ToolArguments(Map.of("idempotencyKey", "a".repeat(101)));

        assertThat(accepted.requiredIdempotencyKey()).hasSize(100);
        assertThatThrownBy(rejected::requiredIdempotencyKey)
                .isInstanceOf(ToolFailureException.class)
                .hasMessageContaining("100");
    }

    @Test
    void paymentIdempotencyKeyAllows255Characters() {
        ToolArguments accepted = new ToolArguments(Map.of("idempotencyKey", "p".repeat(255)));
        ToolArguments rejected = new ToolArguments(Map.of("idempotencyKey", "p".repeat(256)));

        assertThat(accepted.requiredPaymentIdempotencyKey()).hasSize(255);
        assertThatThrownBy(rejected::requiredPaymentIdempotencyKey)
                .isInstanceOf(ToolFailureException.class)
                .hasMessageContaining("255");
    }

    @Test
    void contractUuidsAreAcceptedAndLooseIdentifiersAreRejected() {
        String eventId = "e4f5a6b7-c8d9-4e0f-a1b2-c3d4e5f6a7b8";
        ToolArguments valid = new ToolArguments(Map.of(
                "eventId", eventId,
                "lines", List.of(Map.of(
                        "ticketTierId", "f1e2d3c4-b5a6-4978-8695-a4b3c2d1e0f9",
                        "quantity", 1))));
        ToolArguments invalid = new ToolArguments(Map.of(
                "bookingId", "book-1",
                "lines", List.of(Map.of("ticketTierId", "tier-1", "quantity", 1))));

        assertThat(valid.requiredUuid("eventId")).isEqualTo(eventId);
        assertThat(valid.requiredBookingLines()).hasSize(1);
        assertThatThrownBy(() -> invalid.requiredUuid("bookingId"))
                .isInstanceOf(ToolFailureException.class)
                .hasMessageContaining("UUID");
        assertThatThrownBy(invalid::requiredBookingLines)
                .isInstanceOf(ToolFailureException.class)
                .hasMessageContaining("UUID");
    }
}
