package com.envisione.progressiveskills.common.rule;

import java.util.Locale;

public enum FakePlayerPolicy {
    DENY,
    ALLOW;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FakePlayerPolicy parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown fake player policy " + value, exception);
        }
    }
}
