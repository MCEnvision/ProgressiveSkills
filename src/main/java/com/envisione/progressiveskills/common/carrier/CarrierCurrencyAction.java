package com.envisione.progressiveskills.common.carrier;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

public record CarrierCurrencyAction(
        ResourceLocation id,
        ResourceLocation currency,
        long amount,
        int consume
) implements CarrierUseAction {
    public static final long MAX_AMOUNT = 1_000_000_000_000L;

    public CarrierCurrencyAction {
        id = StableId.requireValid(id);
        currency = StableId.requireValid(currency);
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("Carrier currency amount exceeds its bound");
        }
        CarrierUseAction.requireConsume(consume);
    }

    @Override
    public CarrierUseActionType type() {
        return CarrierUseActionType.CURRENCY;
    }
}
