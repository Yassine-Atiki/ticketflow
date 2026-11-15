package com.ticketflow.mcptoolserver.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketflow.mcptoolserver.exceptions.ToolFailureException;
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

        assertThatThrownBy(() -> arguments.requiredQuantity("quantity"))
                .isInstanceOf(ToolFailureException.class)
                .hasMessageContaining("between 1 and 50");
    }
}
