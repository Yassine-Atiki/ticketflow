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
        putIfPresent(filters, "q", arguments.optionalText("q"));
        Integer categoryId = arguments.optionalInt("categoryId", 1, Integer.MAX_VALUE);
        if (categoryId != null) {
            filters.put("categoryId", categoryId);
        }
        putIfPresent(filters, "city", arguments.optionalText("city"));
        putIfPresent(filters, "dateFrom", arguments.optionalDate("dateFrom"));
        putIfPresent(filters, "dateTo", arguments.optionalDate("dateTo"));
        Integer page = arguments.optionalInt("page", 0, 10_000);
        if (page != null) {
            filters.put("page", page);
        }
        Integer size = arguments.optionalInt("size", 1, 100);
        if (size != null) {
            filters.put("size", size);
        }
        putIfPresent(filters, "sort", arguments.optionalText("sort"));
        return events.search(filters, caller);
    }

    public Object details(ToolArguments arguments, CallerContext caller) {
        return events.details(arguments.requiredId("eventId"), caller);
    }

    private static void putIfPresent(Map<String, Object> target, String name, String value) {
        if (value != null) {
            target.put(name, value);
        }
    }
}
