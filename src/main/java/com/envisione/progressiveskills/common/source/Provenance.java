package com.envisione.progressiveskills.common.source;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Identifies an authoring document without embedding machine-specific absolute paths.
 * Provenance is intentionally separate from semantic IR values and their digests.
 *
 * @param packId stable content-pack id
 * @param sourcePath normalized POSIX path relative to the pack root
 * @param adapter stable authoring-adapter name, such as {@code toml}
 */
public record Provenance(ResourceLocation packId, String sourcePath, String adapter)
        implements Comparable<Provenance> {
    public static final int MAX_SOURCE_PATH_LENGTH = 1024;
    public static final int MAX_SOURCE_SEGMENT_LENGTH = 255;
    public static final int MAX_ADAPTER_LENGTH = 64;

    public Provenance {
        packId = StableId.requireValid(packId);
        sourcePath = requireRelativePosixPath(sourcePath);
        adapter = requireAdapter(adapter);
    }

    private static String requireRelativePosixPath(String path) {
        Objects.requireNonNull(path, "sourcePath");
        if (path.isEmpty() || path.length() > MAX_SOURCE_PATH_LENGTH) {
            throw new IllegalArgumentException("Source path length must be 1.." + MAX_SOURCE_PATH_LENGTH);
        }
        if (path.startsWith("/") || path.indexOf('\\') >= 0 || path.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Source path must be a relative POSIX path: " + path);
        }

        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Source path contains an invalid segment: " + path);
            }
            if (segment.length() > MAX_SOURCE_SEGMENT_LENGTH) {
                throw new IllegalArgumentException("Source path segment exceeds " + MAX_SOURCE_SEGMENT_LENGTH + " characters: " + segment);
            }
            for (int index = 0; index < segment.length();) {
                char unit = segment.charAt(index);
                if (Character.isHighSurrogate(unit)
                        && (index + 1 >= segment.length()
                        || !Character.isLowSurrogate(segment.charAt(index + 1)))) {
                    throw new IllegalArgumentException("Source path contains an unpaired high surrogate");
                }
                if (Character.isLowSurrogate(unit)) {
                    throw new IllegalArgumentException("Source path contains an unpaired low surrogate");
                }
                int codePoint = segment.codePointAt(index);
                if (Character.isISOControl(codePoint)) {
                    throw new IllegalArgumentException("Source path contains a control character");
                }
                index += Character.charCount(codePoint);
            }
        }
        return path;
    }

    private static String requireAdapter(String value) {
        Objects.requireNonNull(value, "adapter");
        if (value.isEmpty() || value.length() > MAX_ADAPTER_LENGTH) {
            throw new IllegalArgumentException("Adapter length must be 1.." + MAX_ADAPTER_LENGTH);
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean valid = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_'
                    || character == '-'
                    || character == '.';
            if (!valid) {
                throw new IllegalArgumentException("Adapter contains an invalid character: " + value);
            }
        }
        return value;
    }

    @Override
    public int compareTo(Provenance other) {
        int packComparison = packId.compareNamespaced(other.packId);
        if (packComparison != 0) {
            return packComparison;
        }
        int pathComparison = sourcePath.compareTo(other.sourcePath);
        return pathComparison != 0 ? pathComparison : adapter.compareTo(other.adapter);
    }
}
