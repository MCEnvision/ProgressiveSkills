package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.common.transaction.ActionDisposition;
import com.envisione.progressiveskills.common.transaction.AuditRecord;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.DeliveryContract;
import com.envisione.progressiveskills.common.transaction.EntitlementContribution;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantReceipt;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.PersistedTransactionState;
import com.envisione.progressiveskills.common.transaction.ProjectionChange;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ReceiptKey;
import com.envisione.progressiveskills.common.transaction.RepeatPolicy;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.common.transaction.TransactionStatus;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionResult;
import com.envisione.progressiveskills.common.transaction.TransitionFailurePolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.UUID;

/** Strict deterministic NBT codec for the exact durable Phase 4 transaction state. */
final class TransactionStateNbtCodec {
    private TransactionStateNbtCodec() {
    }

    static CompoundTag encode(PersistedTransactionState state) {
        var tag = new CompoundTag();
        tag.putLong("state_revision", state.stateRevision());
        tag.put("balances", encodeBalances(state.balances()));
        tag.put("ownership", encodeOwnership(state.ownership()));
        tag.put("receipts", encodeReceipts(state.receipts()));
        tag.put("idempotency", encodeIdempotency(state.idempotencyResults()));
        tag.put("audit", encodeAudit(state.auditRecords()));
        return tag;
    }

    static PersistedTransactionState decode(CompoundTag tag) {
        long revision = optionalLong(tag, "state_revision", 0);
        if (revision < 0) {
            throw new IllegalArgumentException("Persisted transaction revision must not be negative");
        }
        return new PersistedTransactionState(
                revision,
                decodeBalances(optionalList(tag, "balances")),
                decodeOwnership(optionalList(tag, "ownership")),
                decodeReceipts(optionalList(tag, "receipts")),
                decodeIdempotency(optionalList(tag, "idempotency")),
                decodeAudit(optionalList(tag, "audit"))
        );
    }

    static CompoundTag encodeDefinitionRevision(DefinitionRevision revision) {
        var tag = new CompoundTag();
        tag.putLong("generation", revision.generation());
        tag.putString("digest", revision.semanticDigest());
        return tag;
    }

    static DefinitionRevision decodeDefinitionRevision(CompoundTag tag) {
        return new DefinitionRevision(requiredLong(tag, "generation"), requiredString(tag, "digest"));
    }

    private static ListTag encodeBalances(Map<ResourceLocation, Long> balances) {
        var list = new ListTag();
        balances.forEach((id, value) -> {
            var entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putLong("value", value);
            list.add(entry);
        });
        return list;
    }

    private static Map<ResourceLocation, Long> decodeBalances(ListTag list) {
        var balances = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = compoundAt(list, index);
            ResourceLocation id = requiredId(entry, "id");
            long value = requiredLong(entry, "value");
            if (value == 0 || balances.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("Invalid or duplicate persisted balance: " + id);
            }
        }
        return balances;
    }

    private static ListTag encodeOwnership(
            Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership
    ) {
        var list = new ListTag();
        ownership.forEach((key, owners) -> {
            var entry = encodeEntitlementKey(key);
            var ownerList = new ListTag();
            owners.forEach((source, contribution) -> {
                var owner = encodeGrantSource(source);
                owner.putLong("value", contribution.value());
                owner.putString("resolver", contribution.resolver().name());
                ownerList.add(owner);
            });
            entry.put("owners", ownerList);
            list.add(entry);
        });
        return list;
    }

    private static Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> decodeOwnership(ListTag list) {
        var ownership = new TreeMap<EntitlementKey, Map<GrantSourceId, EntitlementContribution>>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = compoundAt(list, index);
            EntitlementKey key = decodeEntitlementKey(entry);
            var owners = new TreeMap<GrantSourceId, EntitlementContribution>();
            ListTag ownerList = requiredList(entry, "owners");
            for (int ownerIndex = 0; ownerIndex < ownerList.size(); ownerIndex++) {
                CompoundTag owner = compoundAt(ownerList, ownerIndex);
                GrantSourceId source = decodeGrantSource(owner);
                var contribution = new EntitlementContribution(
                        requiredLong(owner, "value"),
                        requiredEnum(owner, "resolver", EntitlementResolver.class)
                );
                if (owners.putIfAbsent(source, contribution) != null) {
                    throw new IllegalArgumentException("Duplicate persisted grant source: " + source);
                }
            }
            if (owners.isEmpty() || ownership.putIfAbsent(key, owners) != null) {
                throw new IllegalArgumentException("Invalid or duplicate persisted entitlement: " + key);
            }
        }
        return ownership;
    }

    private static ListTag encodeReceipts(Map<ReceiptKey, GrantReceipt> receipts) {
        var list = new ListTag();
        receipts.values().forEach(receipt -> {
            var tag = encodeReceiptKey(receipt.key());
            tag.putString("transaction_id", receipt.transactionId().toString());
            tag.put("definition", encodeDefinitionRevision(receipt.definitionRevision()));
            tag.putLong("delivered_at", receipt.deliveredAt().toEpochMilli());
            tag.putString("delivery_contract", receipt.deliveryContract().name());
            tag.putString("detail", receipt.detail());
            list.add(tag);
        });
        return list;
    }

    private static Map<ReceiptKey, GrantReceipt> decodeReceipts(ListTag list) {
        var receipts = new TreeMap<ReceiptKey, GrantReceipt>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = compoundAt(list, index);
            ReceiptKey key = decodeReceiptKey(entry);
            var receipt = new GrantReceipt(
                    key,
                    requiredTransactionId(entry, "transaction_id"),
                    decodeDefinitionRevision(requiredCompound(entry, "definition")),
                    requiredInstant(entry, "delivered_at"),
                    requiredEnum(entry, "delivery_contract", DeliveryContract.class),
                    requiredString(entry, "detail")
            );
            if (receipts.putIfAbsent(key, receipt) != null) {
                throw new IllegalArgumentException("Duplicate persisted receipt: " + key);
            }
        }
        return receipts;
    }

    private static ListTag encodeIdempotency(Map<IdempotencyKey, TransactionResult> results) {
        var list = new ListTag();
        results.forEach((key, result) -> {
            var entry = new CompoundTag();
            entry.putString("key", key.value());
            entry.put("result", encodeTransactionResult(result));
            list.add(entry);
        });
        return list;
    }

    private static Map<IdempotencyKey, TransactionResult> decodeIdempotency(ListTag list) {
        var results = new TreeMap<IdempotencyKey, TransactionResult>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = compoundAt(list, index);
            var key = new IdempotencyKey(requiredString(entry, "key"));
            TransactionResult result = decodeTransactionResult(requiredCompound(entry, "result"));
            if (results.putIfAbsent(key, result) != null) {
                throw new IllegalArgumentException("Duplicate persisted idempotency key: " + key.value());
            }
        }
        return results;
    }

    private static CompoundTag encodeTransactionResult(TransactionResult result) {
        var tag = new CompoundTag();
        tag.putString("transaction_id", result.transactionId().toString());
        tag.putString("status", result.status().name());
        tag.putLong("before_revision", result.beforeRevision());
        tag.putLong("after_revision", result.afterRevision());
        tag.putString("diagnostic_code", result.diagnosticCode());
        tag.putString("message", result.message());
        tag.putBoolean("replayed", result.replayed());
        tag.put("projection_changes", encodeProjectionChanges(result.projectionChanges()));
        tag.put("action_results", encodeActionResults(result.actionResults()));
        return tag;
    }

    private static TransactionResult decodeTransactionResult(CompoundTag tag) {
        return new TransactionResult(
                requiredTransactionId(tag, "transaction_id"),
                requiredEnum(tag, "status", TransactionStatus.class),
                requiredLong(tag, "before_revision"),
                requiredLong(tag, "after_revision"),
                requiredString(tag, "diagnostic_code"),
                requiredString(tag, "message"),
                optionalBoolean(tag, "replayed", false),
                decodeProjectionChanges(optionalList(tag, "projection_changes")),
                decodeActionResults(optionalList(tag, "action_results"))
        );
    }

    private static ListTag encodeAudit(List<AuditRecord> records) {
        var list = new ListTag();
        records.forEach(record -> {
            var tag = new CompoundTag();
            tag.putString("transaction_id", record.transactionId().toString());
            tag.putString("actor_id", record.actorId().toString());
            tag.putString("target_id", record.targetId().toString());
            tag.putString("cause", record.cause().name());
            tag.putString("reason", record.reason());
            tag.put("definition", encodeDefinitionRevision(record.definitionRevision()));
            tag.putLong("completed_at", record.completedAt().toEpochMilli());
            tag.putString("status", record.status().name());
            tag.putLong("before_revision", record.beforeRevision());
            tag.putLong("after_revision", record.afterRevision());
            tag.putBoolean("reversible", record.reversible());
            tag.put("balance_mutations", encodeBalanceMutations(record.balanceMutations()));
            tag.put("projection_changes", encodeProjectionChanges(record.projectionChanges()));
            tag.put("action_results", encodeActionResults(record.actionResults()));
            tag.putString("message", record.message());
            list.add(tag);
        });
        return list;
    }

    private static List<AuditRecord> decodeAudit(ListTag list) {
        var records = new ArrayList<AuditRecord>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = compoundAt(list, index);
            records.add(new AuditRecord(
                    requiredTransactionId(tag, "transaction_id"),
                    requiredUuid(tag, "actor_id"),
                    requiredUuid(tag, "target_id"),
                    requiredEnum(tag, "cause", ProgressionCause.class),
                    requiredString(tag, "reason"),
                    decodeDefinitionRevision(requiredCompound(tag, "definition")),
                    requiredInstant(tag, "completed_at"),
                    requiredEnum(tag, "status", TransactionStatus.class),
                    requiredLong(tag, "before_revision"),
                    requiredLong(tag, "after_revision"),
                    optionalBoolean(tag, "reversible", false),
                    decodeBalanceMutations(optionalList(tag, "balance_mutations")),
                    decodeProjectionChanges(optionalList(tag, "projection_changes")),
                    decodeActionResults(optionalList(tag, "action_results")),
                    requiredString(tag, "message")
            ));
        }
        return List.copyOf(records);
    }

    private static ListTag encodeBalanceMutations(List<BalanceMutation> mutations) {
        var list = new ListTag();
        mutations.forEach(mutation -> {
            var tag = new CompoundTag();
            tag.putString("id", mutation.balanceId().toString());
            tag.putLong("delta", mutation.delta());
            tag.putLong("minimum", mutation.minimum());
            tag.putLong("maximum", mutation.maximum());
            list.add(tag);
        });
        return list;
    }

    private static List<BalanceMutation> decodeBalanceMutations(ListTag list) {
        var mutations = new ArrayList<BalanceMutation>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = compoundAt(list, index);
            mutations.add(new BalanceMutation(
                    requiredId(tag, "id"),
                    requiredLong(tag, "delta"),
                    requiredLong(tag, "minimum"),
                    requiredLong(tag, "maximum")
            ));
        }
        return List.copyOf(mutations);
    }

    private static ListTag encodeProjectionChanges(List<ProjectionChange> changes) {
        var list = new ListTag();
        changes.forEach(change -> {
            var tag = encodeEntitlementKey(change.key());
            if (change.before().isPresent()) {
                tag.putLong("before", change.before().getAsLong());
            }
            if (change.after().isPresent()) {
                tag.putLong("after", change.after().getAsLong());
            }
            list.add(tag);
        });
        return list;
    }

    private static List<ProjectionChange> decodeProjectionChanges(ListTag list) {
        var changes = new ArrayList<ProjectionChange>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = compoundAt(list, index);
            OptionalLong before = tag.contains("before", Tag.TAG_LONG)
                    ? OptionalLong.of(tag.getLong("before")) : OptionalLong.empty();
            OptionalLong after = tag.contains("after", Tag.TAG_LONG)
                    ? OptionalLong.of(tag.getLong("after")) : OptionalLong.empty();
            changes.add(new ProjectionChange(decodeEntitlementKey(tag), before, after));
        }
        return List.copyOf(changes);
    }

    private static ListTag encodeActionResults(List<TransitionActionResult> results) {
        var list = new ListTag();
        results.forEach(result -> {
            var tag = new CompoundTag();
            tag.put("action", encodeAction(result.action()));
            tag.putString("disposition", result.disposition().name());
            tag.putString("detail", result.detail());
            list.add(tag);
        });
        return list;
    }

    private static List<TransitionActionResult> decodeActionResults(ListTag list) {
        var results = new ArrayList<TransitionActionResult>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = compoundAt(list, index);
            results.add(new TransitionActionResult(
                    decodeAction(requiredCompound(tag, "action")),
                    requiredEnum(tag, "disposition", ActionDisposition.class),
                    requiredString(tag, "detail")
            ));
        }
        return List.copyOf(results);
    }

    private static CompoundTag encodeAction(TransitionAction action) {
        var tag = encodeGrantSource(action.source());
        tag.putString("type", action.type().toString());
        tag.putString("payload", action.payload());
        tag.putLong("amount", action.amount());
        tag.putString("repeat_policy", action.repeatPolicy().name());
        tag.putString("delivery_contract", action.deliveryContract().name());
        tag.putString("failure_policy", action.failurePolicy().name());
        return tag;
    }

    private static TransitionAction decodeAction(CompoundTag tag) {
        return new TransitionAction(
                requiredId(tag, "type"),
                decodeGrantSource(tag),
                requiredString(tag, "payload"),
                requiredLong(tag, "amount"),
                requiredEnum(tag, "repeat_policy", RepeatPolicy.class),
                requiredEnum(tag, "delivery_contract", DeliveryContract.class),
                requiredEnum(tag, "failure_policy", TransitionFailurePolicy.class)
        );
    }

    private static CompoundTag encodeEntitlementKey(EntitlementKey key) {
        var tag = new CompoundTag();
        tag.putString("target_type", key.targetType().toString());
        tag.putString("target_id", key.targetId().toString());
        return tag;
    }

    private static EntitlementKey decodeEntitlementKey(CompoundTag tag) {
        return new EntitlementKey(requiredId(tag, "target_type"), requiredId(tag, "target_id"));
    }

    private static CompoundTag encodeGrantSource(GrantSourceId source) {
        var tag = new CompoundTag();
        tag.putString("owner_kind", source.ownerKind().toString());
        tag.putString("owner_id", source.ownerId().toString());
        tag.putString("grant_id", source.grantId().toString());
        return tag;
    }

    private static GrantSourceId decodeGrantSource(CompoundTag tag) {
        return new GrantSourceId(
                requiredId(tag, "owner_kind"),
                requiredId(tag, "owner_id"),
                requiredId(tag, "grant_id")
        );
    }

    private static CompoundTag encodeReceiptKey(ReceiptKey key) {
        var tag = encodeGrantSource(key.source());
        tag.putString("policy", key.policy().name());
        tag.putString("scope", key.scope());
        return tag;
    }

    private static ReceiptKey decodeReceiptKey(CompoundTag tag) {
        return new ReceiptKey(
                decodeGrantSource(tag),
                requiredEnum(tag, "policy", RepeatPolicy.class),
                requiredString(tag, "scope")
        );
    }

    private static CompoundTag requiredCompound(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing compound field: " + key);
        }
        return parent.getCompound(key);
    }

    private static ListTag requiredList(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Missing list field: " + key);
        }
        return parent.getList(key, Tag.TAG_COMPOUND);
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
        ResourceLocation value = ResourceLocation.tryParse(requiredString(tag, key));
        if (value == null) {
            throw new IllegalArgumentException("Invalid resource location field: " + key);
        }
        return value;
    }

    private static UUID requiredUuid(CompoundTag tag, String key) {
        try {
            return UUID.fromString(requiredString(tag, key));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid UUID field: " + key, exception);
        }
    }

    private static TransactionId requiredTransactionId(CompoundTag tag, String key) {
        return new TransactionId(requiredUuid(tag, key));
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
}
