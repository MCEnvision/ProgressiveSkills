package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import net.minecraft.resources.ResourceLocation;

public record AbilityHealAction(
        ResourceLocation id,
        long amountUnits
) implements AbilityAction {
    public static final long MAX_AMOUNT_UNITS = 2_048L * FixedPoint.SCALE;

    public AbilityHealAction {
        id = StableId.requireValid(id);
        if (amountUnits < 1 || amountUnits > MAX_AMOUNT_UNITS) {
            throw new IllegalArgumentException("Ability heal amount must be within one fixed point unit and 2048");
        }
    }

    @Override
    public AbilityActionType type() {
        return AbilityActionType.HEAL;
    }
}
