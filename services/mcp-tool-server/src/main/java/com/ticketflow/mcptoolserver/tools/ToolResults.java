package com.ticketflow.mcptoolserver.tools;

import com.ticketflow.mcptoolserver.exceptions.ToolFailure;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolResults {

    private static final McpJsonMapper JSON = McpJsonDefaults.getMapper();

    private ToolResults() {
    }

    public static McpSchema.CallToolResult success(String service, String operation, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "OK");
        body.put("service", service);
        body.put("operation", operation);
        body.put("data", data);
        return result(body, false);
    }

    public static McpSchema.CallToolResult failure(ToolFailure failure) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("code", failure.code());
        if (failure.service() != null) {
            body.put("service", failure.service());
        }
        if (failure.operation() != null) {
            body.put("operation", failure.operation());
        }
        body.put("message", failure.message());
        if (failure.httpStatus() != null) {
            body.put("httpStatus", failure.httpStatus());
        }
        return result(body, true);
    }

    private static McpSchema.CallToolResult result(Map<String, Object> body, boolean error) {
        String text;
        try {
            text = JSON.writeValueAsString(body);
        } catch (IOException ex) {
            text = "{\"status\":\"ERROR\",\"code\":\"UNEXPECTED_ERROR\",\"message\":\"The tool result could not be serialized.\"}";
            error = true;
        }
        return McpSchema.CallToolResult.builder()
                .addTextContent(text)
                .structuredContent(body)
                .isError(error)
                .build();
    }
}
