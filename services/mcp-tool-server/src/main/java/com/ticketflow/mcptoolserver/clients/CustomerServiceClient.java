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
}
