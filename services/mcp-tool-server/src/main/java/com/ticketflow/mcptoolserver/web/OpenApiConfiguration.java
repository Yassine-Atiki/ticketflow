package com.ticketflow.mcptoolserver.web;

import com.ticketflow.mcptoolserver.config.McpServerProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI mcpToolServerOpenApi(McpServerProperties properties) {
        return new OpenAPI().info(new Info()
                .title("TicketFlow MCP Tool Server")
                .version(properties.getServerVersion())
                .description("""
                        Documentation for the MCP tool server HTTP surface.
                        Tool calls use the MCP Streamable HTTP endpoint at %s.
                        GET /api/tools lists the tools registered on that endpoint and their input schemas.
                        The catalog does not execute a tool.
                        """.formatted(properties.getEndpoint())));
    }
}
