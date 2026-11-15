package com.ticketflow.mcptoolserver.tools;

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
    McpTool getMyTicketsTool(TicketIntegrationService tickets) {
        return tool(tickets::mine, ToolDefinitions.define(
                "get_my_tickets",
                "List tickets for the authenticated caller through GET /api/v1/me/tickets. "
                        + "The owner is the bearer token subject. This tool does not accept a customer id. "
                        + "Ticket Service has published the contract and has not implemented the endpoint, so the call stays disabled.",
                objectSchema(properties(
                        "page", integerProperty("Zero-based page index.", 0, 10_000),
                        "size", integerProperty("Page size.", 1, 100),
                        "status", stringProperty("ISSUED or CANCELLED.")),
                        List.of()),
                true, false, true), 30);
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
