package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record AbilityVanillaCost(
        ResourceLocation id,
        AbilityCostType type,
        long amount
) implements AbilityCost {
    public static final int MAX_HUNGER = 20;
    public static final int MAX_EXPERIENCE = 1_000_000;

    public AbilityVanillaCost {
        id = StableId.requireValid(id);
        Objects.requireNonNull(type, "type");
        if (type == AbilityCostType.CURRENCY) {
            throw new IllegalArgumentException("Named currency costs require AbilityCurrencyCost");
        }
        long maximum = type == AbilityCostType.HUNGER ? MAX_HUNGER : MAX_EXPERIENCE;
        if (amount < 1 || amount > maximum) {
            throw new IllegalArgumentException("Ability " + type.serializedName()
                    + " cost must be within 1 and " + maximum);
        }
    }
}
