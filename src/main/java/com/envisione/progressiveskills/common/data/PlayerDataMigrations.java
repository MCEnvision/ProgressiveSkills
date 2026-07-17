package com.envisione.progressiveskills.common.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Pure raw-tag migrations that run only after the input passes hard NBT limits. */
public final class PlayerDataMigrations {
    private PlayerDataMigrations() {
    }

    public static CompoundTag migrateToCurrent(CompoundTag input, int sourceVersion) {
        CompoundTag current = input.copy();
        int version = sourceVersion;
        while (version < ProgressiveSkillsData.CURRENT_DATA_VERSION) {
            current = switch (version) {
                case 1 -> migrateV1ToV2(current);
                case 2 -> migrateV2ToV3(current);
                default -> throw new IllegalArgumentException("No player-data migration from version " + version);
            };
            version++;
        }
        return current;
    }

    static CompoundTag migrateV1ToV2(CompoundTag input) {
        CompoundTag output = input.copy();
        output.putInt("data_version", 2);
        if (output.contains("revision", Tag.TAG_LONG) && !output.contains("storage_revision", Tag.TAG_LONG)) {
            output.putLong("storage_revision", output.getLong("revision"));
        }
        output.remove("revision");
        if (!output.contains("status", Tag.TAG_STRING)) {
            output.putString("status", PlayerDataStatus.ACTIVE.name());
        }
        if (!output.contains("transaction", Tag.TAG_COMPOUND)) {
            output.put("transaction", new CompoundTag());
        }
        if (output.contains("definition_generation", Tag.TAG_LONG)
                && output.contains("definition_digest", Tag.TAG_STRING)
                && !output.contains("state_definition", Tag.TAG_COMPOUND)) {
            var definition = new CompoundTag();
            definition.putLong("generation", output.getLong("definition_generation"));
            definition.putString("digest", output.getString("definition_digest"));
            output.put("state_definition", definition);
        }
        output.remove("definition_generation");
        output.remove("definition_digest");
        return output;
    }

    static CompoundTag migrateV2ToV3(CompoundTag input) {
        CompoundTag output = input.copy();
        output.putInt("data_version", 3);
        CompoundTag transaction = output.contains("transaction", Tag.TAG_COMPOUND)
                ? output.getCompound("transaction").copy()
                : new CompoundTag();
        if (!transaction.contains("paid_costs")) {
            transaction.put("paid_costs", new ListTag());
        }
        output.put("transaction", transaction);
        return output;
    }
}
