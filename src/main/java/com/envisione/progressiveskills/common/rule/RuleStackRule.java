package com.envisione.progressiveskills.common.rule;

import java.util.Locale;

public enum RuleStackRule {
    SUM,
    HIGHEST,
    FIRST,
    EXCLUSIVE,
    DIMINISHING;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RuleStackRule parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown rule stack policy " + value, exception);
        }
    }
}
