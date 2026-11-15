package com.ticketflow.mcptoolserver.tools;

import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.CUSTOMER_ID_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.ID_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.integerProperty;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.objectSchema;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.properties;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.stringProperty;

import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.services.TicketIntegrationService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketToolConfiguration {

    @Bean
    McpTool checkTicketAvailabilityTool(TicketIntegrationService tickets) {
        return tool(tickets::availability, ToolDefinitions.define(
                "check_ticket_availability",
                "Check ticket availability for an event by delegating to Ticket Service. "
                        + "The Ticket Service HTTP contract is not confirmed, so this tool returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "eventId", stringProperty("Event identifier.", ID_PATTERN),
                        "ticketType", stringProperty("Optional ticket type. Ticket types are not confirmed yet.", ID_PATTERN),
                        "quantity", integerProperty("Optional quantity to check. Ticket Service enforces the real inventory rule.", 1, 50)),
                        List.of("eventId")),
                true, false, true), 40);
    }

    @Bean
    McpTool getMyTicketsTool(TicketIntegrationService tickets) {
        return tool(tickets::mine, ToolDefinitions.define(
                "get_my_tickets",
                "Retrieve tickets for a customer. Authenticated subject extraction is not available yet, so customerId is required. "
                        + "The Ticket Service HTTP contract is not confirmed, so this tool returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "customerId", stringProperty(
                                "Numeric customer id published by Customer Service. A bearer token is forwarded when the MCP client sends one, and it is not validated here.",
                                CUSTOMER_ID_PATTERN)),
                        List.of("customerId")),
                true, false, true), 50);
    }

    private static McpTool tool(Handler handler, McpSchema.Tool definition, int order) {
        return new AbstractMcpTool(definition, order) {
            @Override
            protected McpSchema.CallToolResult execute(CallerContext caller, ToolArguments arguments) {
                return ToolResults.success("Ticket Service", definition.name(), handler.handle(arguments, caller));
            }
        };
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(ToolArguments arguments, CallerContext caller);
    }
}
