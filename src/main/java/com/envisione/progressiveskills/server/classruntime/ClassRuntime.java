package com.envisione.progressiveskills.server.classruntime;

import com.envisione.progressiveskills.common.classdef.ClassCatalog;
import com.envisione.progressiveskills.common.classdef.ClassProgression;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

public final class ClassRuntime {
    private static final int MAX_RECONCILE_TRANSACTIONS = 64;

    private ClassRuntime() {
    }

    public static Optional<ClassCatalog> catalog() {
        return PackRuntime.service().filter(service -> service.live().generation() > 0).map(service -> {
            var canonical = service.live().snapshot().canonicalIr();
            SkillCatalog skills = SkillCatalog.from(canonical);
            return ClassCatalog.from(canonical, skills, TreeCatalog.from(canonical, skills));
        });
    }

    public static ClassProgression.ChangePreview previewSelect(
            ServerPlayer player,
            ResourceLocation classId
    ) {
        Context context = context(player);
        return ClassProgression.previewSelect(
                context.classes(), context.skills(), context.transactions().service().snapshot(player.getUUID()),
                classId
        );
    }

    public static ChangeResult select(
            ServerPlayer player,
            ResourceLocation classId,
            IdempotencyKey idempotencyKey
    ) {
        Context context = context(player);
        var snapshot = context.transactions().service().snapshot(player.getUUID());
        var preview = ClassProgression.previewSelect(context.classes(), context.skills(), snapshot, classId);
        var plan = ClassProgression.select(
                player.getUUID(), player.getUUID(), context.classes(), context.skills(), snapshot,
                context.definition(), classId, idempotencyKey, ProgressionCause.GAMEPLAY
        );
        TransactionResult transaction = context.transactions().executeAndPersist(
                player, plan, context.definition()
        );
        return new ChangeResult(preview, transaction);
    }

    public static ClassProgression.ChangePreview previewRespec(
            ServerPlayer player,
            ResourceLocation classId
    ) {
        Context context = context(player);
        return ClassProgression.previewRespec(
                context.classes(), context.skills(), context.transactions().service().snapshot(player.getUUID()),
                context.definition(), classId
        );
    }

    public static ChangeResult respec(
            ServerPlayer player,
            ResourceLocation classId,
            String previewDigest,
            IdempotencyKey idempotencyKey
    ) {
        Context context = context(player);
        var snapshot = context.transactions().service().snapshot(player.getUUID());
        var preview = ClassProgression.previewRespec(
                context.classes(), context.skills(), snapshot, context.definition(), classId
        );
        var plan = ClassProgression.respec(
                player.getUUID(), player.getUUID(), context.classes(), context.skills(), snapshot,
                context.definition(), classId, previewDigest, idempotencyKey, ProgressionCause.GAMEPLAY
        );
        TransactionResult transaction = context.transactions().executeAndPersist(
                player, plan, context.definition()
        );
        return new ChangeResult(preview, transaction);
    }

    public static ClassProgression.ChangePreview previewSwap(
            ServerPlayer player,
            ResourceLocation removedClassId,
            ResourceLocation replacementClassId
    ) {
        Context context = context(player);
        return ClassProgression.previewSwap(
                context.classes(), context.skills(), context.transactions().service().snapshot(player.getUUID()),
                context.definition(), removedClassId, replacementClassId
        );
    }

    public static ChangeResult swap(
            ServerPlayer player,
            ResourceLocation removedClassId,
            ResourceLocation replacementClassId,
            String previewDigest,
            IdempotencyKey idempotencyKey
    ) {
        Context context = context(player);
        var snapshot = context.transactions().service().snapshot(player.getUUID());
        var preview = ClassProgression.previewSwap(
                context.classes(), context.skills(), snapshot, context.definition(),
                removedClassId, replacementClassId
        );
        var plan = ClassProgression.swap(
                player.getUUID(), player.getUUID(), context.classes(), context.skills(), snapshot,
                context.definition(), removedClassId, replacementClassId, previewDigest,
                idempotencyKey, ProgressionCause.GAMEPLAY
        );
        TransactionResult transaction = context.transactions().executeAndPersist(
                player, plan, context.definition()
        );
        return new ChangeResult(preview, transaction);
    }

    public static void reconcile(
            ServerPlayer player,
            TransactionRuntime.Context transactions,
            DefinitionRevision definition
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(transactions, "transactions");
        Objects.requireNonNull(definition, "definition");
        var service = PackRuntime.service().orElseThrow(
                () -> new IllegalStateException("Pack runtime is unavailable")
        );
        var canonical = service.live().snapshot().canonicalIr();
        SkillCatalog skills = SkillCatalog.from(canonical);
        ClassCatalog classes = ClassCatalog.from(canonical, skills, TreeCatalog.from(canonical, skills));
        for (int attempt = 0; attempt < MAX_RECONCILE_TRANSACTIONS; attempt++) {
            var snapshot = transactions.service().snapshot(player.getUUID());
            var plan = ClassProgression.reconcile(
                    player.getUUID(), classes, skills, snapshot, definition
            );
            if (plan.isEmpty()) {
                return;
            }
            TransactionResult result = transactions.executeAndPersistReconcile(
                    player, plan.orElseThrow(), definition
            );
            if (!result.status().committed()) {
                throw new IllegalStateException("Class reconciliation failed with "
                        + result.diagnosticCode() + " " + result.message());
            }
        }
        throw new IllegalStateException("Class reconciliation exceeded its transaction bound");
    }

    private static Context context(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        requireActivePlayerData(player.getData(PsDataAttachments.PLAYER_DATA));
        var service = PackRuntime.service().orElseThrow(
                () -> new IllegalStateException("Pack runtime is unavailable")
        );
        var canonical = service.live().snapshot().canonicalIr();
        SkillCatalog skills = SkillCatalog.from(canonical);
        ClassCatalog classes = ClassCatalog.from(canonical, skills, TreeCatalog.from(canonical, skills));
        TransactionRuntime.Context transactions = TransactionRuntime.context(player.getServer())
                .orElseThrow(() -> new IllegalStateException("Transaction runtime is unavailable"));
        if (!transactions.ready(player)) {
            throw new IllegalStateException("Player transaction state is unavailable");
        }
        DefinitionRevision definition = TransactionRuntime.currentDefinition()
                .orElseThrow(() -> new IllegalStateException("Live definitions are unavailable"));
        return new Context(skills, classes, transactions, definition);
    }

    static void requireActivePlayerData(ProgressiveSkillsData data) {
        Objects.requireNonNull(data, "data");
        if (!data.active()) {
            throw new IllegalStateException("Player progression data is quarantined");
        }
    }

    public record ChangeResult(
            ClassProgression.ChangePreview preview,
            TransactionResult transaction
    ) {
        public ChangeResult {
            Objects.requireNonNull(preview, "preview");
            Objects.requireNonNull(transaction, "transaction");
        }
    }

    private record Context(
            SkillCatalog skills,
            ClassCatalog classes,
            TransactionRuntime.Context transactions,
            DefinitionRevision definition
    ) {
    }
}
