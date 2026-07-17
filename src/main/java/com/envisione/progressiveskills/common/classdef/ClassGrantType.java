package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Optional;

public enum ClassGrantType {
    ATTRIBUTE("attribute", "attribute", null, EntitlementResolver.ADDITIVE),
    ABILITY("ability", "ability", id("ability"), EntitlementResolver.BOOLEAN_UNION),
    SPELL("spell", "spell", id("spell"), EntitlementResolver.HIGHEST),
    STAGE("stage", "stage", id("stage"), EntitlementResolver.BOOLEAN_UNION),
    TREE_ACCESS("tree_access", "tree", id("tree_access"), EntitlementResolver.BOOLEAN_UNION),
    CLASS_ACCESS("class_access", "class", id("class_access"), EntitlementResolver.BOOLEAN_UNION);

    private final String serializedName;
    private final String targetField;
    private final ResourceLocation targetType;
    private final EntitlementResolver resolver;

    ClassGrantType(
            String serializedName,
            String targetField,
            ResourceLocation targetType,
            EntitlementResolver resolver
    ) {
        this.serializedName = serializedName;
        this.targetField = targetField;
        this.targetType = targetType;
        this.resolver = resolver;
    }

    public String serializedName() {
        return serializedName;
    }

    public String targetField() {
        return targetField;
    }

    public Optional<ResourceLocation> entitlementType() {
        return Optional.ofNullable(targetType);
    }

    public EntitlementResolver resolver() {
        return resolver;
    }

    public static ClassGrantType parse(String value) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown class grant type " + value));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }
}
