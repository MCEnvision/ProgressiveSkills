package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.transaction.EntitlementContribution;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import net.minecraft.resources.ResourceLocation;

public sealed interface ClassGrant extends Comparable<ClassGrant>
        permits ClassAttributeGrant, ClassEntitlementGrant, ClassSpellGrant {
    ResourceLocation id();

    ClassGrantType type();

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

    default GrantSourceId source(ResourceLocation ownerKind, ResourceLocation ownerId) {
        return new GrantSourceId(ownerKind, ownerId, id());
    }

    @Override
    default int compareTo(ClassGrant other) {
        return id().compareNamespaced(other.id());
    }
}
