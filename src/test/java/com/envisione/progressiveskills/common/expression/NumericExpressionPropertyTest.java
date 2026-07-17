package com.envisione.progressiveskills.common.expression;

import com.envisione.progressiveskills.common.skill.FixedPoint;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumericExpressionPropertyTest {
    @Property(tries = 500)
    void fixedPointMultiplicationMatchesAnExactReference(
            @ForAll @LongRange(min = 1, max = 1_000_000_000L) long amount,
            @ForAll @LongRange(min = 1, max = 4_000_000L) long factor
    ) {
        long expected = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(factor))
                .divide(BigInteger.valueOf(FixedPoint.SCALE)).longValueExact();
        long actual = CompiledNumericExpression.compile(
                new NumericExpression.Multiply(
                        new NumericExpression.Constant(amount),
                        new NumericExpression.Constant(factor)
                ),
                ExpressionRounding.FLOOR
        ).constantValue().orElseThrow();
        assertEquals(expected, actual);
    }
}
