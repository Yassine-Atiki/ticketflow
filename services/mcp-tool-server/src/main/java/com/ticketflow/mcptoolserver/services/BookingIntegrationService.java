package com.ticketflow.mcptoolserver.services;

import com.ticketflow.mcptoolserver.clients.BookingServiceClient;
import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.tools.ToolArguments;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BookingIntegrationService {

    private final BookingServiceClient bookings;

    public BookingIntegrationService(BookingServiceClient bookings) {
        this.bookings = bookings;
    }

    public Object create(ToolArguments arguments, CallerContext caller) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("eventId", arguments.requiredId("eventId"));
        fields.put("customerId", arguments.requiredCustomerId("customerId"));
        fields.put("quantity", arguments.requiredQuantity("quantity"));
        String ticketType = arguments.optionalId("ticketType");
        if (ticketType != null) {
            fields.put("ticketType", ticketType);
        }
        String idempotencyKey = arguments.optionalIdempotencyKey();
        arguments.requireConfirmation("create_booking");
        return bookings.create(fields, caller, idempotencyKey);
    }

    public Object status(ToolArguments arguments, CallerContext caller) {
        return bookings.status(arguments.requiredId("bookingId"), caller);
    }

    public Object cancel(ToolArguments arguments, CallerContext caller) {
        String bookingId = arguments.requiredId("bookingId");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("customerId", arguments.requiredCustomerId("customerId"));
        String reason = arguments.optionalText("reason");
        if (reason != null) {
            fields.put("reason", reason);
        }
        String idempotencyKey = arguments.optionalIdempotencyKey();
        arguments.requireConfirmation("cancel_booking");
        return bookings.cancel(bookingId, fields, caller, idempotencyKey);
    }
}
