package com.envisione.progressiveskills.common.expression;

import java.math.BigInteger;
import java.util.Locale;

public enum ExpressionRounding {
    FLOOR,
    CEIL,
    NEAREST,
    BANKERS;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ExpressionRounding parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown expression rounding " + value, exception);
        }
    }

    long round(BigInteger numerator, BigInteger denominator) {
        if (denominator.signum() == 0) {
            throw new IllegalArgumentException("Expression division by zero");
        }
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        BigInteger quotient = division[0];
        BigInteger remainder = division[1];
        if (remainder.signum() == 0) {
            return quotient.longValueExact();
        }
        int direction = numerator.signum();
        BigInteger rounded = switch (this) {
            case FLOOR -> direction < 0 ? quotient.subtract(BigInteger.ONE) : quotient;
            case CEIL -> direction > 0 ? quotient.add(BigInteger.ONE) : quotient;
            case NEAREST -> nearest(quotient, remainder, denominator, direction, false);
            case BANKERS -> nearest(quotient, remainder, denominator, direction, true);
        };
        return rounded.longValueExact();
    }

    private static BigInteger nearest(
            BigInteger quotient,
            BigInteger remainder,
            BigInteger denominator,
            int direction,
            boolean evenTie
    ) {
        int comparison = remainder.abs().shiftLeft(1).compareTo(denominator);
        if (comparison < 0 || comparison == 0 && evenTie && !quotient.testBit(0)) {
            return quotient;
        }
        return quotient.add(BigInteger.valueOf(direction));
    }
}
