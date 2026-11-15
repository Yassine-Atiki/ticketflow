package com.ticketflow.mcptoolserver.clients;

import com.ticketflow.mcptoolserver.security.CallerContext;
import java.util.Map;

abstract class HttpServiceClient {

    private final String serviceKey;
    private final ServiceInvoker invoker;

    protected HttpServiceClient(String serviceKey, ServiceInvoker invoker) {
        this.serviceKey = serviceKey;
        this.invoker = invoker;
    }

    protected Object get(
            String operation,
            Map<String, String> pathVariables,
            Map<String, Object> arguments,
            CallerContext caller) {
        return invoker.invoke(serviceKey, operation, pathVariables, arguments, caller, null);
    }

    protected Object send(
            String operation,
            Map<String, String> pathVariables,
            Map<String, Object> arguments,
            CallerContext caller,
            String idempotencyKey) {
        return invoker.invoke(serviceKey, operation, pathVariables, arguments, caller, idempotencyKey);
    }
}
