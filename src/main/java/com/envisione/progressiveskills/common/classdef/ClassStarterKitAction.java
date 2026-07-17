package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class ClassStarterKitAction {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            "progressiveskills", "starter_kit"
    );

    private ClassStarterKitAction() {
    }

    public static String encode(ClassStarterKit kit) {
        Objects.requireNonNull(kit, "kit");
        return encode(kit.items());
    }

    static String encode(List<ResourceLocation> items) {
        Objects.requireNonNull(items, "items");
        var counts = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        items.forEach(item -> counts.merge(StableId.requireValid(item), 1, Math::addExact));
        String payload = encodeCounts(counts);
        if (payload.isEmpty() || payload.length() > TransitionAction.MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("Starter kit action payload exceeds its bound");
        }
        return payload;
    }

    public static Map<ResourceLocation, Integer> decode(String payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.isBlank() || payload.length() > TransitionAction.MAX_PAYLOAD_LENGTH
                || payload.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Starter kit action payload is invalid");
        }
        var result = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        for (String entry : payload.split(",", -1)) {
            int separator = entry.lastIndexOf('=');
            if (separator < 1 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("Starter kit action entry is invalid");
            }
            ResourceLocation item = StableId.parse(entry.substring(0, separator));
            int count;
            try {
                count = Integer.parseInt(entry.substring(separator + 1));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Starter kit action count is invalid", exception);
            }
            if (count < 1 || count > ClassStarterKit.MAX_ITEMS) {
                throw new IllegalArgumentException("Starter kit action count exceeds its bound");
            }
            if (result.putIfAbsent(item, count) != null) {
                throw new IllegalArgumentException("Starter kit action contains a duplicate item");
            }
        }
        int total = result.values().stream().reduce(0, Math::addExact);
        if (total < 1 || total > ClassStarterKit.MAX_ITEMS) {
            throw new IllegalArgumentException("Starter kit action total exceeds its bound");
        }
        if (!encodeCounts(result).equals(payload)) {
            throw new IllegalArgumentException("Starter kit action payload is not canonical");
        }
        var canonical = new LinkedHashMap<ResourceLocation, Integer>();
        result.forEach(canonical::put);
        return Collections.unmodifiableMap(canonical);
    }

    public static int total(Map<ResourceLocation, Integer> counts) {
        Objects.requireNonNull(counts, "counts");
        return counts.values().stream().reduce(0, Math::addExact);
    }

    private static String encodeCounts(Map<ResourceLocation, Integer> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }
}
