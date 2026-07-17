package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.ir.CanonicalValue;
import com.envisione.progressiveskills.common.ir.DefinitionHeader;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.ir.SchemaVersion;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefinitionProjectionCodecTest {
    @Test
    void projectionRoundTripsPresentationWithoutGameplayFieldsOrProvenance() {
        DefinitionKey key = DefinitionKey.parse(DefinitionKinds.SKILL, "example:secret_skill");
        var presentation = new DefinitionPresentation(
                ComponentSpec.localized("skill.example.secret", "Visible\nName"),
                Optional.of(ComponentSpec.literal("Visible description")),
                IconSpec.single(IconKind.ITEM, id("minecraft:diamond"), id("minecraft:barrier"),
                        ComponentSpec.literal("Diamond"), ComponentSpec.literal("Skill icon")),
                Set.of("Search Alias")
        );
        var definition = new CanonicalDefinition(
                new DefinitionHeader(SchemaVersion.V2, key, presentation),
                new CanonicalValue.ObjectValue(Map.of(
                        "server_only_cost", new CanonicalValue.IntegerValue(999_999),
                        "secret_command", new CanonicalValue.TextValue("op @a")
                )),
                new Provenance(id("example:pack"), "private/server/path.toml", "toml"),
                SourceMap.empty()
        );
        DefinitionProjection projection = DefinitionProjection.from(
                CanonicalIr.of(List.of(definition), AliasMap.empty()));

        byte[] encoded = DefinitionProjectionCodec.encode(projection);
        assertEquals(projection, DefinitionProjectionCodec.decode(encoded));
        String wire = new String(encoded, StandardCharsets.UTF_8);
        assertFalse(wire.contains("server_only_cost"));
        assertFalse(wire.contains("secret_command"));
        assertFalse(wire.contains("private/server/path.toml"));
        assertFalse(wire.contains("op @a"));
    }

    @Test
    void malformedCountsAndTrailingDataFailClosed() {
        byte[] valid = DefinitionProjectionCodec.encode(NetworkFixtures.definitions());
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> DefinitionProjectionCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> DefinitionProjectionCodec.decode(new byte[]{0, 0, 0, 1, 127, -1, -1, -1}));
    }

    @Test
    void sanitizedTreeViewRoundTripsWithoutServerGrantData() {
        DefinitionProjection projection = NetworkFixtures.treeDefinitions();
        byte[] encoded = DefinitionProjectionCodec.encode(projection);
        DefinitionProjection decoded = DefinitionProjectionCodec.decode(encoded);

        assertEquals(projection, decoded);
        DefinitionProjection.TreeView tree = decoded.definitions().values().iterator().next()
                .tree().orElseThrow();
        assertEquals(-5, tree.currencyMinimum());
        assertEquals(7, tree.currencyInitial());
        assertEquals(7, tree.visibleCurrencyBalance(Map.of()));
        assertEquals(0, tree.visibleCurrencyBalance(Map.of(NetworkFixtures.CURRENCY.toString(), 0L)));
        assertEquals(List.of(NetworkFixtures.NODE_ROOT), tree.nodes().stream()
                .filter(node -> node.id().equals(NetworkFixtures.NODE_BRANCH))
                .findFirst().orElseThrow().requires());
        String wire = new String(encoded, StandardCharsets.UTF_8);
        assertFalse(wire.contains("persistent_source"));
        assertFalse(wire.contains("entitlement"));
    }

    @Test
    void sanitizedClassViewsRoundTripWithoutOwnershipSourcesOrGrantValues() {
        DefinitionProjection projection = NetworkFixtures.classDefinitions();
        byte[] encoded = DefinitionProjectionCodec.encode(projection);
        DefinitionProjection decoded = DefinitionProjectionCodec.decode(encoded);

        assertEquals(projection, decoded);
        DefinitionProjection.ClassSlotView slot = decoded.definitions().entrySet().stream()
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.CLASS_SLOT))
                .findFirst().orElseThrow().getValue().classSlot().orElseThrow();
        assertEquals(2, slot.capacity());
        DefinitionProjection.ClassView classView = decoded.definitions().entrySet().stream()
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.CLASS))
                .findFirst().orElseThrow().getValue().classDefinition().orElseThrow();
        assertEquals(0, classView.slotCost());
        assertEquals(List.of(new DefinitionProjection.GrantSummary(
                "ability", NetworkFixtures.CLASS_SYNERGY, "owned", "highest", 1)), classView.grants());
        assertEquals(List.of(new DefinitionProjection.StarterItemView(
                id("minecraft:book"), 1)), classView.starterKit());
        assertEquals(List.of(NetworkFixtures.CLASS_MAGE, NetworkFixtures.CLASS_WARRIOR),
                decoded.classSynergies().get(NetworkFixtures.CLASS_SYNERGY).requiredClasses());
        String wire = new String(encoded, StandardCharsets.UTF_8);
        assertFalse(wire.contains("ownerKind"));
        assertFalse(wire.contains("sourceOwners"));
        assertFalse(wire.contains("valueUnits"));
    }

    @Test
    void projectedGameplayViewsMustMatchTheirDefinitionKinds() {
        DefinitionProjection.Entry classEntry = NetworkFixtures.classDefinitions().definitions().entrySet().stream()
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.CLASS))
                .findFirst().orElseThrow().getValue();
        assertThrows(IllegalArgumentException.class, () -> new DefinitionProjection(Map.of(
                new DefinitionKey(DefinitionKinds.SKILL, NetworkFixtures.CLASS_MAGE), classEntry
        )));
    }

    @Test
    void sanitizedAbilityViewsRoundTripWithBoundedCostsTargetsAndActions() {
        DefinitionProjection projection = NetworkFixtures.abilityDefinitions();
        byte[] encoded = DefinitionProjectionCodec.encode(projection);
        DefinitionProjection decoded = DefinitionProjectionCodec.decode(encoded);

        assertEquals(projection, decoded);
        DefinitionProjection.AbilityView guard = decoded.definitions().get(
                new DefinitionKey(DefinitionKinds.ABILITY, NetworkFixtures.ABILITY_GUARD))
                .ability().orElseThrow();
        assertEquals("active", guard.kind());
        assertEquals(2, guard.maximumCharges());
        assertEquals(List.of("hunger", "currency"), guard.costs().stream()
                .map(DefinitionProjection.AbilityCostView::type).toList());
        assertEquals(List.of("heal", "vanilla_effect"), guard.actions().stream()
                .map(DefinitionProjection.AbilityActionView::type).toList());
        assertThrows(IllegalArgumentException.class, () -> new DefinitionProjection.AbilityView(
                guard.enabled(), guard.kind(), false, guard.defaultOn(), guard.persistentEffects(),
                guard.costs(), guard.targeting(), guard.cooldownGroup(), guard.cooldownTicks(),
                guard.maximumCharges(), guard.rechargeTicks(), guard.actions()
        ));
        String wire = new String(encoded, StandardCharsets.UTF_8);
        assertFalse(wire.contains("ownerKind"));
        assertFalse(wire.contains("cooldownReadyTick"));
        assertFalse(wire.contains("actionExecutor"));
    }

    @Test
    void starterKitProjectionRejectsDuplicateItemIds() {
        DefinitionProjection.ClassView source = NetworkFixtures.classDefinitions().definitions().entrySet().stream()
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.CLASS))
                .findFirst().orElseThrow().getValue().classDefinition().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> new DefinitionProjection.ClassView(
                source.enabled(), source.slotId(), source.slotCost(), source.accessRequired(),
                source.exclusiveTags(), source.minimumSkillLevels(), source.requiredNodes(),
                source.requiredClasses(), source.selectionCost(), source.respecAllowed(),
                source.respecCost(), List.of(
                        new DefinitionProjection.StarterItemView(id("minecraft:book"), 1),
                        new DefinitionProjection.StarterItemView(id("minecraft:book"), 2)
                ), source.grants()
        ));
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
