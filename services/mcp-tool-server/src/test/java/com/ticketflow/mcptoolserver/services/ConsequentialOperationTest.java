package com.ticketflow.mcptoolserver.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketflow.mcptoolserver.clients.BookingServiceClient;
import com.ticketflow.mcptoolserver.clients.PaymentServiceClient;
import com.ticketflow.mcptoolserver.clients.ServiceInvoker;
import com.ticketflow.mcptoolserver.exceptions.ToolFailureException;
import com.ticketflow.mcptoolserver.security.CallerContext;
import com.ticketflow.mcptoolserver.tools.ToolArguments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsequentialOperationTest {

    @Test
    void createBookingDoesNotCallTheServiceWithoutConfirmation() {
        RecordingInvoker invoker = new RecordingInvoker();
        BookingIntegrationService bookings = new BookingIntegrationService(new BookingServiceClient(invoker));

        assertThatThrownBy(() -> bookings.create(new ToolArguments(Map.of(
                "eventId", "evt-1",
                "customerId", "42",
                "quantity", 2,
                "userConfirmed", false)), CallerContext.anonymous()))
                .isInstanceOf(ToolFailureException.class)
                .extracting(ex -> ((ToolFailureException) ex).failure().code())
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(invoker.calls).isEmpty();
    }

    @Test
    void createBookingForwardsOnlyBookingFieldsAfterConfirmation() {
        RecordingInvoker invoker = new RecordingInvoker();
        BookingIntegrationService bookings = new BookingIntegrationService(new BookingServiceClient(invoker));

        bookings.create(new ToolArguments(Map.of(
                "eventId", "evt-1",
                "customerId", "42",
                "quantity", 2,
                "ticketType", "standard",
                "userConfirmed", true,
                "idempotencyKey", "idem-1")), CallerContext.anonymous());

        assertThat(invoker.calls).containsExactly(new RecordingInvoker.Call(
                "create",
                Map.of("eventId", "evt-1", "customerId", "42", "quantity", 2, "ticketType", "standard"),
                "idem-1"));
    }

    @Test
    void initiatePaymentSendsTheBookingIdAndNoPaymentCredentials() {
        RecordingInvoker invoker = new RecordingInvoker();
        PaymentIntegrationService payments = new PaymentIntegrationService(new PaymentServiceClient(invoker));

        payments.initiate(new ToolArguments(Map.of(
                "bookingId", "book-1",
                "userConfirmed", true,
                "cardNumber", "4242424242424242",
                "cvv", "123")), CallerContext.anonymous());

        assertThat(invoker.calls).containsExactly(new RecordingInvoker.Call(
                "initiate",
                Map.of("bookingId", "book-1"),
                null));
    }

    @Test
    void initiatePaymentDoesNotCallTheServiceWithoutABooking() {
        RecordingInvoker invoker = new RecordingInvoker();
        PaymentIntegrationService payments = new PaymentIntegrationService(new PaymentServiceClient(invoker));

        assertThatThrownBy(() -> payments.initiate(new ToolArguments(Map.of(
                "userConfirmed", true)), CallerContext.anonymous()))
                .isInstanceOf(ToolFailureException.class);
        assertThat(invoker.calls).isEmpty();
    }

    private static final class RecordingInvoker implements ServiceInvoker {

        private final List<Call> calls = new ArrayList<>();

        @Override
        public Object invoke(
                String serviceKey,
                String operationName,
                Map<String, String> pathVariables,
                Map<String, Object> arguments,
                CallerContext caller,
                String idempotencyKey) {
            calls.add(new Call(operationName, arguments, idempotencyKey));
            return Map.of("accepted", true);
        }

        private record Call(String operation, Map<String, Object> arguments, String idempotencyKey) {
        }
    }
}
