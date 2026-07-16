package com.envisione.progressiveskills.server.audit;

import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Optional;

/** World-scoped record used to emit one honest backup reminder for each player-data schema bump. */
public final class PersistenceMetadataSavedData extends SavedData {
    public static final String FILE_ID = "progressiveskills_persistence_metadata";
    private int observedPlayerDataVersion;

    public static SavedData.Factory<PersistenceMetadataSavedData> factory() {
        return new SavedData.Factory<>(PersistenceMetadataSavedData::new, PersistenceMetadataSavedData::load);
    }

    public static PersistenceMetadataSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public synchronized Optional<VersionChange> observeCurrentVersion() {
        int current = ProgressiveSkillsData.CURRENT_DATA_VERSION;
        if (observedPlayerDataVersion > current) {
            throw new IllegalStateException("World persistence metadata was written by newer player data v"
                    + observedPlayerDataVersion);
        }
        if (observedPlayerDataVersion == current) {
            return Optional.empty();
        }
        VersionChange change = new VersionChange(observedPlayerDataVersion, current);
        observedPlayerDataVersion = current;
        setDirty();
        return Optional.of(change);
    }

    @Override
    public synchronized CompoundTag save(CompoundTag output, HolderLookup.Provider provider) {
        output.putInt("player_data_version", observedPlayerDataVersion);
        return output;
    }

    static PersistenceMetadataSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        var result = new PersistenceMetadataSavedData();
        if (input.contains("player_data_version", Tag.TAG_INT)) {
            int version = input.getInt("player_data_version");
            if (version < 0) {
                throw new IllegalArgumentException("Observed player-data version must not be negative");
            }
            result.observedPlayerDataVersion = version;
        }
        return result;
    }

    public record VersionChange(int previousVersion, int currentVersion) {
        public VersionChange {
            if (previousVersion < 0 || currentVersion <= previousVersion) {
                throw new IllegalArgumentException("Persistence version change must increase monotonically");
            }
        }
    }
}
