package com.envisione.progressiveskills.common.pack;

import java.util.Locale;

/** Explicit path-addressed patch operation. */
public enum PatchOperation {
    SET,
    REMOVE,
    APPEND,
    PREPEND,
    REPLACE_BY_ID;

    static PatchOperation parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown patch operation: " + value, exception);
        }
    }
}
