package com.envisione.progressiveskills.common.network;

import org.junit.jupiter.api.Test;

import java.util.Map;

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
}
