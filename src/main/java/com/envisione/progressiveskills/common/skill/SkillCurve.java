package com.envisione.progressiveskills.common.skill;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

public final class SkillCurve {
    public static final int MAX_LEVEL_SPAN = 10_000;
    public static final int MAX_POLYNOMIAL_POWER = 32;

    private final CurveType type;
    private final CurveRounding rounding;
    private final int minLevel;
    private final int maxLevel;
    private final List<Long> costs;
    private final long[] cumulativeUnits;

    private SkillCurve(
            CurveType type,
            CurveRounding rounding,
            int minLevel,
            int maxLevel,
            List<Long> costs
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.rounding = Objects.requireNonNull(rounding, "rounding");
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        validateBounds(minLevel, maxLevel);
        if (costs.size() != maxLevel - minLevel) {
            throw new IllegalArgumentException("Curve cost count must equal the level span");
        }
        this.costs = List.copyOf(costs);
        this.cumulativeUnits = new long[costs.size() + 1];
        long previous = 0;
        for (int index = 0; index < costs.size(); index++) {
            long cost = costs.get(index);
            if (cost < 1) {
                throw new IllegalArgumentException("Curve cost must be at least one at level " + (minLevel + index));
            }
            if (index > 0 && cost < previous) {
                throw new IllegalArgumentException("Curve costs must be nondecreasing at level " + (minLevel + index));
            }
            previous = cost;
            cumulativeUnits[index + 1] = Math.addExact(
                    cumulativeUnits[index], FixedPoint.wholeXpToUnits(cost)
            );
        }
    }

    public static SkillCurve flat(
            int minLevel,
            int maxLevel,
            CurveRounding rounding,
            BigDecimal base
    ) {
        return generated(CurveType.FLAT, minLevel, maxLevel, rounding, ignored -> base);
    }

    public static SkillCurve linear(
            int minLevel,
            int maxLevel,
            CurveRounding rounding,
            BigDecimal base,
            BigDecimal step
    ) {
        return generated(CurveType.LINEAR, minLevel, maxLevel, rounding,
                index -> base.add(step.multiply(BigDecimal.valueOf(index))));
    }

    public static SkillCurve polynomial(
            int minLevel,
            int maxLevel,
            CurveRounding rounding,
            BigDecimal base,
            BigDecimal coefficient,
            int power
    ) {
        if (power < 0 || power > MAX_POLYNOMIAL_POWER) {
            throw new IllegalArgumentException("Polynomial power must be between zero and " + MAX_POLYNOMIAL_POWER);
        }
        return generated(CurveType.POLYNOMIAL, minLevel, maxLevel, rounding, index ->
                base.add(coefficient.multiply(BigDecimal.valueOf(index).pow(power))));
    }

    public static SkillCurve exponential(
            int minLevel,
            int maxLevel,
            CurveRounding rounding,
            BigDecimal base,
            BigDecimal factor
    ) {
        if (factor.signum() <= 0) {
            throw new IllegalArgumentException("Exponential factor must be positive");
        }
        validateBounds(minLevel, maxLevel);
        var raw = new ArrayList<BigDecimal>(maxLevel - minLevel);
        BigDecimal current = base;
        BigDecimal overflowLimit = BigDecimal.valueOf(Long.MAX_VALUE).add(BigDecimal.ONE);
        for (int index = 0; index < maxLevel - minLevel; index++) {
            raw.add(current);
            current = current.multiply(factor);
            if (current.abs().compareTo(overflowLimit) > 0 && index + 1 < maxLevel - minLevel) {
                throw new IllegalArgumentException("Exponential curve exceeds long bounds at level "
                        + (minLevel + index + 1));
            }
        }
        return fromRaw(CurveType.EXPONENTIAL, minLevel, maxLevel, rounding, raw);
    }

    public static SkillCurve customTable(
            int minLevel,
            int maxLevel,
            CurveRounding rounding,
            List<BigDecimal> values
    ) {
        return fromRaw(CurveType.CUSTOM_TABLE, minLevel, maxLevel, rounding, values);
    }

    public static SkillCurve precomputed(
            CurveType type,
            int minLevel,
            int maxLevel,
            CurveRounding rounding,
            List<Long> costs
    ) {
        return new SkillCurve(type, rounding, minLevel, maxLevel, costs);
    }

    private static SkillCurve generated(
            CurveType type,
            int minLevel,
            int maxLevel,
            CurveRounding rounding,
            IntFunction<BigDecimal> generator
    ) {
        validateBounds(minLevel, maxLevel);
        var values = new ArrayList<BigDecimal>(maxLevel - minLevel);
        for (int index = 0; index < maxLevel - minLevel; index++) {
            values.add(generator.apply(index));
        }
        return fromRaw(type, minLevel, maxLevel, rounding, values);
    }

    private static SkillCurve fromRaw(
            CurveType type,
            int minLevel,
            int maxLevel,
            CurveRounding rounding,
            List<BigDecimal> values
    ) {
        Objects.requireNonNull(values, "values");
        var costs = new ArrayList<Long>(values.size());
        for (int index = 0; index < values.size(); index++) {
            BigDecimal value = Objects.requireNonNull(values.get(index), "curve value");
            try {
                costs.add(value.setScale(0, rounding.mode()).longValueExact());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Curve cost exceeds long bounds at level "
                        + (minLevel + index), exception);
            }
        }
        return new SkillCurve(type, rounding, minLevel, maxLevel, costs);
    }

    private static void validateBounds(int minLevel, int maxLevel) {
        long span = (long) maxLevel - minLevel;
        if (minLevel < 0 || span < 0 || span > MAX_LEVEL_SPAN) {
            throw new IllegalArgumentException("Skill level span must be between zero and " + MAX_LEVEL_SPAN);
        }
    }

    public CurveType type() {
        return type;
    }

    public CurveRounding rounding() {
        return rounding;
    }

    public int minLevel() {
        return minLevel;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public List<Long> costs() {
        return costs;
    }

    public long costUnitsAt(int level) {
        if (level < minLevel || level >= maxLevel) {
            throw new IllegalArgumentException("Level has no outgoing curve cost " + level);
        }
        return FixedPoint.wholeXpToUnits(costs.get(level - minLevel));
    }

    public long totalUnitsAt(int level) {
        if (level < minLevel || level > maxLevel) {
            throw new IllegalArgumentException("Level is outside the curve " + level);
        }
        return cumulativeUnits[level - minLevel];
    }

    public long capUnits() {
        return cumulativeUnits[cumulativeUnits.length - 1];
    }

    public int levelForUnits(long units) {
        if (units < 0) {
            throw new IllegalArgumentException("Skill XP units must not be negative");
        }
        int low = 0;
        int high = cumulativeUnits.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (cumulativeUnits[middle] <= units) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return minLevel + Math.min(costs.size(), low - 1);
    }

    public long intoLevelUnits(long units) {
        int level = levelForUnits(units);
        return level == maxLevel ? 0 : units - totalUnitsAt(level);
    }
}
