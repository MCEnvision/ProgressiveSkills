package com.envisione.progressiveskills.common.social;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public final class SharedAwardAllocator {
    private SharedAwardAllocator() {
    }

    public static Map<UUID, Long> allocate(long total, Map<UUID, Long> weights) {
        if (total < 0 || weights.isEmpty() || weights.size() > 128) {
            throw new IllegalArgumentException("Shared award total or participants are invalid");
        }
        var sorted = new TreeMap<UUID, Long>();
        weights.forEach((player, weight) -> {
            Objects.requireNonNull(player, "player");
            if (weight == null || weight < 0) {
                throw new IllegalArgumentException("Shared award weight is invalid");
            }
            sorted.put(player, weight);
        });
        long weightTotal = sorted.values().stream().mapToLong(Long::longValue).sum();
        if (weightTotal == 0) {
            sorted.replaceAll((player, weight) -> 1L);
            weightTotal = sorted.size();
        }
        var result = new LinkedHashMap<UUID, Long>();
        long assigned = 0;
        for (Map.Entry<UUID, Long> entry : sorted.entrySet()) {
            long share = BigInteger.valueOf(total).multiply(BigInteger.valueOf(entry.getValue()))
                    .divide(BigInteger.valueOf(weightTotal)).longValueExact();
            result.put(entry.getKey(), share);
            assigned = Math.addExact(assigned, share);
        }
        long remainder = total - assigned;
        var iterator = result.entrySet().iterator();
        while (remainder > 0) {
            if (!iterator.hasNext()) {
                iterator = result.entrySet().iterator();
            }
            Map.Entry<UUID, Long> entry = iterator.next();
            entry.setValue(Math.addExact(entry.getValue(), 1L));
            remainder--;
        }
        return Collections.unmodifiableMap(result);
    }
}
