package com.envisione.progressiveskills.server.hardening;

import com.envisione.progressiveskills.common.hardening.PerformanceGuard;

public final class HardeningRuntime {
    private static final PerformanceGuard PERFORMANCE = PerformanceGuard.systemClock();

    private HardeningRuntime() {
    }

    public static PerformanceGuard performance() {
        return PERFORMANCE;
    }
}
