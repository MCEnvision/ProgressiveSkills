package com.envisione.progressiveskills.common.ability;

import java.util.Objects;

public record AbilityTargeting(
        AbilityTargetMode mode,
        int range,
        boolean lineOfSight
) {
    public static final int MAX_RANGE = 64;
    public static final AbilityTargeting SELF = new AbilityTargeting(AbilityTargetMode.SELF, 0, false);

    public AbilityTargeting {
        Objects.requireNonNull(mode, "mode");
        if (mode == AbilityTargetMode.SELF) {
            if (range != 0 || lineOfSight) {
                throw new IllegalArgumentException("Self targeting must use zero range without line of sight");
            }
        } else if (range < 1 || range > MAX_RANGE) {
            throw new IllegalArgumentException("Entity and block targeting range must be within 1 and " + MAX_RANGE);
        }
    }
}
