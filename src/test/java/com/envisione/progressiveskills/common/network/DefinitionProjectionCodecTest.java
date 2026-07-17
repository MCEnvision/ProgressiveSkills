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

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
