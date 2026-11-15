package com.ticketflow.mcptoolserver.tools;

import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.CUSTOMER_ID_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.ID_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.booleanProperty;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.objectSchema;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.properties;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.stringProperty;

import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.services.NotificationIntegrationService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationToolConfiguration {

    private static final String CONFIRMATION =
            "Set userConfirmed to true only after the user explicitly asks to resend the confirmation.";

    @Bean
    McpTool getNotificationHistoryTool(NotificationIntegrationService notifications) {
        return tool(notifications::history, ToolDefinitions.define(
                "get_notification_history",
                "Retrieve notification history from Notification Service. "
                        + "The Notification Service HTTP contract is not confirmed, so this tool returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "customerId", stringProperty("Numeric customer id published by Customer Service.", CUSTOMER_ID_PATTERN),
                        "bookingId", stringProperty("Optional booking identifier.", ID_PATTERN)),
                        List.of("customerId")),
                true, false, true), 120);
    }

    @Bean
    McpTool resendBookingConfirmationTool(NotificationIntegrationService notifications) {
        return tool(notifications::resend, ToolDefinitions.define(
                "resend_booking_confirmation",
                "Ask Notification Service to resend a booking confirmation. "
                        + CONFIRMATION
                        + " The Notification Service HTTP contract is not confirmed, so a confirmed call returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "bookingId", stringProperty("Booking identifier.", ID_PATTERN),
                        "customerId", stringProperty("Numeric customer id published by Customer Service.", CUSTOMER_ID_PATTERN),
                        "userConfirmed", booleanProperty(CONFIRMATION),
                        "idempotencyKey", stringProperty("Optional caller-generated idempotency key.", ID_PATTERN)),
                        List.of("bookingId", "customerId", "userConfirmed")),
                false, false, true), 130);
    }

    private static McpTool tool(Handler handler, McpSchema.Tool definition, int order) {
        return new AbstractMcpTool(definition, order) {
            @Override
            protected McpSchema.CallToolResult execute(CallerContext caller, ToolArguments arguments) {
                return ToolResults.success("Notification Service", definition.name(), handler.handle(arguments, caller));
            }
        };
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(ToolArguments arguments, CallerContext caller);
    }
}
