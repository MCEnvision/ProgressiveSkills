package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.transaction.EntitlementContribution;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import net.minecraft.resources.ResourceLocation;

public sealed interface AbilityPersistentEffect extends Comparable<AbilityPersistentEffect>
        permits AbilityAttributeEffect, AbilityFlagEffect {
    ResourceLocation id();

    AbilityEffectType type();

    ResourceLocation targetId();

    ResourceLocation targetType();

    long value();

    EntitlementResolver resolver();

    default EntitlementKey entitlementKey() {
        return new EntitlementKey(targetType(), targetId());
    }

    default EntitlementContribution contribution() {
        return new EntitlementContribution(value(), resolver());
    }

    default GrantSourceId source(ResourceLocation abilityId) {
        return new GrantSourceId(DefinitionKinds.ABILITY.id(), abilityId, id());
    }

    @Override
    default int compareTo(AbilityPersistentEffect other) {
        return id().compareNamespaced(other.id());
    }
}
