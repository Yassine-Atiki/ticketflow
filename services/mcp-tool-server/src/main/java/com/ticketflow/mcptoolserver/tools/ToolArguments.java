package com.ticketflow.mcptoolserver.tools;

import com.ticketflow.mcptoolserver.exceptions.ToolFailure;
import com.ticketflow.mcptoolserver.exceptions.ToolFailureException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Pattern;

public final class ToolArguments {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern CUSTOMER_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final int MAX_TEXT = 200;

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

    public int requiredQuantity(String name) {
        return boundedInt(name, 1, 50);
    }

    public Integer optionalQuantity(String name) {
        if (!arguments.containsKey(name) || arguments.get(name) == null) {
            return null;
        }
        return boundedInt(name, 1, 50);
    }

    public Integer optionalLimit(String name) {
        if (!arguments.containsKey(name) || arguments.get(name) == null) {
            return null;
        }
        return boundedInt(name, 1, 100);
    }

    public String optionalIdempotencyKey() {
        return optionalId("idempotencyKey");
    }

    public String optionalReason() {
        String value = optionalText("reason");
        if (value != null && value.length() > MAX_TEXT) {
            throw new ToolFailureException(ToolFailure.invalid("reason is longer than " + MAX_TEXT + " characters."));
        }
        return value;
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

    private static String identifier(String name, String value) {
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new ToolFailureException(ToolFailure.invalid(
                    name + " must contain only letters, digits, underscores and hyphens."));
        }
        return value;
    }
}
