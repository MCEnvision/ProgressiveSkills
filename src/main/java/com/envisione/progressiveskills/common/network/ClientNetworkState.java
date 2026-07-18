package com.envisione.progressiveskills.common.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shared client cache and atomic transfer and delta assembly state. */
public final class ClientNetworkState {
    private static final int MAX_CACHE_ENTRIES = 8;
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final Clock clock;
    private final Map<CacheKey, CacheEntry> definitionCache = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<UUID, IncomingTransfer> incomingTransfers = new LinkedHashMap<>();
    private Optional<NetworkPayloads.ServerHello> hello = Optional.empty();
    private Optional<UUID> expectedPlayerId = Optional.empty();
    private String localServerIdentity = "<disconnected>";
    private Optional<DefinitionProjection> activeDefinitions = Optional.empty();
    private Optional<VisiblePlayerState> visibleState = Optional.empty();
    private Optional<NetworkPayloads.IntentResult> lastIntentResult = Optional.empty();
    private Optional<NetworkPayloads.TreeRefundPreview> treeRefundPreview = Optional.empty();
    private Optional<NetworkPayloads.ClassChangePreview> classChangePreview = Optional.empty();
    private Optional<NetworkPayloads.CarrierMigrationPreview> carrierMigrationPreview = Optional.empty();
    private Optional<String> lastResyncReason = Optional.empty();
    private long nextRequestId;
    private long latestClassPreviewRequestId = -1;
    private long latestCarrierPreviewRequestId = -1;
    private ClientPhase phase = ClientPhase.DISCONNECTED;

    public ClientNetworkState(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static ClientNetworkState systemClock() {
        return new ClientNetworkState(Clock.systemUTC());
    }

    public synchronized NetworkPayloads.ClientHello receiveHello(NetworkPayloads.ServerHello payload) {
        return receiveHello("<unspecified>", payload);
    }

    public synchronized NetworkPayloads.ClientHello receiveHello(
            String connectionIdentity,
            NetworkPayloads.ServerHello payload
    ) {
        return receiveHello(connectionIdentity, Optional.empty(), payload);
    }

    public synchronized NetworkPayloads.ClientHello receiveHello(
            String connectionIdentity,
            UUID playerId,
            NetworkPayloads.ServerHello payload
    ) {
        return receiveHello(connectionIdentity, Optional.of(Objects.requireNonNull(playerId, "playerId")), payload);
    }

    private NetworkPayloads.ClientHello receiveHello(
            String connectionIdentity,
            Optional<UUID> playerId,
            NetworkPayloads.ServerHello payload
    ) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(connectionIdentity, "connectionIdentity");
        expectedPlayerId = Objects.requireNonNull(playerId, "playerId");
        if (payload.protocolVersion() != NetworkLimits.PROTOCOL_VERSION
                || (payload.features() & NetworkLimits.REQUIRED_FEATURES) != NetworkLimits.REQUIRED_FEATURES) {
            throw new IllegalArgumentException("Server uses an incompatible ProgressiveSkills protocol/features set");
        }
        clearAuthoritativeState();
        pruneCache();
        localServerIdentity = connectionIdentity;
        hello = Optional.of(payload);
        CacheEntry cached = definitionCache.get(CacheKey.from(connectionIdentity, payload));
        boolean cacheHit = cached != null && cached.expiresAt().isAfter(Instant.now(clock));
        if (cacheHit) {
            activeDefinitions = Optional.of(cached.projection());
            phase = ClientPhase.WAITING_STATE;
        } else {
            phase = ClientPhase.WAITING_DEFINITIONS;
        }
        return new NetworkPayloads.ClientHello(
                payload.sessionId(), NetworkLimits.PROTOCOL_VERSION,
                NetworkLimits.REQUIRED_FEATURES, cacheHit
        );
    }

    public synchronized void receiveTransferStart(NetworkPayloads.TransferStart payload) {
        requireSession(payload.sessionId());
        if ((payload.kind() == TransferKind.DEFINITIONS && phase != ClientPhase.WAITING_DEFINITIONS)
                || (payload.kind() == TransferKind.FULL_STATE
                && (!canReceiveFullState() || activeDefinitions.isEmpty()))) {
            throw new IllegalArgumentException("Transfer kind is invalid for the current client handshake phase");
        }
        pruneTransfers();
        if (incomingTransfers.size() >= NetworkLimits.MAX_CONCURRENT_TRANSFERS
                && !incomingTransfers.containsKey(payload.transferId())) {
            throw new IllegalArgumentException("Too many concurrent ProgressiveSkills transfers");
        }
        IncomingTransfer existing = incomingTransfers.putIfAbsent(
                payload.transferId(), new IncomingTransfer(payload, Instant.now(clock)));
        if (existing != null && !existing.start().equals(payload)) {
            throw new IllegalArgumentException("Transfer identity was reused with different metadata");
        }
    }

    public synchronized Optional<CustomPacketPayload> receiveTransferChunk(NetworkPayloads.TransferChunk payload) {
        requireSession(payload.sessionId());
        IncomingTransfer transfer = incomingTransfers.get(payload.transferId());
        if (transfer == null) {
            return Optional.of(resync("chunk arrived before its transfer envelope"));
        }
        if (transfer.expired(Instant.now(clock))) {
            incomingTransfers.remove(payload.transferId());
            phase = ClientPhase.RESYNC_REQUIRED;
            return Optional.of(resync("transfer timed out before atomic assembly"));
        }
        try {
            if (!transfer.accept(payload)) {
                return Optional.empty();
            }
            byte[] decoded = transfer.finish();
            incomingTransfers.remove(payload.transferId());
            if (transfer.start().kind() == TransferKind.DEFINITIONS) {
                DefinitionProjection projection = DefinitionProjectionCodec.decode(decoded);
                NetworkPayloads.ServerHello current = hello.orElseThrow();
                if (projection.definitions().size() != current.definitionCount()
                        || !BoundedNetworkCodec.digest(decoded).equals(current.presentationDigest())) {
                    throw new IllegalArgumentException("Definition projection does not match the server hello");
                }
                activeDefinitions = Optional.of(projection);
                putCache(CacheKey.from(localServerIdentity, current), projection);
                phase = ClientPhase.WAITING_STATE;
            } else {
                VisiblePlayerState state = VisibleStateCodec.decode(decoded);
                validateStateGeneration(state);
                if (expectedPlayerId.isPresent() && !expectedPlayerId.orElseThrow().equals(state.playerId())) {
                    throw new IllegalArgumentException("Full state belongs to another player");
                }
                visibleState = Optional.of(state);
                treeRefundPreview = Optional.empty();
                classChangePreview = Optional.empty();
                carrierMigrationPreview = Optional.empty();
                latestClassPreviewRequestId = -1;
                latestCarrierPreviewRequestId = -1;
                phase = ClientPhase.ACTIVE;
            }
            return Optional.of(new NetworkPayloads.TransferAck(
                    payload.sessionId(), payload.transferId(), transfer.start().kind(), transfer.start().digest()));
        } catch (RuntimeException exception) {
            incomingTransfers.remove(payload.transferId());
            phase = transfer.start().kind() == TransferKind.DEFINITIONS
                    ? ClientPhase.WAITING_DEFINITIONS : ClientPhase.RESYNC_REQUIRED;
            return Optional.of(resync(safeReason(exception)));
        }
    }

    public synchronized CustomPacketPayload receiveDelta(NetworkPayloads.StateDeltaPayload payload) {
        requireSession(payload.sessionId());
        if (phase != ClientPhase.ACTIVE || visibleState.isEmpty()) {
            return resync("state delta arrived before a full state snapshot");
        }
        try {
            StateDelta delta = VisibleStateCodec.decodeDelta(payload.encodedDelta());
            VisiblePlayerState current = visibleState.orElseThrow();
            if (delta.newSyncRevision() == current.syncRevision()
                    && delta.resultingStateDigest().equals(VisibleStateCodec.digest(current))) {
                return new NetworkPayloads.StateAck(
                        payload.sessionId(), current.syncRevision(), delta.resultingStateDigest());
            }
            VisiblePlayerState next = current.apply(delta);
            String digest = VisibleStateCodec.digest(next);
            if (!digest.equals(delta.resultingStateDigest())) {
                throw new IllegalArgumentException("state delta result digest mismatch");
            }
            visibleState = Optional.of(next);
            if (next.stateRevision() != current.stateRevision()) {
                treeRefundPreview = Optional.empty();
                classChangePreview = Optional.empty();
                carrierMigrationPreview = Optional.empty();
                latestClassPreviewRequestId = -1;
                latestCarrierPreviewRequestId = -1;
            }
            return new NetworkPayloads.StateAck(payload.sessionId(), next.syncRevision(), digest);
        } catch (RuntimeException exception) {
            phase = ClientPhase.RESYNC_REQUIRED;
            return resync(safeReason(exception));
        }
    }

    public synchronized void receiveIntentResult(NetworkPayloads.IntentResult result) {
        requireSession(result.sessionId());
        lastIntentResult = Optional.of(result);
    }

    public synchronized Optional<NetworkPayloads.ResyncRequest> prepareResync(String reason) {
        if (hello.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(resync(NetworkLimits.requireBoundedText(
                Objects.requireNonNull(reason, "reason"),
                NetworkLimits.MAX_RESYNC_REASON_BYTES,
                "resync reason"
        )));
    }

    public synchronized void receiveTreeRefundPreview(NetworkPayloads.TreeRefundPreview preview) {
        Objects.requireNonNull(preview, "preview");
        requireSession(preview.sessionId());
        if (phase != ClientPhase.ACTIVE || visibleState.isEmpty() || activeDefinitions.isEmpty()) {
            throw new IllegalArgumentException("Tree refund preview arrived before active synchronized state");
        }
        NetworkPayloads.ServerHello currentHello = hello.orElseThrow();
        VisiblePlayerState currentState = visibleState.orElseThrow();
        if (preview.definitionGeneration() != currentHello.definitionGeneration()
                || !preview.semanticDigest().equals(currentHello.semanticDigest())
                || preview.stateRevision() != currentState.stateRevision()
                || preview.requestId() >= nextRequestId
                || !containsTreeNode(activeDefinitions.orElseThrow(), preview.treeId(), preview.nodeId())) {
            throw new IllegalArgumentException("Tree refund preview does not match active synchronized state");
        }
        treeRefundPreview = Optional.of(preview);
    }

    public synchronized void receiveClassChangePreview(NetworkPayloads.ClassChangePreview preview) {
        Objects.requireNonNull(preview, "preview");
        requireSession(preview.sessionId());
        if (phase != ClientPhase.ACTIVE || visibleState.isEmpty() || activeDefinitions.isEmpty()) {
            throw new IllegalArgumentException("Class preview arrived before active synchronized state");
        }
        NetworkPayloads.ServerHello currentHello = hello.orElseThrow();
        VisiblePlayerState currentState = visibleState.orElseThrow();
        if (preview.definitionGeneration() != currentHello.definitionGeneration()
                || !preview.semanticDigest().equals(currentHello.semanticDigest())
                || preview.stateRevision() != currentState.stateRevision()
                || preview.requestId() != latestClassPreviewRequestId
                || !containsClass(activeDefinitions.orElseThrow(), preview.classId())
                || preview.replacementClassId().filter(id ->
                !containsClass(activeDefinitions.orElseThrow(), id)).isPresent()) {
            throw new IllegalArgumentException("Class preview does not match active synchronized state");
        }
        classChangePreview = Optional.of(preview);
    }

    public synchronized void receiveCarrierMigrationPreview(
            NetworkPayloads.CarrierMigrationPreview preview
    ) {
        Objects.requireNonNull(preview, "preview");
        requireSession(preview.sessionId());
        if (phase != ClientPhase.ACTIVE || visibleState.isEmpty()) {
            throw new IllegalArgumentException("Carrier preview arrived before active synchronized state");
        }
        NetworkPayloads.ServerHello currentHello = hello.orElseThrow();
        VisiblePlayerState currentState = visibleState.orElseThrow();
        if (preview.definitionGeneration() != currentHello.definitionGeneration()
                || !preview.semanticDigest().equals(currentHello.semanticDigest())
                || preview.stateRevision() != currentState.stateRevision()
                || preview.requestId() != latestCarrierPreviewRequestId
                || preview.definitionId().isPresent() && currentState.carriers().held()
                .filter(held -> held.definitionId().equals(preview.definitionId().orElseThrow()))
                .isEmpty()) {
            throw new IllegalArgumentException("Carrier preview does not match active synchronized state");
        }
        carrierMigrationPreview = Optional.of(preview);
    }

    public synchronized Optional<NetworkPayloads.Intent> prepareIntent(
            NetworkPayloads.IntentType intentType,
            TreeIntentPayload payload
    ) {
        Objects.requireNonNull(intentType, "intentType");
        Objects.requireNonNull(payload, "payload");
        if (phase != ClientPhase.ACTIVE || hello.isEmpty() || visibleState.isEmpty()) {
            return Optional.empty();
        }
        if (!containsTreeNode(activeDefinitions.orElseThrow(), payload.treeId(), payload.nodeId())) {
            throw new IllegalArgumentException("Tree intent references an unavailable tree node");
        }
        if (nextRequestId == Long.MAX_VALUE) {
            throw new IllegalStateException("Tree intent request sequence is exhausted");
        }
        NetworkPayloads.ServerHello currentHello = hello.orElseThrow();
        VisiblePlayerState currentState = visibleState.orElseThrow();
        long requestId = nextRequestId++;
        if (intentType == NetworkPayloads.IntentType.TREE_REFUND_PREVIEW
                || intentType == NetworkPayloads.IntentType.TREE_REFUND_CONFIRM) {
            treeRefundPreview = Optional.empty();
        }
        return Optional.of(new NetworkPayloads.Intent(
                currentHello.sessionId(), requestId, currentHello.definitionGeneration(),
                currentHello.semanticDigest(), currentState.stateRevision(), intentType,
                payload.encode(intentType)
        ));
    }

    public synchronized Optional<NetworkPayloads.Intent> prepareClassIntent(
            NetworkPayloads.IntentType intentType,
            ClassIntentPayload payload
    ) {
        Objects.requireNonNull(intentType, "intentType");
        Objects.requireNonNull(payload, "payload");
        if (phase != ClientPhase.ACTIVE || hello.isEmpty() || visibleState.isEmpty()
                || activeDefinitions.isEmpty()) {
            return Optional.empty();
        }
        if (!containsClass(activeDefinitions.orElseThrow(), payload.classId())
                || payload.replacementClassId().filter(id ->
                !containsClass(activeDefinitions.orElseThrow(), id)).isPresent()) {
            throw new IllegalArgumentException("Class intent references an unavailable class");
        }
        if (nextRequestId == Long.MAX_VALUE) {
            throw new IllegalStateException("Class intent request sequence is exhausted");
        }
        NetworkPayloads.ServerHello currentHello = hello.orElseThrow();
        VisiblePlayerState currentState = visibleState.orElseThrow();
        long requestId = nextRequestId++;
        boolean preview = intentType == NetworkPayloads.IntentType.CLASS_RESPEC_PREVIEW
                || intentType == NetworkPayloads.IntentType.CLASS_SWAP_PREVIEW;
        classChangePreview = Optional.empty();
        latestClassPreviewRequestId = preview ? requestId : -1;
        return Optional.of(new NetworkPayloads.Intent(
                currentHello.sessionId(), requestId, currentHello.definitionGeneration(),
                currentHello.semanticDigest(), currentState.stateRevision(), intentType,
                payload.encode(intentType)
        ));
    }

    public synchronized Optional<NetworkPayloads.Intent> prepareAbilityIntent(
            NetworkPayloads.IntentType intentType,
            AbilityIntentPayload payload
    ) {
        Objects.requireNonNull(intentType, "intentType");
        Objects.requireNonNull(payload, "payload");
        if (phase != ClientPhase.ACTIVE || hello.isEmpty() || visibleState.isEmpty()
                || activeDefinitions.isEmpty()) {
            return Optional.empty();
        }
        payload.abilityId().ifPresent(abilityId -> {
            if (!containsAbility(activeDefinitions.orElseThrow(), abilityId)) {
                throw new IllegalArgumentException("Ability intent references an unavailable ability");
            }
            if (!visibleState.orElseThrow().abilities().containsKey(abilityId)) {
                throw new IllegalArgumentException("Ability intent references an unowned ability");
            }
        });
        if (intentType == NetworkPayloads.IntentType.ABILITY_ASSIGN
                && payload.abilityId().flatMap(abilityId -> abilityView(
                activeDefinitions.orElseThrow(), abilityId)).filter(
                view -> view.enabled() && view.slotAllowed()).isEmpty()) {
            throw new IllegalArgumentException("Ability cannot be assigned to a fixed slot");
        }
        if (intentType == NetworkPayloads.IntentType.ABILITY_TOGGLE
                && payload.abilityId().flatMap(abilityId -> abilityView(
                activeDefinitions.orElseThrow(), abilityId)).filter(
                view -> view.enabled() && view.kind().equals("toggle")).isEmpty()) {
            throw new IllegalArgumentException("Ability toggle intent requires a toggle ability");
        }
        if (intentType != NetworkPayloads.IntentType.ABILITY_ASSIGN
                && intentType != NetworkPayloads.IntentType.ABILITY_TOGGLE
                && payload.slot().isPresent()
                && !visibleState.orElseThrow().abilitySlots().containsKey(payload.slot().getAsInt())) {
            throw new IllegalArgumentException("Ability intent references an empty slot");
        }
        if (intentType == NetworkPayloads.IntentType.ABILITY_ACTIVATE) {
            ResourceLocation assigned = visibleState.orElseThrow().abilitySlots().get(
                    payload.slot().orElseThrow());
            if (abilityView(activeDefinitions.orElseThrow(), assigned)
                    .filter(view -> view.enabled() && view.kind().equals("active")).isEmpty()) {
                throw new IllegalArgumentException("Ability activation intent requires an active ability");
            }
        }
        if (nextRequestId == Long.MAX_VALUE) {
            throw new IllegalStateException("Ability intent request sequence is exhausted");
        }
        NetworkPayloads.ServerHello currentHello = hello.orElseThrow();
        VisiblePlayerState currentState = visibleState.orElseThrow();
        return Optional.of(new NetworkPayloads.Intent(
                currentHello.sessionId(), nextRequestId++, currentHello.definitionGeneration(),
                currentHello.semanticDigest(), currentState.stateRevision(), intentType,
                payload.encode(intentType)
        ));
    }

    public synchronized Optional<NetworkPayloads.Intent> prepareCarrierIntent(
            NetworkPayloads.IntentType intentType,
            CarrierIntentPayload payload
    ) {
        Objects.requireNonNull(intentType, "intentType");
        Objects.requireNonNull(payload, "payload");
        if (phase != ClientPhase.ACTIVE || hello.isEmpty() || visibleState.isEmpty()) {
            return Optional.empty();
        }
        VisiblePlayerState currentState = visibleState.orElseThrow();
        if (intentType == NetworkPayloads.IntentType.CLAIM_TAKE
                && currentState.carriers().pendingClaims().stream().noneMatch(
                claim -> claim.claimId().equals(payload.claimId().orElseThrow()))) {
            throw new IllegalArgumentException("Carrier claim intent references an unavailable claim");
        }
        if (intentType == NetworkPayloads.IntentType.CLAIM_TAKE_ALL
                && currentState.carriers().pendingClaims().isEmpty()) {
            throw new IllegalArgumentException("Carrier claim list is empty");
        }
        if ((intentType == NetworkPayloads.IntentType.CARRIER_INSPECT
                || intentType == NetworkPayloads.IntentType.CARRIER_MIGRATE_PREVIEW
                || intentType == NetworkPayloads.IntentType.CARRIER_MIGRATE_CONFIRM)
                && currentState.carriers().held().isEmpty()) {
            throw new IllegalArgumentException("Carrier intent requires a held carrier");
        }
        if (intentType == NetworkPayloads.IntentType.CARRIER_MIGRATE_CONFIRM) {
            NetworkPayloads.CarrierMigrationPreview preview = carrierMigrationPreview
                    .filter(NetworkPayloads.CarrierMigrationPreview::allowed)
                    .filter(value -> value.previewDigest().equals(payload.previewDigest().orElseThrow()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Carrier migration confirm requires the current accepted preview"));
            if (preview.stateRevision() != currentState.stateRevision()) {
                throw new IllegalArgumentException("Carrier migration preview is stale");
            }
        }
        if (nextRequestId == Long.MAX_VALUE) {
            throw new IllegalStateException("Carrier intent request sequence is exhausted");
        }
        NetworkPayloads.ServerHello currentHello = hello.orElseThrow();
        long requestId = nextRequestId++;
        if (intentType == NetworkPayloads.IntentType.CARRIER_MIGRATE_PREVIEW) {
            carrierMigrationPreview = Optional.empty();
            latestCarrierPreviewRequestId = requestId;
        } else if (intentType == NetworkPayloads.IntentType.CARRIER_MIGRATE_CONFIRM) {
            carrierMigrationPreview = Optional.empty();
            latestCarrierPreviewRequestId = -1;
        }
        return Optional.of(new NetworkPayloads.Intent(
                currentHello.sessionId(), requestId, currentHello.definitionGeneration(),
                currentHello.semanticDigest(), currentState.stateRevision(), intentType,
                payload.encode(intentType)
        ));
    }

    public synchronized void disconnect() {
        clearAuthoritativeState();
        hello = Optional.empty();
        expectedPlayerId = Optional.empty();
        localServerIdentity = "<disconnected>";
        phase = ClientPhase.DISCONNECTED;
        pruneCache();
    }

    public synchronized void clearDefinitionCache() {
        definitionCache.clear();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                phase,
                hello.map(NetworkPayloads.ServerHello::sessionId),
                activeDefinitions.map(value -> value.definitions().size()).orElse(0),
                activeDefinitions,
                visibleState,
                lastIntentResult,
                treeRefundPreview,
                classChangePreview,
                carrierMigrationPreview,
                lastResyncReason,
                definitionCache.size()
        );
    }

    private void validateStateGeneration(VisiblePlayerState state) {
        NetworkPayloads.ServerHello current = hello.orElseThrow();
        if (state.definitionRevision().generation() != current.definitionGeneration()
                || !state.definitionRevision().semanticDigest().equals(current.semanticDigest())
                || state.presentationRevision() != current.presentationRevision()
                || !state.presentationDigest().equals(current.presentationDigest())) {
            throw new IllegalArgumentException("Full state does not match the negotiated definition generation");
        }
    }

    private NetworkPayloads.ResyncRequest resync(String reason) {
        String bounded = reason.substring(0, Math.min(reason.length(), NetworkLimits.MAX_RESYNC_REASON_BYTES));
        lastResyncReason = Optional.of(bounded);
        return new NetworkPayloads.ResyncRequest(
                hello.orElseThrow().sessionId(),
                bounded
        );
    }

    private void requireSession(UUID sessionId) {
        if (hello.isEmpty() || !hello.orElseThrow().sessionId().equals(sessionId)) {
            throw new IllegalArgumentException("Payload belongs to a stale ProgressiveSkills session");
        }
    }

    private void clearAuthoritativeState() {
        incomingTransfers.clear();
        activeDefinitions = Optional.empty();
        visibleState = Optional.empty();
        lastIntentResult = Optional.empty();
        treeRefundPreview = Optional.empty();
        classChangePreview = Optional.empty();
        carrierMigrationPreview = Optional.empty();
        lastResyncReason = Optional.empty();
        nextRequestId = 0;
        latestClassPreviewRequestId = -1;
        latestCarrierPreviewRequestId = -1;
    }

    private void putCache(CacheKey key, DefinitionProjection projection) {
        definitionCache.put(key, new CacheEntry(projection, Instant.now(clock).plus(CACHE_TTL)));
        while (definitionCache.size() > MAX_CACHE_ENTRIES) {
            definitionCache.remove(definitionCache.keySet().iterator().next());
        }
    }

    private void pruneCache() {
        Instant now = Instant.now(clock);
        definitionCache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private void pruneTransfers() {
        Instant now = Instant.now(clock);
        incomingTransfers.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    private boolean canReceiveFullState() {
        return phase == ClientPhase.WAITING_STATE
                || phase == ClientPhase.ACTIVE
                || phase == ClientPhase.RESYNC_REQUIRED;
    }

    private static String safeReason(RuntimeException exception) {
        String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return reason.isBlank() ? "invalid synchronized state" : reason;
    }

    private static boolean containsTreeNode(
            DefinitionProjection projection,
            net.minecraft.resources.ResourceLocation treeId,
            net.minecraft.resources.ResourceLocation nodeId
    ) {
        return projection.definitions().entrySet().stream()
                .filter(entry -> entry.getKey().id().equals(treeId))
                .map(Map.Entry::getValue)
                .flatMap(entry -> entry.tree().stream())
                .flatMap(tree -> tree.nodes().stream())
                .anyMatch(node -> node.id().equals(nodeId));
    }

    private static boolean containsClass(
            DefinitionProjection projection,
            net.minecraft.resources.ResourceLocation classId
    ) {
        return projection.definitions().entrySet().stream()
                .anyMatch(entry -> entry.getKey().id().equals(classId)
                        && entry.getValue().classDefinition().isPresent());
    }

    private static boolean containsAbility(
            DefinitionProjection projection,
            net.minecraft.resources.ResourceLocation abilityId
    ) {
        return projection.definitions().keySet().stream()
                .anyMatch(key -> key.kind().equals(
                        com.envisione.progressiveskills.common.id.DefinitionKinds.ABILITY)
                        && key.id().equals(abilityId));
    }

    private static Optional<DefinitionProjection.AbilityView> abilityView(
            DefinitionProjection projection,
            net.minecraft.resources.ResourceLocation abilityId
    ) {
        return projection.definitions().entrySet().stream()
                .filter(entry -> entry.getKey().kind().equals(
                        com.envisione.progressiveskills.common.id.DefinitionKinds.ABILITY)
                        && entry.getKey().id().equals(abilityId))
                .map(Map.Entry::getValue)
                .flatMap(entry -> entry.ability().stream())
                .findFirst();
    }

    public enum ClientPhase {
        DISCONNECTED,
        WAITING_DEFINITIONS,
        WAITING_STATE,
        ACTIVE,
        RESYNC_REQUIRED
    }

    public record Snapshot(
            ClientPhase phase,
            Optional<UUID> sessionId,
            int definitionCount,
            Optional<DefinitionProjection> activeDefinitions,
            Optional<VisiblePlayerState> visibleState,
            Optional<NetworkPayloads.IntentResult> lastIntentResult,
            Optional<NetworkPayloads.TreeRefundPreview> treeRefundPreview,
            Optional<NetworkPayloads.ClassChangePreview> classChangePreview,
            Optional<NetworkPayloads.CarrierMigrationPreview> carrierMigrationPreview,
            Optional<String> lastResyncReason,
            int cachedDefinitionSets
    ) {
        public Snapshot {
            Objects.requireNonNull(phase, "phase");
            sessionId = Objects.requireNonNull(sessionId, "sessionId");
            activeDefinitions = Objects.requireNonNull(activeDefinitions, "activeDefinitions");
            visibleState = Objects.requireNonNull(visibleState, "visibleState");
            lastIntentResult = Objects.requireNonNull(lastIntentResult, "lastIntentResult");
            treeRefundPreview = Objects.requireNonNull(treeRefundPreview, "treeRefundPreview");
            classChangePreview = Objects.requireNonNull(classChangePreview, "classChangePreview");
            carrierMigrationPreview = Objects.requireNonNull(
                    carrierMigrationPreview, "carrierMigrationPreview");
            lastResyncReason = Objects.requireNonNull(lastResyncReason, "lastResyncReason");
        }
    }

    private record CacheKey(
            String localServerIdentity,
            UUID serverIdentity,
            int protocolVersion,
            String semanticDigest,
            String presentationDigest
    ) {
        static CacheKey from(String localServerIdentity, NetworkPayloads.ServerHello hello) {
            return new CacheKey(
                    localServerIdentity, hello.serverIdentity(), hello.protocolVersion(),
                    hello.semanticDigest(), hello.presentationDigest());
        }
    }

    private record CacheEntry(DefinitionProjection projection, Instant expiresAt) {
    }
}
