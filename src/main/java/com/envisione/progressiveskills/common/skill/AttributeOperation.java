package com.envisione.progressiveskills.common.skill;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public enum AttributeOperation {
    ADD_VALUE,
    ADD_MULTIPLIED_BASE,
    ADD_MULTIPLIED_TOTAL;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ResourceLocation targetType() {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", "attribute_" + serializedName());
    }

    public static AttributeOperation parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown attribute operation " + value, exception);
        }
    }
}
