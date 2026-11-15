package com.ticketflow.mcptoolserver.tools;

import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.UUID_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.booleanProperty;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.boundedString;
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
    private static final String DISABLED =
            " Payment Service has published this path and has not implemented it, so the call stays disabled.";

    @Bean
    McpTool initiatePaymentTool(PaymentIntegrationService payments) {
        return tool(payments::initiate, ToolDefinitions.define(
                "initiate_payment",
                "Ask Payment Service to initiate payment with POST /api/payments. "
                        + "The body is bookingId and an optional provider. The amount comes from the booking. "
                        + "Idempotency-Key is required by the payment contract. "
                        + CONFIRMATION
                        + " Card numbers, CVV and other payment credentials are rejected. "
                        + "A successful tool result means Payment Service accepted the request, not that money was captured."
                        + DISABLED,
                objectSchema(properties(
                        "bookingId", stringProperty("Booking identifier. The payment contract requires a UUID.", UUID_PATTERN),
                        "provider", stringProperty("Optional payment provider. The contract example is SIMULATED."),
                        "userConfirmed", booleanProperty(CONFIRMATION),
                        "idempotencyKey", boundedString(
                                "Required Idempotency-Key value. The payment contract allows 1 to 255 characters.",
                                1,
                                255)),
                        List.of("bookingId", "userConfirmed", "idempotencyKey")),
                false, false, false), 70);
    }

    @Bean
    McpTool getPaymentStatusTool(PaymentIntegrationService payments) {
        return tool(payments::status, ToolDefinitions.define(
                "get_payment_status",
                "Retrieve one payment with GET /api/payments/{id}. The returned status is not interpreted as capture."
                        + DISABLED,
                objectSchema(properties(
                        "paymentId", stringProperty("Payment identifier. The payment contract requires a UUID.", UUID_PATTERN)),
                        List.of("paymentId")),
                true, false, true), 80);
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
