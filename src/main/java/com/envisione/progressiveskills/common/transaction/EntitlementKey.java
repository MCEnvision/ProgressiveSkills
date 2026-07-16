package com.envisione.progressiveskills.common.transaction;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Typed target whose effective persistent value is resolved from all owning sources. */
public record EntitlementKey(ResourceLocation targetType, ResourceLocation targetId)
        implements Comparable<EntitlementKey> {
    public EntitlementKey {
        targetType = StableId.requireValid(targetType);
        targetId = StableId.requireValid(targetId);
    }

    @Override
    public int compareTo(EntitlementKey other) {
        Objects.requireNonNull(other, "other");
        int type = targetType.compareNamespaced(other.targetType);
        return type != 0 ? type : targetId.compareNamespaced(other.targetId);
    }

    @Override
    public String toString() {
        return targetType + "[" + targetId + "]";
    }
}
