package com.envisione.progressiveskills.common.carrier;

import java.util.Arrays;

public enum CarrierRarity {
    COMMON("common"),
    UNCOMMON("uncommon"),
    RARE("rare"),
    EPIC("epic");

    private final String serializedName;

    CarrierRarity(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CarrierRarity parse(String value) {
        if (value == null || value.length() > 16) {
            throw new IllegalArgumentException("Carrier rarity is missing or exceeds its bound");
        }
        return Arrays.stream(values())
                .filter(rarity -> rarity.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown carrier rarity " + value));
    }
}
