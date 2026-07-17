package com.envisione.progressiveskills.common.rule;

import java.util.Locale;

public enum RuleMultiplierMode {
    ADD,
    MULTIPLY,
    HIGHEST,
    LOWEST,
    REPLACE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RuleMultiplierMode parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown rule multiplier mode " + value, exception);
        }
    }
}
