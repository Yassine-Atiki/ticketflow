package com.ticketflow.mcptoolserver.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolSchemas {

    private ToolSchemas() {
    }

    public static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        if (required != null && !required.isEmpty()) {
            schema.put("required", List.copyOf(required));
        }
        return schema;
    }

    public static Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    public static Map<String, Object> stringProperty(String description, String pattern) {
        return Map.of("type", "string", "description", description, "pattern", pattern);
    }

    public static Map<String, Object> integerProperty(String description, int minimum, int maximum) {
        return Map.of(
                "type", "integer",
                "description", description,
                "minimum", minimum,
                "maximum", maximum);
    }

    public static Map<String, Object> booleanProperty(String description) {
        return Map.of("type", "boolean", "description", description);
    }

    public static Map<String, Object> properties(Object... keysAndValues) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            properties.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return properties;
    }
}
