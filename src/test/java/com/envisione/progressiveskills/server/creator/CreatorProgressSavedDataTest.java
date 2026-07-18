package com.envisione.progressiveskills.server.creator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatorProgressSavedDataTest {
    @Test
    void timedResourceChangesCommitCooldownAndAwardTogether() {
        var data = new CreatorProgressSavedData();
        UUID player = UUID.randomUUID();
        ResourceLocation ready = id("ready");
        ResourceLocation resource = id("resource");
        var change = new CreatorProgressSavedData.ResourceChange(resource, 3, 0, 10, 0);

        assertTrue(data.applyTimedResourceChange(player, ready, 100, 20, Optional.of(change)).applied());
        assertFalse(data.applyTimedResourceChange(player, ready, 110, 20, Optional.of(change)).applied());
        assertEquals(3L, data.state(player).resources().get(resource));
        assertEquals(120L, data.state(player).resources().get(ready));
    }

    @Test
    void comboStateRecoversWhenSequenceDefinitionShrinks() {
        var data = new CreatorProgressSavedData();
        UUID player = UUID.randomUUID();
        ResourceLocation combo = id("combo");
        ResourceLocation first = id("first");
        ResourceLocation second = id("second");
        ResourceLocation third = id("third");
        data.awardCombo(player, combo, List.of(first, second, third), first,
                10, 100, 0, 10, 1000, 10);
        data.awardCombo(player, combo, List.of(first, second, third), second,
                11, 100, 0, 10, 1000, 10);

        var state = data.awardCombo(player, combo, List.of(first, third), first,
                12, 100, 0, 10, 1000, 10);

        assertEquals(1, state.index());
    }

    @Test
    void duplicatePlayerDataQuarantinesWholeCreatorStore() {
        UUID player = UUID.randomUUID();
        var root = new CompoundTag();
        root.putInt("data_version", CreatorProgressSavedData.DATA_VERSION);
        var players = new ListTag();
        players.add(emptyPlayer(player));
        players.add(emptyPlayer(player));
        root.put("players", players);

        CreatorProgressSavedData loaded = CreatorProgressSavedData.load(root, null);

        assertFalse(loaded.active());
        assertTrue(loaded.quarantineReason().isPresent());
        assertThrows(IllegalStateException.class, () -> loaded.state(player));
    }

    @Test
    void skillAwardSideEffectsAreReceiptProtectedAcrossReload() {
        var data = new CreatorProgressSavedData();
        UUID player = UUID.randomUUID();
        UUID receipt = UUID.randomUUID();
        ResourceLocation challenge = id("challenge");

        assertTrue(data.runSkillAward(player, receipt,
                () -> data.progressChallenge(player, challenge, 3, 10)));
        assertFalse(data.runSkillAward(player, receipt,
                () -> data.progressChallenge(player, challenge, 3, 10)));
        CreatorProgressSavedData loaded = CreatorProgressSavedData.load(
                data.save(new CompoundTag(), null), null);
        assertFalse(loaded.runSkillAward(player, receipt,
                () -> loaded.progressChallenge(player, challenge, 3, 10)));
        assertEquals(3L, loaded.state(player).challenges().get(challenge));
    }

    private static CompoundTag emptyPlayer(UUID player) {
        var tag = new CompoundTag();
        tag.putUUID("player", player);
        for (String key : List.of("resources", "prestige", "choices", "milestone_receipts",
                "contracts", "combos", "loadouts", "challenges")) {
            tag.put(key, new ListTag());
        }
        return tag;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
