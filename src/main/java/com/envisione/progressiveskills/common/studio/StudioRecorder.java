package com.envisione.progressiveskills.common.studio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StudioRecorder {
    public static final int MAX_EVENTS = 256;
    private final UUID recordingId = UUID.randomUUID();
    private final Instant startedAt = Instant.now();
    private final List<Event> events = new ArrayList<>();

    public synchronized void record(String type, String subject, long value) {
        if (events.size() >= MAX_EVENTS) {
            throw new IllegalStateException("Studio recording event capacity is full");
        }
        events.add(new Event(events.size(), bounded(type), bounded(subject), value));
    }

    public synchronized Recording finish() {
        return new Recording(recordingId, startedAt, Instant.now(), List.copyOf(events));
    }

    private static String bounded(String value) {
        String result = Objects.requireNonNull(value, "value").strip();
        if (result.isEmpty() || result.length() > 256) {
            throw new IllegalArgumentException("Studio recording text is invalid");
        }
        return result;
    }

    public record Event(int sequence, String type, String subject, long value) {
    }

    public record Recording(UUID id, Instant startedAt, Instant finishedAt, List<Event> events) {
        public Recording {
            events = List.copyOf(events);
        }
    }
}
