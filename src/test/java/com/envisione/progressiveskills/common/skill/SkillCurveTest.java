package com.envisione.progressiveskills.common.skill;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillCurveTest {
    @Test
    void allFiveCurveTypesFollowTheExactCostContract() {
        assertEquals(List.of(100L, 100L, 100L), SkillCurve.flat(
                0, 3, CurveRounding.CEIL, decimal("100")
        ).costs());
        assertEquals(List.of(100L, 125L, 150L), SkillCurve.linear(
                0, 3, CurveRounding.CEIL, decimal("100"), decimal("25")
        ).costs());
        assertEquals(List.of(100L, 115L, 160L), SkillCurve.polynomial(
                0, 3, CurveRounding.CEIL, decimal("100"), decimal("15"), 2
        ).costs());
        assertEquals(List.of(100L, 150L, 225L), SkillCurve.exponential(
                0, 3, CurveRounding.CEIL, decimal("100"), decimal("1.5")
        ).costs());
        assertEquals(List.of(100L, 125L, 200L), SkillCurve.customTable(
                0, 3, CurveRounding.CEIL, List.of(decimal("100"), decimal("125"), decimal("200"))
        ).costs());
    }

    @Test
    void roundingOccursOnceAndFixedPointProgressHasNoDrift() {
        assertEquals(List.of(2L), SkillCurve.flat(0, 1, CurveRounding.CEIL, decimal("1.5")).costs());
        assertEquals(List.of(1L), SkillCurve.flat(0, 1, CurveRounding.FLOOR, decimal("1.5")).costs());
        assertEquals(List.of(2L), SkillCurve.flat(0, 1, CurveRounding.NEAREST, decimal("1.5")).costs());
        assertEquals(List.of(2L), SkillCurve.flat(0, 1, CurveRounding.BANKERS, decimal("2.5")).costs());
        assertEquals(1_000_000L, Math.multiplyExact(FixedPoint.parse("0.1"), 10));
        assertEquals("0.000001", FixedPoint.format(FixedPoint.parse("0.000001")));
    }

    @Test
    void invalidDecreasingAndOverflowingCurvesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> SkillCurve.linear(
                0, 3, CurveRounding.CEIL, decimal("100"), decimal("-1")
        ));
        assertThrows(ArithmeticException.class, () -> SkillCurve.flat(
                0, 2, CurveRounding.CEIL, decimal("9223372036854")
        ));
        assertThrows(IllegalArgumentException.class, () -> SkillCurve.customTable(
                0, 2, CurveRounding.CEIL, List.of(decimal("1"))
        ));
    }

    @Test
    void levelAndIntoLevelDeriveFromTheAuthoritativeXpCoordinate() {
        SkillCurve curve = SkillCurve.linear(0, 3, CurveRounding.CEIL, decimal("100"), decimal("25"));

        assertEquals(0, curve.levelForUnits(FixedPoint.parse("99.999999")));
        assertEquals(1, curve.levelForUnits(FixedPoint.parse("100")));
        assertEquals(FixedPoint.parse("12.5"), curve.intoLevelUnits(FixedPoint.parse("112.5")));
        assertEquals(3, curve.levelForUnits(curve.capUnits()));
        assertEquals(0, curve.intoLevelUnits(curve.capUnits()));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
