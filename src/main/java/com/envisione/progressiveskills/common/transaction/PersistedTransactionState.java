package com.envisione.progressiveskills.common.transaction;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Exact durable subset of one account in the single-authority transaction coordinator. */
public record PersistedTransactionState(
        long stateRevision,
        Map<ResourceLocation, Long> balances,
        Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership,
        Map<ReceiptKey, GrantReceipt> receipts,
        Map<IdempotencyKey, TransactionResult> idempotencyResults,
        List<AuditRecord> auditRecords
) {
    public PersistedTransactionState {
        if (stateRevision < 0) {
            throw new IllegalArgumentException("Persisted state revision must not be negative");
        }
        balances = immutableBalances(balances);
        ownership = immutableOwnership(ownership);
        receipts = immutableSorted(receipts);
        idempotencyResults = immutableSorted(idempotencyResults);
        auditRecords = List.copyOf(Objects.requireNonNull(auditRecords, "auditRecords"));
    }

    public static PersistedTransactionState empty() {
        return new PersistedTransactionState(0, Map.of(), Map.of(), Map.of(), Map.of(), List.of());
    }

    public boolean isEmpty() {
        return stateRevision == 0 && balances.isEmpty() && ownership.isEmpty()
                && receipts.isEmpty() && idempotencyResults.isEmpty() && auditRecords.isEmpty();
    }

    private static Map<ResourceLocation, Long> immutableBalances(Map<ResourceLocation, Long> source) {
        Objects.requireNonNull(source, "balances");
        var sorted = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        source.forEach((key, value) -> sorted.put(
                Objects.requireNonNull(key, "balance id"),
                Objects.requireNonNull(value, "balance value")
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> immutableOwnership(
            Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> source
    ) {
        Objects.requireNonNull(source, "ownership");
        var sorted = new TreeMap<EntitlementKey, Map<GrantSourceId, EntitlementContribution>>();
        source.forEach((key, owners) -> {
            var ownerCopy = new TreeMap<GrantSourceId, EntitlementContribution>();
            Objects.requireNonNull(owners, "entitlement owners").forEach((owner, contribution) -> ownerCopy.put(
                    Objects.requireNonNull(owner, "grant source"),
                    Objects.requireNonNull(contribution, "entitlement contribution")
            ));
            if (ownerCopy.isEmpty()) {
                throw new IllegalArgumentException("Persisted entitlement owner map must not be empty: " + key);
            }
            sorted.put(Objects.requireNonNull(key, "entitlement key"), Collections.unmodifiableMap(ownerCopy));
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static <K extends Comparable<? super K>, V> Map<K, V> immutableSorted(Map<K, V> source) {
        Objects.requireNonNull(source, "persisted map");
        var sorted = new TreeMap<K, V>();
        source.forEach((key, value) -> sorted.put(
                Objects.requireNonNull(key, "persisted key"),
                Objects.requireNonNull(value, "persisted value")
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
