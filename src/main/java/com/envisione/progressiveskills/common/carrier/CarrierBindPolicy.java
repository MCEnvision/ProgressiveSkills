package com.envisione.progressiveskills.common.carrier;

import java.util.Arrays;

public enum CarrierBindPolicy {
    NONE("none"),
    ON_PICKUP("on_pickup"),
    ON_USE("on_use"),
    ON_CRAFT("on_craft");

    private final String serializedName;

    CarrierBindPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CarrierBindPolicy parse(String value) {
        if (value == null || value.length() > 32) {
            throw new IllegalArgumentException("Carrier bind policy is missing or exceeds its bound");
        }
        return Arrays.stream(values())
                .filter(policy -> policy.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown carrier bind policy " + value));
    }
}
