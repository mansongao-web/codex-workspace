package com.example.legacyai.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonMaps {
    private JsonMaps() {
    }

    public static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value.toString();
    }

    public static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static double decimal(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> stringMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?> input)) {
            return new LinkedHashMap<>();
        }
        Map<String, String> output = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                output.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return output;
    }

    public static List<String> stringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> input)) {
            return List.of();
        }
        List<String> output = new ArrayList<>();
        for (Object item : input) {
            if (item != null) {
                output.add(item.toString());
            }
        }
        return output;
    }

    public static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> input)) {
            throw new IllegalArgumentException("JSON body must be an object");
        }
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() != null) {
                output.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return output;
    }
}
