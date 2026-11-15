package com.ticketflow.mcptoolserver.clients;

import java.util.Map;
import com.ticketflow.mcptoolserver.security.CallerContext;

public interface ServiceInvoker {

    Object invoke(
            String serviceKey,
            String operationName,
            Map<String, String> pathVariables,
            Map<String, Object> arguments,
            CallerContext caller,
            String idempotencyKey);
}
