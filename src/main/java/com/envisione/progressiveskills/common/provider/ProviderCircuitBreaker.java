package com.envisione.progressiveskills.common.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class ProviderCircuitBreaker {
    private final Clock clock;
    private final int failureThreshold;
    private final Duration resetAfter;
    private int failures;
    private Instant openedAt;

    public ProviderCircuitBreaker(Clock clock, int failureThreshold, Duration resetAfter) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("Circuit failure threshold must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.resetAfter = Objects.requireNonNull(resetAfter, "resetAfter");
        if (resetAfter.isNegative() || resetAfter.isZero()) {
            throw new IllegalArgumentException("Circuit reset duration must be positive");
        }
    }

    public synchronized boolean allowRequest() {
        if (openedAt == null) {
            return true;
        }
        if (!Instant.now(clock).isBefore(openedAt.plus(resetAfter))) {
            openedAt = null;
            failures = Math.max(0, failureThreshold - 1);
            return true;
        }
        return false;
    }

    public synchronized void success() {
        failures = 0;
        openedAt = null;
    }

    public synchronized void failure() {
        failures++;
        if (failures >= failureThreshold) {
            openedAt = Instant.now(clock);
        }
    }

    public synchronized State state() {
        return openedAt == null ? State.CLOSED : State.OPEN;
    }

    public enum State {
        CLOSED,
        OPEN
    }
}
