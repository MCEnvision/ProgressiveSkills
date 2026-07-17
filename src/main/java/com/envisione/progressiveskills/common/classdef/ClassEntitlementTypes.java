package com.envisione.progressiveskills.common.classdef;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class ClassEntitlementTypes {
    public static final ResourceLocation SELECTED = id("class_selected");
    public static final ResourceLocation ACTIVE = id("class_active");
    private static final Set<ResourceLocation> LOGICAL = Set.of(
            SELECTED,
            ACTIVE,
            ClassGrantType.ABILITY.entitlementType().orElseThrow(),
            ClassGrantType.SPELL.entitlementType().orElseThrow(),
            ClassGrantType.STAGE.entitlementType().orElseThrow(),
            ClassGrantType.TREE_ACCESS.entitlementType().orElseThrow(),
            ClassGrantType.CLASS_ACCESS.entitlementType().orElseThrow()
    );

    private ClassEntitlementTypes() {
    }

    public static boolean isLogical(ResourceLocation targetType) {
        return LOGICAL.contains(targetType);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }
}
