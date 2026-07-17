package com.envisione.progressiveskills.common.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerNetworkSessionsTest {
    @Test
    void applicationMismatchAndRepeatedTransferFailureRejectWithinABoundedRetry() {
        var incompatible = ServerNetworkSessions.systemClock();
        VisiblePlayerState initial = NetworkFixtures.state(0, 0, Map.of());
        NetworkPayloads.ServerHello incompatibleHello = incompatible.begin(
                NetworkFixtures.PLAYER, NetworkFixtures.SERVER, 1, NetworkFixtures.SEMANTIC,
                1, NetworkFixtures.definitions(), initial);
        incompatible.handleClientHello(NetworkFixtures.PLAYER, new NetworkPayloads.ClientHello(
                incompatibleHello.sessionId(), NetworkLimits.PROTOCOL_VERSION + 1,
                NetworkLimits.REQUIRED_FEATURES, false));
        assertEquals(ServerNetworkSessions.ServerPhase.REJECTED,
                incompatible.status(NetworkFixtures.PLAYER).orElseThrow().phase());

        var retry = ServerNetworkSessions.systemClock();
        NetworkPayloads.ServerHello retryHello = retry.begin(
                NetworkFixtures.PLAYER, NetworkFixtures.SERVER, 1, NetworkFixtures.SEMANTIC,
                1, NetworkFixtures.definitions(), initial);
        retry.handleClientHello(NetworkFixtures.PLAYER, new NetworkPayloads.ClientHello(
                retryHello.sessionId(), NetworkLimits.PROTOCOL_VERSION,
                NetworkLimits.REQUIRED_FEATURES, false));
        var resync = new NetworkPayloads.ResyncRequest(retryHello.sessionId(), "definition digest mismatch");
        assertTrue(!retry.handleResync(NetworkFixtures.PLAYER, resync).isEmpty());
        assertTrue(retry.handleResync(NetworkFixtures.PLAYER, resync).isEmpty());
        assertEquals(ServerNetworkSessions.ServerPhase.REJECTED,
                retry.status(NetworkFixtures.PLAYER).orElseThrow().phase());
    }

    @Test
    void handshakeCacheDeltaReplayAndStaleIntentFollowOneAuthoritativeSession() {
        var server = new ServerNetworkSessions(Clock.systemUTC());
        var client = ClientNetworkState.systemClock();
        VisiblePlayerState initial = NetworkFixtures.state(0, 0, Map.of());

        NetworkPayloads.ServerHello firstHello = server.begin(
                NetworkFixtures.PLAYER, NetworkFixtures.SERVER, 1, NetworkFixtures.SEMANTIC,
                1, NetworkFixtures.definitions(), initial);
        NetworkPayloads.ClientHello firstReply = client.receiveHello(firstHello);
        assertEquals(false, firstReply.definitionCacheHit());
        completePayloadExchange(server, client, server.handleClientHello(NetworkFixtures.PLAYER, firstReply));
        assertEquals(ServerNetworkSessions.ServerPhase.ACTIVE,
                server.status(NetworkFixtures.PLAYER).orElseThrow().phase());

        var acceptedIntent = new NetworkPayloads.Intent(
                firstHello.sessionId(), 0, 1, NetworkFixtures.SEMANTIC, 0,
                NetworkPayloads.IntentType.NOOP_TEST, "");
        var accepted = assertInstanceOf(NetworkPayloads.IntentResult.class,
                server.handleIntent(NetworkFixtures.PLAYER, acceptedIntent).getFirst());
        var replay = assertInstanceOf(NetworkPayloads.IntentResult.class,
                server.handleIntent(NetworkFixtures.PLAYER, acceptedIntent).getFirst());
        assertEquals(NetworkPayloads.IntentStatus.ACCEPTED, accepted.status());
        assertEquals(accepted, replay);

        VisiblePlayerState changed = NetworkFixtures.state(1, 1, Map.of("example:points", 4L));
        var deltaPayload = assertInstanceOf(NetworkPayloads.StateDeltaPayload.class,
                server.sync(NetworkFixtures.PLAYER, changed).getFirst());
        var deltaAck = assertInstanceOf(NetworkPayloads.StateAck.class, client.receiveDelta(deltaPayload));
        server.handleStateAck(NetworkFixtures.PLAYER, deltaAck);
        assertEquals(changed, client.snapshot().visibleState().orElseThrow());
        assertEquals(1, server.status(NetworkFixtures.PLAYER).orElseThrow().acknowledgedSyncRevision());

        var staleIntent = new NetworkPayloads.Intent(
                firstHello.sessionId(), 1, 1, NetworkFixtures.SEMANTIC, 0,
                NetworkPayloads.IntentType.NOOP_TEST, "");
        List<CustomPacketPayload> staleResponses = server.handleIntent(NetworkFixtures.PLAYER, staleIntent);
        assertEquals(NetworkPayloads.IntentStatus.STALE_STATE,
                assertInstanceOf(NetworkPayloads.IntentResult.class, staleResponses.getFirst()).status());
        completePayloadExchange(server, client, staleResponses.subList(1, staleResponses.size()));
        assertEquals(ServerNetworkSessions.ServerPhase.ACTIVE,
                server.status(NetworkFixtures.PLAYER).orElseThrow().phase());

        NetworkPayloads.ServerHello secondHello = server.begin(
                NetworkFixtures.PLAYER, NetworkFixtures.SERVER, 1, NetworkFixtures.SEMANTIC,
                1, NetworkFixtures.definitions(), changed);
        NetworkPayloads.ClientHello cacheReply = client.receiveHello(secondHello);
        assertTrue(cacheReply.definitionCacheHit());
        List<CustomPacketPayload> cacheHitPayloads = server.handleClientHello(NetworkFixtures.PLAYER, cacheReply);
        assertInstanceOf(NetworkPayloads.TransferStart.class, cacheHitPayloads.getFirst());
        assertEquals(TransferKind.FULL_STATE,
                ((NetworkPayloads.TransferStart) cacheHitPayloads.getFirst()).kind());
        completePayloadExchange(server, client, cacheHitPayloads);
        assertTrue(server.status(NetworkFixtures.PLAYER).orElseThrow().definitionCacheHit());

        var requestTen = intent(secondHello, 10, changed.stateRevision());
        assertEquals(NetworkPayloads.IntentStatus.ACCEPTED,
                result(server.handleIntent(NetworkFixtures.PLAYER, requestTen)).status());
        assertEquals(NetworkPayloads.IntentStatus.TOO_OLD,
                result(server.handleIntent(NetworkFixtures.PLAYER,
                        intent(secondHello, 9, changed.stateRevision()))).status());
        assertEquals(NetworkPayloads.IntentStatus.FUTURE_JUMP,
                result(server.handleIntent(NetworkFixtures.PLAYER,
                        intent(secondHello, 2_000, changed.stateRevision()))).status());
        for (long requestId = 11; requestId <= 27; requestId++) {
            assertEquals(NetworkPayloads.IntentStatus.ACCEPTED,
                    result(server.handleIntent(NetworkFixtures.PLAYER,
                            intent(secondHello, requestId, changed.stateRevision()))).status());
        }
        assertEquals(NetworkPayloads.IntentStatus.RATE_LIMITED,
                result(server.handleIntent(NetworkFixtures.PLAYER,
                        intent(secondHello, 28, changed.stateRevision()))).status());
    }

    @Test
    void validatedTreeExecutorRunsOnceAndReplaysTheFinalPreviewResponse() {
        var server = ServerNetworkSessions.systemClock();
        var client = ClientNetworkState.systemClock();
        DefinitionProjection definitions = NetworkFixtures.treeDefinitions();
        VisiblePlayerState state = NetworkFixtures.state(
                definitions, 0, 0, Map.of(NetworkFixtures.CURRENCY.toString(), 10L),
                Map.of(NetworkFixtures.NODE_ROOT, 1));
        NetworkPayloads.ServerHello hello = server.begin(
                NetworkFixtures.PLAYER, NetworkFixtures.SERVER, 1, NetworkFixtures.SEMANTIC,
                1, definitions, state);
        NetworkPayloads.ClientHello reply = client.receiveHello(hello);
        completePayloadExchange(server, client, server.handleClientHello(NetworkFixtures.PLAYER, reply));
        var calls = new AtomicInteger();
        var request = new NetworkPayloads.Intent(
                hello.sessionId(), 0, 1, NetworkFixtures.SEMANTIC, 0,
                NetworkPayloads.IntentType.TREE_REFUND_PREVIEW,
                TreeIntentPayload.refundPreview(NetworkFixtures.TREE, NetworkFixtures.NODE_ROOT)
                        .encode(NetworkPayloads.IntentType.TREE_REFUND_PREVIEW)
        );
        ServerNetworkSessions.IntentExecutor executor = (playerId, intent, treePayload) -> {
            calls.incrementAndGet();
            var preview = new NetworkPayloads.TreeRefundPreview(
                    intent.sessionId(), intent.requestId(), intent.definitionGeneration(),
                    intent.semanticDigest(), intent.stateRevision(), treePayload.treeId(),
                    treePayload.nodeId(), List.of(NetworkFixtures.NODE_BRANCH, NetworkFixtures.NODE_ROOT),
                    Map.of(NetworkFixtures.CURRENCY, 3L), "d".repeat(64), List.of()
            );
            return ServerNetworkSessions.IntentExecution.accepted("Refund preview ready", preview);
        };

        List<CustomPacketPayload> accepted = server.handleIntent(
                NetworkFixtures.PLAYER, request, executor);
        List<CustomPacketPayload> replayed = server.handleIntent(
                NetworkFixtures.PLAYER, request, executor);
        assertEquals(accepted, replayed);
        assertEquals(1, calls.get());
        assertEquals(NetworkPayloads.IntentStatus.ACCEPTED, result(accepted).status());
        assertEquals(List.of(NetworkFixtures.NODE_BRANCH, NetworkFixtures.NODE_ROOT),
                assertInstanceOf(NetworkPayloads.TreeRefundPreview.class, accepted.get(1)).affectedNodes());

        var malformed = new NetworkPayloads.Intent(
                hello.sessionId(), 1, 1, NetworkFixtures.SEMANTIC, 0,
                NetworkPayloads.IntentType.TREE_BUY, "bad");
        assertEquals(NetworkPayloads.IntentStatus.INVALID,
                result(server.handleIntent(NetworkFixtures.PLAYER, malformed, executor)).status());
        assertEquals(1, calls.get());

        var stale = new NetworkPayloads.Intent(
                hello.sessionId(), 2, 1, NetworkFixtures.SEMANTIC, 99,
                NetworkPayloads.IntentType.TREE_BUY,
                TreeIntentPayload.buy(NetworkFixtures.TREE, NetworkFixtures.NODE_BRANCH)
                        .encode(NetworkPayloads.IntentType.TREE_BUY));
        assertEquals(NetworkPayloads.IntentStatus.STALE_STATE,
                result(server.handleIntent(NetworkFixtures.PLAYER, stale, executor)).status());
        assertEquals(1, calls.get());
    }

    @Test
    void quarantinedStateRejectsTreeIntentBeforeExecutor() {
        var server = ServerNetworkSessions.systemClock();
        var client = ClientNetworkState.systemClock();
        DefinitionProjection definitions = NetworkFixtures.treeDefinitions();
        VisiblePlayerState state = NetworkFixtures.state(
                definitions, 0, 0, Map.of(), Map.of(), true
        );
        NetworkPayloads.ServerHello hello = server.begin(
                NetworkFixtures.PLAYER, NetworkFixtures.SERVER, 1, NetworkFixtures.SEMANTIC,
                1, definitions, state
        );
        completePayloadExchange(server, client, server.handleClientHello(
                NetworkFixtures.PLAYER, client.receiveHello(hello)
        ));
        var calls = new AtomicInteger();
        var request = new NetworkPayloads.Intent(
                hello.sessionId(), 0, 1, NetworkFixtures.SEMANTIC, 0,
                NetworkPayloads.IntentType.TREE_BUY,
                TreeIntentPayload.buy(NetworkFixtures.TREE, NetworkFixtures.NODE_ROOT)
                        .encode(NetworkPayloads.IntentType.TREE_BUY)
        );

        var response = result(server.handleIntent(
                NetworkFixtures.PLAYER,
                request,
                (playerId, intent, payload) -> {
                    calls.incrementAndGet();
                    return ServerNetworkSessions.IntentExecution.accepted("Unexpected execution");
                }
        ));

        assertEquals(NetworkPayloads.IntentStatus.INVALID, response.status());
        assertEquals(0, calls.get());
        assertTrue(response.message().contains("quarantined"));
    }

    private static NetworkPayloads.Intent intent(
            NetworkPayloads.ServerHello hello,
            long requestId,
            long stateRevision
    ) {
        return new NetworkPayloads.Intent(
                hello.sessionId(), requestId, 1, NetworkFixtures.SEMANTIC, stateRevision,
                NetworkPayloads.IntentType.NOOP_TEST, "");
    }

    private static NetworkPayloads.IntentResult result(List<CustomPacketPayload> payloads) {
        return assertInstanceOf(NetworkPayloads.IntentResult.class, payloads.getFirst());
    }

    private static void completePayloadExchange(
            ServerNetworkSessions server,
            ClientNetworkState client,
            List<CustomPacketPayload> initialPayloads
    ) {
        List<CustomPacketPayload> serverPayloads = initialPayloads;
        while (!serverPayloads.isEmpty()) {
            CustomPacketPayload reply = null;
            for (CustomPacketPayload payload : serverPayloads) {
                if (payload instanceof NetworkPayloads.TransferStart start) {
                    client.receiveTransferStart(start);
                } else if (payload instanceof NetworkPayloads.TransferChunk chunk) {
                    var candidate = client.receiveTransferChunk(chunk);
                    if (candidate.isPresent()) {
                        reply = candidate.orElseThrow();
                    }
                }
            }
            if (reply instanceof NetworkPayloads.TransferAck ack) {
                serverPayloads = server.handleTransferAck(NetworkFixtures.PLAYER, ack);
            } else if (reply instanceof NetworkPayloads.ResyncRequest resync) {
                serverPayloads = server.handleResync(NetworkFixtures.PLAYER, resync);
            } else {
                serverPayloads = List.of();
            }
        }
    }
}
