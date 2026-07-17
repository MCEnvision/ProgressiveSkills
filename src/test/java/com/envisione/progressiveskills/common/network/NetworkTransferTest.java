package com.envisione.progressiveskills.common.network;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkTransferTest {
    @Test
    void boundedChunksAssembleAtomicallyOutOfOrderAndAllowExactDuplicates() {
        byte[] content = new byte[180_000];
        new java.util.Random(6).nextBytes(content);
        PreparedTransfer outgoing = PreparedTransfer.create(
                UUID.randomUUID(), TransferKind.DEFINITIONS, content);
        var incoming = new IncomingTransfer(outgoing.start(), Instant.EPOCH);

        var chunks = outgoing.chunks();
        for (int index = chunks.size() - 1; index >= 0; index--) {
            boolean complete = incoming.accept(chunks.get(index));
            if (index == chunks.size() - 1) {
                assertFalse(complete);
                assertFalse(incoming.accept(chunks.get(index)));
            }
        }

        assertTrue(incoming.complete());
        assertArrayEquals(content, incoming.finish());
    }

    @Test
    void conflictingDuplicateDigestMismatchAndTimeoutFailClosed() {
        PreparedTransfer outgoing = PreparedTransfer.create(
                UUID.randomUUID(), TransferKind.FULL_STATE, new byte[64_000]);
        var incoming = new IncomingTransfer(outgoing.start(), Instant.EPOCH);
        NetworkPayloads.TransferChunk first = outgoing.chunks().getFirst();
        incoming.accept(first);
        byte[] changed = first.contents();
        changed[0] ^= 1;
        var conflict = new NetworkPayloads.TransferChunk(
                first.sessionId(), first.transferId(), first.index(), changed);
        assertThrows(IllegalArgumentException.class, () -> incoming.accept(conflict));
        assertTrue(incoming.expired(Instant.EPOCH.plusMillis(NetworkLimits.SESSION_TIMEOUT_MILLIS + 1)));

        byte[] corrupt = outgoing.chunks().getLast().contents();
        corrupt[corrupt.length - 1] ^= 1;
        var corruptedChunks = new java.util.ArrayList<>(outgoing.chunks());
        var last = outgoing.chunks().getLast();
        corruptedChunks.set(corruptedChunks.size() - 1, new NetworkPayloads.TransferChunk(
                last.sessionId(), last.transferId(), last.index(), corrupt));
        var digestMismatch = new IncomingTransfer(outgoing.start());
        corruptedChunks.forEach(digestMismatch::accept);
        assertThrows(IllegalArgumentException.class, digestMismatch::finish);

        assertThrows(IllegalArgumentException.class, () -> new NetworkPayloads.TransferStart(
                UUID.randomUUID(), UUID.randomUUID(), TransferKind.DEFINITIONS,
                NetworkLimits.MAX_DEFINITION_BYTES + 1, 1, 1, "0".repeat(64)));
        byte[] compressionBomb = BoundedNetworkCodec.compress(new byte[2_048]);
        assertThrows(IllegalArgumentException.class,
                () -> BoundedNetworkCodec.decompress(compressionBomb, 64, 64));
        assertFalse(Arrays.equals(first.contents(), changed));
    }
}
