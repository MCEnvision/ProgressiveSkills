package com.envisione.progressiveskills.client;

import com.envisione.progressiveskills.common.network.NetworkPayloads;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class SafeRetryTray {
    private static Optional<RetryableAction> pending = Optional.empty();

    private SafeRetryTray() {
    }

    public static boolean sendOrRemember(String label, BooleanSupplier sender) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(sender, "sender");
        pending = Optional.of(new RetryableAction(label, sender, -1L, false));
        boolean sent = sender.getAsBoolean();
        if (!sent) {
            pending = Optional.of(new RetryableAction(label, sender, -1L, true));
        } else if (pending.filter(action -> action.requestId() < 0).isPresent()) {
            pending = Optional.empty();
        }
        return sent;
    }

    public static void onIntentSent(long requestId) {
        if (requestId < 0) {
            throw new IllegalArgumentException("Intent request id must not be negative");
        }
        pending.filter(action -> !action.ready() && action.requestId() < 0)
                .ifPresent(action -> pending = Optional.of(new RetryableAction(
                        action.label(), action.sender(), requestId, false)));
    }

    public static void onIntentResult(NetworkPayloads.IntentResult result) {
        Objects.requireNonNull(result, "result");
        if (pending.filter(action -> action.requestId() == result.requestId()).isEmpty()) {
            return;
        }
        switch (result.status()) {
            case STALE_SESSION, STALE_DEFINITION, STALE_STATE, RATE_LIMITED -> pending = pending.map(
                    action -> new RetryableAction(action.label(), action.sender(), -1L, true));
            case ACCEPTED, TOO_OLD, FUTURE_JUMP, INVALID -> pending = Optional.empty();
        }
    }

    public static Optional<String> label() {
        return pending.map(RetryableAction::label);
    }

    public static boolean retry() {
        if (pending.filter(RetryableAction::ready).isEmpty()) {
            return false;
        }
        RetryableAction action = pending.orElseThrow();
        pending = Optional.of(new RetryableAction(action.label(), action.sender(), -1L, false));
        boolean sent = action.sender().getAsBoolean();
        if (!sent) {
            pending = Optional.of(new RetryableAction(action.label(), action.sender(), -1L, true));
        } else if (pending.filter(value -> value.requestId() < 0).isPresent()) {
            pending = Optional.empty();
        }
        return sent;
    }

    public static void clear() {
        pending = Optional.empty();
    }

    private record RetryableAction(
            String label,
            BooleanSupplier sender,
            long requestId,
            boolean ready
    ) {
        private RetryableAction {
            label = Objects.requireNonNull(label, "label");
            Objects.requireNonNull(sender, "sender");
            if (requestId < -1) {
                throw new IllegalArgumentException("Retry request id must not be below minus one");
            }
        }
    }
}
