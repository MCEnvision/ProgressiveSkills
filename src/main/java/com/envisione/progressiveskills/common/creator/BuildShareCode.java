package com.envisione.progressiveskills.common.creator;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class BuildShareCode {
    public static final int MAX_CODE_BYTES = 16_384;
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{16}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private BuildShareCode() {
    }

    public static String encode(Build build) {
        byte[] payload = payload(build);
        String digest = digest(payload).substring(0, 16);
        return "PSB1." + digest + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    }

    public static Build decode(String code) {
        String[] parts = Objects.requireNonNull(code, "code").split("\\.", 3);
        if (parts.length != 3 || !parts[0].equals("PSB1")
                || !CHECKSUM.matcher(parts[1]).matches()
                || parts[2].isEmpty() || parts[2].length() > MAX_CODE_BYTES * 2) {
            throw new IllegalArgumentException("Build share code envelope is invalid");
        }
        byte[] payload;
        try {
            payload = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Build share code payload is invalid", exception);
        }
        String canonicalPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        byte[] expectedChecksum = digest(payload).substring(0, 16).getBytes(StandardCharsets.US_ASCII);
        if (payload.length > MAX_CODE_BYTES || !parts[2].equals(canonicalPayload)
                || !MessageDigest.isEqual(
                expectedChecksum, parts[1].getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Build share code checksum is invalid");
        }
        try (var input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != 1) {
                throw new IllegalArgumentException("Build share code version is unsupported");
            }
            String definitionDigest = read(input);
            List<ResourceLocation> classes = readIds(input);
            List<ResourceLocation> nodes = readIds(input);
            var abilities = new TreeMap<Integer, ResourceLocation>();
            int abilityCount = boundedCount(input.readInt(), 8);
            for (int index = 0; index < abilityCount; index++) {
                int slot = input.readInt();
                ResourceLocation ability = StableId.parse(read(input));
                if (abilities.putIfAbsent(slot, ability) != null) {
                    throw new IllegalArgumentException("Build share code contains a duplicate ability slot");
                }
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException("Build share code contains trailing data");
            }
            Build build = new Build(definitionDigest, classes, nodes, abilities);
            if (!MessageDigest.isEqual(payload, payload(build))) {
                throw new IllegalArgumentException("Build share code payload is not canonical");
            }
            return build;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Build share code is truncated", exception);
        }
    }

    private static byte[] payload(Build build) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeInt(1);
                write(output, build.definitionDigest());
                writeIds(output, build.classes());
                writeIds(output, build.nodes());
                output.writeInt(build.abilities().size());
                build.abilities().forEach((slot, id) -> {
                    try {
                        output.writeInt(slot);
                        write(output, id.toString());
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
            byte[] result = bytes.toByteArray();
            if (result.length > MAX_CODE_BYTES) {
                throw new IllegalArgumentException("Build share code exceeds capacity");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in memory build encoding failure", exception);
        }
    }

    private static void writeIds(DataOutputStream output, List<ResourceLocation> values) throws IOException {
        output.writeInt(values.size());
        for (ResourceLocation value : values) {
            write(output, value.toString());
        }
    }

    private static List<ResourceLocation> readIds(DataInputStream input) throws IOException {
        int count = boundedCount(input.readInt(), 4096);
        var result = new java.util.ArrayList<ResourceLocation>();
        var seen = new HashSet<ResourceLocation>();
        ResourceLocation previous = null;
        for (int index = 0; index < count; index++) {
            ResourceLocation value = StableId.parse(read(input));
            if (!seen.add(value)) {
                throw new IllegalArgumentException("Build share code contains a duplicate identity");
            }
            if (previous != null && previous.compareNamespaced(value) >= 0) {
                throw new IllegalArgumentException("Build share code identities are not canonical");
            }
            result.add(value);
            previous = value;
        }
        return List.copyOf(result);
    }

    private static int boundedCount(int value, int maximum) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException("Build share code count exceeds capacity");
        }
        return value;
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 4096) {
            throw new IllegalArgumentException("Build share code text exceeds capacity");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String read(DataInputStream input) throws IOException {
        int length = boundedCount(input.readInt(), 4096);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Build share code text is truncated");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String digest(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Build(
            String definitionDigest,
            List<ResourceLocation> classes,
            List<ResourceLocation> nodes,
            Map<Integer, ResourceLocation> abilities
    ) {
        public Build {
            definitionDigest = Objects.requireNonNull(definitionDigest, "definitionDigest");
            if (!DIGEST.matcher(definitionDigest).matches()) {
                throw new IllegalArgumentException("Build definition digest is invalid");
            }
            classes = sorted(classes, 128);
            nodes = sorted(nodes, 4096);
            Objects.requireNonNull(abilities, "abilities");
            var sortedAbilities = new TreeMap<Integer, ResourceLocation>();
            var assignedAbilities = new HashSet<ResourceLocation>();
            abilities.forEach((slot, id) -> {
                ResourceLocation stableId = StableId.requireValid(id);
                if (!assignedAbilities.add(stableId)) {
                    throw new IllegalArgumentException("Build contains a duplicate assigned ability");
                }
                sortedAbilities.put(Objects.requireNonNull(slot, "ability slot"), stableId);
            });
            abilities = Map.copyOf(sortedAbilities);
            if (abilities.size() > 8 || abilities.keySet().stream().anyMatch(slot -> slot < 0 || slot >= 8)) {
                throw new IllegalArgumentException("Build ability slots are invalid");
            }
        }

        private static List<ResourceLocation> sorted(List<ResourceLocation> values, int maximum) {
            Objects.requireNonNull(values, "values");
            if (values.size() > maximum) {
                throw new IllegalArgumentException("Build identity count exceeds capacity");
            }
            var seen = new HashSet<ResourceLocation>();
            var result = values.stream().map(StableId::requireValid).peek(value -> {
                if (!seen.add(value)) {
                    throw new IllegalArgumentException("Build identity list contains duplicates");
                }
            }).sorted(ResourceLocation::compareNamespaced).toList();
            return List.copyOf(result);
        }
    }
}
