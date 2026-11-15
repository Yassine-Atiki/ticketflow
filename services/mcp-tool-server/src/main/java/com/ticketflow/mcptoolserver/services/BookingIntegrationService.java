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
        fields.put("eventId", arguments.requiredUuid("eventId"));
        fields.put("lines", arguments.requiredBookingLines());
        String idempotencyKey = arguments.requiredIdempotencyKey();
        arguments.requireConfirmation("create_booking");
        caller.requireBearer("Booking Service", "create");
        return bookings.create(fields, caller, idempotencyKey);
    }

    public Object status(ToolArguments arguments, CallerContext caller) {
        String bookingId = arguments.requiredUuid("bookingId");
        caller.requireBearer("Booking Service", "status");
        return bookings.status(bookingId, caller);
    }

    public Object cancel(ToolArguments arguments, CallerContext caller) {
        String bookingId = arguments.requiredUuid("bookingId");
        Map<String, Object> fields = new LinkedHashMap<>();
        String reason = arguments.optionalReason();
        if (reason != null) {
            fields.put("reason", reason);
        }
        String idempotencyKey = arguments.optionalIdempotencyKey();
        arguments.requireConfirmation("cancel_booking");
        caller.requireBearer("Booking Service", "cancel");
        return bookings.cancel(bookingId, fields, caller, idempotencyKey);
    }

    public Object mine(ToolArguments arguments, CallerContext caller) {
        Map<String, Object> filters = new LinkedHashMap<>();
        String status = arguments.optionalBookingStatus();
        if (status != null) {
            filters.put("status", status);
        }
        Integer page = arguments.optionalInt("page", 0, 10_000);
        if (page != null) {
            filters.put("page", page);
        }
        Integer size = arguments.optionalInt("size", 1, 50);
        if (size != null) {
            filters.put("size", size);
        }
        caller.requireBearer("Booking Service", "mine");
        return bookings.mine(filters, caller);
    }
}
