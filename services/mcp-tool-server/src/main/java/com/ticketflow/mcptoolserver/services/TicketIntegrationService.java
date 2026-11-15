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

    public Object mine(ToolArguments arguments, CallerContext caller) {
        Map<String, Object> filters = new LinkedHashMap<>();
        Integer page = arguments.optionalInt("page", 0, 10_000);
        if (page != null) {
            filters.put("page", page);
        }
        Integer size = arguments.optionalInt("size", 1, 100);
        if (size != null) {
            filters.put("size", size);
        }
        String status = arguments.optionalTicketStatus();
        if (status != null) {
            filters.put("status", status);
        }
        caller.requireBearer("Ticket Service", "mine");
        return tickets.mine(filters, caller);
    }
}
