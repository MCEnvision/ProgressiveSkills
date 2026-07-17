package com.envisione.progressiveskills.common.skill;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.EntitlementContribution;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementMutation;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SkillProgression {
    private static final ResourceLocation MANUAL_ORIGIN = id("manual_xp");
    private static final ResourceLocation RECONCILE_ORIGIN = id("skill_reconcile");

    private SkillProgression() {
    }

    public static SkillAwardPlan award(
            UUID actor,
            UUID target,
            SkillDefinition skill,
            long amountUnits,
            SkillCatalog catalog,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            IdempotencyKey idempotencyKey,
            ResourceLocation origin,
            ProgressionCause cause,
            String reason
    ) {
        return award(
                actor,
                target,
                skill,
                amountUnits,
                catalog,
                snapshot,
                definition,
                idempotencyKey,
                origin,
                cause,
                reason,
                List.of()
        );
    }

    public static SkillAwardPlan award(
            UUID actor,
            UUID target,
            SkillDefinition skill,
            long amountUnits,
            SkillCatalog catalog,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            IdempotencyKey idempotencyKey,
            ResourceLocation origin,
            ProgressionCause cause,
            String reason,
            List<BalanceMutation> additionalBalances
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(cause, "cause");
        additionalBalances = List.copyOf(Objects.requireNonNull(additionalBalances, "additionalBalances"));
        if (!skill.enabled()) {
            throw new IllegalArgumentException("Skill is disabled " + skill.id());
        }
        if (amountUnits <= 0) {
            throw new IllegalArgumentException("XP awards must be positive");
        }
        SkillProgress before = SkillProgress.from(skill, snapshot);
        long remainingCapacity = Math.subtractExact(skill.curve().capUnits(), before.activeXpUnits());
        long activeAward = Math.min(amountUnits, remainingCapacity);
        long bankedAward = Math.subtractExact(amountUnits, activeAward);
        long newActive = Math.addExact(before.activeXpUnits(), activeAward);
        long newBank = Math.addExact(before.bankedXpUnits(), bankedAward);
        int newLevel = skill.curve().levelForUnits(newActive);
        int newHighest = Math.max(before.highestLevel(), newLevel);

        var balances = new ArrayList<BalanceMutation>();
        addBalance(balances, SkillStateIds.activeXp(skill.id()), activeAward, 0, skill.curve().capUnits());
        addBalance(balances, SkillStateIds.bankedXp(skill.id()), bankedAward, 0, Long.MAX_VALUE);
        long storedLevelBalance = snapshot.balances().getOrDefault(SkillStateIds.level(skill.id()), 0L);
        long storedHighestBalance = snapshot.balances().getOrDefault(SkillStateIds.highestLevel(skill.id()), 0L);
        addBalance(balances, SkillStateIds.level(skill.id()), newLevel - storedLevelBalance,
                skill.curve().minLevel(), skill.curve().maxLevel());
        addBalance(balances, SkillStateIds.highestLevel(skill.id()), newHighest - storedHighestBalance,
                skill.curve().minLevel(), Integer.MAX_VALUE);

        var currencyDeltas = new LinkedHashMap<ResourceLocation, Long>();
        int highestCrossings = newHighest - before.highestLevel();
        for (SkillDefinition.CurrencyAward award : skill.currencyAwards()) {
            if (highestCrossings > 0) {
                currencyDeltas.merge(
                        award.currency(),
                        Math.multiplyExact(award.amountPerLevel(), highestCrossings),
                        Math::addExact
                );
            }
        }
        long currencyAwarded = 0;
        for (var entry : currencyDeltas.entrySet()) {
            CurrencyDefinition currency = catalog.currency(entry.getKey()).orElseThrow();
            long initial = snapshot.balances().containsKey(entry.getKey()) ? 0 : currency.initial();
            addBalance(balances, entry.getKey(), Math.addExact(initial, entry.getValue()),
                    currency.minimum(), currency.maximum());
            currencyAwarded = Math.addExact(currencyAwarded, entry.getValue());
        }
        balances.addAll(additionalBalances);

        var entitlements = entitlementChanges(skill, newLevel, snapshot);
        var step = new TransactionStep(origin, balances, entitlements, java.util.List.of());
        var plan = CascadePlan.single(new TransactionPlan(
                actor,
                target,
                idempotencyKey,
                snapshot.stateRevision(),
                definition,
                cause,
                reason,
                step
        ));
        SkillProgress after = new SkillProgress(
                newActive,
                newBank,
                newLevel,
                newHighest,
                skill.curve().intoLevelUnits(newActive)
        );
        return new SkillAwardPlan(
                plan,
                before,
                after,
                amountUnits,
                activeAward,
                bankedAward,
                currencyAwarded
        );
    }

    public static Optional<CascadePlan> reconcile(
            UUID target,
            SkillCatalog catalog,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(definition, "definition");
        var balances = new ArrayList<BalanceMutation>();
        for (CurrencyDefinition currency : catalog.currencies().values()) {
            if (!snapshot.balances().containsKey(currency.id()) && currency.initial() != 0) {
                addBalance(balances, currency.id(), currency.initial(), currency.minimum(), currency.maximum());
            }
        }

        var desired = new LinkedHashMap<GrantSourceId, DesiredGrant>();
        for (SkillDefinition skill : catalog.skills().values()) {
            if (!skill.enabled()) {
                continue;
            }
            long storedActive = snapshot.balances().getOrDefault(SkillStateIds.activeXp(skill.id()), 0L);
            long storedBank = snapshot.balances().getOrDefault(SkillStateIds.bankedXp(skill.id()), 0L);
            if (storedActive < 0 || storedBank < 0) {
                throw new IllegalStateException("Skill XP state must not be negative for " + skill.id());
            }
            long active = Math.min(storedActive, skill.curve().capUnits());
            long overflow = storedActive - active;
            addBalance(balances, SkillStateIds.activeXp(skill.id()), active - storedActive,
                    0, skill.curve().capUnits());
            addBalance(balances, SkillStateIds.bankedXp(skill.id()), overflow, 0, Long.MAX_VALUE);
            int level = skill.curve().levelForUnits(active);
            long storedLevel = snapshot.balances().getOrDefault(SkillStateIds.level(skill.id()), 0L);
            long storedHighest = snapshot.balances().getOrDefault(SkillStateIds.highestLevel(skill.id()), 0L);
            addBalance(balances, SkillStateIds.level(skill.id()), level - storedLevel,
                    skill.curve().minLevel(), skill.curve().maxLevel());
            addBalance(balances, SkillStateIds.highestLevel(skill.id()),
                    Math.max(storedHighest, level) - storedHighest,
                    skill.curve().minLevel(), Integer.MAX_VALUE);
            for (SkillDefinition.AttributeGrant grant : skill.attributeGrants()) {
                long value = grant.valueAt(level);
                if (value != 0) {
                    GrantSourceId source = source(skill, grant);
                    DesiredGrant previous = desired.putIfAbsent(source, new DesiredGrant(
                            key(grant), new EntitlementContribution(value, EntitlementResolver.ADDITIVE)
                    ));
                    if (previous != null) {
                        throw new IllegalArgumentException("Duplicate skill grant source " + source);
                    }
                }
            }
        }

        var entitlements = new ArrayList<EntitlementMutation>();
        snapshot.ownership().forEach((key, owners) -> owners.forEach((source, contribution) -> {
            if (!source.ownerKind().equals(DefinitionKinds.SKILL.id())) {
                return;
            }
            DesiredGrant wanted = desired.get(source);
            if (wanted == null || !wanted.key().equals(key)) {
                entitlements.add(EntitlementMutation.revoke(key, source));
            }
        }));
        desired.forEach((source, grant) -> {
            EntitlementContribution current = snapshot.ownership()
                    .getOrDefault(grant.key(), Map.of()).get(source);
            if (!grant.contribution().equals(current)) {
                entitlements.add(new EntitlementMutation(
                        grant.key(), source, Optional.of(grant.contribution())
                ));
            }
        });
        if (balances.isEmpty() && entitlements.isEmpty()) {
            return Optional.empty();
        }
        var step = new TransactionStep(RECONCILE_ORIGIN, balances, entitlements, java.util.List.of());
        return Optional.of(CascadePlan.single(new TransactionPlan(
                target,
                target,
                new IdempotencyKey("phase7/reconcile/" + definition.generation() + "/"
                        + snapshot.stateRevision() + "/" + target),
                snapshot.stateRevision(),
                definition,
                ProgressionCause.RECONCILE,
                "Reconcile skill XP and persistent grants",
                step
        )));
    }

    public static ResourceLocation manualOrigin() {
        return MANUAL_ORIGIN;
    }

    private static ArrayList<EntitlementMutation> entitlementChanges(
            SkillDefinition skill,
            int newLevel,
            ProgressionSnapshot snapshot
    ) {
        var changes = new ArrayList<EntitlementMutation>();
        for (SkillDefinition.AttributeGrant grant : skill.attributeGrants()) {
            EntitlementKey key = key(grant);
            GrantSourceId source = source(skill, grant);
            EntitlementContribution current = snapshot.ownership().getOrDefault(key, Map.of()).get(source);
            long desired = grant.valueAt(newLevel);
            if (desired == 0 && current != null) {
                changes.add(EntitlementMutation.revoke(key, source));
            } else if (desired != 0) {
                var contribution = new EntitlementContribution(desired, EntitlementResolver.ADDITIVE);
                if (!contribution.equals(current)) {
                    changes.add(new EntitlementMutation(key, source, Optional.of(contribution)));
                }
            }
        }
        return changes;
    }

    private static EntitlementKey key(SkillDefinition.AttributeGrant grant) {
        return new EntitlementKey(grant.operation().targetType(), grant.attribute());
    }

    private static GrantSourceId source(SkillDefinition skill, SkillDefinition.AttributeGrant grant) {
        return new GrantSourceId(DefinitionKinds.SKILL.id(), skill.id(), grant.id());
    }

    private static void addBalance(
            ArrayList<BalanceMutation> mutations,
            ResourceLocation id,
            long delta,
            long minimum,
            long maximum
    ) {
        if (delta != 0) {
            mutations.add(new BalanceMutation(id, delta, minimum, maximum));
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }

    private record DesiredGrant(EntitlementKey key, EntitlementContribution contribution) {
    }
}
