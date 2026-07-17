package com.envisione.progressiveskills.common.network;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** Bounded transfer assembly that accepts out of order chunks and exact duplicates. */
public final class IncomingTransfer {
    private final NetworkPayloads.TransferStart start;
    private final byte[][] chunks;
    private final Instant createdAt;
    private int received;
    private int receivedBytes;

    public IncomingTransfer(NetworkPayloads.TransferStart start) {
        this(start, Instant.now());
    }

    public IncomingTransfer(NetworkPayloads.TransferStart start, Instant createdAt) {
        this.start = Objects.requireNonNull(start, "start");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        chunks = new byte[start.chunkCount()][];
    }

    public synchronized boolean expired(Instant now) {
        return Duration.between(createdAt, Objects.requireNonNull(now, "now")).toMillis()
                > NetworkLimits.SESSION_TIMEOUT_MILLIS;
    }

    public synchronized boolean accept(NetworkPayloads.TransferChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        if (!chunk.sessionId().equals(start.sessionId()) || !chunk.transferId().equals(start.transferId())
                || chunk.index() >= chunks.length) {
            throw new IllegalArgumentException("Chunk does not belong to this transfer");
        }
        byte[] contents = chunk.contents();
        int expectedMaximum = chunk.index() == chunks.length - 1
                ? start.compressedBytes() - (chunk.index() * NetworkLimits.CHUNK_BYTES)
                : NetworkLimits.CHUNK_BYTES;
        if (expectedMaximum < 0 || contents.length != expectedMaximum) {
            throw new IllegalArgumentException("Chunk length does not match its transfer envelope");
        }
        byte[] existing = chunks[chunk.index()];
        if (existing != null) {
            if (!Arrays.equals(existing, contents)) {
                throw new IllegalArgumentException("Conflicting duplicate transfer chunk");
            }
            return complete();
        }
        chunks[chunk.index()] = contents;
        received++;
        receivedBytes = Math.addExact(receivedBytes, contents.length);
        if (receivedBytes > start.compressedBytes()) {
            throw new IllegalArgumentException("Received transfer bytes exceed the declared envelope");
        }
        return complete();
    }

    public synchronized boolean complete() {
        return received == chunks.length;
    }

    public synchronized byte[] finish() {
        if (!complete() || receivedBytes != start.compressedBytes()) {
            throw new IllegalStateException("Transfer is not complete");
        }
        var compressed = new ByteArrayOutputStream(receivedBytes);
        for (byte[] chunk : chunks) {
            compressed.writeBytes(chunk);
        }
        int maximum = start.kind() == TransferKind.DEFINITIONS
                ? NetworkLimits.MAX_DEFINITION_BYTES : NetworkLimits.MAX_STATE_BYTES;
        byte[] result = BoundedNetworkCodec.decompress(
                compressed.toByteArray(), start.uncompressedBytes(), maximum);
        if (!BoundedNetworkCodec.digest(result).equals(start.digest())) {
            throw new IllegalArgumentException("Completed transfer digest does not match its envelope");
        }
        return result;
    }

    public NetworkPayloads.TransferStart start() {
        return start;
    }
}
