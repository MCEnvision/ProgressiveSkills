package com.envisione.progressiveskills.common.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded-complexity conjunction of SemVer comparisons such as {@code >=1.0.0 <2.0.0}. */
public record VersionConstraint(String expression, List<Term> terms) {
    public static final int MAX_TERMS = 8;

    public VersionConstraint {
        Objects.requireNonNull(expression, "expression");
        terms = List.copyOf(terms);
        if (terms.isEmpty() || terms.size() > MAX_TERMS) {
            throw new IllegalArgumentException("A version constraint requires 1.." + MAX_TERMS + " terms");
        }
    }

    public static VersionConstraint parse(String expression) {
        Objects.requireNonNull(expression, "expression");
        String normalized = expression.strip();
        if (normalized.isEmpty() || normalized.length() > 512) {
            throw new IllegalArgumentException("Invalid version constraint: " + expression);
        }
        String[] tokens = normalized.split("\\s+");
        if (tokens.length > MAX_TERMS) {
            throw new IllegalArgumentException("Version constraint exceeds " + MAX_TERMS + " terms");
        }
        var parsed = new ArrayList<Term>(tokens.length);
        for (String token : tokens) {
            Operator operator = Operator.EXACT;
            String version = token;
            for (Operator candidate : Operator.PARSE_ORDER) {
                if (token.startsWith(candidate.symbol)) {
                    operator = candidate;
                    version = token.substring(candidate.symbol.length());
                    break;
                }
            }
            parsed.add(new Term(operator, SemanticVersion.parse(version)));
        }
        return new VersionConstraint(normalized, parsed);
    }

    public boolean contains(SemanticVersion version) {
        Objects.requireNonNull(version, "version");
        return terms.stream().allMatch(term -> term.matches(version));
    }

    public boolean hasLowerBound() {
        return terms.stream().anyMatch(term -> term.operator == Operator.EXACT
                || term.operator == Operator.GREATER_OR_EQUAL || term.operator == Operator.GREATER);
    }

    public boolean hasUpperBound() {
        return terms.stream().anyMatch(term -> term.operator == Operator.EXACT
                || term.operator == Operator.LESS_OR_EQUAL || term.operator == Operator.LESS);
    }

    public record Term(Operator operator, SemanticVersion version) {
        public Term {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(version, "version");
        }

        boolean matches(SemanticVersion candidate) {
            int comparison = candidate.compareTo(version);
            return switch (operator) {
                case EXACT -> comparison == 0;
                case GREATER_OR_EQUAL -> comparison >= 0;
                case GREATER -> comparison > 0;
                case LESS_OR_EQUAL -> comparison <= 0;
                case LESS -> comparison < 0;
            };
        }
    }

    public enum Operator {
        EXACT("="), GREATER_OR_EQUAL(">="), GREATER(">"), LESS_OR_EQUAL("<="), LESS("<");

        private static final List<Operator> PARSE_ORDER = List.of(
                GREATER_OR_EQUAL, LESS_OR_EQUAL, GREATER, LESS, EXACT
        );
        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }
    }
}
