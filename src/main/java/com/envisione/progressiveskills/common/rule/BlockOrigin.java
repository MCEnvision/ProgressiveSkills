package com.envisione.progressiveskills.common.rule;

import java.util.Arrays;

public enum BlockOrigin {
    NATURAL("natural"),
    CREATIVE_PLACED("creative_placed"),
    SURVIVAL_PLACED("survival_placed"),
    AUTOMATION_PLACED("automation_placed"),
    UNKNOWN("unknown");

    private final String serializedName;

    BlockOrigin(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static BlockOrigin parse(String value) {
        return Arrays.stream(values())
                .filter(origin -> origin.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown block origin " + value));
    }
}
