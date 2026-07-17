package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Sanitized authoritative state sent to one client. Durable ledgers and raw attachment data stay on the server. */
public record VisiblePlayerState(
        UUID playerId,
        long syncRevision,
        long stateRevision,
        DefinitionRevision definitionRevision,
        long presentationRevision,
        String presentationDigest,
        Map<String, Long> balances,
        Map<String, Long> effectiveValues,
        Map<ResourceLocation, Integer> nodeRanks,
        Map<ResourceLocation, ClassSelection> selectedClasses,
        int orphanCount,
        int operationReceiptCount,
        boolean quarantined
) {
    public VisiblePlayerState(
            UUID playerId,
            long syncRevision,
            long stateRevision,
            DefinitionRevision definitionRevision,
            long presentationRevision,
            String presentationDigest,
            Map<String, Long> balances,
            Map<String, Long> effectiveValues,
            int orphanCount,
            int operationReceiptCount,
            boolean quarantined
    ) {
        this(playerId, syncRevision, stateRevision, definitionRevision, presentationRevision,
                presentationDigest, balances, effectiveValues, Map.of(), orphanCount,
                operationReceiptCount, quarantined);
    }

    public VisiblePlayerState(
            UUID playerId,
            long syncRevision,
            long stateRevision,
            DefinitionRevision definitionRevision,
            long presentationRevision,
            String presentationDigest,
            Map<String, Long> balances,
            Map<String, Long> effectiveValues,
            Map<ResourceLocation, Integer> nodeRanks,
            int orphanCount,
            int operationReceiptCount,
            boolean quarantined
    ) {
        this(playerId, syncRevision, stateRevision, definitionRevision, presentationRevision,
                presentationDigest, balances, effectiveValues, nodeRanks, Map.of(), orphanCount,
                operationReceiptCount, quarantined);
    }

    public VisiblePlayerState {
        Objects.requireNonNull(playerId, "playerId");
        if (syncRevision < 0 || stateRevision < 0 || presentationRevision < 0
                || orphanCount < 0 || operationReceiptCount < 0) {
            throw new IllegalArgumentException("Visible state revisions and counts must not be negative");
        }
        Objects.requireNonNull(definitionRevision, "definitionRevision");
        presentationDigest = NetworkLimits.requireDigest(presentationDigest, "presentationDigest");
        balances = immutableValues(balances, "balance");
        effectiveValues = immutableValues(effectiveValues, "effective value");
        nodeRanks = immutableNodeRanks(nodeRanks);
        selectedClasses = immutableClassSelections(selectedClasses);
    }

    public VisiblePlayerState apply(StateDelta delta) {
        Objects.requireNonNull(delta, "delta");
        if (!delta.playerId().equals(playerId)
                || delta.baseSyncRevision() != syncRevision
                || !delta.definitionRevision().equals(definitionRevision)
                || delta.presentationRevision() != presentationRevision
                || !delta.presentationDigest().equals(presentationDigest)) {
            throw new IllegalArgumentException("State delta does not continue the active client snapshot");
        }
        var nextBalances = new TreeMap<>(balances);
        delta.removedBalances().forEach(nextBalances::remove);
        nextBalances.putAll(delta.changedBalances());
        var nextValues = new TreeMap<>(effectiveValues);
        delta.removedEffectiveValues().forEach(nextValues::remove);
        nextValues.putAll(delta.changedEffectiveValues());
        var nextNodeRanks = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        nextNodeRanks.putAll(nodeRanks);
        delta.removedNodeRanks().forEach(nextNodeRanks::remove);
        nextNodeRanks.putAll(delta.changedNodeRanks());
        return new VisiblePlayerState(
                playerId,
                delta.newSyncRevision(),
                delta.newStateRevision(),
                definitionRevision,
                presentationRevision,
                presentationDigest,
                nextBalances,
                nextValues,
                nextNodeRanks,
                applyClassSelections(delta),
                delta.orphanCount(),
                delta.operationReceiptCount(),
                delta.quarantined()
        );
    }

    private Map<ResourceLocation, ClassSelection> applyClassSelections(StateDelta delta) {
        var next = new TreeMap<ResourceLocation, ClassSelection>(ResourceLocation::compareNamespaced);
        next.putAll(selectedClasses);
        delta.removedSelectedClasses().forEach(next::remove);
        next.putAll(delta.changedSelectedClasses());
        return next;
    }

    private static Map<ResourceLocation, ClassSelection> immutableClassSelections(
            Map<ResourceLocation, ClassSelection> source
    ) {
        Objects.requireNonNull(source, "selectedClasses");
        if (source.size() > NetworkLimits.MAX_VISIBLE_VALUES) {
            throw new IllegalArgumentException("Visible selected class count exceeds capacity");
        }
        var sorted = new TreeMap<ResourceLocation, ClassSelection>(ResourceLocation::compareNamespaced);
        source.forEach((classId, state) -> sorted.put(
                StableId.requireValid(classId), Objects.requireNonNull(state, "selected class state")));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<ResourceLocation, Integer> immutableNodeRanks(Map<ResourceLocation, Integer> source) {
        Objects.requireNonNull(source, "nodeRanks");
        if (source.size() > NetworkLimits.MAX_VISIBLE_VALUES) {
            throw new IllegalArgumentException("Visible node rank count exceeds capacity");
        }
        var sorted = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        source.forEach((node, rank) -> {
            ResourceLocation stable = StableId.requireValid(node);
            if (rank == null || rank != 1) {
                throw new IllegalArgumentException("Core visible node rank must equal one");
            }
            sorted.put(stable, rank);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<String, Long> immutableValues(Map<String, Long> source, String name) {
        Objects.requireNonNull(source, name + "s");
        if (source.size() > NetworkLimits.MAX_VISIBLE_VALUES) {
            throw new IllegalArgumentException("Visible " + name + " count exceeds capacity");
        }
        var sorted = new TreeMap<String, Long>();
        source.forEach((key, value) -> sorted.put(
                NetworkLimits.requireBoundedText(key, NetworkLimits.MAX_KEY_BYTES, name + " key"),
                Objects.requireNonNull(value, name + " value")
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    public record ClassSelection(
            Optional<ResourceLocation> slotId,
            int slotCost,
            Activity activity
    ) {
        public ClassSelection(ResourceLocation slotId, int slotCost, Activity activity) {
            this(Optional.of(slotId), slotCost, activity);
        }

        public ClassSelection {
            slotId = Objects.requireNonNull(slotId, "slotId").map(StableId::requireValid);
            if (slotCost < 0) {
                throw new IllegalArgumentException("Visible selected class slot cost must not be negative");
            }
            Objects.requireNonNull(activity, "activity");
            if (slotId.isEmpty() && (slotCost != 0 || activity != Activity.SUSPENDED)) {
                throw new IllegalArgumentException("Missing class definitions must remain visibly suspended");
            }
        }
    }

    public enum Activity {
        ACTIVE,
        SUSPENDED
    }
}
