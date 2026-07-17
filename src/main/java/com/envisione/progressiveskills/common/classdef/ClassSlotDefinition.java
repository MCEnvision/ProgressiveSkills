package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ClassSlotDefinition(
        ResourceLocation id,
        DefinitionPresentation presentation,
        int capacity,
        ClassSwapPolicy swapPolicy
) implements Comparable<ClassSlotDefinition> {
    public static final int MAX_CAPACITY = 64;

    public ClassSlotDefinition {
        id = StableId.requireValid(id);
        Objects.requireNonNull(presentation, "presentation");
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("Class slot capacity must be within 1 and " + MAX_CAPACITY);
        }
        Objects.requireNonNull(swapPolicy, "swapPolicy");
    }

    @Override
    public int compareTo(ClassSlotDefinition other) {
        return id.compareNamespaced(other.id);
    }
}
