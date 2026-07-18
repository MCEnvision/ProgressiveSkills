package com.envisione.progressiveskills.common.social;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatContributionPolicyTest {
    @Test
    void damageAndLevelScalingStayBounded() {
        CombatContributionPolicy policy = policy();

        assertEquals(250L, policy.awardForDamage(10_000L));
        assertEquals(1_000L, policy.awardForDamage(1_000_000L));
        assertEquals(1_000L, policy.applyPvpLevelScaling(1_000L, 10, 10));
        assertEquals(500L, policy.applyPvpLevelScaling(1_000L, 20, 5));
        assertEquals(100L, policy.applyPvpLevelScaling(1_000L, 100, 1));
    }

    private static CombatContributionPolicy policy() {
        return new CombatContributionPolicy(
                25L, 1_000L, 200, 1_000L, true,
                1_200, 2_500L, 2_500, 5, 500, 1_000);
    }
}
