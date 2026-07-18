package com.envisione.progressiveskills.common.provider;

import java.time.Instant;
import java.util.Objects;

public record ProviderHealth(Status status, String message, Instant checkedAt) {
    public ProviderHealth {
        Objects.requireNonNull(status, "status");
        message = Objects.requireNonNull(message, "message").strip();
        Objects.requireNonNull(checkedAt, "checkedAt");
        if (message.isEmpty() || message.length() > 512) {
            throw new IllegalArgumentException("Provider health message is invalid");
        }
    }

    public enum Status {
        HEALTHY,
        DEGRADED,
        UNAVAILABLE,
        CIRCUIT_OPEN
    }
}
