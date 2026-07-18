package com.envisione.progressiveskills.common.studio;

import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StudioDraft(
        ResourceLocation id,
        UUID owner,
        String namespace,
        String name,
        String baseDigest,
        long revision,
        Status status,
        Instant createdAt,
        Instant updatedAt
) {
    public StudioDraft {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        namespace = Objects.requireNonNull(namespace, "namespace");
        name = Objects.requireNonNull(name, "name");
        baseDigest = Objects.requireNonNull(baseDigest, "baseDigest");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (revision < 0) {
            throw new IllegalArgumentException("Studio draft revision is invalid");
        }
    }

    public enum Status {
        EDITING,
        CONFLICTED,
        VALIDATED,
        PUBLISHED,
        ARCHIVED
    }
}
