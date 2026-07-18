package com.envisione.progressiveskills.common.hardening;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceGuardTest {
    @Test
    void recordsBoundedRouteStatisticsWithoutChangingTheOperation() {
        var guard = new PerformanceGuard(Clock.fixed(
                Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        for (int index = 0; index < 20; index++) {
            guard.record("route", 100, index == 0 ? 101 : 50);
        }

        var snapshot = guard.snapshots().get("route");
        assertEquals(20, snapshot.samples());
        assertEquals(1, snapshot.overruns());
        assertEquals(101, snapshot.maximumNanos());
        assertTrue(snapshot.healthy());

        guard.record("route", 100, 101);
        assertFalse(guard.snapshots().get("route").healthy());
    }
}
