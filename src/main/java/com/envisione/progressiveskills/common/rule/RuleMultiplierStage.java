package com.envisione.progressiveskills.common.rule;

import java.util.Locale;

public enum RuleMultiplierStage {
    CONTEXT,
    EQUIPMENT,
    PARTY_TEAM,
    RESTED_CATCH_UP,
    PRESTIGE_SEASON,
    GLOBAL_DIFFICULTY;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RuleMultiplierStage parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown rule multiplier stage " + value, exception);
        }
    }
}
