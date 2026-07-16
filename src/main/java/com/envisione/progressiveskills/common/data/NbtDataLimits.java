package com.envisione.progressiveskills.common.data;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/** Hard pre-decode traversal, string, collection, depth, and encoded-byte ceilings. */
public final class NbtDataLimits {
    public static final int MAX_ENCODED_BYTES = 1_048_576;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_TAGS = 16_384;
    public static final int MAX_CONTAINER_ENTRIES = 4_096;
    public static final int MAX_STRING_CHARACTERS = 2_048;
    public static final int MAX_KEY_CHARACTERS = 128;
    public static final int MAX_ARRAY_ELEMENTS = 131_072;

    private NbtDataLimits() {
    }

    public static Optional<String> rejection(CompoundTag root) {
        if (root == null) {
            return Optional.of("Root player-data tag is null");
        }
        var budget = new Budget();
        try {
            visit(root, 0, budget);
            countEncodedBytes(root, MAX_ENCODED_BYTES);
            return Optional.empty();
        } catch (LimitException | IOException exception) {
            return Optional.of(exception.getMessage());
        }
    }

    public static String digest(CompoundTag root) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                NbtIo.write(root, output);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to hash in-memory NBT", exception);
        }
    }

    private static void visit(Tag tag, int depth, Budget budget) {
        if (depth > MAX_DEPTH) {
            throw new LimitException("Player data exceeds maximum NBT depth " + MAX_DEPTH);
        }
        budget.tags++;
        if (budget.tags > MAX_TAGS) {
            throw new LimitException("Player data exceeds maximum NBT tag count " + MAX_TAGS);
        }
        if (tag instanceof StringTag string && string.getAsString().length() > MAX_STRING_CHARACTERS) {
            throw new LimitException("Player data contains an oversized string");
        }
        if (tag instanceof CompoundTag compound) {
            if (compound.size() > MAX_CONTAINER_ENTRIES) {
                throw new LimitException("Player data contains an oversized compound");
            }
            for (String key : compound.getAllKeys()) {
                if (key.length() > MAX_KEY_CHARACTERS) {
                    throw new LimitException("Player data contains an oversized compound key");
                }
                Tag child = compound.get(key);
                if (child != null) {
                    visit(child, depth + 1, budget);
                }
            }
        } else if (tag instanceof ListTag list) {
            if (list.size() > MAX_CONTAINER_ENTRIES) {
                throw new LimitException("Player data contains an oversized list");
            }
            for (Tag child : list) {
                visit(child, depth + 1, budget);
            }
        } else if (tag instanceof ByteArrayTag bytes) {
            if (bytes.size() > MAX_ARRAY_ELEMENTS) {
                throw new LimitException("Player data contains an oversized byte array");
            }
        } else if (tag instanceof IntArrayTag integers) {
            if (integers.size() > MAX_ARRAY_ELEMENTS) {
                throw new LimitException("Player data contains an oversized integer array");
            }
        } else if (tag instanceof LongArrayTag longs) {
            if (longs.size() > MAX_ARRAY_ELEMENTS) {
                throw new LimitException("Player data contains an oversized long array");
            }
        } else if (tag instanceof CollectionTag<?> collection && collection.size() > MAX_CONTAINER_ENTRIES) {
            throw new LimitException("Player data contains an oversized collection");
        }
    }

    private static void countEncodedBytes(CompoundTag root, long maximum) throws IOException {
        try (var output = new DataOutputStream(new BoundedCountingOutputStream(maximum))) {
            NbtIo.write(root, output);
        }
    }

    private static final class Budget {
        private int tags;
    }

    private static final class LimitException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private LimitException(String message) {
            super(message);
        }
    }

    private static final class BoundedCountingOutputStream extends OutputStream {
        private final long maximum;
        private long count;

        private BoundedCountingOutputStream(long maximum) {
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            add(1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            add(length);
        }

        private void add(long amount) throws IOException {
            count = Math.addExact(count, amount);
            if (count > maximum) {
                throw new IOException("Player data exceeds maximum encoded size " + maximum + " bytes");
            }
        }
    }
}
