package com.envisione.progressiveskills.common.carrier;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record CarrierDefinition(
        ResourceLocation id,
        DefinitionPresentation presentation,
        boolean enabled,
        CarrierKind carrier,
        int behaviorVersion,
        CarrierMigrationPolicy migrationPolicy,
        CarrierBindPolicy bindPolicy,
        CarrierDeliveryPolicy deliveryPolicy,
        CarrierRarity rarity,
        boolean glint,
        int stackSize,
        int charges,
        int cooldownTicks,
        List<CarrierUseAction> useActions
) implements Comparable<CarrierDefinition> {
    public CarrierDefinition {
        id = StableId.requireValid(id);
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(rarity, "rarity");
        var snapshot = new CarrierBehaviorSnapshot(
                id, carrier, behaviorVersion, migrationPolicy, bindPolicy, deliveryPolicy,
                stackSize, charges, cooldownTicks, useActions
        );
        carrier = snapshot.carrier();
        migrationPolicy = snapshot.migrationPolicy();
        bindPolicy = snapshot.bindPolicy();
        deliveryPolicy = snapshot.deliveryPolicy();
        useActions = snapshot.useActions();
    }

    public CarrierBehaviorSnapshot behaviorSnapshot() {
        return new CarrierBehaviorSnapshot(
                id, carrier, behaviorVersion, migrationPolicy, bindPolicy, deliveryPolicy,
                stackSize, charges, cooldownTicks, useActions
        );
    }

    public String behaviorDigest() {
        return behaviorSnapshot().digest();
    }

    @Override
    public int compareTo(CarrierDefinition other) {
        return id.compareNamespaced(other.id);
    }
}
