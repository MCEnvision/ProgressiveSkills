package com.envisione.progressiveskills.common.network;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisibleStateSyncTest {
    @Test
    void fullAndDeltaCodecsRoundTripWithStrictContinuityAndDigest() {
        VisiblePlayerState before = NetworkFixtures.state(2, 1, Map.of("example:points", 2L));
        VisiblePlayerState after = NetworkFixtures.state(3, 2,
                Map.of("example:points", 4L, "example:mastery", 1L));

        assertEquals(before, VisibleStateCodec.decode(VisibleStateCodec.encode(before)));
        StateDelta delta = StateDelta.between(before, after, VisibleStateCodec.digest(after));
        StateDelta decoded = VisibleStateCodec.decodeDelta(VisibleStateCodec.encodeDelta(delta));
        assertEquals(after, before.apply(decoded));

        VisiblePlayerState gap = NetworkFixtures.state(1, 1, Map.of());
        assertThrows(IllegalArgumentException.class, () -> gap.apply(decoded));
        byte[] trailing = java.util.Arrays.copyOf(
                VisibleStateCodec.encodeDelta(delta), VisibleStateCodec.encodeDelta(delta).length + 1);
        assertThrows(IllegalArgumentException.class, () -> VisibleStateCodec.decodeDelta(trailing));
    }

    @Test
    void nodeRanksRoundTripAndDeltaInStableOrder() {
        VisiblePlayerState before = NetworkFixtures.state(
                4, 3, Map.of(), Map.of(NetworkFixtures.NODE_ROOT, 1));
        VisiblePlayerState after = NetworkFixtures.state(
                5, 4, Map.of(), Map.of(NetworkFixtures.NODE_BRANCH, 1));

        assertEquals(before, VisibleStateCodec.decode(VisibleStateCodec.encode(before)));
        StateDelta delta = StateDelta.between(before, after, VisibleStateCodec.digest(after));
        assertEquals(Map.of(NetworkFixtures.NODE_BRANCH, 1), delta.changedNodeRanks());
        assertEquals(java.util.Set.of(NetworkFixtures.NODE_ROOT), delta.removedNodeRanks());
        assertEquals(after, before.apply(VisibleStateCodec.decodeDelta(VisibleStateCodec.encodeDelta(delta))));
    }

    @Test
    void selectedClassesRoundTripAndDeltaWithoutOwnershipInternals() {
        ResourceLocation background = ResourceLocation.parse("example:background");
        ResourceLocation mage = ResourceLocation.parse("example:mage");
        ResourceLocation warrior = ResourceLocation.parse("example:warrior");
        var before = new VisiblePlayerState(
                NetworkFixtures.PLAYER, 7, 5,
                new com.envisione.progressiveskills.common.transaction.DefinitionRevision(
                        1, NetworkFixtures.SEMANTIC),
                1, BoundedNetworkCodec.digest(DefinitionProjectionCodec.encode(NetworkFixtures.definitions())),
                Map.of(), Map.of(), Map.of(),
                Map.of(mage, new VisiblePlayerState.ClassSelection(
                        background, 0, VisiblePlayerState.Activity.ACTIVE)),
                0, 0, false
        );
        var after = new VisiblePlayerState(
                NetworkFixtures.PLAYER, 8, 6, before.definitionRevision(),
                before.presentationRevision(), before.presentationDigest(),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        mage, new VisiblePlayerState.ClassSelection(
                                background, 0, VisiblePlayerState.Activity.SUSPENDED),
                        warrior, new VisiblePlayerState.ClassSelection(
                                ResourceLocation.parse("example:combat"), 2,
                                VisiblePlayerState.Activity.ACTIVE)
                ),
                0, 0, false
        );

        assertEquals(before, VisibleStateCodec.decode(VisibleStateCodec.encode(before)));
        StateDelta delta = StateDelta.between(before, after, VisibleStateCodec.digest(after));
        assertEquals(Set.of(), delta.removedSelectedClasses());
        assertEquals(after.selectedClasses(), delta.changedSelectedClasses());
        assertEquals(after, before.apply(VisibleStateCodec.decodeDelta(
                VisibleStateCodec.encodeDelta(delta))));
        assertThrows(IllegalArgumentException.class, () -> new VisiblePlayerState.ClassSelection(
                background, -1, VisiblePlayerState.Activity.ACTIVE));
    }

    @Test
    void missingClassDefinitionRemainsVisibleAsSuspended() {
        ResourceLocation retired = ResourceLocation.parse("example:retired_class");
        var state = new VisiblePlayerState(
                NetworkFixtures.PLAYER, 9, 7,
                new com.envisione.progressiveskills.common.transaction.DefinitionRevision(
                        1, NetworkFixtures.SEMANTIC),
                1, BoundedNetworkCodec.digest(DefinitionProjectionCodec.encode(NetworkFixtures.definitions())),
                Map.of(), Map.of(), Map.of(),
                Map.of(retired, new VisiblePlayerState.ClassSelection(
                        Optional.empty(), 0, VisiblePlayerState.Activity.SUSPENDED)),
                1, 0, false
        );

        VisiblePlayerState decoded = VisibleStateCodec.decode(VisibleStateCodec.encode(state));
        assertEquals(state, decoded);
        assertEquals(Optional.empty(), decoded.selectedClasses().get(retired).slotId());
        assertEquals(VisiblePlayerState.Activity.SUSPENDED,
                decoded.selectedClasses().get(retired).activity());
        assertThrows(IllegalArgumentException.class, () -> new VisiblePlayerState.ClassSelection(
                Optional.empty(), 0, VisiblePlayerState.Activity.ACTIVE));
    }
}
