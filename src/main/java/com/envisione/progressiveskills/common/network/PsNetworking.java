package com.envisione.progressiveskills.common.network;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Registers the closed ProgressiveSkills protocol and owns shared connection state. */
public final class PsNetworking {
    private static final ClientNetworkState CLIENT = ClientNetworkState.systemClock();
    private static final ServerNetworkSessions SERVER = ServerNetworkSessions.systemClock();
    private static volatile Supplier<String> clientConnectionIdentity = () -> "";

    private PsNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(NetworkLimits.REGISTRAR_VERSION);

        registrar.playToClient(NetworkPayloads.ServerHello.TYPE,
                        NetworkPayloads.ServerHello.STREAM_CODEC, PsNetworking::receiveServerHello)
                .playToClient(NetworkPayloads.TransferStart.TYPE,
                        NetworkPayloads.TransferStart.STREAM_CODEC, PsNetworking::receiveTransferStart)
                .playToClient(NetworkPayloads.TransferChunk.TYPE,
                        NetworkPayloads.TransferChunk.STREAM_CODEC, PsNetworking::receiveTransferChunk)
                .playToClient(NetworkPayloads.StateDeltaPayload.TYPE,
                        NetworkPayloads.StateDeltaPayload.STREAM_CODEC, PsNetworking::receiveStateDelta)
                .playToClient(NetworkPayloads.IntentResult.TYPE,
                        NetworkPayloads.IntentResult.STREAM_CODEC, PsNetworking::receiveIntentResult)
                .playToServer(NetworkPayloads.ClientHello.TYPE,
                        NetworkPayloads.ClientHello.STREAM_CODEC, PsNetworking::receiveClientHello)
                .playToServer(NetworkPayloads.TransferAck.TYPE,
                        NetworkPayloads.TransferAck.STREAM_CODEC, PsNetworking::receiveTransferAck)
                .playToServer(NetworkPayloads.StateAck.TYPE,
                        NetworkPayloads.StateAck.STREAM_CODEC, PsNetworking::receiveStateAck)
                .playToServer(NetworkPayloads.ResyncRequest.TYPE,
                        NetworkPayloads.ResyncRequest.STREAM_CODEC, PsNetworking::receiveResyncRequest)
                .playToServer(NetworkPayloads.Intent.TYPE,
                        NetworkPayloads.Intent.STREAM_CODEC, PsNetworking::receiveIntent);
    }

    public static NetworkPayloads.ServerHello beginServerSession(
            UUID playerId,
            UUID serverIdentity,
            long definitionGeneration,
            String semanticDigest,
            long presentationRevision,
            DefinitionProjection definitions,
            VisiblePlayerState state
    ) {
        return SERVER.begin(playerId, serverIdentity, definitionGeneration, semanticDigest,
                presentationRevision, definitions, state);
    }

    public static List<CustomPacketPayload> syncServerState(UUID playerId, VisiblePlayerState state) {
        return SERVER.sync(playerId, state);
    }

    public static Optional<ServerNetworkSessions.Status> serverStatus(UUID playerId) {
        return SERVER.status(playerId);
    }

    public static void removeServerSession(UUID playerId) {
        SERVER.remove(playerId);
    }

    public static void clearServerSessions() {
        SERVER.clear();
    }

    public static ClientNetworkState.Snapshot clientSnapshot() {
        return CLIENT.snapshot();
    }

    public static void clientDisconnect() {
        CLIENT.disconnect();
    }

    public static void clearClientDefinitionCache() {
        CLIENT.clearDefinitionCache();
    }

    /** Installs a client resolver for the selected world destination. */
    public static void configureClientConnectionIdentity(Supplier<String> resolver) {
        clientConnectionIdentity = Objects.requireNonNull(resolver, "resolver");
    }

    private static void receiveServerHello(NetworkPayloads.ServerHello payload, IPayloadContext context) {
        clientHandle(context, () -> context.reply(CLIENT.receiveHello(
                resolveClientConnectionIdentity(String.valueOf(context.connection().getRemoteAddress())),
                context.player().getUUID(), payload)));
    }

    private static String resolveClientConnectionIdentity(String transportFallback) {
        try {
            String selectedDestination = clientConnectionIdentity.get();
            if (selectedDestination != null && !selectedDestination.isBlank()) {
                return selectedDestination;
            }
        } catch (RuntimeException ignored) {
            // The transport address remains a safe cache isolation fallback during client teardown races.
        }
        return transportFallback;
    }

    private static void receiveTransferStart(NetworkPayloads.TransferStart payload, IPayloadContext context) {
        clientHandle(context, () -> CLIENT.receiveTransferStart(payload));
    }

    private static void receiveTransferChunk(NetworkPayloads.TransferChunk payload, IPayloadContext context) {
        clientHandle(context, () -> CLIENT.receiveTransferChunk(payload).ifPresent(context::reply));
    }

    private static void receiveStateDelta(NetworkPayloads.StateDeltaPayload payload, IPayloadContext context) {
        clientHandle(context, () -> context.reply(CLIENT.receiveDelta(payload)));
    }

    private static void receiveIntentResult(NetworkPayloads.IntentResult payload, IPayloadContext context) {
        clientHandle(context, () -> CLIENT.receiveIntentResult(payload));
    }

    private static void receiveClientHello(NetworkPayloads.ClientHello payload, IPayloadContext context) {
        serverHandle(context, () -> {
            replyAll(context, SERVER.handleClientHello(context.player().getUUID(), payload));
            disconnectIfRejected(context);
        });
    }

    private static void receiveTransferAck(NetworkPayloads.TransferAck payload, IPayloadContext context) {
        serverHandle(context, () -> replyAll(context,
                SERVER.handleTransferAck(context.player().getUUID(), payload)));
    }

    private static void receiveStateAck(NetworkPayloads.StateAck payload, IPayloadContext context) {
        serverHandle(context, () -> SERVER.handleStateAck(context.player().getUUID(), payload));
    }

    private static void receiveResyncRequest(NetworkPayloads.ResyncRequest payload, IPayloadContext context) {
        serverHandle(context, () -> {
            replyAll(context, SERVER.handleResync(context.player().getUUID(), payload));
            disconnectIfRejected(context);
        });
    }

    private static void receiveIntent(NetworkPayloads.Intent payload, IPayloadContext context) {
        serverHandle(context, () -> replyAll(context,
                SERVER.handleIntent(context.player().getUUID(), payload)));
    }

    private static void replyAll(IPayloadContext context, List<CustomPacketPayload> payloads) {
        payloads.forEach(context::reply);
    }

    private static void clientHandle(IPayloadContext context, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            CLIENT.disconnect();
            context.disconnect(Component.literal(
                    "ProgressiveSkills rejected invalid synchronized data: " + safeMessage(exception)));
        }
    }

    private static void serverHandle(IPayloadContext context, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            context.disconnect(Component.literal(
                    "ProgressiveSkills rejected an invalid client request: " + safeMessage(exception)));
        }
    }

    private static void disconnectIfRejected(IPayloadContext context) {
        SERVER.status(context.player().getUUID())
                .filter(status -> status.phase() == ServerNetworkSessions.ServerPhase.REJECTED)
                .ifPresent(status -> context.disconnect(Component.literal(
                        "ProgressiveSkills synchronization was rejected after a bounded retry; reconnect safely.")));
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), NetworkLimits.MAX_RESYNC_REASON_BYTES));
    }
}
