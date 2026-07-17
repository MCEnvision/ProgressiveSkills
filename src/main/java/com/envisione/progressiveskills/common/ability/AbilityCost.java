package com.envisione.progressiveskills.common.ability;

import net.minecraft.resources.ResourceLocation;

public sealed interface AbilityCost extends Comparable<AbilityCost>
        permits AbilityCurrencyCost, AbilityVanillaCost {
    ResourceLocation id();

    AbilityCostType type();

    long amount();

    @Override
    default int compareTo(AbilityCost other) {
        return id().compareNamespaced(other.id());
    }
}
