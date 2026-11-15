package com.ticketflow.mcptoolserver.security;

import com.ticketflow.mcptoolserver.exceptions.ToolFailure;
import com.ticketflow.mcptoolserver.exceptions.ToolFailureException;
import io.modelcontextprotocol.common.McpTransportContext;

public record CallerContext(String authorizationHeader) {

    public static final String AUTHORIZATION = "authorization";

    public static CallerContext anonymous() {
        return new CallerContext(null);
    }

    public static CallerContext from(McpTransportContext context) {
        if (context == null) {
            return anonymous();
        }
        Object value = context.get(AUTHORIZATION);
        if (value instanceof String header && !header.isBlank()) {
            return new CallerContext(header);
        }
        return anonymous();
    }

    public boolean hasAuthorization() {
        return authorizationHeader != null && !authorizationHeader.isBlank();
    }

    public void requireBearer(String service, String operation) {
        if (authorizationHeader == null
                || authorizationHeader.length() < 8
                || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorizationHeader.substring(7).isBlank()) {
            throw new ToolFailureException(ToolFailure.callerAuthenticationRequired(service, operation));
        }
    }
}
