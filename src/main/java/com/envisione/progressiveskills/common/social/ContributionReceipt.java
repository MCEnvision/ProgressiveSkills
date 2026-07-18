package com.envisione.progressiveskills.common.social;

import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public record ContributionReceipt(
        UUID receiptId,
        UUID groupId,
        ResourceLocation source,
        long total,
        Map<UUID, Long> shares,
        String explanation,
        Instant createdAt
) {
    public ContributionReceipt {
        Objects.requireNonNull(receiptId, "receiptId");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(source, "source");
        if (total < 0 || shares.size() > 128) {
            throw new IllegalArgumentException("Contribution receipt amount or share count is invalid");
        }
        var sorted = new TreeMap<UUID, Long>();
        shares.forEach((player, amount) -> {
            if (amount == null || amount < 0) {
                throw new IllegalArgumentException("Contribution share is invalid");
            }
            sorted.put(player, amount);
        });
        long sum = 0;
        for (long amount : sorted.values()) {
            sum = Math.addExact(sum, amount);
        }
        if (sum != total) {
            throw new IllegalArgumentException("Contribution shares do not conserve the total");
        }
        shares = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        explanation = Objects.requireNonNull(explanation, "explanation").strip();
        if (explanation.isEmpty() || explanation.length() > 512) {
            throw new IllegalArgumentException("Contribution explanation is invalid");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
