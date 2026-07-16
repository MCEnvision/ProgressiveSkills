package com.envisione.progressiveskills.server.transaction;

import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.ProjectionChange;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Executable server-safe proof of the complete Phase 4 lifecycle sequence. */
public final class Phase4SelfTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000404");
    private static final DefinitionRevision DEFINITIONS = new DefinitionRevision(4, "4".repeat(64));

    private Phase4SelfTest() {
    }

    public static Result run() {
        var service = new ProgressionTransactionService(
                1,
                16,
                16,
                16,
                Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC)
        );
        var projector = new RecordingProjector();
        var executor = new RecordingExecutor();

        var primaryPlan = Phase4LifecycleDemo.primary(
                TARGET, TARGET, service.snapshot(TARGET), DEFINITIONS
        );
        var primary = service.execute(primaryPlan, DEFINITIONS, projector, executor);
        var replay = service.execute(primaryPlan, DEFINITIONS, projector, executor);
        var recompute = service.recompute(TARGET, projector);
        var coowner = service.execute(Phase4LifecycleDemo.coowner(
                TARGET, TARGET, service.snapshot(TARGET), DEFINITIONS
        ), DEFINITIONS, projector, executor);
        var revokePrimary = service.execute(Phase4LifecycleDemo.revoke(
                TARGET, TARGET, service.snapshot(TARGET), DEFINITIONS, true
        ), DEFINITIONS, projector, executor);
        var revokeSecondary = service.execute(Phase4LifecycleDemo.revoke(
                TARGET, TARGET, service.snapshot(TARGET), DEFINITIONS, false
        ), DEFINITIONS, projector, executor);

        boolean valid = primary.status().committed()
                && replay.replayed()
                && primary.transactionId().equals(replay.transactionId())
                && recompute.successful()
                && recompute.changes().isEmpty()
                && coowner.projectionChanges().isEmpty()
                && revokePrimary.projectionChanges().isEmpty()
                && revokeSecondary.projectionChanges().size() == 1
                && executor.executions == 1
                && projector.nonEmptyApplications == 2
                && service.snapshot(TARGET).balances().getOrDefault(Phase4LifecycleDemo.DEMO_POINTS, 0L) == 1
                && service.snapshot(TARGET).projectedValues().isEmpty()
                && service.snapshot(TARGET).receiptCount() == 1
                && service.audit(TARGET).size() == 4;
        return valid
                ? new Result(true, "idempotency, recompute, receipts, co-ownership, revocation, and audit passed")
                : new Result(false, "one or more lifecycle invariants failed");
    }

    public record Result(boolean successful, String detail) {
    }

    private static final class RecordingProjector implements PersistentProjector {
        private int nonEmptyApplications;

        @Override
        public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
            return Optional.empty();
        }

        @Override
        public void apply(UUID targetId, List<ProjectionChange> changes) {
            if (!changes.isEmpty()) {
                nonEmptyApplications++;
            }
        }
    }

    private static final class RecordingExecutor implements TransitionActionExecutor {
        private int executions;

        @Override
        public Optional<String> validate(UUID targetId, TransitionAction action) {
            return Optional.empty();
        }

        @Override
        public ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action) {
            executions++;
            return ActionExecution.success("self-test delivery");
        }
    }
}
