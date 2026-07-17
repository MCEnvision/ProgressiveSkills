package com.envisione.progressiveskills.common.network;

import org.junit.jupiter.api.Test;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientNetworkStateTest {
    @Test
    void reconnectCacheUsesStableSelectedDestinationAndNeverAuthoritativeState() {
        VisiblePlayerState initial = NetworkFixtures.state(0, 0, Map.of());
        var sessions = ServerNetworkSessions.systemClock();
        var client = ClientNetworkState.systemClock();

        NetworkPayloads.ServerHello firstHello = sessions.begin(
                NetworkFixtures.PLAYER, NetworkFixtures.SERVER, 1, NetworkFixtures.SEMANTIC,
                1, NetworkFixtures.definitions(), initial);
        NetworkPayloads.ClientHello firstReply = client.receiveHello(
                "singleplayer", NetworkFixtures.PLAYER, firstHello);
        assertFalse(firstReply.definitionCacheHit());
        cacheDefinitions(client, firstHello);
        client.disconnect();
        assertTrue(client.snapshot().visibleState().isEmpty());

        NetworkPayloads.ServerHello reconnectHello = sessions.begin(
                NetworkFixtures.PLAYER, NetworkFixtures.SERVER, 1, NetworkFixtures.SEMANTIC,
                1, NetworkFixtures.definitions(), initial);
        NetworkPayloads.ClientHello reconnectReply = client.receiveHello(
                "singleplayer", NetworkFixtures.PLAYER, reconnectHello);
        assertTrue(reconnectReply.definitionCacheHit());
        assertEquals(ClientNetworkState.ClientPhase.WAITING_STATE, client.snapshot().phase());
        assertTrue(client.snapshot().visibleState().isEmpty());

        client.disconnect();
        NetworkPayloads.ClientHello otherDestinationReply = client.receiveHello(
                "multiplayer:different", NetworkFixtures.PLAYER, reconnectHello);
        assertFalse(otherDestinationReply.definitionCacheHit());
    }

    @Test
    void fullStateCannotBypassDefinitionsOrTargetAnotherPlayer() {
        VisiblePlayerState initial = NetworkFixtures.state(0, 0, Map.of());
        var sessions = ServerNetworkSessions.systemClock();
        NetworkPayloads.ServerHello hello = sessions.begin(
                NetworkFixtures.PLAYER, NetworkFixtures.SERVER, 1, NetworkFixtures.SEMANTIC,
                1, NetworkFixtures.definitions(), initial);
        var client = ClientNetworkState.systemClock();
        client.receiveHello("local-test", NetworkFixtures.PLAYER, hello);

        PreparedTransfer prematureState = PreparedTransfer.create(
                hello.sessionId(), TransferKind.FULL_STATE, VisibleStateCodec.encode(initial));
        assertThrows(IllegalArgumentException.class,
                () -> client.receiveTransferStart(prematureState.start()));

        PreparedTransfer definitions = PreparedTransfer.create(
                hello.sessionId(), TransferKind.DEFINITIONS,
                DefinitionProjectionCodec.encode(NetworkFixtures.definitions()));
        client.receiveTransferStart(definitions.start());
        definitions.chunks().forEach(client::receiveTransferChunk);

        var foreign = new VisiblePlayerState(
                UUID.randomUUID(), initial.syncRevision(), initial.stateRevision(),
                initial.definitionRevision(), initial.presentationRevision(), initial.presentationDigest(),
                initial.balances(), initial.effectiveValues(), initial.orphanCount(),
                initial.operationReceiptCount(), initial.quarantined());
        PreparedTransfer foreignState = PreparedTransfer.create(
                hello.sessionId(), TransferKind.FULL_STATE, VisibleStateCodec.encode(foreign));
        client.receiveTransferStart(foreignState.start());
        CustomPacketPayload response = null;
        for (var chunk : foreignState.chunks()) {
            response = client.receiveTransferChunk(chunk).orElse(response);
        }
        assertInstanceOf(NetworkPayloads.ResyncRequest.class, response);
        assertEquals(ClientNetworkState.ClientPhase.RESYNC_REQUIRED, client.snapshot().phase());
    }

    @Test
    void activeClientPreparesMonotonicTreeIntentsAndAcceptsOnlyCurrentPreview() {
        DefinitionProjection definitions = NetworkFixtures.treeDefinitions();
        VisiblePlayerState state = NetworkFixtures.state(
                definitions, 0, 7, Map.of(NetworkFixtures.CURRENCY.toString(), 10L),
                Map.of(NetworkFixtures.NODE_ROOT, 1));
        var sessions = ServerNetworkSessions.systemClock();
        NetworkPayloads.ServerHello hello = sessions.begin(
                NetworkFixtures.PLAYER, NetworkFixtures.SERVER, 1, NetworkFixtures.SEMANTIC,
                1, definitions, state);
        var client = ClientNetworkState.systemClock();
        client.receiveHello("tree-test", NetworkFixtures.PLAYER, hello);
        receiveFullClientState(client, hello, definitions, state);

        NetworkPayloads.Intent buy = client.prepareIntent(
                NetworkPayloads.IntentType.TREE_BUY,
                TreeIntentPayload.buy(NetworkFixtures.TREE, NetworkFixtures.NODE_BRANCH)
        ).orElseThrow();
        NetworkPayloads.Intent previewIntent = client.prepareIntent(
                NetworkPayloads.IntentType.TREE_REFUND_PREVIEW,
                TreeIntentPayload.refundPreview(NetworkFixtures.TREE, NetworkFixtures.NODE_ROOT)
        ).orElseThrow();
        assertEquals(0, buy.requestId());
        assertEquals(1, previewIntent.requestId());
        assertEquals(state.stateRevision(), previewIntent.stateRevision());

        var preview = new NetworkPayloads.TreeRefundPreview(
                hello.sessionId(), previewIntent.requestId(), 1, NetworkFixtures.SEMANTIC,
                state.stateRevision(), NetworkFixtures.TREE, NetworkFixtures.NODE_ROOT,
                List.of(NetworkFixtures.NODE_BRANCH, NetworkFixtures.NODE_ROOT),
                Map.of(NetworkFixtures.CURRENCY, 3L), "a".repeat(64), List.of()
        );
        client.receiveTreeRefundPreview(preview);
        assertEquals(preview, client.snapshot().treeRefundPreview().orElseThrow());

        NetworkPayloads.Intent confirm = client.prepareIntent(
                NetworkPayloads.IntentType.TREE_REFUND_CONFIRM,
                TreeIntentPayload.refundConfirm(
                        NetworkFixtures.TREE, NetworkFixtures.NODE_ROOT, preview.previewDigest())
        ).orElseThrow();
        assertEquals(2, confirm.requestId());
        assertTrue(client.snapshot().treeRefundPreview().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> client.receiveTreeRefundPreview(
                new NetworkPayloads.TreeRefundPreview(
                        hello.sessionId(), previewIntent.requestId(), 1, NetworkFixtures.SEMANTIC,
                        state.stateRevision() + 1, NetworkFixtures.TREE, NetworkFixtures.NODE_ROOT,
                        List.of(), Map.of(), "b".repeat(64), List.of()
                )));
    }

    private static void cacheDefinitions(
            ClientNetworkState client,
            NetworkPayloads.ServerHello hello
    ) {
        PreparedTransfer definitions = PreparedTransfer.create(
                hello.sessionId(), TransferKind.DEFINITIONS,
                DefinitionProjectionCodec.encode(NetworkFixtures.definitions()));
        client.receiveTransferStart(definitions.start());
        definitions.chunks().forEach(client::receiveTransferChunk);
    }

    private static void receiveFullClientState(
            ClientNetworkState client,
            NetworkPayloads.ServerHello hello,
            DefinitionProjection definitions,
            VisiblePlayerState state
    ) {
        PreparedTransfer definitionTransfer = PreparedTransfer.create(
                hello.sessionId(), TransferKind.DEFINITIONS,
                DefinitionProjectionCodec.encode(definitions));
        client.receiveTransferStart(definitionTransfer.start());
        definitionTransfer.chunks().forEach(client::receiveTransferChunk);
        PreparedTransfer stateTransfer = PreparedTransfer.create(
                hello.sessionId(), TransferKind.FULL_STATE, VisibleStateCodec.encode(state));
        client.receiveTransferStart(stateTransfer.start());
        stateTransfer.chunks().forEach(client::receiveTransferChunk);
    }
}
