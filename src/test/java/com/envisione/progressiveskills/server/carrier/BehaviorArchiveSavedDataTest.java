package com.envisione.progressiveskills.server.carrier;

import com.envisione.progressiveskills.common.carrier.CarrierBehaviorSnapshot;
import com.envisione.progressiveskills.common.carrier.CarrierBindPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierDeliveryPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierKind;
import com.envisione.progressiveskills.common.carrier.CarrierMigrationPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierSkillXpAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviorArchiveSavedDataTest {
    @Test
    void archiveRoundTripsAndRetainsPinnedBehaviorVersions() {
        CarrierBehaviorSnapshot first = behavior(1, 250);
        CarrierBehaviorSnapshot second = behavior(2, 500);
        var archive = new BehaviorArchiveSavedData();

        BehaviorArchiveSavedData.Reservation reservation = archive.reserve(List.of(first, second));
        CompoundTag encoded = archive.save(new CompoundTag(), null);
        BehaviorArchiveSavedData decoded = BehaviorArchiveSavedData.load(encoded, null);

        assertEquals(2, reservation.addedEntries());
        assertEquals(first, decoded.resolve(first.digest()).orElseThrow());
        assertEquals(second, decoded.resolve(second.digest()).orElseThrow());
        assertTrue(decoded.verify().valid());
        assertEquals(2, decoded.status().entries());
    }

    @Test
    void reservationIsAtomicAndDuplicateReservationConsumesNoQuota() {
        CarrierBehaviorSnapshot first = behavior(1, 250);
        CarrierBehaviorSnapshot second = behavior(2, 500);
        var archive = new BehaviorArchiveSavedData(1, BehaviorArchiveSavedData.MAX_TOTAL_BYTES);

        BehaviorArchiveSavedData.Reservation preview = archive.previewReserve(List.of(first));
        assertEquals(1, preview.addedEntries());
        assertEquals(0, archive.status().entries());
        archive.reserve(List.of(first));
        assertEquals(0, archive.reserve(List.of(first)).addedEntries());
        assertThrows(IllegalStateException.class, () -> archive.reserve(List.of(second)));

        assertEquals(1, archive.status().entries());
        assertEquals(first, archive.resolve(first.digest()).orElseThrow());
        assertFalse(archive.contains(second.digest()));
    }

    @Test
    void byteQuotaRejectsTheWholeReservationWithoutEvictingPinnedEntries() {
        CarrierBehaviorSnapshot first = behavior(1, 250);
        CarrierBehaviorSnapshot second = behavior(2, 500);
        int firstBytes = com.envisione.progressiveskills.common.carrier.CarrierBehaviorCodec
                .encodeSnapshot(first).length;
        var archive = new BehaviorArchiveSavedData(2, firstBytes);

        archive.reserve(List.of(first));
        assertThrows(IllegalStateException.class, () -> archive.reserve(List.of(second)));

        assertEquals(firstBytes, archive.status().bytes());
        assertEquals(first, archive.resolve(first.digest()).orElseThrow());
        assertFalse(archive.contains(second.digest()));
    }

    @Test
    void corruptedPayloadQuarantinesTheWholeStoreAndRefusesReservation() {
        CarrierBehaviorSnapshot snapshot = behavior(1, 250);
        var archive = new BehaviorArchiveSavedData();
        archive.reserve(List.of(snapshot));
        CompoundTag encoded = archive.save(new CompoundTag(), null);
        ListTag entries = encoded.getList("entries", CompoundTag.TAG_COMPOUND);
        CompoundTag entry = entries.getCompound(0);
        byte[] payload = entry.getByteArray("payload");
        byte[] original = payload.clone();
        payload[payload.length - 1] ^= 1;
        entry.putByteArray("payload", payload);

        BehaviorArchiveSavedData decoded = BehaviorArchiveSavedData.load(encoded, null);

        assertArrayEquals(original, archive.save(new CompoundTag(), null)
                .getList("entries", CompoundTag.TAG_COMPOUND).getCompound(0).getByteArray("payload"));
        assertFalse(decoded.status().reliable());
        assertTrue(decoded.resolve(snapshot.digest()).isEmpty());
        assertThrows(IllegalStateException.class, () -> decoded.reserve(List.of(snapshot)));
    }

    @Test
    void malformedVersionFailsClosed() {
        var malformed = new CompoundTag();
        malformed.putInt("data_version", 99);

        BehaviorArchiveSavedData decoded = BehaviorArchiveSavedData.load(malformed, null);

        assertFalse(decoded.verify().valid());
        assertTrue(decoded.status().quarantine().isPresent());
    }

    private static CarrierBehaviorSnapshot behavior(int version, long amount) {
        return new CarrierBehaviorSnapshot(
                id("physique_tome"),
                CarrierKind.TOME,
                version,
                CarrierMigrationPolicy.KEEP_PINNED,
                CarrierBindPolicy.ON_USE,
                CarrierDeliveryPolicy.PENDING_CLAIM,
                1,
                3,
                20,
                List.of(new CarrierSkillXpAction(id("award"), id("physique"), amount, 1))
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
