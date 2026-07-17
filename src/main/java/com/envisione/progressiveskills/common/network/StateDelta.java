package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/** One continuity checked semantic state delta. */
public record StateDelta(
        UUID playerId,
        long baseSyncRevision,
        long newSyncRevision,
        long newStateRevision,
        DefinitionRevision definitionRevision,
        long presentationRevision,
        String presentationDigest,
        Map<String, Long> changedBalances,
        Set<String> removedBalances,
        Map<String, Long> changedEffectiveValues,
        Set<String> removedEffectiveValues,
        Map<ResourceLocation, Integer> changedNodeRanks,
        Set<ResourceLocation> removedNodeRanks,
        Map<ResourceLocation, VisiblePlayerState.ClassSelection> changedSelectedClasses,
        Set<ResourceLocation> removedSelectedClasses,
        int orphanCount,
        int operationReceiptCount,
        boolean quarantined,
        String resultingStateDigest
) {
    public StateDelta(
            UUID playerId,
            long baseSyncRevision,
            long newSyncRevision,
            long newStateRevision,
            DefinitionRevision definitionRevision,
            long presentationRevision,
            String presentationDigest,
            Map<String, Long> changedBalances,
            Set<String> removedBalances,
            Map<String, Long> changedEffectiveValues,
            Set<String> removedEffectiveValues,
            int orphanCount,
            int operationReceiptCount,
            boolean quarantined,
            String resultingStateDigest
    ) {
        this(playerId, baseSyncRevision, newSyncRevision, newStateRevision, definitionRevision,
                presentationRevision, presentationDigest, changedBalances, removedBalances,
                changedEffectiveValues, removedEffectiveValues, Map.of(), Set.of(), orphanCount,
                operationReceiptCount, quarantined, resultingStateDigest);
    }

    public StateDelta(
            UUID playerId,
            long baseSyncRevision,
            long newSyncRevision,
            long newStateRevision,
            DefinitionRevision definitionRevision,
            long presentationRevision,
            String presentationDigest,
            Map<String, Long> changedBalances,
            Set<String> removedBalances,
            Map<String, Long> changedEffectiveValues,
            Set<String> removedEffectiveValues,
            Map<ResourceLocation, Integer> changedNodeRanks,
            Set<ResourceLocation> removedNodeRanks,
            int orphanCount,
            int operationReceiptCount,
            boolean quarantined,
            String resultingStateDigest
    ) {
        this(playerId, baseSyncRevision, newSyncRevision, newStateRevision, definitionRevision,
                presentationRevision, presentationDigest, changedBalances, removedBalances,
                changedEffectiveValues, removedEffectiveValues, changedNodeRanks, removedNodeRanks,
                Map.of(), Set.of(), orphanCount, operationReceiptCount, quarantined,
                resultingStateDigest);
    }

    public StateDelta {
        Objects.requireNonNull(playerId, "playerId");
        if (baseSyncRevision < 0 || newSyncRevision <= baseSyncRevision || newStateRevision < 0
                || presentationRevision < 0 || orphanCount < 0 || operationReceiptCount < 0) {
            throw new IllegalArgumentException("State delta revisions and counts are invalid");
        }
        Objects.requireNonNull(definitionRevision, "definitionRevision");
        presentationDigest = NetworkLimits.requireDigest(presentationDigest, "presentationDigest");
        changedBalances = copyMap(changedBalances);
        removedBalances = copySet(removedBalances);
        changedEffectiveValues = copyMap(changedEffectiveValues);
        removedEffectiveValues = copySet(removedEffectiveValues);
        changedNodeRanks = copyNodeRanks(changedNodeRanks);
        removedNodeRanks = copyNodeIds(removedNodeRanks);
        changedSelectedClasses = copyClassSelections(changedSelectedClasses);
        removedSelectedClasses = copyNodeIds(removedSelectedClasses);
        if (!Collections.disjoint(changedBalances.keySet(), removedBalances)
                || !Collections.disjoint(changedEffectiveValues.keySet(), removedEffectiveValues)) {
            throw new IllegalArgumentException("State delta cannot change and remove the same path");
        }
        if (!Collections.disjoint(changedNodeRanks.keySet(), removedNodeRanks)) {
            throw new IllegalArgumentException("State delta cannot change and remove the same node rank");
        }
        if (!Collections.disjoint(changedSelectedClasses.keySet(), removedSelectedClasses)) {
            throw new IllegalArgumentException("State delta cannot change and remove the same selected class");
        }
        resultingStateDigest = NetworkLimits.requireDigest(resultingStateDigest, "resultingStateDigest");
    }

    public static StateDelta between(VisiblePlayerState before, VisiblePlayerState after, String digest) {
        if (!before.playerId().equals(after.playerId())
                || !before.definitionRevision().equals(after.definitionRevision())
                || before.presentationRevision() != after.presentationRevision()
                || !before.presentationDigest().equals(after.presentationDigest())) {
            throw new IllegalArgumentException("A delta cannot cross player or definition generations");
        }
        return new StateDelta(
                before.playerId(), before.syncRevision(), after.syncRevision(), after.stateRevision(),
                after.definitionRevision(), after.presentationRevision(), after.presentationDigest(),
                changes(before.balances(), after.balances()), removals(before.balances(), after.balances()),
                changes(before.effectiveValues(), after.effectiveValues()),
                removals(before.effectiveValues(), after.effectiveValues()),
                nodeChanges(before.nodeRanks(), after.nodeRanks()),
                nodeRemovals(before.nodeRanks(), after.nodeRanks()),
                classChanges(before.selectedClasses(), after.selectedClasses()),
                classRemovals(before.selectedClasses(), after.selectedClasses()),
                after.orphanCount(), after.operationReceiptCount(), after.quarantined(), digest
        );
    }

    private static Map<ResourceLocation, VisiblePlayerState.ClassSelection> classChanges(
            Map<ResourceLocation, VisiblePlayerState.ClassSelection> before,
            Map<ResourceLocation, VisiblePlayerState.ClassSelection> after
    ) {
        var changed = new TreeMap<ResourceLocation, VisiblePlayerState.ClassSelection>(
                ResourceLocation::compareNamespaced);
        after.forEach((key, value) -> {
            if (!Objects.equals(before.get(key), value)) {
                changed.put(key, value);
            }
        });
        return changed;
    }

    private static Set<ResourceLocation> classRemovals(
            Map<ResourceLocation, VisiblePlayerState.ClassSelection> before,
            Map<ResourceLocation, VisiblePlayerState.ClassSelection> after
    ) {
        var removed = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        removed.addAll(before.keySet());
        removed.removeAll(after.keySet());
        return removed;
    }

    private static Map<ResourceLocation, Integer> nodeChanges(
            Map<ResourceLocation, Integer> before,
            Map<ResourceLocation, Integer> after
    ) {
        var changed = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        after.forEach((key, value) -> {
            if (!Objects.equals(before.get(key), value)) {
                changed.put(key, value);
            }
        });
        return changed;
    }

    private static Set<ResourceLocation> nodeRemovals(
            Map<ResourceLocation, Integer> before,
            Map<ResourceLocation, Integer> after
    ) {
        var removed = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        removed.addAll(before.keySet());
        removed.removeAll(after.keySet());
        return removed;
    }

    private static Map<String, Long> changes(Map<String, Long> before, Map<String, Long> after) {
        var changed = new TreeMap<String, Long>();
        after.forEach((key, value) -> {
            if (!Objects.equals(before.get(key), value)) {
                changed.put(key, value);
            }
        });
        return changed;
    }

    private static Set<String> removals(Map<String, Long> before, Map<String, Long> after) {
        var removed = new TreeSet<>(before.keySet());
        removed.removeAll(after.keySet());
        return removed;
    }

    private static Map<String, Long> copyMap(Map<String, Long> source) {
        Objects.requireNonNull(source, "delta map");
        if (source.size() > NetworkLimits.MAX_VISIBLE_VALUES) {
            throw new IllegalArgumentException("State delta map exceeds capacity");
        }
        var sorted = new TreeMap<String, Long>();
        source.forEach((key, value) -> sorted.put(
                NetworkLimits.requireBoundedText(key, NetworkLimits.MAX_KEY_BYTES, "delta key"),
                Objects.requireNonNull(value, "delta value")
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Set<String> copySet(Set<String> source) {
        Objects.requireNonNull(source, "delta set");
        if (source.size() > NetworkLimits.MAX_VISIBLE_VALUES) {
            throw new IllegalArgumentException("State delta removal set exceeds capacity");
        }
        var sorted = new TreeSet<String>();
        source.forEach(value -> sorted.add(NetworkLimits.requireBoundedText(
                value, NetworkLimits.MAX_KEY_BYTES, "removed delta key")));
        return Collections.unmodifiableSet(sorted);
    }

    private static Map<ResourceLocation, Integer> copyNodeRanks(Map<ResourceLocation, Integer> source) {
        Objects.requireNonNull(source, "node rank delta");
        if (source.size() > NetworkLimits.MAX_VISIBLE_VALUES) {
            throw new IllegalArgumentException("State delta node ranks exceed capacity");
        }
        var sorted = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        source.forEach((node, rank) -> {
            ResourceLocation stable = StableId.requireValid(node);
            if (rank == null || rank != 1) {
                throw new IllegalArgumentException("Core node rank delta must equal one");
            }
            sorted.put(stable, rank);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Set<ResourceLocation> copyNodeIds(Set<ResourceLocation> source) {
        Objects.requireNonNull(source, "removed node ranks");
        if (source.size() > NetworkLimits.MAX_VISIBLE_VALUES) {
            throw new IllegalArgumentException("State delta removed node ranks exceed capacity");
        }
        var sorted = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        source.forEach(node -> sorted.add(StableId.requireValid(node)));
        return Collections.unmodifiableSet(sorted);
    }

    private static Map<ResourceLocation, VisiblePlayerState.ClassSelection> copyClassSelections(
            Map<ResourceLocation, VisiblePlayerState.ClassSelection> source
    ) {
        Objects.requireNonNull(source, "selected class delta");
        if (source.size() > NetworkLimits.MAX_VISIBLE_VALUES) {
            throw new IllegalArgumentException("State delta selected classes exceed capacity");
        }
        var sorted = new TreeMap<ResourceLocation, VisiblePlayerState.ClassSelection>(
                ResourceLocation::compareNamespaced);
        source.forEach((classId, state) -> sorted.put(
                StableId.requireValid(classId), Objects.requireNonNull(state, "selected class state")));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
