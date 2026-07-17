package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

public record AbilityCurrencyCost(
        ResourceLocation id,
        ResourceLocation currency,
        long amount
) implements AbilityCost {
    public static final long MAX_AMOUNT = 1_000_000_000_000L;

    public AbilityCurrencyCost {
        id = StableId.requireValid(id);
        currency = StableId.requireValid(currency);
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("Ability currency cost must be within 1 and " + MAX_AMOUNT);
        }
    }

    @Override
    public AbilityCostType type() {
        return AbilityCostType.CURRENCY;
    }
}
