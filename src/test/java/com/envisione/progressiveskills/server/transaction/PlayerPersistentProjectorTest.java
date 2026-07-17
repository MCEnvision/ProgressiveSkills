package com.envisione.progressiveskills.server.transaction;

import com.envisione.progressiveskills.common.classdef.ClassEntitlementTypes;
import com.envisione.progressiveskills.common.classdef.ClassGrantType;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.ProjectionChange;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPersistentProjectorTest {
    @Test
    void logicalClassEntitlementsAreAcceptedWithoutAPhysicalAdapter() {
        List<ResourceLocation> logicalTypes = List.of(
                ClassEntitlementTypes.SELECTED,
                ClassEntitlementTypes.ACTIVE,
                ClassGrantType.ABILITY.entitlementType().orElseThrow(),
                ClassGrantType.SPELL.entitlementType().orElseThrow(),
                ClassGrantType.STAGE.entitlementType().orElseThrow(),
                ClassGrantType.TREE_ACCESS.entitlementType().orElseThrow(),
                ClassGrantType.CLASS_ACCESS.entitlementType().orElseThrow()
        );
        List<ProjectionChange> changes = logicalTypes.stream().map(type -> new ProjectionChange(
                new EntitlementKey(type, id("test:target")), OptionalLong.empty(), OptionalLong.of(1)
        )).toList();

        assertTrue(PlayerPersistentProjector.validateProjectionTypes(changes).isEmpty());
    }

    @Test
    void unknownProjectionTypeIsRejectedBeforePlayerLookup() {
        ProjectionChange unknown = new ProjectionChange(
                new EntitlementKey(id("test:unknown"), id("test:target")),
                OptionalLong.empty(),
                OptionalLong.of(1)
        );

        var rejection = PlayerPersistentProjector.validateProjectionTypes(List.of(unknown));

        assertTrue(rejection.isPresent());
        assertTrue(rejection.orElseThrow().contains("No physical projector is registered"));
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
