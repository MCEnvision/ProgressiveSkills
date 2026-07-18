package com.envisione.progressiveskills.server.hardening;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MassCheckSavedDataTest {
    @Test
    void roundTripsVersionedSessions() {
        UUID player = UUID.randomUUID();
        var data = new MassCheckSavedData();
        data.start(player);
        data.mark(player, MassCheckSavedData.Result.PASS, "Passed");

        MassCheckSavedData loaded = MassCheckSavedData.load(
                data.save(new CompoundTag(), null), null);

        assertTrue(loaded.active());
        assertTrue(loaded.session(player).isPresent());
    }

    @Test
    void quarantinesFutureVersionsAndDuplicatePlayers() {
        var future = new CompoundTag();
        future.putInt("data_version", MassCheckSavedData.DATA_VERSION + 1);
        assertFalse(MassCheckSavedData.load(future, null).active());

        UUID player = UUID.randomUUID();
        var source = new MassCheckSavedData();
        source.start(player);
        CompoundTag duplicate = source.save(new CompoundTag(), null);
        ListTag sessions = duplicate.getList("sessions", Tag.TAG_COMPOUND);
        sessions.add(sessions.getCompound(0).copy());
        assertFalse(MassCheckSavedData.load(duplicate, null).active());
    }
}
