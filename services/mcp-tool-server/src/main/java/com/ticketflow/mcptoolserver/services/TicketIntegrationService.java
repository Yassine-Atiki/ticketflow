package com.ticketflow.mcptoolserver.services;

import com.ticketflow.mcptoolserver.clients.TicketServiceClient;
import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.tools.ToolArguments;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TicketIntegrationService {

    private final TicketServiceClient tickets;

    public TicketIntegrationService(TicketServiceClient tickets) {
        this.tickets = tickets;
    }

    public Object availability(ToolArguments arguments, CallerContext caller) {
        Map<String, Object> filters = new LinkedHashMap<>();
        String ticketType = arguments.optionalId("ticketType");
        if (ticketType != null) {
            filters.put("ticketType", ticketType);
        }
        Integer quantity = arguments.optionalQuantity("quantity");
        if (quantity != null) {
            filters.put("quantity", quantity);
        }
        return tickets.availability(arguments.requiredId("eventId"), filters, caller);
    }

    public Object mine(ToolArguments arguments, CallerContext caller) {
        return tickets.mine(arguments.requiredCustomerId("customerId"), caller);
    }
}
