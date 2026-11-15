package com.ticketflow.mcptoolserver.tools;

import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.UUID_PATTERN;
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

    private static final String DISABLED =
            " Ticket Service has published the contract and has not implemented the endpoint, so the call stays disabled.";

    @Bean
    McpTool getMyTicketsTool(TicketIntegrationService tickets) {
        return tool(tickets::mine, ToolDefinitions.define(
                "get_my_tickets",
                "List tickets for the authenticated caller through GET /api/v1/me/tickets. "
                        + "The owner is the bearer token subject. This tool does not accept a customer id."
                        + DISABLED,
                objectSchema(properties(
                        "page", integerProperty("Zero-based page index.", 0, 10_000),
                        "size", integerProperty("Page size.", 1, 100),
                        "status", stringProperty("ISSUED or CANCELLED.")),
                        List.of()),
                true, false, true), 30);
    }

    @Bean
    McpTool getMyTicketTool(TicketIntegrationService tickets) {
        return tool(tickets::detail, ToolDefinitions.define(
                "get_my_ticket",
                "Retrieve one ticket for the authenticated caller through GET /api/v1/me/tickets/{ticketId}. "
                        + "The owner is the bearer token subject. "
                        + "controlCode is redacted before the result reaches the assistant."
                        + DISABLED,
                objectSchema(properties(
                        "ticketId", stringProperty(
                                "Ticket identifier. The ticket contract requires a UUID.",
                                UUID_PATTERN)),
                        List.of("ticketId")),
                true, false, true), 35);
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
