package com.envisione.progressiveskills.common.carrier;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CarrierBehaviorCodecTest {
    @Test
    void deterministicBytesDigestAndRestartDecodePreserveTheBehaviorSnapshot() {
        CarrierBehaviorSnapshot snapshot = CarrierCanonicalCodecTest.definition().behaviorSnapshot();

        byte[] first = CarrierBehaviorCodec.encodeSnapshot(snapshot);
        byte[] second = CarrierBehaviorCodec.encodeSnapshot(snapshot);

        assertArrayEquals(first, second);
        assertEquals(snapshot, CarrierBehaviorCodec.decodeSnapshot(first));
        assertEquals(64, CarrierBehaviorCodec.digest(snapshot).length());
        assertEquals(CarrierBehaviorCodec.digest(first), snapshot.digest());
    }

    @Test
    void actionOrderIsPartOfTheDigestAndMalformedBytesFailClosed() {
        CarrierBehaviorSnapshot original = CarrierCanonicalCodecTest.definition().behaviorSnapshot();
        var reversedActions = new ArrayList<>(original.useActions());
        java.util.Collections.reverse(reversedActions);
        CarrierBehaviorSnapshot reversed = new CarrierBehaviorSnapshot(
                original.definitionId(), original.carrier(), original.behaviorVersion(),
                original.migrationPolicy(), original.bindPolicy(), original.deliveryPolicy(),
                original.stackSize(), original.charges(), original.cooldownTicks(), reversedActions
        );

        assertNotEquals(original.digest(), reversed.digest());

        byte[] trailing = java.util.Arrays.copyOf(
                CarrierBehaviorCodec.encode(original),
                CarrierBehaviorCodec.encode(original).length + 1
        );
        assertThrows(IllegalArgumentException.class, () -> CarrierBehaviorCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class, () -> CarrierBehaviorCodec.decode(new byte[] {1, 2, 3}));
    }

    @Test
    void presentationOnlyChangesDoNotRewritePinnedEconomicBehavior() {
        CarrierDefinition original = CarrierCanonicalCodecTest.definition();
        CarrierDefinition restyled = new CarrierDefinition(
                original.id(),
                new com.envisione.progressiveskills.common.ir.DefinitionPresentation(
                        com.envisione.progressiveskills.common.presentation.ComponentSpec.literal("Restyled"),
                        Optional.empty(),
                        com.envisione.progressiveskills.common.presentation.IconSpec.single(
                                com.envisione.progressiveskills.common.presentation.IconKind.ITEM,
                                CarrierCanonicalCodecTest.id("minecraft:diamond"),
                                CarrierCanonicalCodecTest.id("minecraft:barrier"),
                                com.envisione.progressiveskills.common.presentation.ComponentSpec.literal("Diamond")
                        ),
                        Set.of("restyled")
                ),
                false,
                original.carrier(),
                original.behaviorVersion(),
                original.migrationPolicy(),
                original.bindPolicy(),
                original.deliveryPolicy(),
                CarrierRarity.EPIC,
                false,
                original.stackSize(),
                original.charges(),
                original.cooldownTicks(),
                original.useActions()
        );

        assertEquals(original.behaviorDigest(), restyled.behaviorDigest());
    }
}
