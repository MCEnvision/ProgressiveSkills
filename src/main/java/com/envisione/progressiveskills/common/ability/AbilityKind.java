package com.envisione.progressiveskills.common.ability;

import java.util.Arrays;
import java.util.Locale;

public enum AbilityKind {
    PASSIVE,
    TOGGLE,
    ACTIVE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AbilityKind parse(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.serializedName().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Core ability kind " + value));
    }
}
