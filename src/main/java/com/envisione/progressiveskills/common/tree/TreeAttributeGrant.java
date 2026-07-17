package com.envisione.progressiveskills.common.tree;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record TreeAttributeGrant(
        ResourceLocation id,
        ResourceLocation attribute,
        AttributeOperation operation,
        long valueUnits
) implements Comparable<TreeAttributeGrant> {
    public TreeAttributeGrant {
        id = StableId.requireValid(id);
        attribute = StableId.requireValid(attribute);
        Objects.requireNonNull(operation, "operation");
        if (valueUnits == 0) {
            throw new IllegalArgumentException("Tree attribute grant value must not be zero");
        }
    }

    @Override
    public int compareTo(TreeAttributeGrant other) {
        return id.compareNamespaced(other.id);
    }
}
