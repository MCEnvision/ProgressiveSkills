package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.transaction.DefinitionRevision;

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
        int orphanCount,
        int operationReceiptCount,
        boolean quarantined,
        String resultingStateDigest
) {
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
        if (!Collections.disjoint(changedBalances.keySet(), removedBalances)
                || !Collections.disjoint(changedEffectiveValues.keySet(), removedEffectiveValues)) {
            throw new IllegalArgumentException("State delta cannot change and remove the same path");
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
                after.orphanCount(), after.operationReceiptCount(), after.quarantined(), digest
        );
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
}
