package com.envisione.progressiveskills.common.social;

import java.time.Instant;
import java.util.Objects;

public record PartyReadiness(
        boolean ready,
        String role,
        String build,
        String resources,
        String cooldowns,
        Instant updatedAt
) {
    public PartyReadiness {
        role = bounded(role);
        build = bounded(build);
        resources = bounded(resources);
        cooldowns = bounded(cooldowns);
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static String bounded(String value) {
        String result = Objects.requireNonNull(value, "value").strip();
        return result.substring(0, Math.min(256, result.length()));
    }
}
