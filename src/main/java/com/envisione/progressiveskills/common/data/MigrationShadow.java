package com.envisione.progressiveskills.common.data;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/** Bounded pre-migration raw state retained through a clean login/save cycle. */
public record MigrationShadow(int sourceVersion, MigrationShadowStatus status, CompoundTag rawData) {
    public MigrationShadow {
        if (sourceVersion < 0 || sourceVersion >= ProgressiveSkillsData.CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException("Migration shadow source version is not an older supported version");
        }
        Objects.requireNonNull(status, "status");
        rawData = Objects.requireNonNull(rawData, "rawData").copy();
    }

    @Override
    public CompoundTag rawData() {
        return rawData.copy();
    }

    public MigrationShadow persistedAfterLogin() {
        return new MigrationShadow(sourceVersion, MigrationShadowStatus.PERSISTED_AFTER_LOGIN, rawData);
    }
}
