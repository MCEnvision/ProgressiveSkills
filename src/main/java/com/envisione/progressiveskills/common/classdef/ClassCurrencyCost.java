package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

public record ClassCurrencyCost(ResourceLocation currency, long amount) {
    public ClassCurrencyCost {
        currency = StableId.requireValid(currency);
        if (amount <= 0) {
            throw new IllegalArgumentException("Class currency cost must be positive");
        }
    }
}
