package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.common.carrier.CarrierBehaviorSnapshot;
import com.envisione.progressiveskills.common.carrier.CarrierBindPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierDeliveryPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierIdentity;
import com.envisione.progressiveskills.common.carrier.CarrierKind;
import com.envisione.progressiveskills.common.carrier.CarrierMigrationPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierSkillXpAction;
import com.envisione.progressiveskills.common.carrier.CarrierStackState;
import com.envisione.progressiveskills.common.carrier.PendingCarrierClaim;
import com.envisione.progressiveskills.common.carrier.PendingClaimTakeStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingCarrierClaimPersistenceTest {
    private static final UUID PLAYER = uuid(1);
    private static final String PACK_DIGEST = "b".repeat(64);

    @Test
    void pinnedClaimRoundTripsAndDeliveryRemovesOnlyAfterSuccess() {
        ProgressiveSkillsData data = ProgressiveSkillsData.empty(PLAYER);
        PendingCarrierClaim claim = claim(10, 20, 1_000);

        assertTrue(data.canAcceptPendingClaim(claim));
        assertTrue(data.addPendingClaim(claim));
        assertFalse(data.addPendingClaim(claim));
        assertEquals(claim, data.pendingClaimByOrigin(claim.originDeliveryId()).orElseThrow());

        ProgressiveSkillsData restarted = ProgressiveSkillsDataSerializer.decode(
                PLAYER, ProgressiveSkillsDataSerializer.encode(data));

        assertEquals(claim, restarted.pendingClaim(claim.claimId()).orElseThrow());
        assertEquals(PendingClaimTakeStatus.DELIVERY_REJECTED,
                restarted.takePendingClaim(claim.claimId(), ignored -> false));
        assertTrue(restarted.pendingClaim(claim.claimId()).isPresent());
        assertEquals(PendingClaimTakeStatus.DELIVERED,
                restarted.takePendingClaim(claim.claimId(), ignored -> true));
        assertTrue(restarted.pendingClaims().isEmpty());
    }

    @Test
    void takeAllStopsAtTheFirstRejectedClaimAndRetainsTheDeterministicSuffix() {
        ProgressiveSkillsData data = ProgressiveSkillsData.empty(PLAYER);
        PendingCarrierClaim first = claim(30, 40, 1_000);
        PendingCarrierClaim second = claim(31, 41, 2_000);
        PendingCarrierClaim third = claim(32, 42, 3_000);
        data.addPendingClaim(third);
        data.addPendingClaim(first);
        data.addPendingClaim(second);
        var attempted = new ArrayList<UUID>();

        var result = data.takeAllPendingClaims(value -> {
            attempted.add(value.claimId());
            return !value.claimId().equals(second.claimId());
        });

        assertEquals(List.of(first.claimId(), second.claimId()), attempted);
        assertEquals(1, result.delivered());
        assertEquals(2, result.retained());
        assertEquals(List.of(second, third), data.pendingClaims());
    }

    @Test
    void mismatchedPinnedBehaviorAndDuplicateOriginsFailClosed() {
        PendingCarrierClaim valid = claim(50, 60, 1_000);
        assertThrows(IllegalArgumentException.class, () -> new PendingCarrierClaim(
                valid.claimId(), valid.originDeliveryId(), valid.identity(), CarrierKind.TOME,
                valid.state(), valid.behavior(), valid.createdAt(), valid.reason()));

        ProgressiveSkillsData data = ProgressiveSkillsData.empty(PLAYER);
        data.addPendingClaim(valid);
        PendingCarrierClaim duplicateOrigin = new PendingCarrierClaim(
                uuid(51), valid.originDeliveryId(), valid.identity(), valid.kind(),
                CarrierStackState.fresh(
                        valid.behavior().behaviorVersion(), valid.behavior().charges(),
                        PACK_DIGEST, uuid(71)),
                valid.behavior(), valid.createdAt().plusSeconds(1), valid.reason());
        assertFalse(data.canAcceptPendingClaim(duplicateOrigin));
        assertThrows(IllegalStateException.class, () -> data.addPendingClaim(duplicateOrigin));
    }

    @Test
    void claimCapacityIsCheckedBeforeAcquisition() {
        ProgressiveSkillsData data = ProgressiveSkillsData.empty(PLAYER);
        for (int index = 0; index < ProgressiveSkillsData.MAX_PENDING_CARRIER_CLAIMS; index++) {
            assertTrue(data.addPendingClaim(claim(1_000 + index, 2_000 + index, 1_000L + index)));
        }
        PendingCarrierClaim overflow = claim(9_000, 9_001, 9_000);

        assertFalse(data.canAcceptPendingClaim(overflow));
        assertThrows(IllegalStateException.class, () -> data.addPendingClaim(overflow));
        assertEquals(ProgressiveSkillsData.MAX_PENDING_CARRIER_CLAIMS, data.pendingClaims().size());
    }

    @Test
    void versionThreeMigratesEmptyCarrierStateAndMalformedClaimsQuarantine() {
        CompoundTag versionThree = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        versionThree.putInt("data_version", 3);
        versionThree.remove("pending_carrier_claims");
        versionThree.remove("carrier_use_counters");

        ProgressiveSkillsData migrated = ProgressiveSkillsDataSerializer.decode(PLAYER, versionThree);

        assertTrue(migrated.active());
        assertTrue(migrated.pendingClaims().isEmpty());
        assertTrue(migrated.view().carrierUseCounters().isEmpty());
        assertEquals(3, migrated.view().migrationShadow().orElseThrow().sourceVersion());

        CompoundTag malformed = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        var claims = new ListTag();
        claims.add(new CompoundTag());
        malformed.put("pending_carrier_claims", claims);
        assertFalse(ProgressiveSkillsDataSerializer.decode(PLAYER, malformed).active());
    }

    private static PendingCarrierClaim claim(long claimId, long originId, long createdAt) {
        CarrierBehaviorSnapshot behavior = behavior();
        return new PendingCarrierClaim(
                uuid(claimId),
                uuid(originId),
                new CarrierIdentity(behavior.definitionId(), behavior.digest()),
                behavior.carrier(),
                CarrierStackState.fresh(
                        behavior.behaviorVersion(), behavior.charges(), PACK_DIGEST, uuid(claimId + 10_000)),
                behavior,
                Instant.ofEpochMilli(createdAt),
                "Inventory was full"
        );
    }

    private static CarrierBehaviorSnapshot behavior() {
        ResourceLocation definition = id("recovery_token");
        return new CarrierBehaviorSnapshot(
                definition,
                CarrierKind.TOKEN,
                3,
                CarrierMigrationPolicy.KEEP_PINNED,
                CarrierBindPolicy.ON_PICKUP,
                CarrierDeliveryPolicy.PENDING_CLAIM,
                1,
                2,
                20,
                List.of(new CarrierSkillXpAction(
                        id("grant_physique"), id("physique"), 5_000, 1))
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }

    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0, leastSignificantBits);
    }
}
