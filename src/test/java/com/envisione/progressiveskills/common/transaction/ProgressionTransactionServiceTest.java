package com.envisione.progressiveskills.common.transaction;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionTransactionServiceTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final DefinitionRevision DEFINITIONS = new DefinitionRevision(7, "a".repeat(64));
    private static final ResourceLocation ORIGIN = id("progressiveskills:test");
    private static final ResourceLocation POINTS = id("progressiveskills:test_points");
    private static final EntitlementKey HEALTH = new EntitlementKey(
            id("progressiveskills:attribute"), id("minecraft:generic.max_health")
    );
    private static final GrantSourceId SOURCE_A = source("health_a");
    private static final GrantSourceId SOURCE_B = source("health_b");
    private static final TransitionAction ITEM_ACTION = new TransitionAction(
            id("progressiveskills:item"),
            source("starter_item"),
            "minecraft:gold_ingot",
            1,
            RepeatPolicy.ONCE_PER_CHARACTER,
            DeliveryContract.EFFECTIVELY_ONCE,
            TransitionFailurePolicy.STOP
    );
    private static final TransitionAction COMMAND_ACTION = new TransitionAction(
            id("progressiveskills:command"),
            source("audit_command"),
            "say phase4",
            1,
            RepeatPolicy.ONCE_PER_TRANSACTION,
            DeliveryContract.AT_LEAST_ONCE,
            TransitionFailurePolicy.STOP
    );

    @Test
    void commitIsIdempotentAndRecomputeNeverReplaysTransitions() {
        var service = service();
        var projector = new RecordingProjector();
        var executor = new RecordingExecutor();
        CascadePlan plan = plan(
                "test/primary",
                0,
                List.of(new BalanceMutation(POINTS, 1, 0, 10)),
                List.of(EntitlementMutation.grant(HEALTH, SOURCE_A, 4, EntitlementResolver.HIGHEST)),
                List.of(ITEM_ACTION, COMMAND_ACTION)
        );

        TransactionResult first = service.execute(plan, DEFINITIONS, projector, executor);
        TransactionResult replay = service.execute(plan, DEFINITIONS, projector, executor);
        ProjectionReport recompute = service.recompute(TARGET, projector);

        assertEquals(TransactionStatus.COMMITTED, first.status());
        assertTrue(replay.replayed());
        assertEquals(first.transactionId(), replay.transactionId());
        assertEquals(2, executor.executions);
        assertEquals(1, projector.nonEmptyApplications);
        assertTrue(recompute.successful());
        assertTrue(recompute.changes().isEmpty());
        ProgressionSnapshot snapshot = service.snapshot(TARGET);
        assertEquals(1, snapshot.stateRevision());
        assertEquals(1, snapshot.balances().get(POINTS));
        assertEquals(4, snapshot.projectedValues().get(HEALTH));
        assertEquals(2, snapshot.receiptCount());
    }

    @Test
    void previewSnapshotAppliesTheCascadeWithoutMutationAndRejectsStaleInputs() {
        var service = service();
        CascadePlan plan = plan(
                "test/preview",
                0,
                List.of(new BalanceMutation(POINTS, 2, 0, 10)),
                List.of(EntitlementMutation.grant(
                        HEALTH, SOURCE_A, 4, EntitlementResolver.HIGHEST)),
                List.of()
        );

        ProgressionSnapshot preview = service.previewSnapshot(plan, DEFINITIONS);

        assertEquals(1, preview.stateRevision());
        assertEquals(2, preview.balances().get(POINTS));
        assertEquals(4, preview.projectedValues().get(HEALTH));
        assertFalse(service.hasAccount(TARGET));
        assertEquals(ProgressionSnapshot.empty(), service.snapshot(TARGET));
        assertThrows(IllegalArgumentException.class, () -> service.previewSnapshot(
                plan("test/preview-stale-state", 1, List.of(), List.of(), List.of()),
                DEFINITIONS
        ));
        assertThrows(IllegalArgumentException.class, () -> service.previewSnapshot(
                plan,
                new DefinitionRevision(DEFINITIONS.generation() + 1, DEFINITIONS.semanticDigest())
        ));

        TransactionResult committed = service.execute(
                plan, DEFINITIONS, new RecordingProjector(), new RecordingExecutor());
        assertTrue(committed.status().committed());
        assertEquals(service.snapshot(TARGET), service.previewSnapshot(plan, DEFINITIONS));
    }

    @Test
    void permanentReceiptSkipsSameActionAcrossDifferentTransactions() {
        var service = service();
        var executor = new RecordingExecutor();
        var projector = new RecordingProjector();
        TransactionResult first = service.execute(plan(
                "test/reward-a", 0, List.of(), List.of(), List.of(ITEM_ACTION)
        ), DEFINITIONS, projector, executor);
        TransactionResult second = service.execute(plan(
                "test/reward-b", 1, List.of(), List.of(), List.of(ITEM_ACTION)
        ), DEFINITIONS, projector, executor);

        assertTrue(first.status().committed());
        assertTrue(second.status().committed());
        assertEquals(1, executor.executions);
        assertEquals(ActionDisposition.SKIPPED_EXISTING_RECEIPT,
                second.actionResults().getFirst().disposition());
        assertEquals(1, service.snapshot(TARGET).receiptCount());
    }

    @Test
    void highestResolverPreservesValueUntilLastOwnerIsRevoked() {
        var service = service();
        var projector = new RecordingProjector();
        var executor = new RecordingExecutor();
        service.execute(plan("test/grant-a", 0, List.of(), List.of(
                EntitlementMutation.grant(HEALTH, SOURCE_A, 4, EntitlementResolver.HIGHEST)
        ), List.of()), DEFINITIONS, projector, executor);
        TransactionResult coowner = service.execute(plan("test/grant-b", 1, List.of(), List.of(
                EntitlementMutation.grant(HEALTH, SOURCE_B, 4, EntitlementResolver.HIGHEST)
        ), List.of()), DEFINITIONS, projector, executor);
        TransactionResult firstRevoke = service.execute(plan("test/revoke-a", 2, List.of(), List.of(
                EntitlementMutation.revoke(HEALTH, SOURCE_A)
        ), List.of()), DEFINITIONS, projector, executor);
        TransactionResult secondRevoke = service.execute(plan("test/revoke-b", 3, List.of(), List.of(
                EntitlementMutation.revoke(HEALTH, SOURCE_B)
        ), List.of()), DEFINITIONS, projector, executor);

        assertTrue(coowner.projectionChanges().isEmpty());
        assertTrue(firstRevoke.projectionChanges().isEmpty());
        assertEquals(3, service.audit(TARGET).get(2).afterRevision());
        assertEquals(1, secondRevoke.projectionChanges().size());
        assertFalse(service.snapshot(TARGET).projectedValues().containsKey(HEALTH));
    }

    @Test
    void invalidCostAndStalePlansLeaveStateUntouched() {
        var service = service();
        var projector = new RecordingProjector();
        var executor = new RecordingExecutor();
        TransactionResult insufficient = service.execute(plan(
                "test/overspend",
                0,
                List.of(new BalanceMutation(POINTS, -1, 0, 10)),
                List.of(),
                List.of()
        ), DEFINITIONS, projector, executor);
        TransactionResult staleDefinition = service.execute(plan(
                "test/stale-definition", 0, List.of(), List.of(), List.of()
        ), new DefinitionRevision(8, "b".repeat(64)), projector, executor);
        TransactionResult staleState = service.execute(plan(
                "test/stale-state", 1, List.of(), List.of(), List.of()
        ), DEFINITIONS, projector, executor);

        assertEquals(ProgressionTransactionService.BALANCE_REJECTED, insufficient.diagnosticCode());
        assertEquals(ProgressionTransactionService.STALE_DEFINITION, staleDefinition.diagnosticCode());
        assertEquals(ProgressionTransactionService.STALE_STATE, staleState.diagnosticCode());
        assertEquals(0, service.snapshot(TARGET).stateRevision());
        assertTrue(service.snapshot(TARGET).balances().isEmpty());
    }

    @Test
    void resolverConflictAndProjectorRejectionAreAtomic() {
        var service = service();
        var projector = new RecordingProjector();
        var executor = new RecordingExecutor();
        service.execute(plan("test/grant", 0, List.of(), List.of(
                EntitlementMutation.grant(HEALTH, SOURCE_A, 4, EntitlementResolver.HIGHEST)
        ), List.of()), DEFINITIONS, projector, executor);

        TransactionResult conflict = service.execute(plan("test/conflict", 1, List.of(), List.of(
                EntitlementMutation.grant(HEALTH, SOURCE_B, 4, EntitlementResolver.ADDITIVE)
        ), List.of()), DEFINITIONS, projector, executor);
        projector.rejection = Optional.of("unsupported target");
        TransactionResult projection = service.execute(plan("test/projection", 1, List.of(), List.of(
                EntitlementMutation.revoke(HEALTH, SOURCE_A)
        ), List.of()), DEFINITIONS, projector, executor);

        assertEquals(ProgressionTransactionService.LIFECYCLE_REJECTED, conflict.diagnosticCode());
        assertEquals(ProgressionTransactionService.PROJECTION_FAILED, projection.diagnosticCode());
        assertEquals(1, service.snapshot(TARGET).stateRevision());
        assertEquals(4, service.snapshot(TARGET).projectedValues().get(HEALTH));
    }

    @Test
    void actionValidationPreventsCommitButDeliveryFailureDoesNotLieAboutRollback() {
        var service = service();
        var projector = new RecordingProjector();
        var executor = new RecordingExecutor();
        executor.rejection = Optional.of("inventory unavailable");
        TransactionResult preflight = service.execute(plan(
                "test/action-preflight", 0,
                List.of(new BalanceMutation(POINTS, 1, 0, 10)), List.of(), List.of(ITEM_ACTION)
        ), DEFINITIONS, projector, executor);
        assertEquals(TransactionStatus.REJECTED, preflight.status());
        assertEquals(0, service.snapshot(TARGET).stateRevision());

        executor.rejection = Optional.empty();
        executor.success = false;
        TransactionResult failedDelivery = service.execute(plan(
                "test/action-fails", 0,
                List.of(new BalanceMutation(POINTS, 1, 0, 10)), List.of(), List.of(ITEM_ACTION)
        ), DEFINITIONS, projector, executor);
        assertEquals(TransactionStatus.COMMITTED_WITH_ACTION_FAILURES, failedDelivery.status());
        assertEquals(1, service.snapshot(TARGET).balances().get(POINTS));
        assertEquals(0, service.snapshot(TARGET).receiptCount());
        assertFalse(service.audit(TARGET).getLast().reversible());
        TransactionResult rollback = service.rollback(
                ACTOR, TARGET, failedDelivery.transactionId(), new IdempotencyKey("test/rollback-action"),
                1, DEFINITIONS, projector, "Do not roll back external action boundary"
        );
        assertEquals(ProgressionTransactionService.ROLLBACK_REJECTED, rollback.diagnosticCode());
    }

    @Test
    void stopOnFailureSkipsLaterActionsAndCachesTheFailedOutcome() {
        var service = service();
        var executor = new RecordingExecutor();
        executor.success = false;
        CascadePlan plan = plan(
                "test/stop-failure", 0, List.of(), List.of(), List.of(ITEM_ACTION, COMMAND_ACTION)
        );

        TransactionResult first = service.execute(plan, DEFINITIONS, new RecordingProjector(), executor);
        TransactionResult replay = service.execute(plan, DEFINITIONS, new RecordingProjector(), executor);

        assertEquals(TransactionStatus.COMMITTED_WITH_ACTION_FAILURES, first.status());
        assertEquals(ActionDisposition.FAILED, first.actionResults().get(0).disposition());
        assertEquals(ActionDisposition.SKIPPED_AFTER_FAILURE, first.actionResults().get(1).disposition());
        assertTrue(replay.replayed());
        assertEquals(1, executor.executions);
    }

    @Test
    void actionFreeTransactionCanRollbackOnlyOnce() {
        var service = service();
        var projector = new RecordingProjector();
        var executor = new RecordingExecutor();
        TransactionResult committed = service.execute(plan(
                "test/reversible", 0,
                List.of(new BalanceMutation(POINTS, 3, 0, 10)), List.of(), List.of()
        ), DEFINITIONS, projector, executor);
        TransactionResult rollback = service.rollback(
                ACTOR, TARGET, committed.transactionId(), new IdempotencyKey("test/rollback"),
                1, DEFINITIONS, projector, "Undo reversible test"
        );

        assertEquals(TransactionStatus.COMMITTED, rollback.status());
        assertEquals(2, service.snapshot(TARGET).stateRevision());
        assertTrue(service.snapshot(TARGET).balances().isEmpty());
        assertEquals(2, service.audit(TARGET).size());

        TransactionResult second = service.rollback(
                ACTOR, TARGET, committed.transactionId(), new IdempotencyKey("test/rollback-again"),
                2, DEFINITIONS, projector, "Try duplicate rollback"
        );
        assertEquals(ProgressionTransactionService.ROLLBACK_REJECTED, second.diagnosticCode());
    }

    @Test
    void laterMutationClosesAnOlderRollbackBoundary() {
        var service = service();
        var projector = new RecordingProjector();
        var executor = new RecordingExecutor();
        TransactionResult first = service.execute(plan(
                "test/older", 0,
                List.of(new BalanceMutation(POINTS, 3, 0, 10)), List.of(), List.of()
        ), DEFINITIONS, projector, executor);
        service.execute(plan(
                "test/newer", 1,
                List.of(new BalanceMutation(POINTS, 1, 0, 10)), List.of(), List.of()
        ), DEFINITIONS, projector, executor);

        TransactionResult rollback = service.rollback(
                ACTOR, TARGET, first.transactionId(), new IdempotencyKey("test/rollback-older"),
                2, DEFINITIONS, projector, "Reject stale rollback boundary"
        );

        assertEquals(ProgressionTransactionService.ROLLBACK_REJECTED, rollback.diagnosticCode());
        assertEquals(4, service.snapshot(TARGET).balances().get(POINTS));
        assertEquals(2, service.snapshot(TARGET).stateRevision());
    }

    @Test
    void physicalProjectionExceptionCannotCommitCopiedState() {
        var service = service();
        var projector = new RecordingProjector();
        projector.failApply = true;
        TransactionResult result = service.execute(plan(
                "test/projector-throws", 0, List.of(), List.of(
                        EntitlementMutation.grant(HEALTH, SOURCE_A, 4, EntitlementResolver.HIGHEST)
                ), List.of()
        ), DEFINITIONS, projector, new RecordingExecutor());

        assertEquals(ProgressionTransactionService.PROJECTION_FAILED, result.diagnosticCode());
        assertEquals(0, service.snapshot(TARGET).stateRevision());
        assertTrue(service.snapshot(TARGET).ownership().isEmpty());
    }

    @Test
    void boundedCascadeCommitsInDeclaredOrderAndRejectsOversizedPlans() {
        var service = service();
        TransactionPlan root = plan(
                "test/cascade", 0,
                List.of(new BalanceMutation(POINTS, 3, 0, 10)), List.of(), List.of()
        ).transaction();
        var child = new TransactionStep(
                id("progressiveskills:child"),
                List.of(new BalanceMutation(POINTS, -2, 0, 10)),
                List.of(),
                List.of()
        );
        TransactionResult result = service.execute(
                new CascadePlan(root, List.of(child)),
                DEFINITIONS,
                new RecordingProjector(),
                new RecordingExecutor()
        );
        assertEquals(TransactionStatus.COMMITTED, result.status());
        assertEquals(1, service.snapshot(TARGET).balances().get(POINTS));

        var children = new ArrayList<TransactionStep>();
        for (int index = 0; index < CascadePlan.MAX_STEPS; index++) {
            children.add(TransactionStep.empty(id("progressiveskills:child_" + index)));
        }
        assertThrows(IllegalArgumentException.class, () -> new CascadePlan(root, children));
    }

    @Test
    void replayRetentionRollsForwardAndAuditUsesBoundedRetention() {
        var service = new ProgressionTransactionService(1, 1, 2, 1,
                Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC));
        var projector = new RecordingProjector();
        var executor = new RecordingExecutor();
        service.execute(plan("test/one", 0, List.of(), List.of(), List.of(ITEM_ACTION)),
                DEFINITIONS, projector, executor);
        service.execute(plan("test/two", 1, List.of(), List.of(), List.of()),
                DEFINITIONS, projector, executor);
        CascadePlan thirdPlan = plan("test/three", 2, List.of(), List.of(), List.of());
        TransactionResult third = service.execute(thirdPlan,
                DEFINITIONS, projector, executor);
        TransactionResult replay = service.execute(thirdPlan, DEFINITIONS, projector, executor);

        assertTrue(third.status().committed());
        assertTrue(replay.replayed());
        assertEquals(3, service.snapshot(TARGET).stateRevision());
        assertEquals(1, service.snapshot(TARGET).receiptCount());
        assertEquals(1, service.audit(TARGET).size());
        assertEquals(2, service.snapshot(TARGET).idempotencyCount());
    }

    @Test
    void exactReceiptCapacityRejectsBeforeASecondDeliveryCommits() {
        var service = new ProgressionTransactionService(1, 1, 8, 8, Clock.systemUTC());
        var executor = new RecordingExecutor();
        service.execute(plan("test/receipt-one", 0, List.of(), List.of(), List.of(ITEM_ACTION)),
                DEFINITIONS, new RecordingProjector(), executor);
        TransactionResult rejected = service.execute(plan(
                "test/receipt-two", 1, List.of(), List.of(), List.of(COMMAND_ACTION)
        ), DEFINITIONS, new RecordingProjector(), executor);

        assertEquals(ProgressionTransactionService.LEDGER_FULL, rejected.diagnosticCode());
        assertEquals(1, service.snapshot(TARGET).stateRevision());
        assertEquals(1, service.snapshot(TARGET).receiptCount());
        assertEquals(1, executor.executions);
    }

    private static ProgressionTransactionService service() {
        return new ProgressionTransactionService(8, 16, 32, 32,
                Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC));
    }

    private static CascadePlan plan(
            String key,
            long expectedRevision,
            List<BalanceMutation> balances,
            List<EntitlementMutation> entitlements,
            List<TransitionAction> actions
    ) {
        var step = new TransactionStep(ORIGIN, balances, entitlements, actions);
        return CascadePlan.single(new TransactionPlan(
                ACTOR,
                TARGET,
                new IdempotencyKey(key),
                expectedRevision,
                DEFINITIONS,
                ProgressionCause.ADMIN,
                "Unit-test transaction",
                step
        ));
    }

    private static GrantSourceId source(String path) {
        return new GrantSourceId(
                id("progressiveskills:test_owner"),
                id("progressiveskills:phase4_test"),
                id("progressiveskills:" + path)
        );
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private static final class RecordingProjector implements PersistentProjector {
        private Optional<String> rejection = Optional.empty();
        private boolean failApply;
        private int nonEmptyApplications;

        @Override
        public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
            return rejection;
        }

        @Override
        public void apply(UUID targetId, List<ProjectionChange> changes) {
            if (failApply) {
                throw new IllegalStateException("simulated atomic projection failure");
            }
            if (!changes.isEmpty()) {
                nonEmptyApplications++;
            }
        }
    }

    private static final class RecordingExecutor implements TransitionActionExecutor {
        private Optional<String> rejection = Optional.empty();
        private boolean success = true;
        private int executions;

        @Override
        public Optional<String> validate(UUID targetId, TransitionAction action) {
            return rejection;
        }

        @Override
        public ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action) {
            executions++;
            return success ? ActionExecution.success("delivered") : ActionExecution.failure("simulated failure");
        }
    }
}
