package com.envisione.progressiveskills.server.network;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.ability.AbilityCatalog;
import com.envisione.progressiveskills.common.ability.AbilityProgression;
import com.envisione.progressiveskills.common.ability.AbilityState;
import com.envisione.progressiveskills.common.carrier.PsCarrierItems;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.classdef.ClassCatalog;
import com.envisione.progressiveskills.common.classdef.ClassProgression;
import com.envisione.progressiveskills.common.network.BoundedNetworkCodec;
import com.envisione.progressiveskills.common.network.AbilityIntentPayload;
import com.envisione.progressiveskills.common.network.ClassIntentPayload;
import com.envisione.progressiveskills.common.network.CarrierIntentPayload;
import com.envisione.progressiveskills.common.network.CarrierProjection;
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
import com.envisione.progressiveskills.server.ability.AbilityRuntime;
import com.envisione.progressiveskills.server.classruntime.ClassRuntime;
import com.envisione.progressiveskills.server.carrier.CarrierDeliveryService;
import com.envisione.progressiveskills.server.carrier.CarrierStackService;
import com.envisione.progressiveskills.server.hardening.HardeningRuntime;
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
import java.util.Locale;
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
        PsNetworking.configureServerClassIntentExecutor(NetworkRuntime::executeClassIntent);
        PsNetworking.configureServerAbilityIntentExecutor(NetworkRuntime::executeAbilityIntent);
        PsNetworking.configureServerCarrierIntentExecutor(NetworkRuntime::executeCarrierIntent);
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
        PsNetworking.configureServerClassIntentExecutor(
                ServerNetworkSessions.ClassIntentExecutor.REJECT_CLASS_INTENTS);
        PsNetworking.configureServerAbilityIntentExecutor(
                ServerNetworkSessions.AbilityIntentExecutor.REJECT_ABILITY_INTENTS);
        PsNetworking.configureServerCarrierIntentExecutor(
                ServerNetworkSessions.CarrierIntentExecutor.REJECT_CARRIER_INTENTS);
        CACHED_DEFINITIONS.set(null);
        ACTIVE_SERVER.compareAndSet(event.getServer(), null);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        long started = System.nanoTime();
        try {
            if (event.getServer().getTickCount() % 20 != 0) {
                return;
            }
            event.getServer().getPlayerList().getPlayers().forEach(player ->
                    PsNetworking.serverStatus(player.getUUID())
                            .filter(status -> status.phase() == ServerNetworkSessions.ServerPhase.TIMED_OUT)
                            .ifPresent(status -> player.connection.disconnect(Component.literal(
                                    "ProgressiveSkills synchronization timed out. Reconnect to retry safely."))));
        } finally {
            HardeningRuntime.performance().record(
                    "network.tick", 1_000_000L, System.nanoTime() - started);
        }
    }

    public static void begin(ServerPlayer player) {
        ACTIVE_SERVER.set(player.getServer());
        PsNetworking.configureServerIntentExecutor(NetworkRuntime::executeTreeIntent);
        PsNetworking.configureServerClassIntentExecutor(NetworkRuntime::executeClassIntent);
        PsNetworking.configureServerAbilityIntentExecutor(NetworkRuntime::executeAbilityIntent);
        PsNetworking.configureServerCarrierIntentExecutor(NetworkRuntime::executeCarrierIntent);
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
        return HardeningRuntime.performance().measure(
                "network.projection", 2_000_000L, () -> buildProjection(player));
    }

    private static Projection buildProjection(ServerPlayer player) {
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
        long gameTick = player.serverLevel().getGameTime();
        AbilityState abilityState = AbilityProgression.state(cached.abilities(), state, gameTick);
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
                visibleClasses(state, cached.classes()),
                visibleAbilities(abilityState, cached.abilities(), gameTick),
                visibleAbilitySlots(abilityState),
                visibleSelectedAbilitySlot(abilityState),
                carrierProjection(player),
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
            if (!RuleMemoryKeys.isInternal(key) && !AbilityProgression.isInternalBalance(key)) {
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

    static Map<net.minecraft.resources.ResourceLocation, VisiblePlayerState.ClassSelection> visibleClasses(
            ProgressionSnapshot snapshot,
            ClassCatalog classes
    ) {
        var result = new java.util.TreeMap<
                net.minecraft.resources.ResourceLocation,
                VisiblePlayerState.ClassSelection>(
                net.minecraft.resources.ResourceLocation::compareNamespaced);
        var active = ClassProgression.activeClasses(snapshot);
        ClassProgression.selectedClasses(snapshot).forEach(classId -> {
            result.put(classId, new VisiblePlayerState.ClassSelection(
                    Optional.empty(), 0, VisiblePlayerState.Activity.SUSPENDED));
                classes.classDefinition(classId).ifPresent(definition -> result.put(
                        classId,
                        new VisiblePlayerState.ClassSelection(
                                definition.slot(),
                                definition.slotCost(),
                                active.contains(classId)
                                        ? VisiblePlayerState.Activity.ACTIVE
                                        : VisiblePlayerState.Activity.SUSPENDED
                        )
                ));
        });
        return Map.copyOf(result);
    }

    static Map<net.minecraft.resources.ResourceLocation, VisiblePlayerState.AbilityState> visibleAbilities(
            AbilityState state,
            AbilityCatalog abilities,
            long gameTick
    ) {
        var result = new java.util.TreeMap<
                net.minecraft.resources.ResourceLocation,
                VisiblePlayerState.AbilityState>(
                net.minecraft.resources.ResourceLocation::compareNamespaced);
        state.ownedAbilities().forEach(abilityId -> {
            var definition = abilities.ability(abilityId);
            int charges = 0;
            int maximumCharges = 0;
            long cooldown = 0;
            if (definition.isPresent()) {
                var ability = definition.orElseThrow();
                var charge = state.charges().get(abilityId);
                if (charge != null) {
                    charges = charge.current();
                    maximumCharges = charge.maximum();
                }
                cooldown = state.cooldownRemaining(ability.cooldownGroup(), gameTick);
            }
            result.put(abilityId, new VisiblePlayerState.AbilityState(
                    state.toggleOn(abilityId), charges, maximumCharges, cooldown));
        });
        return Map.copyOf(result);
    }

    static Map<Integer, net.minecraft.resources.ResourceLocation> visibleAbilitySlots(
            AbilityState state
    ) {
        var result = new java.util.TreeMap<Integer, net.minecraft.resources.ResourceLocation>();
        state.assignments().forEach((slotId, abilityId) -> {
            if (state.ownedAbilities().contains(abilityId)) {
                result.put(AbilityState.slotIndex(slotId), abilityId);
            }
        });
        return Map.copyOf(result);
    }

    static int visibleSelectedAbilitySlot(AbilityState state) {
        return state.selectedSlot().filter(state.assignments()::containsKey)
                .filter(slot -> state.ownedAbilities().contains(state.assignments().get(slot)))
                .map(AbilityState::slotIndex).orElse(-1);
    }

    static CarrierProjection carrierProjection(ServerPlayer player) {
        var data = player.getData(PsDataAttachments.PLAYER_DATA);
        var claims = data.pendingClaims().stream().map(CarrierProjection::summarize).toList();
        var stack = player.getMainHandItem();
        var kind = PsCarrierItems.kindOf(stack);
        if (kind.isEmpty()) {
            return new CarrierProjection(Optional.empty(), claims);
        }
        CarrierStackService.Inspection inspection = CarrierStackService.inspect(player, stack);
        if (inspection.identity().isEmpty() || inspection.state().isEmpty()) {
            return new CarrierProjection(Optional.empty(), claims);
        }
        var identity = inspection.identity().orElseThrow();
        var state = inspection.state().orElseThrow();
        String status = inspection.resolution().map(value -> value.code().name().toLowerCase(Locale.ROOT))
                .orElse("incomplete");
        var held = new CarrierProjection.HeldCarrier(
                identity.definitionId(),
                kind.orElseThrow(),
                identity.behaviorDigest(),
                state.behaviorVersion(),
                state.charges(),
                state.boundOwner().isPresent(),
                state.boundOwner().filter(player.getUUID()::equals).isPresent(),
                state.migrationMarker().isPresent(),
                status,
                inspection.message()
        );
        return new CarrierProjection(Optional.of(held), claims);
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

    private static ServerNetworkSessions.IntentExecution executeClassIntent(
            UUID playerId,
            NetworkPayloads.Intent intent,
            ClassIntentPayload payload
    ) {
        MinecraftServer server = ACTIVE_SERVER.get();
        if (server == null) {
            return ServerNetworkSessions.IntentExecution.invalid("Server progression runtime is unavailable");
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return ServerNetworkSessions.IntentExecution.invalid("Player is no longer online");
        }
        return dispatchClassIntent(intent, payload, new LiveClassIntentOperations(player));
    }

    private static ServerNetworkSessions.IntentExecution executeAbilityIntent(
            UUID playerId,
            NetworkPayloads.Intent intent,
            AbilityIntentPayload payload
    ) {
        MinecraftServer server = ACTIVE_SERVER.get();
        if (server == null) {
            return ServerNetworkSessions.IntentExecution.invalid("Server progression runtime is unavailable");
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return ServerNetworkSessions.IntentExecution.invalid("Player is no longer online");
        }
        return dispatchAbilityIntent(intent, payload, new LiveAbilityIntentOperations(player));
    }

    private static ServerNetworkSessions.IntentExecution executeCarrierIntent(
            UUID playerId,
            NetworkPayloads.Intent intent,
            CarrierIntentPayload payload
    ) {
        MinecraftServer server = ACTIVE_SERVER.get();
        if (server == null) {
            return ServerNetworkSessions.IntentExecution.invalid("Server progression runtime is unavailable");
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return ServerNetworkSessions.IntentExecution.invalid("Player is no longer online");
        }
        ServerNetworkSessions.IntentExecution execution = dispatchCarrierIntent(
                intent, payload, new LiveCarrierIntentOperations(player));
        if (execution.status() == NetworkPayloads.IntentStatus.ACCEPTED
                && intent.intentType() != NetworkPayloads.IntentType.CARRIER_INSPECT
                && intent.intentType() != NetworkPayloads.IntentType.CARRIER_MIGRATE_PREVIEW) {
            sync(player);
        }
        return execution;
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
            case CLASS_SELECT, CLASS_RESPEC_PREVIEW, CLASS_RESPEC_CONFIRM,
                    CLASS_SWAP_PREVIEW, CLASS_SWAP_CONFIRM ->
                    ServerNetworkSessions.IntentExecution.invalid("Class intent reached the tree dispatcher");
            case ABILITY_ASSIGN, ABILITY_UNASSIGN, ABILITY_SELECT, ABILITY_TOGGLE,
                    ABILITY_ACTIVATE -> ServerNetworkSessions.IntentExecution.invalid(
                    "Ability intent reached the tree dispatcher");
            case CLAIM_TAKE, CLAIM_TAKE_ALL, CARRIER_INSPECT, CARRIER_MIGRATE_PREVIEW,
                    CARRIER_MIGRATE_CONFIRM -> ServerNetworkSessions.IntentExecution.invalid(
                    "Carrier intent reached the tree dispatcher");
        };
    }

    static ServerNetworkSessions.IntentExecution dispatchClassIntent(
            NetworkPayloads.Intent intent,
            ClassIntentPayload payload,
            ClassIntentOperations operations
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(operations, "operations");
        if (!operations.active()) {
            return ServerNetworkSessions.IntentExecution.invalid("Player progression data is quarantined");
        }
        return switch (intent.intentType()) {
            case CLASS_SELECT -> classMutationExecution(
                    operations.select(payload.classId(), classIntentKey(intent)),
                    "Class selected"
            );
            case CLASS_RESPEC_PREVIEW -> classPreviewExecution(
                    intent, operations.previewRespec(payload.classId())
            );
            case CLASS_RESPEC_CONFIRM -> classMutationExecution(
                    operations.respec(
                            payload.classId(), payload.previewDigest().orElseThrow(),
                            classIntentKey(intent)),
                    "Class respec committed"
            );
            case CLASS_SWAP_PREVIEW -> classPreviewExecution(
                    intent, operations.previewSwap(
                            payload.classId(), payload.replacementClassId().orElseThrow())
            );
            case CLASS_SWAP_CONFIRM -> classMutationExecution(
                    operations.swap(
                            payload.classId(), payload.replacementClassId().orElseThrow(),
                            payload.previewDigest().orElseThrow(), classIntentKey(intent)),
                    "Class swap committed"
            );
            case NOOP_TEST, TREE_BUY, TREE_REFUND_PREVIEW, TREE_REFUND_CONFIRM ->
                    ServerNetworkSessions.IntentExecution.invalid("Tree intent reached the class dispatcher");
            case ABILITY_ASSIGN, ABILITY_UNASSIGN, ABILITY_SELECT, ABILITY_TOGGLE,
                    ABILITY_ACTIVATE -> ServerNetworkSessions.IntentExecution.invalid(
                    "Ability intent reached the class dispatcher");
            case CLAIM_TAKE, CLAIM_TAKE_ALL, CARRIER_INSPECT, CARRIER_MIGRATE_PREVIEW,
                    CARRIER_MIGRATE_CONFIRM -> ServerNetworkSessions.IntentExecution.invalid(
                    "Carrier intent reached the class dispatcher");
        };
    }

    static ServerNetworkSessions.IntentExecution dispatchAbilityIntent(
            NetworkPayloads.Intent intent,
            AbilityIntentPayload payload,
            AbilityIntentOperations operations
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(operations, "operations");
        if (!operations.active()) {
            return ServerNetworkSessions.IntentExecution.invalid("Player progression data is quarantined");
        }
        return switch (intent.intentType()) {
            case ABILITY_ASSIGN -> abilityMutationExecution(operations.assign(
                    payload.abilityId().orElseThrow(), payload.slot().orElseThrow(),
                    abilityIntentKey(intent)), "Ability assigned");
            case ABILITY_UNASSIGN -> abilityMutationExecution(operations.unassign(
                    payload.slot().orElseThrow(), abilityIntentKey(intent)), "Ability unassigned");
            case ABILITY_SELECT -> abilityMutationExecution(operations.select(
                    payload.slot().orElseThrow(), abilityIntentKey(intent)), "Ability slot selected");
            case ABILITY_TOGGLE -> abilityMutationExecution(operations.toggle(
                    payload.abilityId().orElseThrow(), abilityIntentKey(intent)), "Ability toggled");
            case ABILITY_ACTIVATE -> abilityMutationExecution(operations.activate(
                    payload.slot().orElseThrow(), abilityIntentKey(intent)), "Ability activated");
            default -> ServerNetworkSessions.IntentExecution.invalid(
                    "Non ability intent reached the ability dispatcher");
        };
    }

    static ServerNetworkSessions.IntentExecution dispatchCarrierIntent(
            NetworkPayloads.Intent intent,
            CarrierIntentPayload payload,
            CarrierIntentOperations operations
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(operations, "operations");
        if (!operations.active()) {
            return ServerNetworkSessions.IntentExecution.invalid("Player progression data is quarantined");
        }
        return switch (intent.intentType()) {
            case CLAIM_TAKE -> carrierMutationExecution(
                    operations.takeClaim(payload.claimId().orElseThrow()));
            case CLAIM_TAKE_ALL -> carrierMutationExecution(operations.takeAllClaims());
            case CARRIER_INSPECT -> carrierMutationExecution(operations.inspect());
            case CARRIER_MIGRATE_PREVIEW -> carrierPreviewExecution(intent, operations.migratePreview());
            case CARRIER_MIGRATE_CONFIRM -> carrierMutationExecution(
                    operations.migrate(payload.previewDigest().orElseThrow()));
            default -> ServerNetworkSessions.IntentExecution.invalid(
                    "Non carrier intent reached the carrier dispatcher");
        };
    }

    private static ServerNetworkSessions.IntentExecution carrierMutationExecution(
            CarrierMutationOutcome outcome
    ) {
        return outcome.accepted()
                ? ServerNetworkSessions.IntentExecution.accepted(outcome.message())
                : ServerNetworkSessions.IntentExecution.invalid(outcome.message());
    }

    private static ServerNetworkSessions.IntentExecution carrierPreviewExecution(
            NetworkPayloads.Intent intent,
            CarrierPreviewOutcome preview
    ) {
        var followup = new NetworkPayloads.CarrierMigrationPreview(
                intent.sessionId(),
                intent.requestId(),
                intent.definitionGeneration(),
                intent.semanticDigest(),
                intent.stateRevision(),
                preview.allowed(),
                preview.definitionId(),
                preview.currentBehaviorVersion(),
                preview.nextBehaviorVersion(),
                preview.currentCharges(),
                preview.nextCharges(),
                preview.digest(),
                preview.message()
        );
        return ServerNetworkSessions.IntentExecution.accepted("Carrier migration preview ready", followup);
    }

    private static ServerNetworkSessions.IntentExecution abilityMutationExecution(
            AbilityMutationOutcome outcome,
            String acceptedMessage
    ) {
        return outcome.accepted()
                ? ServerNetworkSessions.IntentExecution.accepted(
                acceptedMessage + ". " + outcome.message())
                : ServerNetworkSessions.IntentExecution.invalid(outcome.message());
    }

    private static IdempotencyKey abilityIntentKey(NetworkPayloads.Intent intent) {
        return new IdempotencyKey("phase12/network/" + intent.sessionId() + "/" + intent.requestId());
    }

    private static ServerNetworkSessions.IntentExecution classMutationExecution(
            ClassMutationOutcome outcome,
            String acceptedMessage
    ) {
        if (!outcome.committed()) {
            return ServerNetworkSessions.IntentExecution.invalid(outcome.message());
        }
        return ServerNetworkSessions.IntentExecution.accepted(acceptedMessage);
    }

    private static ServerNetworkSessions.IntentExecution classPreviewExecution(
            NetworkPayloads.Intent intent,
            ClassPreviewOutcome preview
    ) {
        var followup = new NetworkPayloads.ClassChangePreview(
                intent.sessionId(),
                intent.requestId(),
                intent.definitionGeneration(),
                intent.semanticDigest(),
                intent.stateRevision(),
                intent.intentType(),
                preview.classId(),
                preview.replacementClassId(),
                preview.affectedClasses(),
                preview.costBalances(),
                preview.digest(),
                preview.blockers()
        );
        return ServerNetworkSessions.IntentExecution.accepted("Class change preview ready", followup);
    }

    private static IdempotencyKey classIntentKey(NetworkPayloads.Intent intent) {
        return new IdempotencyKey("phase11/network/" + intent.sessionId() + "/" + intent.requestId());
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

    interface ClassIntentOperations {
        boolean active();

        ClassMutationOutcome select(
                net.minecraft.resources.ResourceLocation classId,
                IdempotencyKey idempotencyKey
        );

        ClassPreviewOutcome previewRespec(net.minecraft.resources.ResourceLocation classId);

        ClassMutationOutcome respec(
                net.minecraft.resources.ResourceLocation classId,
                String previewDigest,
                IdempotencyKey idempotencyKey
        );

        ClassPreviewOutcome previewSwap(
                net.minecraft.resources.ResourceLocation removedClassId,
                net.minecraft.resources.ResourceLocation replacementClassId
        );

        ClassMutationOutcome swap(
                net.minecraft.resources.ResourceLocation removedClassId,
                net.minecraft.resources.ResourceLocation replacementClassId,
                String previewDigest,
                IdempotencyKey idempotencyKey
        );
    }

    interface AbilityIntentOperations {
        boolean active();

        AbilityMutationOutcome assign(
                net.minecraft.resources.ResourceLocation abilityId,
                int slot,
                IdempotencyKey idempotencyKey
        );

        AbilityMutationOutcome unassign(int slot, IdempotencyKey idempotencyKey);

        AbilityMutationOutcome select(int slot, IdempotencyKey idempotencyKey);

        AbilityMutationOutcome toggle(
                net.minecraft.resources.ResourceLocation abilityId,
                IdempotencyKey idempotencyKey
        );

        AbilityMutationOutcome activate(int slot, IdempotencyKey idempotencyKey);
    }

    interface CarrierIntentOperations {
        boolean active();

        CarrierMutationOutcome takeClaim(UUID claimId);

        CarrierMutationOutcome takeAllClaims();

        CarrierMutationOutcome inspect();

        CarrierPreviewOutcome migratePreview();

        CarrierMutationOutcome migrate(String previewDigest);
    }

    record AbilityMutationOutcome(boolean accepted, String message) {
        AbilityMutationOutcome {
            message = Objects.requireNonNull(message, "message");
        }
    }

    record CarrierMutationOutcome(boolean accepted, String message) {
        CarrierMutationOutcome {
            message = Objects.requireNonNull(message, "message");
        }
    }

    record CarrierPreviewOutcome(
            boolean allowed,
            Optional<net.minecraft.resources.ResourceLocation> definitionId,
            int currentBehaviorVersion,
            int nextBehaviorVersion,
            int currentCharges,
            int nextCharges,
            String digest,
            String message
    ) {
        CarrierPreviewOutcome {
            definitionId = Objects.requireNonNull(definitionId, "definitionId");
            digest = Objects.requireNonNull(digest, "digest");
            message = Objects.requireNonNull(message, "message");
        }
    }

    record ClassMutationOutcome(boolean committed, String message) {
        ClassMutationOutcome {
            message = Objects.requireNonNull(message, "message");
        }
    }

    record ClassPreviewOutcome(
            net.minecraft.resources.ResourceLocation classId,
            Optional<net.minecraft.resources.ResourceLocation> replacementClassId,
            List<net.minecraft.resources.ResourceLocation> affectedClasses,
            Map<net.minecraft.resources.ResourceLocation, Long> costBalances,
            String digest,
            List<String> blockers
    ) {
        ClassPreviewOutcome {
            Objects.requireNonNull(classId, "classId");
            replacementClassId = Objects.requireNonNull(replacementClassId, "replacementClassId");
            affectedClasses = List.copyOf(Objects.requireNonNull(affectedClasses, "affectedClasses"));
            costBalances = Map.copyOf(Objects.requireNonNull(costBalances, "costBalances"));
            digest = Objects.requireNonNull(digest, "digest");
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        }
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

    private record LiveClassIntentOperations(ServerPlayer player) implements ClassIntentOperations {
        private LiveClassIntentOperations {
            Objects.requireNonNull(player, "player");
        }

        @Override
        public boolean active() {
            return TransactionRuntime.context(player.getServer())
                    .map(context -> context.ready(player))
                    .orElse(false);
        }

        @Override
        public ClassMutationOutcome select(
                net.minecraft.resources.ResourceLocation classId,
                IdempotencyKey idempotencyKey
        ) {
            var transaction = ClassRuntime.select(player, classId, idempotencyKey).transaction();
            return new ClassMutationOutcome(transaction.status().committed(), transaction.message());
        }

        @Override
        public ClassPreviewOutcome previewRespec(net.minecraft.resources.ResourceLocation classId) {
            return classPreview(ClassRuntime.previewRespec(player, classId));
        }

        @Override
        public ClassMutationOutcome respec(
                net.minecraft.resources.ResourceLocation classId,
                String previewDigest,
                IdempotencyKey idempotencyKey
        ) {
            var transaction = ClassRuntime.respec(
                    player, classId, previewDigest, idempotencyKey).transaction();
            return new ClassMutationOutcome(transaction.status().committed(), transaction.message());
        }

        @Override
        public ClassPreviewOutcome previewSwap(
                net.minecraft.resources.ResourceLocation removedClassId,
                net.minecraft.resources.ResourceLocation replacementClassId
        ) {
            return classPreview(ClassRuntime.previewSwap(
                    player, removedClassId, replacementClassId));
        }

        @Override
        public ClassMutationOutcome swap(
                net.minecraft.resources.ResourceLocation removedClassId,
                net.minecraft.resources.ResourceLocation replacementClassId,
                String previewDigest,
                IdempotencyKey idempotencyKey
        ) {
            var transaction = ClassRuntime.swap(
                    player, removedClassId, replacementClassId, previewDigest,
                    idempotencyKey).transaction();
            return new ClassMutationOutcome(transaction.status().committed(), transaction.message());
        }
    }

    private record LiveAbilityIntentOperations(ServerPlayer player)
            implements AbilityIntentOperations {
        private LiveAbilityIntentOperations {
            Objects.requireNonNull(player, "player");
        }

        @Override
        public boolean active() {
            return TransactionRuntime.context(player.getServer())
                    .map(context -> context.ready(player))
                    .orElse(false);
        }

        @Override
        public AbilityMutationOutcome assign(
                net.minecraft.resources.ResourceLocation abilityId,
                int slot,
                IdempotencyKey idempotencyKey
        ) {
            var result = AbilityRuntime.assign(player, abilityId, slot, idempotencyKey);
            return new AbilityMutationOutcome(result.accepted(), result.message());
        }

        @Override
        public AbilityMutationOutcome unassign(int slot, IdempotencyKey idempotencyKey) {
            var result = AbilityRuntime.unassign(player, slot, idempotencyKey);
            return new AbilityMutationOutcome(result.accepted(), result.message());
        }

        @Override
        public AbilityMutationOutcome select(int slot, IdempotencyKey idempotencyKey) {
            var result = AbilityRuntime.select(player, slot, idempotencyKey);
            return new AbilityMutationOutcome(result.accepted(), result.message());
        }

        @Override
        public AbilityMutationOutcome toggle(
                net.minecraft.resources.ResourceLocation abilityId,
                IdempotencyKey idempotencyKey
        ) {
            var result = AbilityRuntime.toggle(player, abilityId, idempotencyKey);
            return new AbilityMutationOutcome(result.accepted(), result.message());
        }

        @Override
        public AbilityMutationOutcome activate(int slot, IdempotencyKey idempotencyKey) {
            var result = AbilityRuntime.activate(player, slot, idempotencyKey);
            return new AbilityMutationOutcome(result.accepted(), result.message());
        }
    }

    private record LiveCarrierIntentOperations(ServerPlayer player)
            implements CarrierIntentOperations {
        private LiveCarrierIntentOperations {
            Objects.requireNonNull(player, "player");
        }

        @Override
        public boolean active() {
            return player.getData(PsDataAttachments.PLAYER_DATA).active();
        }

        @Override
        public CarrierMutationOutcome takeClaim(UUID claimId) {
            var result = CarrierDeliveryService.takeClaim(player, claimId);
            return new CarrierMutationOutcome(result.accepted(), result.message());
        }

        @Override
        public CarrierMutationOutcome takeAllClaims() {
            var result = CarrierDeliveryService.takeAllClaims(player);
            return new CarrierMutationOutcome(result.accepted(), result.message());
        }

        @Override
        public CarrierMutationOutcome inspect() {
            var inspection = CarrierStackService.inspect(player, player.getMainHandItem());
            return new CarrierMutationOutcome(
                    inspection.identity().isPresent() && inspection.state().isPresent(),
                    inspection.message()
            );
        }

        @Override
        public CarrierPreviewOutcome migratePreview() {
            var inspection = CarrierStackService.inspect(player, player.getMainHandItem());
            var preview = CarrierStackService.migratePreview(player, player.getMainHandItem());
            return new CarrierPreviewOutcome(
                    preview.allowed(),
                    inspection.identity().map(value -> value.definitionId()),
                    inspection.state().map(value -> value.behaviorVersion()).orElse(0),
                    preview.behavior().map(value -> value.behaviorVersion()).orElse(0),
                    inspection.state().map(value -> value.charges()).orElse(0),
                    preview.state().map(value -> value.charges()).orElse(0),
                    preview.previewDigest(),
                    preview.message()
            );
        }

        @Override
        public CarrierMutationOutcome migrate(String previewDigest) {
            var result = CarrierStackService.migrate(player, player.getMainHandItem(), previewDigest);
            if (result.migrated()) {
                player.getInventory().setChanged();
                player.getData(PsDataAttachments.PLAYER_DATA).markCarrierProjectionChanged();
            }
            return new CarrierMutationOutcome(result.migrated(), result.message());
        }
    }

    private static ClassPreviewOutcome classPreview(ClassProgression.ChangePreview preview) {
        return new ClassPreviewOutcome(
                preview.classId(), preview.replacementClassId(), preview.affectedClasses(),
                preview.costBalances(), preview.digest(), preview.blockers());
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
        SkillCatalog skills = SkillCatalog.from(canonicalIr);
        TreeCatalog trees = TreeCatalog.from(canonicalIr, skills);
        ClassCatalog classes = ClassCatalog.from(canonicalIr, skills, trees);
        AbilityCatalog abilities = AbilityCatalog.from(canonicalIr, skills, classes);
        DefinitionProjection definitions = DefinitionProjection.from(
                canonicalIr, trees, classes, abilities);
        CachedDefinitions replacement = new CachedDefinitions(
                generation,
                semanticDigest,
                definitions,
                BoundedNetworkCodec.digest(DefinitionProjectionCodec.encode(definitions)),
                trees,
                classes,
                abilities
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
            TreeCatalog trees,
            ClassCatalog classes,
            AbilityCatalog abilities
    ) {
    }
}
