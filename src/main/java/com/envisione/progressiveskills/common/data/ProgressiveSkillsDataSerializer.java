package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKind;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.PersistedTransactionState;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Raw-first attachment serializer that quarantines unsafe input before typed gameplay decode. */
public final class ProgressiveSkillsDataSerializer
        implements IAttachmentSerializer<CompoundTag, ProgressiveSkillsData> {
    private static final Set<String> KNOWN_ROOT_FIELDS = Set.of(
            "data_version",
            "player_id",
            "storage_revision",
            "status",
            "transaction",
            "state_definition",
            "definition_states",
            "orphans",
            "operation_receipts",
            "death_marker",
            "migration_shadow",
            "quarantine",
            "extensions"
    );

    @Override
    public ProgressiveSkillsData read(
            IAttachmentHolder holder,
            CompoundTag tag,
            HolderLookup.Provider provider
    ) {
        return decode(holderPlayerId(holder), tag);
    }

    @Override
    public CompoundTag write(ProgressiveSkillsData attachment, HolderLookup.Provider provider) {
        CompoundTag encoded = encodeView(attachment.viewForSave());
        NbtDataLimits.rejection(encoded).ifPresent(reason -> {
            throw new IllegalStateException("Refusing to serialize oversized player data: " + reason);
        });
        return encoded;
    }

    public static CompoundTag encode(ProgressiveSkillsData data) {
        CompoundTag encoded = encodeView(data.view());
        NbtDataLimits.rejection(encoded).ifPresent(reason -> {
            throw new IllegalStateException("Refusing to export oversized player data: " + reason);
        });
        return encoded;
    }

    public static Optional<String> transactionStateRejection(
            UUID playerId,
            PersistedTransactionState transactionState,
            DefinitionRevision definition
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(transactionState, "transactionState");
        Objects.requireNonNull(definition, "definition");
        return replacementRejection(ProgressiveSkillsData.empty(playerId), transactionState, definition);
    }

    public static Optional<String> replacementRejection(
            ProgressiveSkillsData data,
            PersistedTransactionState transactionState,
            DefinitionRevision definition
    ) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(transactionState, "transactionState");
        Objects.requireNonNull(definition, "definition");
        ProgressiveSkillsDataView current = data.view();
        if (current.status() != PlayerDataStatus.ACTIVE) {
            return Optional.of("Quarantined player data cannot accept transaction state");
        }
        long storageRevision;
        try {
            storageRevision = current.transactionState().equals(transactionState)
                    && current.stateDefinition().equals(Optional.of(definition))
                    ? current.storageRevision()
                    : Math.addExact(current.storageRevision(), 1);
            if (current.migrationShadow()
                    .map(MigrationShadow::status)
                    .filter(status -> status == MigrationShadowStatus.PERSISTED_AFTER_LOGIN)
                    .isPresent()) {
                Math.addExact(storageRevision, 1);
            }
        } catch (ArithmeticException exception) {
            return Optional.of("Player data storage revision would overflow");
        }
        Optional<MigrationShadow> migrationShadow = current.migrationShadow().map(shadow ->
                shadow.status() == MigrationShadowStatus.FRESH ? shadow.persistedAfterLogin() : shadow);
        var candidate = new ProgressiveSkillsDataView(
                current.playerId(),
                storageRevision,
                current.status(),
                transactionState,
                Optional.of(definition),
                current.definitionStates(),
                current.orphans(),
                current.operationReceipts(),
                current.deathMarker(),
                migrationShadow,
                current.quarantine(),
                current.unknownExtensions()
        );
        return NbtDataLimits.rejection(encodeView(candidate));
    }

    public static ProgressiveSkillsData decode(UUID holderPlayerId, CompoundTag rawInput) {
        CompoundTag raw = rawInput == null ? new CompoundTag() : rawInput.copy();
        Optional<String> limitRejection = NbtDataLimits.rejection(raw);
        if (limitRejection.isPresent()) {
            return quarantine(holderPlayerId, raw, sourceVersion(raw), limitRejection.orElseThrow(), false);
        }
        int sourceVersion = sourceVersion(raw);
        if (sourceVersion < 1) {
            return quarantine(holderPlayerId, raw, sourceVersion, "Missing or unsupported player data version", true);
        }
        if (sourceVersion > ProgressiveSkillsData.CURRENT_DATA_VERSION) {
            return quarantine(holderPlayerId, raw, sourceVersion,
                    "Future player data version " + sourceVersion + " cannot be decoded safely", true);
        }

        try {
            CompoundTag current = sourceVersion == ProgressiveSkillsData.CURRENT_DATA_VERSION
                    ? raw : PlayerDataMigrations.migrateToCurrent(raw, sourceVersion);
            ProgressiveSkillsDataView decoded = decodeCurrent(current);
            if (!decoded.playerId().equals(holderPlayerId)) {
                return quarantine(holderPlayerId, raw, sourceVersion,
                        "Stored player UUID does not match the attachment holder", true);
            }
            if (sourceVersion < ProgressiveSkillsData.CURRENT_DATA_VERSION) {
                decoded = withMigrationShadow(decoded, new MigrationShadow(
                        sourceVersion,
                        MigrationShadowStatus.FRESH,
                        raw
                ));
                if (NbtDataLimits.rejection(encodeView(decoded)).isPresent()) {
                    return quarantine(holderPlayerId, raw, sourceVersion,
                            "Migrated player data and its safety shadow exceed the encoded attachment ceiling", false);
                }
            }
            return ProgressiveSkillsData.fromView(decoded);
        } catch (RuntimeException exception) {
            return quarantine(holderPlayerId, raw, sourceVersion, safeMessage(exception), true);
        }
    }

    private static ProgressiveSkillsDataView decodeCurrent(CompoundTag tag) {
        int version = requiredInt(tag, "data_version");
        if (version != ProgressiveSkillsData.CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException("Expected current player data version");
        }
        UUID playerId = requiredUuid(tag, "player_id");
        PlayerDataStatus status = requiredEnum(tag, "status", PlayerDataStatus.class);
        CompoundTag transaction = tag.contains("transaction", Tag.TAG_COMPOUND)
                ? tag.getCompound("transaction") : new CompoundTag();
        Optional<DefinitionRevision> stateDefinition = tag.contains("state_definition", Tag.TAG_COMPOUND)
                ? Optional.of(TransactionStateNbtCodec.decodeDefinitionRevision(tag.getCompound("state_definition")))
                : Optional.empty();
        Optional<DeathMarker> deathMarker = tag.contains("death_marker", Tag.TAG_COMPOUND)
                ? Optional.of(decodeDeathMarker(tag.getCompound("death_marker"))) : Optional.empty();
        Optional<MigrationShadow> migrationShadow = tag.contains("migration_shadow", Tag.TAG_COMPOUND)
                ? Optional.of(decodeMigrationShadow(tag.getCompound("migration_shadow"))) : Optional.empty();
        Optional<QuarantineRecord> quarantine = tag.contains("quarantine", Tag.TAG_COMPOUND)
                ? Optional.of(decodeQuarantine(tag.getCompound("quarantine"))) : Optional.empty();
        return new ProgressiveSkillsDataView(
                playerId,
                optionalLong(tag, "storage_revision", 0),
                status,
                TransactionStateNbtCodec.decode(transaction),
                stateDefinition,
                decodeDefinitionStates(optionalList(tag, "definition_states")),
                decodeOrphans(optionalList(tag, "orphans")),
                decodeOperationReceipts(optionalList(tag, "operation_receipts")),
                deathMarker,
                migrationShadow,
                quarantine,
                collectExtensions(tag)
        );
    }

    private static CompoundTag encodeView(ProgressiveSkillsDataView view) {
        var tag = new CompoundTag();
        if (!view.unknownExtensions().isEmpty()) {
            tag.put("extensions", view.unknownExtensions());
        }
        tag.putInt("data_version", ProgressiveSkillsData.CURRENT_DATA_VERSION);
        tag.putString("player_id", view.playerId().toString());
        tag.putLong("storage_revision", view.storageRevision());
        tag.putString("status", view.status().name());
        tag.put("transaction", TransactionStateNbtCodec.encode(view.transactionState()));
        view.stateDefinition().ifPresent(definition ->
                tag.put("state_definition", TransactionStateNbtCodec.encodeDefinitionRevision(definition)));
        tag.put("definition_states", encodeDefinitionStates(view.definitionStates()));
        tag.put("orphans", encodeOrphans(view.orphans()));
        tag.put("operation_receipts", encodeOperationReceipts(view.operationReceipts()));
        view.deathMarker().ifPresent(marker -> tag.put("death_marker", encodeDeathMarker(marker)));
        view.migrationShadow().ifPresent(shadow -> tag.put("migration_shadow", encodeMigrationShadow(shadow)));
        view.quarantine().ifPresent(quarantine -> tag.put("quarantine", encodeQuarantine(quarantine)));
        return tag;
    }

    private static ProgressiveSkillsDataView withMigrationShadow(
            ProgressiveSkillsDataView view,
            MigrationShadow shadow
    ) {
        return new ProgressiveSkillsDataView(
                view.playerId(),
                view.storageRevision(),
                view.status(),
                view.transactionState(),
                view.stateDefinition(),
                view.definitionStates(),
                view.orphans(),
                view.operationReceipts(),
                view.deathMarker(),
                Optional.of(shadow),
                view.quarantine(),
                view.unknownExtensions()
        );
    }

    private static ProgressiveSkillsData quarantine(
            UUID holderPlayerId,
            CompoundTag raw,
            int version,
            String reason,
            boolean preserveRaw
    ) {
        var record = new QuarantineRecord(
                Math.max(-1, version),
                boundedReason(reason),
                NbtDataLimits.digest(raw),
                preserveRaw ? raw : new CompoundTag()
        );
        ProgressiveSkillsData quarantined = ProgressiveSkillsData.quarantined(holderPlayerId, record);
        if (preserveRaw && NbtDataLimits.rejection(encodeView(quarantined.view())).isPresent()) {
            record = new QuarantineRecord(
                    Math.max(-1, version),
                    boundedReason(reason + "; raw evidence exceeded the embedded quarantine ceiling"),
                    record.rawDigest(),
                    new CompoundTag()
            );
            quarantined = ProgressiveSkillsData.quarantined(holderPlayerId, record);
        }
        return quarantined;
    }

    private static ListTag encodeDefinitionStates(Map<DefinitionKey, StoredDefinitionState> states) {
        var list = new ListTag();
        states.values().forEach(state -> list.add(encodeDefinitionState(state)));
        return list;
    }

    private static Map<DefinitionKey, StoredDefinitionState> decodeDefinitionStates(ListTag list) {
        var states = new TreeMap<DefinitionKey, StoredDefinitionState>();
        for (int index = 0; index < list.size(); index++) {
            StoredDefinitionState state = decodeDefinitionState(compoundAt(list, index));
            if (states.putIfAbsent(state.key(), state) != null) {
                throw new IllegalArgumentException("Duplicate stored definition state: " + state.key());
            }
        }
        if (states.size() > ProgressiveSkillsData.MAX_DEFINITION_STATES) {
            throw new IllegalArgumentException("Stored definition-state count exceeds capacity");
        }
        return states;
    }

    private static CompoundTag encodeDefinitionState(StoredDefinitionState state) {
        var tag = encodeDefinitionKey(state.key());
        tag.putString("lineage", state.lineage());
        tag.putString("origin_lineage", state.originLineage());
        tag.putInt("payload_version", state.payloadVersion());
        tag.put("payload", state.payload());
        return tag;
    }

    private static StoredDefinitionState decodeDefinitionState(CompoundTag tag) {
        return new StoredDefinitionState(
                decodeDefinitionKey(tag),
                requiredString(tag, "lineage"),
                requiredString(tag, "origin_lineage"),
                requiredInt(tag, "payload_version"),
                requiredCompound(tag, "payload")
        );
    }

    private static ListTag encodeOrphans(Map<DefinitionKey, OrphanRecord> orphans) {
        var list = new ListTag();
        orphans.values().forEach(orphan -> {
            var tag = new CompoundTag();
            tag.put("state", encodeDefinitionState(orphan.state()));
            tag.putString("reason", orphan.reason());
            list.add(tag);
        });
        return list;
    }

    private static Map<DefinitionKey, OrphanRecord> decodeOrphans(ListTag list) {
        var orphans = new TreeMap<DefinitionKey, OrphanRecord>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = compoundAt(list, index);
            var orphan = new OrphanRecord(
                    decodeDefinitionState(requiredCompound(tag, "state")),
                    requiredString(tag, "reason")
            );
            if (orphans.putIfAbsent(orphan.state().key(), orphan) != null) {
                throw new IllegalArgumentException("Duplicate orphan definition: " + orphan.state().key());
            }
        }
        if (orphans.size() > ProgressiveSkillsData.MAX_ORPHANS) {
            throw new IllegalArgumentException("Orphan count exceeds capacity");
        }
        return orphans;
    }

    private static ListTag encodeOperationReceipts(Map<UUID, OperationReceipt> receipts) {
        var list = new ListTag();
        receipts.values().forEach(receipt -> {
            var tag = new CompoundTag();
            tag.putString("operation_id", receipt.operationId().toString());
            tag.putString("operation_type", receipt.operationType());
            tag.putString("transaction_id", receipt.transactionId().toString());
            tag.putLong("resulting_revision", receipt.resultingStateRevision());
            tag.putLong("applied_at", receipt.appliedAt().toEpochMilli());
            tag.putString("detail", receipt.detail());
            list.add(tag);
        });
        return list;
    }

    private static Map<UUID, OperationReceipt> decodeOperationReceipts(ListTag list) {
        var receipts = new TreeMap<UUID, OperationReceipt>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = compoundAt(list, index);
            UUID id = requiredUuid(tag, "operation_id");
            var receipt = new OperationReceipt(
                    id,
                    requiredString(tag, "operation_type"),
                    new TransactionId(requiredUuid(tag, "transaction_id")),
                    requiredLong(tag, "resulting_revision"),
                    requiredInstant(tag, "applied_at"),
                    requiredString(tag, "detail")
            );
            if (receipts.putIfAbsent(id, receipt) != null) {
                throw new IllegalArgumentException("Duplicate operation receipt: " + id);
            }
        }
        if (receipts.size() > ProgressiveSkillsData.MAX_OPERATION_RECEIPTS) {
            throw new IllegalArgumentException("Operation-receipt count exceeds capacity");
        }
        return receipts;
    }

    private static CompoundTag encodeDeathMarker(DeathMarker marker) {
        var tag = new CompoundTag();
        tag.putString("transaction_id", marker.transactionId().toString());
        tag.putLong("created_at", marker.createdAt().toEpochMilli());
        tag.putBoolean("keep_inventory", marker.keepInventory());
        return tag;
    }

    private static DeathMarker decodeDeathMarker(CompoundTag tag) {
        return new DeathMarker(
                requiredUuid(tag, "transaction_id"),
                requiredInstant(tag, "created_at"),
                optionalBoolean(tag, "keep_inventory", false)
        );
    }

    private static CompoundTag encodeMigrationShadow(MigrationShadow shadow) {
        var tag = new CompoundTag();
        tag.putInt("source_version", shadow.sourceVersion());
        tag.putString("status", shadow.status().name());
        tag.put("raw_data", shadow.rawData());
        return tag;
    }

    private static MigrationShadow decodeMigrationShadow(CompoundTag tag) {
        return new MigrationShadow(
                requiredInt(tag, "source_version"),
                requiredEnum(tag, "status", MigrationShadowStatus.class),
                requiredCompound(tag, "raw_data")
        );
    }

    private static CompoundTag encodeQuarantine(QuarantineRecord quarantine) {
        var tag = new CompoundTag();
        tag.putInt("source_version", quarantine.sourceVersion());
        tag.putString("reason", quarantine.reason());
        tag.putString("raw_digest", quarantine.rawDigest());
        tag.put("raw_data", quarantine.rawData());
        return tag;
    }

    private static QuarantineRecord decodeQuarantine(CompoundTag tag) {
        return new QuarantineRecord(
                requiredInt(tag, "source_version"),
                requiredString(tag, "reason"),
                requiredString(tag, "raw_digest"),
                requiredCompound(tag, "raw_data")
        );
    }

    private static CompoundTag encodeDefinitionKey(DefinitionKey key) {
        var tag = new CompoundTag();
        tag.putString("kind", key.kind().id().toString());
        tag.putString("kind_directory", key.kind().sourceDirectory());
        tag.putString("id", key.id().toString());
        return tag;
    }

    private static DefinitionKey decodeDefinitionKey(CompoundTag tag) {
        ResourceLocation kindId = requiredId(tag, "kind");
        DefinitionKind kind = DefinitionKinds.all().stream()
                .filter(candidate -> candidate.id().equals(kindId))
                .findFirst()
                .orElseGet(() -> new DefinitionKind(kindId, requiredString(tag, "kind_directory")));
        return new DefinitionKey(kind, requiredId(tag, "id"));
    }

    private static CompoundTag collectExtensions(CompoundTag root) {
        CompoundTag extensions = root.contains("extensions", Tag.TAG_COMPOUND)
                ? root.getCompound("extensions").copy() : new CompoundTag();
        for (String key : root.getAllKeys()) {
            if (!KNOWN_ROOT_FIELDS.contains(key)) {
                Tag value = root.get(key);
                if (value != null) {
                    extensions.put(key, value.copy());
                }
            }
        }
        return extensions;
    }

    private static int sourceVersion(CompoundTag tag) {
        return tag.contains("data_version", Tag.TAG_INT) ? tag.getInt("data_version") : -1;
    }

    private static UUID holderPlayerId(IAttachmentHolder holder) {
        if (holder instanceof Player player) {
            return player.getUUID();
        }
        throw new IllegalArgumentException("ProgressiveSkills player data can only attach to a player");
    }

    private static CompoundTag requiredCompound(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing compound field: " + key);
        }
        return parent.getCompound(key);
    }

    private static ListTag optionalList(CompoundTag parent, String key) {
        return parent.contains(key, Tag.TAG_LIST) ? parent.getList(key, Tag.TAG_COMPOUND) : new ListTag();
    }

    private static CompoundTag compoundAt(ListTag list, int index) {
        if (!(list.get(index) instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("Expected compound at list index " + index);
        }
        return compound;
    }

    private static String requiredString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing string field: " + key);
        }
        return tag.getString(key);
    }

    private static int requiredInt(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Missing integer field: " + key);
        }
        return tag.getInt(key);
    }

    private static long requiredLong(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing long field: " + key);
        }
        return tag.getLong(key);
    }

    private static long optionalLong(CompoundTag tag, String key, long fallback) {
        return tag.contains(key, Tag.TAG_LONG) ? tag.getLong(key) : fallback;
    }

    private static boolean optionalBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_BYTE) ? tag.getBoolean(key) : fallback;
    }

    private static ResourceLocation requiredId(CompoundTag tag, String key) {
        ResourceLocation id = ResourceLocation.tryParse(requiredString(tag, key));
        if (id == null) {
            throw new IllegalArgumentException("Invalid resource location field: " + key);
        }
        return id;
    }

    private static UUID requiredUuid(CompoundTag tag, String key) {
        try {
            return UUID.fromString(requiredString(tag, key));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid UUID field: " + key, exception);
        }
    }

    private static Instant requiredInstant(CompoundTag tag, String key) {
        try {
            return Instant.ofEpochMilli(requiredLong(tag, key));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid timestamp field: " + key, exception);
        }
    }

    private static <E extends Enum<E>> E requiredEnum(CompoundTag tag, String key, Class<E> type) {
        try {
            return Enum.valueOf(type, requiredString(tag, key));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid enum field: " + key, exception);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static String boundedReason(String reason) {
        String normalized = reason == null || reason.isBlank() ? "Unknown player-data decode failure" : reason.strip();
        return normalized.substring(0, Math.min(normalized.length(), QuarantineRecord.MAX_REASON_LENGTH));
    }
}
