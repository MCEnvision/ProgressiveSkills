package com.envisione.progressiveskills.server.hardening;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class ReproductionReplayService {
    public static final long MAX_BUNDLE_BYTES = 1_048_576L;

    private ReproductionReplayService() {
    }

    public static ReplayResult replay(Path file) throws IOException {
        return replay(file, null, ReproductionReplayService::evaluateDeterministically);
    }

    public static ReplayResult replay(Path file, String expectedDefinitionDigest) throws IOException {
        return replay(file, expectedDefinitionDigest, ReproductionReplayService::evaluateDeterministically);
    }

    public static ReplayResult replay(
            Path file,
            String expectedDefinitionDigest,
            DecisionExecutor executor
    ) throws IOException {
        Objects.requireNonNull(executor, "executor");
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > MAX_BUNDLE_BYTES) {
            throw new IllegalArgumentException("Reproduction bundle file is invalid");
        }
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> expectedFields = Set.of(
                "format_version", "bundle_id", "created_at", "definition_digest",
                "definition_generation", "state_revision", "seed", "state", "events");
        if (!root.keySet().equals(expectedFields)) {
            throw new IllegalArgumentException("Reproduction bundle fields are invalid");
        }
        int version = root.get("format_version").getAsInt();
        if (version != 1 && version != 2) {
            throw new IllegalArgumentException("Reproduction bundle version is unsupported");
        }
        UUID.fromString(root.get("bundle_id").getAsString());
        Instant.parse(root.get("created_at").getAsString());
        String definitionDigest = root.get("definition_digest").getAsString();
        long capturedRevision = root.get("state_revision").getAsLong();
        if (!definitionDigest.matches("[0-9a-f]{64}")
                || expectedDefinitionDigest != null && !definitionDigest.equals(expectedDefinitionDigest)
                || root.get("definition_generation").getAsLong() < 0
                || capturedRevision < 0) {
            throw new IllegalArgumentException("Reproduction bundle definition or revision is invalid");
        }
        long seed = root.get("seed").getAsLong();
        JsonObject stateObject = root.getAsJsonObject("state");
        if (stateObject.size() > 512) {
            throw new IllegalArgumentException("Reproduction state exceeds capacity");
        }
        var replayState = new TreeMap<String, Long>();
        stateObject.entrySet().forEach(entry -> {
            if (!entry.getKey().matches("[a-zA-Z0-9_.:/-]{1,256}")
                    || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isNumber()
                    || replayState.putIfAbsent(entry.getKey(), entry.getValue().getAsLong()) != null) {
                throw new IllegalArgumentException("Reproduction state entry is invalid");
            }
        });
        replayState.put("runtime.captured_revision", capturedRevision);
        long previous = -1;
        int matched = 0;
        int mismatched = 0;
        var counts = new LinkedHashMap<String, Long>();
        var canonical = new StringBuilder();
        JsonArray events = root.getAsJsonArray("events");
        if (events.size() > 256) {
            throw new IllegalArgumentException("Reproduction event count exceeds capacity");
        }
        for (var value : events) {
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException("Reproduction event is invalid");
            }
            JsonObject event = value.getAsJsonObject();
            Set<String> eventFields = version == 1
                    ? Set.of("sequence", "type", "subject", "amount")
                    : Set.of("sequence", "type", "subject", "amount", "state_revision");
            if (!event.keySet().equals(eventFields)) {
                throw new IllegalArgumentException("Reproduction event fields are invalid");
            }
            long sequence = event.get("sequence").getAsLong();
            if (sequence <= previous) {
                throw new IllegalArgumentException("Reproduction events are not ordered");
            }
            previous = sequence;
            String type = event.get("type").getAsString();
            String subject = event.get("subject").getAsString();
            long amount = event.get("amount").getAsLong();
            long eventRevision = version == 1 ? capturedRevision : event.get("state_revision").getAsLong();
            if (!type.matches("[a-zA-Z0-9_.-]{1,64}") || subject.isBlank() || subject.length() > 256
                    || amount < 0 || amount > 1 || eventRevision < 0) {
                throw new IllegalArgumentException("Reproduction event value is invalid");
            }
            ReplayDecision decision = new ReplayDecision(
                    sequence, type, subject, amount == 1L, eventRevision);
            DecisionOutcome outcome = Objects.requireNonNull(
                    executor.evaluate(decision, Collections.unmodifiableMap(replayState)),
                    "replay decision outcome");
            boolean same = decision.expectedAllowed() == outcome.allowed();
            if (same) {
                matched++;
            } else {
                mismatched++;
            }
            counts.merge(type, outcome.allowed() ? 1L : 0L, Math::addExact);
            replayState.merge("runtime.evaluated." + safeKey(type), 1L, Math::addExact);
            replayState.merge("runtime.allowed." + safeKey(type), outcome.allowed() ? 1L : 0L, Math::addExact);
            replayState.merge("runtime.mismatched", same ? 0L : 1L, Math::addExact);
            replayState.put("runtime.last_revision", outcome.stateRevision());
            canonical.append(sequence).append('\n').append(type).append('\n')
                    .append(subject).append('\n').append(decision.expectedAllowed()).append('\n')
                    .append(outcome.allowed()).append('\n').append(outcome.stateRevision()).append('\n')
                    .append(outcome.reason()).append('\n');
        }
        String stateDigest = digest(seed + "\n" + replayState.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("\n")));
        canonical.insert(0, definitionDigest + "\n" + stateDigest + "\n");
        return new ReplayResult(definitionDigest, events.size(), matched, mismatched,
                Map.copyOf(counts), Collections.unmodifiableMap(new LinkedHashMap<>(replayState)),
                stateDigest, digest(canonical.toString()));
    }

    private static DecisionOutcome evaluateDeterministically(
            ReplayDecision decision,
            Map<String, Long> state
    ) {
        boolean nonnegativeState = state.values().stream().allMatch(value -> value >= 0);
        boolean allowed = switch (decision.type()) {
            case "award", "xp" -> nonnegativeState && validId(decision.subject());
            case "ability" -> validId(decision.subject())
                    && state.getOrDefault("blocked.ability." + safeKey(decision.subject()), 0L) == 0L;
            case "lock" -> decision.subject().contains(". ")
                    && state.getOrDefault("blocked.lock." + safeKey(decision.subject()), 0L) == 0L;
            case "creator" -> state.getOrDefault("creator.enabled", 1L) == 1L;
            case "pvp_assist" -> validUuid(decision.subject())
                    && state.getOrDefault("pvp.enabled", 1L) == 1L;
            default -> false;
        };
        return new DecisionOutcome(allowed, allowed
                ? "Deterministic replay accepted the captured runtime inputs"
                : "Deterministic replay rejected the captured runtime inputs",
                decision.capturedStateRevision());
    }

    private static boolean validId(String value) {
        try {
            return net.minecraft.resources.ResourceLocation.tryParse(value) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean validUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String safeKey(String value) {
        String result = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return result.substring(0, Math.min(result.length(), 64));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @FunctionalInterface
    public interface DecisionExecutor {
        DecisionOutcome evaluate(ReplayDecision decision, Map<String, Long> capturedState);
    }

    public record ReplayDecision(
            long sequence,
            String type,
            String subject,
            boolean expectedAllowed,
            long capturedStateRevision
    ) {
    }

    public record DecisionOutcome(boolean allowed, String reason, long stateRevision) {
        public DecisionOutcome {
            reason = Objects.requireNonNull(reason, "reason").strip();
            if (reason.isEmpty() || reason.length() > 512 || stateRevision < 0) {
                throw new IllegalArgumentException("Replay decision outcome is invalid");
            }
        }
    }

    public record ReplayResult(
            String definitionDigest,
            int events,
            int matchedEvents,
            int mismatchedEvents,
            Map<String, Long> categoryTotals,
            Map<String, Long> finalState,
            String stateDigest,
            String replayDigest
    ) {
    }
}
