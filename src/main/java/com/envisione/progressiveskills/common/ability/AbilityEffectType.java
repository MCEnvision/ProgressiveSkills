package com.envisione.progressiveskills.common.ability;

import java.util.Arrays;
import java.util.Locale;

public enum AbilityEffectType {
    ATTRIBUTE,
    FLAG;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AbilityEffectType parse(String value) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Core ability persistent effect type " + value));
    }
}
