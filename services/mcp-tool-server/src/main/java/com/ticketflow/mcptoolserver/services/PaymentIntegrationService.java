package com.ticketflow.mcptoolserver.services;

import com.ticketflow.mcptoolserver.clients.PaymentServiceClient;
import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.tools.ToolArguments;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaymentIntegrationService {

    private final PaymentServiceClient payments;

    public PaymentIntegrationService(PaymentServiceClient payments) {
        this.payments = payments;
    }

    public Object initiate(ToolArguments arguments, CallerContext caller) {
        String bookingId = arguments.requiredId("bookingId");
        String idempotencyKey = arguments.optionalIdempotencyKey();
        arguments.requireConfirmation("initiate_payment");
        return payments.initiate(Map.of("bookingId", bookingId), caller, idempotencyKey);
    }

    public Object status(ToolArguments arguments, CallerContext caller) {
        return payments.status(arguments.requiredId("paymentId"), caller);
    }
}
