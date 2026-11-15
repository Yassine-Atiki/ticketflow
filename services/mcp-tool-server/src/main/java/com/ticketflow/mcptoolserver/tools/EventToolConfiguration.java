package com.ticketflow.mcptoolserver.tools;

import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.DATE_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.ID_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.integerProperty;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.objectSchema;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.properties;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.stringProperty;

import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.services.EventIntegrationService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventToolConfiguration {

    private static final String DISABLED =
            " Event Service has published GET /api/v1/events, but the endpoint is not implemented yet, so this tool stays disabled.";

    @Bean
    McpTool searchEventsTool(EventIntegrationService events) {
        return tool(events::search, ToolDefinitions.define(
                "search_events",
                "Search published events. Query names match Event Service: q, categoryId, city, dateFrom, dateTo, page, size and sort."
                        + DISABLED,
                objectSchema(properties(
                        "q", stringProperty("Keyword matched by Event Service query parameter q."),
                        "categoryId", integerProperty("Category id.", 1, Integer.MAX_VALUE),
                        "city", stringProperty("City filter."),
                        "dateFrom", stringProperty("Inclusive start date in YYYY-MM-DD form.", DATE_PATTERN),
                        "dateTo", stringProperty("Inclusive end date in YYYY-MM-DD form.", DATE_PATTERN),
                        "page", integerProperty("Zero-based page index.", 0, 10_000),
                        "size", integerProperty("Page size.", 1, 100),
                        "sort", stringProperty("Sort expression, for example startDate,asc.")),
                        List.of()),
                true, false, true), 10);
    }

    @Bean
    McpTool getEventDetailsTool(EventIntegrationService events) {
        return tool(events::details, ToolDefinitions.define(
                "get_event_details",
                "Retrieve one event from GET /api/v1/events/{id}."
                        + DISABLED,
                objectSchema(properties(
                        "eventId", stringProperty("Event identifier.", ID_PATTERN)),
                        List.of("eventId")),
                true, false, true), 20);
    }

    private static McpTool tool(Handler handler, McpSchema.Tool definition, int order) {
        return new AbstractMcpTool(definition, order) {
            @Override
            protected McpSchema.CallToolResult execute(CallerContext caller, ToolArguments arguments) {
                return ToolResults.success("Event Service", definition.name(), handler.handle(arguments, caller));
            }
        };
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(ToolArguments arguments, CallerContext caller);
    }
}
