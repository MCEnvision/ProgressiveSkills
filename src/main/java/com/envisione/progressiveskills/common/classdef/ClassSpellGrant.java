package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ClassSpellGrant(
        ResourceLocation id,
        ResourceLocation spell,
        int level,
        ClassSpellLearningPolicy learningPolicy
) implements ClassGrant {
    public static final int MAX_LEVEL = 255;

    public ClassSpellGrant {
        id = StableId.requireValid(id);
        spell = StableId.requireValid(spell);
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Class spell level must be within 1 and " + MAX_LEVEL);
        }
        Objects.requireNonNull(learningPolicy, "learningPolicy");
    }

    @Override
    public ClassGrantType type() {
        return ClassGrantType.SPELL;
    }

    @Override
    public ResourceLocation targetId() {
        return spell;
    }

    @Override
    public ResourceLocation targetType() {
        return ClassGrantType.SPELL.entitlementType().orElseThrow();
    }

    @Override
    public long value() {
        return level;
    }

    @Override
    public EntitlementResolver resolver() {
        return EntitlementResolver.HIGHEST;
    }
}
