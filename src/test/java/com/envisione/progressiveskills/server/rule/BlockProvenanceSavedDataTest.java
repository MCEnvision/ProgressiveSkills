package com.envisione.progressiveskills.server.rule;

import com.envisione.progressiveskills.common.rule.BlockOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockProvenanceSavedDataTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");

    @Test
    void placementsRoundTripAndBreakConsumptionRestoresNaturalInference() {
        var store = new BlockProvenanceSavedData();
        BlockPos creative = new BlockPos(1, 64, 1);
        BlockPos survival = new BlockPos(2, 64, 2);
        store.recordPlacement(OVERWORLD, creative, BlockOrigin.CREATIVE_PLACED);
        store.recordPlacement(OVERWORLD, survival, BlockOrigin.SURVIVAL_PLACED);

        CompoundTag encoded = store.save(new CompoundTag(), null);
        BlockProvenanceSavedData decoded = BlockProvenanceSavedData.load(encoded, null);

        assertEquals(BlockOrigin.CREATIVE_PLACED, decoded.originAt(OVERWORLD, creative));
        assertEquals(BlockOrigin.SURVIVAL_PLACED, decoded.consume(OVERWORLD, survival));
        assertEquals(BlockOrigin.NATURAL, decoded.originAt(OVERWORLD, survival));
        assertEquals(1, decoded.trackedCount());
    }

    @Test
    void pistonMovementTransfersOriginAndRemovesDestroyedEntries() {
        var store = new BlockProvenanceSavedData();
        BlockPos source = new BlockPos(4, 64, 4);
        BlockPos destroyed = new BlockPos(8, 64, 8);
        store.recordPlacement(OVERWORLD, source, BlockOrigin.SURVIVAL_PLACED);
        store.recordPlacement(OVERWORLD, destroyed, BlockOrigin.CREATIVE_PLACED);

        store.move(OVERWORLD, List.of(source), List.of(destroyed), Direction.EAST);

        assertEquals(BlockOrigin.NATURAL, store.originAt(OVERWORLD, source));
        assertEquals(BlockOrigin.SURVIVAL_PLACED, store.originAt(OVERWORLD, source.east()));
        assertEquals(BlockOrigin.NATURAL, store.originAt(OVERWORLD, destroyed));
    }

    @Test
    void capacityAndMalformedDataFailClosedForUntrackedBlocks() {
        var full = new BlockProvenanceSavedData(1);
        full.recordPlacement(OVERWORLD, BlockPos.ZERO, BlockOrigin.SURVIVAL_PLACED);
        full.recordPlacement(OVERWORLD, BlockPos.ZERO.above(), BlockOrigin.SURVIVAL_PLACED);

        assertFalse(full.reliable());
        assertEquals(BlockOrigin.SURVIVAL_PLACED, full.originAt(OVERWORLD, BlockPos.ZERO));
        assertEquals(BlockOrigin.UNKNOWN, full.originAt(OVERWORLD, BlockPos.ZERO.above(2)));
        assertTrue(full.issue().isPresent());

        var malformed = new CompoundTag();
        malformed.putInt("data_version", 99);
        BlockProvenanceSavedData decoded = BlockProvenanceSavedData.load(malformed, null);
        assertFalse(decoded.reliable());
        assertEquals(BlockOrigin.UNKNOWN, decoded.originAt(OVERWORLD, BlockPos.ZERO));
    }
}
