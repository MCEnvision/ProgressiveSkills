package com.envisione.progressiveskills.common.data;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** In-flight player-death identity copied to the replacement attachment. */
public record DeathMarker(UUID transactionId, Instant createdAt, boolean keepInventory) {
    public DeathMarker {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
