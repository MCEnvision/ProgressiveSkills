package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.PersistedTransactionState;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import net.minecraft.nbt.CompoundTag;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Mutable server-thread-confined authority stored in the versioned player data attachment. */
public final class ProgressiveSkillsData {
    public static final int CURRENT_DATA_VERSION = 3;
    public static final int MAX_DEFINITION_STATES = 4_096;
    public static final int MAX_ORPHANS = 4_096;
    public static final int MAX_OPERATION_RECEIPTS = 512;

    private final UUID playerId;
    private long storageRevision;
    private PlayerDataStatus status;
    private PersistedTransactionState transactionState;
    private Optional<DefinitionRevision> stateDefinition;
    private final Map<DefinitionKey, StoredDefinitionState> definitionStates;
    private final Map<DefinitionKey, OrphanRecord> orphans;
    private final Map<UUID, OperationReceipt> operationReceipts;
    private Optional<DeathMarker> deathMarker;
    private Optional<MigrationShadow> migrationShadow;
    private Optional<QuarantineRecord> quarantine;
    private CompoundTag unknownExtensions;
    private boolean loginObserved;

    private ProgressiveSkillsData(ProgressiveSkillsDataView view) {
        playerId = view.playerId();
        storageRevision = view.storageRevision();
        status = view.status();
        transactionState = view.transactionState();
        stateDefinition = view.stateDefinition();
        definitionStates = new TreeMap<>(view.definitionStates());
        orphans = new TreeMap<>(view.orphans());
        operationReceipts = new TreeMap<>(view.operationReceipts());
        deathMarker = view.deathMarker();
        migrationShadow = view.migrationShadow();
        quarantine = view.quarantine();
        unknownExtensions = view.unknownExtensions();
    }

    public static ProgressiveSkillsData empty(UUID playerId) {
        return fromView(new ProgressiveSkillsDataView(
                playerId,
                0,
                PlayerDataStatus.ACTIVE,
                PersistedTransactionState.empty(),
                Optional.empty(),
                Map.of(),
                Map.of(),
                Map.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                new CompoundTag()
        ));
    }

    public static ProgressiveSkillsData quarantined(UUID playerId, QuarantineRecord quarantine) {
        return fromView(new ProgressiveSkillsDataView(
                playerId,
                0,
                PlayerDataStatus.QUARANTINED,
                PersistedTransactionState.empty(),
                Optional.empty(),
                Map.of(),
                Map.of(),
                Map.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(quarantine),
                new CompoundTag()
        ));
    }

    static ProgressiveSkillsData fromView(ProgressiveSkillsDataView view) {
        return new ProgressiveSkillsData(Objects.requireNonNull(view, "view"));
    }

    public synchronized UUID playerId() {
        return playerId;
    }

    public synchronized boolean active() {
        return status == PlayerDataStatus.ACTIVE;
    }

    public synchronized ProgressiveSkillsDataView view() {
        return createView(migrationShadow);
    }

    synchronized ProgressiveSkillsDataView viewForSave() {
        Optional<MigrationShadow> outputShadow = migrationShadow;
        if (loginObserved && migrationShadow.isPresent()) {
            MigrationShadow shadow = migrationShadow.orElseThrow();
            if (shadow.status() == MigrationShadowStatus.FRESH) {
                MigrationShadow persisted = shadow.persistedAfterLogin();
                outputShadow = Optional.of(persisted);
                migrationShadow = Optional.of(persisted);
            } else {
                outputShadow = Optional.empty();
                migrationShadow = Optional.empty();
                incrementStorageRevision();
            }
        }
        return createView(outputShadow);
    }

    public synchronized void onLogin() {
        loginObserved = true;
    }

    public synchronized void replaceTransactionState(
            PersistedTransactionState state,
            DefinitionRevision definition
    ) {
        requireActive();
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(definition, "definition");
        if (transactionState.equals(state) && stateDefinition.equals(Optional.of(definition))) {
            return;
        }
        transactionState = state;
        stateDefinition = Optional.of(definition);
        incrementStorageRevision();
    }

    public synchronized PersistedTransactionState transactionState() {
        return transactionState;
    }

    public synchronized Optional<DefinitionRevision> stateDefinition() {
        return stateDefinition;
    }

    public synchronized void putDefinitionState(StoredDefinitionState state) {
        requireActive();
        Objects.requireNonNull(state, "state");
        if (!definitionStates.containsKey(state.key()) && definitionStates.size() >= MAX_DEFINITION_STATES) {
            throw new IllegalStateException("Definition-state capacity is full");
        }
        definitionStates.put(state.key(), state);
        incrementStorageRevision();
    }

    public synchronized DefinitionReconciliationReport reconcileDefinitions(
            Set<DefinitionKey> liveDefinitions,
            Map<DefinitionKey, String> liveLineages,
            AliasMap aliases
    ) {
        requireActive();
        Objects.requireNonNull(liveDefinitions, "liveDefinitions");
        Objects.requireNonNull(liveLineages, "liveLineages");
        Objects.requireNonNull(aliases, "aliases");
        if (!liveLineages.keySet().containsAll(liveDefinitions)) {
            throw new IllegalArgumentException("Every live definition requires a lineage digest");
        }

        int renamed = 0;
        int orphaned = 0;
        int restored = 0;
        var nextActive = new TreeMap<DefinitionKey, StoredDefinitionState>();
        var nextOrphans = new TreeMap<>(orphans);
        for (StoredDefinitionState state : definitionStates.values()) {
            DefinitionKey target = aliases.resolveTerminal(state.key());
            boolean explicitReplacement = !target.equals(state.key());
            String targetLineage = liveLineages.get(target);
            if (!liveDefinitions.contains(target) || targetLineage == null) {
                orphan(nextOrphans, state, "Definition is absent from the live registry");
                orphaned++;
                continue;
            }
            if (!explicitReplacement && !state.lineage().equals(targetLineage)) {
                orphan(nextOrphans, state, "Definition id was reused with an incompatible lineage");
                orphaned++;
                continue;
            }
            StoredDefinitionState resolved = explicitReplacement ? state.rekey(target, targetLineage) : state;
            if (nextActive.putIfAbsent(target, resolved) != null) {
                orphan(nextOrphans, state, "Replacement collides with existing state for " + target);
                orphaned++;
                continue;
            }
            if (explicitReplacement) {
                renamed++;
            }
        }

        for (var entry : new TreeMap<>(orphans).entrySet()) {
            StoredDefinitionState state = entry.getValue().state();
            DefinitionKey target = aliases.resolveTerminal(state.key());
            boolean explicitReplacement = !target.equals(state.key());
            String targetLineage = liveLineages.get(target);
            boolean compatible = liveDefinitions.contains(target) && targetLineage != null
                    && (explicitReplacement || state.lineage().equals(targetLineage));
            if (!compatible || nextActive.containsKey(target)) {
                continue;
            }
            nextActive.put(target, explicitReplacement ? state.rekey(target, targetLineage) : state);
            nextOrphans.remove(entry.getKey());
            restored++;
            if (explicitReplacement) {
                renamed++;
            }
        }

        if (nextActive.size() > MAX_DEFINITION_STATES || nextOrphans.size() > MAX_ORPHANS) {
            throw new IllegalStateException("Definition reconciliation exceeds attachment capacity");
        }
        if (!definitionStates.equals(nextActive) || !orphans.equals(nextOrphans)) {
            definitionStates.clear();
            definitionStates.putAll(nextActive);
            orphans.clear();
            orphans.putAll(nextOrphans);
            incrementStorageRevision();
        }
        return new DefinitionReconciliationReport(renamed, orphaned, restored, definitionStates.size());
    }

    public synchronized Optional<OperationReceipt> operationReceipt(UUID operationId) {
        return Optional.ofNullable(operationReceipts.get(Objects.requireNonNull(operationId, "operationId")));
    }

    public synchronized boolean canAcceptOperationReceipt(UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        return operationReceipts.containsKey(operationId) || operationReceipts.size() < MAX_OPERATION_RECEIPTS;
    }

    public synchronized void putOperationReceipt(OperationReceipt receipt) {
        requireActive();
        Objects.requireNonNull(receipt, "receipt");
        if (!operationReceipts.containsKey(receipt.operationId())
                && operationReceipts.size() >= MAX_OPERATION_RECEIPTS) {
            throw new IllegalStateException("Operation-receipt capacity is full");
        }
        OperationReceipt existing = operationReceipts.putIfAbsent(receipt.operationId(), receipt);
        if (existing != null && !existing.equals(receipt)) {
            throw new IllegalStateException("Operation receipt identity collision");
        }
        if (existing == null) {
            incrementStorageRevision();
        }
    }

    public synchronized DeathMarker prepareDeath(UUID deathId, Instant createdAt, boolean keepInventory) {
        requireActive();
        if (deathMarker.isPresent()) {
            return deathMarker.orElseThrow();
        }
        if (!canAcceptOperationReceipt(deathId)) {
            throw new IllegalStateException("Operation-receipt capacity is full; death operation was not prepared");
        }
        DeathMarker marker = new DeathMarker(deathId, createdAt, keepInventory);
        deathMarker = Optional.of(marker);
        incrementStorageRevision();
        return marker;
    }

    public synchronized Optional<OperationReceipt> completeDeath(Instant completedAt) {
        requireActive();
        if (deathMarker.isEmpty()) {
            return Optional.empty();
        }
        DeathMarker marker = deathMarker.orElseThrow();
        OperationReceipt existing = operationReceipts.get(marker.transactionId());
        if (existing != null) {
            deathMarker = Optional.empty();
            incrementStorageRevision();
            return Optional.of(existing);
        }
        var receipt = new OperationReceipt(
                marker.transactionId(),
                "death",
                new TransactionId(marker.transactionId()),
                transactionState.stateRevision(),
                completedAt,
                "Death clone completed without a configured Phase 5 progression penalty"
        );
        putOperationReceipt(receipt);
        deathMarker = Optional.empty();
        incrementStorageRevision();
        return Optional.of(receipt);
    }

    public synchronized ProgressiveSkillsData copyFor(UUID newPlayerId) {
        if (!playerId.equals(newPlayerId)) {
            throw new IllegalArgumentException("Player attachment cannot be copied to a different UUID");
        }
        return fromView(view());
    }

    private ProgressiveSkillsDataView createView(Optional<MigrationShadow> outputShadow) {
        return new ProgressiveSkillsDataView(
                playerId,
                storageRevision,
                status,
                transactionState,
                stateDefinition,
                definitionStates,
                orphans,
                operationReceipts,
                deathMarker,
                outputShadow,
                quarantine,
                unknownExtensions
        );
    }

    private static void orphan(
            Map<DefinitionKey, OrphanRecord> target,
            StoredDefinitionState state,
            String reason
    ) {
        if (target.size() >= MAX_ORPHANS && !target.containsKey(state.key())) {
            throw new IllegalStateException("Orphan capacity is full");
        }
        target.put(state.key(), new OrphanRecord(state, reason));
    }

    private void requireActive() {
        if (status != PlayerDataStatus.ACTIVE) {
            throw new IllegalStateException("Quarantined player data cannot be mutated or projected");
        }
    }

    private void incrementStorageRevision() {
        storageRevision = Math.addExact(storageRevision, 1);
    }
}
