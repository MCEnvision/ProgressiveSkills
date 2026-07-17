package com.envisione.progressiveskills.server.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/** Persistent random identity preventing definition caches from crossing worlds or servers. */
public final class NetworkIdentitySavedData extends SavedData {
    public static final String FILE_ID = "progressiveskills_network_identity";
    private final UUID identity;

    private NetworkIdentitySavedData() {
        identity = UUID.randomUUID();
        setDirty();
    }

    private NetworkIdentitySavedData(UUID identity) {
        this.identity = identity;
    }

    public static SavedData.Factory<NetworkIdentitySavedData> factory() {
        return new SavedData.Factory<>(NetworkIdentitySavedData::new, NetworkIdentitySavedData::load);
    }

    public static NetworkIdentitySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public UUID identity() {
        return identity;
    }

    @Override
    public CompoundTag save(CompoundTag output, HolderLookup.Provider provider) {
        output.putUUID("identity", identity);
        return output;
    }

    static NetworkIdentitySavedData load(CompoundTag input, HolderLookup.Provider provider) {
        if (!input.hasUUID("identity")) {
            throw new IllegalArgumentException("ProgressiveSkills network identity is missing or malformed");
        }
        return new NetworkIdentitySavedData(input.getUUID("identity"));
    }
}
