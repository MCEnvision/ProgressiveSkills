package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.common.social.CombatContributionPolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvpAwardLedgerSavedDataTest {
    @Test
    void repeatAndDailyLimitsPersist() {
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        var data = new PvpAwardLedgerSavedData();

        assertEquals(1_000L, data.limit(attacker, victim, 100_000L, 10L,
                1_000L, 1, 1, policy()).allowedUnits());
        assertEquals(250L, data.limit(attacker, victim, 100_100L, 10L,
                1_000L, 1, 1, policy()).allowedUnits());
        var loaded = PvpAwardLedgerSavedData.load(data.save(new CompoundTag(), null), null);
        assertEquals(1_250L, loaded.limit(attacker, victim, 200_000L, 10L,
                2_000L, 1, 1, policy()).allowedUnits());
        assertEquals(0L, loaded.limit(attacker, victim, 300_000L, 10L,
                1L, 1, 1, policy()).allowedUnits());
    }

    @Test
    void duplicatePairsAndFutureVersionsQuarantine() {
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        var root = entryRoot(attacker, victim);
        root.getList("pairs", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .add(root.getList("pairs", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).copy());

        assertFalse(PvpAwardLedgerSavedData.load(root, null).active());

        var future = new CompoundTag();
        future.putInt("data_version", PvpAwardLedgerSavedData.DATA_VERSION + 1);
        var loaded = PvpAwardLedgerSavedData.load(future, null);
        assertFalse(loaded.active());
        assertTrue(loaded.quarantineReason().isPresent());
    }

    private static CompoundTag entryRoot(UUID attacker, UUID victim) {
        var root = new CompoundTag();
        root.putInt("data_version", PvpAwardLedgerSavedData.DATA_VERSION);
        var entries = new ListTag();
        var entry = new CompoundTag();
        entry.putUUID("attacker", attacker);
        entry.putUUID("victim", victim);
        entry.putLong("epoch_day", 10L);
        entry.putLong("last_award", 100L);
        entry.putLong("awarded", 1L);
        entries.add(entry);
        root.put("pairs", entries);
        return root;
    }

    private static CombatContributionPolicy policy() {
        return new CombatContributionPolicy(
                25L, 10_000L, 200, 1_000L, true,
                1_200, 2_500L, 2_500, 5, 500, 1_000);
    }
}
