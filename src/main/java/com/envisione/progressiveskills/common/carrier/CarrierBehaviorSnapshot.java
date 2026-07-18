package com.envisione.progressiveskills.common.carrier;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record CarrierBehaviorSnapshot(
        ResourceLocation definitionId,
        CarrierKind carrier,
        int behaviorVersion,
        CarrierMigrationPolicy migrationPolicy,
        CarrierBindPolicy bindPolicy,
        CarrierDeliveryPolicy deliveryPolicy,
        int stackSize,
        int charges,
        int cooldownTicks,
        List<CarrierUseAction> useActions
) {
    public static final int MAX_BEHAVIOR_VERSION = 1_000_000;
    public static final int MAX_STACK_SIZE = 64;
    public static final int MAX_CHARGES = 1_000_000;
    public static final int MAX_COOLDOWN_TICKS = 72_000;
    public static final int MAX_ACTIONS = 16;

    public CarrierBehaviorSnapshot {
        definitionId = StableId.requireValid(definitionId);
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(migrationPolicy, "migrationPolicy");
        Objects.requireNonNull(bindPolicy, "bindPolicy");
        Objects.requireNonNull(deliveryPolicy, "deliveryPolicy");
        if (behaviorVersion < 1 || behaviorVersion > MAX_BEHAVIOR_VERSION) {
            throw new IllegalArgumentException("Carrier behavior version exceeds its bound");
        }
        if (stackSize < 1 || stackSize > MAX_STACK_SIZE) {
            throw new IllegalArgumentException("Carrier stack size exceeds its bound");
        }
        if (charges < 1 || charges > MAX_CHARGES) {
            throw new IllegalArgumentException("Carrier charge count exceeds its bound");
        }
        if (cooldownTicks < 0 || cooldownTicks > MAX_COOLDOWN_TICKS) {
            throw new IllegalArgumentException("Carrier cooldown exceeds its bound");
        }
        useActions = List.copyOf(Objects.requireNonNull(useActions, "useActions"));
        if (useActions.isEmpty() || useActions.size() > MAX_ACTIONS) {
            throw new IllegalArgumentException("Carrier action count must be within 1 and " + MAX_ACTIONS);
        }
        useActions.forEach(action -> Objects.requireNonNull(action, "use action"));
        if (new HashSet<>(useActions.stream().map(CarrierUseAction::id).toList()).size() != useActions.size()) {
            throw new IllegalArgumentException("Carrier use action ids must be unique");
        }
        int requiredCharges = 0;
        for (CarrierUseAction action : useActions) {
            requiredCharges = Math.addExact(requiredCharges, action.consume());
        }
        if (requiredCharges > charges) {
            throw new IllegalArgumentException("Carrier actions consume more charges than the behavior provides");
        }
    }

    public String digest() {
        return CarrierBehaviorCodec.digest(this);
    }
}
