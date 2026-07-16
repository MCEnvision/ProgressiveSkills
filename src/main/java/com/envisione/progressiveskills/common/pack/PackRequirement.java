package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Typed pack dependency with an optional content-version constraint. */
public record PackRequirement(ResourceLocation packId, Optional<VersionConstraint> version) {
    public PackRequirement {
        packId = StableId.requireValid(packId);
        Objects.requireNonNull(version, "version");
    }

    public static PackRequirement parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf('@');
        if (separator < 0) {
            return new PackRequirement(StableId.parse(value), Optional.empty());
        }
        if (separator == 0 || separator == value.length() - 1 || separator != value.lastIndexOf('@')) {
            throw new IllegalArgumentException("Invalid pack dependency: " + value);
        }
        return new PackRequirement(
                StableId.parse(value.substring(0, separator)),
                Optional.of(VersionConstraint.parse(value.substring(separator + 1)))
        );
    }
}
