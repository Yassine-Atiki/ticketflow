package com.ticketflow.mcptoolserver.tools;

import static com.ticketflow.mcptoolserver.tools.ToolDefinitions.CUSTOMER_ID_PATTERN;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.booleanProperty;
import static com.ticketflow.mcptoolserver.tools.ToolSchemas.enumProperty;
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

    private static final String CONFIRMATION =
            "Set userConfirmed to true only after the user explicitly agrees to change these preferences.";
    private static final String PREFERENCES_DISABLED =
            " Customer Service has published GET/PUT /api/customers/{id}/preferences on the customer branch, "
                    + "but this workspace has not confirmed that controller yet, so the call stays disabled.";

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
                true, false, true), 90) {
            @Override
            protected McpSchema.CallToolResult execute(CallerContext caller, ToolArguments arguments) {
                return ToolResults.success(
                        "Customer Service",
                        "get_customer_profile",
                        customers.profile(arguments, caller));
            }
        };
    }

    @Bean
    McpTool getCustomerPreferencesTool(CustomerIntegrationService customers) {
        return new AbstractMcpTool(ToolDefinitions.define(
                "get_customer_preferences",
                "Retrieve customer preferences from GET /api/customers/{id}/preferences. "
                        + "The response fields are customerId, language, notificationChannel and marketingOptIn."
                        + PREFERENCES_DISABLED,
                objectSchema(properties(
                        "customerId", stringProperty(
                                "Numeric customer id published by Customer Service.",
                                CUSTOMER_ID_PATTERN)),
                        List.of("customerId")),
                true, false, true), 95) {
            @Override
            protected McpSchema.CallToolResult execute(CallerContext caller, ToolArguments arguments) {
                return ToolResults.success(
                        "Customer Service",
                        "get_customer_preferences",
                        customers.preferencesGet(arguments, caller));
            }
        };
    }

    @Bean
    McpTool updateCustomerPreferencesTool(CustomerIntegrationService customers) {
        return new AbstractMcpTool(ToolDefinitions.define(
                "update_customer_preferences",
                "Create or replace customer preferences with PUT /api/customers/{id}/preferences. "
                        + "language is fr, en or ar. notificationChannel is EMAIL or TELEGRAM. "
                        + "marketingOptIn is optional. "
                        + CONFIRMATION
                        + PREFERENCES_DISABLED,
                objectSchema(properties(
                        "customerId", stringProperty(
                                "Numeric customer id published by Customer Service.",
                                CUSTOMER_ID_PATTERN),
                        "language", enumProperty("Preference language.", List.of("fr", "en", "ar")),
                        "notificationChannel", enumProperty(
                                "Notification channel.",
                                List.of("EMAIL", "TELEGRAM")),
                        "marketingOptIn", booleanProperty("Optional marketing opt-in flag."),
                        "userConfirmed", booleanProperty(CONFIRMATION)),
                        List.of("customerId", "language", "notificationChannel", "userConfirmed")),
                false, false, false), 100) {
            @Override
            protected McpSchema.CallToolResult execute(CallerContext caller, ToolArguments arguments) {
                return ToolResults.success(
                        "Customer Service",
                        "update_customer_preferences",
                        customers.preferencesPut(arguments, caller));
            }
        };
    }
}
