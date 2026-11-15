package com.ticketflow.mcptoolserver.clients;

import com.ticketflow.mcptoolserver.security.CallerContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PaymentServiceClient extends HttpServiceClient {

    public PaymentServiceClient(ServiceInvoker invoker) {
        super("payment", invoker);
    }

    public Object initiate(Map<String, Object> fields, CallerContext caller, String idempotencyKey) {
        return send("initiate", Map.of(), fields, caller, idempotencyKey);
    }

    public Object status(String paymentId, CallerContext caller) {
        return get("status", Map.of("id", paymentId), Map.of(), caller);
    }
}
