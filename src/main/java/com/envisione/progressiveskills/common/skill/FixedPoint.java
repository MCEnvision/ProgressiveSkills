package com.envisione.progressiveskills.common.skill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class FixedPoint {
    public static final long SCALE = 1_000_000L;
    private static final BigDecimal DECIMAL_SCALE = BigDecimal.valueOf(SCALE);

    private FixedPoint() {
    }

    public static long parse(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return fromDecimal(new BigDecimal(value.strip()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid fixed point value " + value, exception);
        }
    }

    public static long fromNumber(Number value) {
        Objects.requireNonNull(value, "value");
        return fromDecimal(new BigDecimal(value.toString()));
    }

    public static long fromDecimal(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        try {
            return value.multiply(DECIMAL_SCALE).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Fixed point value exceeds six decimal places or long bounds", exception);
        }
    }

    public static BigDecimal toDecimal(long units) {
        return BigDecimal.valueOf(units, 6).stripTrailingZeros();
    }

    public static String format(long units) {
        return toDecimal(units).toPlainString();
    }

    public static long wholeXpToUnits(long xp) {
        return Math.multiplyExact(xp, SCALE);
    }
}
