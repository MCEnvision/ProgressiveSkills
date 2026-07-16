package com.envisione.progressiveskills.common.transaction;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Typed persistent owner and nested grant identity. */
public record GrantSourceId(
        ResourceLocation ownerKind,
        ResourceLocation ownerId,
        ResourceLocation grantId
) implements Comparable<GrantSourceId> {
    public GrantSourceId {
        ownerKind = StableId.requireValid(ownerKind);
        ownerId = StableId.requireValid(ownerId);
        grantId = StableId.requireValid(grantId);
    }

    @Override
    public int compareTo(GrantSourceId other) {
        Objects.requireNonNull(other, "other");
        int kind = ownerKind.compareNamespaced(other.ownerKind);
        if (kind != 0) {
            return kind;
        }
        int owner = ownerId.compareNamespaced(other.ownerId);
        return owner != 0 ? owner : grantId.compareNamespaced(other.grantId);
    }

    @Override
    public String toString() {
        return ownerKind + "[" + ownerId + "]/" + grantId;
    }
}
