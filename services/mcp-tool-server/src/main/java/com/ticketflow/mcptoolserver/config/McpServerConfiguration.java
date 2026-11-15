package com.ticketflow.mcptoolserver.config;

import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.tools.McpTool;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.ServerHttpHeaderValidator;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfiguration {

    static final String INSTRUCTIONS = """
            TicketFlow MCP tool server. Tools delegate to platform microservices and do not apply booking, payment or inventory rules.
            create_booking, cancel_booking, initiate_payment and update_customer_preferences stay unexecuted until userConfirmed is true after an explicit user agreement.
            Booking and ticket tools identify the caller from the bearer token. They do not accept a customerId owner.
            A payment tool result never means the payment was captured.
            Ticket controlCode values are redacted before they reach the assistant.
            Tools whose downstream implementation is not enabled return CONTRACT_NOT_CONFIRMED and do not send an HTTP call.
            Customer profile reads use the implemented Customer Service path GET /api/customers/{id}.
            Preference tools stay disabled until Customer PR #7 is confirmed on develop.
            """;

    @Bean
    HttpServletStreamableServerTransportProvider mcpTransport(McpServerProperties properties) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint(properties.getEndpoint())
                .contextExtractor(McpServerConfiguration::callerContext)
                .httpHeaderValidator(headerValidator(properties))
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transport,
            McpServerProperties properties) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transport, properties.getEndpoint());
        registration.setName("mcpStreamableTransport");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean(destroyMethod = "close")
    McpSyncServer mcpSyncServer(
            HttpServletStreamableServerTransportProvider transport,
            List<McpTool> tools,
            McpServerProperties properties) {
        List<McpTool> ordered = tools.stream()
                .sorted(Comparator.comparingInt(McpTool::order))
                .toList();
        Set<String> names = new HashSet<>();
        var builder = McpServer.sync(transport)
                .serverInfo(properties.getServerName(), properties.getServerVersion())
                .instructions(INSTRUCTIONS.trim())
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build());
        for (McpTool tool : ordered) {
            String name = tool.definition().name();
            if (!names.add(name)) {
                throw new IllegalStateException("Duplicate MCP tool: " + name);
            }
            builder.toolCall(tool.definition(), tool::call);
        }
        return builder.build();
    }

    private static McpTransportContext callerContext(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return McpTransportContext.EMPTY;
        }
        return McpTransportContext.create(Map.of(CallerContext.AUTHORIZATION, authorization));
    }

    private static ServerHttpHeaderValidator headerValidator(McpServerProperties properties) {
        if (properties.getAllowedHosts().isEmpty() && properties.getAllowedOrigins().isEmpty()) {
            return ServerHttpHeaderValidator.NOOP;
        }
        DefaultServerTransportSecurityValidator.Builder builder = DefaultServerTransportSecurityValidator.builder();
        properties.getAllowedHosts().forEach(builder::allowedHost);
        properties.getAllowedOrigins().forEach(builder::allowedOrigin);
        return builder.build();
    }
}
