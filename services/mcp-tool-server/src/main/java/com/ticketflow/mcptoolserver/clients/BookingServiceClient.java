package com.ticketflow.mcptoolserver.clients;

import com.ticketflow.mcptoolserver.security.CallerContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BookingServiceClient extends HttpServiceClient {

    public BookingServiceClient(ServiceInvoker invoker) {
        super("booking", invoker);
    }

    public Object create(Map<String, Object> fields, CallerContext caller, String idempotencyKey) {
        return send("create", Map.of(), fields, caller, idempotencyKey);
    }

    public Object status(String bookingId, CallerContext caller) {
        return get("status", Map.of("id", bookingId), Map.of(), caller);
    }

    public Object cancel(
            String bookingId,
            Map<String, Object> fields,
            CallerContext caller,
            String idempotencyKey) {
        return send("cancel", Map.of("id", bookingId), fields, caller, idempotencyKey);
    }

    public Object mine(Map<String, Object> filters, CallerContext caller) {
        return get("mine", Map.of(), filters, caller);
    }
}
