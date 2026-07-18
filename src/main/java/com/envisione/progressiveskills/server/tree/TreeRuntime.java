package com.envisione.progressiveskills.server.tree;

import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.common.tree.TreeProgression;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.hardening.DecisionTraceRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

public final class TreeRuntime {
    private static final int MAX_RECONCILE_TRANSACTIONS = 1024;

    private TreeRuntime() {
    }

    public static Optional<TreeCatalog> catalog() {
        return PackRuntime.service().filter(service -> service.live().generation() > 0).map(service -> {
            var canonical = service.live().snapshot().canonicalIr();
            return TreeCatalog.from(canonical, SkillCatalog.from(canonical));
        });
    }

    public static TreeProgression.PurchasePreview previewPurchase(
            ServerPlayer player,
            ResourceLocation treeId,
            ResourceLocation nodeId
    ) {
        Context context = context(player);
        return TreeProgression.previewPurchase(
                context.trees(), context.skills(), context.transactions().service().snapshot(player.getUUID()),
                treeId, nodeId
        );
    }

    public static PurchaseResult purchase(
            ServerPlayer player,
            ResourceLocation treeId,
            ResourceLocation nodeId,
            IdempotencyKey idempotencyKey
    ) {
        Context context = context(player);
        var snapshot = context.transactions().service().snapshot(player.getUUID());
        var preview = TreeProgression.previewPurchase(
                context.trees(), context.skills(), snapshot, treeId, nodeId
        );
        var plan = TreeProgression.purchase(
                player.getUUID(), player.getUUID(), context.trees(), context.skills(), snapshot,
                context.definition(), treeId, nodeId, idempotencyKey, ProgressionCause.GAMEPLAY
        );
        TransactionResult transaction = context.transactions().executeAndPersist(
                player, plan, context.definition()
        );
        trace(player, "tree purchase", nodeId, transaction, context);
        return new PurchaseResult(preview, transaction);
    }

    public static TreeProgression.RefundPreview previewRefund(
            ServerPlayer player,
            ResourceLocation treeId,
            ResourceLocation nodeId
    ) {
        Context context = context(player);
        return TreeProgression.previewRefund(
                context.trees(), context.skills(), context.transactions().service().snapshot(player.getUUID()),
                context.definition(), treeId, nodeId
        );
    }

    public static RefundResult refund(
            ServerPlayer player,
            ResourceLocation treeId,
            ResourceLocation nodeId,
            String previewDigest,
            IdempotencyKey idempotencyKey
    ) {
        Context context = context(player);
        var snapshot = context.transactions().service().snapshot(player.getUUID());
        var preview = TreeProgression.previewRefund(
                context.trees(), context.skills(), snapshot, context.definition(), treeId, nodeId
        );
        var plan = TreeProgression.refund(
                player.getUUID(), player.getUUID(), context.trees(), context.skills(), snapshot,
                context.definition(), treeId, nodeId, previewDigest, idempotencyKey,
                ProgressionCause.GAMEPLAY
        );
        TransactionResult transaction = context.transactions().executeAndPersist(
                player, plan, context.definition()
        );
        trace(player, "tree refund", nodeId, transaction, context);
        return new RefundResult(preview, transaction);
    }

    public static TreeProgression.RefundPreview previewRespec(
            ServerPlayer player,
            ResourceLocation treeId
    ) {
        Context context = context(player);
        return TreeProgression.previewRespec(
                context.trees(), context.skills(), context.transactions().service().snapshot(player.getUUID()),
                context.definition(), treeId
        );
    }

    public static RefundResult respec(
            ServerPlayer player,
            ResourceLocation treeId,
            String previewDigest,
            IdempotencyKey idempotencyKey
    ) {
        Context context = context(player);
        var snapshot = context.transactions().service().snapshot(player.getUUID());
        var preview = TreeProgression.previewRespec(
                context.trees(), context.skills(), snapshot, context.definition(), treeId
        );
        var plan = TreeProgression.respec(
                player.getUUID(), player.getUUID(), context.trees(), context.skills(), snapshot,
                context.definition(), treeId, previewDigest, idempotencyKey, ProgressionCause.GAMEPLAY
        );
        TransactionResult transaction = context.transactions().executeAndPersist(
                player, plan, context.definition()
        );
        trace(player, "tree respec", treeId, transaction, context);
        return new RefundResult(preview, transaction);
    }

    public static void reconcile(
            ServerPlayer player,
            TransactionRuntime.Context transactions,
            DefinitionRevision definition
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(transactions, "transactions");
        Objects.requireNonNull(definition, "definition");
        TreeCatalog trees = catalog().orElseThrow(
                () -> new IllegalStateException("Tree catalog is unavailable")
        );
        SkillCatalog skills = SkillCatalog.from(PackRuntime.service().orElseThrow()
                .live().snapshot().canonicalIr());
        for (int attempt = 0; attempt < MAX_RECONCILE_TRANSACTIONS; attempt++) {
            var snapshot = transactions.service().snapshot(player.getUUID());
            var plan = TreeProgression.reconcile(
                    player.getUUID(), trees, skills, snapshot, definition
            );
            if (plan.isEmpty()) {
                return;
            }
            TransactionResult result = transactions.executeAndPersistReconcile(
                    player, plan.orElseThrow(), definition
            );
            if (!result.status().committed()) {
                throw new IllegalStateException("Tree reconciliation failed with "
                        + result.diagnosticCode() + " " + result.message());
            }
        }
        throw new IllegalStateException("Tree reconciliation exceeded its transaction bound");
    }

    private static Context context(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        requireActivePlayerData(player.getData(PsDataAttachments.PLAYER_DATA));
        var service = PackRuntime.service().orElseThrow(
                () -> new IllegalStateException("Pack runtime is unavailable")
        );
        var canonical = service.live().snapshot().canonicalIr();
        SkillCatalog skills = SkillCatalog.from(canonical);
        TreeCatalog trees = TreeCatalog.from(canonical, skills);
        TransactionRuntime.Context transactions = TransactionRuntime.context(player.getServer())
                .orElseThrow(() -> new IllegalStateException("Transaction runtime is unavailable"));
        if (!transactions.ready(player)) {
            throw new IllegalStateException("Player transaction state is unavailable");
        }
        DefinitionRevision definition = TransactionRuntime.currentDefinition()
                .orElseThrow(() -> new IllegalStateException("Live definitions are unavailable"));
        return new Context(skills, trees, transactions, definition);
    }

    static void requireActivePlayerData(ProgressiveSkillsData data) {
        Objects.requireNonNull(data, "data");
        if (!data.active()) {
            throw new IllegalStateException("Player progression data is quarantined");
        }
    }

    private static void trace(
            ServerPlayer player,
            String operation,
            ResourceLocation subject,
            TransactionResult result,
            Context context
    ) {
        DecisionTraceRuntime.record(
                player.getUUID(), "lock", operation + ". " + subject,
                result.status().committed(), result.message(),
                context.transactions().service().snapshot(player.getUUID()).stateRevision());
    }

    public record PurchaseResult(
            TreeProgression.PurchasePreview preview,
            TransactionResult transaction
    ) {
        public PurchaseResult {
            Objects.requireNonNull(preview, "preview");
            Objects.requireNonNull(transaction, "transaction");
        }
    }

    public record RefundResult(
            TreeProgression.RefundPreview preview,
            TransactionResult transaction
    ) {
        public RefundResult {
            Objects.requireNonNull(preview, "preview");
            Objects.requireNonNull(transaction, "transaction");
        }
    }

    private record Context(
            SkillCatalog skills,
            TreeCatalog trees,
            TransactionRuntime.Context transactions,
            DefinitionRevision definition
    ) {
    }
}
