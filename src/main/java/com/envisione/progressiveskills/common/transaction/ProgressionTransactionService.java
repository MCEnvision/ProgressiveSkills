package com.envisione.progressiveskills.common.transaction;

import com.envisione.progressiveskills.common.data.ProgressiveSkillsDataSerializer;
import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import net.minecraft.resources.ResourceLocation;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Coordinates bounded progression transactions. */
public final class ProgressionTransactionService {
    public static final int DEFAULT_MAX_RECEIPTS_PER_ACCOUNT = 512;
    public static final int DEFAULT_MAX_IDEMPOTENCY_RESULTS_PER_ACCOUNT = 512;
    public static final int DEFAULT_MAX_AUDIT_RECORDS_PER_ACCOUNT = 256;
    public static final int DEFAULT_MAX_PAID_COSTS_PER_ACCOUNT = 512;
    public static final String OK = "PS-TX-OK";
    public static final String STALE_STATE = CoreDiagnostics.STALE_TRANSACTION_STATE.value();
    public static final String BALANCE_REJECTED = CoreDiagnostics.BALANCE_TRANSACTION_REJECTED.value();
    public static final String STALE_DEFINITION = CoreDiagnostics.STALE_TRANSACTION_DEFINITION.value();
    public static final String LIFECYCLE_REJECTED = CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP.value();
    public static final String ACTION_REJECTED = CoreDiagnostics.TRANSITION_ACTION_REJECTED.value();
    public static final String LEDGER_FULL = CoreDiagnostics.TRANSACTION_LEDGER_FULL.value();
    public static final String PROJECTION_FAILED = CoreDiagnostics.PERSISTENT_PROJECTION_FAILED.value();
    public static final String ROLLBACK_REJECTED = CoreDiagnostics.TRANSACTION_ROLLBACK_REJECTED.value();
    public static final String ACCOUNT_UNAVAILABLE = CoreDiagnostics.PLAYER_DATA_QUARANTINED.value();
    private static final String MAXIMUM_ACTION_DETAIL = "\u0800".repeat(ActionExecution.MAX_DETAIL_LENGTH);

    private final int maxAccounts;
    private final int maxReceiptsPerAccount;
    private final int maxIdempotencyResultsPerAccount;
    private final int maxAuditRecordsPerAccount;
    private final int maxPaidCostsPerAccount;
    private final Clock clock;
    private final Map<UUID, Account> accounts = new LinkedHashMap<>();

    public ProgressionTransactionService(
            int maxAccounts,
            int maxReceiptsPerAccount,
            int maxIdempotencyResultsPerAccount,
            int maxAuditRecordsPerAccount,
            Clock clock
    ) {
        this(maxAccounts, maxReceiptsPerAccount, maxIdempotencyResultsPerAccount,
                maxAuditRecordsPerAccount, DEFAULT_MAX_PAID_COSTS_PER_ACCOUNT, clock);
    }

    public ProgressionTransactionService(
            int maxAccounts,
            int maxReceiptsPerAccount,
            int maxIdempotencyResultsPerAccount,
            int maxAuditRecordsPerAccount,
            int maxPaidCostsPerAccount,
            Clock clock
    ) {
        if (maxAccounts < 1 || maxReceiptsPerAccount < 1
                || maxIdempotencyResultsPerAccount < 1 || maxAuditRecordsPerAccount < 1
                || maxPaidCostsPerAccount < 1
                || maxPaidCostsPerAccount > PersistedTransactionState.MAX_PAID_COST_RECORDS) {
            throw new IllegalArgumentException("Transaction service limits must be positive");
        }
        this.maxAccounts = maxAccounts;
        this.maxReceiptsPerAccount = maxReceiptsPerAccount;
        this.maxIdempotencyResultsPerAccount = maxIdempotencyResultsPerAccount;
        this.maxAuditRecordsPerAccount = maxAuditRecordsPerAccount;
        this.maxPaidCostsPerAccount = maxPaidCostsPerAccount;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static ProgressionTransactionService boundedDefaults() {
        return new ProgressionTransactionService(
                256,
                DEFAULT_MAX_RECEIPTS_PER_ACCOUNT,
                DEFAULT_MAX_IDEMPOTENCY_RESULTS_PER_ACCOUNT,
                DEFAULT_MAX_AUDIT_RECORDS_PER_ACCOUNT,
                DEFAULT_MAX_PAID_COSTS_PER_ACCOUNT,
                Clock.systemUTC()
        );
    }

    public synchronized TransactionResult executeLoaded(
            CascadePlan cascade,
            DefinitionRevision currentDefinition,
            PersistentProjector projector,
            TransitionActionExecutor actionExecutor
    ) {
        return executeLoaded(
                cascade,
                currentDefinition,
                projector,
                actionExecutor,
                (targetId, candidate) -> ProgressiveSkillsDataSerializer.transactionStateRejection(
                        targetId, candidate, currentDefinition
                )
        );
    }

    public synchronized TransactionResult executeLoaded(
            CascadePlan cascade,
            DefinitionRevision currentDefinition,
            PersistentProjector projector,
            TransitionActionExecutor actionExecutor,
            PersistentStateValidator stateValidator
    ) {
        Objects.requireNonNull(cascade, "cascade");
        TransactionPlan plan = cascade.transaction();
        if (!accounts.containsKey(plan.targetId())) {
            return uncachedRejection(
                    TransactionId.derive(plan.targetId(), plan.idempotencyKey()),
                    ACCOUNT_UNAVAILABLE,
                    "Player transaction state is not loaded",
                    plan.expectedStateRevision()
            );
        }
        return execute(cascade, currentDefinition, projector, actionExecutor, stateValidator);
    }

    public synchronized TransactionResult execute(
            CascadePlan cascade,
            DefinitionRevision currentDefinition,
            PersistentProjector projector,
            TransitionActionExecutor actionExecutor
    ) {
        return execute(
                cascade,
                currentDefinition,
                projector,
                actionExecutor,
                (targetId, candidate) -> ProgressiveSkillsDataSerializer.transactionStateRejection(
                        targetId, candidate, currentDefinition
                )
        );
    }

    public synchronized TransactionResult execute(
            CascadePlan cascade,
            DefinitionRevision currentDefinition,
            PersistentProjector projector,
            TransitionActionExecutor actionExecutor,
            PersistentStateValidator stateValidator
    ) {
        Objects.requireNonNull(cascade, "cascade");
        Objects.requireNonNull(currentDefinition, "currentDefinition");
        Objects.requireNonNull(projector, "projector");
        Objects.requireNonNull(actionExecutor, "actionExecutor");
        Objects.requireNonNull(stateValidator, "stateValidator");
        TransactionPlan plan = cascade.transaction();
        TransactionId transactionId = TransactionId.derive(plan.targetId(), plan.idempotencyKey());
        Account account = accountFor(plan.targetId());
        if (account == null) {
            return uncachedRejection(transactionId, LEDGER_FULL, "Session account capacity is full", 0);
        }

        TransactionResult prior = account.idempotencyResults.get(plan.idempotencyKey());
        if (prior != null) {
            return prior.asReplay();
        }
        if (!plan.definitionRevision().equals(currentDefinition)) {
            return rejectAndCache(account, plan, transactionId, STALE_DEFINITION,
                    "Definition generation or digest changed after the plan was captured", stateValidator);
        }
        if (plan.expectedStateRevision() != account.revision) {
            return rejectAndCache(account, plan, transactionId, STALE_STATE,
                    "Expected state revision " + plan.expectedStateRevision() + " but found " + account.revision,
                    stateValidator);
        }

        CoreState before = account.copyState();
        CoreState working = before.copy();
        List<BalanceMutation> balanceMutations = flattenBalanceMutations(cascade);
        List<PaidCostMutation> paidCostMutations = flattenPaidCostMutations(cascade);
        List<TransitionAction> actions = flattenActions(cascade);
        try {
            applyBalances(working.balances, balanceMutations);
            applyEntitlements(working.ownership, flattenEntitlementMutations(cascade));
            applyPaidCosts(working.paidCosts, paidCostMutations, maxPaidCostsPerAccount);
            working.projected = resolveEffective(working.ownership);
        } catch (PaidCostCapacityException exception) {
            return rejectAndCache(account, plan, transactionId, LEDGER_FULL, safeMessage(exception), stateValidator);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            String code = exception instanceof ArithmeticException ? BALANCE_REJECTED : LIFECYCLE_REJECTED;
            return rejectAndCache(account, plan, transactionId, code, safeMessage(exception), stateValidator);
        }

        List<ProjectionChange> projectionChanges = projectionDiff(before.projected, working.projected);
        Optional<String> projectionRejection;
        try {
            projectionRejection = boundedReason(projector.validate(plan.targetId(), projectionChanges));
        } catch (RuntimeException exception) {
            return rejectAndCache(account, plan, transactionId, PROJECTION_FAILED, safeMessage(exception),
                    stateValidator);
        }
        if (projectionRejection.isPresent()) {
            return rejectAndCache(account, plan, transactionId, PROJECTION_FAILED,
                    projectionRejection.orElseThrow(), stateValidator);
        }

        Set<ReceiptKey> newReceiptKeys = new HashSet<>();
        var pendingActions = new ArrayList<TransitionAction>();
        for (TransitionAction action : actions) {
            Optional<ReceiptKey> receiptKey = receiptKey(plan.targetId(), transactionId, action);
            if (receiptKey.isPresent() && !account.receipts.containsKey(receiptKey.orElseThrow())) {
                newReceiptKeys.add(receiptKey.orElseThrow());
            }
            if (receiptKey.isPresent() && account.receipts.containsKey(receiptKey.orElseThrow())) {
                continue;
            }
            pendingActions.add(action);
        }
        Optional<String> actionRejection;
        try {
            actionRejection = boundedReason(actionExecutor.validateAll(plan.targetId(), pendingActions));
        } catch (RuntimeException exception) {
            return rejectAndCache(account, plan, transactionId, ACTION_REJECTED, safeMessage(exception),
                    stateValidator);
        }
        if (actionRejection.isPresent()) {
            return rejectAndCache(account, plan, transactionId, ACTION_REJECTED,
                    actionRejection.orElseThrow(), stateValidator);
        }
        if ((long) account.receipts.size() + newReceiptKeys.size() > maxReceiptsPerAccount) {
            return rejectAndCache(account, plan, transactionId, LEDGER_FULL,
                    "Exact transition receipt ledger is full", stateValidator);
        }

        long committedRevision;
        try {
            committedRevision = Math.addExact(account.revision, 1);
        } catch (ArithmeticException exception) {
            return rejectAndCache(account, plan, transactionId, STALE_STATE, "State revision overflow",
                    stateValidator);
        }
        PersistenceReservation reservation = reserveCommitPersistence(
                account,
                working,
                committedRevision,
                plan,
                transactionId,
                balanceMutations,
                paidCostMutations,
                projectionChanges,
                actions,
                newReceiptKeys,
                stateValidator
        );
        if (!reservation.accepted()) {
            return uncachedRejection(
                    transactionId,
                    LEDGER_FULL,
                    "Persistent transaction state is full. " + reservation.rejection(),
                    account.revision
            );
        }
        account.install(working, committedRevision);
        try {
            projector.apply(plan.targetId(), projectionChanges);
        } catch (RuntimeException exception) {
            account.install(before, before.revision);
            return rejectAndCache(account, plan, transactionId, PROJECTION_FAILED,
                    "Persistent projection rejected atomically. " + safeMessage(exception), stateValidator);
        }
        List<TransitionActionResult> actionResults = executeActions(
                account,
                plan,
                transactionId,
                actions,
                actionExecutor
        );
        boolean actionFailure = actionResults.stream()
                .anyMatch(result -> result.disposition() == ActionDisposition.FAILED);
        TransactionStatus status = actionFailure
                ? TransactionStatus.COMMITTED_WITH_ACTION_FAILURES
                : TransactionStatus.COMMITTED;
        String message = actionFailure
                ? "State committed; one or more transition actions failed and were audited"
                : "Transaction committed";
        var result = new TransactionResult(
                transactionId,
                status,
                before.revision,
                account.revision,
                actionFailure ? ACTION_REJECTED : OK,
                message,
                false,
                projectionChanges,
                actionResults
        );
        applyPersistenceRetention(account, reservation, plan.idempotencyKey());
        account.idempotencyResults.put(plan.idempotencyKey(), result);
        boolean reversible = actions.isEmpty();
        recordAudit(account, plan, result, balanceMutations, paidCostMutations, reversible);
        if (reversible) {
            account.rollbackRecords.put(transactionId, new RollbackRecord(plan.targetId(), account.revision, before));
        }
        return result;
    }

    public synchronized ProgressionSnapshot previewSnapshot(
            CascadePlan cascade,
            DefinitionRevision currentDefinition
    ) {
        Objects.requireNonNull(cascade, "cascade");
        Objects.requireNonNull(currentDefinition, "currentDefinition");
        TransactionPlan plan = cascade.transaction();
        Account account = accounts.get(plan.targetId());
        if (account == null && accounts.size() >= maxAccounts) {
            throw new IllegalStateException("Session account capacity is full");
        }
        if (account != null && account.idempotencyResults.containsKey(plan.idempotencyKey())) {
            return account.snapshot();
        }
        if (!plan.definitionRevision().equals(currentDefinition)) {
            throw new IllegalArgumentException(
                    "Definition generation or digest changed after the plan was captured");
        }
        long currentRevision = account == null ? 0 : account.revision;
        if (plan.expectedStateRevision() != currentRevision) {
            throw new IllegalArgumentException("Expected state revision "
                    + plan.expectedStateRevision() + " but found " + currentRevision);
        }
        CoreState working = account == null
                ? new CoreState(
                0,
                new TreeMap<>(ResourceLocation::compareNamespaced),
                new TreeMap<>(),
                new TreeMap<>(),
                new TreeMap<>()
        )
                : account.copyState();
        applyBalances(working.balances, flattenBalanceMutations(cascade));
        applyEntitlements(working.ownership, flattenEntitlementMutations(cascade));
        applyPaidCosts(working.paidCosts, flattenPaidCostMutations(cascade), maxPaidCostsPerAccount);
        working.projected = resolveEffective(working.ownership);
        long previewRevision = Math.addExact(currentRevision, 1);
        return new ProgressionSnapshot(
                previewRevision,
                working.balances,
                working.ownership,
                working.paidCosts,
                working.projected,
                account == null ? 0 : account.receipts.size(),
                account == null ? 0 : account.idempotencyResults.size(),
                account == null ? 0 : account.auditRecords.size()
        );
    }

    public synchronized TransactionResult rollback(
            UUID actorId,
            UUID targetId,
            TransactionId originalTransaction,
            IdempotencyKey idempotencyKey,
            long expectedStateRevision,
            DefinitionRevision currentDefinition,
            PersistentProjector projector,
            String reason
    ) {
        return rollback(
                actorId,
                targetId,
                originalTransaction,
                idempotencyKey,
                expectedStateRevision,
                currentDefinition,
                projector,
                reason,
                (candidateTarget, candidate) -> ProgressiveSkillsDataSerializer.transactionStateRejection(
                        candidateTarget, candidate, currentDefinition
                )
        );
    }

    public synchronized TransactionResult rollback(
            UUID actorId,
            UUID targetId,
            TransactionId originalTransaction,
            IdempotencyKey idempotencyKey,
            long expectedStateRevision,
            DefinitionRevision currentDefinition,
            PersistentProjector projector,
            String reason,
            PersistentStateValidator stateValidator
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(originalTransaction, "originalTransaction");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(currentDefinition, "currentDefinition");
        Objects.requireNonNull(projector, "projector");
        Objects.requireNonNull(stateValidator, "stateValidator");
        TransactionId rollbackId = TransactionId.derive(targetId, idempotencyKey);
        Account account = accountFor(targetId);
        if (account == null) {
            return uncachedRejection(rollbackId, LEDGER_FULL, "Session account capacity is full", 0);
        }
        TransactionResult prior = account.idempotencyResults.get(idempotencyKey);
        if (prior != null) {
            return prior.asReplay();
        }
        TransactionStep step = TransactionStep.empty(ResourceLocation.fromNamespaceAndPath(
                "progressiveskills", "rollback"
        ));
        var plan = new TransactionPlan(
                actorId,
                targetId,
                idempotencyKey,
                expectedStateRevision,
                currentDefinition,
                ProgressionCause.ADMIN,
                reason,
                step
        );
        if (expectedStateRevision != account.revision) {
            return rejectAndCache(account, plan, rollbackId, STALE_STATE,
                    "Rollback expected state revision " + expectedStateRevision + " but found " + account.revision,
                    stateValidator);
        }
        RollbackRecord rollback = account.rollbackRecords.get(originalTransaction);
        if (rollback == null || !rollback.targetId.equals(targetId)) {
            return rejectAndCache(account, plan, rollbackId, ROLLBACK_REJECTED,
                    "Transaction is not retained as a reversible boundary", stateValidator);
        }
        if (rollback.afterRevision != account.revision) {
            return rejectAndCache(account, plan, rollbackId, ROLLBACK_REJECTED,
                    "Later state changes prevent rollback of this transaction", stateValidator);
        }
        List<ProjectionChange> changes = projectionDiff(account.projected, rollback.before.projected);
        Optional<String> rejection;
        try {
            rejection = boundedReason(projector.validate(targetId, changes));
        } catch (RuntimeException exception) {
            return rejectAndCache(account, plan, rollbackId, PROJECTION_FAILED, safeMessage(exception),
                    stateValidator);
        }
        if (rejection.isPresent()) {
            return rejectAndCache(account, plan, rollbackId, PROJECTION_FAILED, rejection.orElseThrow(),
                    stateValidator);
        }
        long beforeRevision = account.revision;
        long committedRevision;
        try {
            committedRevision = Math.addExact(account.revision, 1);
        } catch (ArithmeticException exception) {
            return rejectAndCache(account, plan, rollbackId, STALE_STATE, "State revision overflow",
                    stateValidator);
        }
        CoreState current = account.copyState();
        List<PaidCostMutation> paidCostMutations = paidCostDiff(current.paidCosts, rollback.before.paidCosts);
        var result = new TransactionResult(
                rollbackId,
                TransactionStatus.COMMITTED,
                beforeRevision,
                committedRevision,
                OK,
                "Rollback committed as a new monotonic transaction",
                false,
                changes,
                List.of()
        );
        AuditRecord auditRecord = auditRecord(plan, result, List.of(), paidCostMutations, false);
        PersistenceReservation reservation = reservePersistence(
                account,
                targetId,
                rollback.before,
                committedRevision,
                account.receipts,
                idempotencyKey,
                result,
                auditRecord,
                stateValidator
        );
        if (!reservation.accepted()) {
            return uncachedRejection(
                    rollbackId,
                    LEDGER_FULL,
                    "Persistent transaction state is full. " + reservation.rejection(),
                    account.revision
            );
        }
        account.install(rollback.before, committedRevision);
        try {
            projector.apply(targetId, changes);
        } catch (RuntimeException exception) {
            account.install(current, current.revision);
            return rejectAndCache(account, plan, rollbackId, PROJECTION_FAILED, safeMessage(exception),
                    stateValidator);
        }
        account.rollbackRecords.remove(originalTransaction);
        applyPersistenceRetention(account, reservation, idempotencyKey);
        account.idempotencyResults.put(idempotencyKey, result);
        recordAudit(account, plan, result, List.of(), paidCostMutations, false);
        return result;
    }

    /** Re-resolves existing persistent owners without executing any transition action. */
    public synchronized ProjectionReport recompute(UUID targetId, PersistentProjector projector) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(projector, "projector");
        Account account = accounts.get(targetId);
        if (account == null) {
            return new ProjectionReport(true, "No session state exists", List.of());
        }
        Map<EntitlementKey, Long> desired;
        try {
            desired = resolveEffective(account.ownership);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return new ProjectionReport(false, safeMessage(exception), List.of());
        }
        List<ProjectionChange> changes = projectionDiff(account.projected, desired);
        Optional<String> rejection;
        try {
            rejection = boundedReason(projector.validate(targetId, changes));
        } catch (RuntimeException exception) {
            return new ProjectionReport(false, safeMessage(exception), List.of());
        }
        if (rejection.isPresent()) {
            return new ProjectionReport(false, rejection.orElseThrow(), List.of());
        }
        try {
            projector.apply(targetId, changes);
        } catch (RuntimeException exception) {
            return new ProjectionReport(false, safeMessage(exception), List.of());
        }
        account.projected = new TreeMap<>(desired);
        return new ProjectionReport(true,
                changes.isEmpty() ? "Persistent projection already matches all owners" : "Persistent projection reconciled",
                changes);
    }

    /** Reapplies every desired value to a newly created physical target without changing progression state. */
    public synchronized ProjectionReport forceReproject(UUID targetId, PersistentProjector projector) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(projector, "projector");
        Account account = accounts.get(targetId);
        if (account == null || account.projected.isEmpty()) {
            return new ProjectionReport(true, "No persistent values require re-projection", List.of());
        }
        var changes = new ArrayList<ProjectionChange>();
        account.projected.forEach((key, value) -> changes.add(
                new ProjectionChange(key, OptionalLong.empty(), OptionalLong.of(value))
        ));
        List<ProjectionChange> immutable = List.copyOf(changes);
        Optional<String> rejection;
        try {
            rejection = boundedReason(projector.validate(targetId, immutable));
        } catch (RuntimeException exception) {
            return new ProjectionReport(false, safeMessage(exception), List.of());
        }
        if (rejection.isPresent()) {
            return new ProjectionReport(false, rejection.orElseThrow(), List.of());
        }
        try {
            projector.apply(targetId, immutable);
        } catch (RuntimeException exception) {
            return new ProjectionReport(false, safeMessage(exception), List.of());
        }
        return new ProjectionReport(true, "Persistent values re-projected", immutable);
    }

    public synchronized ProgressionSnapshot snapshot(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        Account account = accounts.get(targetId);
        return account == null ? ProgressionSnapshot.empty() : account.snapshot();
    }

    public synchronized List<AuditRecord> audit(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        Account account = accounts.get(targetId);
        return account == null ? List.of() : List.copyOf(account.auditRecords);
    }

    public synchronized Optional<GrantReceipt> receipt(UUID targetId, ReceiptKey key) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(key, "key");
        Account account = accounts.get(targetId);
        return account == null ? Optional.empty() : Optional.ofNullable(account.receipts.get(key));
    }

    /** Returns the exact durable state needed to resume idempotently after a player unload or restart. */
    public synchronized PersistedTransactionState exportAccount(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        Account account = accounts.get(targetId);
        return account == null ? PersistedTransactionState.empty() : account.persistedState();
    }

    public synchronized boolean hasAccount(UUID targetId) {
        return accounts.containsKey(Objects.requireNonNull(targetId, "targetId"));
    }

    public synchronized Optional<PersistedTransactionState> loadedAccount(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        Account account = accounts.get(targetId);
        return account == null ? Optional.empty() : Optional.of(account.persistedState());
    }

    /**
     * Restores one durable account without projecting it. Callers must keep quarantined data out of
     * this method and invoke {@link #forceReproject(UUID, PersistentProjector)} after a successful load.
     */
    public synchronized void restoreAccount(UUID targetId, PersistedTransactionState state) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(state, "state");
        if (state.receipts().size() > maxReceiptsPerAccount
                || state.idempotencyResults().size() > maxIdempotencyResultsPerAccount
                || state.auditRecords().size() > maxAuditRecordsPerAccount
                || state.paidCosts().size() > maxPaidCostsPerAccount) {
            throw new IllegalArgumentException("Persisted transaction state exceeds configured ledger limits");
        }
        for (AuditRecord record : state.auditRecords()) {
            if (!record.targetId().equals(targetId)) {
                throw new IllegalArgumentException("Persisted audit target does not match attachment owner");
            }
        }
        Map<EntitlementKey, Long> projected = resolveEffective(state.ownership());
        Account existing = accounts.get(targetId);
        if (existing != null) {
            if (existing.revision > state.stateRevision()) {
                return;
            }
            if (existing.revision == state.stateRevision()) {
                if (!existing.persistedState().equals(state)) {
                    throw new IllegalStateException("Equal-revision persisted state differs from the live account");
                }
                return;
            }
        } else if (accounts.size() >= maxAccounts) {
            throw new IllegalStateException("Transaction account capacity is full");
        }

        Account restored = existing == null ? new Account() : existing;
        restored.install(new CoreState(
                state.stateRevision(),
                copyBalances(state.balances()),
                copyOwnership(state.ownership()),
                copyPaidCosts(state.paidCosts()),
                new TreeMap<>(projected)
        ), state.stateRevision());
        restored.receipts.clear();
        restored.receipts.putAll(state.receipts());
        restored.idempotencyResults.clear();
        restored.idempotencyResults.putAll(state.idempotencyResults());
        restored.auditRecords.clear();
        restored.auditRecords.addAll(state.auditRecords());
        restored.rollbackRecords.clear();
        accounts.put(targetId, restored);
    }

    /** Removes an unloaded player's cache entry after its attachment has captured the exported state. */
    public synchronized void unloadAccount(UUID targetId) {
        accounts.remove(Objects.requireNonNull(targetId, "targetId"));
    }

    private Account accountFor(UUID targetId) {
        Account existing = accounts.get(targetId);
        if (existing != null) {
            return existing;
        }
        if (accounts.size() >= maxAccounts) {
            return null;
        }
        var created = new Account();
        accounts.put(targetId, created);
        return created;
    }

    private List<TransitionActionResult> executeActions(
            Account account,
            TransactionPlan plan,
            TransactionId transactionId,
            List<TransitionAction> actions,
            TransitionActionExecutor executor
    ) {
        var results = new ArrayList<TransitionActionResult>();
        boolean stopped = false;
        for (TransitionAction action : actions) {
            if (stopped) {
                results.add(new TransitionActionResult(
                        action,
                        ActionDisposition.SKIPPED_AFTER_FAILURE,
                        "Skipped because an earlier action used stop-on-failure"
                ));
                continue;
            }
            Optional<ReceiptKey> receiptKey = receiptKey(plan.targetId(), transactionId, action);
            if (receiptKey.isPresent() && account.receipts.containsKey(receiptKey.orElseThrow())) {
                results.add(new TransitionActionResult(
                        action,
                        ActionDisposition.SKIPPED_EXISTING_RECEIPT,
                        "Existing exact receipt prevented duplicate delivery"
                ));
                continue;
            }
            ActionExecution execution;
            try {
                execution = Objects.requireNonNull(
                        executor.execute(plan.targetId(), transactionId, action),
                        "action execution"
                );
            } catch (RuntimeException exception) {
                execution = ActionExecution.failure(safeMessage(exception));
            }
            if (execution.successful()) {
                if (receiptKey.isPresent()) {
                    var receipt = new GrantReceipt(
                            receiptKey.orElseThrow(),
                            transactionId,
                            plan.definitionRevision(),
                            clock.instant(),
                            action.deliveryContract(),
                            execution.detail()
                    );
                    account.receipts.put(receipt.key(), receipt);
                }
                results.add(new TransitionActionResult(action, ActionDisposition.EXECUTED, execution.detail()));
            } else {
                results.add(new TransitionActionResult(action, ActionDisposition.FAILED, execution.detail()));
                stopped = action.failurePolicy() == TransitionFailurePolicy.STOP;
            }
        }
        return List.copyOf(results);
    }

    private PersistenceReservation reserveCommitPersistence(
            Account account,
            CoreState working,
            long committedRevision,
            TransactionPlan plan,
            TransactionId transactionId,
            List<BalanceMutation> balanceMutations,
            List<PaidCostMutation> paidCostMutations,
            List<ProjectionChange> projectionChanges,
            List<TransitionAction> actions,
            Set<ReceiptKey> newReceiptKeys,
            PersistentStateValidator stateValidator
    ) {
        var actionResults = new ArrayList<TransitionActionResult>();
        actions.forEach(action -> actionResults.add(new TransitionActionResult(
                action,
                ActionDisposition.SKIPPED_EXISTING_RECEIPT,
                MAXIMUM_ACTION_DETAIL
        )));
        TransactionStatus status = actions.isEmpty()
                ? TransactionStatus.COMMITTED
                : TransactionStatus.COMMITTED_WITH_ACTION_FAILURES;
        String message = actions.isEmpty()
                ? "Transaction committed"
                : "State committed; one or more transition actions failed and were audited";
        var result = new TransactionResult(
                transactionId,
                status,
                account.revision,
                committedRevision,
                actions.isEmpty() ? OK : ACTION_REJECTED,
                message,
                false,
                projectionChanges,
                actionResults
        );
        var receipts = new TreeMap<>(account.receipts);
        for (TransitionAction action : actions) {
            Optional<ReceiptKey> key = receiptKey(plan.targetId(), transactionId, action);
            if (key.isPresent() && newReceiptKeys.contains(key.orElseThrow())) {
                receipts.putIfAbsent(key.orElseThrow(), new GrantReceipt(
                        key.orElseThrow(),
                        transactionId,
                        plan.definitionRevision(),
                        clock.instant(),
                        DeliveryContract.EFFECTIVELY_ONCE,
                        MAXIMUM_ACTION_DETAIL
                ));
            }
        }
        AuditRecord auditRecord = auditRecord(
                plan,
                result,
                balanceMutations,
                paidCostMutations,
                actions.isEmpty()
        );
        return reservePersistence(
                account,
                plan.targetId(),
                working,
                committedRevision,
                receipts,
                plan.idempotencyKey(),
                result,
                auditRecord,
                stateValidator
        );
    }

    private PersistenceReservation reservePersistence(
            Account account,
            UUID targetId,
            CoreState state,
            long stateRevision,
            Map<ReceiptKey, GrantReceipt> receipts,
            IdempotencyKey idempotencyKey,
            TransactionResult result,
            AuditRecord auditRecord,
            PersistentStateValidator stateValidator
    ) {
        var idempotency = new TreeMap<>(account.idempotencyResults);
        idempotency.put(idempotencyKey, result);
        int idempotencyEvictions = 0;
        while (idempotency.size() > maxIdempotencyResultsPerAccount) {
            if (!removeOldestIdempotency(idempotency, idempotencyKey)) {
                return PersistenceReservation.rejected("Replay retention cannot reserve the current result");
            }
            idempotencyEvictions++;
        }

        var audits = new ArrayDeque<>(account.auditRecords);
        audits.addLast(auditRecord);
        int auditEvictions = 0;
        while (audits.size() > maxAuditRecordsPerAccount) {
            audits.removeFirst();
            auditEvictions++;
        }

        String rejection = persistenceRejection(
                state,
                stateRevision,
                receipts,
                idempotency,
                audits,
                stateValidator,
                targetId
        );
        while (!rejection.isEmpty() && audits.size() > 1) {
            int targetSize = Math.max(1, audits.size() / 2);
            while (audits.size() > targetSize) {
                audits.removeFirst();
                auditEvictions++;
            }
            rejection = persistenceRejection(
                    state, stateRevision, receipts, idempotency, audits, stateValidator, targetId
            );
        }
        while (!rejection.isEmpty() && idempotency.size() > 1) {
            int targetSize = Math.max(1, idempotency.size() / 2);
            while (idempotency.size() > targetSize) {
                if (!removeOldestIdempotency(idempotency, idempotencyKey)) {
                    break;
                }
                idempotencyEvictions++;
            }
            rejection = persistenceRejection(
                    state, stateRevision, receipts, idempotency, audits, stateValidator, targetId
            );
        }
        if (!rejection.isEmpty()) {
            return PersistenceReservation.rejected(rejection);
        }
        return PersistenceReservation.accepted(idempotencyEvictions, auditEvictions);
    }

    private static String persistenceRejection(
            CoreState state,
            long stateRevision,
            Map<ReceiptKey, GrantReceipt> receipts,
            Map<IdempotencyKey, TransactionResult> idempotency,
            ArrayDeque<AuditRecord> audits,
            PersistentStateValidator stateValidator,
            UUID targetId
    ) {
        try {
            var candidate = new PersistedTransactionState(
                    stateRevision,
                    state.balances,
                    state.ownership,
                    state.paidCosts,
                    receipts,
                    idempotency,
                    List.copyOf(audits)
            );
            return boundedReason(stateValidator.rejection(targetId, candidate)).orElse("");
        } catch (RuntimeException exception) {
            return safeMessage(exception);
        }
    }

    private static boolean removeOldestIdempotency(
            Map<IdempotencyKey, TransactionResult> results,
            IdempotencyKey protectedKey
    ) {
        IdempotencyKey oldestKey = null;
        TransactionResult oldestResult = null;
        for (var entry : results.entrySet()) {
            if (entry.getKey().equals(protectedKey)) {
                continue;
            }
            TransactionResult candidate = entry.getValue();
            if (oldestResult == null
                    || candidate.afterRevision() < oldestResult.afterRevision()
                    || candidate.afterRevision() == oldestResult.afterRevision()
                    && candidate.beforeRevision() < oldestResult.beforeRevision()
                    || candidate.afterRevision() == oldestResult.afterRevision()
                    && candidate.beforeRevision() == oldestResult.beforeRevision()
                    && entry.getKey().compareTo(oldestKey) < 0) {
                oldestKey = entry.getKey();
                oldestResult = candidate;
            }
        }
        if (oldestKey == null) {
            return false;
        }
        results.remove(oldestKey);
        return true;
    }

    private static void applyPersistenceRetention(
            Account account,
            PersistenceReservation reservation,
            IdempotencyKey protectedKey
    ) {
        for (int index = 0; index < reservation.idempotencyEvictions(); index++) {
            if (!removeOldestIdempotency(account.idempotencyResults, protectedKey)) {
                throw new IllegalStateException("Reserved replay retention could not be applied");
            }
        }
        for (int index = 0; index < reservation.auditEvictions(); index++) {
            AuditRecord removed = account.auditRecords.removeFirst();
            account.rollbackRecords.remove(removed.transactionId());
        }
    }

    private TransactionResult rejectAndCache(
            Account account,
            TransactionPlan plan,
            TransactionId id,
            String code,
            String message,
            PersistentStateValidator stateValidator
    ) {
        var result = new TransactionResult(
                id,
                TransactionStatus.REJECTED,
                account.revision,
                account.revision,
                code,
                message,
                false,
                List.of(),
                List.of()
        );
        AuditRecord auditRecord = auditRecord(plan, result, List.of(), List.of(), false);
        PersistenceReservation reservation = reservePersistence(
                account,
                plan.targetId(),
                account.copyState(),
                account.revision,
                account.receipts,
                plan.idempotencyKey(),
                result,
                auditRecord,
                stateValidator
        );
        if (!reservation.accepted()) {
            return uncachedRejection(
                    id,
                    LEDGER_FULL,
                    "Persistent transaction state is full. " + reservation.rejection(),
                    account.revision
            );
        }
        applyPersistenceRetention(account, reservation, plan.idempotencyKey());
        account.idempotencyResults.put(plan.idempotencyKey(), result);
        appendAudit(account, auditRecord);
        return result;
    }

    private static TransactionResult uncachedRejection(
            TransactionId id,
            String code,
            String message,
            long revision
    ) {
        return new TransactionResult(
                id,
                TransactionStatus.REJECTED,
                revision,
                revision,
                code,
                message,
                false,
                List.of(),
                List.of()
        );
    }

    private void recordAudit(
            Account account,
            TransactionPlan plan,
            TransactionResult result,
            List<BalanceMutation> balances,
            List<PaidCostMutation> paidCosts,
            boolean reversible
    ) {
        appendAudit(account, auditRecord(plan, result, balances, paidCosts, reversible));
    }

    private AuditRecord auditRecord(
            TransactionPlan plan,
            TransactionResult result,
            List<BalanceMutation> balances,
            List<PaidCostMutation> paidCosts,
            boolean reversible
    ) {
        return new AuditRecord(
                result.transactionId(),
                plan.actorId(),
                plan.targetId(),
                plan.cause(),
                plan.reason(),
                plan.definitionRevision(),
                clock.instant(),
                result.status(),
                result.beforeRevision(),
                result.afterRevision(),
                reversible,
                balances,
                paidCosts,
                result.projectionChanges(),
                result.actionResults(),
                result.message()
        );
    }

    private void appendAudit(Account account, AuditRecord record) {
        account.auditRecords.addLast(record);
        while (account.auditRecords.size() > maxAuditRecordsPerAccount) {
            AuditRecord removed = account.auditRecords.removeFirst();
            account.rollbackRecords.remove(removed.transactionId());
        }
    }

    private static List<BalanceMutation> flattenBalanceMutations(CascadePlan cascade) {
        return cascade.steps().stream().flatMap(step -> step.balanceMutations().stream()).toList();
    }

    private static List<EntitlementMutation> flattenEntitlementMutations(CascadePlan cascade) {
        return cascade.steps().stream().flatMap(step -> step.entitlementMutations().stream()).toList();
    }

    private static List<PaidCostMutation> flattenPaidCostMutations(CascadePlan cascade) {
        return cascade.steps().stream().flatMap(step -> step.paidCostMutations().stream()).toList();
    }

    private static List<TransitionAction> flattenActions(CascadePlan cascade) {
        return cascade.steps().stream().flatMap(step -> step.transitionActions().stream()).toList();
    }

    private static void applyBalances(
            Map<ResourceLocation, Long> balances,
            List<BalanceMutation> mutations
    ) {
        for (BalanceMutation mutation : mutations) {
            long before = balances.getOrDefault(mutation.balanceId(), 0L);
            long after = Math.addExact(before, mutation.delta());
            if (after < mutation.minimum() || after > mutation.maximum()) {
                throw new ArithmeticException("Balance " + mutation.balanceId() + " would become " + after
                        + " outside " + mutation.minimum() + ".." + mutation.maximum());
            }
            balances.put(mutation.balanceId(), after);
        }
    }

    private static void applyEntitlements(
            Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership,
            List<EntitlementMutation> mutations
    ) {
        for (EntitlementMutation mutation : mutations) {
            var owners = ownership.computeIfAbsent(mutation.key(), ignored -> new TreeMap<>());
            if (mutation.contribution().isPresent()) {
                owners.put(mutation.source(), mutation.contribution().orElseThrow());
            } else {
                owners.remove(mutation.source());
            }
            if (owners.isEmpty()) {
                ownership.remove(mutation.key());
            }
        }
    }

    private static void applyPaidCosts(
            Map<PurchaseInstanceId, PaidCostRecord> paidCosts,
            List<PaidCostMutation> mutations,
            int maximum
    ) {
        for (PaidCostMutation mutation : mutations) {
            Optional<PaidCostRecord> current = Optional.ofNullable(paidCosts.get(mutation.instanceId()));
            if (!current.equals(mutation.expected())) {
                throw new IllegalArgumentException("Paid cost state does not match expected record for "
                        + mutation.instanceId());
            }
            if (mutation.replacement().isPresent()) {
                paidCosts.put(mutation.instanceId(), mutation.replacement().orElseThrow());
            } else {
                paidCosts.remove(mutation.instanceId());
            }
            if (paidCosts.size() > maximum) {
                throw new PaidCostCapacityException("Exact paid cost ledger is full");
            }
        }
    }

    private static List<PaidCostMutation> paidCostDiff(
            Map<PurchaseInstanceId, PaidCostRecord> before,
            Map<PurchaseInstanceId, PaidCostRecord> after
    ) {
        var keys = new java.util.TreeSet<PurchaseInstanceId>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        var mutations = new ArrayList<PaidCostMutation>();
        for (PurchaseInstanceId key : keys) {
            Optional<PaidCostRecord> oldValue = Optional.ofNullable(before.get(key));
            Optional<PaidCostRecord> newValue = Optional.ofNullable(after.get(key));
            if (!oldValue.equals(newValue)) {
                mutations.add(new PaidCostMutation(key, oldValue, newValue));
            }
        }
        return List.copyOf(mutations);
    }

    private static Map<EntitlementKey, Long> resolveEffective(
            Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership
    ) {
        var resolved = new TreeMap<EntitlementKey, Long>();
        for (var entry : ownership.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            EntitlementResolver resolver = null;
            long value = 0;
            boolean first = true;
            for (EntitlementContribution contribution : entry.getValue().values()) {
                if (resolver == null) {
                    resolver = contribution.resolver();
                } else if (resolver != contribution.resolver()) {
                    throw new IllegalArgumentException("Entitlement " + entry.getKey()
                            + " mixes resolver " + resolver + " with " + contribution.resolver());
                }
                if (first) {
                    value = contribution.value();
                    first = false;
                    continue;
                }
                value = switch (resolver) {
                    case ADDITIVE -> Math.addExact(value, contribution.value());
                    case HIGHEST -> Math.max(value, contribution.value());
                    case LOWEST -> Math.min(value, contribution.value());
                    case BOOLEAN_UNION -> value == 1 || contribution.value() == 1 ? 1 : 0;
                };
            }
            resolved.put(entry.getKey(), value);
        }
        return resolved;
    }

    private static List<ProjectionChange> projectionDiff(
            Map<EntitlementKey, Long> before,
            Map<EntitlementKey, Long> after
    ) {
        var keys = new java.util.TreeSet<EntitlementKey>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        var changes = new ArrayList<ProjectionChange>();
        for (EntitlementKey key : keys) {
            Long oldValue = before.get(key);
            Long newValue = after.get(key);
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            changes.add(new ProjectionChange(
                    key,
                    oldValue == null ? OptionalLong.empty() : OptionalLong.of(oldValue),
                    newValue == null ? OptionalLong.empty() : OptionalLong.of(newValue)
            ));
        }
        return List.copyOf(changes);
    }

    private static Optional<ReceiptKey> receiptKey(
            UUID targetId,
            TransactionId transactionId,
            TransitionAction action
    ) {
        return switch (action.repeatPolicy()) {
            case ALWAYS -> Optional.empty();
            case ONCE_PER_TRANSACTION -> Optional.of(new ReceiptKey(
                    action.source(),
                    action.repeatPolicy(),
                    "transaction:" + transactionId
            ));
            case ONCE_PER_CHARACTER -> Optional.of(new ReceiptKey(
                    action.source(),
                    action.repeatPolicy(),
                    "character:" + targetId
            ));
        };
    }

    private static Optional<String> boundedReason(Optional<String> reason) {
        Objects.requireNonNull(reason, "rejection reason");
        if (reason.isEmpty()) {
            return Optional.empty();
        }
        String normalized = Objects.requireNonNull(reason.orElseThrow(), "rejection reason value").strip();
        if (normalized.isEmpty()) {
            return Optional.of("Adapter rejected the operation without a reason");
        }
        return Optional.of(normalized.substring(0, Math.min(normalized.length(), 512)));
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 512));
    }

    private record PersistenceReservation(
            boolean accepted,
            int idempotencyEvictions,
            int auditEvictions,
            String rejection
    ) {
        private static PersistenceReservation accepted(int idempotencyEvictions, int auditEvictions) {
            return new PersistenceReservation(true, idempotencyEvictions, auditEvictions, "");
        }

        private static PersistenceReservation rejected(String rejection) {
            return new PersistenceReservation(false, 0, 0, rejection);
        }
    }

    private static final class Account {
        private long revision;
        private Map<ResourceLocation, Long> balances = new TreeMap<>(ResourceLocation::compareNamespaced);
        private Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership = new TreeMap<>();
        private Map<PurchaseInstanceId, PaidCostRecord> paidCosts = new TreeMap<>();
        private Map<EntitlementKey, Long> projected = new TreeMap<>();
        private final Map<ReceiptKey, GrantReceipt> receipts = new TreeMap<>();
        private final Map<IdempotencyKey, TransactionResult> idempotencyResults = new TreeMap<>();
        private final ArrayDeque<AuditRecord> auditRecords = new ArrayDeque<>();
        private final Map<TransactionId, RollbackRecord> rollbackRecords = new TreeMap<>();

        private CoreState copyState() {
            return new CoreState(revision, copyBalances(balances), copyOwnership(ownership),
                    copyPaidCosts(paidCosts), new TreeMap<>(projected));
        }

        private void install(CoreState state, long newRevision) {
            balances = copyBalances(state.balances);
            ownership = copyOwnership(state.ownership);
            paidCosts = copyPaidCosts(state.paidCosts);
            projected = new TreeMap<>(state.projected);
            revision = newRevision;
        }

        private ProgressionSnapshot snapshot() {
            return new ProgressionSnapshot(
                    revision,
                    balances,
                    ownership,
                    paidCosts,
                    projected,
                    receipts.size(),
                    idempotencyResults.size(),
                    auditRecords.size()
            );
        }

        private PersistedTransactionState persistedState() {
            return new PersistedTransactionState(
                    revision,
                    balances,
                    ownership,
                    paidCosts,
                    receipts,
                    idempotencyResults,
                    List.copyOf(auditRecords)
            );
        }
    }

    private static final class CoreState {
        private final long revision;
        private final Map<ResourceLocation, Long> balances;
        private final Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership;
        private final Map<PurchaseInstanceId, PaidCostRecord> paidCosts;
        private Map<EntitlementKey, Long> projected;

        private CoreState(
                long revision,
                Map<ResourceLocation, Long> balances,
                Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership,
                Map<PurchaseInstanceId, PaidCostRecord> paidCosts,
                Map<EntitlementKey, Long> projected
        ) {
            this.revision = revision;
            this.balances = balances;
            this.ownership = ownership;
            this.paidCosts = paidCosts;
            this.projected = projected;
        }

        private CoreState copy() {
            return new CoreState(revision, copyBalances(balances), copyOwnership(ownership),
                    copyPaidCosts(paidCosts), new TreeMap<>(projected));
        }
    }

    private record RollbackRecord(UUID targetId, long afterRevision, CoreState before) {
        private RollbackRecord {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(before, "before");
        }
    }

    private static Map<ResourceLocation, Long> copyBalances(Map<ResourceLocation, Long> source) {
        var result = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        result.putAll(source);
        return result;
    }

    private static Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> copyOwnership(
            Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> source
    ) {
        var result = new TreeMap<EntitlementKey, Map<GrantSourceId, EntitlementContribution>>();
        source.forEach((key, owners) -> result.put(key, new TreeMap<>(owners)));
        return result;
    }

    private static Map<PurchaseInstanceId, PaidCostRecord> copyPaidCosts(
            Map<PurchaseInstanceId, PaidCostRecord> source
    ) {
        return new TreeMap<>(source);
    }

    private static final class PaidCostCapacityException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private PaidCostCapacityException(String message) {
            super(message);
        }
    }
}
