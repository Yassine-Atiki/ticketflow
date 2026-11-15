package com.ticketflow.mcptoolserver.clients;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ResponseSanitizer {

    private static final Set<String> REDACTED_KEYS = Set.of(
            "cardnumber",
            "cvv",
            "cvc",
            "pan",
            "password",
            "secret",
            "clientsecret",
            "accesstoken",
            "refreshtoken",
            "securitycode",
            "authorization");

    private ResponseSanitizer() {
    }

    public static Object sanitize(Object value) {
        return switch (value) {
            case Map<?, ?> map -> sanitizeMap(map);
            case List<?> list -> sanitizeList(list);
            case null -> null;
            default -> value;
        };
    }

    private static Map<String, Object> sanitizeMap(Map<?, ?> source) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (REDACTED_KEYS.contains(normalize(key))) {
                sanitized.put(key, "[redacted]");
            } else {
                sanitized.put(key, sanitize(entry.getValue()));
            }
        }
        return sanitized;
    }

    private static List<Object> sanitizeList(List<?> source) {
        List<Object> sanitized = new ArrayList<>(source.size());
        for (Object item : source) {
            sanitized.add(sanitize(item));
        }
        return sanitized;
    }

    private static String normalize(String key) {
        return key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
