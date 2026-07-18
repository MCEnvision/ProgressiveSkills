package com.envisione.progressiveskills.server.carrier;

import com.envisione.progressiveskills.common.carrier.CarrierBehaviorSnapshot;
import com.envisione.progressiveskills.common.carrier.CarrierCurrencyAction;
import com.envisione.progressiveskills.common.carrier.CarrierSkillLevelAction;
import com.envisione.progressiveskills.common.carrier.CarrierSkillXpAction;
import com.envisione.progressiveskills.common.carrier.CarrierStackState;
import com.envisione.progressiveskills.common.carrier.CarrierTreeRespecAction;
import com.envisione.progressiveskills.common.carrier.CarrierUseAction;
import com.envisione.progressiveskills.common.skill.CurrencyDefinition;
import com.envisione.progressiveskills.common.skill.SkillAwardPlan;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.skill.SkillDefinition;
import com.envisione.progressiveskills.common.skill.SkillProgress;
import com.envisione.progressiveskills.common.skill.SkillProgression;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.common.tree.TreeProgression;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CarrierActionPlanCompiler {
    private CarrierActionPlanCompiler() {
    }

    public static CompiledUse compile(
            UUID playerId,
            CarrierBehaviorSnapshot behavior,
            CarrierStackState state,
            SkillCatalog skills,
            TreeCatalog trees,
            ProgressionTransactionService transactions,
            DefinitionRevision definition
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(behavior, "behavior");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(trees, "trees");
        Objects.requireNonNull(transactions, "transactions");
        Objects.requireNonNull(definition, "definition");
        ProgressionSnapshot before = transactions.snapshot(playerId);
        ProgressionSnapshot working = before;
        CascadePlan combined = null;
        IdempotencyKey useKey = key(behavior, state);
        for (CarrierUseAction action : behavior.useActions()) {
            CascadePlan actionPlan = planAction(
                    playerId, action, skills, trees, working, definition, useKey
            );
            combined = append(combined, actionPlan, useKey);
            working = transactions.previewSnapshot(combined, definition);
        }
        if (combined == null) {
            throw new IllegalArgumentException("Carrier behavior has no use actions");
        }
        return new CompiledUse(combined, before, working, behavior.useActions().size());
    }

    static IdempotencyKey key(CarrierBehaviorSnapshot behavior, CarrierStackState state) {
        return new IdempotencyKey(
                "phase13/carrier/" + state.instanceId() + "/" + state.useCounter()
                        + "/" + behavior.digest()
        );
    }

    private static CascadePlan planAction(
            UUID playerId,
            CarrierUseAction action,
            SkillCatalog skills,
            TreeCatalog trees,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            IdempotencyKey key
    ) {
        if (action instanceof CarrierSkillXpAction xp) {
            SkillDefinition skill = skills.skill(xp.skill()).orElseThrow(
                    () -> new IllegalArgumentException("Carrier references missing skill " + xp.skill())
            );
            return skillAward(
                    playerId, skill, xp.amountUnits(), skills, snapshot, definition, key, action.id()
            ).cascade();
        }
        if (action instanceof CarrierSkillLevelAction level) {
            SkillDefinition skill = skills.skill(level.skill()).orElseThrow(
                    () -> new IllegalArgumentException("Carrier references missing skill " + level.skill())
            );
            SkillProgress progress = SkillProgress.from(skill, snapshot);
            int target = Math.min(skill.curve().maxLevel(), Math.addExact(progress.level(), level.levels()));
            if (target <= progress.level()) {
                throw new IllegalArgumentException("Carrier skill level action is already at its cap");
            }
            long amount = Math.subtractExact(
                    skill.curve().totalUnitsAt(target), progress.activeXpUnits()
            );
            return skillAward(
                    playerId, skill, amount, skills, snapshot, definition, key, action.id()
            ).cascade();
        }
        if (action instanceof CarrierCurrencyAction currency) {
            CurrencyDefinition target = skills.currency(currency.currency()).orElseThrow(
                    () -> new IllegalArgumentException("Carrier references missing currency " + currency.currency())
            );
            long current = snapshot.balances().getOrDefault(target.id(), target.initial());
            long after = Math.addExact(current, currency.amount());
            if (after > target.maximum()) {
                throw new IllegalArgumentException("Carrier currency action exceeds the currency maximum");
            }
            long delta = snapshot.balances().containsKey(target.id())
                    ? currency.amount() : Math.addExact(target.initial(), currency.amount());
            return CascadePlan.single(new TransactionPlan(
                    playerId,
                    playerId,
                    key,
                    snapshot.stateRevision(),
                    definition,
                    ProgressionCause.GAMEPLAY,
                    "Use progression carrier",
                    new TransactionStep(
                            action.id(),
                            List.of(new BalanceMutation(target.id(), delta, target.minimum(), target.maximum())),
                            List.of(),
                            List.of(),
                            List.of()
                    )
            ));
        }
        if (action instanceof CarrierTreeRespecAction respec) {
            var preview = TreeProgression.previewRespec(
                    trees, skills, snapshot, definition, respec.tree()
            );
            if (!preview.allowed()) {
                throw new IllegalArgumentException(preview.blockers().getFirst());
            }
            return TreeProgression.respec(
                    playerId,
                    playerId,
                    trees,
                    skills,
                    snapshot,
                    definition,
                    respec.tree(),
                    preview.digest(),
                    key,
                    ProgressionCause.GAMEPLAY
            );
        }
        throw new IllegalArgumentException("Unsupported carrier use action " + action.getClass().getName());
    }

    private static SkillAwardPlan skillAward(
            UUID playerId,
            SkillDefinition skill,
            long amount,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            IdempotencyKey key,
            ResourceLocation origin
    ) {
        return SkillProgression.award(
                playerId,
                playerId,
                skill,
                amount,
                skills,
                snapshot,
                definition,
                key,
                origin,
                ProgressionCause.GAMEPLAY,
                "Use progression carrier"
        );
    }

    private static CascadePlan append(
            CascadePlan combined,
            CascadePlan next,
            IdempotencyKey key
    ) {
        if (combined == null) {
            TransactionPlan source = next.transaction();
            TransactionPlan root = new TransactionPlan(
                    source.actorId(),
                    source.targetId(),
                    key,
                    source.expectedStateRevision(),
                    source.definitionRevision(),
                    ProgressionCause.GAMEPLAY,
                    "Use progression carrier",
                    source.rootStep()
            );
            return new CascadePlan(root, next.queuedChildren());
        }
        TransactionPlan root = combined.transaction();
        TransactionPlan source = next.transaction();
        if (!root.actorId().equals(source.actorId())
                || !root.targetId().equals(source.targetId())
                || !root.definitionRevision().equals(source.definitionRevision())) {
            throw new IllegalArgumentException("Carrier action plans do not share one authority context");
        }
        var children = new ArrayList<TransactionStep>(combined.queuedChildren());
        children.add(source.rootStep());
        children.addAll(next.queuedChildren());
        return new CascadePlan(root, children);
    }

    public record CompiledUse(
            CascadePlan cascade,
            ProgressionSnapshot before,
            ProgressionSnapshot after,
            int actionCount
    ) {
        public CompiledUse {
            Objects.requireNonNull(cascade, "cascade");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            if (actionCount < 1 || actionCount > CarrierBehaviorSnapshot.MAX_ACTIONS) {
                throw new IllegalArgumentException("Carrier compiled action count is outside its bound");
            }
        }
    }
}
