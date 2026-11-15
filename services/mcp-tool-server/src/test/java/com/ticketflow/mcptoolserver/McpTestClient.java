package com.ticketflow.mcptoolserver;

import com.ticketflow.mcptoolserver.tools.McpTool;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class McpTestClient {

    private McpTestClient() {
    }

    static McpSchema.CallToolRequest call(String name, Map<String, Object> arguments) {
        return McpSchema.CallToolRequest.builder().name(name).arguments(arguments).build();
    }

    static McpSyncClient connect(int port, String authorization) {
        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .addHttpRequestCustomizer((builder, method, uri, body, context) -> {
                    if (authorization != null) {
                        builder.header("Authorization", authorization);
                    }
                })
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .clientInfo(McpSchema.Implementation.builder("ticketflow-test", "0.0.1").build())
                .build();
        client.initialize();
        return client;
    }

    static Map<String, Object> body(McpSchema.CallToolResult result) {
        if (result.structuredContent() instanceof Map<?, ?> structured) {
            Map<String, Object> copy = new LinkedHashMap<>();
            structured.forEach((key, value) -> copy.put(String.valueOf(key), value));
            return copy;
        }
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        try {
            return McpJsonDefaults.getMapper().readValue(text.text(), new TypeRef<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Tool result was not JSON", ex);
        }
    }

    static McpTool toolNamed(Iterable<McpTool> tools, String name) {
        for (McpTool tool : tools) {
            if (tool.definition().name().equals(name)) {
                return tool;
            }
        }
        throw new IllegalArgumentException("Missing tool " + name);
    }
}
