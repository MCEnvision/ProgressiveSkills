package com.envisione.progressiveskills.common.ability;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class AbilityEntitlementTypes {
    public static final ResourceLocation OWNED = id("ability");
    public static final ResourceLocation SLOT_ASSIGNMENT = id("ability_slot_assignment");
    public static final ResourceLocation SELECTED_SLOT = id("ability_selected_slot");
    public static final ResourceLocation TOGGLE_STATE = id("ability_toggle_state");
    private static final Set<ResourceLocation> LOGICAL = Set.of(
            OWNED,
            SLOT_ASSIGNMENT,
            SELECTED_SLOT,
            TOGGLE_STATE,
            AbilityFlagEffect.FLAG_ENTITLEMENT_TYPE
    );

    private AbilityEntitlementTypes() {
    }

    public static boolean isLogical(ResourceLocation targetType) {
        return LOGICAL.contains(targetType);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }
}
