package com.envisione.progressiveskills.common.ability;

import java.util.Arrays;
import java.util.Locale;

public enum AbilityTargetMode {
    SELF,
    ENTITY,
    BLOCK;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AbilityTargetMode parse(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.serializedName().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Core ability target mode " + value));
    }
}
