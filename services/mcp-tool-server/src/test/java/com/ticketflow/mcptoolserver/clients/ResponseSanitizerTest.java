package com.ticketflow.mcptoolserver.clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResponseSanitizerTest {

    @Test
    void redactsPaymentCredentialsAndKeepsTheProfileFields() {
        Object sanitized = ResponseSanitizer.sanitize(Map.of(
                "id", 7,
                "email", "sara@example.com",
                "cardNumber", "4242424242424242",
                "nested", Map.of("cvv", "123"),
                "items", List.of(Map.of("access_token", "secret-token"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) sanitized;
        assertThat(body).containsEntry("email", "sara@example.com");
        assertThat(body).containsEntry("cardNumber", "[redacted]");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) body.get("nested");
        assertThat(nested).containsEntry("cvv", "[redacted]");
    }

    @Test
    void redactsTicketControlCode() {
        Object sanitized = ResponseSanitizer.sanitize(Map.of(
                "ticketId", "7d09ea60-5ba3-4a39-a13b-d5b15055f39c",
                "status", "ISSUED",
                "controlCode", "tkt_4mQ8vZ2pL9xN6cR3sT7wB5hK1aE0dFyu"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) sanitized;
        assertThat(body).containsEntry("status", "ISSUED");
        assertThat(body).containsEntry("controlCode", "[redacted]");
    }
}
