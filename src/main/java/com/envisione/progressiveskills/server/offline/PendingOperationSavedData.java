package com.envisione.progressiveskills.server.offline;

import com.envisione.progressiveskills.common.data.NbtDataLimits;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Bounded overworld queue; it never directly opens or rewrites an offline player NBT file. */
public final class PendingOperationSavedData extends SavedData {
    public static final String FILE_ID = "progressiveskills_pending_operations";
    public static final int DATA_VERSION = 1;
    public static final int MAX_OPERATIONS = 1_024;
    public static final int MAX_OPERATIONS_PER_PLAYER = 64;

    private final Map<UUID, PendingProgressionOperation> operations = new TreeMap<>();
    private Optional<String> storeQuarantine = Optional.empty();

    public static SavedData.Factory<PendingOperationSavedData> factory() {
        return new SavedData.Factory<>(PendingOperationSavedData::new, PendingOperationSavedData::load);
    }

    public static PendingOperationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public synchronized void queue(PendingProgressionOperation operation) {
        if (storeQuarantine.isPresent()) {
            throw new IllegalStateException("Pending-operation store is quarantined");
        }
        if (operation.status() != PendingOperationStatus.PENDING) {
            throw new IllegalArgumentException("Only pending operations may be newly queued");
        }
        if (!operations.containsKey(operation.operationId()) && operations.size() >= MAX_OPERATIONS) {
            throw new IllegalStateException("Pending-operation store capacity is full");
        }
        long targetCount = operations.values().stream()
                .filter(existing -> existing.targetId().equals(operation.targetId()))
                .count();
        if (!operations.containsKey(operation.operationId()) && targetCount >= MAX_OPERATIONS_PER_PLAYER) {
            throw new IllegalStateException("Pending-operation per-player capacity is full");
        }
        PendingProgressionOperation existing = operations.putIfAbsent(operation.operationId(), operation);
        if (existing != null && !existing.equals(operation)) {
            throw new IllegalStateException("Pending-operation identity collision");
        }
        if (existing == null) {
            setDirty();
        }
    }

    public synchronized List<PendingProgressionOperation> pendingFor(UUID targetId) {
        return operations.values().stream()
                .filter(operation -> operation.targetId().equals(targetId))
                .filter(operation -> operation.status() == PendingOperationStatus.PENDING)
                .sorted()
                .toList();
    }

    public synchronized List<PendingProgressionOperation> all() {
        return operations.values().stream().sorted().toList();
    }

    public synchronized Optional<String> storeQuarantine() {
        return storeQuarantine;
    }

    public synchronized void recordAttempt(UUID operationId, UUID attemptId) {
        replace(operationId, require(operationId).attempted(attemptId));
    }

    public synchronized void rebase(UUID operationId, DefinitionRevision revision, ResourceLocation balanceId) {
        replace(operationId, require(operationId).rebase(revision, balanceId));
    }

    public synchronized void quarantine(UUID operationId, String reason) {
        replace(operationId, require(operationId).quarantine(reason));
    }

    public synchronized void consume(UUID operationId) {
        if (operations.remove(operationId) != null) {
            setDirty();
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag output, HolderLookup.Provider provider) {
        output.putInt("data_version", DATA_VERSION);
        storeQuarantine.ifPresent(reason -> output.putString("store_quarantine", reason));
        var list = new ListTag();
        operations.values().stream().sorted().forEach(operation -> list.add(encode(operation)));
        output.put("operations", list);
        NbtDataLimits.rejection(output).ifPresent(reason -> {
            throw new IllegalStateException("Refusing to serialize oversized pending-operation data: " + reason);
        });
        return output;
    }

    static PendingOperationSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        var result = new PendingOperationSavedData();
        Optional<String> rejection = NbtDataLimits.rejection(input);
        if (rejection.isPresent()) {
            result.storeQuarantine = Optional.of(rejection.orElseThrow());
            return result;
        }
        try {
            if (!input.contains("data_version", Tag.TAG_INT) || input.getInt("data_version") != DATA_VERSION) {
                throw new IllegalArgumentException("Unsupported pending-operation data version");
            }
            if (input.contains("store_quarantine", Tag.TAG_STRING)) {
                result.storeQuarantine = Optional.of(input.getString("store_quarantine"));
            }
            ListTag list = input.contains("operations", Tag.TAG_LIST)
                    ? input.getList("operations", Tag.TAG_COMPOUND) : new ListTag();
            if (list.size() > MAX_OPERATIONS) {
                throw new IllegalArgumentException("Pending-operation count exceeds capacity");
            }
            for (int index = 0; index < list.size(); index++) {
                if (!(list.get(index) instanceof CompoundTag tag)) {
                    throw new IllegalArgumentException("Pending-operation list contains a non-compound entry");
                }
                PendingProgressionOperation operation = decode(tag);
                if (result.operations.putIfAbsent(operation.operationId(), operation) != null) {
                    throw new IllegalArgumentException("Duplicate pending-operation identity");
                }
            }
            validatePerPlayerCounts(result.operations.values());
        } catch (RuntimeException exception) {
            result.operations.clear();
            result.storeQuarantine = Optional.of(boundedReason(exception));
        }
        return result;
    }

    private synchronized PendingProgressionOperation require(UUID operationId) {
        PendingProgressionOperation operation = operations.get(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("Unknown pending operation: " + operationId);
        }
        return operation;
    }

    private synchronized void replace(UUID operationId, PendingProgressionOperation replacement) {
        if (!replacement.operationId().equals(operationId) || !operations.containsKey(operationId)) {
            throw new IllegalArgumentException("Cannot replace unknown or mismatched pending operation");
        }
        operations.put(operationId, replacement);
        setDirty();
    }

    private static CompoundTag encode(PendingProgressionOperation operation) {
        var tag = new CompoundTag();
        tag.putString("operation_id", operation.operationId().toString());
        tag.putString("target_id", operation.targetId().toString());
        tag.putString("issuer_id", operation.issuerId().toString());
        tag.putLong("created_at", operation.createdAt().toEpochMilli());
        tag.putLong("expires_at", operation.expiresAt().toEpochMilli());
        tag.putLong("definition_generation", operation.definitionRevision().generation());
        tag.putString("definition_digest", operation.definitionRevision().semanticDigest());
        tag.putString("balance_id", operation.balanceId().toString());
        tag.putLong("delta", operation.delta());
        tag.putLong("minimum", operation.minimum());
        tag.putLong("maximum", operation.maximum());
        tag.putBoolean("reward_eligible", operation.rewardEligible());
        tag.putString("status", operation.status().name());
        operation.lastAttemptId().ifPresent(value -> tag.putString("last_attempt_id", value.toString()));
        operation.quarantineReason().ifPresent(value -> tag.putString("quarantine_reason", value));
        return tag;
    }

    private static PendingProgressionOperation decode(CompoundTag tag) {
        ResourceLocation balanceId = ResourceLocation.tryParse(requiredString(tag, "balance_id"));
        if (balanceId == null) {
            throw new IllegalArgumentException("Invalid pending-operation balance id");
        }
        return new PendingProgressionOperation(
                requiredUuid(tag, "operation_id"),
                requiredUuid(tag, "target_id"),
                requiredUuid(tag, "issuer_id"),
                requiredInstant(tag, "created_at"),
                requiredInstant(tag, "expires_at"),
                new DefinitionRevision(
                        requiredLong(tag, "definition_generation"),
                        requiredString(tag, "definition_digest")
                ),
                balanceId,
                requiredLong(tag, "delta"),
                requiredLong(tag, "minimum"),
                requiredLong(tag, "maximum"),
                optionalBoolean(tag, "reward_eligible", false),
                requiredEnum(tag, "status", PendingOperationStatus.class),
                optionalUuid(tag, "last_attempt_id"),
                optionalString(tag, "quarantine_reason")
        );
    }

    private static void validatePerPlayerCounts(Iterable<PendingProgressionOperation> operations) {
        var counts = new TreeMap<UUID, Integer>();
        for (PendingProgressionOperation operation : operations) {
            int count = counts.merge(operation.targetId(), 1, Integer::sum);
            if (count > MAX_OPERATIONS_PER_PLAYER) {
                throw new IllegalArgumentException("Pending-operation per-player count exceeds capacity");
            }
        }
    }

    private static String requiredString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing pending-operation string field: " + key);
        }
        return tag.getString(key);
    }

    private static long requiredLong(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing pending-operation long field: " + key);
        }
        return tag.getLong(key);
    }

    private static boolean optionalBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_BYTE) ? tag.getBoolean(key) : fallback;
    }

    private static UUID requiredUuid(CompoundTag tag, String key) {
        try {
            return UUID.fromString(requiredString(tag, key));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid pending-operation UUID: " + key, exception);
        }
    }

    private static Optional<UUID> optionalUuid(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_STRING) ? Optional.of(requiredUuid(tag, key)) : Optional.empty();
    }

    private static Optional<String> optionalString(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_STRING) ? Optional.of(tag.getString(key)) : Optional.empty();
    }

    private static Instant requiredInstant(CompoundTag tag, String key) {
        try {
            return Instant.ofEpochMilli(requiredLong(tag, key));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid pending-operation timestamp: " + key, exception);
        }
    }

    private static <E extends Enum<E>> E requiredEnum(CompoundTag tag, String key, Class<E> type) {
        try {
            return Enum.valueOf(type, requiredString(tag, key));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid pending-operation enum: " + key, exception);
        }
    }

    private static String boundedReason(RuntimeException exception) {
        String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return reason.substring(0, Math.min(reason.length(), PendingProgressionOperation.MAX_QUARANTINE_REASON));
    }
}
