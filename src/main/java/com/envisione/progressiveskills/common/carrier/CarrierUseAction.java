package com.envisione.progressiveskills.common.carrier;

import net.minecraft.resources.ResourceLocation;

public sealed interface CarrierUseAction permits CarrierSkillXpAction, CarrierSkillLevelAction,
        CarrierCurrencyAction, CarrierTreeRespecAction {
    int MAX_CONSUME = 64;

    ResourceLocation id();

    CarrierUseActionType type();

    int consume();

    static void requireConsume(int consume) {
        if (consume < 0 || consume > MAX_CONSUME) {
            throw new IllegalArgumentException("Carrier action consume count exceeds its bound");
        }
    }
}
