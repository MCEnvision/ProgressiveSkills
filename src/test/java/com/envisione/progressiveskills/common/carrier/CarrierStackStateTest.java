package com.envisione.progressiveskills.common.carrier;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CarrierStackStateTest {
    private static final String DIGEST = "a".repeat(64);

    @Test
    void identityAndStateRoundTripThroughBoundedCodecs() {
        CarrierIdentity identity = new CarrierIdentity(
                ResourceLocation.fromNamespaceAndPath("progressiveskills", "physique_level_token"),
                DIGEST
        );
        UUID instanceId = UUID.fromString("00000000-0000-0000-0000-000000000013");
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000014");
        CarrierStackState state = CarrierStackState.fresh(3, 5, DIGEST, instanceId).bind(owner);

        JsonElement encodedIdentity = CarrierIdentity.CODEC.encodeStart(JsonOps.INSTANCE, identity).getOrThrow();
        JsonElement encodedState = CarrierStackState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();

        assertEquals(identity, CarrierIdentity.CODEC.parse(JsonOps.INSTANCE, encodedIdentity).getOrThrow());
        assertEquals(state, CarrierStackState.CODEC.parse(JsonOps.INSTANCE, encodedState).getOrThrow());
    }

    @Test
    void successfulUseAdvancesOneReplayIdentityAndPreservesIssuance() {
        UUID instanceId = UUID.fromString("00000000-0000-0000-0000-000000000015");
        CarrierStackState initial = CarrierStackState.fresh(2, 3, DIGEST, instanceId);

        CarrierStackState used = initial.afterUse(2);

        assertEquals(instanceId, used.instanceId());
        assertEquals(1, used.useCounter());
        assertEquals(2, used.charges());
        assertEquals(initial.creationPackDigest(), used.creationPackDigest());
    }

    @Test
    void bindingAndBoundsFailClosed() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000016");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000017");
        CarrierStackState state = CarrierStackState.fresh(1, 1, DIGEST, first).bind(first);

        assertThrows(IllegalStateException.class, () -> state.bind(second));
        assertThrows(IllegalArgumentException.class, () -> new CarrierIdentity(
                ResourceLocation.fromNamespaceAndPath("progressiveskills", "invalid"),
                "unsafe"
        ));
        assertThrows(IllegalArgumentException.class, () -> new CarrierStackState(
                CarrierStackState.CURRENT_DATA_VERSION,
                1,
                -1,
                DIGEST,
                first,
                0,
                Optional.empty(),
                Optional.empty()
        ));
    }
}
