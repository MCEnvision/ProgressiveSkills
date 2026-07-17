package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.common.transaction.AuditRecord;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.PaidCostMutation;
import com.envisione.progressiveskills.common.transaction.PaidCostRecord;
import com.envisione.progressiveskills.common.transaction.PersistedTransactionState;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.PurchaseInstanceId;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransactionStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaidCostNbtCodecTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001101");
    private static final UUID TRANSACTION = UUID.fromString("00000000-0000-0000-0000-000000001102");
    private static final DefinitionRevision DEFINITIONS = new DefinitionRevision(12, "d".repeat(64));
    private static final ResourceLocation POINTS = id("points");

    @Test
    void paidCostsAndAuditEvidenceRoundTripDeterministically() {
        PaidCostRecord child = record("child", 1, 7);
        PaidCostRecord root = record("root", 1, 5);
        var unsorted = new HashMap<PurchaseInstanceId, PaidCostRecord>();
        unsorted.put(root.instanceId(), root);
        unsorted.put(child.instanceId(), child);
        var audit = new AuditRecord(
                new TransactionId(TRANSACTION),
                PLAYER,
                PLAYER,
                ProgressionCause.GAMEPLAY,
                "Paid cost round trip",
                DEFINITIONS,
                Instant.parse("2026-07-17T12:00:00Z"),
                TransactionStatus.COMMITTED,
                3,
                4,
                true,
                List.of(),
                List.of(PaidCostMutation.insert(root)),
                List.of(),
                List.of(),
                "Transaction committed"
        );
        var state = new PersistedTransactionState(
                4, Map.of(POINTS, 9L), Map.of(), unsorted, Map.of(), Map.of(), List.of(audit)
        );

        CompoundTag first = TransactionStateNbtCodec.encode(state);
        PersistedTransactionState decoded = TransactionStateNbtCodec.decode(first);
        CompoundTag second = TransactionStateNbtCodec.encode(decoded);
        ListTag records = first.getList("paid_costs", Tag.TAG_COMPOUND);

        assertEquals(state, decoded);
        assertEquals(first, second);
        assertEquals(id("child").toString(), records.getCompound(0).getString("purchase_id"));
        assertEquals(id("root").toString(), records.getCompound(1).getString("purchase_id"));
        assertEquals(List.of(PaidCostMutation.insert(root)), decoded.auditRecords().getFirst().paidCostMutations());
    }

    @Test
    void explicitZeroBalanceRoundTripsDeterministically() {
        var state = new PersistedTransactionState(
                1, Map.of(POINTS, 0L), Map.of(), Map.of(), Map.of(), Map.of(), List.of()
        );

        CompoundTag first = TransactionStateNbtCodec.encode(state);
        PersistedTransactionState decoded = TransactionStateNbtCodec.decode(first);
        CompoundTag second = TransactionStateNbtCodec.encode(decoded);

        assertTrue(decoded.balances().containsKey(POINTS));
        assertEquals(0L, decoded.balances().get(POINTS));
        assertEquals(first, second);
    }

    @Test
    void malformedAndDuplicatePaidCostsAreRejectedStrictly() {
        PaidCostRecord root = record("root", 1, 5);
        var state = new PersistedTransactionState(
                1, Map.of(), Map.of(), Map.of(root.instanceId(), root), Map.of(), Map.of(), List.of()
        );
        CompoundTag encoded = TransactionStateNbtCodec.encode(state);

        CompoundTag duplicateRecord = encoded.copy();
        ListTag duplicateRecords = duplicateRecord.getList("paid_costs", Tag.TAG_COMPOUND);
        duplicateRecords.add(duplicateRecords.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> TransactionStateNbtCodec.decode(duplicateRecord));

        CompoundTag invalidRank = encoded.copy();
        invalidRank.getList("paid_costs", Tag.TAG_COMPOUND).getCompound(0).putInt("rank", 0);
        assertThrows(IllegalArgumentException.class, () -> TransactionStateNbtCodec.decode(invalidRank));

        CompoundTag duplicateBalance = encoded.copy();
        CompoundTag paid = duplicateBalance.getList("paid_costs", Tag.TAG_COMPOUND).getCompound(0);
        ListTag balances = paid.getList("paid_balances", Tag.TAG_COMPOUND);
        balances.add(balances.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> TransactionStateNbtCodec.decode(duplicateBalance));

        CompoundTag wrongListType = encoded.copy();
        wrongListType.putString("paid_costs", "not a list");
        assertThrows(IllegalArgumentException.class, () -> TransactionStateNbtCodec.decode(wrongListType));

        CompoundTag wrongNestedElementType = encoded.copy();
        var strings = new ListTag();
        strings.add(net.minecraft.nbt.StringTag.valueOf("not a compound"));
        wrongNestedElementType.getList("paid_costs", Tag.TAG_COMPOUND).getCompound(0)
                .put("paid_balances", strings);
        assertThrows(IllegalArgumentException.class, () -> TransactionStateNbtCodec.decode(wrongNestedElementType));
    }

    @Test
    void paidCostDecodeRejectsRecordCapacityBeforeAllocatingTypedState() {
        PaidCostRecord root = record("root", 1, 5);
        CompoundTag entry = TransactionStateNbtCodec.encode(new PersistedTransactionState(
                1, Map.of(), Map.of(), Map.of(root.instanceId(), root), Map.of(), Map.of(), List.of()
        )).getList("paid_costs", Tag.TAG_COMPOUND).getCompound(0);
        var oversized = new ListTag();
        for (int index = 0; index <= PersistedTransactionState.MAX_PAID_COST_RECORDS; index++) {
            oversized.add(entry.copy());
        }
        var tag = new CompoundTag();
        tag.put("paid_costs", oversized);

        assertThrows(IllegalArgumentException.class, () -> TransactionStateNbtCodec.decode(tag));
    }

    @Test
    void versionTwoMigrationAddsAnEmptyPaidLedgerAndRetainsSafetyShadow() {
        CompoundTag versionTwo = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        versionTwo.putInt("data_version", 2);
        versionTwo.getCompound("transaction").remove("paid_costs");

        CompoundTag migratedRaw = PlayerDataMigrations.migrateToCurrent(versionTwo, 2);
        ProgressiveSkillsData migrated = ProgressiveSkillsDataSerializer.decode(PLAYER, versionTwo);

        assertEquals(3, migratedRaw.getInt("data_version"));
        assertTrue(migratedRaw.getCompound("transaction").contains("paid_costs", Tag.TAG_LIST));
        assertTrue(migrated.active());
        assertTrue(migrated.transactionState().paidCosts().isEmpty());
        assertEquals(2, migrated.view().migrationShadow().orElseThrow().sourceVersion());
    }

    @Test
    void malformedCurrentPaidLedgerQuarantinesInsteadOfActivating() {
        CompoundTag current = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        current.getCompound("transaction").putString("paid_costs", "invalid");

        ProgressiveSkillsData decoded = ProgressiveSkillsDataSerializer.decode(PLAYER, current);

        assertFalse(decoded.active());
        assertTrue(decoded.view().quarantine().isPresent());
    }

    @Test
    void emptyPaidBalanceRecordQuarantinesThroughThePlayerCodec() {
        PaidCostRecord root = record("root", 1, 5);
        var state = new PersistedTransactionState(
                1, Map.of(), Map.of(), Map.of(root.instanceId(), root), Map.of(), Map.of(), List.of()
        );
        CompoundTag current = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        current.put("transaction", TransactionStateNbtCodec.encode(state));
        current.getCompound("transaction")
                .getList("paid_costs", Tag.TAG_COMPOUND)
                .getCompound(0)
                .put("paid_balances", new ListTag());

        ProgressiveSkillsData decoded = ProgressiveSkillsDataSerializer.decode(PLAYER, current);

        assertFalse(decoded.active());
        assertTrue(decoded.view().quarantine().isPresent());
        assertTrue(decoded.view().quarantine().orElseThrow().reason()
                .contains("Paid balance count must be within 1 and " + PaidCostRecord.MAX_PAID_BALANCES));
    }

    private static PaidCostRecord record(String path, int rank, long amount) {
        PurchaseInstanceId id = new PurchaseInstanceId(id("tree"), id("test_tree"), id(path), rank);
        return new PaidCostRecord(
                id,
                new TransactionId(TRANSACTION),
                DEFINITIONS,
                "e".repeat(64),
                Map.of(POINTS, amount),
                Set.of(new GrantSourceId(id("tree"), id("test_tree"), id(path + "_grant")))
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }
}
