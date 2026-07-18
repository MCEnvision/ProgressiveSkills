package com.envisione.progressiveskills.common.hardening;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;

public final class PerformanceGuard {
    public static final int MAX_ROUTES = 256;
    private final Map<String, MutableRoute> routes = new TreeMap<>();
    private final Clock clock;

    public PerformanceGuard(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static PerformanceGuard systemClock() {
        return new PerformanceGuard(Clock.systemUTC());
    }

    public <T> T measure(String route, long budgetNanos, Supplier<T> operation) {
        long started = System.nanoTime();
        try {
            return operation.get();
        } finally {
            record(route, budgetNanos, System.nanoTime() - started);
        }
    }

    public synchronized void record(String route, long budgetNanos, long elapsedNanos) {
        String key = Objects.requireNonNull(route, "route").strip();
        if (key.isEmpty() || key.length() > 128 || budgetNanos < 1 || elapsedNanos < 0) {
            throw new IllegalArgumentException("Performance sample is invalid");
        }
        MutableRoute value = routes.get(key);
        if (value == null) {
            if (routes.size() >= MAX_ROUTES) {
                throw new IllegalStateException("Performance route capacity is full");
            }
            value = new MutableRoute(budgetNanos);
            routes.put(key, value);
        }
        value.record(budgetNanos, elapsedNanos, Instant.now(clock));
    }

    public synchronized Map<String, RouteSnapshot> snapshots() {
        var result = new LinkedHashMap<String, RouteSnapshot>();
        routes.forEach((key, value) -> result.put(key, value.snapshot()));
        return Collections.unmodifiableMap(result);
    }

    private static final class MutableRoute {
        private long budgetNanos;
        private long samples;
        private long totalNanos;
        private long maximumNanos;
        private long overruns;
        private Instant lastSample = Instant.EPOCH;

        private MutableRoute(long budgetNanos) {
            this.budgetNanos = budgetNanos;
        }

        private void record(long budget, long elapsed, Instant at) {
            budgetNanos = budget;
            samples = saturatingAdd(samples, 1L);
            totalNanos = saturatingAdd(totalNanos, elapsed);
            maximumNanos = Math.max(maximumNanos, elapsed);
            if (elapsed > budget) {
                overruns = saturatingAdd(overruns, 1L);
            }
            lastSample = at;
        }

        private static long saturatingAdd(long first, long second) {
            return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
        }

        private RouteSnapshot snapshot() {
            return new RouteSnapshot(budgetNanos, samples, totalNanos, maximumNanos, overruns, lastSample);
        }
    }

    public record RouteSnapshot(
            long budgetNanos,
            long samples,
            long totalNanos,
            long maximumNanos,
            long overruns,
            Instant lastSample
    ) {
        public long averageNanos() {
            return samples == 0 ? 0 : totalNanos / samples;
        }

        public boolean healthy() {
            return maximumNanos <= budgetNanos || overruns <= samples / 20;
        }
    }
}
