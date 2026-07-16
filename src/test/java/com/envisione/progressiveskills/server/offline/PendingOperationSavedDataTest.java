package com.envisione.progressiveskills.server.offline;

import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingOperationSavedDataTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final UUID ISSUER = UUID.fromString("00000000-0000-0000-0000-000000000602");
    private static final DefinitionRevision DEFINITION = new DefinitionRevision(2, "c".repeat(64));

    @Test
    void queueRoundTripsAndRetainsAttemptUntilExplicitConsumption() {
        var store = new PendingOperationSavedData();
        PendingProgressionOperation operation = operation(UUID.randomUUID());
        store.queue(operation);
        UUID attempt = UUID.randomUUID();
        store.recordAttempt(operation.operationId(), attempt);

        CompoundTag encoded = store.save(new CompoundTag(), null);
        PendingOperationSavedData decoded = PendingOperationSavedData.load(encoded, null);

        assertEquals(1, decoded.pendingFor(TARGET).size());
        assertEquals(attempt, decoded.pendingFor(TARGET).getFirst().lastAttemptId().orElseThrow());
        decoded.consume(operation.operationId());
        assertTrue(decoded.pendingFor(TARGET).isEmpty());
    }

    @Test
    void changedOrExpiredOperationsCanBeQuarantinedWithoutDroppingTheirEvidence() {
        var store = new PendingOperationSavedData();
        PendingProgressionOperation operation = operation(UUID.randomUUID());
        store.queue(operation);
        store.quarantine(operation.operationId(), "definition changed");

        assertTrue(store.pendingFor(TARGET).isEmpty());
        assertEquals(PendingOperationStatus.QUARANTINED, store.all().getFirst().status());
        assertEquals("definition changed", store.all().getFirst().quarantineReason().orElseThrow());
    }

    @Test
    void malformedStoreFailsClosedAndRefusesNewQueueEntries() {
        var malformed = new CompoundTag();
        malformed.putInt("data_version", 99);
        PendingOperationSavedData decoded = PendingOperationSavedData.load(malformed, null);

        assertTrue(decoded.storeQuarantine().isPresent());
        assertThrows(IllegalStateException.class, () -> decoded.queue(operation(UUID.randomUUID())));
    }

    private static PendingProgressionOperation operation(UUID id) {
        Instant created = Instant.parse("2026-07-16T12:00:00Z");
        return PendingProgressionOperation.pending(
                id,
                TARGET,
                ISSUER,
                created,
                created.plusSeconds(3600),
                DEFINITION,
                ResourceLocation.fromNamespaceAndPath("test", "points"),
                5,
                0,
                100,
                false
        );
    }
}
