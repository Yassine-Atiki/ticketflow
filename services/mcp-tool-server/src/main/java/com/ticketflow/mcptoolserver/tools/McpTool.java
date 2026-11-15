package com.ticketflow.mcptoolserver.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

public interface McpTool {

    McpSchema.Tool definition();

    int order();

    McpSchema.CallToolResult call(McpSyncServerExchange exchange, McpSchema.CallToolRequest request);
}
