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

    @Bean
    McpTool searchEventsTool(EventIntegrationService events) {
        return tool(events::search, ToolDefinitions.define(
                "search_events",
                "Search events by keyword, category, date and location. Delegates to Event Service. "
                        + "The Event Service HTTP contract is not confirmed, so this tool returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "keyword", stringProperty("Free-text keyword to match against the event catalog."),
                        "category", stringProperty("Event category. The allowed values are defined by Event Service and are not confirmed yet."),
                        "date", stringProperty("Calendar date in YYYY-MM-DD form.", DATE_PATTERN),
                        "location", stringProperty("Location text. The location model is not confirmed yet.")),
                        List.of()),
                true, false, true), 10);
    }

    @Bean
    McpTool getEventDetailsTool(EventIntegrationService events) {
        return tool(events::details, ToolDefinitions.define(
                "get_event_details",
                "Retrieve the Event Service representation of one event. "
                        + "The Event Service HTTP contract is not confirmed, so this tool returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "eventId", stringProperty("Event identifier.", ID_PATTERN)),
                        List.of("eventId")),
                true, false, true), 20);
    }

    @Bean
    McpTool getUpcomingEventsTool(EventIntegrationService events) {
        return tool(events::upcoming, ToolDefinitions.define(
                "get_upcoming_events",
                "Retrieve upcoming events from Event Service. "
                        + "The Event Service HTTP contract is not confirmed, so this tool returns CONTRACT_NOT_CONFIRMED until that contract is configured.",
                objectSchema(properties(
                        "limit", integerProperty("Maximum number of events to return.", 1, 100),
                        "category", stringProperty("Optional category filter. Allowed values are not confirmed yet."),
                        "location", stringProperty("Optional location filter. The location model is not confirmed yet.")),
                        List.of()),
                true, false, true), 30);
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
