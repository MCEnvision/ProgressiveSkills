package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record AbilityDefinition(
        ResourceLocation id,
        DefinitionPresentation presentation,
        boolean enabled,
        AbilityKind kind,
        boolean slotAllowed,
        boolean defaultOn,
        List<AbilityPersistentEffect> persistentEffects,
        List<AbilityCost> costs,
        AbilityTargeting targeting,
        ResourceLocation cooldownGroup,
        int cooldownTicks,
        int maxCharges,
        int rechargeTicks,
        List<AbilityAction> actions
) implements Comparable<AbilityDefinition> {
    public static final int MAX_PERSISTENT_EFFECTS = 16;
    public static final int MAX_COSTS = 3;
    public static final int MAX_ACTIONS = 16;
    public static final int MAX_COOLDOWN_TICKS = 72_000;
    public static final int MAX_CHARGES = 16;
    public static final int MAX_RECHARGE_TICKS = 72_000;

    public AbilityDefinition {
        id = StableId.requireValid(id);
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(kind, "kind");
        persistentEffects = sortedUnique(persistentEffects, MAX_PERSISTENT_EFFECTS, "persistent effect");
        requireUniqueIds(persistentEffects.stream().map(AbilityPersistentEffect::id).toList(), "persistent effect");
        costs = sortedUnique(costs, MAX_COSTS, "cost");
        requireUniqueIds(costs.stream().map(AbilityCost::id).toList(), "cost");
        Objects.requireNonNull(targeting, "targeting");
        cooldownGroup = StableId.requireValid(cooldownGroup);
        if (cooldownTicks < 0 || cooldownTicks > MAX_COOLDOWN_TICKS) {
            throw new IllegalArgumentException("Ability cooldown must be within 0 and " + MAX_COOLDOWN_TICKS);
        }
        if (maxCharges < 1 || maxCharges > MAX_CHARGES) {
            throw new IllegalArgumentException("Ability charges must be within 1 and " + MAX_CHARGES);
        }
        if (rechargeTicks < 0 || rechargeTicks > MAX_RECHARGE_TICKS) {
            throw new IllegalArgumentException("Ability recharge must be within 0 and " + MAX_RECHARGE_TICKS);
        }
        if (maxCharges > 1 && rechargeTicks == 0) {
            throw new IllegalArgumentException("Abilities with multiple charges require positive recharge ticks");
        }
        actions = boundedCopy(actions, MAX_ACTIONS, "action");
        requireUniqueIds(actions.stream().map(AbilityAction::id).toList(), "action");
        validateLifecycle(id, kind, slotAllowed, defaultOn, persistentEffects, costs, targeting,
                cooldownGroup, cooldownTicks, maxCharges, rechargeTicks, actions);
    }

    private static void validateLifecycle(
            ResourceLocation id,
            AbilityKind kind,
            boolean slotAllowed,
            boolean defaultOn,
            List<AbilityPersistentEffect> persistentEffects,
            List<AbilityCost> costs,
            AbilityTargeting targeting,
            ResourceLocation cooldownGroup,
            int cooldownTicks,
            int maxCharges,
            int rechargeTicks,
        List<AbilityAction> actions
    ) {
        if (kind == AbilityKind.ACTIVE) {
            if (!slotAllowed) {
                throw new IllegalArgumentException("Active abilities require a fixed slot");
            }
            if (defaultOn || !persistentEffects.isEmpty()) {
                throw new IllegalArgumentException("Active abilities cannot declare toggle state or persistent effects");
            }
            if (actions.isEmpty()) {
                throw new IllegalArgumentException("Active abilities require at least one action");
            }
            if (targeting.mode() == AbilityTargetMode.BLOCK
                    && actions.stream().anyMatch(action -> action.type() != AbilityActionType.MESSAGE)) {
                throw new IllegalArgumentException("Block targeted Core abilities support message actions only");
            }
            return;
        }
        if (!costs.isEmpty() || !actions.isEmpty() || !targeting.equals(AbilityTargeting.SELF)
                || !cooldownGroup.equals(id)
                || cooldownTicks != 0 || maxCharges != 1 || rechargeTicks != 0) {
            throw new IllegalArgumentException("Passive and toggle abilities support persistent effects only");
        }
        if (persistentEffects.isEmpty()) {
            throw new IllegalArgumentException("Passive and toggle abilities require a persistent effect");
        }
        if (kind == AbilityKind.PASSIVE) {
            if (slotAllowed || defaultOn) {
                throw new IllegalArgumentException("Passive abilities cannot use a slot or toggle default state");
            }
        }
    }

    private static <T extends Comparable<? super T>> List<T> sortedUnique(
            List<T> values,
            int maximum,
            String label
    ) {
        Objects.requireNonNull(values, label + "s");
        if (values.size() > maximum) {
            throw new IllegalArgumentException("Ability " + label + " count exceeds " + maximum);
        }
        List<T> sorted = values.stream().map(value -> Objects.requireNonNull(value, label)).sorted().toList();
        if (new HashSet<>(sorted).size() != sorted.size()) {
            throw new IllegalArgumentException("Ability contains duplicate " + label + " entries");
        }
        return sorted;
    }

    private static <T> List<T> boundedCopy(List<T> values, int maximum, String label) {
        Objects.requireNonNull(values, label + "s");
        if (values.size() > maximum) {
            throw new IllegalArgumentException("Ability " + label + " count exceeds " + maximum);
        }
        return values.stream().map(value -> Objects.requireNonNull(value, label)).toList();
    }

    private static void requireUniqueIds(List<ResourceLocation> ids, String label) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Ability contains duplicate " + label + " ids");
        }
    }

    @Override
    public int compareTo(AbilityDefinition other) {
        return id.compareNamespaced(other.id);
    }
}
