package com.ticketflow.mcptoolserver.tools;

import com.ticketflow.mcptoolserver.exceptions.ToolFailure;
import com.ticketflow.mcptoolserver.exceptions.ToolFailureException;
import com.ticketflow.mcptoolserver.security.CallerContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractMcpTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(AbstractMcpTool.class);

    private final McpSchema.Tool definition;
    private final int order;

    protected AbstractMcpTool(McpSchema.Tool definition, int order) {
        this.definition = definition;
        this.order = order;
    }

    @Override
    public McpSchema.Tool definition() {
        return definition;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public final McpSchema.CallToolResult call(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        try {
            CallerContext caller = exchange == null
                    ? CallerContext.anonymous()
                    : CallerContext.from(exchange.transportContext());
            ToolArguments arguments = new ToolArguments(request == null ? null : request.arguments());
            return execute(caller, arguments);
        } catch (ToolFailureException ex) {
            ToolFailure failure = ex.failure();
            if (failure.operation() == null) {
                failure = new ToolFailure(
                        failure.code(), failure.service(), definition.name(), failure.message(), failure.httpStatus());
            }
            return ToolResults.failure(failure);
        } catch (RuntimeException ex) {
            log.error("Tool {} failed unexpectedly", definition.name(), ex);
            return ToolResults.failure(ToolFailure.unexpected(definition.name()));
        }
    }

    protected abstract McpSchema.CallToolResult execute(CallerContext caller, ToolArguments arguments);
}
