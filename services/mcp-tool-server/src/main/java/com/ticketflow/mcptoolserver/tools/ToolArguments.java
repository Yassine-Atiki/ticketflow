package com.ticketflow.mcptoolserver.tools;

import com.ticketflow.mcptoolserver.exceptions.ToolFailure;
import com.ticketflow.mcptoolserver.exceptions.ToolFailureException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ToolArguments {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,99}");
    private static final int BOOKING_IDEMPOTENCY_LIMIT = 100;
    private static final int PAYMENT_IDEMPOTENCY_LIMIT = 255;
    private static final Pattern CUSTOMER_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Set<String> TICKET_STATUSES = Set.of("ISSUED", "CANCELLED");
    private static final Set<String> BOOKING_STATUSES =
            Set.of("PENDING_PAYMENT", "CONFIRMED", "EXPIRED", "CANCELLED");
    private static final Set<String> PREFERENCE_LANGUAGES = Set.of("fr", "en", "ar");
    private static final Set<String> NOTIFICATION_CHANNELS = Set.of("EMAIL", "TELEGRAM");
    private static final int MAX_TEXT = 200;
    private static final int MAX_REASON = 255;

    private final Map<String, Object> arguments;

    public ToolArguments(Map<String, Object> arguments) {
        this.arguments = arguments == null ? Map.of() : arguments;
    }

    public void requireConfirmation(String operation) {
        Object value = arguments.get("userConfirmed");
        if (!(value instanceof Boolean confirmed) || !confirmed) {
            throw new ToolFailureException(ToolFailure.confirmationRequired(operation));
        }
    }

    public String requiredId(String name) {
        return identifier(name, requiredText(name));
    }

    public String requiredUuid(String name) {
        String value = readText(name, true, 36);
        if (!UUID.matcher(value).matches()) {
            throw new ToolFailureException(ToolFailure.invalid(name + " must be a UUID."));
        }
        return value;
    }

    public String optionalId(String name) {
        String value = optionalText(name);
        return value == null ? null : identifier(name, value);
    }

    public String requiredCustomerId(String name) {
        String value = requiredText(name);
        if (!CUSTOMER_ID.matcher(value).matches()) {
            throw new ToolFailureException(ToolFailure.invalid(
                    name + " must be the positive numeric id published by Customer Service."));
        }
        return value;
    }

    public String optionalDate(String name) {
        String value = optionalText(name);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeException ex) {
            throw new ToolFailureException(ToolFailure.invalid(
                    name + " must be a calendar date in YYYY-MM-DD form."));
        }
    }

    public String optionalText(String name) {
        Object value = arguments.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new ToolFailureException(ToolFailure.invalid(name + " must be text."));
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_TEXT) {
            throw new ToolFailureException(ToolFailure.invalid(name + " is longer than " + MAX_TEXT + " characters."));
        }
        return trimmed;
    }

    public String requiredText(String name) {
        String value = optionalText(name);
        if (value == null) {
            throw new ToolFailureException(ToolFailure.invalid(name + " is required."));
        }
        return value;
    }

    public int requiredQuantity(String name, int minimum, int maximum) {
        return boundedInt(name, minimum, maximum);
    }

    public Integer optionalInt(String name, int minimum, int maximum) {
        if (!arguments.containsKey(name) || arguments.get(name) == null) {
            return null;
        }
        return boundedInt(name, minimum, maximum);
    }

    public String requiredIdempotencyKey() {
        String value = readText("idempotencyKey", true, BOOKING_IDEMPOTENCY_LIMIT);
        if (!IDEMPOTENCY_KEY.matcher(value).matches()) {
            throw new ToolFailureException(ToolFailure.invalid(
                    "idempotencyKey must contain only letters, digits, underscores and hyphens, with at most 100 characters."));
        }
        return value;
    }

    public String optionalIdempotencyKey() {
        String value = readText("idempotencyKey", false, BOOKING_IDEMPOTENCY_LIMIT);
        if (value == null) {
            return null;
        }
        if (!IDEMPOTENCY_KEY.matcher(value).matches()) {
            throw new ToolFailureException(ToolFailure.invalid(
                    "idempotencyKey must contain only letters, digits, underscores and hyphens, with at most 100 characters."));
        }
        return value;
    }

    public String requiredPaymentIdempotencyKey() {
        return readText("idempotencyKey", true, PAYMENT_IDEMPOTENCY_LIMIT);
    }

    public String optionalTicketStatus() {
        String value = optionalText("status");
        if (value == null) {
            return null;
        }
        if (!TICKET_STATUSES.contains(value)) {
            throw new ToolFailureException(ToolFailure.invalid("status must be ISSUED or CANCELLED."));
        }
        return value;
    }

    public String optionalBookingStatus() {
        String value = optionalText("status");
        if (value == null) {
            return null;
        }
        if (!BOOKING_STATUSES.contains(value)) {
            throw new ToolFailureException(ToolFailure.invalid(
                    "status must be PENDING_PAYMENT, CONFIRMED, EXPIRED or CANCELLED."));
        }
        return value;
    }

    public String requiredPreferenceLanguage() {
        String value = requiredText("language");
        if (!PREFERENCE_LANGUAGES.contains(value)) {
            throw new ToolFailureException(ToolFailure.invalid("language must be fr, en or ar."));
        }
        return value;
    }

    public String requiredNotificationChannel() {
        String value = requiredText("notificationChannel");
        if (!NOTIFICATION_CHANNELS.contains(value)) {
            throw new ToolFailureException(ToolFailure.invalid(
                    "notificationChannel must be EMAIL or TELEGRAM."));
        }
        return value;
    }

    public Boolean optionalBoolean(String name) {
        if (!arguments.containsKey(name) || arguments.get(name) == null) {
            return null;
        }
        Object value = arguments.get(name);
        if (!(value instanceof Boolean flag)) {
            throw new ToolFailureException(ToolFailure.invalid(name + " must be a boolean."));
        }
        return flag;
    }

    public List<Map<String, Object>> requiredBookingLines() {
        Object value = arguments.get("lines");
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new ToolFailureException(ToolFailure.invalid("lines must contain at least one ticket tier."));
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new ToolFailureException(ToolFailure.invalid(
                        "each booking line must be an object with ticketTierId and quantity."));
            }
            Object tier = map.get("ticketTierId");
            if (!(tier instanceof String ticketTierId) || !UUID.matcher(ticketTierId).matches()) {
                throw new ToolFailureException(ToolFailure.invalid("ticketTierId must be a UUID."));
            }
            int quantity = lineQuantity(map.get("quantity"));
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("ticketTierId", ticketTierId);
            line.put("quantity", quantity);
            lines.add(line);
        }
        return lines;
    }

    public String optionalReason() {
        Object value = arguments.get("reason");
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new ToolFailureException(ToolFailure.invalid("reason must be text."));
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_REASON) {
            throw new ToolFailureException(ToolFailure.invalid("reason is longer than " + MAX_REASON + " characters."));
        }
        return trimmed;
    }

    private int lineQuantity(Object value) {
        long quantity;
        if (value instanceof Integer integer) {
            quantity = integer.longValue();
        } else if (value instanceof Long number) {
            quantity = number;
        } else {
            throw new ToolFailureException(ToolFailure.invalid("quantity must be an integer."));
        }
        if (quantity < 1 || quantity > 10) {
            throw new ToolFailureException(ToolFailure.invalid("quantity must be between 1 and 10."));
        }
        return (int) quantity;
    }

    private int boundedInt(String name, int minimum, int maximum) {
        int value = requiredInt(name);
        if (value < minimum || value > maximum) {
            throw new ToolFailureException(ToolFailure.invalid(
                    name + " must be between " + minimum + " and " + maximum + "."));
        }
        return value;
    }

    private int requiredInt(String name) {
        Object value = arguments.get(name);
        if (value == null) {
            throw new ToolFailureException(ToolFailure.invalid(name + " is required."));
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Long number) {
            if (number > Integer.MAX_VALUE || number < Integer.MIN_VALUE) {
                throw new ToolFailureException(ToolFailure.invalid(name + " must be an integer."));
            }
            return number.intValue();
        }
        if (value instanceof Double number) {
            if (number % 1 != 0) {
                throw new ToolFailureException(ToolFailure.invalid(name + " must be an integer."));
            }
            return number.intValue();
        }
        throw new ToolFailureException(ToolFailure.invalid(name + " must be an integer."));
    }

    private String readText(String name, boolean required, int maximum) {
        Object value = arguments.get(name);
        if (value == null) {
            if (required) {
                throw new ToolFailureException(ToolFailure.invalid(name + " is required."));
            }
            return null;
        }
        if (!(value instanceof String text)) {
            throw new ToolFailureException(ToolFailure.invalid(name + " must be text."));
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            if (required) {
                throw new ToolFailureException(ToolFailure.invalid(name + " is required."));
            }
            return null;
        }
        if (trimmed.length() > maximum) {
            throw new ToolFailureException(ToolFailure.invalid(name + " is longer than " + maximum + " characters."));
        }
        return trimmed;
    }

    private static String identifier(String name, String value) {
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new ToolFailureException(ToolFailure.invalid(
                    name + " must contain only letters, digits, underscores and hyphens."));
        }
        return value;
    }
}
