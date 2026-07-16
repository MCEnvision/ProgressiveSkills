package com.envisione.progressiveskills.server.audit;

import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceMetadataSavedDataTest {
    @Test
    void schemaBumpReminderIsRecordedOnceAndFutureMetadataFailsClosed() {
        var metadata = new PersistenceMetadataSavedData();

        var first = metadata.observeCurrentVersion().orElseThrow();

        assertEquals(0, first.previousVersion());
        assertEquals(ProgressiveSkillsData.CURRENT_DATA_VERSION, first.currentVersion());
        assertTrue(metadata.observeCurrentVersion().isEmpty());

        var futureTag = new CompoundTag();
        futureTag.putInt("player_data_version", ProgressiveSkillsData.CURRENT_DATA_VERSION + 1);
        var future = PersistenceMetadataSavedData.load(futureTag, null);
        assertThrows(IllegalStateException.class, future::observeCurrentVersion);
    }
}
