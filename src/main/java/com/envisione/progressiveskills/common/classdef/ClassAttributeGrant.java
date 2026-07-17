package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ClassAttributeGrant(
        ResourceLocation id,
        ResourceLocation attribute,
        AttributeOperation operation,
        long valueUnits
) implements ClassGrant {
    public ClassAttributeGrant {
        id = StableId.requireValid(id);
        attribute = StableId.requireValid(attribute);
        Objects.requireNonNull(operation, "operation");
        if (valueUnits == 0) {
            throw new IllegalArgumentException("Class attribute grant value must not be zero");
        }
    }

    @Override
    public ClassGrantType type() {
        return ClassGrantType.ATTRIBUTE;
    }

    @Override
    public ResourceLocation targetId() {
        return attribute;
    }

    @Override
    public ResourceLocation targetType() {
        return operation.targetType();
    }

    @Override
    public long value() {
        return valueUnits;
    }

    @Override
    public EntitlementResolver resolver() {
        return EntitlementResolver.ADDITIVE;
    }
}
