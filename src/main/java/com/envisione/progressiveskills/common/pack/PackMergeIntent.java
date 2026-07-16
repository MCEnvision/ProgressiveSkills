package com.envisione.progressiveskills.common.pack;

import java.util.Locale;

/** Explicit collision behavior for one layered definition source. */
public enum PackMergeIntent {
    ADD,
    REPLACE,
    MERGE,
    PATCH,
    DISABLE;

    public static PackMergeIntent parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown merge_intent: " + value, exception);
        }
    }
}
