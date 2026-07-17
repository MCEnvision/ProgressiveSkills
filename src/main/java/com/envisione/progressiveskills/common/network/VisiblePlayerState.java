package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.transaction.DefinitionRevision;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
        int orphanCount,
        int operationReceiptCount,
        boolean quarantined
) {
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
        return new VisiblePlayerState(
                playerId,
                delta.newSyncRevision(),
                delta.newStateRevision(),
                definitionRevision,
                presentationRevision,
                presentationDigest,
                nextBalances,
                nextValues,
                delta.orphanCount(),
                delta.operationReceiptCount(),
                delta.quarantined()
        );
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
}
