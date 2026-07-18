package com.envisione.progressiveskills.server.hardening;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DecisionTraceRuntime {
    private static final int MAX_PLAYERS = 256;
    private static final int MAX_PER_PLAYER = 32;
    private static final Map<UUID, ArrayDeque<Decision>> DECISIONS = new LinkedHashMap<>();

    private DecisionTraceRuntime() {
    }

    public static synchronized void record(
            UUID playerId,
            String category,
            String subject,
            boolean allowed,
            String reason,
            long stateRevision
    ) {
        Objects.requireNonNull(playerId, "playerId");
        if (!DECISIONS.containsKey(playerId) && DECISIONS.size() >= MAX_PLAYERS) {
            UUID first = DECISIONS.keySet().iterator().next();
            DECISIONS.remove(first);
        }
        ArrayDeque<Decision> values = DECISIONS.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        while (values.size() >= MAX_PER_PLAYER) {
            values.removeFirst();
        }
        values.addLast(new Decision(Instant.now(), bounded(category), bounded(subject),
                allowed, bounded(reason), stateRevision));
    }

    public static synchronized Optional<Decision> latest(UUID playerId) {
        ArrayDeque<Decision> values = DECISIONS.get(playerId);
        return values == null ? Optional.empty() : Optional.ofNullable(values.peekLast());
    }

    public static synchronized List<Decision> history(UUID playerId) {
        ArrayDeque<Decision> values = DECISIONS.get(playerId);
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static String bounded(String value) {
        String result = Objects.requireNonNull(value, "value").strip();
        if (result.isEmpty()) {
            return "unspecified";
        }
        return result.substring(0, Math.min(512, result.length()));
    }

    public record Decision(
            Instant at,
            String category,
            String subject,
            boolean allowed,
            String reason,
            long stateRevision
    ) {
    }
}
