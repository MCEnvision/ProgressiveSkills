package com.envisione.progressiveskills.common.expression;

import com.envisione.progressiveskills.common.skill.FixedPoint;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericExpressionTest {
    @Test
    void exactArithmeticRoundsOnlyAtTheFinalBoundary() {
        NumericExpression expression = new NumericExpression.Divide(
                new NumericExpression.Multiply(
                        new NumericExpression.Constant(FixedPoint.parse("0.1")),
                        new NumericExpression.Constant(FixedPoint.parse("0.2"))
                ),
                new NumericExpression.Constant(FixedPoint.parse("0.3"))
        );
        assertEquals(66_666, CompiledNumericExpression.compile(
                expression, ExpressionRounding.FLOOR
        ).constantValue().orElseThrow());
        assertEquals(66_667, CompiledNumericExpression.compile(
                expression, ExpressionRounding.CEIL
        ).constantValue().orElseThrow());
    }

    @Test
    void allRoundingModesHandlePositiveAndNegativeTies() {
        NumericExpression positive = new NumericExpression.Divide(
                new NumericExpression.Constant(1),
                new NumericExpression.Constant(FixedPoint.parse("2"))
        );
        NumericExpression negative = new NumericExpression.Divide(
                new NumericExpression.Constant(-1),
                new NumericExpression.Constant(FixedPoint.parse("2"))
        );
        assertEquals(0, value(positive, ExpressionRounding.FLOOR));
        assertEquals(1, value(positive, ExpressionRounding.CEIL));
        assertEquals(1, value(positive, ExpressionRounding.NEAREST));
        assertEquals(0, value(positive, ExpressionRounding.BANKERS));
        assertEquals(-1, value(negative, ExpressionRounding.FLOOR));
        assertEquals(0, value(negative, ExpressionRounding.CEIL));
        assertEquals(-1, value(negative, ExpressionRounding.NEAREST));
        assertEquals(0, value(negative, ExpressionRounding.BANKERS));
    }

    @Test
    void variablesExposeDeterministicDependenciesAndPreviewDetails() {
        var alpha = new ExpressionDependency(id("test:alpha"));
        var beta = new ExpressionDependency(id("test:beta"));
        var compiled = CompiledNumericExpression.compile(
                new NumericExpression.Add(
                        new NumericExpression.Variable(beta),
                        new NumericExpression.Variable(alpha)
                ),
                ExpressionRounding.NEAREST
        );
        assertEquals(java.util.List.of(alpha, beta), compiled.dependencies());
        NumericEvaluation evaluation = compiled.evaluate(dependency -> OptionalLong.of(
                dependency.equals(alpha) ? 10 : 20
        ));
        assertEquals(30, evaluation.valueUnits());
        assertEquals(java.util.List.of(beta, alpha), evaluation.dependenciesRead());
        assertTrue(evaluation.explanation().contains("nearest"));
    }

    @Test
    void invalidOperationsAndBudgetsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> CompiledNumericExpression.compile(
                new NumericExpression.Divide(new NumericExpression.Constant(1), new NumericExpression.Constant(0)),
                ExpressionRounding.FLOOR
        ));
        NumericExpression deep = new NumericExpression.Constant(1);
        for (int index = 0; index < 17; index++) {
            deep = new NumericExpression.Add(deep, new NumericExpression.Constant(1));
        }
        NumericExpression tooDeep = deep;
        assertThrows(IllegalArgumentException.class, () -> CompiledNumericExpression.compile(
                tooDeep, ExpressionRounding.FLOOR
        ));
        var dependency = new ExpressionDependency(id("test:missing"));
        var compiled = CompiledNumericExpression.compile(
                new NumericExpression.Variable(dependency), ExpressionRounding.FLOOR
        );
        assertThrows(IllegalArgumentException.class, () -> compiled.evaluate(ignored -> OptionalLong.empty()));
    }

    private static long value(NumericExpression expression, ExpressionRounding rounding) {
        return CompiledNumericExpression.compile(expression, rounding).constantValue().orElseThrow();
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
