package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record ClassStarterKit(ResourceLocation receiptId, List<ResourceLocation> items) {
    public static final int MAX_ITEMS = 32;

    public ClassStarterKit {
        receiptId = StableId.requireValid(receiptId);
        Objects.requireNonNull(items, "items");
        if (items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("Class starter kit item count must be within 1 and " + MAX_ITEMS);
        }
        items = items.stream().map(StableId::requireValid).toList();
        ClassStarterKitAction.encode(items);
    }
}
