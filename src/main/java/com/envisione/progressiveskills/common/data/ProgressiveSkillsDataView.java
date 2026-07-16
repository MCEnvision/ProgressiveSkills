package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.PersistedTransactionState;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Immutable deep copy used by codecs, snapshots, and diagnostics. */
public record ProgressiveSkillsDataView(
        UUID playerId,
        long storageRevision,
        PlayerDataStatus status,
        PersistedTransactionState transactionState,
        Optional<DefinitionRevision> stateDefinition,
        Map<DefinitionKey, StoredDefinitionState> definitionStates,
        Map<DefinitionKey, OrphanRecord> orphans,
        Map<UUID, OperationReceipt> operationReceipts,
        Optional<DeathMarker> deathMarker,
        Optional<MigrationShadow> migrationShadow,
        Optional<QuarantineRecord> quarantine,
        CompoundTag unknownExtensions
) {
    public ProgressiveSkillsDataView {
        Objects.requireNonNull(playerId, "playerId");
        if (storageRevision < 0) {
            throw new IllegalArgumentException("Storage revision must not be negative");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(transactionState, "transactionState");
        stateDefinition = Objects.requireNonNull(stateDefinition, "stateDefinition");
        definitionStates = immutableSorted(definitionStates);
        orphans = immutableSorted(orphans);
        operationReceipts = immutableSorted(operationReceipts);
        deathMarker = Objects.requireNonNull(deathMarker, "deathMarker");
        migrationShadow = Objects.requireNonNull(migrationShadow, "migrationShadow");
        quarantine = Objects.requireNonNull(quarantine, "quarantine");
        unknownExtensions = Objects.requireNonNull(unknownExtensions, "unknownExtensions").copy();
        if (status == PlayerDataStatus.ACTIVE && quarantine.isPresent()) {
            throw new IllegalArgumentException("Active player data cannot contain a quarantine record");
        }
        if (status == PlayerDataStatus.QUARANTINED && quarantine.isEmpty()) {
            throw new IllegalArgumentException("Quarantined player data requires a quarantine record");
        }
    }

    @Override
    public CompoundTag unknownExtensions() {
        return unknownExtensions.copy();
    }

    private static <K extends Comparable<? super K>, V> Map<K, V> immutableSorted(Map<K, V> source) {
        Objects.requireNonNull(source, "view map");
        var sorted = new TreeMap<K, V>();
        source.forEach((key, value) -> sorted.put(
                Objects.requireNonNull(key, "view key"),
                Objects.requireNonNull(value, "view value")
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
