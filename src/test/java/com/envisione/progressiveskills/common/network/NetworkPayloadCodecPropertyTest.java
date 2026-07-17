package com.envisione.progressiveskills.common.network;

import io.netty.buffer.Unpooled;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkPayloadCodecPropertyTest {
    private static final List<StreamCodec<FriendlyByteBuf, ? extends CustomPacketPayload>> SERVERBOUND = List.of(
            NetworkPayloads.ClientHello.STREAM_CODEC,
            NetworkPayloads.TransferAck.STREAM_CODEC,
            NetworkPayloads.StateAck.STREAM_CODEC,
            NetworkPayloads.ResyncRequest.STREAM_CODEC,
            NetworkPayloads.Intent.STREAM_CODEC
    );

    @Property(tries = 1000)
    void arbitraryBoundedBytesEitherDecodeSafelyOrFailClosed(@ForAll("packetBytes") byte[] bytes) {
        for (var codec : SERVERBOUND) {
            var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            try {
                codec.decode(buffer);
            } catch (RuntimeException expected) {
                assertTrue(buffer.capacity() <= NetworkLimits.MAX_SERVERBOUND_PAYLOAD_BYTES);
            } finally {
                buffer.release();
            }
        }
    }

    @Provide
    Arbitrary<byte[]> packetBytes() {
        return Arbitraries.bytes().array(byte[].class)
                .ofMaxSize(NetworkLimits.MAX_SERVERBOUND_PAYLOAD_BYTES);
    }
}
