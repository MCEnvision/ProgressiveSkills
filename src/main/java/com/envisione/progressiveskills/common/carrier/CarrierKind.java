package com.envisione.progressiveskills.common.carrier;

import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;

public enum CarrierKind {
    TOME("tome"),
    TOKEN("token"),
    CHARM("charm"),
    CONSUMABLE("consumable"),
    ARTIFACT("artifact");

    private final String serializedName;
    private final ResourceLocation itemId;

    CarrierKind(String serializedName) {
        this.serializedName = serializedName;
        this.itemId = ResourceLocation.fromNamespaceAndPath("progressiveskills", serializedName);
    }

    public String serializedName() {
        return serializedName;
    }

    public ResourceLocation itemId() {
        return itemId;
    }

    public ResourceLocation id() {
        return itemId;
    }

    public static CarrierKind parse(String value) {
        if (value == null || value.length() > 64) {
            throw new IllegalArgumentException("Carrier kind is missing or exceeds its bound");
        }
        String normalized = value.startsWith("progressiveskills:")
                ? value.substring("progressiveskills:".length()) : value;
        return Arrays.stream(values())
                .filter(kind -> kind.serializedName.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Core carrier kind " + value));
    }
}
