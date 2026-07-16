package com.envisione.progressiveskills.server.offline;

import com.envisione.progressiveskills.common.data.OperationReceipt;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Two-save recovery coordinator for pending offline operations applied on player login. */
public final class PendingOperationCoordinator {
    private static final ResourceLocation ORIGIN = id("offline_operation");

    private PendingOperationCoordinator() {
    }

    public static void applyOnLogin(
            ServerPlayer player,
            TransactionRuntime.Context context,
            UUID loginAttemptId
    ) {
        ProgressiveSkillsData data = player.getData(PsDataAttachments.PLAYER_DATA);
        if (!data.active()) {
            return;
        }
        Optional<DefinitionRevision> currentDefinition = TransactionRuntime.currentDefinition();
        if (currentDefinition.isEmpty()) {
            return;
        }
        PendingOperationSavedData store = PendingOperationSavedData.get(context.server());
        if (store.storeQuarantine().isPresent()) {
            return;
        }
        for (PendingProgressionOperation original : store.pendingFor(player.getUUID())) {
            Optional<OperationReceipt> receipt = data.operationReceipt(original.operationId());
            if (receipt.isPresent()) {
                if (original.lastAttemptId().isEmpty()
                        || !original.lastAttemptId().orElseThrow().equals(loginAttemptId)) {
                    store.consume(original.operationId());
                }
                continue;
            }
            if (!data.canAcceptOperationReceipt(original.operationId())) {
                store.quarantine(original.operationId(), "Player operation-receipt capacity is full");
                continue;
            }
            if (!original.expiresAt().isAfter(Instant.now())) {
                store.quarantine(original.operationId(), "Pending operation expired before player login");
                continue;
            }
            Optional<PendingProgressionOperation> rebased = rebase(original, currentDefinition.orElseThrow());
            if (rebased.isEmpty()) {
                store.quarantine(original.operationId(),
                        "Definition changed without an equivalent explicit currency replacement");
                continue;
            }
            PendingProgressionOperation operation = rebased.orElseThrow();
            if (!operation.equals(original)) {
                store.rebase(operation.operationId(), operation.definitionRevision(), operation.balanceId());
            }
            var step = new TransactionStep(
                    ORIGIN,
                    List.of(new BalanceMutation(
                            operation.balanceId(),
                            operation.delta(),
                            operation.minimum(),
                            operation.maximum()
                    )),
                    List.of(),
                    List.of()
            );
            var plan = CascadePlan.single(new TransactionPlan(
                    operation.issuerId(),
                    operation.targetId(),
                    new IdempotencyKey("offline/" + operation.operationId()),
                    context.service().snapshot(player.getUUID()).stateRevision(),
                    currentDefinition.orElseThrow(),
                    ProgressionCause.OFFLINE_OPERATION,
                    "Pending offline balance operation; reward eligibility " + operation.rewardEligible(),
                    step
            ));
            var result = context.executeAndPersist(player, plan, currentDefinition.orElseThrow());
            if (!result.status().committed()) {
                store.quarantine(operation.operationId(),
                        result.diagnosticCode() + ": " + result.message());
                continue;
            }
            data.putOperationReceipt(new OperationReceipt(
                    operation.operationId(),
                    "offline",
                    result.transactionId(),
                    result.afterRevision(),
                    Instant.now(),
                    "Pending offline operation applied; SavedData awaits a later-login receipt confirmation"
            ));
            context.persist(player, currentDefinition.orElseThrow());
            store.recordAttempt(operation.operationId(), loginAttemptId);
        }
    }

    private static Optional<PendingProgressionOperation> rebase(
            PendingProgressionOperation operation,
            DefinitionRevision currentDefinition
    ) {
        if (operation.definitionRevision().equals(currentDefinition)) {
            return Optional.of(operation);
        }
        if (operation.definitionRevision().semanticDigest().equals(currentDefinition.semanticDigest())) {
            return Optional.of(operation.rebase(currentDefinition, operation.balanceId()));
        }
        return PackRuntime.service().flatMap(service -> {
            var canonical = service.live().snapshot().canonicalIr();
            var source = new DefinitionKey(DefinitionKinds.CURRENCY, operation.balanceId());
            DefinitionKey target = canonical.aliases().resolveTerminal(source);
            if (target.equals(source) || !canonical.definitions().containsKey(target)) {
                return Optional.empty();
            }
            return Optional.of(operation.rebase(currentDefinition, target.id()));
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }
}
