package com.ticketflow.mcptoolserver.services;

import com.ticketflow.mcptoolserver.clients.CustomerServiceClient;
import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.tools.ToolArguments;
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
}
