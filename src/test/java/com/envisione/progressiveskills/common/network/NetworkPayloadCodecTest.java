package com.envisione.progressiveskills.common.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkPayloadCodecTest {
    @Test
    void everyServerboundPayloadRoundTripsBelowTheProgressiveSkillsCeiling() {
        UUID session = UUID.randomUUID();
        var transfer = PreparedTransfer.create(session, TransferKind.FULL_STATE, new byte[128]);
        assertRoundTrip(NetworkPayloads.ClientHello.STREAM_CODEC,
                new NetworkPayloads.ClientHello(
                        session, NetworkLimits.PROTOCOL_VERSION, NetworkLimits.REQUIRED_FEATURES, false));
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
    void treeIntentsAreCanonicalAndStrictlyShaped() {
        TreeIntentPayload buy = TreeIntentPayload.buy(NetworkFixtures.TREE, NetworkFixtures.NODE_ROOT);
        String encodedBuy = buy.encode(NetworkPayloads.IntentType.TREE_BUY);
        assertEquals(buy, TreeIntentPayload.decode(NetworkPayloads.IntentType.TREE_BUY, encodedBuy));

        TreeIntentPayload confirm = TreeIntentPayload.refundConfirm(
                NetworkFixtures.TREE, NetworkFixtures.NODE_ROOT, "f".repeat(64));
        String encodedConfirm = confirm.encode(NetworkPayloads.IntentType.TREE_REFUND_CONFIRM);
        assertEquals(confirm, TreeIntentPayload.decode(
                NetworkPayloads.IntentType.TREE_REFUND_CONFIRM, encodedConfirm));
        assertThrows(IllegalArgumentException.class,
                () -> TreeIntentPayload.decode(NetworkPayloads.IntentType.TREE_BUY, encodedConfirm));
        assertThrows(IllegalArgumentException.class,
                () -> TreeIntentPayload.decode(NetworkPayloads.IntentType.TREE_BUY, encodedBuy + "\n"));
        assertThrows(IllegalArgumentException.class,
                () -> buy.encode(NetworkPayloads.IntentType.NOOP_TEST));
    }

    @Test
    void classIntentsAreCanonicalAndStrictlyShaped() {
        ResourceLocation mage = ResourceLocation.parse("example:mage");
        ResourceLocation warrior = ResourceLocation.parse("example:warrior");
        ClassIntentPayload select = ClassIntentPayload.select(mage);
        String encodedSelect = select.encode(NetworkPayloads.IntentType.CLASS_SELECT);
        assertEquals(select, ClassIntentPayload.decode(
                NetworkPayloads.IntentType.CLASS_SELECT, encodedSelect));

        ClassIntentPayload respec = ClassIntentPayload.respecConfirm(mage, "f".repeat(64));
        String encodedRespec = respec.encode(NetworkPayloads.IntentType.CLASS_RESPEC_CONFIRM);
        assertEquals(respec, ClassIntentPayload.decode(
                NetworkPayloads.IntentType.CLASS_RESPEC_CONFIRM, encodedRespec));

        ClassIntentPayload swap = ClassIntentPayload.swapConfirm(
                mage, warrior, "e".repeat(64));
        String encodedSwap = swap.encode(NetworkPayloads.IntentType.CLASS_SWAP_CONFIRM);
        assertEquals(swap, ClassIntentPayload.decode(
                NetworkPayloads.IntentType.CLASS_SWAP_CONFIRM, encodedSwap));
        assertThrows(IllegalArgumentException.class, () -> ClassIntentPayload.decode(
                NetworkPayloads.IntentType.CLASS_SWAP_PREVIEW, encodedSwap));
        assertThrows(IllegalArgumentException.class, () -> select.encode(
                NetworkPayloads.IntentType.CLASS_SWAP_PREVIEW));
        assertThrows(IllegalArgumentException.class, () -> ClassIntentPayload.decode(
                NetworkPayloads.IntentType.CLASS_SELECT, encodedSelect + "\n"));
        assertThrows(IllegalArgumentException.class, () -> ClassIntentPayload.swapPreview(mage, mage));
    }

    @Test
    void abilityIntentsAreCanonicalAndStrictlyShaped() {
        AbilityIntentPayload assign = AbilityIntentPayload.assign(NetworkFixtures.ABILITY_GUARD, 7);
        String encodedAssign = assign.encode(NetworkPayloads.IntentType.ABILITY_ASSIGN);
        assertEquals(assign, AbilityIntentPayload.decode(
                NetworkPayloads.IntentType.ABILITY_ASSIGN, encodedAssign));

        AbilityIntentPayload toggle = AbilityIntentPayload.toggle(NetworkFixtures.ABILITY_FOCUS);
        String encodedToggle = toggle.encode(NetworkPayloads.IntentType.ABILITY_TOGGLE);
        assertEquals(toggle, AbilityIntentPayload.decode(
                NetworkPayloads.IntentType.ABILITY_TOGGLE, encodedToggle));

        AbilityIntentPayload activate = AbilityIntentPayload.activate(0);
        assertEquals(activate, AbilityIntentPayload.decode(
                NetworkPayloads.IntentType.ABILITY_ACTIVATE,
                activate.encode(NetworkPayloads.IntentType.ABILITY_ACTIVATE)));
        assertThrows(IllegalArgumentException.class, () -> assign.encode(
                NetworkPayloads.IntentType.ABILITY_TOGGLE));
        assertThrows(IllegalArgumentException.class, () -> AbilityIntentPayload.assign(
                NetworkFixtures.ABILITY_GUARD, NetworkLimits.FIXED_ABILITY_SLOTS));
        assertThrows(IllegalArgumentException.class, () -> AbilityIntentPayload.decode(
                NetworkPayloads.IntentType.ABILITY_UNASSIGN, "\n01"));
    }

    @Test
    void refundPreviewPreservesCascadeOrderAtTheAcceptedBoundary() {
        UUID session = UUID.randomUUID();
        List<ResourceLocation> affected = IntStream.range(0, NetworkLimits.MAX_TREE_PREVIEW_NODES)
                .mapToObj(index -> ResourceLocation.parse("example:node_" + index)).toList().reversed();
        var balances = new LinkedHashMap<ResourceLocation, Long>();
        var blockers = new java.util.ArrayList<String>();
        for (int index = 0; index < NetworkLimits.MAX_TREE_PREVIEW_BALANCES; index++) {
            balances.put(ResourceLocation.parse("example:currency_" + index), (long) index);
        }
        for (int index = 0; index < NetworkLimits.MAX_TREE_PREVIEW_BLOCKERS; index++) {
            blockers.add("Blocker " + index);
        }
        var preview = new NetworkPayloads.TreeRefundPreview(
                session, 9, 1, NetworkFixtures.SEMANTIC, 3,
                NetworkFixtures.TREE, NetworkFixtures.NODE_ROOT,
                affected, balances, "e".repeat(64), blockers
        );

        var decoded = roundTrip(NetworkPayloads.TreeRefundPreview.STREAM_CODEC, preview);
        assertEquals(affected, decoded.affectedNodes());
        assertEquals(NetworkLimits.MAX_TREE_PREVIEW_BALANCES, decoded.refundBalances().size());
        assertEquals(NetworkLimits.MAX_TREE_PREVIEW_BLOCKERS, decoded.blockers().size());
    }

    @Test
    void classPreviewPreservesAffectedOrderAndBoundedCosts() {
        UUID session = UUID.randomUUID();
        var preview = new NetworkPayloads.ClassChangePreview(
                session, 12, 1, NetworkFixtures.SEMANTIC, 8,
                NetworkPayloads.IntentType.CLASS_SWAP_PREVIEW,
                NetworkFixtures.CLASS_MAGE, Optional.of(NetworkFixtures.CLASS_WARRIOR),
                List.of(NetworkFixtures.CLASS_MAGE, NetworkFixtures.CLASS_WARRIOR),
                Map.of(NetworkFixtures.CURRENCY, 6L), "c".repeat(64), List.of()
        );

        assertEquals(preview, roundTrip(NetworkPayloads.ClassChangePreview.STREAM_CODEC, preview));
        assertTrue(preview.allowed());
        assertThrows(IllegalArgumentException.class, () -> new NetworkPayloads.ClassChangePreview(
                session, 12, 1, NetworkFixtures.SEMANTIC, 8,
                NetworkPayloads.IntentType.CLASS_SELECT,
                NetworkFixtures.CLASS_MAGE, Optional.empty(), List.of(), Map.of(),
                "c".repeat(64), List.of()));
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
        assertEquals(payload, roundTrip(codec, payload));
    }

    private static <T extends CustomPacketPayload> T roundTrip(
            StreamCodec<FriendlyByteBuf, T> codec,
            T payload
    ) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, payload);
            assertTrue(buffer.readableBytes() < 32 * 1_024);
            assertTrue(buffer.readableBytes() <= NetworkLimits.MAX_SERVERBOUND_PAYLOAD_BYTES);
            T decoded = codec.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
