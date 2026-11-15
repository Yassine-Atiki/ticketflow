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

    private static final CallerContext CALLER = new CallerContext("Bearer test-token");
    private static final String EVENT_ID = "e4f5a6b7-c8d9-4e0f-a1b2-c3d4e5f6a7b8";
    private static final String TIER_ID = "f1e2d3c4-b5a6-4978-8695-a4b3c2d1e0f9";
    private static final String BOOKING_ID = "b3e1f9a0-1c2d-4e5f-8a9b-0c1d2e3f4a5b";

    @Test
    void createBookingDoesNotCallTheServiceWithoutConfirmation() {
        RecordingInvoker invoker = new RecordingInvoker();
        BookingIntegrationService bookings = new BookingIntegrationService(new BookingServiceClient(invoker));

        assertThatThrownBy(() -> bookings.create(new ToolArguments(bookingArguments(false)), CallerContext.anonymous()))
                .isInstanceOf(ToolFailureException.class)
                .extracting(ex -> ((ToolFailureException) ex).failure().code())
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(invoker.calls).isEmpty();
    }

    @Test
    void createBookingRequiresTheBearerTokenBeforeCalling() {
        RecordingInvoker invoker = new RecordingInvoker();
        BookingIntegrationService bookings = new BookingIntegrationService(new BookingServiceClient(invoker));

        assertThatThrownBy(() -> bookings.create(new ToolArguments(bookingArguments(true)), CallerContext.anonymous()))
                .isInstanceOf(ToolFailureException.class)
                .extracting(ex -> ((ToolFailureException) ex).failure().code())
                .isEqualTo("AUTHENTICATION_FAILED");
        assertThat(invoker.calls).isEmpty();
    }

    @Test
    void createBookingForwardsThePublishedBodyAfterConfirmation() {
        RecordingInvoker invoker = new RecordingInvoker();
        BookingIntegrationService bookings = new BookingIntegrationService(new BookingServiceClient(invoker));

        bookings.create(new ToolArguments(bookingArguments(true)), CALLER);

        assertThat(invoker.calls).containsExactly(new RecordingInvoker.Call(
                "create",
                Map.of(
                        "eventId", EVENT_ID,
                        "lines", List.of(Map.of("ticketTierId", TIER_ID, "quantity", 2))),
                "idem-1"));
    }

    @Test
    void initiatePaymentSendsTheBookingIdAndNoPaymentCredentials() {
        RecordingInvoker invoker = new RecordingInvoker();
        PaymentIntegrationService payments = new PaymentIntegrationService(new PaymentServiceClient(invoker));

        payments.initiate(new ToolArguments(Map.of(
                "bookingId", BOOKING_ID,
                "userConfirmed", true,
                "idempotencyKey", "p".repeat(255),
                "cardNumber", "4242424242424242",
                "cvv", "123")), CallerContext.anonymous());

        assertThat(invoker.calls).containsExactly(new RecordingInvoker.Call(
                "initiate",
                Map.of("bookingId", BOOKING_ID),
                "p".repeat(255)));
    }

    @Test
    void initiatePaymentRequiresAnIdempotencyKey() {
        RecordingInvoker invoker = new RecordingInvoker();
        PaymentIntegrationService payments = new PaymentIntegrationService(new PaymentServiceClient(invoker));

        assertThatThrownBy(() -> payments.initiate(new ToolArguments(Map.of(
                "bookingId", BOOKING_ID,
                "userConfirmed", true)), CallerContext.anonymous()))
                .isInstanceOf(ToolFailureException.class)
                .extracting(ex -> ((ToolFailureException) ex).failure().code())
                .isEqualTo("INVALID_ARGUMENTS");
        assertThat(invoker.calls).isEmpty();
    }

    private static Map<String, Object> bookingArguments(boolean confirmed) {
        return Map.of(
                "eventId", EVENT_ID,
                "lines", List.of(Map.of("ticketTierId", TIER_ID, "quantity", 2)),
                "userConfirmed", confirmed,
                "idempotencyKey", "idem-1");
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
