package com.ticketflow.mcptoolserver.services;

import com.ticketflow.mcptoolserver.clients.PaymentServiceClient;
import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.tools.ToolArguments;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaymentIntegrationService {

    private final PaymentServiceClient payments;

    public PaymentIntegrationService(PaymentServiceClient payments) {
        this.payments = payments;
    }

    public Object initiate(ToolArguments arguments, CallerContext caller) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("bookingId", arguments.requiredUuid("bookingId"));
        String provider = arguments.optionalText("provider");
        if (provider != null) {
            fields.put("provider", provider);
        }
        String idempotencyKey = arguments.requiredPaymentIdempotencyKey();
        arguments.requireConfirmation("initiate_payment");
        return payments.initiate(fields, caller, idempotencyKey);
    }

    public Object status(ToolArguments arguments, CallerContext caller) {
        return payments.status(arguments.requiredUuid("paymentId"), caller);
    }

    public Object byBooking(ToolArguments arguments, CallerContext caller) {
        return payments.byBooking(arguments.requiredUuid("bookingId"), caller);
    }
}
