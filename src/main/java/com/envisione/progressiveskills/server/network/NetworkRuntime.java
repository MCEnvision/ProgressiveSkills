package com.envisione.progressiveskills.server.network;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.network.BoundedNetworkCodec;
import com.envisione.progressiveskills.common.network.DefinitionProjection;
import com.envisione.progressiveskills.common.network.DefinitionProjectionCodec;
import com.envisione.progressiveskills.common.network.PsNetworking;
import com.envisione.progressiveskills.common.network.NetworkPayloads;
import com.envisione.progressiveskills.common.network.ServerNetworkSessions;
import com.envisione.progressiveskills.common.network.TreeIntentPayload;
import com.envisione.progressiveskills.common.network.VisiblePlayerState;
import com.envisione.progressiveskills.common.rule.RuleMemoryKeys;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.common.tree.TreeProgression;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.tree.TreeRuntime;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Projects server authority into one bounded, sanitized protocol session per online player. */
@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class NetworkRuntime {
    private static final AtomicReference<CachedDefinitions> CACHED_DEFINITIONS = new AtomicReference<>();
    private static final AtomicReference<MinecraftServer> ACTIVE_SERVER = new AtomicReference<>();

    static {
        PsNetworking.configureServerIntentExecutor(NetworkRuntime::executeTreeIntent);
    }

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
        PsNetworking.configureServerIntentExecutor(ServerNetworkSessions.IntentExecutor.REJECT_TREE_INTENTS);
        CACHED_DEFINITIONS.set(null);
        ACTIVE_SERVER.compareAndSet(event.getServer(), null);
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
        ACTIVE_SERVER.set(player.getServer());
        PsNetworking.configureServerIntentExecutor(NetworkRuntime::executeTreeIntent);
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
        TransactionRuntime.Context transactions = transactionContext.orElseThrow();
        var state = transactions.service().snapshot(player.getUUID());
        boolean progressionReady = transactions.ready(player);
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
                visibleNodeRanks(state, cached.trees()),
                dataView.orphans().size(),
                dataView.operationReceipts().size(),
                !progressionReady
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

    static Map<net.minecraft.resources.ResourceLocation, Integer> visibleNodeRanks(
            ProgressionSnapshot snapshot,
            TreeCatalog trees
    ) {
        var ranks = new java.util.TreeMap<net.minecraft.resources.ResourceLocation, Integer>(
                net.minecraft.resources.ResourceLocation::compareNamespaced
        );
        snapshot.paidCosts().values().forEach(record -> {
            var instance = record.instanceId();
            if (!instance.ownerKind().equals(DefinitionKinds.TREE.id()) || instance.rank() != 1) {
                return;
            }
            trees.node(instance.ownerId(), instance.purchaseId()).ifPresent(node -> {
                if (record.ownerLineage().equals(
                        trees.nodeLineageFingerprint(instance.ownerId(), instance.purchaseId()))) {
                    ranks.put(instance.purchaseId(), 1);
                }
            });
        });
        return Map.copyOf(ranks);
    }

    private static ServerNetworkSessions.IntentExecution executeTreeIntent(
            UUID playerId,
            NetworkPayloads.Intent intent,
            TreeIntentPayload payload
    ) {
        MinecraftServer server = ACTIVE_SERVER.get();
        if (server == null) {
            return ServerNetworkSessions.IntentExecution.invalid("Server progression runtime is unavailable");
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return ServerNetworkSessions.IntentExecution.invalid("Player is no longer online");
        }
        return dispatchTreeIntent(intent, payload, new LiveTreeIntentOperations(player));
    }

    static ServerNetworkSessions.IntentExecution dispatchTreeIntent(
            NetworkPayloads.Intent intent,
            TreeIntentPayload payload,
            TreeIntentOperations operations
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(operations, "operations");
        if (!operations.active()) {
            return ServerNetworkSessions.IntentExecution.invalid("Player progression data is quarantined");
        }
        return switch (intent.intentType()) {
            case TREE_BUY -> mutationExecution(
                    operations.purchase(payload.treeId(), payload.nodeId(), treeIntentKey(intent)),
                    "Tree node purchased"
            );
            case TREE_REFUND_PREVIEW -> refundPreviewExecution(
                    intent, operations.previewRefund(payload.treeId(), payload.nodeId())
            );
            case TREE_REFUND_CONFIRM -> mutationExecution(
                    operations.refund(
                            payload.treeId(),
                            payload.nodeId(),
                            payload.previewDigest().orElseThrow(),
                            treeIntentKey(intent)
                    ),
                    "Tree refund committed"
            );
            case NOOP_TEST -> ServerNetworkSessions.IntentExecution.invalid("No op is handled before dispatch");
        };
    }

    private static ServerNetworkSessions.IntentExecution mutationExecution(
            TreeMutationOutcome outcome,
            String acceptedMessage
    ) {
        if (!outcome.committed()) {
            return ServerNetworkSessions.IntentExecution.invalid(outcome.message());
        }
        return ServerNetworkSessions.IntentExecution.accepted(acceptedMessage);
    }

    private static ServerNetworkSessions.IntentExecution refundPreviewExecution(
            NetworkPayloads.Intent intent,
            TreeProgression.RefundPreview preview
    ) {
        var followup = new NetworkPayloads.TreeRefundPreview(
                intent.sessionId(),
                intent.requestId(),
                intent.definitionGeneration(),
                intent.semanticDigest(),
                intent.stateRevision(),
                preview.treeId(),
                preview.selectedNode(),
                preview.affectedNodes(),
                preview.refundBalances(),
                preview.digest(),
                preview.blockers()
        );
        return ServerNetworkSessions.IntentExecution.accepted("Tree refund preview ready", followup);
    }

    private static IdempotencyKey treeIntentKey(NetworkPayloads.Intent intent) {
        return new IdempotencyKey("phase10/network/" + intent.sessionId() + "/" + intent.requestId());
    }

    interface TreeIntentOperations {
        boolean active();

        TreeMutationOutcome purchase(
                net.minecraft.resources.ResourceLocation treeId,
                net.minecraft.resources.ResourceLocation nodeId,
                IdempotencyKey idempotencyKey
        );

        TreeProgression.RefundPreview previewRefund(
                net.minecraft.resources.ResourceLocation treeId,
                net.minecraft.resources.ResourceLocation nodeId
        );

        TreeMutationOutcome refund(
                net.minecraft.resources.ResourceLocation treeId,
                net.minecraft.resources.ResourceLocation nodeId,
                String previewDigest,
                IdempotencyKey idempotencyKey
        );
    }

    record TreeMutationOutcome(boolean committed, String message) {
        TreeMutationOutcome {
            message = Objects.requireNonNull(message, "message");
        }
    }

    private record LiveTreeIntentOperations(ServerPlayer player) implements TreeIntentOperations {
        private LiveTreeIntentOperations {
            Objects.requireNonNull(player, "player");
        }

        @Override
        public boolean active() {
            return TransactionRuntime.context(player.getServer())
                    .map(context -> context.ready(player))
                    .orElse(false);
        }

        @Override
        public TreeMutationOutcome purchase(
                net.minecraft.resources.ResourceLocation treeId,
                net.minecraft.resources.ResourceLocation nodeId,
                IdempotencyKey idempotencyKey
        ) {
            var transaction = TreeRuntime.purchase(player, treeId, nodeId, idempotencyKey).transaction();
            return new TreeMutationOutcome(transaction.status().committed(), transaction.message());
        }

        @Override
        public TreeProgression.RefundPreview previewRefund(
                net.minecraft.resources.ResourceLocation treeId,
                net.minecraft.resources.ResourceLocation nodeId
        ) {
            return TreeRuntime.previewRefund(player, treeId, nodeId);
        }

        @Override
        public TreeMutationOutcome refund(
                net.minecraft.resources.ResourceLocation treeId,
                net.minecraft.resources.ResourceLocation nodeId,
                String previewDigest,
                IdempotencyKey idempotencyKey
        ) {
            var transaction = TreeRuntime.refund(
                    player, treeId, nodeId, previewDigest, idempotencyKey
            ).transaction();
            return new TreeMutationOutcome(transaction.status().committed(), transaction.message());
        }
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
        SkillCatalog skills = SkillCatalog.from(canonicalIr);
        TreeCatalog trees = TreeCatalog.from(canonicalIr, skills);
        CachedDefinitions replacement = new CachedDefinitions(
                generation,
                semanticDigest,
                definitions,
                BoundedNetworkCodec.digest(DefinitionProjectionCodec.encode(definitions)),
                trees
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
            String presentationDigest,
            TreeCatalog trees
    ) {
    }
}
