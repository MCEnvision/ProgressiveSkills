package com.envisione.progressiveskills.common.skill;

import java.util.Locale;

public enum CurveType {
    FLAT,
    LINEAR,
    POLYNOMIAL,
    EXPONENTIAL,
    CUSTOM_TABLE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CurveType parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown curve type " + value, exception);
        }
    }
}
