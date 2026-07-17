package com.envisione.progressiveskills.common.classdef;

import java.util.Arrays;

public enum ClassSwapPolicy {
    ALLOWED("allowed"),
    DISABLED("disabled");

    private final String serializedName;

    ClassSwapPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ClassSwapPolicy parse(String value) {
        return Arrays.stream(values())
                .filter(policy -> policy.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown class swap policy " + value));
    }
}
