package com.envisione.progressiveskills.server.social;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamSavedDataTest {
    @Test
    void ownershipAndMembershipLifecyclePersists() {
        var data = new TeamSavedData();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        data.create(owner, id("team"));
        data.invite(owner, member);
        data.accept(member);
        long revision = data.team(owner).orElseThrow().revision();
        data.transferOwnership(owner, member);

        TeamSavedData loaded = TeamSavedData.load(data.save(new CompoundTag(), null), null);

        assertEquals(member, loaded.team(owner).orElseThrow().owner());
        assertTrue(loaded.team(owner).orElseThrow().revision() > revision);
        loaded.kick(member, owner);
        assertTrue(loaded.team(owner).isEmpty());
    }

    @Test
    void unsupportedVersionQuarantinesTeamStore() {
        var root = new CompoundTag();
        root.putInt("data_version", TeamSavedData.DATA_VERSION + 1);

        TeamSavedData loaded = TeamSavedData.load(root, null);

        assertFalse(loaded.active());
        assertTrue(loaded.quarantineReason().isPresent());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
