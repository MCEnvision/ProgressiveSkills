package com.envisione.progressiveskills.common.ability;

import java.util.Arrays;
import java.util.Locale;

public enum AbilityActionType {
    MESSAGE,
    HEAL,
    VANILLA_EFFECT;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AbilityActionType parse(String value) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Core ability action type " + value));
    }
}
