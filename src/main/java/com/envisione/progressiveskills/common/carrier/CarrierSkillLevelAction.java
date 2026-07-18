package com.envisione.progressiveskills.common.carrier;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.skill.SkillCurve;
import net.minecraft.resources.ResourceLocation;

public record CarrierSkillLevelAction(
        ResourceLocation id,
        ResourceLocation skill,
        int levels,
        int consume
) implements CarrierUseAction {
    public CarrierSkillLevelAction {
        id = StableId.requireValid(id);
        skill = StableId.requireValid(skill);
        if (levels < 1 || levels > SkillCurve.MAX_LEVEL_SPAN) {
            throw new IllegalArgumentException("Carrier skill level amount exceeds its bound");
        }
        CarrierUseAction.requireConsume(consume);
    }

    @Override
    public CarrierUseActionType type() {
        return CarrierUseActionType.SKILL_LEVEL;
    }
}
