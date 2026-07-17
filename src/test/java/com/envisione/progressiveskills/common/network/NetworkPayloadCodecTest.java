package com.envisione.progressiveskills.common.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkPayloadCodecTest {
    @Test
    void everyServerboundPayloadRoundTripsBelowTheProgressiveSkillsCeiling() {
        UUID session = UUID.randomUUID();
        var transfer = PreparedTransfer.create(session, TransferKind.FULL_STATE, new byte[128]);
        assertRoundTrip(NetworkPayloads.ClientHello.STREAM_CODEC,
                new NetworkPayloads.ClientHello(session, 1, NetworkLimits.REQUIRED_FEATURES, false));
        assertRoundTrip(NetworkPayloads.TransferAck.STREAM_CODEC,
                new NetworkPayloads.TransferAck(session, transfer.start().transferId(),
                        TransferKind.FULL_STATE, transfer.start().digest()));
        assertRoundTrip(NetworkPayloads.StateAck.STREAM_CODEC,
                new NetworkPayloads.StateAck(session, 4, "a".repeat(64)));
        assertRoundTrip(NetworkPayloads.ResyncRequest.STREAM_CODEC,
                new NetworkPayloads.ResyncRequest(session, "continuity gap"));
        assertRoundTrip(NetworkPayloads.Intent.STREAM_CODEC,
                new NetworkPayloads.Intent(session, 7, 1, "b".repeat(64), 4,
                        NetworkPayloads.IntentType.NOOP_TEST, ""));
    }

    @Test
    void unknownEnumsOversizedStringsAndControlTextFailClosed() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(UUID.randomUUID());
            buffer.writeVarLong(0);
            buffer.writeVarLong(1);
            buffer.writeUtf("c".repeat(64), 64);
            buffer.writeVarLong(0);
            buffer.writeVarInt(99);
            assertThrows(IllegalArgumentException.class,
                    () -> NetworkPayloads.Intent.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }

        assertThrows(IllegalArgumentException.class, () -> new NetworkPayloads.Intent(
                UUID.randomUUID(), 0, 1, "d".repeat(64), 0,
                NetworkPayloads.IntentType.NOOP_TEST, "x".repeat(NetworkLimits.MAX_INTENT_BYTES + 1)));
        assertThrows(IllegalArgumentException.class, () -> new NetworkPayloads.ResyncRequest(
                UUID.randomUUID(), "bad\u0000reason"));
    }

    @Test
    void preparedClientboundPayloadsStayUnderConservativePacketSizes() {
        byte[] bytes = new byte[NetworkLimits.MAX_STATE_BYTES];
        new java.util.Random(60).nextBytes(bytes);
        PreparedTransfer transfer = PreparedTransfer.create(
                UUID.randomUUID(), TransferKind.FULL_STATE, bytes);
        assertTrue(transfer.chunks().size() <= NetworkLimits.MAX_CHUNKS);
        transfer.chunks().forEach(chunk -> assertTrue(chunk.contents().length <= NetworkLimits.CHUNK_BYTES));
    }

    private static <T extends CustomPacketPayload> void assertRoundTrip(
            StreamCodec<FriendlyByteBuf, T> codec,
            T payload
    ) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, payload);
            assertTrue(buffer.readableBytes() < 32 * 1_024);
            assertTrue(buffer.readableBytes() <= NetworkLimits.MAX_SERVERBOUND_PAYLOAD_BYTES);
            assertEquals(payload, codec.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
