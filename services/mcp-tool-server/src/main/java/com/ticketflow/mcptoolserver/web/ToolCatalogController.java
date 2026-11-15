package com.ticketflow.mcptoolserver.web;

import com.ticketflow.mcptoolserver.tools.McpTool;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools")
@Tag(name = "MCP tools", description = "Read-only catalog of tools registered on the Streamable HTTP endpoint.")
public class ToolCatalogController {

    private final List<McpTool> tools;

    public ToolCatalogController(List<McpTool> tools) {
        this.tools = tools.stream().sorted(Comparator.comparingInt(McpTool::order)).toList();
    }

    @GetMapping
    @Operation(summary = "List registered MCP tools",
            description = "Returns each tool name, description, JSON Schema input, and hint flags. "
                    + "Invoke a tool through MCP at /mcp. This endpoint does not call downstream services.")
    public List<ToolDescription> list() {
        return tools.stream().map(ToolCatalogController::describe).toList();
    }

    private static ToolDescription describe(McpTool tool) {
        McpSchema.Tool definition = tool.definition();
        McpSchema.ToolAnnotations annotations = definition.annotations();
        return new ToolDescription(
                definition.name(),
                definition.description(),
                definition.inputSchema(),
                annotations == null ? null : annotations.readOnlyHint(),
                annotations == null ? null : annotations.destructiveHint(),
                annotations == null ? null : annotations.idempotentHint(),
                annotations == null ? null : annotations.openWorldHint());
    }

    public record ToolDescription(
            String name,
            String description,
            Map<String, Object> inputSchema,
            Boolean readOnly,
            Boolean destructive,
            Boolean idempotent,
            Boolean openWorld) {
    }
}
