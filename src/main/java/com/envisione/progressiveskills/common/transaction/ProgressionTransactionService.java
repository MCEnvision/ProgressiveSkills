package com.envisione.progressiveskills.common.transaction;

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

/**
 * Bounded single-authority transaction coordinator.
 *
 * <p>All plans are fully validated against copied state before an atomic persistent projection and
 * in-memory commit. Transition actions run only after commit. Exact receipts and idempotency results
 * are fail-closed and never evicted; audit and reversible snapshots use explicit bounded retention.</p>
 */
public final class ProgressionTransactionService {
    public static final String OK = "PS-TX-OK";
    public static final String STALE_STATE = CoreDiagnostics.STALE_TRANSACTION_STATE.value();
    public static final String BALANCE_REJECTED = CoreDiagnostics.BALANCE_TRANSACTION_REJECTED.value();
    public static final String STALE_DEFINITION = CoreDiagnostics.STALE_TRANSACTION_DEFINITION.value();
    public static final String LIFECYCLE_REJECTED = CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP.value();
    public static final String ACTION_REJECTED = CoreDiagnostics.TRANSITION_ACTION_REJECTED.value();
    public static final String LEDGER_FULL = CoreDiagnostics.TRANSACTION_LEDGER_FULL.value();
    public static final String PROJECTION_FAILED = CoreDiagnostics.PERSISTENT_PROJECTION_FAILED.value();
    public static final String ROLLBACK_REJECTED = CoreDiagnostics.TRANSACTION_ROLLBACK_REJECTED.value();

    private final int maxAccounts;
    private final int maxReceiptsPerAccount;
    private final int maxIdempotencyResultsPerAccount;
    private final int maxAuditRecordsPerAccount;
    private final Clock clock;
    private final Map<UUID, Account> accounts = new LinkedHashMap<>();

    public ProgressionTransactionService(
            int maxAccounts,
            int maxReceiptsPerAccount,
            int maxIdempotencyResultsPerAccount,
            int maxAuditRecordsPerAccount,
            Clock clock
    ) {
        if (maxAccounts < 1 || maxReceiptsPerAccount < 1
                || maxIdempotencyResultsPerAccount < 1 || maxAuditRecordsPerAccount < 1) {
            throw new IllegalArgumentException("Transaction service limits must be positive");
        }
        this.maxAccounts = maxAccounts;
        this.maxReceiptsPerAccount = maxReceiptsPerAccount;
        this.maxIdempotencyResultsPerAccount = maxIdempotencyResultsPerAccount;
        this.maxAuditRecordsPerAccount = maxAuditRecordsPerAccount;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static ProgressionTransactionService boundedDefaults() {
        return new ProgressionTransactionService(256, 512, 512, 256, Clock.systemUTC());
    }

    public synchronized TransactionResult execute(
            CascadePlan cascade,
            DefinitionRevision currentDefinition,
            PersistentProjector projector,
            TransitionActionExecutor actionExecutor
    ) {
        Objects.requireNonNull(cascade, "cascade");
        Objects.requireNonNull(currentDefinition, "currentDefinition");
        Objects.requireNonNull(projector, "projector");
        Objects.requireNonNull(actionExecutor, "actionExecutor");
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
        if (account.idempotencyResults.size() >= maxIdempotencyResultsPerAccount) {
            return uncachedRejection(
                    transactionId, LEDGER_FULL, "Exact idempotency ledger is full", account.revision
            );
        }
        if (!plan.definitionRevision().equals(currentDefinition)) {
            return rejectAndCache(account, plan, transactionId, STALE_DEFINITION,
                    "Definition generation or digest changed after the plan was captured");
        }
        if (plan.expectedStateRevision() != account.revision) {
            return rejectAndCache(account, plan, transactionId, STALE_STATE,
                    "Expected state revision " + plan.expectedStateRevision() + " but found " + account.revision);
        }

        CoreState before = account.copyState();
        CoreState working = before.copy();
        List<BalanceMutation> balanceMutations = flattenBalanceMutations(cascade);
        List<TransitionAction> actions = flattenActions(cascade);
        try {
            applyBalances(working.balances, balanceMutations);
            applyEntitlements(working.ownership, flattenEntitlementMutations(cascade));
            working.projected = resolveEffective(working.ownership);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            String code = exception instanceof ArithmeticException ? BALANCE_REJECTED : LIFECYCLE_REJECTED;
            return rejectAndCache(account, plan, transactionId, code, safeMessage(exception));
        }

        List<ProjectionChange> projectionChanges = projectionDiff(before.projected, working.projected);
        Optional<String> projectionRejection;
        try {
            projectionRejection = boundedReason(projector.validate(plan.targetId(), projectionChanges));
        } catch (RuntimeException exception) {
            return rejectAndCache(account, plan, transactionId, PROJECTION_FAILED, safeMessage(exception));
        }
        if (projectionRejection.isPresent()) {
            return rejectAndCache(account, plan, transactionId, PROJECTION_FAILED, projectionRejection.orElseThrow());
        }

        Set<ReceiptKey> newReceiptKeys = new HashSet<>();
        for (TransitionAction action : actions) {
            Optional<ReceiptKey> receiptKey = receiptKey(plan.targetId(), transactionId, action);
            if (receiptKey.isPresent() && !account.receipts.containsKey(receiptKey.orElseThrow())) {
                newReceiptKeys.add(receiptKey.orElseThrow());
            }
            if (receiptKey.isPresent() && account.receipts.containsKey(receiptKey.orElseThrow())) {
                continue;
            }
            Optional<String> actionRejection;
            try {
                actionRejection = boundedReason(actionExecutor.validate(plan.targetId(), action));
            } catch (RuntimeException exception) {
                return rejectAndCache(account, plan, transactionId, ACTION_REJECTED, safeMessage(exception));
            }
            if (actionRejection.isPresent()) {
                return rejectAndCache(account, plan, transactionId, ACTION_REJECTED,
                        actionRejection.orElseThrow());
            }
        }
        if ((long) account.receipts.size() + newReceiptKeys.size() > maxReceiptsPerAccount) {
            return rejectAndCache(account, plan, transactionId, LEDGER_FULL,
                    "Exact transition receipt ledger is full");
        }

        long committedRevision;
        try {
            committedRevision = Math.addExact(account.revision, 1);
        } catch (ArithmeticException exception) {
            return rejectAndCache(account, plan, transactionId, STALE_STATE, "State revision overflow");
        }
        account.install(working, committedRevision);
        try {
            projector.apply(plan.targetId(), projectionChanges);
        } catch (RuntimeException exception) {
            account.install(before, before.revision);
            return rejectAndCache(account, plan, transactionId, PROJECTION_FAILED,
                    "Persistent projection rejected atomically: " + safeMessage(exception));
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
        account.idempotencyResults.put(plan.idempotencyKey(), result);
        boolean reversible = actions.isEmpty();
        recordAudit(account, plan, result, balanceMutations, reversible);
        if (reversible) {
            account.rollbackRecords.put(transactionId, new RollbackRecord(plan.targetId(), account.revision, before));
        }
        return result;
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
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(originalTransaction, "originalTransaction");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(currentDefinition, "currentDefinition");
        Objects.requireNonNull(projector, "projector");
        TransactionId rollbackId = TransactionId.derive(targetId, idempotencyKey);
        Account account = accountFor(targetId);
        if (account == null) {
            return uncachedRejection(rollbackId, LEDGER_FULL, "Session account capacity is full", 0);
        }
        TransactionResult prior = account.idempotencyResults.get(idempotencyKey);
        if (prior != null) {
            return prior.asReplay();
        }
        if (account.idempotencyResults.size() >= maxIdempotencyResultsPerAccount) {
            return uncachedRejection(rollbackId, LEDGER_FULL, "Exact idempotency ledger is full", account.revision);
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
                    "Rollback expected state revision " + expectedStateRevision + " but found " + account.revision);
        }
        RollbackRecord rollback = account.rollbackRecords.get(originalTransaction);
        if (rollback == null || !rollback.targetId.equals(targetId)) {
            return rejectAndCache(account, plan, rollbackId, ROLLBACK_REJECTED,
                    "Transaction is not retained as a reversible boundary");
        }
        if (rollback.afterRevision != account.revision) {
            return rejectAndCache(account, plan, rollbackId, ROLLBACK_REJECTED,
                    "Later state changes prevent rollback of this transaction");
        }
        List<ProjectionChange> changes = projectionDiff(account.projected, rollback.before.projected);
        Optional<String> rejection;
        try {
            rejection = boundedReason(projector.validate(targetId, changes));
        } catch (RuntimeException exception) {
            return rejectAndCache(account, plan, rollbackId, PROJECTION_FAILED, safeMessage(exception));
        }
        if (rejection.isPresent()) {
            return rejectAndCache(account, plan, rollbackId, PROJECTION_FAILED, rejection.orElseThrow());
        }
        long beforeRevision = account.revision;
        long committedRevision;
        try {
            committedRevision = Math.addExact(account.revision, 1);
        } catch (ArithmeticException exception) {
            return rejectAndCache(account, plan, rollbackId, STALE_STATE, "State revision overflow");
        }
        CoreState current = account.copyState();
        account.install(rollback.before, committedRevision);
        try {
            projector.apply(targetId, changes);
        } catch (RuntimeException exception) {
            account.install(current, current.revision);
            return rejectAndCache(account, plan, rollbackId, PROJECTION_FAILED, safeMessage(exception));
        }
        account.rollbackRecords.remove(originalTransaction);
        var result = new TransactionResult(
                rollbackId,
                TransactionStatus.COMMITTED,
                beforeRevision,
                account.revision,
                OK,
                "Rollback committed as a new monotonic transaction",
                false,
                changes,
                List.of()
        );
        account.idempotencyResults.put(idempotencyKey, result);
        recordAudit(account, plan, result, List.of(), false);
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

    private TransactionResult rejectAndCache(
            Account account,
            TransactionPlan plan,
            TransactionId id,
            String code,
            String message
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
        account.idempotencyResults.put(plan.idempotencyKey(), result);
        recordAudit(account, plan, result, List.of(), false);
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
            boolean reversible
    ) {
        var record = new AuditRecord(
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
                result.projectionChanges(),
                result.actionResults(),
                result.message()
        );
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
            if (after == 0) {
                balances.remove(mutation.balanceId());
            } else {
                balances.put(mutation.balanceId(), after);
            }
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

    private static final class Account {
        private long revision;
        private Map<ResourceLocation, Long> balances = new TreeMap<>(ResourceLocation::compareNamespaced);
        private Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership = new TreeMap<>();
        private Map<EntitlementKey, Long> projected = new TreeMap<>();
        private final Map<ReceiptKey, GrantReceipt> receipts = new TreeMap<>();
        private final Map<IdempotencyKey, TransactionResult> idempotencyResults = new TreeMap<>();
        private final ArrayDeque<AuditRecord> auditRecords = new ArrayDeque<>();
        private final Map<TransactionId, RollbackRecord> rollbackRecords = new TreeMap<>();

        private CoreState copyState() {
            return new CoreState(revision, copyBalances(balances), copyOwnership(ownership), new TreeMap<>(projected));
        }

        private void install(CoreState state, long newRevision) {
            balances = copyBalances(state.balances);
            ownership = copyOwnership(state.ownership);
            projected = new TreeMap<>(state.projected);
            revision = newRevision;
        }

        private ProgressionSnapshot snapshot() {
            return new ProgressionSnapshot(
                    revision,
                    balances,
                    ownership,
                    projected,
                    receipts.size(),
                    idempotencyResults.size(),
                    auditRecords.size()
            );
        }
    }

    private static final class CoreState {
        private final long revision;
        private final Map<ResourceLocation, Long> balances;
        private final Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership;
        private Map<EntitlementKey, Long> projected;

        private CoreState(
                long revision,
                Map<ResourceLocation, Long> balances,
                Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership,
                Map<EntitlementKey, Long> projected
        ) {
            this.revision = revision;
            this.balances = balances;
            this.ownership = ownership;
            this.projected = projected;
        }

        private CoreState copy() {
            return new CoreState(revision, copyBalances(balances), copyOwnership(ownership), new TreeMap<>(projected));
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
}
