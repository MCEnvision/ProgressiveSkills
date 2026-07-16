package com.envisione.progressiveskills.common.id;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Extensible definition-kind identity and its top-level authoring directory.
 * Equality is based only on the stable kind id; schema registration is responsible for detecting
 * two providers that assign different directories to the same kind id.
 */
public final class DefinitionKind implements Comparable<DefinitionKind> {
    public static final int MAX_SOURCE_DIRECTORY_LENGTH = 64;

    private final ResourceLocation id;
    private final String sourceDirectory;

    public DefinitionKind(ResourceLocation id, String sourceDirectory) {
        this.id = StableId.requireValid(id);
        if (this.id.getPath().indexOf('/') >= 0) {
            throw new IllegalArgumentException("Definition-kind id path must be a single segment: " + id);
        }
        this.sourceDirectory = requireSourceDirectory(sourceDirectory);
    }

    public static DefinitionKind of(String id, String sourceDirectory) {
        return new DefinitionKind(StableId.parse(id), sourceDirectory);
    }

    public ResourceLocation id() {
        return id;
    }

    public String sourceDirectory() {
        return sourceDirectory;
    }

    private static String requireSourceDirectory(String value) {
        Objects.requireNonNull(value, "sourceDirectory");
        if (value.isEmpty() || value.length() > MAX_SOURCE_DIRECTORY_LENGTH
                || value.equals(".") || value.equals("..") || value.indexOf('/') >= 0) {
            throw new IllegalArgumentException(
                    "Definition source directory must be one nonblank segment of at most "
                            + MAX_SOURCE_DIRECTORY_LENGTH + " characters: " + value
            );
        }
        if (!ResourceLocation.isValidPath(value)) {
            throw new IllegalArgumentException("Invalid definition source directory: " + value);
        }
        return value;
    }

    @Override
    public int compareTo(DefinitionKind other) {
        return id.compareNamespaced(other.id);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof DefinitionKind kind && id.equals(kind.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
