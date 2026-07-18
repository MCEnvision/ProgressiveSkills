package com.envisione.progressiveskills.common.carrier;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

public record PendingCarrierClaim(
        UUID claimId,
        UUID originDeliveryId,
        CarrierIdentity identity,
        CarrierKind kind,
        CarrierStackState state,
        CarrierBehaviorSnapshot behavior,
        Instant createdAt,
        String reason
) {
    public static final int MAX_REASON_BYTES = 256;
    public static final Comparator<PendingCarrierClaim> ORDER = Comparator
            .comparing(PendingCarrierClaim::createdAt)
            .thenComparing(PendingCarrierClaim::claimId);

    public PendingCarrierClaim {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(originDeliveryId, "originDeliveryId");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(behavior, "behavior");
        Objects.requireNonNull(createdAt, "createdAt");
        reason = requireReason(reason);
        try {
            if (createdAt.toEpochMilli() < 0) {
                throw new IllegalArgumentException("Pending claim creation time must not be negative");
            }
        } catch (ArithmeticException | DateTimeException exception) {
            throw new IllegalArgumentException("Pending claim creation time is outside its bound", exception);
        }
        if (!behavior.definitionId().equals(identity.definitionId())
                || behavior.carrier() != kind
                || behavior.behaviorVersion() != state.behaviorVersion()
                || !behavior.digest().equals(identity.behaviorDigest())) {
            throw new IllegalArgumentException("Pending claim behavior does not match its carrier identity");
        }
        if (state.charges() > behavior.charges()) {
            throw new IllegalArgumentException("Pending claim charges exceed the pinned behavior");
        }
    }

    private static String requireReason(String value) {
        Objects.requireNonNull(value, "reason");
        String normalized = value.strip();
        if (normalized.isEmpty()
                || normalized.getBytes(StandardCharsets.UTF_8).length > MAX_REASON_BYTES
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Pending claim reason exceeds its safe text contract");
        }
        return normalized;
    }
}
