package com.ticketflow.mcptoolserver.services;

import com.ticketflow.mcptoolserver.clients.NotificationServiceClient;
import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.tools.ToolArguments;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class NotificationIntegrationService {

    private final NotificationServiceClient notifications;

    public NotificationIntegrationService(NotificationServiceClient notifications) {
        this.notifications = notifications;
    }

    public Object history(ToolArguments arguments, CallerContext caller) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("customerId", arguments.requiredCustomerId("customerId"));
        String bookingId = arguments.optionalId("bookingId");
        if (bookingId != null) {
            filters.put("bookingId", bookingId);
        }
        return notifications.history(filters, caller);
    }

    public Object resend(ToolArguments arguments, CallerContext caller) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("bookingId", arguments.requiredId("bookingId"));
        fields.put("customerId", arguments.requiredCustomerId("customerId"));
        String idempotencyKey = arguments.optionalIdempotencyKey();
        arguments.requireConfirmation("resend_booking_confirmation");
        return notifications.resend(fields, caller, idempotencyKey);
    }
}
