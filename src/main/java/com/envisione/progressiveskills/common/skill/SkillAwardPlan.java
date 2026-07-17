package com.envisione.progressiveskills.common.skill;

import com.envisione.progressiveskills.common.transaction.CascadePlan;

import java.util.Objects;

public record SkillAwardPlan(
        CascadePlan cascade,
        SkillProgress before,
        SkillProgress after,
        long awardedUnits,
        long activeAwardUnits,
        long bankedAwardUnits,
        long currencyAwarded
) {
    public SkillAwardPlan {
        Objects.requireNonNull(cascade, "cascade");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }
}
