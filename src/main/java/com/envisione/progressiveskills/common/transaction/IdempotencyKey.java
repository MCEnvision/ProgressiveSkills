package com.envisione.progressiveskills.common.transaction;

import java.util.Objects;
import java.util.regex.Pattern;

/** Bounded caller-provided identity that makes a transaction safely replayable. */
public record IdempotencyKey(String value) implements Comparable<IdempotencyKey> {
    public static final int MAX_LENGTH = 192;
    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]*");

    public IdempotencyKey {
        value = Objects.requireNonNull(value, "value");
        if (value.length() > MAX_LENGTH || !VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid idempotency key: " + value);
        }
    }

    @Override
    public int compareTo(IdempotencyKey other) {
        return value.compareTo(other.value);
    }
}
