package com.envisione.progressiveskills.common.pack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Fail-closed typed accessors shared by the TOML manifest and definition compilers. */
final class TomlValues {
    private TomlValues() {}

    static Map<String, Object> object(Map<String, Object> parent, String key, boolean required) {
        Object value = parent.get(key);
        if (value == null && !required) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException(key + " must be a TOML table");
        }
        @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) raw;
        return result;
    }

    static String string(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (!(value instanceof String result)) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return result;
    }

    static Optional<String> optionalString(Map<String, Object> parent, String key) {
        return parent.containsKey(key) ? Optional.of(string(parent, key)) : Optional.empty();
    }

    static int integer(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (!(value instanceof Number number) || value instanceof Double || value instanceof Float) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        long result = number.longValue();
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " exceeds integer bounds");
        }
        return (int) result;
    }

    static int optionalInteger(Map<String, Object> parent, String key, int fallback) {
        return parent.containsKey(key) ? integer(parent, key) : fallback;
    }

    static boolean optionalBoolean(Map<String, Object> parent, String key, boolean fallback) {
        Object value = parent.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean result)) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return result;
    }

    static List<String> stringList(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(key + " must be a list of strings");
        }
        return list.stream().map(item -> {
            if (!(item instanceof String string)) {
                throw new IllegalArgumentException(key + " must contain only strings");
            }
            return string;
        }).toList();
    }

    static void rejectUnknown(Map<String, Object> values, Set<String> allowed, String context) {
        Objects.requireNonNull(values, "values");
        for (String field : values.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("Unknown " + context + " field: " + field);
            }
        }
    }
}
