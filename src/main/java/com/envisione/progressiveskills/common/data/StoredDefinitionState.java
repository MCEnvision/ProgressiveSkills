package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/** Generic state payload with lineage evidence for a stateful definition. */
public record StoredDefinitionState(
        DefinitionKey key,
        String lineage,
        String originLineage,
        int payloadVersion,
        CompoundTag payload
) {
    public StoredDefinitionState {
        Objects.requireNonNull(key, "key");
        lineage = requireDigest(lineage, "lineage");
        originLineage = requireDigest(originLineage, "originLineage");
        if (payloadVersion < 0) {
            throw new IllegalArgumentException("Definition-state payload version must not be negative");
        }
        payload = Objects.requireNonNull(payload, "payload").copy();
    }

    public static StoredDefinitionState create(
            DefinitionKey key,
            String lineage,
            int payloadVersion,
            CompoundTag payload
    ) {
        return new StoredDefinitionState(key, lineage, lineage, payloadVersion, payload);
    }

    @Override
    public CompoundTag payload() {
        return payload.copy();
    }

    public StoredDefinitionState rekey(DefinitionKey target, String targetLineage) {
        return new StoredDefinitionState(target, targetLineage, originLineage, payloadVersion, payload);
    }

    private static String requireDigest(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
        return value;
    }
}
