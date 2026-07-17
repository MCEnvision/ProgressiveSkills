package com.envisione.progressiveskills.common.ability;

import java.util.Arrays;
import java.util.Locale;

public enum AbilityCostType {
    CURRENCY,
    HUNGER,
    EXPERIENCE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AbilityCostType parse(String value) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Core ability cost type " + value));
    }
}
