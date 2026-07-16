package com.envisione.progressiveskills.common.transaction;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

/** Checked long balance delta with explicit post-mutation bounds. */
public record BalanceMutation(ResourceLocation balanceId, long delta, long minimum, long maximum) {
    public BalanceMutation {
        balanceId = StableId.requireValid(balanceId);
        if (minimum > maximum) {
            throw new IllegalArgumentException("Balance minimum exceeds maximum");
        }
        if (delta == 0) {
            throw new IllegalArgumentException("Balance mutation delta must not be zero");
        }
    }
}
