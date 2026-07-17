package com.envisione.progressiveskills.common.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Deterministic binary primitives plus bounded compression and digest verification. */
public final class BoundedNetworkCodec {
    private BoundedNetworkCodec() {
    }

    public static byte[] encode(Writer writer, int maximumBytes, String name) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(new LimitedOutputStream(bytes, maximumBytes, name))) {
                writer.write(output);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to encode " + name + ": " + exception.getMessage(), exception);
        }
    }

    public static DataInputStream input(byte[] encoded, int maximumBytes, String name) {
        if (encoded == null || encoded.length > maximumBytes) {
            throw new IllegalArgumentException(name + " exceeds its encoded ceiling");
        }
        return new DataInputStream(new ByteArrayInputStream(encoded));
    }

    public static void requireFullyRead(DataInputStream input, String name) throws IOException {
        if (input.read() != -1) {
            throw new IOException(name + " contains trailing bytes");
        }
    }

    public static void writeString(DataOutputStream output, String value, int maximumBytes) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes) {
            throw new IOException("String exceeds " + maximumBytes + " bytes");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    public static String readString(DataInputStream input, int maximumBytes) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximumBytes) {
            throw new IOException("String length exceeds " + maximumBytes + " bytes");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Truncated string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static byte[] compress(byte[] uncompressed) {
        if (uncompressed == null || uncompressed.length > NetworkLimits.MAX_DEFINITION_BYTES) {
            throw new IllegalArgumentException("Uncompressed transfer exceeds the absolute ceiling");
        }
        var bytes = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_SPEED, false);
        try (var output = new DeflaterOutputStream(
                new LimitedOutputStream(bytes, NetworkLimits.MAX_COMPRESSED_TRANSFER_BYTES, "compressed transfer"),
                deflater
        )) {
            output.write(uncompressed);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to compress bounded transfer", exception);
        } finally {
            deflater.end();
        }
        return bytes.toByteArray();
    }

    public static byte[] decompress(byte[] compressed, int expectedBytes, int maximumBytes) {
        if (compressed == null || compressed.length > NetworkLimits.MAX_COMPRESSED_TRANSFER_BYTES
                || expectedBytes < 0 || expectedBytes > maximumBytes) {
            throw new IllegalArgumentException("Compressed transfer metadata exceeds a hard ceiling");
        }
        try (var input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            byte[] result = readBounded(input, maximumBytes);
            if (result.length != expectedBytes) {
                throw new IllegalArgumentException("Decompressed transfer length does not match its envelope");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decompress bounded transfer", exception);
        }
    }

    public static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static byte[] readBounded(InputStream input, int maximumBytes) throws IOException {
        var output = new ByteArrayOutputStream(Math.min(maximumBytes, 8_192));
        byte[] buffer = new byte[8_192];
        int total = 0;
        while (true) {
            int read = input.read(buffer);
            if (read < 0) {
                return output.toByteArray();
            }
            total = Math.addExact(total, read);
            if (total > maximumBytes) {
                throw new IOException("Decompressed transfer exceeds " + maximumBytes + " bytes");
            }
            output.write(buffer, 0, read);
        }
    }

    @FunctionalInterface
    public interface Writer {
        void write(DataOutputStream output) throws IOException;
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final int maximum;
        private final String name;
        private int count;

        private LimitedOutputStream(OutputStream delegate, int maximum, String name) {
            this.delegate = delegate;
            this.maximum = maximum;
            this.name = name;
        }

        @Override
        public void write(int value) throws IOException {
            add(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            add(length);
            delegate.write(bytes, offset, length);
        }

        private void add(int amount) throws IOException {
            count = Math.addExact(count, amount);
            if (count > maximum) {
                throw new IOException(name + " exceeds " + maximum + " bytes");
            }
        }
    }
}
