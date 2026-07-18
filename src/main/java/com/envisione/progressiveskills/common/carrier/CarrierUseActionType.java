package com.envisione.progressiveskills.common.carrier;

import java.util.Arrays;

public enum CarrierUseActionType {
    SKILL_XP("xp"),
    SKILL_LEVEL("level"),
    CURRENCY("currency"),
    TREE_RESPEC("tree_respec");

    private final String serializedName;

    CarrierUseActionType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CarrierUseActionType parse(String value) {
        if (value == null || value.length() > 32) {
            throw new IllegalArgumentException("Carrier action type is missing or exceeds its bound");
        }
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Core carrier action type " + value));
    }
}
