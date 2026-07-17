package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.skill.CurrencyDefinition;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.EntitlementContribution;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementMutation;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.InternalBalanceIds;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

public final class AbilityProgression {
    public static final ResourceLocation ASSIGNMENT_OWNER_KIND = id("ability_assignment");
    public static final ResourceLocation SELECTION_OWNER_KIND = id("ability_selection");
    public static final ResourceLocation TOGGLE_OWNER_KIND = id("ability_toggle");
    private static final ResourceLocation ASSIGN_ORIGIN = id("ability_assign");
    private static final ResourceLocation UNASSIGN_ORIGIN = id("ability_unassign");
    private static final ResourceLocation SELECT_ORIGIN = id("ability_select");
    private static final ResourceLocation TOGGLE_ORIGIN = id("ability_toggle");
    private static final ResourceLocation ACTIVATE_ORIGIN = id("ability_activate");
    private static final ResourceLocation RECHARGE_ORIGIN = id("ability_recharge");
    private static final ResourceLocation RECONCILE_ORIGIN = id("ability_reconcile");
    private static final long MAX_SAFE_GAME_TICK = Long.MAX_VALUE - AbilityDefinition.MAX_RECHARGE_TICKS;

    private AbilityProgression() {
    }

    public static AbilityState state(
            AbilityCatalog catalog,
            ProgressionSnapshot snapshot,
            long gameTick
    ) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(snapshot, "snapshot");
        requireGameTick(gameTick);
        Set<ResourceLocation> owned = ownedAbilities(snapshot);
        Map<ResourceLocation, ResourceLocation> assignments = currentAssignments(snapshot);
        Optional<ResourceLocation> selected = currentSelectedSlot(snapshot);
        Map<ResourceLocation, Boolean> toggles = currentToggleStates(snapshot);
        for (ResourceLocation abilityId : owned) {
            catalog.ability(abilityId).filter(definition -> definition.kind() == AbilityKind.TOGGLE)
                    .ifPresent(definition -> toggles.putIfAbsent(abilityId, definition.defaultOn()));
        }
        var charges = new TreeMap<ResourceLocation, AbilityState.ChargeState>(ResourceLocation::compareNamespaced);
        var cooldowns = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        for (AbilityDefinition definition : catalog.abilities().values()) {
            if (!owned.contains(definition.id()) || definition.kind() != AbilityKind.ACTIVE) {
                continue;
            }
            charges.put(definition.id(), chargeState(definition, snapshot, gameTick));
            cooldowns.put(
                    definition.cooldownGroup(),
                    storedBalance(snapshot, cooldownBalanceId(definition.cooldownGroup()), 0)
            );
        }
        return new AbilityState(owned, assignments, selected, toggles, charges, cooldowns);
    }

    public static Set<ResourceLocation> ownedAbilities(ProgressionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var owned = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        snapshot.ownership().forEach((key, sources) -> {
            if (!key.targetType().equals(AbilityEntitlementTypes.OWNED)) {
                return;
            }
            long effective = snapshot.projectedValues().getOrDefault(
                    key,
                    sources.values().stream().anyMatch(value -> value.value() == 1) ? 1L : 0L
            );
            if (effective > 0) {
                owned.add(key.targetId());
            }
        });
        return Collections.unmodifiableSet(new LinkedHashSet<>(owned));
    }

    public static SlotPreview previewAssign(
            AbilityCatalog catalog,
            ProgressionSnapshot snapshot,
            ResourceLocation abilityId,
            ResourceLocation slotId
    ) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(snapshot, "snapshot");
        ResourceLocation validAbilityId = StableId.requireValid(abilityId);
        requireSlot(slotId);
        AbilityDefinition definition = catalog.ability(validAbilityId).orElseThrow(
                () -> new IllegalArgumentException("Unknown ability " + validAbilityId)
        );
        var blockers = new ArrayList<String>();
        if (!ownedAbilities(snapshot).contains(validAbilityId)) {
            blockers.add("Ability is not owned");
        }
        if (!definition.enabled()) {
            blockers.add("Ability is disabled");
        }
        if (!definition.slotAllowed() || definition.kind() == AbilityKind.PASSIVE) {
            blockers.add("Ability cannot be assigned to a fixed slot");
        }
        Map<ResourceLocation, ResourceLocation> assignments = currentAssignments(snapshot);
        if (validAbilityId.equals(assignments.get(slotId))) {
            blockers.add("Ability is already assigned to this slot");
        }
        return new SlotPreview(validAbilityId, slotId, Optional.ofNullable(assignments.get(slotId)), blockers);
    }

    public static CascadePlan assign(
            UUID actor,
            UUID target,
            AbilityCatalog catalog,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation abilityId,
            ResourceLocation slotId,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause
    ) {
        SlotPreview preview = previewAssign(catalog, snapshot, abilityId, slotId);
        requireAllowed(preview.blockers());
        var mutations = new ArrayList<EntitlementMutation>();
        Optional<ResourceLocation> movedFrom = Optional.empty();
        for (ResourceLocation candidate : AbilityState.slots()) {
            if (abilityId.equals(currentAssignments(snapshot).get(candidate))) {
                movedFrom = Optional.of(candidate);
                mutations.addAll(replaceAssignment(snapshot, candidate, Optional.empty()));
            }
        }
        mutations.addAll(replaceAssignment(snapshot, slotId, Optional.of(abilityId)));
        if (movedFrom.isPresent() && currentSelectedSlot(snapshot).equals(movedFrom)) {
            mutations.addAll(replaceSelectedSlot(snapshot, Optional.of(slotId)));
        }
        return plan(actor, target, snapshot, definition, idempotencyKey, cause,
                "Assign ability", ASSIGN_ORIGIN, List.of(), mutations);
    }

    public static CascadePlan unassign(
            UUID actor,
            UUID target,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation slotId,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        requireSlot(slotId);
        if (!currentAssignments(snapshot).containsKey(slotId)) {
            throw new IllegalArgumentException("Ability slot is already empty");
        }
        var mutations = new ArrayList<>(replaceAssignment(snapshot, slotId, Optional.empty()));
        if (currentSelectedSlot(snapshot).filter(slotId::equals).isPresent()) {
            mutations.addAll(replaceSelectedSlot(snapshot, Optional.empty()));
        }
        return plan(actor, target, snapshot, definition, idempotencyKey, cause,
                "Unassign ability", UNASSIGN_ORIGIN, List.of(), mutations);
    }

    public static CascadePlan selectSlot(
            UUID actor,
            UUID target,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation slotId,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        requireSlot(slotId);
        if (!currentAssignments(snapshot).containsKey(slotId)) {
            throw new IllegalArgumentException("Ability slot is empty");
        }
        if (currentSelectedSlot(snapshot).filter(slotId::equals).isPresent()) {
            throw new IllegalArgumentException("Ability slot is already selected");
        }
        return plan(actor, target, snapshot, definition, idempotencyKey, cause,
                "Select ability slot", SELECT_ORIGIN, List.of(), replaceSelectedSlot(snapshot, Optional.of(slotId)));
    }

    public static CascadePlan toggle(
            UUID actor,
            UUID target,
            AbilityCatalog catalog,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation abilityId,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause
    ) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(snapshot, "snapshot");
        ResourceLocation validAbilityId = StableId.requireValid(abilityId);
        AbilityDefinition ability = catalog.ability(validAbilityId).orElseThrow(
                () -> new IllegalArgumentException("Unknown ability " + validAbilityId)
        );
        if (!ownedAbilities(snapshot).contains(validAbilityId)) {
            throw new IllegalArgumentException("Ability is not owned");
        }
        if (!ability.enabled()) {
            throw new IllegalArgumentException("Ability is disabled");
        }
        if (ability.kind() != AbilityKind.TOGGLE) {
            throw new IllegalArgumentException("Ability is not a toggle");
        }
        boolean before = currentToggleStates(snapshot).getOrDefault(validAbilityId, ability.defaultOn());
        boolean after = !before;
        var mutations = new ArrayList<>(replaceToggleState(snapshot, validAbilityId, Optional.of(after)));
        mutations.addAll(replaceEffects(snapshot, ability, after));
        return plan(actor, target, snapshot, definition, idempotencyKey, cause,
                after ? "Enable ability toggle" : "Disable ability toggle",
                TOGGLE_ORIGIN, List.of(), mutations);
    }

    public static ActivationPreview previewActivation(
            AbilityCatalog catalog,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            ResourceLocation abilityId,
            long gameTick
    ) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(snapshot, "snapshot");
        ResourceLocation validAbilityId = StableId.requireValid(abilityId);
        requireGameTick(gameTick);
        AbilityDefinition ability = catalog.ability(validAbilityId).orElseThrow(
                () -> new IllegalArgumentException("Unknown ability " + validAbilityId)
        );
        var blockers = new ArrayList<String>();
        Set<ResourceLocation> owned = ownedAbilities(snapshot);
        if (!owned.contains(validAbilityId)) {
            blockers.add("Ability is not owned");
        }
        if (!ability.enabled()) {
            blockers.add("Ability is disabled");
        }
        if (ability.kind() != AbilityKind.ACTIVE) {
            blockers.add("Ability is not active");
        }
        if (!currentAssignments(snapshot).containsValue(validAbilityId)) {
            blockers.add("Ability is not assigned to a fixed slot");
        }
        long readyTick = storedBalance(snapshot, cooldownBalanceId(ability.cooldownGroup()), 0);
        if (readyTick > gameTick) {
            blockers.add("Cooldown has " + Math.subtractExact(readyTick, gameTick) + " ticks remaining");
        }
        AbilityState.ChargeState charge = chargeState(ability, snapshot, gameTick);
        if (!charge.ready()) {
            blockers.add("Ability has no available charges");
        }
        var running = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        var currencyLegs = new ArrayList<CurrencyCostLeg>();
        var vanillaCosts = new ArrayList<AbilityVanillaCost>();
        for (AbilityCost cost : ability.costs()) {
            if (cost instanceof AbilityVanillaCost vanilla) {
                vanillaCosts.add(vanilla);
                continue;
            }
            AbilityCurrencyCost currencyCost = (AbilityCurrencyCost) cost;
            CurrencyDefinition currency = skills.currency(currencyCost.currency()).orElseThrow(
                    () -> new IllegalArgumentException("Ability references missing currency " + currencyCost.currency())
            );
            long balance = running.computeIfAbsent(currency.id(), ignored -> currentBalance(snapshot, currency));
            long after;
            try {
                after = Math.subtractExact(balance, currencyCost.amount());
                if (after < currency.minimum()) {
                    blockers.add("Ability cost would leave " + currency.id() + " below its minimum");
                }
            } catch (ArithmeticException exception) {
                after = Long.MIN_VALUE;
                blockers.add("Ability cost is outside the supported range for " + currency.id());
            }
            currencyLegs.add(new CurrencyCostLeg(
                    currencyCost.id(), currency.id(), currencyCost.amount(), balance, after
            ));
            running.put(currency.id(), after);
        }
        return new ActivationPreview(
                ability.id(), ability.cooldownGroup(), gameTick, readyTick, charge,
                currencyLegs, vanillaCosts, ability.targeting(), ability.actions(), blockers
        );
    }

    public static ActivationPlan activate(
            UUID actor,
            UUID target,
            AbilityCatalog catalog,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation abilityId,
            long gameTick,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause
    ) {
        ActivationPreview preview = previewActivation(catalog, skills, snapshot, abilityId, gameTick);
        requireAllowed(preview.blockers());
        AbilityDefinition ability = catalog.ability(abilityId).orElseThrow();
        var balances = new ArrayList<BalanceMutation>();
        var currencyTotals = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        preview.currencyCosts().forEach(leg -> currencyTotals.merge(leg.currency(), leg.amount(), Math::addExact));
        currencyTotals.forEach((currencyId, amount) -> {
            CurrencyDefinition currency = skills.currency(currencyId).orElseThrow();
            long desired = Math.subtractExact(currentBalance(snapshot, currency), amount);
            balances.add(setBalance(snapshot, currency.id(), desired, currency.minimum(), currency.maximum()));
        });
        if (ability.cooldownTicks() > 0) {
            ResourceLocation cooldown = cooldownBalanceId(ability.cooldownGroup());
            long desired = Math.addExact(gameTick, ability.cooldownTicks());
            balances.add(setBalance(snapshot, cooldown, desired, 0, Long.MAX_VALUE));
        }
        AbilityState.ChargeState charge = preview.chargeState();
        if (ability.rechargeTicks() > 0) {
            int remaining = charge.current() - 1;
            long next = charge.current() == charge.maximum()
                    ? Math.addExact(gameTick, ability.rechargeTicks())
                    : charge.nextRechargeTick();
            balances.add(setBalance(snapshot, chargeBalanceId(ability.id()), remaining, 0, ability.maxCharges()));
            balances.add(setBalance(snapshot, rechargeBalanceId(ability.id()), next, 0, Long.MAX_VALUE));
        }
        CascadePlan transaction = plan(actor, target, snapshot, definition, idempotencyKey, cause,
                "Activate ability", ACTIVATE_ORIGIN, balances, List.of());
        return new ActivationPlan(
                preview, transaction, preview.vanillaCosts(), preview.actions(), preview.targeting(), gameTick
        );
    }

    public static Optional<CascadePlan> recharge(
            UUID target,
            AbilityCatalog catalog,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            long gameTick
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(definition, "definition");
        requireGameTick(gameTick);
        Set<ResourceLocation> owned = ownedAbilities(snapshot);
        var balances = new ArrayList<BalanceMutation>();
        for (AbilityDefinition ability : catalog.abilities().values()) {
            if (!owned.contains(ability.id()) || ability.kind() != AbilityKind.ACTIVE
                    || ability.rechargeTicks() == 0) {
                continue;
            }
            AbilityState.ChargeState state = chargeState(ability, snapshot, gameTick);
            long storedCharges = storedBalance(snapshot, chargeBalanceId(ability.id()), ability.maxCharges());
            long storedRecharge = storedBalance(snapshot, rechargeBalanceId(ability.id()), 0);
            if (state.current() != storedCharges) {
                balances.add(setBalance(snapshot, chargeBalanceId(ability.id()),
                        state.current(), 0, ability.maxCharges()));
            }
            if (state.nextRechargeTick() != storedRecharge) {
                balances.add(setBalance(snapshot, rechargeBalanceId(ability.id()),
                        state.nextRechargeTick(), 0, Long.MAX_VALUE));
            }
        }
        if (balances.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyKey key = new IdempotencyKey(
                "phase12/recharge/" + definition.generation() + "/"
                        + snapshot.stateRevision() + "/" + gameTick + "/" + target
        );
        return Optional.of(plan(target, target, snapshot, definition, key, ProgressionCause.RECONCILE,
                "Recharge abilities", RECHARGE_ORIGIN, balances, List.of()));
    }

    public static Optional<CascadePlan> reconcile(
            UUID target,
            AbilityCatalog catalog,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(definition, "definition");
        List<EntitlementMutation> required = reconciliationMutations(catalog, snapshot);
        List<EntitlementMutation> mutations = required.subList(
                0, Math.min(required.size(), CascadePlan.MAX_TOTAL_MUTATIONS));
        if (mutations.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyKey key = new IdempotencyKey(
                "phase12/reconcile/" + definition.generation() + "/"
                        + snapshot.stateRevision() + "/" + target
        );
        return Optional.of(plan(target, target, snapshot, definition, key, ProgressionCause.RECONCILE,
                "Reconcile abilities", RECONCILE_ORIGIN, List.of(), mutations));
    }

    public static CascadePlan appendReconciliation(
            CascadePlan primary,
            AbilityCatalog catalog,
            ProgressionSnapshot postPrimarySnapshot,
            DefinitionRevision currentDefinition
    ) {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(postPrimarySnapshot, "postPrimarySnapshot");
        Objects.requireNonNull(currentDefinition, "currentDefinition");
        if (!primary.transaction().definitionRevision().equals(currentDefinition)) {
            throw new IllegalArgumentException("Primary transaction definition is stale");
        }
        List<EntitlementMutation> mutations = reconciliationMutations(catalog, postPrimarySnapshot);
        long primaryMutationCount = primary.steps().stream()
                .mapToLong(step -> (long) step.balanceMutations().size()
                        + step.entitlementMutations().size()
                        + step.paidCostMutations().size())
                .sum();
        if (primaryMutationCount + mutations.size() > CascadePlan.MAX_TOTAL_MUTATIONS) {
            throw new IllegalArgumentException(
                    "Combined class and ability reconciliation exceeds the transaction mutation bound");
        }
        if (mutations.isEmpty()) {
            return primary;
        }
        var children = new ArrayList<>(primary.queuedChildren());
        for (int start = 0; start < mutations.size(); start += TransactionStep.MAX_MUTATIONS) {
            int end = Math.min(mutations.size(), start + TransactionStep.MAX_MUTATIONS);
            children.add(new TransactionStep(
                    RECONCILE_ORIGIN,
                    List.of(),
                    mutations.subList(start, end),
                    List.of(),
                    List.of()
            ));
        }
        return new CascadePlan(primary.transaction(), children);
    }

    private static List<EntitlementMutation> reconciliationMutations(
            AbilityCatalog catalog,
            ProgressionSnapshot snapshot
    ) {
        Map<ManagedKey, EntitlementContribution> desired = desiredOwnership(catalog, snapshot);
        Map<ManagedKey, EntitlementContribution> current = currentManagedOwnership(snapshot);
        var keys = new TreeSet<ManagedKey>();
        keys.addAll(current.keySet());
        keys.addAll(desired.keySet());
        var mutations = new ArrayList<EntitlementMutation>();
        for (ManagedKey key : keys) {
            EntitlementContribution before = current.get(key);
            EntitlementContribution after = desired.get(key);
            if (Objects.equals(before, after)) {
                continue;
            }
            mutations.add(after == null
                    ? EntitlementMutation.revoke(key.key(), key.source())
                    : new EntitlementMutation(key.key(), key.source(), Optional.of(after)));
        }
        return List.copyOf(mutations);
    }

    public static ResourceLocation cooldownBalanceId(ResourceLocation cooldownGroup) {
        return stateBalanceId("cooldown", cooldownGroup);
    }

    public static ResourceLocation chargeBalanceId(ResourceLocation abilityId) {
        return stateBalanceId("charges", abilityId);
    }

    public static ResourceLocation rechargeBalanceId(ResourceLocation abilityId) {
        return stateBalanceId("recharge", abilityId);
    }

    public static boolean isInternalBalance(ResourceLocation balanceId) {
        return InternalBalanceIds.isAbilityState(balanceId);
    }

    private static Map<ManagedKey, EntitlementContribution> desiredOwnership(
            AbilityCatalog catalog,
            ProgressionSnapshot snapshot
    ) {
        Set<ResourceLocation> owned = ownedAbilities(snapshot);
        var desired = new TreeMap<ManagedKey, EntitlementContribution>();
        var assignedAbilities = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        var assignedSlots = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        Map<ResourceLocation, ResourceLocation> assignments = currentAssignments(snapshot);
        for (ResourceLocation slot : AbilityState.slots()) {
            ResourceLocation abilityId = assignments.get(slot);
            if (abilityId == null || !owned.contains(abilityId) || assignedAbilities.contains(abilityId)) {
                continue;
            }
            AbilityDefinition definition = catalog.ability(abilityId).orElse(null);
            if (definition != null && definition.enabled()
                    && (!definition.slotAllowed() || definition.kind() == AbilityKind.PASSIVE)) {
                continue;
            }
            desired.put(new ManagedKey(assignmentKey(slot), assignmentSource(abilityId, slot)), bool(true));
            assignedAbilities.add(abilityId);
            assignedSlots.add(slot);
        }
        currentSelectedSlot(snapshot).filter(assignedSlots::contains).ifPresent(slot ->
                desired.put(new ManagedKey(selectedKey(slot), selectedSource(slot)), bool(true)));
        Map<ResourceLocation, Boolean> toggles = currentToggleStates(snapshot);
        for (ResourceLocation abilityId : owned) {
            Optional<AbilityDefinition> definition = catalog.ability(abilityId);
            if (definition.isEmpty()) {
                if (toggles.containsKey(abilityId)) {
                    desired.put(new ManagedKey(toggleKey(abilityId), toggleSource(abilityId)),
                            bool(toggles.get(abilityId)));
                }
                continue;
            }
            AbilityDefinition ability = definition.orElseThrow();
            if (ability.kind() == AbilityKind.TOGGLE) {
                boolean on = toggles.getOrDefault(ability.id(), ability.defaultOn());
                desired.put(new ManagedKey(toggleKey(ability.id()), toggleSource(ability.id())), bool(on));
            }
        }
        for (AbilityDefinition ability : catalog.abilities().values()) {
            if (!owned.contains(ability.id()) || !ability.enabled()) {
                continue;
            }
            boolean active = ability.kind() == AbilityKind.PASSIVE
                    || ability.kind() == AbilityKind.TOGGLE
                    && desired.getOrDefault(
                    new ManagedKey(toggleKey(ability.id()), toggleSource(ability.id())), bool(false)
            ).value() == 1;
            if (!active) {
                continue;
            }
            for (AbilityPersistentEffect effect : ability.persistentEffects()) {
                desired.put(new ManagedKey(effect.entitlementKey(), effect.source(ability.id())), effect.contribution());
            }
        }
        if (desired.size() > CascadePlan.MAX_TOTAL_MUTATIONS) {
            throw new IllegalArgumentException("Ability reconciliation exceeds the transaction mutation bound");
        }
        return desired;
    }

    private static Map<ManagedKey, EntitlementContribution> currentManagedOwnership(
            ProgressionSnapshot snapshot
    ) {
        var current = new TreeMap<ManagedKey, EntitlementContribution>();
        snapshot.ownership().forEach((key, sources) -> sources.forEach((source, contribution) -> {
            boolean logical = key.targetType().equals(AbilityEntitlementTypes.SLOT_ASSIGNMENT)
                    && source.ownerKind().equals(ASSIGNMENT_OWNER_KIND)
                    || key.targetType().equals(AbilityEntitlementTypes.SELECTED_SLOT)
                    && source.ownerKind().equals(SELECTION_OWNER_KIND)
                    || key.targetType().equals(AbilityEntitlementTypes.TOGGLE_STATE)
                    && source.ownerKind().equals(TOGGLE_OWNER_KIND);
            boolean effect = source.ownerKind().equals(DefinitionKinds.ABILITY.id())
                    && !key.targetType().equals(AbilityEntitlementTypes.OWNED);
            if (logical || effect) {
                current.put(new ManagedKey(key, source), contribution);
            }
        }));
        return current;
    }

    private static List<EntitlementMutation> replaceAssignment(
            ProgressionSnapshot snapshot,
            ResourceLocation slotId,
            Optional<ResourceLocation> abilityId
    ) {
        EntitlementKey key = assignmentKey(slotId);
        var mutations = revokeOwners(snapshot, key, ASSIGNMENT_OWNER_KIND);
        abilityId.ifPresent(value -> mutations.add(EntitlementMutation.grant(
                key, assignmentSource(value, slotId), 1, EntitlementResolver.BOOLEAN_UNION
        )));
        return mutations;
    }

    private static List<EntitlementMutation> replaceSelectedSlot(
            ProgressionSnapshot snapshot,
            Optional<ResourceLocation> selected
    ) {
        var mutations = new ArrayList<EntitlementMutation>();
        for (ResourceLocation slot : AbilityState.slots()) {
            mutations.addAll(revokeOwners(snapshot, selectedKey(slot), SELECTION_OWNER_KIND));
        }
        selected.ifPresent(slot -> mutations.add(EntitlementMutation.grant(
                selectedKey(slot), selectedSource(slot), 1, EntitlementResolver.BOOLEAN_UNION
        )));
        return mutations;
    }

    private static List<EntitlementMutation> replaceToggleState(
            ProgressionSnapshot snapshot,
            ResourceLocation abilityId,
            Optional<Boolean> state
    ) {
        EntitlementKey key = toggleKey(abilityId);
        var mutations = revokeOwners(snapshot, key, TOGGLE_OWNER_KIND);
        state.ifPresent(value -> mutations.add(EntitlementMutation.grant(
                key, toggleSource(abilityId), value ? 1 : 0, EntitlementResolver.BOOLEAN_UNION
        )));
        return mutations;
    }

    private static List<EntitlementMutation> replaceEffects(
            ProgressionSnapshot snapshot,
            AbilityDefinition ability,
            boolean active
    ) {
        var mutations = new ArrayList<EntitlementMutation>();
        for (AbilityPersistentEffect effect : ability.persistentEffects()) {
            Map<GrantSourceId, EntitlementContribution> owners = snapshot.ownership()
                    .getOrDefault(effect.entitlementKey(), Map.of());
            if (owners.containsKey(effect.source(ability.id()))) {
                mutations.add(EntitlementMutation.revoke(effect.entitlementKey(), effect.source(ability.id())));
            }
            if (active) {
                mutations.add(new EntitlementMutation(
                        effect.entitlementKey(), effect.source(ability.id()), Optional.of(effect.contribution())
                ));
            }
        }
        return mutations;
    }

    private static ArrayList<EntitlementMutation> revokeOwners(
            ProgressionSnapshot snapshot,
            EntitlementKey key,
            ResourceLocation ownerKind
    ) {
        var mutations = new ArrayList<EntitlementMutation>();
        snapshot.ownership().getOrDefault(key, Map.of()).keySet().stream()
                .filter(source -> source.ownerKind().equals(ownerKind))
                .forEach(source -> mutations.add(EntitlementMutation.revoke(key, source)));
        return mutations;
    }

    private static Map<ResourceLocation, ResourceLocation> currentAssignments(ProgressionSnapshot snapshot) {
        var assignments = new TreeMap<ResourceLocation, ResourceLocation>(ResourceLocation::compareNamespaced);
        for (ResourceLocation slot : AbilityState.slots()) {
            snapshot.ownership().getOrDefault(assignmentKey(slot), Map.of()).entrySet().stream()
                    .filter(entry -> entry.getKey().ownerKind().equals(ASSIGNMENT_OWNER_KIND))
                    .filter(entry -> entry.getKey().grantId().equals(slot))
                    .filter(entry -> entry.getValue().value() == 1)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .findFirst()
                    .ifPresent(source -> assignments.put(slot, source.ownerId()));
        }
        return assignments;
    }

    private static Optional<ResourceLocation> currentSelectedSlot(ProgressionSnapshot snapshot) {
        for (ResourceLocation slot : AbilityState.slots()) {
            EntitlementContribution contribution = snapshot.ownership()
                    .getOrDefault(selectedKey(slot), Map.of()).get(selectedSource(slot));
            if (contribution != null && contribution.value() == 1) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    private static Map<ResourceLocation, Boolean> currentToggleStates(ProgressionSnapshot snapshot) {
        var toggles = new TreeMap<ResourceLocation, Boolean>(ResourceLocation::compareNamespaced);
        snapshot.ownership().forEach((key, sources) -> {
            if (!key.targetType().equals(AbilityEntitlementTypes.TOGGLE_STATE)) {
                return;
            }
            EntitlementContribution contribution = sources.get(toggleSource(key.targetId()));
            if (contribution != null) {
                toggles.put(key.targetId(), contribution.value() == 1);
            }
        });
        return toggles;
    }

    private static AbilityState.ChargeState chargeState(
            AbilityDefinition ability,
            ProgressionSnapshot snapshot,
            long gameTick
    ) {
        if (ability.rechargeTicks() == 0) {
            return new AbilityState.ChargeState(ability.id(), ability.maxCharges(), ability.maxCharges(), 0);
        }
        long stored = storedBalance(snapshot, chargeBalanceId(ability.id()), ability.maxCharges());
        if (stored < 0 || stored > ability.maxCharges()) {
            throw new IllegalArgumentException("Stored ability charges are outside the definition bound");
        }
        int current = Math.toIntExact(stored);
        long next = storedBalance(snapshot, rechargeBalanceId(ability.id()), 0);
        if (current == ability.maxCharges()) {
            return new AbilityState.ChargeState(ability.id(), current, ability.maxCharges(), 0);
        }
        if (next == 0) {
            next = Math.addExact(gameTick, ability.rechargeTicks());
        }
        if (next <= gameTick) {
            long elapsed = Math.subtractExact(gameTick, next);
            long available = Math.addExact(1, elapsed / ability.rechargeTicks());
            int gained = (int) Math.min(available, ability.maxCharges() - current);
            current += gained;
            next = current == ability.maxCharges()
                    ? 0
                    : Math.addExact(next, Math.multiplyExact((long) gained, ability.rechargeTicks()));
        }
        return new AbilityState.ChargeState(ability.id(), current, ability.maxCharges(), next);
    }

    private static long currentBalance(ProgressionSnapshot snapshot, CurrencyDefinition currency) {
        return storedBalance(snapshot, currency.id(), currency.initial());
    }

    private static long storedBalance(
            ProgressionSnapshot snapshot,
            ResourceLocation balanceId,
            long defaultValue
    ) {
        return snapshot.balances().getOrDefault(balanceId, defaultValue);
    }

    private static BalanceMutation setBalance(
            ProgressionSnapshot snapshot,
            ResourceLocation balanceId,
            long desired,
            long minimum,
            long maximum
    ) {
        long stored = snapshot.balances().getOrDefault(balanceId, 0L);
        return new BalanceMutation(balanceId, Math.subtractExact(desired, stored), minimum, maximum);
    }

    private static CascadePlan plan(
            UUID actor,
            UUID target,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause,
            String reason,
            ResourceLocation origin,
            List<BalanceMutation> balances,
            List<EntitlementMutation> entitlements
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(cause, "cause");
        int rootCount = Math.min(TransactionStep.MAX_MUTATIONS - balances.size(), entitlements.size());
        if (rootCount < 0) {
            throw new IllegalArgumentException("Ability transaction exceeds the root mutation bound");
        }
        var steps = new ArrayList<TransactionStep>();
        steps.add(new TransactionStep(origin, balances, entitlements.subList(0, rootCount), List.of(), List.of()));
        for (int start = rootCount; start < entitlements.size(); start += TransactionStep.MAX_MUTATIONS) {
            int end = Math.min(entitlements.size(), start + TransactionStep.MAX_MUTATIONS);
            steps.add(new TransactionStep(origin, List.of(), entitlements.subList(start, end), List.of(), List.of()));
        }
        TransactionPlan transaction = new TransactionPlan(
                actor, target, idempotencyKey, snapshot.stateRevision(), definition,
                cause, reason, steps.getFirst()
        );
        return new CascadePlan(transaction, steps.subList(1, steps.size()));
    }

    private static EntitlementKey assignmentKey(ResourceLocation slotId) {
        return new EntitlementKey(AbilityEntitlementTypes.SLOT_ASSIGNMENT, requireSlot(slotId));
    }

    private static EntitlementKey selectedKey(ResourceLocation slotId) {
        return new EntitlementKey(AbilityEntitlementTypes.SELECTED_SLOT, requireSlot(slotId));
    }

    private static EntitlementKey toggleKey(ResourceLocation abilityId) {
        return new EntitlementKey(AbilityEntitlementTypes.TOGGLE_STATE, StableId.requireValid(abilityId));
    }

    private static GrantSourceId assignmentSource(ResourceLocation abilityId, ResourceLocation slotId) {
        return new GrantSourceId(ASSIGNMENT_OWNER_KIND, StableId.requireValid(abilityId), requireSlot(slotId));
    }

    private static GrantSourceId selectedSource(ResourceLocation slotId) {
        return new GrantSourceId(SELECTION_OWNER_KIND, requireSlot(slotId), requireSlot(slotId));
    }

    private static GrantSourceId toggleSource(ResourceLocation abilityId) {
        ResourceLocation valid = StableId.requireValid(abilityId);
        return new GrantSourceId(TOGGLE_OWNER_KIND, valid, valid);
    }

    private static EntitlementContribution bool(boolean value) {
        return new EntitlementContribution(value ? 1 : 0, EntitlementResolver.BOOLEAN_UNION);
    }

    private static ResourceLocation requireSlot(ResourceLocation slotId) {
        ResourceLocation valid = StableId.requireValid(slotId);
        AbilityState.slotIndex(valid);
        return valid;
    }

    private static void requireAllowed(List<String> blockers) {
        if (!blockers.isEmpty()) {
            throw new IllegalArgumentException(blockers.getFirst());
        }
    }

    private static void requireGameTick(long gameTick) {
        if (gameTick < 0 || gameTick > MAX_SAFE_GAME_TICK) {
            throw new IllegalArgumentException("Server game tick is outside the supported range");
        }
    }

    private static ResourceLocation stateBalanceId(String category, ResourceLocation source) {
        source = StableId.requireValid(source);
        try {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(StandardCharsets.UTF_8))
            );
            return ResourceLocation.fromNamespaceAndPath(
                    "progressiveskills", "ability_state/" + category + "/" + digest
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }

    public record SlotPreview(
            ResourceLocation abilityId,
            ResourceLocation slotId,
            Optional<ResourceLocation> replacedAbility,
            List<String> blockers
    ) {
        public SlotPreview {
            abilityId = StableId.requireValid(abilityId);
            slotId = requireSlot(slotId);
            replacedAbility = Objects.requireNonNull(replacedAbility, "replacedAbility");
            replacedAbility.ifPresent(StableId::requireValid);
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        }

        public boolean allowed() {
            return blockers.isEmpty();
        }
    }

    public record CurrencyCostLeg(
            ResourceLocation id,
            ResourceLocation currency,
            long amount,
            long balanceBefore,
            long balanceAfter
    ) {
        public CurrencyCostLeg {
            id = StableId.requireValid(id);
            currency = StableId.requireValid(currency);
            if (amount < 1) {
                throw new IllegalArgumentException("Ability currency cost must be positive");
            }
        }
    }

    public record ActivationPreview(
            ResourceLocation abilityId,
            ResourceLocation cooldownGroup,
            long gameTick,
            long cooldownReadyTick,
            AbilityState.ChargeState chargeState,
            List<CurrencyCostLeg> currencyCosts,
            List<AbilityVanillaCost> vanillaCosts,
            AbilityTargeting targeting,
            List<AbilityAction> actions,
            List<String> blockers
    ) {
        public ActivationPreview {
            abilityId = StableId.requireValid(abilityId);
            cooldownGroup = StableId.requireValid(cooldownGroup);
            if (gameTick < 0 || cooldownReadyTick < 0) {
                throw new IllegalArgumentException("Ability activation ticks must not be negative");
            }
            Objects.requireNonNull(chargeState, "chargeState");
            currencyCosts = List.copyOf(Objects.requireNonNull(currencyCosts, "currencyCosts"));
            vanillaCosts = List.copyOf(Objects.requireNonNull(vanillaCosts, "vanillaCosts"));
            Objects.requireNonNull(targeting, "targeting");
            actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        }

        public boolean allowed() {
            return blockers.isEmpty();
        }

        public long cooldownRemaining() {
            return cooldownReadyTick <= gameTick ? 0 : Math.subtractExact(cooldownReadyTick, gameTick);
        }
    }

    public record ActivationPlan(
            ActivationPreview preview,
            CascadePlan transaction,
            List<AbilityVanillaCost> vanillaCosts,
            List<AbilityAction> actions,
            AbilityTargeting targeting,
            long gameTick
    ) {
        public ActivationPlan {
            Objects.requireNonNull(preview, "preview");
            Objects.requireNonNull(transaction, "transaction");
            vanillaCosts = List.copyOf(Objects.requireNonNull(vanillaCosts, "vanillaCosts"));
            actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
            Objects.requireNonNull(targeting, "targeting");
            requireGameTick(gameTick);
        }

        public ResourceLocation abilityId() {
            return preview.abilityId();
        }

        public ActivationPlan withTransitionActions(List<TransitionAction> transitionActions) {
            List<TransitionAction> actions = List.copyOf(
                    Objects.requireNonNull(transitionActions, "transitionActions")
            );
            TransactionPlan before = transaction.transaction();
            TransactionStep root = before.rootStep();
            TransactionStep replacement = new TransactionStep(
                    root.origin(), root.balanceMutations(), root.entitlementMutations(),
                    root.paidCostMutations(), actions
            );
            TransactionPlan rebuilt = new TransactionPlan(
                    before.actorId(), before.targetId(), before.idempotencyKey(),
                    before.expectedStateRevision(), before.definitionRevision(),
                    before.cause(), before.reason(), replacement
            );
            return new ActivationPlan(
                    preview, new CascadePlan(rebuilt, transaction.queuedChildren()),
                    vanillaCosts, this.actions, targeting, gameTick
            );
        }
    }

    private record ManagedKey(EntitlementKey key, GrantSourceId source) implements Comparable<ManagedKey> {
        private ManagedKey {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(source, "source");
        }

        @Override
        public int compareTo(ManagedKey other) {
            int keyOrder = key.compareTo(other.key);
            return keyOrder != 0 ? keyOrder : source.compareTo(other.source);
        }
    }
}
