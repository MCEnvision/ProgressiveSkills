package com.envisione.progressiveskills.server.tree;

import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import com.envisione.progressiveskills.common.data.QuarantineRecord;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TreeRuntimeTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Test
    void commandRuntimeRejectsQuarantinedDataBeforePlanning() {
        assertDoesNotThrow(() -> TreeRuntime.requireActivePlayerData(
                ProgressiveSkillsData.empty(PLAYER)
        ));
        var quarantined = ProgressiveSkillsData.quarantined(
                PLAYER,
                new QuarantineRecord(3, "Invalid data", "0".repeat(64), new CompoundTag())
        );

        assertThrows(IllegalStateException.class,
                () -> TreeRuntime.requireActivePlayerData(quarantined));
    }
}
