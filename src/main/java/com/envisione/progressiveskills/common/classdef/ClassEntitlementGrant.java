package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ClassEntitlementGrant(
        ResourceLocation id,
        ClassGrantType type,
        ResourceLocation target,
        long value
) implements ClassGrant {
    public ClassEntitlementGrant {
        id = StableId.requireValid(id);
        Objects.requireNonNull(type, "type");
        if (type == ClassGrantType.ATTRIBUTE || type == ClassGrantType.SPELL) {
            throw new IllegalArgumentException("This class grant type requires its specialized grant record");
        }
        target = StableId.requireValid(target);
        if (value != 1) {
            throw new IllegalArgumentException("Boolean class entitlement grant value must be 1");
        }
    }

    @Override
    public ResourceLocation targetId() {
        return target;
    }

    @Override
    public ResourceLocation targetType() {
        return type.entitlementType().orElseThrow();
    }

    @Override
    public EntitlementResolver resolver() {
        return type.resolver();
    }
}
