package com.envisione.progressiveskills.server.ability;

import com.envisione.progressiveskills.common.ability.AbilityTargetMode;
import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AbilityExecutionTarget(
        AbilityTargetMode mode,
        Optional<UUID> entityId,
        Optional<BlockPos> blockPos
) {
    public AbilityExecutionTarget {
        Objects.requireNonNull(mode, "mode");
        entityId = Objects.requireNonNull(entityId, "entityId");
        blockPos = Objects.requireNonNull(blockPos, "blockPos").map(BlockPos::immutable);
        boolean valid = switch (mode) {
            case SELF -> entityId.isEmpty() && blockPos.isEmpty();
            case ENTITY -> entityId.isPresent() && blockPos.isEmpty();
            case BLOCK -> entityId.isEmpty() && blockPos.isPresent();
        };
        if (!valid) {
            throw new IllegalArgumentException("Ability execution target shape is invalid");
        }
    }

    public static AbilityExecutionTarget self() {
        return new AbilityExecutionTarget(AbilityTargetMode.SELF, Optional.empty(), Optional.empty());
    }

    public static AbilityExecutionTarget entity(UUID entityId) {
        return new AbilityExecutionTarget(
                AbilityTargetMode.ENTITY,
                Optional.of(Objects.requireNonNull(entityId, "entityId")),
                Optional.empty()
        );
    }

    public static AbilityExecutionTarget block(BlockPos blockPos) {
        return new AbilityExecutionTarget(
                AbilityTargetMode.BLOCK,
                Optional.empty(),
                Optional.of(Objects.requireNonNull(blockPos, "blockPos"))
        );
    }
}
