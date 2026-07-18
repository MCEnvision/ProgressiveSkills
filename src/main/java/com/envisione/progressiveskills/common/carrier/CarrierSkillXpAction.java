package com.envisione.progressiveskills.common.carrier;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

public record CarrierSkillXpAction(
        ResourceLocation id,
        ResourceLocation skill,
        long amountUnits,
        int consume
) implements CarrierUseAction {
    public static final long MAX_AMOUNT_UNITS = 1_000_000_000_000_000L;

    public CarrierSkillXpAction {
        id = StableId.requireValid(id);
        skill = StableId.requireValid(skill);
        if (amountUnits < 1 || amountUnits > MAX_AMOUNT_UNITS) {
            throw new IllegalArgumentException("Carrier skill XP amount exceeds its bound");
        }
        CarrierUseAction.requireConsume(consume);
    }

    @Override
    public CarrierUseActionType type() {
        return CarrierUseActionType.SKILL_XP;
    }
}
