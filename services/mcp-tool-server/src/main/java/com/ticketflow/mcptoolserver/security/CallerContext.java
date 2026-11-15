package com.ticketflow.mcptoolserver.security;

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
}
