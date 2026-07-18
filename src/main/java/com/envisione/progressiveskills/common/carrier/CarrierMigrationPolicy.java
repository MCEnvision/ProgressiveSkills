package com.envisione.progressiveskills.common.carrier;

import java.util.Arrays;

public enum CarrierMigrationPolicy {
    KEEP_PINNED("keep_pinned"),
    MIGRATE("migrate"),
    WARN("warn"),
    INVALIDATE("invalidate"),
    ACCEPT_LIVE("accept_live");

    private final String serializedName;

    CarrierMigrationPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CarrierMigrationPolicy parse(String value) {
        if (value == null || value.length() > 32) {
            throw new IllegalArgumentException("Carrier migration policy is missing or exceeds its bound");
        }
        return Arrays.stream(values())
                .filter(policy -> policy.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown carrier migration policy " + value));
    }
}
