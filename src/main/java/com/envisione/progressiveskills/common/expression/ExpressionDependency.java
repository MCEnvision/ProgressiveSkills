package com.envisione.progressiveskills.common.expression;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

public record ExpressionDependency(ResourceLocation id) implements Comparable<ExpressionDependency> {
    public ExpressionDependency {
        id = StableId.requireValid(id);
    }

    @Override
    public int compareTo(ExpressionDependency other) {
        return id.compareNamespaced(other.id);
    }
}
