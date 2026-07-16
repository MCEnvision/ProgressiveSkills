package com.envisione.progressiveskills.common.id;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Strict parsing and deterministic path-derived identity for canonical definitions. */
public final class StableId {
    public static final int MAX_NAMESPACE_LENGTH = 64;
    public static final int MAX_PATH_LENGTH = 255;
    public static final int MAX_PATH_SEGMENT_LENGTH = 64;
    public static final int MAX_SERIALIZED_LENGTH = MAX_NAMESPACE_LENGTH + 1 + MAX_PATH_LENGTH;
    public static final int MAX_SOURCE_PATH_LENGTH = 1024;
    public static final int MAX_SOURCE_SEGMENT_LENGTH = 255;

    private StableId() {
    }

    /** Parses a full, explicitly namespaced identifier. Minecraft's implicit namespace is rejected. */
    public static ResourceLocation parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(ResourceLocation.NAMESPACE_SEPARATOR);
        if (separator < 1 || separator != value.lastIndexOf(ResourceLocation.NAMESPACE_SEPARATOR) || separator == value.length() - 1) {
            throw new IllegalArgumentException("Stable ids must use a nonblank explicit namespace (`namespace:path`): " + value);
        }
        if (value.length() > MAX_SERIALIZED_LENGTH) {
            throw new IllegalArgumentException("Stable id exceeds " + MAX_SERIALIZED_LENGTH + " characters");
        }

        String namespace = value.substring(0, separator);
        String path = value.substring(separator + 1);
        requireNamespace(namespace);
        requirePath(path);
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static Optional<ResourceLocation> tryParse(String value) {
        try {
            return Optional.of(parse(value));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Optional.empty();
        }
    }

    /** Revalidates a ResourceLocation because vanilla permits empty namespace and path values. */
    public static ResourceLocation requireValid(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        requireNamespace(id.getNamespace());
        requirePath(id.getPath());
        if (id.toString().length() > MAX_SERIALIZED_LENGTH) {
            throw new IllegalArgumentException("Stable id exceeds " + MAX_SERIALIZED_LENGTH + " characters: " + id);
        }
        return id;
    }

    /**
     * Derives {@code namespace:relative/path} from a normalized source such as
     * {@code skills/relative/path.toml}. The definition-kind directory and extension are identity-neutral.
     */
    public static ResourceLocation derive(DefinitionKind kind, String namespace, String sourcePath) {
        Objects.requireNonNull(kind, "kind");
        requireNamespace(namespace);
        String[] segments = requireSourcePath(sourcePath);
        if (segments.length < 2 || !segments[0].equals(kind.sourceDirectory())) {
            throw new IllegalArgumentException(
                    "Definition source must be beneath `" + kind.sourceDirectory() + "/`: " + sourcePath
            );
        }

        String fileName = segments[segments.length - 1];
        if (!fileName.endsWith(".toml") || fileName.length() == ".toml".length()) {
            throw new IllegalArgumentException("Definition source must end in a nonblank lowercase .toml filename: " + sourcePath);
        }
        segments[segments.length - 1] = fileName.substring(0, fileName.length() - ".toml".length());

        StringBuilder definitionPath = new StringBuilder();
        for (int index = 1; index < segments.length; index++) {
            if (index > 1) {
                definitionPath.append('/');
            }
            definitionPath.append(segments[index]);
        }
        requirePath(definitionPath.toString());
        return ResourceLocation.fromNamespaceAndPath(namespace, definitionPath.toString());
    }

    /** Derives an id and requires an explicitly-authored id to match it exactly. */
    public static ResourceLocation deriveAndMatch(
            DefinitionKind kind,
            String namespace,
            String sourcePath,
            String explicitId
    ) {
        ResourceLocation derived = derive(kind, namespace, sourcePath);
        ResourceLocation authored = parse(explicitId);
        if (!derived.equals(authored)) {
            throw new IllegalArgumentException("Explicit id `" + authored + "` does not match path-derived id `" + derived + "`");
        }
        return derived;
    }

    static String requireNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        if (namespace.isEmpty() || namespace.length() > MAX_NAMESPACE_LENGTH || namespace.equals(".") || namespace.equals("..")) {
            throw new IllegalArgumentException("Stable-id namespace length must be 1.." + MAX_NAMESPACE_LENGTH + ": " + namespace);
        }
        if (!ResourceLocation.isValidNamespace(namespace)) {
            throw new IllegalArgumentException("Invalid stable-id namespace: " + namespace);
        }
        return namespace;
    }

    static String requirePath(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isEmpty() || path.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("Stable-id path length must be 1.." + MAX_PATH_LENGTH + ": " + path);
        }
        if (!ResourceLocation.isValidPath(path)) {
            throw new IllegalArgumentException("Invalid stable-id path: " + path);
        }

        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Stable-id path contains an invalid segment: " + path);
            }
            if (segment.length() > MAX_PATH_SEGMENT_LENGTH) {
                throw new IllegalArgumentException(
                        "Stable-id path segment exceeds " + MAX_PATH_SEGMENT_LENGTH + " characters: " + segment
                );
            }
        }
        return path;
    }

    private static String[] requireSourcePath(String sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        if (sourcePath.isEmpty() || sourcePath.length() > MAX_SOURCE_PATH_LENGTH) {
            throw new IllegalArgumentException("Source path length must be 1.." + MAX_SOURCE_PATH_LENGTH);
        }
        if (sourcePath.startsWith("/") || sourcePath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Definition source must be a relative POSIX path: " + sourcePath);
        }

        String[] segments = sourcePath.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Definition source contains an invalid path segment: " + sourcePath);
            }
            if (segment.length() > MAX_SOURCE_SEGMENT_LENGTH) {
                throw new IllegalArgumentException(
                        "Definition source segment exceeds " + MAX_SOURCE_SEGMENT_LENGTH + " characters: " + segment
                );
            }
            for (int index = 0; index < segment.length(); index++) {
                if (Character.isISOControl(segment.charAt(index))) {
                    throw new IllegalArgumentException("Definition source contains a control character");
                }
            }
        }
        return segments;
    }
}
