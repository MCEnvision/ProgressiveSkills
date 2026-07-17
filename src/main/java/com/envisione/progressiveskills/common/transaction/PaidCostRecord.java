package com.envisione.progressiveskills.common.transaction;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public record PaidCostRecord(
        PurchaseInstanceId instanceId,
        TransactionId purchaseTransactionId,
        DefinitionRevision definitionRevision,
        String ownerLineage,
        Map<ResourceLocation, Long> paidBalances,
        Set<GrantSourceId> persistentSources
) {
    public static final int MAX_PAID_BALANCES = 8;
    public static final int MAX_PERSISTENT_SOURCES = 32;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public PaidCostRecord {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(purchaseTransactionId, "purchaseTransactionId");
        Objects.requireNonNull(definitionRevision, "definitionRevision");
        ownerLineage = Objects.requireNonNull(ownerLineage, "ownerLineage");
        if (!SHA_256.matcher(ownerLineage).matches()) {
            throw new IllegalArgumentException("Owner lineage must be lowercase SHA-256");
        }
        paidBalances = immutableBalances(paidBalances);
        persistentSources = immutableSources(persistentSources);
    }

    private static Map<ResourceLocation, Long> immutableBalances(Map<ResourceLocation, Long> source) {
        Objects.requireNonNull(source, "paidBalances");
        if (source.isEmpty() || source.size() > MAX_PAID_BALANCES) {
            throw new IllegalArgumentException(
                    "Paid balance count must be within 1 and " + MAX_PAID_BALANCES
            );
        }
        var sorted = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        long total = 0;
        for (var entry : source.entrySet()) {
            ResourceLocation id = StableId.requireValid(entry.getKey());
            long amount = Objects.requireNonNull(entry.getValue(), "paid balance amount");
            if (amount <= 0) {
                throw new IllegalArgumentException("Paid balance amounts must be positive");
            }
            total = Math.addExact(total, amount);
            sorted.put(id, amount);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Set<GrantSourceId> immutableSources(Set<GrantSourceId> source) {
        Objects.requireNonNull(source, "persistentSources");
        if (source.size() > MAX_PERSISTENT_SOURCES) {
            throw new IllegalArgumentException("Persistent source count exceeds " + MAX_PERSISTENT_SOURCES);
        }
        var sorted = new TreeSet<GrantSourceId>();
        source.forEach(value -> sorted.add(Objects.requireNonNull(value, "persistent source")));
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }
}
