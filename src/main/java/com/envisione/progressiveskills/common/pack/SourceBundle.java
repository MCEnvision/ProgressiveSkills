package com.envisione.progressiveskills.common.pack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, quota-bounded canonical source files retained for last-known-good recovery. */
public final class SourceBundle {
    public static final int MAX_FILES = 16_384;
    public static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 64L * 1024L * 1024L;
    private final Map<String, byte[]> files;
    private final long totalBytes;

    private SourceBundle(Map<String, byte[]> source) {
        if (source.size() > MAX_FILES) {
            throw new IllegalArgumentException("Source bundle exceeds " + MAX_FILES + " files");
        }
        var sorted = new TreeMap<String, byte[]>();
        long bytes = 0;
        for (var entry : source.entrySet()) {
            String path = requirePath(entry.getKey());
            byte[] value = Objects.requireNonNull(entry.getValue(), "source bytes").clone();
            if (value.length > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Source file exceeds " + MAX_FILE_BYTES + " bytes: " + path);
            }
            bytes = Math.addExact(bytes, value.length);
            if (bytes > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("Source bundle exceeds " + MAX_TOTAL_BYTES + " bytes");
            }
            if (sorted.putIfAbsent(path, value) != null) {
                throw new IllegalArgumentException("Duplicate source bundle path: " + path);
            }
        }
        this.files = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        this.totalBytes = bytes;
    }

    public static SourceBundle of(Map<String, byte[]> files) {
        return new SourceBundle(Objects.requireNonNull(files, "files"));
    }

    public static SourceBundle empty() {
        return new SourceBundle(Map.of());
    }

    public Map<String, byte[]> files() {
        var copy = new LinkedHashMap<String, byte[]>();
        files.forEach((path, value) -> copy.put(path, value.clone()));
        return Collections.unmodifiableMap(copy);
    }

    public long totalBytes() {
        return totalBytes;
    }

    /** Stable digest of every retained source path and byte, including comments and field order. */
    public String digest() {
        return digestPrefix("");
    }

    String digestPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeUTF("progressiveskills-source-bundle-v1");
                var selected = files.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith(prefix))
                        .toList();
                output.writeInt(selected.size());
                for (var entry : selected) {
                    byte[] path = entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    output.writeInt(path.length);
                    output.write(path);
                    output.writeInt(entry.getValue().length);
                    output.write(entry.getValue());
                }
            }
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory source digest failure", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SourceBundle bundle) || !files.keySet().equals(bundle.files.keySet())) {
            return false;
        }
        return files.entrySet().stream().allMatch(entry -> Arrays.equals(entry.getValue(), bundle.files.get(entry.getKey())));
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (var entry : files.entrySet()) {
            result = 31 * result + entry.getKey().hashCode();
            result = 31 * result + Arrays.hashCode(entry.getValue());
        }
        return result;
    }

    private static String requirePath(String value) {
        Objects.requireNonNull(value, "path");
        if (value.isEmpty() || value.length() > 2_048 || value.startsWith("/") || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid source-bundle path: " + value);
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid source-bundle path: " + value);
            }
        }
        return value;
    }
}
