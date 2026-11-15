package com.ticketflow.mcptoolserver.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;

public final class ToolDefinitions {

    public static final String ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$";
    public static final String CUSTOMER_ID_PATTERN = "^[1-9][0-9]{0,18}$";
    public static final String DATE_PATTERN = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$";

    private ToolDefinitions() {
    }

    public static McpSchema.Tool define(
            String name,
            String description,
            Map<String, Object> inputSchema,
            boolean readOnly,
            boolean destructive,
            boolean idempotent) {
        return McpSchema.Tool.builder(name, inputSchema)
                .description(description)
                .annotations(McpSchema.ToolAnnotations.builder()
                        .readOnlyHint(readOnly)
                        .destructiveHint(destructive)
                        .idempotentHint(idempotent)
                        .openWorldHint(true)
                        .build())
                .build();
    }
}
