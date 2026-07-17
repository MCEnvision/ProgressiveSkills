package com.envisione.progressiveskills.server.skill;

import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillAwardPlan;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.skill.SkillDefinition;
import com.envisione.progressiveskills.common.skill.SkillProgression;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SkillRuntime {
    private SkillRuntime() {
    }

    public static Optional<SkillCatalog> catalog() {
        return PackRuntime.service().filter(service -> service.live().generation() > 0)
                .map(service -> SkillCatalog.from(service.live().snapshot().canonicalIr()));
    }

    public static AwardResult awardManual(
            UUID actor,
            ServerPlayer target,
            SkillDefinition skill,
            long amountUnits
    ) {
        return award(
                actor,
                target,
                skill,
                amountUnits,
                SkillProgression.manualOrigin(),
                "phase7/manual/" + UUID.randomUUID(),
                ProgressionCause.ADMIN,
                "Award manual skill XP"
        );
    }

    public static AwardResult awardCustom(
            UUID actor,
            ServerPlayer target,
            SkillCatalog.CustomXpRoute route
    ) {
        return award(
                actor,
                target,
                route.skill(),
                route.source().amountUnits(),
                route.source().id(),
                "phase7/custom/" + route.source().id() + "/" + UUID.randomUUID(),
                ProgressionCause.GAMEPLAY,
                "Award custom source skill XP"
        );
    }

    private static AwardResult award(
            UUID actor,
            ServerPlayer target,
            SkillDefinition skill,
            long amountUnits,
            ResourceLocation origin,
            String idempotency,
            ProgressionCause cause,
            String reason
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        SkillCatalog catalog = catalog().orElseThrow(() -> new IllegalStateException("Skill catalog is unavailable"));
        TransactionRuntime.Context context = TransactionRuntime.context(target.getServer())
                .orElseThrow(() -> new IllegalStateException("Transaction runtime is unavailable"));
        DefinitionRevision definition = TransactionRuntime.currentDefinition()
                .orElseThrow(() -> new IllegalStateException("Live definitions are unavailable"));
        SkillAwardPlan plan = SkillProgression.award(
                actor,
                target.getUUID(),
                skill,
                amountUnits,
                catalog,
                context.service().snapshot(target.getUUID()),
                definition,
                new IdempotencyKey(idempotency),
                origin,
                cause,
                reason
        );
        TransactionResult result = context.executeAndPersist(target, plan.cascade(), definition);
        if (result.status().committed() && !result.replayed()) {
            feedback(target, skill, plan);
        }
        return new AwardResult(skill, plan, result);
    }

    public static void reconcile(
            ServerPlayer player,
            TransactionRuntime.Context context,
            DefinitionRevision definition
    ) {
        SkillCatalog catalog = catalog().orElseThrow(() -> new IllegalStateException("Skill catalog is unavailable"));
        var snapshot = context.service().snapshot(player.getUUID());
        SkillProgression.reconcile(player.getUUID(), catalog, snapshot, definition).ifPresent(plan -> {
            TransactionResult result = context.executeAndPersist(player, plan, definition);
            if (!result.status().committed()) {
                throw new IllegalStateException("Skill reconciliation failed with "
                        + result.diagnosticCode() + " " + result.message());
            }
        });
    }

    private static void feedback(ServerPlayer player, SkillDefinition skill, SkillAwardPlan plan) {
        String name = skill.presentation().display().fallback();
        player.displayClientMessage(Component.literal(
                "+" + FixedPoint.format(plan.awardedUnits()) + " XP. " + name + ". Level " + plan.after().level()
        ), true);
        if (plan.after().level() > plan.before().level()) {
            int crossed = plan.after().level() - plan.before().level();
            player.sendSystemMessage(Component.literal(
                    name + " reached level " + plan.after().level() + ". Levels crossed " + crossed + "."
            ));
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (plan.bankedAwardUnits() > 0) {
            player.sendSystemMessage(Component.literal(
                    FixedPoint.format(plan.bankedAwardUnits()) + " XP was banked at the level cap."
            ));
        }
        if (plan.currencyAwarded() > 0) {
            player.sendSystemMessage(Component.literal(
                    plan.currencyAwarded() + " global points awarded for new highest levels."
            ));
        }
    }

    public record AwardResult(SkillDefinition skill, SkillAwardPlan plan, TransactionResult transaction) {
        public AwardResult {
            Objects.requireNonNull(skill, "skill");
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(transaction, "transaction");
        }
    }
}
