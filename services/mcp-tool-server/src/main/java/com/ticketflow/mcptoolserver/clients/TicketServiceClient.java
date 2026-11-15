package com.ticketflow.mcptoolserver.clients;

import com.ticketflow.mcptoolserver.security.CallerContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TicketServiceClient extends HttpServiceClient {

    public TicketServiceClient(ServiceInvoker invoker) {
        super("ticket", invoker);
    }

    public Object availability(String eventId, Map<String, Object> filters, CallerContext caller) {
        return get("availability", Map.of("eventId", eventId), filters, caller);
    }

    public Object mine(String customerId, CallerContext caller) {
        return get("mine", Map.of("customerId", customerId), Map.of(), caller);
    }
}
