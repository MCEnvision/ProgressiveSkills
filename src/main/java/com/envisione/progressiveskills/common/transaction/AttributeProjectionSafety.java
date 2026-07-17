package com.envisione.progressiveskills.common.transaction;

import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.skill.FixedPoint;

import java.util.Objects;

public final class AttributeProjectionSafety {
    public static final long MAX_ADD_VALUE_UNITS = 1_000L * FixedPoint.SCALE;
    public static final long MAX_MULTIPLIER_UNITS = 16L * FixedPoint.SCALE;

    private AttributeProjectionSafety() {
    }

    public static long maximumMagnitude(AttributeOperation operation) {
        Objects.requireNonNull(operation, "operation");
        return operation == AttributeOperation.ADD_VALUE
                ? MAX_ADD_VALUE_UNITS : MAX_MULTIPLIER_UNITS;
    }

    public static boolean isWithinBounds(AttributeOperation operation, long valueUnits) {
        long maximum = maximumMagnitude(operation);
        return valueUnits >= -maximum && valueUnits <= maximum;
    }
}
