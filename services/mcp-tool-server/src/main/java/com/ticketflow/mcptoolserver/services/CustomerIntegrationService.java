package com.ticketflow.mcptoolserver.services;

import com.ticketflow.mcptoolserver.clients.CustomerServiceClient;
import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.tools.ToolArguments;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CustomerIntegrationService {

    private final CustomerServiceClient customers;

    public CustomerIntegrationService(CustomerServiceClient customers) {
        this.customers = customers;
    }

    public Object profile(ToolArguments arguments, CallerContext caller) {
        return customers.profile(arguments.requiredCustomerId("customerId"), caller);
    }

    public Object preferencesGet(ToolArguments arguments, CallerContext caller) {
        return customers.preferencesGet(arguments.requiredCustomerId("customerId"), caller);
    }

    public Object preferencesPut(ToolArguments arguments, CallerContext caller) {
        String customerId = arguments.requiredCustomerId("customerId");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("language", arguments.requiredPreferenceLanguage());
        fields.put("notificationChannel", arguments.requiredNotificationChannel());
        Boolean marketingOptIn = arguments.optionalBoolean("marketingOptIn");
        if (marketingOptIn != null) {
            fields.put("marketingOptIn", marketingOptIn);
        }
        arguments.requireConfirmation("update_customer_preferences");
        return customers.preferencesPut(customerId, fields, caller);
    }
}
