package com.envisione.progressiveskills.common.transaction;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaidCostLedgerTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final DefinitionRevision DEFINITIONS = new DefinitionRevision(10, "a".repeat(64));
    private static final DefinitionRevision EDITED_DEFINITIONS = new DefinitionRevision(11, "b".repeat(64));
    private static final ResourceLocation POINTS = id("points");
    private static final EntitlementKey HEALTH = new EntitlementKey(id("attribute"), id("health"));
    private static final PurchaseInstanceId PURCHASE = purchase("root", 1);
    private static final GrantSourceId SOURCE = new GrantSourceId(id("tree"), id("test_tree"), id("health_grant"));

    @Test
    void purchaseAndRefundUsePersistedHistoricalCost() {
        var service = service(8);
        execute(service, plan("seed", 0, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, 10, 0, 100)), List.of(), List.of()), projector());
        IdempotencyKey purchaseKey = new IdempotencyKey("ledger/purchase");
        PaidCostRecord paid = record(PURCHASE, purchaseKey, DEFINITIONS, 5);
        TransactionResult purchase = execute(service, plan(purchaseKey, 1, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, -5, 0, 100)),
                List.of(EntitlementMutation.grant(HEALTH, SOURCE, 4, EntitlementResolver.ADDITIVE)),
                List.of(PaidCostMutation.insert(paid))), projector());

        assertTrue(purchase.status().committed());
        PaidCostRecord stored = service.snapshot(TARGET).paidCosts().get(PURCHASE);
        assertEquals(5, stored.paidBalances().get(POINTS));
        assertEquals(DEFINITIONS, stored.definitionRevision());
        assertEquals(List.of(PaidCostMutation.insert(paid)), service.audit(TARGET).getLast().paidCostMutations());

        long editedCurrentCost = 17;
        assertFalse(editedCurrentCost == stored.paidBalances().get(POINTS));
        TransactionResult refund = execute(service, plan("refund", 2, EDITED_DEFINITIONS,
                List.of(new BalanceMutation(POINTS, stored.paidBalances().get(POINTS), 0, 100)),
                List.of(EntitlementMutation.revoke(HEALTH, SOURCE)),
                List.of(PaidCostMutation.remove(stored))), projector());

        assertTrue(refund.status().committed());
        assertEquals(10, service.snapshot(TARGET).balances().get(POINTS));
        assertTrue(service.snapshot(TARGET).paidCosts().isEmpty());
        assertTrue(service.snapshot(TARGET).ownership().isEmpty());
    }

    @Test
    void projectionFailureRollsBackDebitGrantAndPaidRecord() {
        var service = service(8);
        execute(service, plan("seed", 0, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, 10, 0, 100)), List.of(), List.of()), projector());
        IdempotencyKey key = new IdempotencyKey("ledger/projection_failure");
        PaidCostRecord paid = record(PURCHASE, key, DEFINITIONS, 5);
        PersistentProjector failing = new PersistentProjector() {
            @Override
            public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
                return Optional.empty();
            }

            @Override
            public void apply(UUID targetId, List<ProjectionChange> changes) {
                throw new IllegalStateException("projection failed");
            }
        };

        TransactionResult result = execute(service, plan(key, 1, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, -5, 0, 100)),
                List.of(EntitlementMutation.grant(HEALTH, SOURCE, 4, EntitlementResolver.ADDITIVE)),
                List.of(PaidCostMutation.insert(paid))), failing);

        assertEquals(ProgressionTransactionService.PROJECTION_FAILED, result.diagnosticCode());
        assertEquals(1, service.snapshot(TARGET).stateRevision());
        assertEquals(10, service.snapshot(TARGET).balances().get(POINTS));
        assertTrue(service.snapshot(TARGET).paidCosts().isEmpty());
        assertTrue(service.snapshot(TARGET).ownership().isEmpty());
    }

    @Test
    void exactMutationRejectsStaleRemovalWithoutCreditingCurrency() {
        var service = service(8);
        execute(service, plan("seed", 0, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, 10, 0, 100)), List.of(), List.of()), projector());
        IdempotencyKey key = new IdempotencyKey("ledger/exact_purchase");
        PaidCostRecord paid = record(PURCHASE, key, DEFINITIONS, 5);
        execute(service, plan(key, 1, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, -5, 0, 100)), List.of(),
                List.of(PaidCostMutation.insert(paid))), projector());
        PaidCostRecord stale = new PaidCostRecord(
                paid.instanceId(), paid.purchaseTransactionId(), EDITED_DEFINITIONS,
                paid.ownerLineage(), paid.paidBalances(), paid.persistentSources());

        TransactionResult result = execute(service, plan("stale_refund", 2, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, 5, 0, 100)), List.of(),
                List.of(PaidCostMutation.remove(stale))), projector());

        assertEquals(ProgressionTransactionService.LIFECYCLE_REJECTED, result.diagnosticCode());
        assertEquals(5, service.snapshot(TARGET).balances().get(POINTS));
        assertEquals(paid, service.snapshot(TARGET).paidCosts().get(PURCHASE));
        assertEquals(2, service.snapshot(TARGET).stateRevision());
    }

    @Test
    void paidLedgerCapacityRejectsWholeSecondPurchase() {
        var service = service(1);
        execute(service, plan("seed", 0, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, 20, 0, 100)), List.of(), List.of()), projector());
        PaidCostRecord first = record(PURCHASE, new IdempotencyKey("ledger/first"), DEFINITIONS, 5);
        execute(service, plan("first", 1, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, -5, 0, 100)), List.of(),
                List.of(PaidCostMutation.insert(first))), projector());
        PurchaseInstanceId secondId = purchase("child", 1);
        PaidCostRecord second = record(secondId, new IdempotencyKey("ledger/second"), DEFINITIONS, 6);

        TransactionResult result = execute(service, plan("second", 2, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, -6, 0, 100)), List.of(),
                List.of(PaidCostMutation.insert(second))), projector());

        assertEquals(ProgressionTransactionService.LEDGER_FULL, result.diagnosticCode());
        assertEquals(15, service.snapshot(TARGET).balances().get(POINTS));
        assertEquals(Map.of(PURCHASE, first), service.snapshot(TARGET).paidCosts());
        assertEquals(2, service.snapshot(TARGET).stateRevision());
    }

    @Test
    void rollbackRestoresPaidLedgerAndAuditsTheExactRemoval() {
        var service = service(8);
        execute(service, plan("seed", 0, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, 10, 0, 100)), List.of(), List.of()), projector());
        IdempotencyKey key = new IdempotencyKey("ledger/reversible_purchase");
        PaidCostRecord paid = record(PURCHASE, key, DEFINITIONS, 5);
        TransactionResult purchase = execute(service, plan(key, 1, DEFINITIONS,
                List.of(new BalanceMutation(POINTS, -5, 0, 100)), List.of(),
                List.of(PaidCostMutation.insert(paid))), projector());

        TransactionResult rollback = service.rollback(
                ACTOR, TARGET, purchase.transactionId(), new IdempotencyKey("ledger/rollback"),
                2, DEFINITIONS, projector(), "Rollback paid purchase"
        );

        assertTrue(rollback.status().committed());
        assertEquals(10, service.snapshot(TARGET).balances().get(POINTS));
        assertTrue(service.snapshot(TARGET).paidCosts().isEmpty());
        assertEquals(List.of(PaidCostMutation.remove(paid)), service.audit(TARGET).getLast().paidCostMutations());
    }

    @Test
    void paidCostValuesAndMutationBudgetsAreBounded() {
        assertThrows(IllegalArgumentException.class,
                () -> new PurchaseInstanceId(id("tree"), id("test_tree"), id("root"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PaidCostRecord(PURCHASE, new TransactionId(UUID.randomUUID()), DEFINITIONS,
                        "c".repeat(64), Map.of(POINTS, 0L), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PaidCostRecord(PURCHASE, new TransactionId(UUID.randomUUID()), DEFINITIONS,
                        "c".repeat(64), Map.of(), Set.of()));
        var balances = new HashMap<ResourceLocation, Long>();
        for (int index = 0; index <= PaidCostRecord.MAX_PAID_BALANCES; index++) {
            balances.put(id("currency_" + index), 1L);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new PaidCostRecord(PURCHASE, new TransactionId(UUID.randomUUID()), DEFINITIONS,
                        "c".repeat(64), balances, Set.of()));
        var sources = new HashSet<GrantSourceId>();
        for (int index = 0; index <= PaidCostRecord.MAX_PERSISTENT_SOURCES; index++) {
            sources.add(new GrantSourceId(id("tree"), id("test_tree"), id("grant_" + index)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new PaidCostRecord(PURCHASE, new TransactionId(UUID.randomUUID()), DEFINITIONS,
                        "c".repeat(64), Map.of(POINTS, 1L), sources));

        PaidCostRecord paid = record(PURCHASE, new IdempotencyKey("ledger/budget"), DEFINITIONS, 1);
        PaidCostRecord rewritten = new PaidCostRecord(
                paid.instanceId(), paid.purchaseTransactionId(), paid.definitionRevision(), paid.ownerLineage(),
                Map.of(POINTS, 2L), paid.persistentSources());
        assertThrows(IllegalArgumentException.class, () -> PaidCostMutation.replace(paid, rewritten));
        var mutations = new ArrayList<PaidCostMutation>();
        for (int index = 0; index <= TransactionStep.MAX_MUTATIONS; index++) {
            mutations.add(PaidCostMutation.insert(paid));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new TransactionStep(id("test"), List.of(), List.of(), mutations, List.of()));
    }

    private static TransactionResult execute(
            ProgressionTransactionService service,
            CascadePlan plan,
            PersistentProjector projector
    ) {
        return service.execute(plan, plan.transaction().definitionRevision(), projector, executor());
    }

    private static CascadePlan plan(
            String key,
            long revision,
            DefinitionRevision definitions,
            List<BalanceMutation> balances,
            List<EntitlementMutation> entitlements,
            List<PaidCostMutation> paidCosts
    ) {
        return plan(new IdempotencyKey("ledger/" + key), revision, definitions, balances, entitlements, paidCosts);
    }

    private static CascadePlan plan(
            IdempotencyKey key,
            long revision,
            DefinitionRevision definitions,
            List<BalanceMutation> balances,
            List<EntitlementMutation> entitlements,
            List<PaidCostMutation> paidCosts
    ) {
        return CascadePlan.single(new TransactionPlan(
                ACTOR,
                TARGET,
                key,
                revision,
                definitions,
                ProgressionCause.GAMEPLAY,
                "Paid cost ledger test",
                new TransactionStep(id("test"), balances, entitlements, paidCosts, List.of())
        ));
    }

    private static PaidCostRecord record(
            PurchaseInstanceId id,
            IdempotencyKey key,
            DefinitionRevision definitions,
            long cost
    ) {
        return new PaidCostRecord(
                id,
                TransactionId.derive(TARGET, key),
                definitions,
                "c".repeat(64),
                Map.of(POINTS, cost),
                Set.of(SOURCE)
        );
    }

    private static ProgressionTransactionService service(int maxPaidCosts) {
        return new ProgressionTransactionService(
                4, 8, 32, 32, maxPaidCosts,
                Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static PersistentProjector projector() {
        return new PersistentProjector() {
            @Override
            public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
                return Optional.empty();
            }

            @Override
            public void apply(UUID targetId, List<ProjectionChange> changes) {
            }
        };
    }

    private static TransitionActionExecutor executor() {
        return new TransitionActionExecutor() {
            @Override
            public Optional<String> validate(UUID targetId, TransitionAction action) {
                return Optional.empty();
            }

            @Override
            public ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action) {
                return ActionExecution.success("unused");
            }
        };
    }

    private static PurchaseInstanceId purchase(String path, int rank) {
        return new PurchaseInstanceId(id("tree"), id("test_tree"), id(path), rank);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }
}
