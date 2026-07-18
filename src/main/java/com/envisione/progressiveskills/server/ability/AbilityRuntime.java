package com.envisione.progressiveskills.server.ability;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.ability.AbilityCatalog;
import com.envisione.progressiveskills.common.ability.AbilityProgression;
import com.envisione.progressiveskills.common.ability.AbilityState;
import com.envisione.progressiveskills.common.classdef.ClassCatalog;
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
import com.envisione.progressiveskills.server.hardening.DecisionTraceRuntime;
import com.envisione.progressiveskills.server.hardening.HardeningRuntime;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class AbilityRuntime {
    private static final int MAX_RECONCILE_TRANSACTIONS = 64;
    private static final Logger LOGGER = LogUtils.getLogger();

    private AbilityRuntime() {
    }

    public static Optional<AbilityCatalog> catalog() {
        return PackRuntime.service().filter(service -> service.live().generation() > 0).map(service -> {
            var canonical = service.live().snapshot().canonicalIr();
            SkillCatalog skills = SkillCatalog.from(canonical);
            TreeCatalog trees = TreeCatalog.from(canonical, skills);
            ClassCatalog classes = ClassCatalog.from(canonical, skills, trees);
            return AbilityCatalog.from(canonical, skills, classes);
        });
    }

    public static AbilityState state(ServerPlayer player) {
        Context context = context(player);
        return AbilityProgression.state(
                context.abilities(),
                context.transactions().service().snapshot(player.getUUID()),
                gameTick(player)
        );
    }

    public static AbilityProgression.SlotPreview previewAssign(
            ServerPlayer player,
            ResourceLocation abilityId,
            int slot
    ) {
        Context context = context(player);
        return AbilityProgression.previewAssign(
                context.abilities(),
                context.transactions().service().snapshot(player.getUUID()),
                abilityId,
                AbilityState.slotId(slot)
        );
    }

    public static ChangeResult assign(
            ServerPlayer player,
            ResourceLocation abilityId,
            int slot,
            IdempotencyKey idempotencyKey
    ) {
        Context context = context(player);
        var snapshot = context.transactions().service().snapshot(player.getUUID());
        var preview = AbilityProgression.previewAssign(
                context.abilities(), snapshot, abilityId, AbilityState.slotId(slot));
        var plan = AbilityProgression.assign(
                player.getUUID(), player.getUUID(), context.abilities(), snapshot,
                context.definition(), abilityId, AbilityState.slotId(slot), idempotencyKey,
                ProgressionCause.GAMEPLAY
        );
        TransactionResult transaction = context.transactions().executeAndPersist(
                player, plan, context.definition());
        return new ChangeResult(Optional.of(preview), transaction);
    }

    public static ChangeResult unassign(
            ServerPlayer player,
            int slot,
            IdempotencyKey idempotencyKey
    ) {
        Context context = context(player);
        var snapshot = context.transactions().service().snapshot(player.getUUID());
        var plan = AbilityProgression.unassign(
                player.getUUID(), player.getUUID(), snapshot, context.definition(),
                AbilityState.slotId(slot), idempotencyKey, ProgressionCause.GAMEPLAY
        );
        return new ChangeResult(Optional.empty(), context.transactions().executeAndPersist(
                player, plan, context.definition()));
    }

    public static ChangeResult select(
            ServerPlayer player,
            int slot,
            IdempotencyKey idempotencyKey
    ) {
        Context context = context(player);
        var snapshot = context.transactions().service().snapshot(player.getUUID());
        var plan = AbilityProgression.selectSlot(
                player.getUUID(), player.getUUID(), snapshot, context.definition(),
                AbilityState.slotId(slot), idempotencyKey, ProgressionCause.GAMEPLAY
        );
        return new ChangeResult(Optional.empty(), context.transactions().executeAndPersist(
                player, plan, context.definition()));
    }

    public static ChangeResult toggle(
            ServerPlayer player,
            ResourceLocation abilityId,
            IdempotencyKey idempotencyKey
    ) {
        Context context = context(player);
        var snapshot = context.transactions().service().snapshot(player.getUUID());
        var plan = AbilityProgression.toggle(
                player.getUUID(), player.getUUID(), context.abilities(), snapshot,
                context.definition(), abilityId, idempotencyKey, ProgressionCause.GAMEPLAY
        );
        return new ChangeResult(Optional.empty(), context.transactions().executeAndPersist(
                player, plan, context.definition()));
    }

    public static AbilityProgression.ActivationPreview previewActivation(
            ServerPlayer player,
            int slot
    ) {
        Context context = context(player);
        var snapshot = context.transactions().service().snapshot(player.getUUID());
        ResourceLocation abilityId = AbilityProgression.state(
                        context.abilities(), snapshot, gameTick(player))
                .assignedAbility(AbilityState.slotId(slot))
                .orElseThrow(() -> new IllegalArgumentException("Ability slot is empty"));
        return AbilityProgression.previewActivation(
                context.abilities(), context.skills(), snapshot, abilityId, gameTick(player));
    }

    public static ActivationResult activate(
            ServerPlayer player,
            int slot,
            IdempotencyKey idempotencyKey
    ) {
        long started = System.nanoTime();
        try {
            Context context = context(player);
            long tick = gameTick(player);
            var snapshot = context.transactions().service().snapshot(player.getUUID());
            ResourceLocation abilityId = AbilityProgression.state(context.abilities(), snapshot, tick)
                    .assignedAbility(AbilityState.slotId(slot))
                    .orElseThrow(() -> new IllegalArgumentException("Ability slot is empty"));
            AbilityProgression.ActivationPlan plan = AbilityProgression.activate(
                    player.getUUID(), player.getUUID(), context.abilities(), context.skills(), snapshot,
                    context.definition(), abilityId, tick, idempotencyKey, ProgressionCause.GAMEPLAY
            );
            AbilityTargetResolver.Resolution target = AbilityTargetResolver.resolve(player, plan.targeting());
            if (!target.accepted()) {
                DecisionTraceRuntime.record(player.getUUID(), "ability", abilityId.toString(), false,
                        target.message(), snapshot.stateRevision());
                HardeningRuntime.performance().record(
                        "ability_activation", 5_000_000L, System.nanoTime() - started);
                return ActivationResult.rejected(plan.preview(), target.message());
            }
            AbilityProgression.ActivationPlan executable = AbilityActivationBridge.attach(
                    plan, target.target().orElseThrow());
            TransactionResult transaction = context.transactions().executeAndPersist(
                    player, executable.transaction(), context.definition());
            DecisionTraceRuntime.record(player.getUUID(), "ability", abilityId.toString(),
                    transaction.status().committed(), transaction.message(),
                    context.transactions().service().snapshot(player.getUUID()).stateRevision());
            HardeningRuntime.performance().record(
                    "ability_activation", 5_000_000L, System.nanoTime() - started);
            return new ActivationResult(
                    transaction.status().committed(),
                    transaction.message(),
                    Optional.of(plan.preview()),
                    Optional.of(transaction)
            );
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
            HardeningRuntime.performance().record(
                    "ability_activation", 5_000_000L, System.nanoTime() - started);
            DecisionTraceRuntime.record(player.getUUID(), "ability", "activation", false,
                    safeMessage(exception), TransactionRuntime.context(player.getServer())
                            .map(value -> value.service().snapshot(player.getUUID()).stateRevision()).orElse(0L));
            return new ActivationResult(false, safeMessage(exception), Optional.empty(), Optional.empty());
        }
    }

    public static void reconcile(
            ServerPlayer player,
            TransactionRuntime.Context transactions,
            DefinitionRevision definition
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(transactions, "transactions");
        Objects.requireNonNull(definition, "definition");
        Catalogs catalogs = catalogs();
        for (int attempt = 0; attempt < MAX_RECONCILE_TRANSACTIONS; attempt++) {
            var snapshot = transactions.service().snapshot(player.getUUID());
            var plan = AbilityProgression.reconcile(
                    player.getUUID(), catalogs.abilities(), snapshot, definition);
            if (plan.isEmpty()) {
                return;
            }
            TransactionResult result = transactions.executeAndPersistReconcile(
                    player, plan.orElseThrow(), definition);
            if (!result.status().committed()) {
                throw new IllegalStateException("Ability reconciliation failed with "
                        + result.diagnosticCode() + " " + result.message());
            }
        }
        throw new IllegalStateException("Ability reconciliation exceeded its transaction bound");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        long started = System.nanoTime();
        try {
            if (event.getServer().getTickCount() % 5 != 0) {
                return;
            }
            TransactionRuntime.context(event.getServer()).ifPresent(transactions ->
                    TransactionRuntime.currentDefinition().ifPresent(definition -> {
                        Catalogs catalogs;
                        try {
                            catalogs = catalogs();
                        } catch (RuntimeException exception) {
                            LOGGER.error("ProgressiveSkills ability recharge catalog is unavailable", exception);
                            return;
                        }
                            event.getServer().getPlayerList().getPlayers().forEach(player -> {
                                if (!transactions.ready(player)) {
                                    return;
                                }
                                try {
                                    var snapshot = transactions.service().snapshot(player.getUUID());
                                    long gameTick = gameTick(player);
                                    AbilityProgression.recharge(
                                            player.getUUID(), catalogs.abilities(), snapshot, definition, gameTick
                                    ).ifPresent(plan -> {
                                        TransactionResult result = transactions.executeAndPersist(
                                                player, plan, definition);
                                        if (!result.status().committed()) {
                                            LOGGER.warn("ProgressiveSkills ability recharge was rejected for {} with {}",
                                                    player.getUUID(), result.message());
                                        }
                                    });
                                } catch (RuntimeException exception) {
                                    LOGGER.error("ProgressiveSkills ability recharge failed for {}",
                                            player.getUUID(), exception);
                                }
                            });
                    }));
        } finally {
            HardeningRuntime.performance().record(
                    "ability.recharge.tick", 2_000_000L, System.nanoTime() - started);
        }
    }

    private static Context context(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        requireActivePlayerData(player.getData(PsDataAttachments.PLAYER_DATA));
        Catalogs catalogs = catalogs();
        TransactionRuntime.Context transactions = TransactionRuntime.context(player.getServer())
                .orElseThrow(() -> new IllegalStateException("Transaction runtime is unavailable"));
        if (!transactions.ready(player)) {
            throw new IllegalStateException("Player transaction state is unavailable");
        }
        DefinitionRevision definition = TransactionRuntime.currentDefinition()
                .orElseThrow(() -> new IllegalStateException("Live definitions are unavailable"));
        return new Context(catalogs.skills(), catalogs.abilities(), transactions, definition);
    }

    private static Catalogs catalogs() {
        var canonical = PackRuntime.service()
                .filter(service -> service.live().generation() > 0)
                .map(service -> service.live().snapshot().canonicalIr())
                .orElseThrow(() -> new IllegalStateException("Ability catalog is unavailable"));
        SkillCatalog skills = SkillCatalog.from(canonical);
        TreeCatalog trees = TreeCatalog.from(canonical, skills);
        ClassCatalog classes = ClassCatalog.from(canonical, skills, trees);
        return new Catalogs(skills, AbilityCatalog.from(canonical, skills, classes));
    }

    private static long gameTick(ServerPlayer player) {
        return player.serverLevel().getGameTime();
    }

    private static void requireActivePlayerData(ProgressiveSkillsData data) {
        Objects.requireNonNull(data, "data");
        if (!data.active()) {
            throw new IllegalStateException("Player progression data is quarantined");
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public record ChangeResult(
            Optional<AbilityProgression.SlotPreview> preview,
            TransactionResult transaction
    ) {
        public ChangeResult {
            preview = Objects.requireNonNull(preview, "preview");
            Objects.requireNonNull(transaction, "transaction");
        }

        public boolean accepted() {
            return transaction.status().committed();
        }

        public String message() {
            return transaction.message();
        }
    }

    public record ActivationResult(
            boolean accepted,
            String message,
            Optional<AbilityProgression.ActivationPreview> preview,
            Optional<TransactionResult> transaction
    ) {
        public ActivationResult {
            message = Objects.requireNonNull(message, "message");
            preview = Objects.requireNonNull(preview, "preview");
            transaction = Objects.requireNonNull(transaction, "transaction");
        }

        public static ActivationResult rejected(
                AbilityProgression.ActivationPreview preview,
                String message
        ) {
            return new ActivationResult(false, message, Optional.of(preview), Optional.empty());
        }
    }

    private record Context(
            SkillCatalog skills,
            AbilityCatalog abilities,
            TransactionRuntime.Context transactions,
            DefinitionRevision definition
    ) {
    }

    private record Catalogs(SkillCatalog skills, AbilityCatalog abilities) {
    }
}
