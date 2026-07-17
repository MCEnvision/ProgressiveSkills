package com.envisione.progressiveskills.server.network;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.network.BoundedNetworkCodec;
import com.envisione.progressiveskills.common.network.DefinitionProjection;
import com.envisione.progressiveskills.common.network.DefinitionProjectionCodec;
import com.envisione.progressiveskills.common.network.PsNetworking;
import com.envisione.progressiveskills.common.network.NetworkPayloads;
import com.envisione.progressiveskills.common.network.ServerNetworkSessions;
import com.envisione.progressiveskills.common.network.VisiblePlayerState;
import com.envisione.progressiveskills.common.rule.RuleMemoryKeys;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Projects server authority into one bounded, sanitized protocol session per online player. */
@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class NetworkRuntime {
    private static final AtomicReference<CachedDefinitions> CACHED_DEFINITIONS = new AtomicReference<>();

    private NetworkRuntime() {
    }

    @SubscribeEvent
    static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            begin(player);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PsNetworking.removeServerSession(player.getUUID());
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        PsNetworking.clearServerSessions();
        CACHED_DEFINITIONS.set(null);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        event.getServer().getPlayerList().getPlayers().forEach(player ->
                PsNetworking.serverStatus(player.getUUID())
                        .filter(status -> status.phase() == ServerNetworkSessions.ServerPhase.TIMED_OUT)
                        .ifPresent(status -> player.connection.disconnect(Component.literal(
                                "ProgressiveSkills synchronization timed out; reconnect to retry safely."))));
    }

    public static void begin(ServerPlayer player) {
        if (!player.connection.hasChannel(NetworkPayloads.ServerHello.TYPE)) {
            PsNetworking.removeServerSession(player.getUUID());
            return;
        }
        Projection projection = projection(player);
        if (projection == null) {
            PsNetworking.removeServerSession(player.getUUID());
            return;
        }
        var hello = PsNetworking.beginServerSession(
                player.getUUID(),
                NetworkIdentitySavedData.get(player.getServer()).identity(),
                projection.definition().generation(),
                projection.definition().semanticDigest(),
                projection.presentationRevision(),
                projection.definitions(),
                projection.visibleState()
        );
        PacketDistributor.sendToPlayer(player, hello);
    }

    public static void sync(ServerPlayer player) {
        Projection projection = projection(player);
        if (projection == null) {
            return;
        }
        Optional<ServerNetworkSessions.Status> status = PsNetworking.serverStatus(player.getUUID());
        if (status.isEmpty()) {
            return;
        }
        if (status.orElseThrow().definitionGeneration() != projection.definition().generation()
                || !status.orElseThrow().semanticDigest().equals(projection.definition().semanticDigest())
                || !status.orElseThrow().presentationDigest().equals(projection.presentationDigest())) {
            begin(player);
            return;
        }
        sendAll(player, PsNetworking.syncServerState(player.getUUID(), projection.visibleState()));
        PsNetworking.serverStatus(player.getUUID())
                .filter(value -> value.phase() == ServerNetworkSessions.ServerPhase.REJECTED)
                .ifPresent(value -> begin(player));
    }

    public static void refreshAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(NetworkRuntime::begin);
    }

    public static Optional<ServerNetworkSessions.Status> status(ServerPlayer player) {
        return PsNetworking.serverStatus(player.getUUID());
    }

    public static void invalidate(ServerPlayer player) {
        PsNetworking.removeServerSession(player.getUUID());
    }

    private static Projection projection(ServerPlayer player) {
        var packService = PackRuntime.service();
        var transactionContext = TransactionRuntime.context(player.getServer());
        if (packService.isEmpty() || transactionContext.isEmpty()) {
            return null;
        }
        var live = packService.orElseThrow().live();
        if (live.generation() < 1) {
            return null;
        }
        DefinitionRevision definition = new DefinitionRevision(
                live.generation(), live.snapshot().contentDigest());
        CachedDefinitions cached = cachedDefinitions(
                live.generation(), live.snapshot().contentDigest(), live.snapshot().canonicalIr());
        DefinitionProjection definitions = cached.definitions();
        String presentationDigest = cached.presentationDigest();

        var data = player.getData(PsDataAttachments.PLAYER_DATA);
        var dataView = data.view();
        var state = transactionContext.orElseThrow().service().snapshot(player.getUUID());
        Map<String, Long> balances = visibleBalances(state.balances());
        Map<String, Long> effective = new LinkedHashMap<>();
        state.projectedValues().forEach((key, value) -> effective.put(key.toString(), value));
        var visible = new VisiblePlayerState(
                player.getUUID(),
                dataView.storageRevision(),
                state.stateRevision(),
                definition,
                live.generation(),
                presentationDigest,
                balances,
                effective,
                dataView.orphans().size(),
                dataView.operationReceipts().size(),
                !data.active()
        );
        return new Projection(definition, live.generation(), presentationDigest, definitions, visible);
    }

    static Map<String, Long> visibleBalances(
            Map<net.minecraft.resources.ResourceLocation, Long> authoritative
    ) {
        Map<String, Long> balances = new LinkedHashMap<>();
        authoritative.forEach((key, value) -> {
            if (!RuleMemoryKeys.isInternal(key)) {
                balances.put(key.toString(), value);
            }
        });
        return balances;
    }

    private static void sendAll(ServerPlayer player, List<CustomPacketPayload> payloads) {
        payloads.forEach(payload -> PacketDistributor.sendToPlayer(player, payload));
    }

    private static CachedDefinitions cachedDefinitions(
            long generation,
            String semanticDigest,
            com.envisione.progressiveskills.common.ir.CanonicalIr canonicalIr
    ) {
        CachedDefinitions current = CACHED_DEFINITIONS.get();
        if (current != null && current.generation() == generation
                && current.semanticDigest().equals(semanticDigest)) {
            return current;
        }
        DefinitionProjection definitions = DefinitionProjection.from(canonicalIr);
        CachedDefinitions replacement = new CachedDefinitions(
                generation,
                semanticDigest,
                definitions,
                BoundedNetworkCodec.digest(DefinitionProjectionCodec.encode(definitions))
        );
        CACHED_DEFINITIONS.set(replacement);
        return replacement;
    }

    private record Projection(
            DefinitionRevision definition,
            long presentationRevision,
            String presentationDigest,
            DefinitionProjection definitions,
            VisiblePlayerState visibleState
    ) {
    }

    private record CachedDefinitions(
            long generation,
            String semanticDigest,
            DefinitionProjection definitions,
            String presentationDigest
    ) {
    }
}
