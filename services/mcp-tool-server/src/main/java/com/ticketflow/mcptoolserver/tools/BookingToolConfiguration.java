package com.ticketflow.mcptoolserver.tools;

import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.CUSTOMER_ID_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.ID_PATTERN;
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

    @Bean
    McpTool createBookingTool(BookingIntegrationService bookings) {
        return tool(bookings::create, ToolDefinitions.define(
                "create_booking",
                "Ask Booking Service to create a booking for one or more tickets. "
                        + CONFIRMATION
                        + " Payment credentials are not accepted. An idempotencyKey is forwarded as a header when present; "
                        + "whether Booking Service deduplicates retries is not confirmed. "
                        + "The Booking Service HTTP contract is not confirmed, so a confirmed call returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "eventId", stringProperty("Event identifier.", ID_PATTERN),
                        "customerId", stringProperty("Numeric customer id published by Customer Service.", CUSTOMER_ID_PATTERN),
                        "quantity", integerProperty("Number of tickets requested. Booking Service enforces inventory.", 1, 50),
                        "ticketType", stringProperty("Optional ticket type. Ticket types are not confirmed yet.", ID_PATTERN),
                        "userConfirmed", booleanProperty(CONFIRMATION),
                        "idempotencyKey", stringProperty("Optional caller-generated idempotency key.", ID_PATTERN)),
                        List.of("eventId", "customerId", "quantity", "userConfirmed")),
                false, false, false), 60);
    }

    @Bean
    McpTool getBookingStatusTool(BookingIntegrationService bookings) {
        return tool(bookings::status, ToolDefinitions.define(
                "get_booking_status",
                "Retrieve the status of a booking from Booking Service. "
                        + "The Booking Service HTTP contract is not confirmed, so this tool returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "bookingId", stringProperty("Booking identifier.", ID_PATTERN)),
                        List.of("bookingId")),
                true, false, true), 70);
    }

    @Bean
    McpTool cancelBookingTool(BookingIntegrationService bookings) {
        return tool(bookings::cancel, ToolDefinitions.define(
                "cancel_booking",
                "Ask Booking Service to cancel a booking. "
                        + CONFIRMATION
                        + " The Booking Service HTTP contract is not confirmed, so a confirmed call returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "bookingId", stringProperty("Booking identifier.", ID_PATTERN),
                        "customerId", stringProperty("Numeric customer id published by Customer Service.", CUSTOMER_ID_PATTERN),
                        "reason", stringProperty("Optional cancellation reason."),
                        "userConfirmed", booleanProperty(CONFIRMATION),
                        "idempotencyKey", stringProperty("Optional caller-generated idempotency key.", ID_PATTERN)),
                        List.of("bookingId", "customerId", "userConfirmed")),
                false, true, false), 80);
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
