package com.envisione.progressiveskills.server.carrier;

import com.envisione.progressiveskills.common.carrier.CarrierBehaviorSnapshot;
import com.envisione.progressiveskills.common.carrier.CarrierBindPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierDeliveryPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierIdentity;
import com.envisione.progressiveskills.common.carrier.CarrierKind;
import com.envisione.progressiveskills.common.carrier.CarrierMigrationPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierSkillXpAction;
import com.envisione.progressiveskills.common.carrier.CarrierStackState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrierBehaviorResolverTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001301");
    private static final String PACK_DIGEST = "a".repeat(64);

    @Test
    void onUseBindingAndChargesAdvanceOnlyInTheReturnedState() {
        CarrierBehaviorSnapshot behavior = behavior(
                1, CarrierMigrationPolicy.KEEP_PINNED, CarrierBindPolicy.ON_USE, 2
        );
        var archive = archive(behavior);
        CarrierStackState state = CarrierStackState.fresh(
                behavior.behaviorVersion(), behavior.charges(), PACK_DIGEST,
                UUID.fromString("00000000-0000-0000-0000-000000001302")
        );

        CarrierBehaviorResolver.Resolution result = CarrierBehaviorResolver.resolve(
                new CarrierIdentity(behavior.definitionId(), behavior.digest()),
                state,
                CarrierKind.TOME,
                PLAYER,
                archive,
                Optional.of(behavior)
        );

        assertTrue(result.allowed());
        assertEquals(2, result.requiredCharges());
        assertEquals(3, state.charges());
        assertTrue(state.boundOwner().isEmpty());
        CarrierStackState after = result.afterState().orElseThrow();
        assertEquals(1, after.charges());
        assertEquals(1, after.useCounter());
        assertEquals(PLAYER, after.boundOwner().orElseThrow());
        assertNotEquals(
                CarrierActionPlanCompiler.key(behavior, state),
                CarrierActionPlanCompiler.key(behavior, after)
        );
    }

    @Test
    void pinnedBehaviorRemainsUsableAfterLiveBehaviorChanges() {
        CarrierBehaviorSnapshot pinned = behavior(
                1, CarrierMigrationPolicy.KEEP_PINNED, CarrierBindPolicy.NONE, 1
        );
        CarrierBehaviorSnapshot current = behavior(
                2, CarrierMigrationPolicy.KEEP_PINNED, CarrierBindPolicy.NONE, 1
        );
        CarrierStackState state = CarrierStackState.fresh(
                1, 3, PACK_DIGEST, UUID.fromString("00000000-0000-0000-0000-000000001303")
        );

        CarrierBehaviorResolver.Resolution result = CarrierBehaviorResolver.resolve(
                new CarrierIdentity(pinned.definitionId(), pinned.digest()),
                state,
                CarrierKind.TOME,
                PLAYER,
                archive(pinned),
                Optional.of(current)
        );

        assertTrue(result.allowed());
        assertEquals(pinned, result.behavior().orElseThrow());
        assertTrue(result.warning().isEmpty());
    }

    @Test
    void changedBehaviorHonorsWarnInvalidateAndExplicitMigratePolicies() {
        CarrierBehaviorResolver.Resolution warned = resolveChanged(
                behavior(1, CarrierMigrationPolicy.KEEP_PINNED, CarrierBindPolicy.NONE, 1),
                behavior(9, CarrierMigrationPolicy.WARN, CarrierBindPolicy.NONE, 1)
        );
        CarrierBehaviorResolver.Resolution invalidated = resolveChanged(
                behavior(1, CarrierMigrationPolicy.KEEP_PINNED, CarrierBindPolicy.NONE, 1),
                behavior(9, CarrierMigrationPolicy.INVALIDATE, CarrierBindPolicy.NONE, 1)
        );
        CarrierBehaviorResolver.Resolution migrate = resolveChanged(
                behavior(1, CarrierMigrationPolicy.KEEP_PINNED, CarrierBindPolicy.NONE, 1),
                behavior(9, CarrierMigrationPolicy.MIGRATE, CarrierBindPolicy.NONE, 1)
        );

        assertTrue(warned.allowed());
        assertTrue(warned.warning().isPresent());
        assertEquals(CarrierBehaviorResolver.Code.INVALIDATED, invalidated.code());
        assertEquals(CarrierBehaviorResolver.Code.MIGRATION_REQUIRED, migrate.code());
    }

    @Test
    void digestKindVersionChargeAndOwnerTamperingFailClosed() {
        CarrierBehaviorSnapshot behavior = behavior(
                1, CarrierMigrationPolicy.KEEP_PINNED, CarrierBindPolicy.ON_USE, 1
        );
        var archive = archive(behavior);
        CarrierIdentity identity = new CarrierIdentity(behavior.definitionId(), behavior.digest());
        CarrierStackState fresh = CarrierStackState.fresh(
                1, 3, PACK_DIGEST, UUID.fromString("00000000-0000-0000-0000-000000001304")
        );

        assertEquals(CarrierBehaviorResolver.Code.ARCHIVED_BEHAVIOR_MISSING,
                CarrierBehaviorResolver.resolve(
                        new CarrierIdentity(behavior.definitionId(), "b".repeat(64)), fresh,
                        CarrierKind.TOME, PLAYER, archive, Optional.of(behavior)
                ).code());
        assertEquals(CarrierBehaviorResolver.Code.KIND_MISMATCH,
                CarrierBehaviorResolver.resolve(
                        identity, fresh, CarrierKind.TOKEN, PLAYER, archive, Optional.of(behavior)
                ).code());
        assertEquals(CarrierBehaviorResolver.Code.STATE_VERSION_MISMATCH,
                CarrierBehaviorResolver.resolve(
                        identity, CarrierStackState.fresh(2, 3, PACK_DIGEST, UUID.randomUUID()),
                        CarrierKind.TOME, PLAYER, archive, Optional.of(behavior)
                ).code());
        assertEquals(CarrierBehaviorResolver.Code.CHARGE_TAMPER,
                CarrierBehaviorResolver.resolve(
                        identity, CarrierStackState.fresh(1, 4, PACK_DIGEST, UUID.randomUUID()),
                        CarrierKind.TOME, PLAYER, archive, Optional.of(behavior)
                ).code());
        CarrierBehaviorSnapshot expensive = behavior(
                1, CarrierMigrationPolicy.KEEP_PINNED, CarrierBindPolicy.ON_USE, 2
        );
        assertEquals(CarrierBehaviorResolver.Code.INSUFFICIENT_CHARGES,
                CarrierBehaviorResolver.resolve(
                        new CarrierIdentity(expensive.definitionId(), expensive.digest()),
                        CarrierStackState.fresh(1, 1, PACK_DIGEST, UUID.randomUUID()),
                        CarrierKind.TOME, PLAYER, archive(expensive), Optional.of(expensive)
                ).code());
        assertEquals(CarrierBehaviorResolver.Code.WRONG_OWNER,
                CarrierBehaviorResolver.resolve(
                        identity, fresh.bind(UUID.randomUUID()),
                        CarrierKind.TOME, PLAYER, archive, Optional.of(behavior)
                ).code());
    }

    @Test
    void unsafeLiveAcceptanceIsAlwaysHeld() {
        CarrierBehaviorSnapshot behavior = behavior(
                1, CarrierMigrationPolicy.ACCEPT_LIVE, CarrierBindPolicy.NONE, 1
        );
        CarrierStackState state = CarrierStackState.fresh(1, 3, PACK_DIGEST, UUID.randomUUID());

        CarrierBehaviorResolver.Resolution result = CarrierBehaviorResolver.resolve(
                new CarrierIdentity(behavior.definitionId(), behavior.digest()),
                state,
                CarrierKind.TOME,
                PLAYER,
                archive(behavior),
                Optional.of(behavior)
        );

        assertFalse(result.allowed());
        assertEquals(CarrierBehaviorResolver.Code.UNSAFE_LIVE_POLICY, result.code());
    }

    private static CarrierBehaviorResolver.Resolution resolveChanged(
            CarrierBehaviorSnapshot pinned,
            CarrierBehaviorSnapshot current
    ) {
        CarrierStackState state = CarrierStackState.fresh(
                pinned.behaviorVersion(), pinned.charges(), PACK_DIGEST, UUID.randomUUID()
        );
        return CarrierBehaviorResolver.resolve(
                new CarrierIdentity(pinned.definitionId(), pinned.digest()),
                state,
                CarrierKind.TOME,
                PLAYER,
                archive(pinned),
                Optional.of(current)
        );
    }

    private static BehaviorArchiveSavedData archive(CarrierBehaviorSnapshot behavior) {
        var archive = new BehaviorArchiveSavedData();
        archive.reserve(List.of(behavior));
        return archive;
    }

    private static CarrierBehaviorSnapshot behavior(
            int version,
            CarrierMigrationPolicy migration,
            CarrierBindPolicy binding,
            int consume
    ) {
        return new CarrierBehaviorSnapshot(
                id("physique_tome"),
                CarrierKind.TOME,
                version,
                migration,
                binding,
                CarrierDeliveryPolicy.PENDING_CLAIM,
                1,
                3,
                20,
                List.of(new CarrierSkillXpAction(id("award"), id("physique"), 250, consume))
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
