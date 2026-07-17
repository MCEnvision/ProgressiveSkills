package com.envisione.progressiveskills.common.ability;

import net.minecraft.resources.ResourceLocation;

public sealed interface AbilityAction extends Comparable<AbilityAction>
        permits AbilityMessageAction, AbilityHealAction, AbilityVanillaEffectAction {
    ResourceLocation id();

    AbilityActionType type();

    @Override
    default int compareTo(AbilityAction other) {
        return id().compareNamespaced(other.id());
    }
}
