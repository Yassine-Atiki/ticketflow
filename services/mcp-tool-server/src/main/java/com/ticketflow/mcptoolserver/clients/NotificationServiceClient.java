package com.ticketflow.mcptoolserver.clients;

import com.ticketflow.mcptoolserver.security.CallerContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationServiceClient extends HttpServiceClient {

    public NotificationServiceClient(ServiceInvoker invoker) {
        super("notification", invoker);
    }

    public Object history(Map<String, Object> filters, CallerContext caller) {
        return get("history", Map.of(), filters, caller);
    }

    public Object resend(Map<String, Object> fields, CallerContext caller, String idempotencyKey) {
        return send("resend", Map.of(), fields, caller, idempotencyKey);
    }
}
