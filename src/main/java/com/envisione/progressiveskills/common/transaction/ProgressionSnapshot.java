package com.envisione.progressiveskills.common.transaction;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, side-neutral view of one in-memory progression authority. */
public record ProgressionSnapshot(
        long stateRevision,
        Map<ResourceLocation, Long> balances,
        Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership,
        Map<PurchaseInstanceId, PaidCostRecord> paidCosts,
        Map<EntitlementKey, Long> projectedValues,
        int receiptCount,
        int idempotencyCount,
        int auditCount
) {
    public ProgressionSnapshot {
        if (stateRevision < 0 || receiptCount < 0 || idempotencyCount < 0 || auditCount < 0) {
            throw new IllegalArgumentException("Snapshot counts and revision must not be negative");
        }
        balances = immutableBalances(balances);
        ownership = immutableOwnership(ownership);
        paidCosts = immutablePaidCosts(paidCosts);
        projectedValues = immutableProjected(projectedValues);
    }

    public static ProgressionSnapshot empty() {
        return new ProgressionSnapshot(0, Map.of(), Map.of(), Map.of(), Map.of(), 0, 0, 0);
    }

    public ProgressionSnapshot(
            long stateRevision,
            Map<ResourceLocation, Long> balances,
            Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership,
            Map<EntitlementKey, Long> projectedValues,
            int receiptCount,
            int idempotencyCount,
            int auditCount
    ) {
        this(stateRevision, balances, ownership, Map.of(), projectedValues,
                receiptCount, idempotencyCount, auditCount);
    }

    private static Map<ResourceLocation, Long> immutableBalances(Map<ResourceLocation, Long> source) {
        Objects.requireNonNull(source, "balances");
        var sorted = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        sorted.putAll(source);
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> immutableOwnership(
            Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> source
    ) {
        Objects.requireNonNull(source, "ownership");
        var sorted = new TreeMap<EntitlementKey, Map<GrantSourceId, EntitlementContribution>>();
        source.forEach((key, owners) -> {
            var sortedOwners = new TreeMap<GrantSourceId, EntitlementContribution>();
            sortedOwners.putAll(owners);
            sorted.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(sortedOwners)));
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<EntitlementKey, Long> immutableProjected(Map<EntitlementKey, Long> source) {
        Objects.requireNonNull(source, "projectedValues");
        var sorted = new TreeMap<EntitlementKey, Long>();
        sorted.putAll(source);
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<PurchaseInstanceId, PaidCostRecord> immutablePaidCosts(
            Map<PurchaseInstanceId, PaidCostRecord> source
    ) {
        Objects.requireNonNull(source, "paidCosts");
        if (source.size() > PersistedTransactionState.MAX_PAID_COST_RECORDS) {
            throw new IllegalArgumentException("Paid cost record count exceeds capacity");
        }
        var sorted = new TreeMap<PurchaseInstanceId, PaidCostRecord>();
        source.forEach((key, value) -> {
            Objects.requireNonNull(key, "paid cost key");
            Objects.requireNonNull(value, "paid cost record");
            if (!key.equals(value.instanceId())) {
                throw new IllegalArgumentException("Paid cost key does not match its record");
            }
            sorted.put(key, value);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
