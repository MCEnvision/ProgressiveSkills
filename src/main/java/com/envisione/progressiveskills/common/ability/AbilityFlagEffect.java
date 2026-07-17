package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import net.minecraft.resources.ResourceLocation;

public record AbilityFlagEffect(
        ResourceLocation id,
        ResourceLocation flag,
        boolean enabled
) implements AbilityPersistentEffect {
    public static final ResourceLocation FLAG_ENTITLEMENT_TYPE = ResourceLocation.fromNamespaceAndPath(
            "progressiveskills", "flag"
    );

    public AbilityFlagEffect {
        id = StableId.requireValid(id);
        flag = StableId.requireValid(flag);
    }

    @Override
    public AbilityEffectType type() {
        return AbilityEffectType.FLAG;
    }

    @Override
    public ResourceLocation targetId() {
        return flag;
    }

    @Override
    public ResourceLocation targetType() {
        return FLAG_ENTITLEMENT_TYPE;
    }

    @Override
    public long value() {
        return enabled ? 1 : 0;
    }

    @Override
    public EntitlementResolver resolver() {
        return EntitlementResolver.HIGHEST;
    }
}
