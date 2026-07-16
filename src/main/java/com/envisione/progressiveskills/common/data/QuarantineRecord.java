package com.envisione.progressiveskills.common.data;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/** Raw bounded player data withheld from gameplay after an unsafe decode or future schema. */
public record QuarantineRecord(int sourceVersion, String reason, String rawDigest, CompoundTag rawData) {
    public static final int MAX_REASON_LENGTH = 512;

    public QuarantineRecord {
        if (sourceVersion < -1) {
            throw new IllegalArgumentException("Quarantine source version must be -1 or greater");
        }
        reason = Objects.requireNonNull(reason, "reason").strip();
        if (reason.isEmpty() || reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Quarantine reason must be bounded");
        }
        rawDigest = Objects.requireNonNull(rawDigest, "rawDigest");
        if (!rawDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Quarantine raw digest must be lowercase SHA-256");
        }
        rawData = Objects.requireNonNull(rawData, "rawData").copy();
    }

    @Override
    public CompoundTag rawData() {
        return rawData.copy();
    }
}
