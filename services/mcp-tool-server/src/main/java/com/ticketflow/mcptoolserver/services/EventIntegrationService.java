package com.ticketflow.mcptoolserver.services;

import com.ticketflow.mcptoolserver.clients.EventServiceClient;
import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.tools.ToolArguments;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EventIntegrationService {

    private final EventServiceClient events;

    public EventIntegrationService(EventServiceClient events) {
        this.events = events;
    }

    public Object search(ToolArguments arguments, CallerContext caller) {
        Map<String, Object> filters = new LinkedHashMap<>();
        putIfPresent(filters, "keyword", arguments.optionalText("keyword"));
        putIfPresent(filters, "category", arguments.optionalText("category"));
        putIfPresent(filters, "date", arguments.optionalDate("date"));
        putIfPresent(filters, "location", arguments.optionalText("location"));
        return events.search(filters, caller);
    }

    public Object details(ToolArguments arguments, CallerContext caller) {
        return events.details(arguments.requiredId("eventId"), caller);
    }

    public Object upcoming(ToolArguments arguments, CallerContext caller) {
        Map<String, Object> filters = new LinkedHashMap<>();
        Integer limit = arguments.optionalLimit("limit");
        if (limit != null) {
            filters.put("limit", limit);
        }
        putIfPresent(filters, "category", arguments.optionalText("category"));
        putIfPresent(filters, "location", arguments.optionalText("location"));
        return events.upcoming(filters, caller);
    }

    private static void putIfPresent(Map<String, Object> target, String name, String value) {
        if (value != null) {
            target.put(name, value);
        }
    }
}
