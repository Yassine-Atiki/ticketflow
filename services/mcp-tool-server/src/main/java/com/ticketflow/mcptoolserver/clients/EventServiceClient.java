package com.ticketflow.mcptoolserver.clients;

import com.ticketflow.mcptoolserver.security.CallerContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EventServiceClient extends HttpServiceClient {

    public EventServiceClient(ServiceInvoker invoker) {
        super("event", invoker);
    }

    public Object search(Map<String, Object> filters, CallerContext caller) {
        return get("search", Map.of(), filters, caller);
    }

    public Object details(String eventId, CallerContext caller) {
        return get("details", Map.of("eventId", eventId), Map.of(), caller);
    }

    public Object upcoming(Map<String, Object> filters, CallerContext caller) {
        return get("upcoming", Map.of(), filters, caller);
    }
}
