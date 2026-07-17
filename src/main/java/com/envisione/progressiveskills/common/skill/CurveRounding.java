package com.envisione.progressiveskills.common.skill;

import java.math.RoundingMode;
import java.util.Locale;

public enum CurveRounding {
    CEIL(RoundingMode.CEILING),
    FLOOR(RoundingMode.FLOOR),
    NEAREST(RoundingMode.HALF_UP),
    BANKERS(RoundingMode.HALF_EVEN);

    private final RoundingMode mode;

    CurveRounding(RoundingMode mode) {
        this.mode = mode;
    }

    public RoundingMode mode() {
        return mode;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CurveRounding parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown curve rounding " + value, exception);
        }
    }
}
