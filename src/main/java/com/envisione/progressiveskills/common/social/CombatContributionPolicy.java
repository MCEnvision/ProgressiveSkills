package com.envisione.progressiveskills.common.social;

import java.math.BigInteger;

public record CombatContributionPolicy(
        long xpUnitsPerDamage,
        long maximumAwardUnits,
        int assistWindowTicks,
        long minimumDamageMilli,
        boolean pvpAwardsEnabled,
        int pvpPairCooldownTicks,
        long pvpPairDailyCapUnits,
        int pvpRepeatMultiplierBasisPoints,
        int pvpFreeLevelGap,
        int pvpLevelPenaltyBasisPoints,
        int pvpMinimumMultiplierBasisPoints
) {
    public CombatContributionPolicy {
        if (xpUnitsPerDamage < 0 || maximumAwardUnits < 0
                || assistWindowTicks < 1 || assistWindowTicks > 12_000
                || minimumDamageMilli < 1 || minimumDamageMilli > 1_000_000_000L
                || pvpPairCooldownTicks < 0 || pvpPairCooldownTicks > 1_728_000
                || pvpPairDailyCapUnits < 0
                || pvpRepeatMultiplierBasisPoints < 0 || pvpRepeatMultiplierBasisPoints > 10_000
                || pvpFreeLevelGap < 0 || pvpFreeLevelGap > 1_000_000
                || pvpLevelPenaltyBasisPoints < 0 || pvpLevelPenaltyBasisPoints > 10_000
                || pvpMinimumMultiplierBasisPoints < 0 || pvpMinimumMultiplierBasisPoints > 10_000) {
            throw new IllegalArgumentException("Combat contribution policy is invalid");
        }
    }

    public long awardForDamage(long damageMilli) {
        if (damageMilli < 0) {
            throw new IllegalArgumentException("Combat contribution damage must not be negative");
        }
        long calculated = BigInteger.valueOf(damageMilli)
                .multiply(BigInteger.valueOf(xpUnitsPerDamage))
                .divide(BigInteger.valueOf(1_000L))
                .min(BigInteger.valueOf(maximumAwardUnits))
                .longValueExact();
        return Math.max(0L, calculated);
    }

    public long applyPvpLevelScaling(long requestedUnits, int attackerLevel, int victimLevel) {
        if (requestedUnits < 0 || attackerLevel < 0 || victimLevel < 0) {
            throw new IllegalArgumentException("Pvp award input is invalid");
        }
        int penalizedGap = Math.max(0, attackerLevel - victimLevel - pvpFreeLevelGap);
        long reduction = Math.min(10_000L,
                Math.multiplyExact((long) penalizedGap, pvpLevelPenaltyBasisPoints));
        long multiplier = Math.max(pvpMinimumMultiplierBasisPoints, 10_000L - reduction);
        return BigInteger.valueOf(requestedUnits).multiply(BigInteger.valueOf(multiplier))
                .divide(BigInteger.valueOf(10_000L)).longValueExact();
    }

    public long cooldownMillis() {
        return Math.multiplyExact((long) pvpPairCooldownTicks, 50L);
    }
}
