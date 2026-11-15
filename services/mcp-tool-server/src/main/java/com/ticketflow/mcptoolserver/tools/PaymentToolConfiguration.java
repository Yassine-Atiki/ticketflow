package com.ticketflow.mcptoolserver.tools;

import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.ID_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.booleanProperty;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.objectSchema;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.properties;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.stringProperty;

import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.services.PaymentIntegrationService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentToolConfiguration {

    private static final String CONFIRMATION =
            "Set userConfirmed to true only after the user explicitly agrees to pay for this booking.";

    @Bean
    McpTool initiatePaymentTool(PaymentIntegrationService payments) {
        return tool(payments::initiate, ToolDefinitions.define(
                "initiate_payment",
                "Ask Payment Service to initiate payment for an existing booking. "
                        + CONFIRMATION
                        + " Card numbers, CVV and other payment credentials are rejected. "
                        + "A successful tool result means Payment Service accepted the request, not that money was captured. "
                        + "The Payment Service HTTP contract is not confirmed, so a confirmed call returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "bookingId", stringProperty("Identifier of the booking to pay.", ID_PATTERN),
                        "userConfirmed", booleanProperty(CONFIRMATION),
                        "idempotencyKey", stringProperty("Optional caller-generated idempotency key.", ID_PATTERN)),
                        List.of("bookingId", "userConfirmed")),
                false, false, false), 90);
    }

    @Bean
    McpTool getPaymentStatusTool(PaymentIntegrationService payments) {
        return tool(payments::status, ToolDefinitions.define(
                "get_payment_status",
                "Retrieve payment status from Payment Service. The returned status is the downstream status and is not interpreted as success. "
                        + "The Payment Service HTTP contract is not confirmed, so this tool returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "paymentId", stringProperty("Payment identifier.", ID_PATTERN)),
                        List.of("paymentId")),
                true, false, true), 100);
    }

    private static McpTool tool(Handler handler, McpSchema.Tool definition, int order) {
        return new AbstractMcpTool(definition, order) {
            @Override
            protected McpSchema.CallToolResult execute(CallerContext caller, ToolArguments arguments) {
                return ToolResults.success("Payment Service", definition.name(), handler.handle(arguments, caller));
            }
        };
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(ToolArguments arguments, CallerContext caller);
    }
}
