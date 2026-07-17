package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public record AbilityState(
        Set<ResourceLocation> ownedAbilities,
        Map<ResourceLocation, ResourceLocation> assignments,
        Optional<ResourceLocation> selectedSlot,
        Map<ResourceLocation, Boolean> toggleStates,
        Map<ResourceLocation, ChargeState> charges,
        Map<ResourceLocation, Long> cooldownReadyTicks
) {
    public static final int SLOT_COUNT = 8;
    private static final List<ResourceLocation> SLOTS = java.util.stream.IntStream.range(0, SLOT_COUNT)
            .mapToObj(AbilityState::slotId)
            .toList();

    public AbilityState {
        ownedAbilities = immutableSet(ownedAbilities);
        assignments = immutableMap(assignments);
        selectedSlot = Objects.requireNonNull(selectedSlot, "selectedSlot");
        selectedSlot.ifPresent(AbilityState::requireSlot);
        toggleStates = immutableMap(toggleStates);
        charges = immutableMap(charges);
        cooldownReadyTicks = immutableLongMap(cooldownReadyTicks);
        assignments.keySet().forEach(AbilityState::requireSlot);
    }

    public static List<ResourceLocation> slots() {
        return SLOTS;
    }

    public static ResourceLocation slotId(int index) {
        if (index < 0 || index >= SLOT_COUNT) {
            throw new IllegalArgumentException("Ability slot index must be within zero and seven");
        }
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", "ability_slot/" + (index + 1));
    }

    public static int slotIndex(ResourceLocation slotId) {
        requireSlot(slotId);
        return SLOTS.indexOf(slotId);
    }

    public Optional<ResourceLocation> assignedAbility(ResourceLocation slotId) {
        requireSlot(slotId);
        return Optional.ofNullable(assignments.get(slotId));
    }

    public Optional<ResourceLocation> selectedAbility() {
        return selectedSlot.flatMap(this::assignedAbility);
    }

    public boolean toggleOn(ResourceLocation abilityId) {
        return toggleStates.getOrDefault(StableId.requireValid(abilityId), false);
    }

    public long cooldownRemaining(ResourceLocation cooldownGroup, long gameTick) {
        if (gameTick < 0) {
            throw new IllegalArgumentException("Server game tick must not be negative");
        }
        long readyTick = cooldownReadyTicks.getOrDefault(StableId.requireValid(cooldownGroup), 0L);
        return readyTick <= gameTick ? 0 : Math.subtractExact(readyTick, gameTick);
    }

    private static ResourceLocation requireSlot(ResourceLocation slotId) {
        ResourceLocation valid = StableId.requireValid(slotId);
        if (!SLOTS.contains(valid)) {
            throw new IllegalArgumentException("Unknown fixed ability slot " + valid);
        }
        return valid;
    }

    private static Set<ResourceLocation> immutableSet(Set<ResourceLocation> source) {
        Objects.requireNonNull(source, "ownedAbilities");
        var sorted = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        source.forEach(value -> sorted.add(StableId.requireValid(value)));
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static <V> Map<ResourceLocation, V> immutableMap(Map<ResourceLocation, V> source) {
        Objects.requireNonNull(source, "state map");
        var sorted = new TreeMap<ResourceLocation, V>(ResourceLocation::compareNamespaced);
        source.forEach((key, value) -> sorted.put(
                StableId.requireValid(key),
                Objects.requireNonNull(value, "state value")
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<ResourceLocation, Long> immutableLongMap(Map<ResourceLocation, Long> source) {
        Map<ResourceLocation, Long> copy = immutableMap(source);
        copy.forEach((key, value) -> {
            if (value < 0) {
                throw new IllegalArgumentException("Cooldown ready tick must not be negative");
            }
        });
        return copy;
    }

    public record ChargeState(
            ResourceLocation abilityId,
            int current,
            int maximum,
            long nextRechargeTick
    ) {
        public ChargeState {
            abilityId = StableId.requireValid(abilityId);
            if (maximum < 1 || maximum > AbilityDefinition.MAX_CHARGES) {
                throw new IllegalArgumentException("Ability charge maximum is outside the supported range");
            }
            if (current < 0 || current > maximum || nextRechargeTick < 0) {
                throw new IllegalArgumentException("Ability charge state is outside the supported range");
            }
            if (current == maximum && nextRechargeTick != 0) {
                throw new IllegalArgumentException("Full ability charges must not have a recharge tick");
            }
        }

        public boolean ready() {
            return current > 0;
        }
    }
}
