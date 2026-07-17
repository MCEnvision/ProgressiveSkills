package com.envisione.progressiveskills.common.requirement;

public enum ComparisonOperator {
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    EQUAL("=="),
    NOT_EQUAL("!="),
    GREATER_THAN_OR_EQUAL(">="),
    GREATER_THAN(">");

    private final String serializedName;

    ComparisonOperator(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean test(long actual, long expected) {
        return switch (this) {
            case LESS_THAN -> actual < expected;
            case LESS_THAN_OR_EQUAL -> actual <= expected;
            case EQUAL -> actual == expected;
            case NOT_EQUAL -> actual != expected;
            case GREATER_THAN_OR_EQUAL -> actual >= expected;
            case GREATER_THAN -> actual > expected;
        };
    }

    public static ComparisonOperator parse(String value) {
        for (ComparisonOperator operator : values()) {
            if (operator.serializedName.equals(value)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("Unknown requirement comparison " + value);
    }
}
