package com.envisione.progressiveskills.common.transaction;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PurchaseInstanceId(
        ResourceLocation ownerKind,
        ResourceLocation ownerId,
        ResourceLocation purchaseId,
        int rank
) implements Comparable<PurchaseInstanceId> {
    public static final int MAX_RANK = 1_000_000;

    public PurchaseInstanceId {
        ownerKind = StableId.requireValid(ownerKind);
        ownerId = StableId.requireValid(ownerId);
        purchaseId = StableId.requireValid(purchaseId);
        if (rank < 1 || rank > MAX_RANK) {
            throw new IllegalArgumentException("Purchase rank must be between 1 and " + MAX_RANK);
        }
    }

    @Override
    public int compareTo(PurchaseInstanceId other) {
        Objects.requireNonNull(other, "other");
        int kind = ownerKind.compareNamespaced(other.ownerKind);
        if (kind != 0) {
            return kind;
        }
        int owner = ownerId.compareNamespaced(other.ownerId);
        if (owner != 0) {
            return owner;
        }
        int purchase = purchaseId.compareNamespaced(other.purchaseId);
        return purchase != 0 ? purchase : Integer.compare(rank, other.rank);
    }
}
