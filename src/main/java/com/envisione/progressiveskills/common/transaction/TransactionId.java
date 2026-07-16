package com.envisione.progressiveskills.common.transaction;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Stable transaction identity derived from target and idempotency key. */
public record TransactionId(UUID value) implements Comparable<TransactionId> {
    public TransactionId {
        Objects.requireNonNull(value, "value");
    }

    public static TransactionId derive(UUID targetId, IdempotencyKey key) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(key, "key");
        String seed = targetId + "\n" + key.value();
        return new TransactionId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public int compareTo(TransactionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
