package com.envisione.progressiveskills.common.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Bounded connection handshake, replay window, transfer, and delta state. */
public final class ServerNetworkSessions {
    private static final double INTENT_BUCKET_CAPACITY = 20.0;
    private static final double INTENT_REFILL_PER_SECOND = 10.0;

    private final Clock clock;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();

    public ServerNetworkSessions(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static ServerNetworkSessions systemClock() {
        return new ServerNetworkSessions(Clock.systemUTC());
    }

    public synchronized NetworkPayloads.ServerHello begin(
            UUID playerId,
            UUID serverIdentity,
            long definitionGeneration,
            String semanticDigest,
            long presentationRevision,
            DefinitionProjection definitions,
            VisiblePlayerState state
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(state, "state");
        if (!state.playerId().equals(playerId)) {
            throw new IllegalArgumentException("Network state owner does not match the session player");
        }
        byte[] definitionBytes = DefinitionProjectionCodec.encode(definitions);
        String presentationDigest = BoundedNetworkCodec.digest(definitionBytes);
        if (state.definitionRevision().generation() != definitionGeneration
                || !state.definitionRevision().semanticDigest().equals(semanticDigest)
                || state.presentationRevision() != presentationRevision
                || !state.presentationDigest().equals(presentationDigest)) {
            throw new IllegalArgumentException("Initial visible state does not match the definition handshake");
        }
        UUID sessionId = UUID.randomUUID();
        var hello = new NetworkPayloads.ServerHello(
                serverIdentity,
                sessionId,
                NetworkLimits.PROTOCOL_VERSION,
                NetworkLimits.REQUIRED_FEATURES,
                definitionGeneration,
                semanticDigest,
                presentationRevision,
                presentationDigest,
                definitions.definitions().size()
        );
        sessions.put(playerId, new Session(
                hello,
                definitionBytes,
                state,
                Instant.now(clock)
        ));
        return hello;
    }

    public synchronized List<CustomPacketPayload> handleClientHello(
            UUID playerId,
            NetworkPayloads.ClientHello payload
    ) {
        Session session = require(playerId);
        if (!payload.sessionId().equals(session.hello.sessionId())) {
            return List.of();
        }
        if (payload.protocolVersion() != NetworkLimits.PROTOCOL_VERSION
                || (payload.features() & NetworkLimits.REQUIRED_FEATURES) != NetworkLimits.REQUIRED_FEATURES) {
            session.phase = ServerPhase.REJECTED;
            return List.of();
        }
        session.cacheHit = payload.definitionCacheHit();
        session.lastActivity = Instant.now(clock);
        if (payload.definitionCacheHit()) {
            return sendFullState(session);
        }
        session.definitionTransfer = Optional.of(PreparedTransfer.create(
                session.hello.sessionId(), TransferKind.DEFINITIONS, session.definitionBytes));
        session.phase = ServerPhase.WAITING_DEFINITION_ACK;
        return session.definitionTransfer.orElseThrow().payloads();
    }

    public synchronized List<CustomPacketPayload> handleTransferAck(
            UUID playerId,
            NetworkPayloads.TransferAck payload
    ) {
        Session session = require(playerId);
        if (!payload.sessionId().equals(session.hello.sessionId())) {
            return List.of();
        }
        session.lastActivity = Instant.now(clock);
        if (payload.kind() == TransferKind.DEFINITIONS
                && session.phase == ServerPhase.WAITING_DEFINITION_ACK
                && matches(session.definitionTransfer, payload)) {
            session.definitionRetryCount = 0;
            return sendFullState(session);
        }
        if (payload.kind() == TransferKind.FULL_STATE
                && session.phase == ServerPhase.WAITING_STATE_ACK
                && matches(session.stateTransfer, payload)) {
            session.phase = ServerPhase.ACTIVE;
            session.lastAcknowledgedSyncRevision = session.sentState.syncRevision();
            session.lastAcknowledgedStateDigest = VisibleStateCodec.digest(session.sentState);
            session.fullStateRetryCount = 0;
            if (session.currentState.syncRevision() > session.sentState.syncRevision()) {
                return sync(playerId, session.currentState);
            }
        }
        return List.of();
    }

    public synchronized void handleStateAck(UUID playerId, NetworkPayloads.StateAck payload) {
        Session session = require(playerId);
        if (!payload.sessionId().equals(session.hello.sessionId()) || session.phase != ServerPhase.ACTIVE) {
            return;
        }
        if (payload.syncRevision() == session.sentState.syncRevision()
                && payload.stateDigest().equals(VisibleStateCodec.digest(session.sentState))) {
            session.lastAcknowledgedSyncRevision = payload.syncRevision();
            session.lastAcknowledgedStateDigest = payload.stateDigest();
            session.lastActivity = Instant.now(clock);
        }
    }

    public synchronized List<CustomPacketPayload> handleResync(
            UUID playerId,
            NetworkPayloads.ResyncRequest payload
    ) {
        Session session = require(playerId);
        if (!payload.sessionId().equals(session.hello.sessionId())) {
            return List.of();
        }
        session.resyncCount = Math.addExact(session.resyncCount, 1);
        session.lastActivity = Instant.now(clock);
        if (session.phase == ServerPhase.WAITING_DEFINITION_ACK) {
            if (session.definitionRetryCount >= NetworkLimits.MAX_TRANSFER_RETRIES) {
                session.phase = ServerPhase.REJECTED;
                return List.of();
            }
            session.definitionRetryCount++;
            session.definitionTransfer = Optional.of(PreparedTransfer.create(
                    session.hello.sessionId(), TransferKind.DEFINITIONS, session.definitionBytes));
            return session.definitionTransfer.orElseThrow().payloads();
        }
        if (session.phase == ServerPhase.WAITING_STATE_ACK) {
            if (session.fullStateRetryCount >= NetworkLimits.MAX_TRANSFER_RETRIES) {
                session.phase = ServerPhase.REJECTED;
                return List.of();
            }
            session.fullStateRetryCount++;
            return sendFullState(session);
        }
        session.fullStateRetryCount = 0;
        return sendFullState(session);
    }

    public synchronized List<CustomPacketPayload> handleIntent(
            UUID playerId,
            NetworkPayloads.Intent payload
    ) {
        return handleIntent(
                playerId, payload, IntentExecutor.REJECT_TREE_INTENTS,
                ClassIntentExecutor.REJECT_CLASS_INTENTS,
                AbilityIntentExecutor.REJECT_ABILITY_INTENTS);
    }

    public synchronized List<CustomPacketPayload> handleIntent(
            UUID playerId,
            NetworkPayloads.Intent payload,
            IntentExecutor executor
    ) {
        return handleIntent(playerId, payload, executor, ClassIntentExecutor.REJECT_CLASS_INTENTS,
                AbilityIntentExecutor.REJECT_ABILITY_INTENTS);
    }

    public synchronized List<CustomPacketPayload> handleIntent(
            UUID playerId,
            NetworkPayloads.Intent payload,
            IntentExecutor executor,
            ClassIntentExecutor classExecutor
    ) {
        return handleIntent(playerId, payload, executor, classExecutor,
                AbilityIntentExecutor.REJECT_ABILITY_INTENTS);
    }

    public synchronized List<CustomPacketPayload> handleIntent(
            UUID playerId,
            NetworkPayloads.Intent payload,
            IntentExecutor executor,
            ClassIntentExecutor classExecutor,
            AbilityIntentExecutor abilityExecutor
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(classExecutor, "classExecutor");
        Objects.requireNonNull(abilityExecutor, "abilityExecutor");
        Session session = sessions.get(playerId);
        if (session == null) {
            return List.of(staleSessionResult(payload.sessionId(), payload.requestId(), 0));
        }
        if (!payload.sessionId().equals(session.hello.sessionId())) {
            return List.of(staleSessionResult(
                    session.hello.sessionId(), payload.requestId(), session.currentState.stateRevision()));
        }
        List<CustomPacketPayload> cached = session.requestResponses.get(payload.requestId());
        if (cached != null) {
            return cached;
        }
        if (session.phase != ServerPhase.ACTIVE) {
            return List.of(staleSessionResult(
                    session.hello.sessionId(), payload.requestId(), session.currentState.stateRevision()));
        }
        if (!session.intentBucket.tryConsume(Instant.now(clock))) {
            return cacheSingle(session, payload.requestId(), NetworkPayloads.IntentStatus.RATE_LIMITED,
                    "Intent rate limit exceeded");
        }
        if (session.highestRequestId >= 0 && payload.requestId() <= session.highestRequestId) {
            return cacheSingle(session, payload.requestId(), NetworkPayloads.IntentStatus.TOO_OLD,
                    "Request id is older than the highest monotonic request");
        }
        long maximum = session.highestRequestId < 0
                ? NetworkLimits.MAX_FUTURE_REQUEST_JUMP
                : session.highestRequestId > Long.MAX_VALUE - NetworkLimits.MAX_FUTURE_REQUEST_JUMP
                ? Long.MAX_VALUE
                : session.highestRequestId + NetworkLimits.MAX_FUTURE_REQUEST_JUMP;
        if (payload.requestId() > maximum) {
            return cacheSingle(session, payload.requestId(), NetworkPayloads.IntentStatus.FUTURE_JUMP,
                    "Request id jumps beyond the bounded replay window");
        }
        session.highestRequestId = Math.max(session.highestRequestId, payload.requestId());
        NetworkPayloads.IntentResult response;
        if (payload.definitionGeneration() != session.currentState.definitionRevision().generation()
                || !payload.semanticDigest().equals(session.currentState.definitionRevision().semanticDigest())) {
            response = result(session, payload.requestId(), NetworkPayloads.IntentStatus.STALE_DEFINITION,
                    "Intent definition generation is stale");
            return cacheWithFullState(session, payload.requestId(), response);
        } else if (payload.stateRevision() != session.currentState.stateRevision()) {
            response = result(session, payload.requestId(), NetworkPayloads.IntentStatus.STALE_STATE,
                    "Intent state revision is stale");
            return cacheWithFullState(session, payload.requestId(), response);
        }
        if (payload.intentType() == NetworkPayloads.IntentType.NOOP_TEST) {
            if (!payload.payload().isEmpty()) {
                return cacheSingle(session, payload.requestId(), NetworkPayloads.IntentStatus.INVALID,
                        "No op intent payload must be empty");
            }
            return cacheSingle(session, payload.requestId(), NetworkPayloads.IntentStatus.ACCEPTED,
                    "Bounded no op intent accepted without gameplay mutation");
        }
        if (session.currentState.quarantined()) {
            return cacheSingle(session, payload.requestId(), NetworkPayloads.IntentStatus.INVALID,
                    "Player progression data is quarantined");
        }
        try {
            boolean classIntent = isClassIntent(payload.intentType());
            boolean abilityIntent = isAbilityIntent(payload.intentType());
            TreeIntentPayload treePayload = classIntent || abilityIntent ? null
                    : TreeIntentPayload.decode(payload.intentType(), payload.payload());
            ClassIntentPayload classPayload = classIntent
                    ? ClassIntentPayload.decode(payload.intentType(), payload.payload()) : null;
            AbilityIntentPayload abilityPayload = abilityIntent
                    ? AbilityIntentPayload.decode(payload.intentType(), payload.payload()) : null;
            IntentExecution execution = Objects.requireNonNull(abilityIntent
                            ? abilityExecutor.execute(playerId, payload, abilityPayload)
                            : classIntent
                            ? classExecutor.execute(playerId, payload, classPayload)
                            : executor.execute(playerId, payload, treePayload),
                    "intent execution");
            response = result(session, payload.requestId(), execution.status(), execution.message());
            List<CustomPacketPayload> responses = validateExecutionResponses(
                    session, payload, treePayload, classPayload, response, execution.followups());
            cacheResponses(session, payload.requestId(), responses);
            session.lastActivity = Instant.now(clock);
            return responses;
        } catch (RuntimeException exception) {
            return cacheSingle(session, payload.requestId(), NetworkPayloads.IntentStatus.INVALID,
                    safeIntentMessage(exception));
        }
    }

    public synchronized List<CustomPacketPayload> sync(UUID playerId, VisiblePlayerState state) {
        Session session = sessions.get(playerId);
        if (session == null) {
            return List.of();
        }
        if (!state.playerId().equals(playerId)) {
            throw new IllegalArgumentException("Visible state owner does not match session player");
        }
        session.currentState = state;
        if (session.phase != ServerPhase.ACTIVE) {
            return List.of();
        }
        if (!state.definitionRevision().equals(session.sentState.definitionRevision())
                || state.presentationRevision() != session.sentState.presentationRevision()
                || !state.presentationDigest().equals(session.sentState.presentationDigest())) {
            session.phase = ServerPhase.REJECTED;
            return List.of();
        }
        if (state.syncRevision() <= session.sentState.syncRevision()) {
            return List.of();
        }
        String digest = VisibleStateCodec.digest(state);
        StateDelta delta = StateDelta.between(session.sentState, state, digest);
        byte[] encoded = VisibleStateCodec.encodeDelta(delta);
        if (encoded.length > NetworkLimits.MAX_DELTA_BYTES) {
            return sendFullState(session);
        }
        session.sentState = state;
        session.deltaCount = Math.addExact(session.deltaCount, 1);
        return List.of(new NetworkPayloads.StateDeltaPayload(session.hello.sessionId(), encoded));
    }

    public synchronized Optional<Status> status(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session == null) {
            return Optional.empty();
        }
        ServerPhase phase = session.phase;
        if (phase != ServerPhase.ACTIVE && phase != ServerPhase.REJECTED
                && DurationBetween.millis(session.lastActivity, Instant.now(clock))
                > NetworkLimits.SESSION_TIMEOUT_MILLIS) {
            phase = ServerPhase.TIMED_OUT;
            session.phase = phase;
        }
        return Optional.of(new Status(
                session.hello.sessionId(),
                phase,
                session.cacheHit,
                session.hello.definitionGeneration(),
                session.hello.semanticDigest(),
                session.hello.presentationDigest(),
                session.sentState.syncRevision(),
                session.sentState.stateRevision(),
                session.lastAcknowledgedSyncRevision,
                session.deltaCount,
                session.resyncCount,
                session.requestResponses.size()
        ));
    }

    public synchronized void remove(UUID playerId) {
        sessions.remove(playerId);
    }

    public synchronized void clear() {
        sessions.clear();
    }

    private List<CustomPacketPayload> sendFullState(Session session) {
        session.sentState = session.currentState;
        session.stateTransfer = Optional.of(PreparedTransfer.create(
                session.hello.sessionId(), TransferKind.FULL_STATE,
                VisibleStateCodec.encode(session.currentState)));
        session.phase = ServerPhase.WAITING_STATE_ACK;
        return session.stateTransfer.orElseThrow().payloads();
    }

    private static boolean matches(
            Optional<PreparedTransfer> transfer,
            NetworkPayloads.TransferAck payload
    ) {
        return transfer.isPresent()
                && transfer.orElseThrow().start().transferId().equals(payload.transferId())
                && transfer.orElseThrow().start().kind() == payload.kind()
                && transfer.orElseThrow().start().digest().equals(payload.digest());
    }

    private Session require(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session == null) {
            throw new IllegalArgumentException("No active ProgressiveSkills network session for player");
        }
        return session;
    }

    private static NetworkPayloads.IntentResult staleSessionResult(
            UUID sessionId,
            long requestId,
            long revision
    ) {
        return new NetworkPayloads.IntentResult(
                sessionId, requestId, resultId(sessionId, requestId),
                NetworkPayloads.IntentStatus.STALE_SESSION, revision,
                "ProgressiveSkills network session is stale", false
        );
    }

    private static NetworkPayloads.IntentResult result(
            Session session,
            long requestId,
            NetworkPayloads.IntentStatus status,
            String message
    ) {
        return new NetworkPayloads.IntentResult(
                session.hello.sessionId(), requestId, resultId(session.hello.sessionId(), requestId),
                status, session.currentState.stateRevision(), message, false
        );
    }

    private static UUID resultId(UUID sessionId, long requestId) {
        return UUID.nameUUIDFromBytes(
                (sessionId + ":" + requestId).getBytes(StandardCharsets.UTF_8));
    }

    private static void cacheResponses(
            Session session,
            long requestId,
            List<CustomPacketPayload> responses
    ) {
        session.requestResponses.put(requestId, List.copyOf(responses));
        while (session.requestResponses.size() > NetworkLimits.MAX_REQUEST_RESULTS) {
            session.requestResponses.pollFirstEntry();
        }
    }

    private List<CustomPacketPayload> cacheSingle(
            Session session,
            long requestId,
            NetworkPayloads.IntentStatus status,
            String message
    ) {
        NetworkPayloads.IntentResult response = result(session, requestId, status, message);
        List<CustomPacketPayload> responses = List.of(response);
        cacheResponses(session, requestId, responses);
        session.lastActivity = Instant.now(clock);
        return responses;
    }

    private List<CustomPacketPayload> cacheWithFullState(
            Session session,
            long requestId,
            NetworkPayloads.IntentResult response
    ) {
        var responses = new ArrayList<CustomPacketPayload>();
        session.resyncCount = Math.addExact(session.resyncCount, 1);
        responses.add(response);
        responses.addAll(sendFullState(session));
        List<CustomPacketPayload> finalResponses = List.copyOf(responses);
        cacheResponses(session, requestId, finalResponses);
        session.lastActivity = Instant.now(clock);
        return finalResponses;
    }

    private static List<CustomPacketPayload> validateExecutionResponses(
            Session session,
            NetworkPayloads.Intent intent,
            TreeIntentPayload treePayload,
            ClassIntentPayload classPayload,
            NetworkPayloads.IntentResult response,
            List<CustomPacketPayload> followups
    ) {
        Objects.requireNonNull(followups, "intent followups");
        if (followups.size() > 1) {
            throw new IllegalArgumentException("Intent execution returned too many followups");
        }
        var responses = new ArrayList<CustomPacketPayload>();
        responses.add(response);
        for (CustomPacketPayload followup : followups) {
            if (!validTreeFollowup(session, intent, treePayload, followup)
                    && !validClassFollowup(session, intent, classPayload, followup)) {
                throw new IllegalArgumentException("Intent execution returned an invalid followup");
            }
            responses.add(followup);
        }
        return List.copyOf(responses);
    }

    private static boolean validTreeFollowup(
            Session session,
            NetworkPayloads.Intent intent,
            TreeIntentPayload payload,
            CustomPacketPayload followup
    ) {
        return payload != null
                && followup instanceof NetworkPayloads.TreeRefundPreview preview
                && intent.intentType() == NetworkPayloads.IntentType.TREE_REFUND_PREVIEW
                && preview.sessionId().equals(session.hello.sessionId())
                && preview.requestId() == intent.requestId()
                && preview.definitionGeneration() == intent.definitionGeneration()
                && preview.semanticDigest().equals(intent.semanticDigest())
                && preview.stateRevision() == intent.stateRevision()
                && preview.treeId().equals(payload.treeId())
                && preview.nodeId().equals(payload.nodeId());
    }

    private static boolean validClassFollowup(
            Session session,
            NetworkPayloads.Intent intent,
            ClassIntentPayload payload,
            CustomPacketPayload followup
    ) {
        return payload != null
                && followup instanceof NetworkPayloads.ClassChangePreview preview
                && preview.intentType() == intent.intentType()
                && preview.sessionId().equals(session.hello.sessionId())
                && preview.requestId() == intent.requestId()
                && preview.definitionGeneration() == intent.definitionGeneration()
                && preview.semanticDigest().equals(intent.semanticDigest())
                && preview.stateRevision() == intent.stateRevision()
                && preview.classId().equals(payload.classId())
                && preview.replacementClassId().equals(payload.replacementClassId());
    }

    private static boolean isClassIntent(NetworkPayloads.IntentType type) {
        return type == NetworkPayloads.IntentType.CLASS_SELECT
                || type == NetworkPayloads.IntentType.CLASS_RESPEC_PREVIEW
                || type == NetworkPayloads.IntentType.CLASS_RESPEC_CONFIRM
                || type == NetworkPayloads.IntentType.CLASS_SWAP_PREVIEW
                || type == NetworkPayloads.IntentType.CLASS_SWAP_CONFIRM;
    }

    private static boolean isAbilityIntent(NetworkPayloads.IntentType type) {
        return type == NetworkPayloads.IntentType.ABILITY_ASSIGN
                || type == NetworkPayloads.IntentType.ABILITY_UNASSIGN
                || type == NetworkPayloads.IntentType.ABILITY_SELECT
                || type == NetworkPayloads.IntentType.ABILITY_TOGGLE
                || type == NetworkPayloads.IntentType.ABILITY_ACTIVATE;
    }

    private static String safeIntentMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "Intent execution failed";
        }
        var safe = new StringBuilder();
        message.codePoints().forEach(character -> {
            if (!Character.isISOControl(character)) {
                String next = new String(Character.toChars(character));
                if ((safe.toString() + next).getBytes(StandardCharsets.UTF_8).length
                        <= NetworkLimits.MAX_RESYNC_REASON_BYTES) {
                    safe.append(next);
                }
            }
        });
        return safe.isEmpty() ? "Intent execution failed" : safe.toString();
    }

    @FunctionalInterface
    public interface IntentExecutor {
        IntentExecutor REJECT_TREE_INTENTS = (playerId, intent, payload) ->
                IntentExecution.invalid("Tree intents are not configured on this server");

        IntentExecution execute(
                UUID playerId,
                NetworkPayloads.Intent intent,
                TreeIntentPayload payload
        );
    }

    @FunctionalInterface
    public interface ClassIntentExecutor {
        ClassIntentExecutor REJECT_CLASS_INTENTS = (playerId, intent, payload) ->
                IntentExecution.invalid("Class intents are not configured on this server");

        IntentExecution execute(
                UUID playerId,
                NetworkPayloads.Intent intent,
                ClassIntentPayload payload
        );
    }

    @FunctionalInterface
    public interface AbilityIntentExecutor {
        AbilityIntentExecutor REJECT_ABILITY_INTENTS = (playerId, intent, payload) ->
                IntentExecution.invalid("Ability intents are not configured on this server");

        IntentExecution execute(
                UUID playerId,
                NetworkPayloads.Intent intent,
                AbilityIntentPayload payload
        );
    }

    public record IntentExecution(
            NetworkPayloads.IntentStatus status,
            String message,
            List<CustomPacketPayload> followups
    ) {
        public IntentExecution {
            Objects.requireNonNull(status, "status");
            message = NetworkLimits.requireBoundedText(
                    message, NetworkLimits.MAX_RESYNC_REASON_BYTES, "intent execution message");
            followups = List.copyOf(Objects.requireNonNull(followups, "followups"));
            if (status != NetworkPayloads.IntentStatus.ACCEPTED
                    && status != NetworkPayloads.IntentStatus.INVALID) {
                throw new IllegalArgumentException("Intent executor may only accept or reject an intent");
            }
        }

        public static IntentExecution accepted(String message) {
            return new IntentExecution(NetworkPayloads.IntentStatus.ACCEPTED, message, List.of());
        }

        public static IntentExecution accepted(String message, CustomPacketPayload followup) {
            return new IntentExecution(
                    NetworkPayloads.IntentStatus.ACCEPTED, message, List.of(followup));
        }

        public static IntentExecution invalid(String message) {
            return new IntentExecution(NetworkPayloads.IntentStatus.INVALID, message, List.of());
        }
    }

    public enum ServerPhase {
        WAITING_CLIENT_HELLO,
        WAITING_DEFINITION_ACK,
        WAITING_STATE_ACK,
        ACTIVE,
        TIMED_OUT,
        REJECTED
    }

    public record Status(
            UUID sessionId,
            ServerPhase phase,
            boolean definitionCacheHit,
            long definitionGeneration,
            String semanticDigest,
            String presentationDigest,
            long sentSyncRevision,
            long stateRevision,
            long acknowledgedSyncRevision,
            int deltaCount,
            int resyncCount,
            int cachedIntentResults
    ) {
    }

    private static final class Session {
        private final NetworkPayloads.ServerHello hello;
        private final byte[] definitionBytes;
        private final TokenBucket intentBucket;
        private final TreeMap<Long, List<CustomPacketPayload>> requestResponses = new TreeMap<>();
        private Optional<PreparedTransfer> definitionTransfer = Optional.empty();
        private Optional<PreparedTransfer> stateTransfer = Optional.empty();
        private VisiblePlayerState currentState;
        private VisiblePlayerState sentState;
        private ServerPhase phase = ServerPhase.WAITING_CLIENT_HELLO;
        private Instant lastActivity;
        private long highestRequestId = -1;
        private long lastAcknowledgedSyncRevision = -1;
        private String lastAcknowledgedStateDigest = "";
        private boolean cacheHit;
        private int deltaCount;
        private int resyncCount;
        private int definitionRetryCount;
        private int fullStateRetryCount;

        private Session(
                NetworkPayloads.ServerHello hello,
                byte[] definitionBytes,
                VisiblePlayerState initialState,
                Instant now
        ) {
            this.hello = hello;
            this.definitionBytes = definitionBytes.clone();
            currentState = initialState;
            sentState = initialState;
            lastActivity = now;
            intentBucket = new TokenBucket(now);
        }
    }

    private static final class TokenBucket {
        private double tokens = INTENT_BUCKET_CAPACITY;
        private Instant lastRefill;

        private TokenBucket(Instant now) {
            lastRefill = now;
        }

        private boolean tryConsume(Instant now) {
            long elapsedNanos = Math.max(0, java.time.Duration.between(lastRefill, now).toNanos());
            tokens = Math.min(INTENT_BUCKET_CAPACITY,
                    tokens + (elapsedNanos / 1_000_000_000.0) * INTENT_REFILL_PER_SECOND);
            lastRefill = now;
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }

    private static final class DurationBetween {
        private DurationBetween() {
        }

        private static long millis(Instant start, Instant end) {
            return Math.max(0, java.time.Duration.between(start, end).toMillis());
        }
    }
}
