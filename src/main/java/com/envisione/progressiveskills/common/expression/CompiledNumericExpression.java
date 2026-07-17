package com.envisione.progressiveskills.common.expression;

import com.envisione.progressiveskills.common.skill.FixedPoint;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

public final class CompiledNumericExpression {
    private final NumericExpression root;
    private final ExpressionRounding rounding;
    private final ExpressionLimits limits;
    private final List<ExpressionDependency> dependencies;
    private final OptionalLong constant;

    private CompiledNumericExpression(
            NumericExpression root,
            ExpressionRounding rounding,
            ExpressionLimits limits,
            List<ExpressionDependency> dependencies,
            OptionalLong constant
    ) {
        this.root = root;
        this.rounding = rounding;
        this.limits = limits;
        this.dependencies = dependencies;
        this.constant = constant;
    }

    public static CompiledNumericExpression compile(
            NumericExpression root,
            ExpressionRounding rounding,
            ExpressionLimits limits
    ) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(rounding, "rounding");
        Objects.requireNonNull(limits, "limits");
        var dependencies = new TreeSet<ExpressionDependency>();
        CompileCount count = inspect(root, 1, limits, dependencies);
        if (count.nodes() > limits.maxNodes()) {
            throw new IllegalArgumentException("Expression node budget exceeded");
        }
        if (dependencies.size() > limits.maxDependencies()) {
            throw new IllegalArgumentException("Expression dependency budget exceeded");
        }
        List<ExpressionDependency> immutableDependencies = List.copyOf(dependencies);
        OptionalLong constant = OptionalLong.empty();
        if (immutableDependencies.isEmpty()) {
            constant = OptionalLong.of(evaluate(root, rounding, limits, NumericContext.EMPTY).valueUnits());
        }
        return new CompiledNumericExpression(root, rounding, limits, immutableDependencies, constant);
    }

    public static CompiledNumericExpression compile(NumericExpression root, ExpressionRounding rounding) {
        return compile(root, rounding, ExpressionLimits.CORE);
    }

    public NumericEvaluation evaluate(NumericContext context) {
        Objects.requireNonNull(context, "context");
        if (constant.isPresent()) {
            return new NumericEvaluation(
                    constant.getAsLong(),
                    0,
                    List.of(),
                    "constant expression rounded with " + rounding.serializedName()
            );
        }
        return evaluate(root, rounding, limits, context);
    }

    public OptionalLong constantValue() {
        return constant;
    }

    public List<ExpressionDependency> dependencies() {
        return dependencies;
    }

    public ExpressionRounding rounding() {
        return rounding;
    }

    private static NumericEvaluation evaluate(
            NumericExpression root,
            ExpressionRounding rounding,
            ExpressionLimits limits,
            NumericContext context
    ) {
        var state = new EvaluationState(limits, context);
        Rational value = state.evaluate(root);
        long rounded;
        try {
            rounded = rounding.round(value.numerator(), value.denominator());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Expression result exceeds long bounds", exception);
        }
        return new NumericEvaluation(
                rounded,
                state.operations,
                List.copyOf(state.read),
                "expression result rounded with " + rounding.serializedName()
        );
    }

    private static CompileCount inspect(
            NumericExpression expression,
            int depth,
            ExpressionLimits limits,
            Set<ExpressionDependency> dependencies
    ) {
        if (depth > limits.maxDepth()) {
            throw new IllegalArgumentException("Expression depth budget exceeded");
        }
        int nodes = 1;
        if (expression instanceof NumericExpression.Variable variable) {
            dependencies.add(variable.dependency());
        } else if (expression instanceof NumericExpression.Add add) {
            nodes += pair(add.left(), add.right(), depth, limits, dependencies);
        } else if (expression instanceof NumericExpression.Subtract subtract) {
            nodes += pair(subtract.left(), subtract.right(), depth, limits, dependencies);
        } else if (expression instanceof NumericExpression.Multiply multiply) {
            nodes += pair(multiply.left(), multiply.right(), depth, limits, dependencies);
        } else if (expression instanceof NumericExpression.Divide divide) {
            nodes += pair(divide.left(), divide.right(), depth, limits, dependencies);
        } else if (expression instanceof NumericExpression.Minimum minimum) {
            nodes += pair(minimum.left(), minimum.right(), depth, limits, dependencies);
        } else if (expression instanceof NumericExpression.Maximum maximum) {
            nodes += pair(maximum.left(), maximum.right(), depth, limits, dependencies);
        } else if (expression instanceof NumericExpression.Clamp clamp) {
            nodes += inspect(clamp.value(), depth + 1, limits, dependencies).nodes();
            nodes += inspect(clamp.minimum(), depth + 1, limits, dependencies).nodes();
            nodes += inspect(clamp.maximum(), depth + 1, limits, dependencies).nodes();
        } else if (expression instanceof NumericExpression.Product product) {
            for (NumericExpression factor : product.factors()) {
                nodes += inspect(factor, depth + 1, limits, dependencies).nodes();
            }
        }
        if (nodes > limits.maxNodes()) {
            throw new IllegalArgumentException("Expression node budget exceeded");
        }
        return new CompileCount(nodes);
    }

    private static int pair(
            NumericExpression left,
            NumericExpression right,
            int depth,
            ExpressionLimits limits,
            Set<ExpressionDependency> dependencies
    ) {
        return Math.addExact(
                inspect(left, depth + 1, limits, dependencies).nodes(),
                inspect(right, depth + 1, limits, dependencies).nodes()
        );
    }

    private record CompileCount(int nodes) {
    }

    private record Rational(BigInteger numerator, BigInteger denominator) {
        private Rational {
            if (denominator.signum() == 0) {
                throw new IllegalArgumentException("Expression division by zero");
            }
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            BigInteger divisor = numerator.gcd(denominator);
            numerator = numerator.divide(divisor);
            denominator = denominator.divide(divisor);
        }

        static Rational units(long units) {
            return new Rational(BigInteger.valueOf(units), BigInteger.ONE);
        }

        Rational add(Rational other) {
            return new Rational(
                    numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator)
            );
        }

        Rational subtract(Rational other) {
            return new Rational(
                    numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator)
            );
        }

        Rational multiply(Rational other) {
            return new Rational(
                    numerator.multiply(other.numerator),
                    denominator.multiply(other.denominator).multiply(BigInteger.valueOf(FixedPoint.SCALE))
            );
        }

        Rational divide(Rational other) {
            if (other.numerator.signum() == 0) {
                throw new IllegalArgumentException("Expression division by zero");
            }
            return new Rational(
                    numerator.multiply(other.denominator).multiply(BigInteger.valueOf(FixedPoint.SCALE)),
                    denominator.multiply(other.numerator)
            );
        }

        int compareTo(Rational other) {
            return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
        }
    }

    private static final class EvaluationState {
        private final ExpressionLimits limits;
        private final NumericContext context;
        private final LinkedHashSet<ExpressionDependency> read = new LinkedHashSet<>();
        private int operations;

        private EvaluationState(ExpressionLimits limits, NumericContext context) {
            this.limits = limits;
            this.context = context;
        }

        private Rational evaluate(NumericExpression expression) {
            operations++;
            if (operations > limits.maxOperations()) {
                throw new IllegalArgumentException("Expression operation budget exceeded");
            }
            Rational result;
            if (expression instanceof NumericExpression.Constant constant) {
                result = Rational.units(constant.units());
            } else if (expression instanceof NumericExpression.Variable variable) {
                read.add(variable.dependency());
                long value = context.value(variable.dependency()).orElseThrow(
                        () -> new IllegalArgumentException("Missing expression dependency " + variable.dependency().id())
                );
                result = Rational.units(value);
            } else if (expression instanceof NumericExpression.Add add) {
                result = evaluate(add.left()).add(evaluate(add.right()));
            } else if (expression instanceof NumericExpression.Subtract subtract) {
                result = evaluate(subtract.left()).subtract(evaluate(subtract.right()));
            } else if (expression instanceof NumericExpression.Multiply multiply) {
                result = evaluate(multiply.left()).multiply(evaluate(multiply.right()));
            } else if (expression instanceof NumericExpression.Divide divide) {
                result = evaluate(divide.left()).divide(evaluate(divide.right()));
            } else if (expression instanceof NumericExpression.Minimum minimum) {
                Rational left = evaluate(minimum.left());
                Rational right = evaluate(minimum.right());
                result = left.compareTo(right) <= 0 ? left : right;
            } else if (expression instanceof NumericExpression.Maximum maximum) {
                Rational left = evaluate(maximum.left());
                Rational right = evaluate(maximum.right());
                result = left.compareTo(right) >= 0 ? left : right;
            } else if (expression instanceof NumericExpression.Clamp clamp) {
                Rational value = evaluate(clamp.value());
                Rational minimum = evaluate(clamp.minimum());
                Rational maximum = evaluate(clamp.maximum());
                if (minimum.compareTo(maximum) > 0) {
                    throw new IllegalArgumentException("Expression clamp minimum exceeds maximum");
                }
                result = value.compareTo(minimum) < 0 ? minimum
                        : value.compareTo(maximum) > 0 ? maximum : value;
            } else if (expression instanceof NumericExpression.Product product) {
                result = Rational.units(FixedPoint.SCALE);
                for (NumericExpression factor : product.factors()) {
                    result = result.multiply(evaluate(factor));
                }
            } else {
                throw new IllegalArgumentException("Unknown numeric expression node");
            }
            requireBits(result);
            return result;
        }

        private void requireBits(Rational value) {
            if (value.numerator().bitLength() > limits.maxIntermediateBits()
                    || value.denominator().bitLength() > limits.maxIntermediateBits()) {
                throw new IllegalArgumentException("Expression intermediate bit budget exceeded");
            }
        }
    }
}
