package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.common.carrier.CarrierUseReservationStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrierUseLedgerTest {
    private static final UUID PLAYER = uuid(1);
    private static final UUID INSTANCE = uuid(2);

    @Test
    void exactCountersReserveThenAdvanceOnlyAfterCommit() {
        ProgressiveSkillsData data = ProgressiveSkillsData.empty(PLAYER);

        assertEquals(CarrierUseReservationStatus.SKIPPED, data.previewCarrierUse(INSTANCE, 1));
        assertEquals(CarrierUseReservationStatus.RESERVED, data.reserveCarrierUse(INSTANCE, 0));
        assertEquals(CarrierUseReservationStatus.ALREADY_RESERVED, data.previewCarrierUse(INSTANCE, 0));
        assertEquals(0, data.nextExpectedCarrierUse(INSTANCE));
        assertTrue(data.cancelCarrierUse(INSTANCE, 0));
        assertEquals(CarrierUseReservationStatus.RESERVED, data.reserveCarrierUse(INSTANCE, 0));

        data.commitCarrierUse(INSTANCE, 0);

        assertEquals(1, data.nextExpectedCarrierUse(INSTANCE));
        assertEquals(CarrierUseReservationStatus.REPLAYED, data.previewCarrierUse(INSTANCE, 0));
        assertEquals(CarrierUseReservationStatus.SKIPPED, data.previewCarrierUse(INSTANCE, 2));
        assertEquals(CarrierUseReservationStatus.RESERVED, data.previewCarrierUse(INSTANCE, 1));
    }

    @Test
    void committedCountersSurviveRestartWhileUncommittedReservationsDoNotAdvance() {
        ProgressiveSkillsData data = ProgressiveSkillsData.empty(PLAYER);
        data.reserveCarrierUse(INSTANCE, 0);
        data.commitCarrierUse(INSTANCE, 0);
        data.reserveCarrierUse(INSTANCE, 1);

        ProgressiveSkillsData restarted = ProgressiveSkillsDataSerializer.decode(
                PLAYER, ProgressiveSkillsDataSerializer.encode(data));

        assertTrue(restarted.active());
        assertEquals(1, restarted.nextExpectedCarrierUse(INSTANCE));
        assertEquals(CarrierUseReservationStatus.RESERVED, restarted.reserveCarrierUse(INSTANCE, 1));
        restarted.commitCarrierUse(INSTANCE, 1);
        assertEquals(2, restarted.nextExpectedCarrierUse(INSTANCE));
    }

    @Test
    void fullLedgerRejectsNewInstancesWithoutEviction() {
        CompoundTag encoded = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        var counters = new ListTag();
        for (int index = 0; index < ProgressiveSkillsData.MAX_CARRIER_USE_COUNTERS; index++) {
            var entry = new CompoundTag();
            entry.putString("instance_id", uuid(10_000L + index).toString());
            entry.putLong("next_counter", 1);
            counters.add(entry);
        }
        encoded.put("carrier_use_counters", counters);

        ProgressiveSkillsData full = ProgressiveSkillsDataSerializer.decode(PLAYER, encoded);

        assertTrue(full.active());
        assertEquals(ProgressiveSkillsData.MAX_CARRIER_USE_COUNTERS,
                full.view().carrierUseCounters().size());
        assertEquals(CarrierUseReservationStatus.CAPACITY_FULL,
                full.reserveCarrierUse(uuid(99_999), 0));
        assertFalse(full.view().carrierUseCounters().containsKey(uuid(99_999)));
    }

    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0, leastSignificantBits);
    }
}
