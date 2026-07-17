package com.envisione.progressiveskills.common.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable outgoing compressed transfer split into conservative clientbound chunks. */
public record PreparedTransfer(
        NetworkPayloads.TransferStart start,
        List<NetworkPayloads.TransferChunk> chunks
) {
    public PreparedTransfer {
        Objects.requireNonNull(start, "start");
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        if (chunks.size() != start.chunkCount()) {
            throw new IllegalArgumentException("Prepared transfer chunk count does not match its start envelope");
        }
    }

    public static PreparedTransfer create(UUID sessionId, TransferKind kind, byte[] uncompressed) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(kind, "kind");
        int maximum = kind == TransferKind.DEFINITIONS
                ? NetworkLimits.MAX_DEFINITION_BYTES : NetworkLimits.MAX_STATE_BYTES;
        if (uncompressed == null || uncompressed.length > maximum) {
            throw new IllegalArgumentException("Outgoing transfer exceeds its uncompressed ceiling");
        }
        byte[] compressed = BoundedNetworkCodec.compress(uncompressed);
        UUID transferId = UUID.randomUUID();
        int count = Math.max(1,
                (compressed.length + NetworkLimits.CHUNK_BYTES - 1) / NetworkLimits.CHUNK_BYTES);
        var start = new NetworkPayloads.TransferStart(
                sessionId,
                transferId,
                kind,
                uncompressed.length,
                compressed.length,
                count,
                BoundedNetworkCodec.digest(uncompressed)
        );
        var chunks = new ArrayList<NetworkPayloads.TransferChunk>(count);
        for (int index = 0; index < count; index++) {
            int from = index * NetworkLimits.CHUNK_BYTES;
            int to = Math.min(compressed.length, from + NetworkLimits.CHUNK_BYTES);
            chunks.add(new NetworkPayloads.TransferChunk(
                    sessionId,
                    transferId,
                    index,
                    Arrays.copyOfRange(compressed, from, to)
            ));
        }
        return new PreparedTransfer(start, chunks);
    }

    public List<CustomPacketPayload> payloads() {
        var payloads = new ArrayList<CustomPacketPayload>(chunks.size() + 1);
        payloads.add(start);
        payloads.addAll(chunks);
        return List.copyOf(payloads);
    }
}
