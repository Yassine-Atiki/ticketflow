package com.ticketflow.mcptoolserver.tools;

import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.CUSTOMER_ID_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.objectSchema;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.properties;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.stringProperty;

import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.services.CustomerIntegrationService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerToolConfiguration {

    @Bean
    McpTool getCustomerProfileTool(CustomerIntegrationService customers) {
        return new AbstractMcpTool(ToolDefinitions.define(
                "get_customer_profile",
                "Retrieve a customer profile from Customer Service GET /api/customers/{id}. "
                        + "That path is the published Customer Service contract. "
                        + "Authenticated subject extraction is not available yet, so customerId is required. "
                        + "The response contains the fields Customer Service already returns: id, firstName, lastName, email, phone and createdAt.",
                objectSchema(properties(
                        "customerId", stringProperty(
                                "Numeric customer id published by Customer Service.",
                                CUSTOMER_ID_PATTERN)),
                        List.of("customerId")),
                true, false, true), 110) {
            @Override
            protected McpSchema.CallToolResult execute(CallerContext caller, ToolArguments arguments) {
                return ToolResults.success(
                        "Customer Service",
                        "get_customer_profile",
                        customers.profile(arguments, caller));
            }
        };
    }
}
