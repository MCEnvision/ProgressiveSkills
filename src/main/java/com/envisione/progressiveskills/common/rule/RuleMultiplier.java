package com.envisione.progressiveskills.common.rule;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record RuleMultiplier(
        ResourceLocation id,
        RuleMultiplierStage stage,
        ResourceLocation group,
        RuleMultiplierMode mode,
        long valueUnits,
        int priority
) {
    public static final long MAX_FACTOR_UNITS = 16L * FixedPoint.SCALE;

    public RuleMultiplier {
        id = StableId.requireValid(id);
        Objects.requireNonNull(stage, "stage");
        group = StableId.requireValid(group);
        Objects.requireNonNull(mode, "mode");
        if (mode == RuleMultiplierMode.ADD) {
            if (valueUnits <= -FixedPoint.SCALE || valueUnits > MAX_FACTOR_UNITS) {
                throw new IllegalArgumentException("Additive multiplier must remain above negative one");
            }
        } else if (valueUnits <= 0 || valueUnits > MAX_FACTOR_UNITS) {
            throw new IllegalArgumentException("Rule multiplier factor is outside its bound");
        }
    }
}
