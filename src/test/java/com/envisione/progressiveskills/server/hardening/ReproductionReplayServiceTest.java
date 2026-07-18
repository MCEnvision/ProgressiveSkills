package com.envisione.progressiveskills.server.hardening;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReproductionReplayServiceTest {
    @TempDir
    Path temporary;

    @Test
    void replayIsDeterministicAndAppliesCapturedDecisions() throws Exception {
        Path bundle = temporary.resolve("bundle.json");
        Files.writeString(bundle, bundle("0,1"));

        var first = ReproductionReplayService.replay(bundle, "a".repeat(64));
        var second = ReproductionReplayService.replay(bundle, "a".repeat(64));

        assertEquals(first, second);
        assertEquals(2, first.events());
        assertEquals(2L, first.categoryTotals().get("award"));
        assertEquals(1, first.matchedEvents());
        assertEquals(1, first.mismatchedEvents());
        assertEquals(2L, first.finalState().get("runtime.evaluated.award"));
        assertNotEquals("0".repeat(64), first.stateDigest());
    }

    @Test
    void replayUsesProvidedRuntimeExecutor() throws Exception {
        Path bundle = temporary.resolve("runtime.json");
        Files.writeString(bundle, bundle("0,1"));

        var replay = ReproductionReplayService.replay(bundle, "a".repeat(64),
                (decision, state) -> new ReproductionReplayService.DecisionOutcome(
                        decision.expectedAllowed(), "Test runtime decision", 9L));

        assertEquals(2, replay.matchedEvents());
        assertEquals(0, replay.mismatchedEvents());
        assertEquals(9L, replay.finalState().get("runtime.last_revision"));
    }

    @Test
    void capturedExecutorUsesOnlyBundleState() {
        var state = new LinkedHashMap<String, Long>();
        state.put("replay.data_active", 1L);
        String prefix = CapturedDecisionReplayExecutor.statePrefix("xp", "test:first");
        state.put(prefix + ".known", 1L);
        state.put(prefix + ".eligible", 1L);
        var decision = new ReproductionReplayService.ReplayDecision(
                0L, "xp", "test:first", true, 4L);
        var executor = new CapturedDecisionReplayExecutor();

        var first = executor.evaluate(decision, Map.copyOf(state));
        state.put("unrelated.live.change", 99L);
        var second = executor.evaluate(decision, Map.copyOf(state));

        assertEquals(first, second);
    }

    @Test
    void rejectsOutOfOrderEventsAndWrongDefinition() throws Exception {
        Path bundle = temporary.resolve("invalid.json");
        Files.writeString(bundle, bundle("1,1"));

        assertThrows(IllegalArgumentException.class,
                () -> ReproductionReplayService.replay(bundle, "a".repeat(64)));
        Files.writeString(bundle, bundle("0,1"));
        assertThrows(IllegalArgumentException.class,
                () -> ReproductionReplayService.replay(bundle, "b".repeat(64)));
    }

    private static String bundle(String sequences) {
        String[] values = sequences.split(",");
        return """
                {
                  "format_version": 1,
                  "bundle_id": "00000000-0000-0000-0000-000000000001",
                  "created_at": "2026-01-01T00:00:00Z",
                  "definition_digest": "%s",
                  "definition_generation": 1,
                  "state_revision": 2,
                  "seed": 42,
                  "state": {"balance.test": 3},
                  "events": [
                    {"sequence": %s, "type": "award", "subject": "test:first", "amount": 1},
                    {"sequence": %s, "type": "award", "subject": "test:second", "amount": 0}
                  ]
                }
                """.formatted("a".repeat(64), values[0], values[1]);
    }
}
