package com.envisione.progressiveskills.common.creator;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.expression.CompiledNumericExpression;
import com.envisione.progressiveskills.common.expression.ExpressionDependency;
import com.envisione.progressiveskills.common.expression.ExpressionLimits;
import com.envisione.progressiveskills.common.expression.ExpressionRounding;
import com.envisione.progressiveskills.common.expression.NumericExpression;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class CreatorFormulaParser {
    private CreatorFormulaParser() {
    }

    public static CompiledNumericExpression compile(String source, ExpressionRounding rounding) {
        Parser parser = new Parser(source);
        NumericExpression expression = parser.expression();
        parser.whitespace();
        if (!parser.end()) {
            throw new IllegalArgumentException("Formula contains unexpected input at " + parser.index);
        }
        return CompiledNumericExpression.compile(expression, rounding, ExpressionLimits.CORE);
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source.strip();
            if (this.source.isEmpty() || this.source.length() > 4096) {
                throw new IllegalArgumentException("Formula source length is invalid");
            }
        }

        private NumericExpression expression() {
            NumericExpression value = term();
            while (true) {
                whitespace();
                if (take('+')) {
                    value = new NumericExpression.Add(value, term());
                } else if (take('-')) {
                    value = new NumericExpression.Subtract(value, term());
                } else {
                    return value;
                }
            }
        }

        private NumericExpression term() {
            NumericExpression value = unary();
            while (true) {
                whitespace();
                if (take('*')) {
                    value = new NumericExpression.Multiply(value, unary());
                } else if (take('/')) {
                    value = new NumericExpression.Divide(value, unary());
                } else {
                    return value;
                }
            }
        }

        private NumericExpression unary() {
            whitespace();
            if (take('-')) {
                return new NumericExpression.Subtract(new NumericExpression.Constant(0), unary());
            }
            if (take('(')) {
                NumericExpression value = expression();
                require(')');
                return value;
            }
            if (digit(peek())) {
                return number();
            }
            String name = name();
            whitespace();
            if (!take('(')) {
                ResourceLocation id = name.indexOf(':') >= 0
                        ? ResourceLocation.parse(name)
                        : ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "variables/" + name);
                return new NumericExpression.Variable(new ExpressionDependency(id));
            }
            var arguments = new ArrayList<NumericExpression>();
            whitespace();
            if (!take(')')) {
                do {
                    arguments.add(expression());
                    whitespace();
                } while (take(','));
                require(')');
            }
            return function(name, arguments);
        }

        private NumericExpression number() {
            int start = index;
            while (!end() && (digit(peek()) || peek() == '.')) {
                index++;
            }
            return new NumericExpression.Constant(FixedPoint.parse(source.substring(start, index)));
        }

        private String name() {
            whitespace();
            int start = index;
            while (!end()) {
                char value = peek();
                if (!Character.isLetterOrDigit(value) && value != '_' && value != ':' && value != '.'
                        && value != '/' && value != '-') {
                    break;
                }
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("Formula expected a value at " + index);
            }
            return source.substring(start, index);
        }

        private static NumericExpression function(String name, List<NumericExpression> arguments) {
            return switch (name) {
                case "min" -> pair(name, arguments, NumericExpression.Minimum::new);
                case "max" -> pair(name, arguments, NumericExpression.Maximum::new);
                case "clamp" -> {
                    if (arguments.size() != 3) {
                        throw new IllegalArgumentException("Formula clamp requires three arguments");
                    }
                    yield new NumericExpression.Clamp(arguments.get(0), arguments.get(1), arguments.get(2));
                }
                case "product" -> new NumericExpression.Product(arguments);
                default -> throw new IllegalArgumentException("Unknown formula function " + name);
            };
        }

        private static NumericExpression pair(
                String name,
                List<NumericExpression> arguments,
                java.util.function.BiFunction<NumericExpression, NumericExpression, NumericExpression> factory
        ) {
            if (arguments.size() != 2) {
                throw new IllegalArgumentException("Formula " + name + " requires two arguments");
            }
            return factory.apply(arguments.get(0), arguments.get(1));
        }

        private void require(char value) {
            whitespace();
            if (!take(value)) {
                throw new IllegalArgumentException("Formula expected " + value + " at " + index);
            }
        }

        private boolean take(char value) {
            if (!end() && source.charAt(index) == value) {
                index++;
                return true;
            }
            return false;
        }

        private char peek() {
            return end() ? '\0' : source.charAt(index);
        }

        private boolean end() {
            return index >= source.length();
        }

        private void whitespace() {
            while (!end() && Character.isWhitespace(peek())) {
                index++;
            }
        }

        private static boolean digit(char value) {
            return value >= '0' && value <= '9';
        }
    }
}
