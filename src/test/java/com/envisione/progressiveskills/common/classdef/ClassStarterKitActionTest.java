package com.envisione.progressiveskills.common.classdef;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassStarterKitActionTest {
    @Test
    void canonicalPayloadAggregatesAndSortsOneReceiptDelivery() {
        var kit = new ClassStarterKit(
                id("test:warrior/starter_kit"),
                List.of(id("minecraft:stick"), id("minecraft:book"), id("minecraft:stick"))
        );

        String payload = ClassStarterKitAction.encode(kit);

        assertEquals("minecraft:book=1,minecraft:stick=2", payload);
        assertEquals(Map.of(id("minecraft:book"), 1, id("minecraft:stick"), 2),
                ClassStarterKitAction.decode(payload));
        assertEquals(3, ClassStarterKitAction.total(ClassStarterKitAction.decode(payload)));
    }

    @Test
    void decoderRejectsMalformedNoncanonicalAndOversizedPayloads() {
        assertThrows(IllegalArgumentException.class, () -> ClassStarterKitAction.decode(""));
        assertThrows(IllegalArgumentException.class, () -> ClassStarterKitAction.decode("minecraft:stick"));
        assertThrows(IllegalArgumentException.class, () -> ClassStarterKitAction.decode("minecraft:stick=0"));
        assertThrows(IllegalArgumentException.class, () ->
                ClassStarterKitAction.decode("minecraft:stick=1,minecraft:stick=1"));
        assertThrows(IllegalArgumentException.class, () ->
                ClassStarterKitAction.decode("minecraft:stick=1,minecraft:book=1"));
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
