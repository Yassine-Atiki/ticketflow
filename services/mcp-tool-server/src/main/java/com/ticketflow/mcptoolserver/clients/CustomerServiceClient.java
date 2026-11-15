package com.ticketflow.mcptoolserver.clients;

import com.ticketflow.mcptoolserver.security.CallerContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CustomerServiceClient extends HttpServiceClient {

    public CustomerServiceClient(ServiceInvoker invoker) {
        super("customer", invoker);
    }

    public Object profile(String customerId, CallerContext caller) {
        return get("profile", Map.of("customerId", customerId), Map.of(), caller);
    }

    public Object preferencesGet(String customerId, CallerContext caller) {
        return get("preferencesGet", Map.of("id", customerId), Map.of(), caller);
    }

    public Object preferencesPut(String customerId, Map<String, Object> fields, CallerContext caller) {
        return send("preferencesPut", Map.of("id", customerId), fields, caller, null);
    }
}
