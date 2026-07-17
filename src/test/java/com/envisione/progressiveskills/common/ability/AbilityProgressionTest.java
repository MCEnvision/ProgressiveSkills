package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.classdef.ClassCatalog;
import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.skill.CurrencyDefinition;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillCanonicalCodec;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.DeliveryContract;
import com.envisione.progressiveskills.common.transaction.EntitlementContribution;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.PersistedTransactionState;
import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.ProjectionChange;
import com.envisione.progressiveskills.common.transaction.RepeatPolicy;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import com.envisione.progressiveskills.common.transaction.TransitionFailurePolicy;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityProgressionTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final ResourceLocation POINTS = id("progressiveskills:ability_points");
    private static final ResourceLocation PASSIVE = id("progressiveskills:keen_eye");
    private static final ResourceLocation TOGGLE = id("progressiveskills:guard");
    private static final ResourceLocation ACTIVE = id("progressiveskills:burst");
    private static final ResourceLocation PASSIVE_FLAG = id("progressiveskills:keen");
    private static final ResourceLocation TOGGLE_FLAG = id("progressiveskills:guarding");
    private static final ResourceLocation COOLDOWN = id("progressiveskills:movement");
    private static final DefinitionRevision REVISION = new DefinitionRevision(12, "b".repeat(64));

    @Test
    void fixedSlotsAndHashedRuntimeKeysRemainStableAndBounded() {
        assertEquals(8, AbilityState.slots().size());
        assertEquals(id("progressiveskills:ability_slot/1"), AbilityState.slotId(0));
        assertEquals(id("progressiveskills:ability_slot/8"), AbilityState.slotId(7));
        assertEquals(7, AbilityState.slotIndex(AbilityState.slotId(7)));
        assertThrows(IllegalArgumentException.class, () -> AbilityState.slotId(8));

        ResourceLocation longId = id("longnamespace:" + "a".repeat(64) + "/"
                + "b".repeat(64) + "/" + "c".repeat(64) + "/" + "d".repeat(60));
        ResourceLocation first = AbilityProgression.cooldownBalanceId(longId);
        ResourceLocation second = AbilityProgression.cooldownBalanceId(longId);
        assertEquals(first, second);
        assertNotEquals(first, AbilityProgression.chargeBalanceId(longId));
        assertTrue(first.toString().length() <= 320);
        assertTrue(AbilityProgression.isInternalBalance(first));
        assertTrue(AbilityProgression.isInternalBalance(AbilityProgression.chargeBalanceId(longId)));
        assertTrue(AbilityProgression.isInternalBalance(AbilityProgression.rechargeBalanceId(longId)));
        assertFalse(AbilityProgression.isInternalBalance(POINTS));
        assertFalse(AbilityProgression.isInternalBalance(id("progressiveskills:ability_state/cooldown/not_a_hash")));
    }

    @Test
    void reconciliationInitializesToggleAndProjectsPassiveWithoutReplay() {
        Catalogs catalogs = catalogs(active(20, 2, 10, true));
        ProgressionTransactionService service = service(
                Map.of(), ownership(PASSIVE, TOGGLE)
        );

        CascadePlan firstPlan = AbilityProgression.reconcile(
                PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION
        ).orElseThrow();
        TransactionResult first = execute(service, firstPlan);

        assertTrue(first.status().committed());
        AbilityState after = AbilityProgression.state(catalogs.abilities(), service.snapshot(PLAYER), 100);
        assertFalse(after.toggleOn(TOGGLE));
        assertEquals(1L, service.snapshot(PLAYER).projectedValues().get(
                new EntitlementKey(AbilityFlagEffect.FLAG_ENTITLEMENT_TYPE, PASSIVE_FLAG)
        ));
        assertFalse(service.snapshot(PLAYER).projectedValues().containsKey(
                new EntitlementKey(AbilityFlagEffect.FLAG_ENTITLEMENT_TYPE, TOGGLE_FLAG)
        ));
        assertTrue(AbilityProgression.reconcile(
                PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION
        ).isEmpty());

        CascadePlan toggle = AbilityProgression.toggle(
                PLAYER, PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION,
                TOGGLE, new IdempotencyKey("phase12/test/toggle/on"), ProgressionCause.GAMEPLAY
        );
        TransactionResult toggled = execute(service, toggle);
        TransactionResult replay = execute(service, toggle);

        assertTrue(toggled.status().committed());
        assertTrue(replay.replayed());
        assertTrue(AbilityProgression.state(catalogs.abilities(), service.snapshot(PLAYER), 100).toggleOn(TOGGLE));
        assertEquals(1L, service.snapshot(PLAYER).projectedValues().get(
                new EntitlementKey(AbilityFlagEffect.FLAG_ENTITLEMENT_TYPE, TOGGLE_FLAG)
        ));
        assertEquals(2L * FixedPoint.SCALE, service.snapshot(PLAYER).projectedValues().get(
                new EntitlementKey(AttributeOperation.ADD_VALUE.targetType(), id("minecraft:generic.armor"))
        ));

        assertTrue(execute(service, AbilityProgression.toggle(
                PLAYER, PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION,
                TOGGLE, new IdempotencyKey("phase12/test/toggle/off"), ProgressionCause.GAMEPLAY
        )).status().committed());
        assertFalse(AbilityProgression.state(catalogs.abilities(), service.snapshot(PLAYER), 100).toggleOn(TOGGLE));
    }

    @Test
    void assignmentsMoveAcrossEightSlotsPersistAndClearSelectedSlot() {
        Catalogs catalogs = catalogs(active(20, 2, 10, true));
        ProgressionTransactionService service = service(Map.of(), ownership(TOGGLE, ACTIVE, PASSIVE));
        ResourceLocation first = AbilityState.slotId(0);
        ResourceLocation second = AbilityState.slotId(1);
        ResourceLocation third = AbilityState.slotId(2);

        assertFalse(AbilityProgression.previewAssign(
                catalogs.abilities(), service.snapshot(PLAYER), PASSIVE, first
        ).allowed());
        assertTrue(execute(service, AbilityProgression.assign(
                PLAYER, PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION,
                TOGGLE, first, key("assign/guard"), ProgressionCause.GAMEPLAY
        )).status().committed());
        assertTrue(execute(service, AbilityProgression.assign(
                PLAYER, PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION,
                ACTIVE, second, key("assign/burst"), ProgressionCause.GAMEPLAY
        )).status().committed());
        assertTrue(execute(service, AbilityProgression.selectSlot(
                PLAYER, PLAYER, service.snapshot(PLAYER), REVISION, first,
                key("select/guard"), ProgressionCause.GAMEPLAY
        )).status().committed());

        assertTrue(execute(service, AbilityProgression.assign(
                PLAYER, PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION,
                TOGGLE, third, key("move/guard"), ProgressionCause.GAMEPLAY
        )).status().committed());
        AbilityState moved = AbilityProgression.state(catalogs.abilities(), service.snapshot(PLAYER), 100);
        assertEquals(Map.of(second, ACTIVE, third, TOGGLE), moved.assignments());
        assertEquals(Optional.of(third), moved.selectedSlot());

        ProgressionTransactionService restored = service(Map.of(), Map.of());
        restored.restoreAccount(PLAYER, service.exportAccount(PLAYER));
        assertEquals(moved, AbilityProgression.state(catalogs.abilities(), restored.snapshot(PLAYER), 100));

        assertTrue(execute(restored, AbilityProgression.unassign(
                PLAYER, PLAYER, restored.snapshot(PLAYER), REVISION, third,
                key("unassign/guard"), ProgressionCause.GAMEPLAY
        )).status().committed());
        AbilityState unassigned = AbilityProgression.state(catalogs.abilities(), restored.snapshot(PLAYER), 100);
        assertEquals(Map.of(second, ACTIVE), unassigned.assignments());
        assertEquals(Optional.empty(), unassigned.selectedSlot());
    }

    @Test
    void activationCommitsNamedCurrencyCooldownAndChargesExactlyOnce() {
        Catalogs catalogs = catalogs(active(20, 2, 10, true));
        ProgressionTransactionService service = service(Map.of(), assignedOwnership(ACTIVE));

        AbilityProgression.ActivationPreview preview = AbilityProgression.previewActivation(
                catalogs.abilities(), catalogs.skills(), service.snapshot(PLAYER), ACTIVE, 100
        );
        assertTrue(preview.allowed(), preview.blockers().toString());
        assertEquals(1, preview.currencyCosts().size());
        assertEquals(10, preview.currencyCosts().getFirst().balanceBefore());
        assertEquals(8, preview.currencyCosts().getFirst().balanceAfter());
        assertEquals(1, preview.vanillaCosts().size());
        assertEquals(1, preview.actions().size());

        AbilityProgression.ActivationPlan activation = AbilityProgression.activate(
                PLAYER, PLAYER, catalogs.abilities(), catalogs.skills(), service.snapshot(PLAYER), REVISION,
                ACTIVE, 100, key("activate/first"), ProgressionCause.GAMEPLAY
        );
        TransitionAction bridge = transition();
        AbilityProgression.ActivationPlan bridged = activation.withTransitionActions(List.of(bridge));
        assertEquals(List.of(bridge), bridged.transaction().transaction().rootStep().transitionActions());
        assertEquals(activation.actions(), bridged.actions());

        TransactionResult first = execute(service, bridged.transaction());
        TransactionResult replay = execute(service, bridged.transaction());
        assertTrue(first.status().committed());
        assertTrue(replay.replayed());
        ProgressionSnapshot after = service.snapshot(PLAYER);
        assertEquals(8L, after.balances().get(POINTS));
        AbilityState state = AbilityProgression.state(catalogs.abilities(), after, 100);
        assertEquals(1, state.charges().get(ACTIVE).current());
        assertEquals(110, state.charges().get(ACTIVE).nextRechargeTick());
        assertEquals(120, state.cooldownReadyTicks().get(COOLDOWN));
        assertEquals(20, state.cooldownRemaining(COOLDOWN, 100));
        assertFalse(AbilityProgression.previewActivation(
                catalogs.abilities(), catalogs.skills(), after, ACTIVE, 101
        ).allowed());

        CascadePlan recharge = AbilityProgression.recharge(
                PLAYER, catalogs.abilities(), after, REVISION, 110
        ).orElseThrow();
        assertTrue(execute(service, recharge).status().committed());
        AbilityState recharged = AbilityProgression.state(catalogs.abilities(), service.snapshot(PLAYER), 110);
        assertEquals(2, recharged.charges().get(ACTIVE).current());
        assertEquals(0, recharged.charges().get(ACTIVE).nextRechargeTick());

        AbilityProgression.ActivationPlan stale = AbilityProgression.activate(
                PLAYER, PLAYER, catalogs.abilities(), catalogs.skills(), service.snapshot(PLAYER), REVISION,
                ACTIVE, 120, key("activate/stale"), ProgressionCause.GAMEPLAY
        );
        assertTrue(execute(service, AbilityProgression.selectSlot(
                PLAYER, PLAYER, service.snapshot(PLAYER), REVISION, AbilityState.slotId(0),
                key("select/stale"), ProgressionCause.GAMEPLAY
        )).status().committed());
        TransactionResult staleResult = execute(service, stale.transaction());
        assertFalse(staleResult.status().committed());
        assertEquals(ProgressionTransactionService.STALE_STATE, staleResult.diagnosticCode());
    }

    @Test
    void insufficientCurrencyRejectsBeforeAnyMutation() {
        AbilityDefinition expensive = active(0, 1, 0, false, List.of(
                new AbilityCurrencyCost(id("progressiveskills:burst/points"), POINTS, 12)
        ));
        Catalogs catalogs = catalogs(expensive);
        ProgressionTransactionService service = service(Map.of(), assignedOwnership(ACTIVE));

        AbilityProgression.ActivationPreview preview = AbilityProgression.previewActivation(
                catalogs.abilities(), catalogs.skills(), service.snapshot(PLAYER), ACTIVE, 10
        );

        assertFalse(preview.allowed());
        assertEquals(1, preview.currencyCosts().size());
        assertEquals(-2, preview.currencyCosts().getFirst().balanceAfter());
        assertThrows(IllegalArgumentException.class, () -> AbilityProgression.activate(
                PLAYER, PLAYER, catalogs.abilities(), catalogs.skills(), service.snapshot(PLAYER), REVISION,
                ACTIVE, 10, key("activate/expensive"), ProgressionCause.GAMEPLAY
        ));
        assertEquals(0, service.snapshot(PLAYER).stateRevision());
        assertFalse(service.snapshot(PLAYER).balances().containsKey(POINTS));
    }

    @Test
    void reconciliationRemovesUnownedRuntimeStateButPreservesForeignCoowner() {
        Catalogs catalogs = catalogs(active(20, 2, 10, true));
        ResourceLocation slot = AbilityState.slotId(0);
        EntitlementKey effectKey = new EntitlementKey(AbilityFlagEffect.FLAG_ENTITLEMENT_TYPE, TOGGLE_FLAG);
        GrantSourceId abilityEffect = new GrantSourceId(
                DefinitionKinds.ABILITY.id(), TOGGLE, id("progressiveskills:guard/flag")
        );
        GrantSourceId foreign = new GrantSourceId(
                id("progressiveskills:test"), id("progressiveskills:foreign"), id("progressiveskills:foreign/flag")
        );
        Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> stale = Map.of(
                new EntitlementKey(AbilityEntitlementTypes.SLOT_ASSIGNMENT, slot), Map.of(
                        new GrantSourceId(AbilityProgression.ASSIGNMENT_OWNER_KIND, TOGGLE, slot), bool(true)
                ),
                new EntitlementKey(AbilityEntitlementTypes.SELECTED_SLOT, slot), Map.of(
                        new GrantSourceId(AbilityProgression.SELECTION_OWNER_KIND, slot, slot), bool(true)
                ),
                new EntitlementKey(AbilityEntitlementTypes.TOGGLE_STATE, TOGGLE), Map.of(
                        new GrantSourceId(AbilityProgression.TOGGLE_OWNER_KIND, TOGGLE, TOGGLE), bool(true)
                ),
                effectKey, Map.of(abilityEffect, highest(true), foreign, highest(true))
        );
        ProgressionTransactionService service = service(Map.of(), stale);

        assertTrue(execute(service, AbilityProgression.reconcile(
                PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION
        ).orElseThrow()).status().committed());

        AbilityState state = AbilityProgression.state(catalogs.abilities(), service.snapshot(PLAYER), 10);
        assertTrue(state.assignments().isEmpty());
        assertTrue(state.selectedSlot().isEmpty());
        assertTrue(state.toggleStates().isEmpty());
        assertEquals(Map.of(foreign, highest(true)), service.snapshot(PLAYER).ownership().get(effectKey));
        assertEquals(1L, service.snapshot(PLAYER).projectedValues().get(effectKey));
    }

    @Test
    void reconciliationPreservesOrphanIdentityButRemovesItsEffects() {
        Catalogs catalogs = catalogs(active(20, 2, 10, true));
        ResourceLocation orphan = id("progressiveskills:removed_ability");
        ResourceLocation slot = AbilityState.slotId(0);
        ResourceLocation orphanFlag = id("progressiveskills:removed_flag");
        EntitlementKey effectKey = new EntitlementKey(AbilityFlagEffect.FLAG_ENTITLEMENT_TYPE, orphanFlag);
        var stale = new java.util.LinkedHashMap<
                EntitlementKey, Map<GrantSourceId, EntitlementContribution>>(ownership(orphan));
        stale.put(new EntitlementKey(AbilityEntitlementTypes.SLOT_ASSIGNMENT, slot), Map.of(
                new GrantSourceId(AbilityProgression.ASSIGNMENT_OWNER_KIND, orphan, slot), bool(true)
        ));
        stale.put(new EntitlementKey(AbilityEntitlementTypes.SELECTED_SLOT, slot), Map.of(
                new GrantSourceId(AbilityProgression.SELECTION_OWNER_KIND, slot, slot), bool(true)
        ));
        stale.put(new EntitlementKey(AbilityEntitlementTypes.TOGGLE_STATE, orphan), Map.of(
                new GrantSourceId(AbilityProgression.TOGGLE_OWNER_KIND, orphan, orphan), bool(true)
        ));
        stale.put(effectKey, Map.of(
                new GrantSourceId(DefinitionKinds.ABILITY.id(), orphan, orphanFlag), bool(true)
        ));
        ProgressionTransactionService service = service(Map.of(), stale);

        assertTrue(execute(service, AbilityProgression.reconcile(
                PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION
        ).orElseThrow()).status().committed());

        AbilityState state = AbilityProgression.state(catalogs.abilities(), service.snapshot(PLAYER), 10);
        assertEquals(Map.of(slot, orphan), state.assignments());
        assertEquals(Optional.of(slot), state.selectedSlot());
        assertTrue(state.toggleOn(orphan));
        assertFalse(service.snapshot(PLAYER).ownership().containsKey(effectKey));
        assertFalse(service.snapshot(PLAYER).projectedValues().containsKey(effectKey));
    }

    @Test
    void reconciliationPreservesDisabledAssignmentAndToggleButRemovesEffects() {
        AbilityDefinition active = active(20, 2, 10, true);
        Catalogs enabled = catalogs(active);
        Catalogs disabled = catalogs(active, toggle(false));
        ProgressionTransactionService service = service(Map.of(), ownership(TOGGLE));
        ResourceLocation slot = AbilityState.slotId(0);

        assertTrue(execute(service, AbilityProgression.reconcile(
                PLAYER, enabled.abilities(), service.snapshot(PLAYER), REVISION
        ).orElseThrow()).status().committed());
        assertTrue(execute(service, AbilityProgression.assign(
                PLAYER, PLAYER, enabled.abilities(), service.snapshot(PLAYER), REVISION,
                TOGGLE, slot, key("assign/disabled"), ProgressionCause.GAMEPLAY
        )).status().committed());
        assertTrue(execute(service, AbilityProgression.toggle(
                PLAYER, PLAYER, enabled.abilities(), service.snapshot(PLAYER), REVISION,
                TOGGLE, key("toggle/disabled"), ProgressionCause.GAMEPLAY
        )).status().committed());

        assertTrue(execute(service, AbilityProgression.reconcile(
                PLAYER, disabled.abilities(), service.snapshot(PLAYER), REVISION
        ).orElseThrow()).status().committed());

        AbilityState state = AbilityProgression.state(disabled.abilities(), service.snapshot(PLAYER), 10);
        assertEquals(Map.of(slot, TOGGLE), state.assignments());
        assertTrue(state.toggleOn(TOGGLE));
        assertFalse(service.snapshot(PLAYER).projectedValues().containsKey(
                new EntitlementKey(AbilityFlagEffect.FLAG_ENTITLEMENT_TYPE, TOGGLE_FLAG)
        ));
        assertFalse(service.snapshot(PLAYER).projectedValues().containsKey(
                new EntitlementKey(AttributeOperation.ADD_VALUE.targetType(), id("minecraft:generic.armor"))
        ));
    }

    @Test
    void reconciliationBatchesLargeStaleStateWithinCascadeBounds() {
        Catalogs catalogs = catalogs(active(20, 2, 10, true));
        var stale = new java.util.LinkedHashMap<
                EntitlementKey, Map<GrantSourceId, EntitlementContribution>>();
        for (int index = 0; index < 520; index++) {
            ResourceLocation target = id("progressiveskills:stale_flag/" + index);
            ResourceLocation owner = id("progressiveskills:removed_ability/" + index);
            stale.put(new EntitlementKey(AbilityFlagEffect.FLAG_ENTITLEMENT_TYPE, target), Map.of(
                    new GrantSourceId(DefinitionKinds.ABILITY.id(), owner, target), highest(true)
            ));
        }
        ProgressionTransactionService service = service(Map.of(), stale);

        CascadePlan first = AbilityProgression.reconcile(
                PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION
        ).orElseThrow();
        assertEquals(CascadePlan.MAX_TOTAL_MUTATIONS, first.steps().stream()
                .mapToInt(step -> step.entitlementMutations().size()).sum());
        assertTrue(execute(service, first).status().committed());
        assertEquals(8, service.snapshot(PLAYER).ownership().size());

        CascadePlan second = AbilityProgression.reconcile(
                PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION
        ).orElseThrow();
        assertEquals(8, second.steps().stream().mapToInt(step -> step.entitlementMutations().size()).sum());
        assertTrue(execute(service, second).status().committed());
        assertTrue(AbilityProgression.reconcile(
                PLAYER, catalogs.abilities(), service.snapshot(PLAYER), REVISION
        ).isEmpty());
    }

    private static AbilityDefinition active(int cooldown, int charges, int recharge, boolean currency) {
        List<AbilityCost> costs = new ArrayList<>();
        if (currency) {
            costs.add(new AbilityCurrencyCost(id("progressiveskills:burst/points"), POINTS, 2));
        }
        costs.add(new AbilityVanillaCost(
                id("progressiveskills:burst/hunger"), AbilityCostType.HUNGER, 3
        ));
        return active(cooldown, charges, recharge, true, costs);
    }

    private static AbilityDefinition active(
            int cooldown,
            int charges,
            int recharge,
            boolean vanillaAction,
            List<AbilityCost> costs
    ) {
        List<AbilityAction> actions = vanillaAction
                ? List.of(new AbilityHealAction(id("progressiveskills:burst/heal"), 2 * FixedPoint.SCALE))
                : List.of(new AbilityMessageAction(id("progressiveskills:burst/message"), ComponentSpec.literal("Burst")));
        return new AbilityDefinition(
                ACTIVE, presentation(), true, AbilityKind.ACTIVE, true, false,
                List.of(), costs, AbilityTargeting.SELF, COOLDOWN,
                cooldown, charges, recharge, actions
        );
    }

    private static AbilityDefinition passive() {
        return new AbilityDefinition(
                PASSIVE, presentation(), true, AbilityKind.PASSIVE, false, false,
                List.of(new AbilityFlagEffect(id("progressiveskills:keen_eye/flag"), PASSIVE_FLAG, true)),
                List.of(), AbilityTargeting.SELF, PASSIVE, 0, 1, 0, List.of()
        );
    }

    private static AbilityDefinition toggle() {
        return toggle(true);
    }

    private static AbilityDefinition toggle(boolean enabled) {
        return new AbilityDefinition(
                TOGGLE, presentation(), enabled, AbilityKind.TOGGLE, true, false,
                List.of(
                        new AbilityAttributeEffect(
                                id("progressiveskills:guard/armor"), id("minecraft:generic.armor"),
                                AttributeOperation.ADD_VALUE, 2 * FixedPoint.SCALE
                        ),
                        new AbilityFlagEffect(id("progressiveskills:guard/flag"), TOGGLE_FLAG, true)
                ),
                List.of(), AbilityTargeting.SELF, TOGGLE, 0, 1, 0, List.of()
        );
    }

    private static Catalogs catalogs(AbilityDefinition active) {
        return catalogs(active, toggle());
    }

    private static Catalogs catalogs(AbilityDefinition active, AbilityDefinition toggle) {
        CurrencyDefinition currency = new CurrencyDefinition(
                POINTS, presentation(), 0, 1_000, 10, "character"
        );
        Provenance provenance = new Provenance(id("progressiveskills:test_pack"), "memory", "test");
        var ir = CanonicalIr.of(List.of(
                SkillCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.CURRENCY, POINTS),
                        currency, provenance, SourceMap.empty()
                ),
                AbilityCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.ABILITY, PASSIVE),
                        passive(), provenance, SourceMap.empty()
                ),
                AbilityCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.ABILITY, TOGGLE),
                        toggle, provenance, SourceMap.empty()
                ),
                AbilityCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.ABILITY, ACTIVE),
                        active, provenance, SourceMap.empty()
                )
        ), AliasMap.empty());
        SkillCatalog skills = SkillCatalog.from(ir);
        TreeCatalog trees = TreeCatalog.from(ir, skills);
        ClassCatalog classes = ClassCatalog.from(ir, skills, trees);
        return new Catalogs(skills, AbilityCatalog.from(ir, skills, classes));
    }

    private static ProgressionTransactionService service(
            Map<ResourceLocation, Long> balances,
            Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership
    ) {
        var service = new ProgressionTransactionService(8, 128, 128, 128, 128, Clock.systemUTC());
        service.restoreAccount(PLAYER, new PersistedTransactionState(
                0, balances, ownership, Map.of(), Map.of(), Map.of(), List.of()
        ));
        return service;
    }

    private static Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership(
            ResourceLocation... abilities
    ) {
        var result = new java.util.LinkedHashMap<EntitlementKey, Map<GrantSourceId, EntitlementContribution>>();
        for (ResourceLocation ability : abilities) {
            result.put(new EntitlementKey(AbilityEntitlementTypes.OWNED, ability), Map.of(
                    new GrantSourceId(id("progressiveskills:test"), id("progressiveskills:owner"), ability), bool(true)
            ));
        }
        return result;
    }

    private static Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> assignedOwnership(
            ResourceLocation ability
    ) {
        var result = new java.util.LinkedHashMap<>(ownership(ability));
        ResourceLocation slot = AbilityState.slotId(0);
        result.put(new EntitlementKey(AbilityEntitlementTypes.SLOT_ASSIGNMENT, slot), Map.of(
                new GrantSourceId(AbilityProgression.ASSIGNMENT_OWNER_KIND, ability, slot), bool(true)
        ));
        return result;
    }

    private static TransactionResult execute(ProgressionTransactionService service, CascadePlan plan) {
        return service.execute(plan, REVISION, new NoopProjector(), new NoopExecutor());
    }

    private static IdempotencyKey key(String value) {
        return new IdempotencyKey("phase12/test/" + value);
    }

    private static EntitlementContribution bool(boolean value) {
        return new EntitlementContribution(value ? 1 : 0, EntitlementResolver.BOOLEAN_UNION);
    }

    private static EntitlementContribution highest(boolean value) {
        return new EntitlementContribution(value ? 1 : 0, EntitlementResolver.HIGHEST);
    }

    private static TransitionAction transition() {
        return new TransitionAction(
                id("progressiveskills:test_action"),
                new GrantSourceId(DefinitionKinds.ABILITY.id(), ACTIVE, id("progressiveskills:burst/test_action")),
                "server authored", 1, RepeatPolicy.ONCE_PER_TRANSACTION,
                DeliveryContract.EFFECTIVELY_ONCE, TransitionFailurePolicy.STOP
        );
    }

    private static DefinitionPresentation presentation() {
        ComponentSpec text = ComponentSpec.literal("Ability");
        return new DefinitionPresentation(
                text,
                Optional.empty(),
                IconSpec.single(IconKind.ITEM, id("minecraft:stone"), id("minecraft:barrier"), text),
                Set.of()
        );
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private record Catalogs(SkillCatalog skills, AbilityCatalog abilities) {
    }

    private static final class NoopProjector implements PersistentProjector {
        @Override
        public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
            return Optional.empty();
        }

        @Override
        public void apply(UUID targetId, List<ProjectionChange> changes) {
        }
    }

    private static final class NoopExecutor implements TransitionActionExecutor {
        @Override
        public Optional<String> validate(UUID targetId, TransitionAction action) {
            return Optional.empty();
        }

        @Override
        public ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action) {
            return ActionExecution.success("executed");
        }
    }
}
