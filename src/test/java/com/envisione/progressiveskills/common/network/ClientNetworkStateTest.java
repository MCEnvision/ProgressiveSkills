package com.envisione.progressiveskills.common.network;

import org.junit.jupiter.api.Test;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Map;
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
}
