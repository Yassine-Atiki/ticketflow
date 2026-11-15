package com.ticketflow.mcptoolserver.clients;

import com.ticketflow.mcptoolserver.security.CallerContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TicketServiceClient extends HttpServiceClient {

    public TicketServiceClient(ServiceInvoker invoker) {
        super("ticket", invoker);
    }

    public Object mine(Map<String, Object> filters, CallerContext caller) {
        return get("mine", Map.of(), filters, caller);
    }

    public Object detail(String ticketId, CallerContext caller) {
        return get("detail", Map.of("ticketId", ticketId), Map.of(), caller);
    }
}
