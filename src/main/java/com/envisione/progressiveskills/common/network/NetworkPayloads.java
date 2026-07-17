package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Closed Phase 6 payload vocabulary with allocation bounds enforced inside every decoder. */
public final class NetworkPayloads {
    private NetworkPayloads() {
    }

    public record ServerHello(
            UUID serverIdentity,
            UUID sessionId,
            int protocolVersion,
            long features,
            long definitionGeneration,
            String semanticDigest,
            long presentationRevision,
            String presentationDigest,
            int definitionCount
    ) implements CustomPacketPayload {
        public static final Type<ServerHello> TYPE = NetworkPayloads.type("server_hello");
        public static final StreamCodec<FriendlyByteBuf, ServerHello> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.serverIdentity);
                    buffer.writeUUID(value.sessionId);
                    buffer.writeVarInt(value.protocolVersion);
                    buffer.writeVarLong(value.features);
                    buffer.writeVarLong(value.definitionGeneration);
                    buffer.writeUtf(value.semanticDigest, 64);
                    buffer.writeVarLong(value.presentationRevision);
                    buffer.writeUtf(value.presentationDigest, 64);
                    buffer.writeVarInt(value.definitionCount);
                },
                buffer -> new ServerHello(
                        buffer.readUUID(), buffer.readUUID(), buffer.readVarInt(), buffer.readVarLong(),
                        buffer.readVarLong(), buffer.readUtf(64), buffer.readVarLong(), buffer.readUtf(64),
                        buffer.readVarInt()
                )
        );

        public ServerHello {
            Objects.requireNonNull(serverIdentity, "serverIdentity");
            Objects.requireNonNull(sessionId, "sessionId");
            if (protocolVersion <= 0 || definitionGeneration < 0 || presentationRevision < 0
                    || definitionCount < 0 || definitionCount > NetworkLimits.MAX_DEFINITIONS) {
                throw new IllegalArgumentException("Server hello contains invalid revisions or counts");
            }
            semanticDigest = NetworkLimits.requireDigest(semanticDigest, "semanticDigest");
            presentationDigest = NetworkLimits.requireDigest(presentationDigest, "presentationDigest");
        }

        @Override
        public Type<ServerHello> type() {
            return TYPE;
        }
    }

    public record ClientHello(UUID sessionId, int protocolVersion, long features, boolean definitionCacheHit)
            implements CustomPacketPayload {
        public static final Type<ClientHello> TYPE = NetworkPayloads.type("client_hello");
        public static final StreamCodec<FriendlyByteBuf, ClientHello> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeVarInt(value.protocolVersion);
                    buffer.writeVarLong(value.features);
                    buffer.writeBoolean(value.definitionCacheHit);
                },
                buffer -> new ClientHello(
                        buffer.readUUID(), buffer.readVarInt(), buffer.readVarLong(), buffer.readBoolean())
        );

        public ClientHello {
            Objects.requireNonNull(sessionId, "sessionId");
            if (protocolVersion <= 0) {
                throw new IllegalArgumentException("Client protocol version must be positive");
            }
        }

        @Override
        public Type<ClientHello> type() {
            return TYPE;
        }
    }

    public record TransferStart(
            UUID sessionId,
            UUID transferId,
            TransferKind kind,
            int uncompressedBytes,
            int compressedBytes,
            int chunkCount,
            String digest
    ) implements CustomPacketPayload {
        public static final Type<TransferStart> TYPE = NetworkPayloads.type("transfer_start");
        public static final StreamCodec<FriendlyByteBuf, TransferStart> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeUUID(value.transferId);
                    writeEnum(buffer, value.kind);
                    buffer.writeVarInt(value.uncompressedBytes);
                    buffer.writeVarInt(value.compressedBytes);
                    buffer.writeVarInt(value.chunkCount);
                    buffer.writeUtf(value.digest, 64);
                },
                buffer -> new TransferStart(
                        buffer.readUUID(), buffer.readUUID(), readEnum(buffer, TransferKind.values(), "transfer kind"),
                        buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(64)
                )
        );

        public TransferStart {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(transferId, "transferId");
            Objects.requireNonNull(kind, "kind");
            int maximum = kind == TransferKind.DEFINITIONS
                    ? NetworkLimits.MAX_DEFINITION_BYTES : NetworkLimits.MAX_STATE_BYTES;
            if (uncompressedBytes < 0 || uncompressedBytes > maximum
                    || compressedBytes < 0 || compressedBytes > NetworkLimits.MAX_COMPRESSED_TRANSFER_BYTES
                    || chunkCount < 1 || chunkCount > NetworkLimits.MAX_CHUNKS
                    || chunkCount != Math.max(1,
                    (compressedBytes + NetworkLimits.CHUNK_BYTES - 1) / NetworkLimits.CHUNK_BYTES)) {
                throw new IllegalArgumentException("Transfer envelope exceeds a hard ceiling");
            }
            digest = NetworkLimits.requireDigest(digest, "transfer digest");
        }

        @Override
        public Type<TransferStart> type() {
            return TYPE;
        }
    }

    public record TransferChunk(UUID sessionId, UUID transferId, int index, byte[] contents)
            implements CustomPacketPayload {
        public static final Type<TransferChunk> TYPE = NetworkPayloads.type("transfer_chunk");
        public static final StreamCodec<FriendlyByteBuf, TransferChunk> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeUUID(value.transferId);
                    buffer.writeVarInt(value.index);
                    buffer.writeByteArray(value.contents);
                },
                buffer -> new TransferChunk(
                        buffer.readUUID(), buffer.readUUID(), buffer.readVarInt(),
                        buffer.readByteArray(NetworkLimits.CHUNK_BYTES)
                )
        );

        public TransferChunk {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(transferId, "transferId");
            if (index < 0 || index >= NetworkLimits.MAX_CHUNKS) {
                throw new IllegalArgumentException("Transfer chunk index is out of bounds");
            }
            contents = Arrays.copyOf(Objects.requireNonNull(contents, "contents"), contents.length);
            if (contents.length > NetworkLimits.CHUNK_BYTES) {
                throw new IllegalArgumentException("Transfer chunk exceeds its payload ceiling");
            }
        }

        @Override
        public byte[] contents() {
            return Arrays.copyOf(contents, contents.length);
        }

        @Override
        public Type<TransferChunk> type() {
            return TYPE;
        }
    }

    public record TransferAck(UUID sessionId, UUID transferId, TransferKind kind, String digest)
            implements CustomPacketPayload {
        public static final Type<TransferAck> TYPE = NetworkPayloads.type("transfer_ack");
        public static final StreamCodec<FriendlyByteBuf, TransferAck> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeUUID(value.transferId);
                    writeEnum(buffer, value.kind);
                    buffer.writeUtf(value.digest, 64);
                },
                buffer -> new TransferAck(
                        buffer.readUUID(), buffer.readUUID(), readEnum(buffer, TransferKind.values(), "transfer kind"),
                        buffer.readUtf(64)
                )
        );

        public TransferAck {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(transferId, "transferId");
            Objects.requireNonNull(kind, "kind");
            digest = NetworkLimits.requireDigest(digest, "transfer digest");
        }

        @Override
        public Type<TransferAck> type() {
            return TYPE;
        }
    }

    public record StateDeltaPayload(UUID sessionId, byte[] encodedDelta) implements CustomPacketPayload {
        public static final Type<StateDeltaPayload> TYPE = NetworkPayloads.type("state_delta");
        public static final StreamCodec<FriendlyByteBuf, StateDeltaPayload> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeByteArray(value.encodedDelta);
                },
                buffer -> new StateDeltaPayload(
                        buffer.readUUID(), buffer.readByteArray(NetworkLimits.MAX_DELTA_BYTES))
        );

        public StateDeltaPayload {
            Objects.requireNonNull(sessionId, "sessionId");
            encodedDelta = Arrays.copyOf(Objects.requireNonNull(encodedDelta, "encodedDelta"), encodedDelta.length);
            if (encodedDelta.length > NetworkLimits.MAX_DELTA_BYTES) {
                throw new IllegalArgumentException("State delta exceeds the single-payload ceiling");
            }
        }

        @Override
        public byte[] encodedDelta() {
            return Arrays.copyOf(encodedDelta, encodedDelta.length);
        }

        @Override
        public Type<StateDeltaPayload> type() {
            return TYPE;
        }
    }

    public record StateAck(UUID sessionId, long syncRevision, String stateDigest)
            implements CustomPacketPayload {
        public static final Type<StateAck> TYPE = NetworkPayloads.type("state_ack");
        public static final StreamCodec<FriendlyByteBuf, StateAck> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeVarLong(value.syncRevision);
                    buffer.writeUtf(value.stateDigest, 64);
                },
                buffer -> new StateAck(buffer.readUUID(), buffer.readVarLong(), buffer.readUtf(64))
        );

        public StateAck {
            Objects.requireNonNull(sessionId, "sessionId");
            if (syncRevision < 0) {
                throw new IllegalArgumentException("Acknowledged sync revision must not be negative");
            }
            stateDigest = NetworkLimits.requireDigest(stateDigest, "stateDigest");
        }

        @Override
        public Type<StateAck> type() {
            return TYPE;
        }
    }

    public record ResyncRequest(UUID sessionId, String reason) implements CustomPacketPayload {
        public static final Type<ResyncRequest> TYPE = NetworkPayloads.type("resync_request");
        public static final StreamCodec<FriendlyByteBuf, ResyncRequest> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeUtf(value.reason, NetworkLimits.MAX_RESYNC_REASON_BYTES);
                },
                buffer -> new ResyncRequest(
                        buffer.readUUID(), buffer.readUtf(NetworkLimits.MAX_RESYNC_REASON_BYTES))
        );

        public ResyncRequest {
            Objects.requireNonNull(sessionId, "sessionId");
            reason = NetworkLimits.requireBoundedText(
                    reason, NetworkLimits.MAX_RESYNC_REASON_BYTES, "resync reason");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("Resync reason must not be blank");
            }
        }

        @Override
        public Type<ResyncRequest> type() {
            return TYPE;
        }
    }

    public record Intent(
            UUID sessionId,
            long requestId,
            long definitionGeneration,
            String semanticDigest,
            long stateRevision,
            IntentType intentType,
            String payload
    ) implements CustomPacketPayload {
        public static final Type<Intent> TYPE = NetworkPayloads.type("intent");
        public static final StreamCodec<FriendlyByteBuf, Intent> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeVarLong(value.requestId);
                    buffer.writeVarLong(value.definitionGeneration);
                    buffer.writeUtf(value.semanticDigest, 64);
                    buffer.writeVarLong(value.stateRevision);
                    writeEnum(buffer, value.intentType);
                    buffer.writeUtf(value.payload, NetworkLimits.MAX_INTENT_BYTES);
                },
                buffer -> new Intent(
                        buffer.readUUID(), buffer.readVarLong(), buffer.readVarLong(), buffer.readUtf(64),
                        buffer.readVarLong(), readEnum(buffer, IntentType.values(), "intent type"),
                        buffer.readUtf(NetworkLimits.MAX_INTENT_BYTES)
                )
        );

        public Intent {
            Objects.requireNonNull(sessionId, "sessionId");
            if (requestId < 0 || definitionGeneration < 0 || stateRevision < 0) {
                throw new IllegalArgumentException("Intent revisions must not be negative");
            }
            semanticDigest = NetworkLimits.requireDigest(semanticDigest, "semanticDigest");
            Objects.requireNonNull(intentType, "intentType");
            payload = NetworkLimits.requireBoundedText(payload, NetworkLimits.MAX_INTENT_BYTES, "intent payload");
        }

        @Override
        public Type<Intent> type() {
            return TYPE;
        }
    }

    public record IntentResult(
            UUID sessionId,
            long requestId,
            UUID resultId,
            IntentStatus status,
            long currentStateRevision,
            String message,
            boolean replayed
    ) implements CustomPacketPayload {
        public static final Type<IntentResult> TYPE = NetworkPayloads.type("intent_result");
        public static final StreamCodec<FriendlyByteBuf, IntentResult> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeVarLong(value.requestId);
                    buffer.writeUUID(value.resultId);
                    writeEnum(buffer, value.status);
                    buffer.writeVarLong(value.currentStateRevision);
                    buffer.writeUtf(value.message, NetworkLimits.MAX_RESYNC_REASON_BYTES);
                    buffer.writeBoolean(value.replayed);
                },
                buffer -> new IntentResult(
                        buffer.readUUID(), buffer.readVarLong(), buffer.readUUID(),
                        readEnum(buffer, IntentStatus.values(), "intent status"), buffer.readVarLong(),
                        buffer.readUtf(NetworkLimits.MAX_RESYNC_REASON_BYTES), buffer.readBoolean()
                )
        );

        public IntentResult {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(resultId, "resultId");
            Objects.requireNonNull(status, "status");
            if (requestId < 0 || currentStateRevision < 0) {
                throw new IllegalArgumentException("Intent result revisions must not be negative");
            }
            message = NetworkLimits.requireBoundedText(
                    message, NetworkLimits.MAX_RESYNC_REASON_BYTES, "intent result message");
        }

        @Override
        public Type<IntentResult> type() {
            return TYPE;
        }
    }

    public record TreeRefundPreview(
            UUID sessionId,
            long requestId,
            long definitionGeneration,
            String semanticDigest,
            long stateRevision,
            ResourceLocation treeId,
            ResourceLocation nodeId,
            List<ResourceLocation> affectedNodes,
            Map<ResourceLocation, Long> refundBalances,
            String previewDigest,
            List<String> blockers
    ) implements CustomPacketPayload {
        public static final Type<TreeRefundPreview> TYPE = NetworkPayloads.type("tree_refund_preview");
        public static final StreamCodec<FriendlyByteBuf, TreeRefundPreview> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeVarLong(value.requestId);
                    buffer.writeVarLong(value.definitionGeneration);
                    buffer.writeUtf(value.semanticDigest, 64);
                    buffer.writeVarLong(value.stateRevision);
                    writeId(buffer, value.treeId);
                    writeId(buffer, value.nodeId);
                    buffer.writeVarInt(value.affectedNodes.size());
                    value.affectedNodes.forEach(node -> writeId(buffer, node));
                    buffer.writeVarInt(value.refundBalances.size());
                    value.refundBalances.forEach((currency, amount) -> {
                        writeId(buffer, currency);
                        buffer.writeVarLong(amount);
                    });
                    buffer.writeUtf(value.previewDigest, 64);
                    buffer.writeVarInt(value.blockers.size());
                    value.blockers.forEach(blocker ->
                            buffer.writeUtf(blocker, NetworkLimits.MAX_RESYNC_REASON_BYTES));
                },
                buffer -> {
                    UUID sessionId = buffer.readUUID();
                    long requestId = buffer.readVarLong();
                    long definitionGeneration = buffer.readVarLong();
                    String semanticDigest = buffer.readUtf(64);
                    long stateRevision = buffer.readVarLong();
                    ResourceLocation treeId = readId(buffer);
                    ResourceLocation nodeId = readId(buffer);
                    int affectedCount = readCount(
                            buffer, NetworkLimits.MAX_TREE_PREVIEW_NODES, "affected tree node");
                    var affectedNodes = new ArrayList<ResourceLocation>(affectedCount);
                    for (int index = 0; index < affectedCount; index++) {
                        affectedNodes.add(readId(buffer));
                    }
                    int balanceCount = readCount(
                            buffer, NetworkLimits.MAX_TREE_PREVIEW_BALANCES, "tree refund balance");
                    var refundBalances = new LinkedHashMap<ResourceLocation, Long>();
                    for (int index = 0; index < balanceCount; index++) {
                        ResourceLocation currency = readId(buffer);
                        if (refundBalances.putIfAbsent(currency, buffer.readVarLong()) != null) {
                            throw new IllegalArgumentException("Duplicate tree refund currency");
                        }
                    }
                    String previewDigest = buffer.readUtf(64);
                    int blockerCount = readCount(
                            buffer, NetworkLimits.MAX_TREE_PREVIEW_BLOCKERS, "tree refund blocker");
                    var blockers = new ArrayList<String>(blockerCount);
                    for (int index = 0; index < blockerCount; index++) {
                        blockers.add(buffer.readUtf(NetworkLimits.MAX_RESYNC_REASON_BYTES));
                    }
                    return new TreeRefundPreview(
                            sessionId, requestId, definitionGeneration, semanticDigest, stateRevision,
                            treeId, nodeId, affectedNodes, refundBalances, previewDigest, blockers
                    );
                }
        );

        public TreeRefundPreview {
            Objects.requireNonNull(sessionId, "sessionId");
            if (requestId < 0 || definitionGeneration < 0 || stateRevision < 0) {
                throw new IllegalArgumentException("Tree refund preview revisions must not be negative");
            }
            semanticDigest = NetworkLimits.requireDigest(semanticDigest, "semanticDigest");
            treeId = StableId.requireValid(treeId);
            nodeId = StableId.requireValid(nodeId);
            Objects.requireNonNull(affectedNodes, "affectedNodes");
            if (affectedNodes.size() > NetworkLimits.MAX_TREE_PREVIEW_NODES) {
                throw new IllegalArgumentException("Tree refund preview node count exceeds capacity");
            }
            var orderedNodes = new LinkedHashSet<ResourceLocation>();
            for (ResourceLocation node : affectedNodes) {
                if (!orderedNodes.add(StableId.requireValid(node))) {
                    throw new IllegalArgumentException("Tree refund preview contains duplicate nodes");
                }
            }
            affectedNodes = List.copyOf(orderedNodes);
            Objects.requireNonNull(refundBalances, "refundBalances");
            if (refundBalances.size() > NetworkLimits.MAX_TREE_PREVIEW_BALANCES) {
                throw new IllegalArgumentException("Tree refund preview balance count exceeds capacity");
            }
            var sortedBalances = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
            refundBalances.forEach((currency, amount) -> {
                ResourceLocation stableCurrency = StableId.requireValid(currency);
                if (amount == null || amount < 0) {
                    throw new IllegalArgumentException("Tree refund preview balance is invalid");
                }
                sortedBalances.put(stableCurrency, amount);
            });
            refundBalances = Collections.unmodifiableMap(new LinkedHashMap<>(sortedBalances));
            previewDigest = NetworkLimits.requireDigest(previewDigest, "previewDigest");
            Objects.requireNonNull(blockers, "blockers");
            if (blockers.size() > NetworkLimits.MAX_TREE_PREVIEW_BLOCKERS) {
                throw new IllegalArgumentException("Tree refund preview blocker count exceeds capacity");
            }
            blockers = blockers.stream().map(blocker -> NetworkLimits.requireBoundedText(
                    blocker, NetworkLimits.MAX_RESYNC_REASON_BYTES, "tree refund blocker"
            )).toList();
        }

        public boolean allowed() {
            return blockers.isEmpty();
        }

        @Override
        public Type<TreeRefundPreview> type() {
            return TYPE;
        }
    }

    public record ClassChangePreview(
            UUID sessionId,
            long requestId,
            long definitionGeneration,
            String semanticDigest,
            long stateRevision,
            IntentType intentType,
            ResourceLocation classId,
            Optional<ResourceLocation> replacementClassId,
            List<ResourceLocation> affectedClasses,
            Map<ResourceLocation, Long> costBalances,
            String previewDigest,
            List<String> blockers
    ) implements CustomPacketPayload {
        public static final Type<ClassChangePreview> TYPE = NetworkPayloads.type("class_change_preview");
        public static final StreamCodec<FriendlyByteBuf, ClassChangePreview> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeUUID(value.sessionId);
                    buffer.writeVarLong(value.requestId);
                    buffer.writeVarLong(value.definitionGeneration);
                    buffer.writeUtf(value.semanticDigest, 64);
                    buffer.writeVarLong(value.stateRevision);
                    writeEnum(buffer, value.intentType);
                    writeId(buffer, value.classId);
                    buffer.writeBoolean(value.replacementClassId.isPresent());
                    value.replacementClassId.ifPresent(id -> writeId(buffer, id));
                    buffer.writeVarInt(value.affectedClasses.size());
                    value.affectedClasses.forEach(id -> writeId(buffer, id));
                    buffer.writeVarInt(value.costBalances.size());
                    value.costBalances.forEach((currency, amount) -> {
                        writeId(buffer, currency);
                        buffer.writeVarLong(amount);
                    });
                    buffer.writeUtf(value.previewDigest, 64);
                    buffer.writeVarInt(value.blockers.size());
                    value.blockers.forEach(blocker ->
                            buffer.writeUtf(blocker, NetworkLimits.MAX_RESYNC_REASON_BYTES));
                },
                buffer -> {
                    UUID sessionId = buffer.readUUID();
                    long requestId = buffer.readVarLong();
                    long definitionGeneration = buffer.readVarLong();
                    String semanticDigest = buffer.readUtf(64);
                    long stateRevision = buffer.readVarLong();
                    IntentType intentType = readEnum(buffer, IntentType.values(), "class preview intent type");
                    ResourceLocation classId = readId(buffer);
                    Optional<ResourceLocation> replacement = buffer.readBoolean()
                            ? Optional.of(readId(buffer)) : Optional.empty();
                    int affectedCount = readCount(
                            buffer, NetworkLimits.MAX_CLASS_PREVIEW_CLASSES, "affected class");
                    var affected = new ArrayList<ResourceLocation>(affectedCount);
                    for (int index = 0; index < affectedCount; index++) {
                        affected.add(readId(buffer));
                    }
                    int balanceCount = readCount(
                            buffer, NetworkLimits.MAX_CLASS_PREVIEW_BALANCES, "class preview balance");
                    var costs = new LinkedHashMap<ResourceLocation, Long>();
                    for (int index = 0; index < balanceCount; index++) {
                        ResourceLocation currency = readId(buffer);
                        if (costs.putIfAbsent(currency, buffer.readVarLong()) != null) {
                            throw new IllegalArgumentException("Duplicate class preview currency");
                        }
                    }
                    String previewDigest = buffer.readUtf(64);
                    int blockerCount = readCount(
                            buffer, NetworkLimits.MAX_CLASS_PREVIEW_BLOCKERS, "class preview blocker");
                    var blockers = new ArrayList<String>(blockerCount);
                    for (int index = 0; index < blockerCount; index++) {
                        blockers.add(buffer.readUtf(NetworkLimits.MAX_RESYNC_REASON_BYTES));
                    }
                    return new ClassChangePreview(
                            sessionId, requestId, definitionGeneration, semanticDigest, stateRevision,
                            intentType, classId, replacement, affected, costs, previewDigest, blockers);
                }
        );

        public ClassChangePreview {
            Objects.requireNonNull(sessionId, "sessionId");
            if (requestId < 0 || definitionGeneration < 0 || stateRevision < 0) {
                throw new IllegalArgumentException("Class preview revisions must not be negative");
            }
            semanticDigest = NetworkLimits.requireDigest(semanticDigest, "semanticDigest");
            Objects.requireNonNull(intentType, "intentType");
            boolean swap = intentType == IntentType.CLASS_SWAP_PREVIEW;
            if (intentType != IntentType.CLASS_RESPEC_PREVIEW && !swap) {
                throw new IllegalArgumentException("Class preview intent type is invalid");
            }
            classId = StableId.requireValid(classId);
            replacementClassId = Objects.requireNonNull(replacementClassId, "replacementClassId")
                    .map(StableId::requireValid);
            if (swap != replacementClassId.isPresent()
                    || replacementClassId.filter(classId::equals).isPresent()) {
                throw new IllegalArgumentException("Class preview replacement shape is invalid");
            }
            Objects.requireNonNull(affectedClasses, "affectedClasses");
            if (affectedClasses.size() > NetworkLimits.MAX_CLASS_PREVIEW_CLASSES) {
                throw new IllegalArgumentException("Class preview affected class count exceeds capacity");
            }
            var orderedClasses = new LinkedHashSet<ResourceLocation>();
            for (ResourceLocation affected : affectedClasses) {
                if (!orderedClasses.add(StableId.requireValid(affected))) {
                    throw new IllegalArgumentException("Class preview contains duplicate affected classes");
                }
            }
            affectedClasses = List.copyOf(orderedClasses);
            Objects.requireNonNull(costBalances, "costBalances");
            if (costBalances.size() > NetworkLimits.MAX_CLASS_PREVIEW_BALANCES) {
                throw new IllegalArgumentException("Class preview balance count exceeds capacity");
            }
            var sortedCosts = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
            costBalances.forEach((currency, amount) -> {
                if (amount == null || amount < 0) {
                    throw new IllegalArgumentException("Class preview cost balance is invalid");
                }
                sortedCosts.put(StableId.requireValid(currency), amount);
            });
            costBalances = Collections.unmodifiableMap(new LinkedHashMap<>(sortedCosts));
            previewDigest = NetworkLimits.requireDigest(previewDigest, "previewDigest");
            Objects.requireNonNull(blockers, "blockers");
            if (blockers.size() > NetworkLimits.MAX_CLASS_PREVIEW_BLOCKERS) {
                throw new IllegalArgumentException("Class preview blocker count exceeds capacity");
            }
            blockers = blockers.stream().map(blocker -> NetworkLimits.requireBoundedText(
                    blocker, NetworkLimits.MAX_RESYNC_REASON_BYTES, "class preview blocker")).toList();
        }

        public boolean allowed() {
            return blockers.isEmpty();
        }

        @Override
        public Type<ClassChangePreview> type() {
            return TYPE;
        }
    }

    public enum IntentType {
        NOOP_TEST,
        TREE_BUY,
        TREE_REFUND_PREVIEW,
        TREE_REFUND_CONFIRM,
        CLASS_SELECT,
        CLASS_RESPEC_PREVIEW,
        CLASS_RESPEC_CONFIRM,
        CLASS_SWAP_PREVIEW,
        CLASS_SWAP_CONFIRM,
        ABILITY_ASSIGN,
        ABILITY_UNASSIGN,
        ABILITY_SELECT,
        ABILITY_TOGGLE,
        ABILITY_ACTIVATE
    }

    public enum IntentStatus {
        ACCEPTED,
        STALE_SESSION,
        STALE_DEFINITION,
        STALE_STATE,
        TOO_OLD,
        FUTURE_JUMP,
        RATE_LIMITED,
        INVALID
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, path));
    }

    private static void writeEnum(FriendlyByteBuf buffer, Enum<?> value) {
        buffer.writeVarInt(value.ordinal());
    }

    private static <E extends Enum<E>> E readEnum(FriendlyByteBuf buffer, E[] values, String name) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Unknown " + name + " ordinal " + ordinal);
        }
        return values[ordinal];
    }

    private static void writeId(FriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeUtf(id.toString(), NetworkLimits.MAX_KEY_BYTES);
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        ResourceLocation id = ResourceLocation.tryParse(buffer.readUtf(NetworkLimits.MAX_KEY_BYTES));
        if (id == null) {
            throw new IllegalArgumentException("Invalid network resource location");
        }
        return StableId.requireValid(id);
    }

    private static int readCount(FriendlyByteBuf buffer, int maximum, String name) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(name + " count exceeds capacity");
        }
        return count;
    }
}
