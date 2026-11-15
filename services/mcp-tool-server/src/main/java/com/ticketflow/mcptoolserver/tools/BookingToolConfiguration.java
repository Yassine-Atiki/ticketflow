package com.ticketflow.mcptoolserver.tools;

import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.IDEMPOTENCY_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.UUID_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.arrayProperty;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.booleanProperty;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.integerProperty;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.objectSchema;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.properties;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.stringProperty;

import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.services.BookingIntegrationService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BookingToolConfiguration {

    private static final String CONFIRMATION =
            "Set userConfirmed to true only after the user explicitly agrees to this change in the conversation.";
    private static final String DISABLED =
            " Booking Service has published this path and has not implemented it, so a confirmed call stays disabled.";

    @Bean
    McpTool createBookingTool(BookingIntegrationService bookings) {
        return tool(bookings::create, ToolDefinitions.define(
                "create_booking",
                "Ask Booking Service to create a booking with POST /api/v1/bookings. "
                        + "The body is eventId plus lines of ticketTierId and quantity. "
                        + "The caller is the bearer token subject, not a customerId argument. "
                        + "Idempotency-Key is required by the booking contract. "
                        + CONFIRMATION
                        + DISABLED,
                objectSchema(properties(
                        "eventId", stringProperty("Event identifier. The booking contract requires a UUID.", UUID_PATTERN),
                        "lines", arrayProperty(
                                "Ticket tiers to reserve. Each line quantity is 1 to 10.",
                                objectSchema(properties(
                                        "ticketTierId", stringProperty("Ticket tier identifier. The booking contract requires a UUID.", UUID_PATTERN),
                                        "quantity", integerProperty("Quantity for this tier. The booking contract caps a line at 10.", 1, 10)),
                                        List.of("ticketTierId", "quantity"))),
                        "userConfirmed", booleanProperty(CONFIRMATION),
                        "idempotencyKey", stringProperty("Required Idempotency-Key value.", IDEMPOTENCY_PATTERN)),
                        List.of("eventId", "lines", "userConfirmed", "idempotencyKey")),
                false, false, false), 40);
    }

    @Bean
    McpTool getBookingStatusTool(BookingIntegrationService bookings) {
        return tool(bookings::status, ToolDefinitions.define(
                "get_booking_status",
                "Retrieve one booking with GET /api/v1/bookings/{id}. The booking must belong to the bearer token subject."
                        + DISABLED,
                objectSchema(properties(
                        "bookingId", stringProperty("Booking identifier. The booking contract requires a UUID.", UUID_PATTERN)),
                        List.of("bookingId")),
                true, false, true), 50);
    }

    @Bean
    McpTool cancelBookingTool(BookingIntegrationService bookings) {
        return tool(bookings::cancel, ToolDefinitions.define(
                "cancel_booking",
                "Ask Booking Service to cancel a booking with POST /api/v1/bookings/{id}/cancel. "
                        + "The body may contain a reason. The caller is the bearer token subject. "
                        + CONFIRMATION
                        + DISABLED,
                objectSchema(properties(
                        "bookingId", stringProperty("Booking identifier. The booking contract requires a UUID.", UUID_PATTERN),
                        "reason", stringProperty("Optional cancellation reason, at most 255 characters."),
                        "userConfirmed", booleanProperty(CONFIRMATION),
                        "idempotencyKey", stringProperty("Optional idempotency key. The cancel contract does not require one.", IDEMPOTENCY_PATTERN)),
                        List.of("bookingId", "userConfirmed")),
                false, true, false), 60);
    }

    private static McpTool tool(Handler handler, McpSchema.Tool definition, int order) {
        return new AbstractMcpTool(definition, order) {
            @Override
            protected McpSchema.CallToolResult execute(CallerContext caller, ToolArguments arguments) {
                return ToolResults.success("Booking Service", definition.name(), handler.handle(arguments, caller));
            }
        };
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(ToolArguments arguments, CallerContext caller);
    }
}
