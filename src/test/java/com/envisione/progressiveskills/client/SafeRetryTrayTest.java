package com.envisione.progressiveskills.client;

import com.envisione.progressiveskills.common.network.NetworkPayloads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeRetryTrayTest {
    @AfterEach
    void clear() {
        SafeRetryTray.clear();
    }

    @Test
    void remembersOnlyAnActionThatCouldNotBeSent() {
        var calls = new AtomicInteger();
        assertTrue(SafeRetryTray.sendOrRemember("sent", () -> {
            calls.incrementAndGet();
            return true;
        }));
        assertTrue(SafeRetryTray.label().isEmpty());
        assertFalse(SafeRetryTray.retry());
        assertEquals(1, calls.get());
    }

    @Test
    void retryRebuildsThePendingActionUntilItCanBeSent() {
        var ready = new AtomicBoolean();
        var calls = new AtomicInteger();
        assertFalse(SafeRetryTray.sendOrRemember("pending", () -> {
            calls.incrementAndGet();
            return ready.get();
        }));
        assertEquals("pending", SafeRetryTray.label().orElseThrow());
        assertFalse(SafeRetryTray.retry());
        ready.set(true);
        assertTrue(SafeRetryTray.retry());
        assertTrue(SafeRetryTray.label().isEmpty());
        assertEquals(3, calls.get());
    }

    @Test
    void retainsOnlyMatchingRetryableServerRejections() {
        var request = new AtomicInteger(4);
        assertTrue(SafeRetryTray.sendOrRemember("stale", () -> {
            SafeRetryTray.onIntentSent(request.get());
            return true;
        }));
        assertEquals("stale", SafeRetryTray.label().orElseThrow());

        SafeRetryTray.onIntentResult(result(3, NetworkPayloads.IntentStatus.STALE_STATE));
        assertFalse(SafeRetryTray.retry());
        SafeRetryTray.onIntentResult(result(4, NetworkPayloads.IntentStatus.STALE_STATE));
        request.set(5);
        assertTrue(SafeRetryTray.retry());
        SafeRetryTray.onIntentResult(result(5, NetworkPayloads.IntentStatus.ACCEPTED));
        assertTrue(SafeRetryTray.label().isEmpty());
    }

    @Test
    void rejectsAnInvalidUiActionWithoutCrashingTheClient() {
        assertFalse(SafeRetryTray.sendOrRemember("invalid", () -> {
            throw new IllegalArgumentException("Ability intent references an unowned ability");
        }));
        assertTrue(SafeRetryTray.label().isEmpty());
        assertFalse(SafeRetryTray.retry());
    }

    private static NetworkPayloads.IntentResult result(
            long requestId,
            NetworkPayloads.IntentStatus status
    ) {
        return new NetworkPayloads.IntentResult(
                UUID.randomUUID(), requestId, UUID.randomUUID(), status, 1L, "Result", false);
    }
}
