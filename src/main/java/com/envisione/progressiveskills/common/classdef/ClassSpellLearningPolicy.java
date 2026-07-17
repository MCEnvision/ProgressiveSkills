package com.envisione.progressiveskills.common.classdef;

import java.util.Arrays;

public enum ClassSpellLearningPolicy {
    REQUIRE_EXISTING("require_existing"),
    SATISFY_WHILE_OWNED("satisfy_while_owned");

    private final String serializedName;

    ClassSpellLearningPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ClassSpellLearningPolicy parse(String value) {
        if (value.equals("permanently_learn")) {
            throw new IllegalArgumentException("Core class grants do not support permanently_learn spell learning");
        }
        return Arrays.stream(values())
                .filter(policy -> policy.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown class spell learning policy " + value));
    }
}
