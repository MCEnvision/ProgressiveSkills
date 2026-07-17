package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

public record AbilityVanillaEffectAction(
        ResourceLocation id,
        ResourceLocation effect,
        int amplifier,
        int durationTicks,
        boolean ambient,
        boolean showParticles,
        boolean showIcon
) implements AbilityAction {
    public static final int MAX_AMPLIFIER = 255;
    public static final int MAX_DURATION_TICKS = 72_000;

    public AbilityVanillaEffectAction {
        id = StableId.requireValid(id);
        effect = StableId.requireValid(effect);
        if (amplifier < 0 || amplifier > MAX_AMPLIFIER) {
            throw new IllegalArgumentException("Ability effect amplifier must be within 0 and " + MAX_AMPLIFIER);
        }
        if (durationTicks < 1 || durationTicks > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("Ability effect duration must be within 1 and " + MAX_DURATION_TICKS);
        }
    }

    @Override
    public AbilityActionType type() {
        return AbilityActionType.VANILLA_EFFECT;
    }
}
