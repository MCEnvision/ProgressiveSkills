package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record AbilityAttributeEffect(
        ResourceLocation id,
        ResourceLocation attribute,
        AttributeOperation operation,
        long valueUnits
) implements AbilityPersistentEffect {
    public AbilityAttributeEffect {
        id = StableId.requireValid(id);
        attribute = StableId.requireValid(attribute);
        Objects.requireNonNull(operation, "operation");
        if (valueUnits == 0) {
            throw new IllegalArgumentException("Ability attribute effect value must not be zero");
        }
    }

    @Override
    public AbilityEffectType type() {
        return AbilityEffectType.ATTRIBUTE;
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
