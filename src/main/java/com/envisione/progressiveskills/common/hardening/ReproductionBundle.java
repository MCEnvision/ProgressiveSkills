package com.envisione.progressiveskills.common.hardening;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ReproductionBundle(
        int formatVersion,
        UUID bundleId,
        Instant createdAt,
        String definitionDigest,
        long definitionGeneration,
        long stateRevision,
        long deterministicSeed,
        Map<String, Long> redactedState,
        List<Event> events
) {
    public static final int MAX_EVENTS = 256;

    public ReproductionBundle {
        if (formatVersion != 1 && formatVersion != 2 || definitionGeneration < 0 || stateRevision < 0) {
            throw new IllegalArgumentException("Reproduction bundle metadata is invalid");
        }
        Objects.requireNonNull(bundleId, "bundleId");
        Objects.requireNonNull(createdAt, "createdAt");
        definitionDigest = Objects.requireNonNull(definitionDigest, "definitionDigest");
        if (!definitionDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Reproduction definition digest is invalid");
        }
        redactedState = Map.copyOf(Objects.requireNonNull(redactedState, "redactedState"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        if (events.size() > MAX_EVENTS || redactedState.size() > 512) {
            throw new IllegalArgumentException("Reproduction bundle exceeds capacity");
        }
        redactedState.forEach((key, value) -> {
            if (key == null || !key.matches("[a-zA-Z0-9_.:/-]{1,256}") || value == null) {
                throw new IllegalArgumentException("Reproduction state is invalid");
            }
        });
        long previous = -1;
        for (Event event : events) {
            if (event.sequence() <= previous) {
                throw new IllegalArgumentException("Reproduction events are not strictly ordered");
            }
            previous = event.sequence();
        }
    }

    public record Event(long sequence, String type, String subject, long amount, long stateRevision) {
        public Event(long sequence, String type, String subject, long amount) {
            this(sequence, type, subject, amount, -1L);
        }

        public Event {
            if (sequence < 0 || amount < 0 || amount > 1 || stateRevision < -1) {
                throw new IllegalArgumentException("Reproduction event sequence is invalid");
            }
            type = bounded(type, "type");
            subject = bounded(subject, "subject");
        }

        private static String bounded(String value, String name) {
            String result = Objects.requireNonNull(value, name).strip();
            if (result.isEmpty() || result.length() > 256) {
                throw new IllegalArgumentException("Reproduction event text is invalid");
            }
            return result;
        }
    }
}
