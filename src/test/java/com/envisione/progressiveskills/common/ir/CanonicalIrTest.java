package com.envisione.progressiveskills.common.ir;

import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.source.SourcePosition;
import com.envisione.progressiveskills.common.source.SourceReference;
import com.envisione.progressiveskills.common.source.SourceSpan;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalIrTest {
    @Test
    void semanticProjectionExcludesOnlySourceAndProvenance() {
        var first = fixtureDefinition("skills/physique.toml", 2);
        var second = fixtureDefinition("generated/physique.json", 7);

        assertNotEquals(first, second);
        assertEquals(first.semanticProjection(), second.semanticProjection());
        assertEquals(
                CanonicalIr.of(List.of(first), AliasMap.empty()).semanticProjection(),
                CanonicalIr.of(List.of(second), AliasMap.empty()).semanticProjection()
        );
    }

    @Test
    void canonicalCollectionsAreDefensiveImmutableAndDeterministic() {
        var mutable = new HashMap<String, CanonicalValue>();
        mutable.put("zeta", new CanonicalValue.IntegerValue(2));
        mutable.put("alpha", new CanonicalValue.DecimalValue(new BigDecimal("1.000")));
        var object = new CanonicalValue.ObjectValue(mutable);
        mutable.put("later", new CanonicalValue.BooleanValue(true));

        assertEquals(List.of("alpha", "zeta"), object.fields().keySet().stream().toList());
        assertEquals(
                BigDecimal.ONE,
                ((CanonicalValue.DecimalValue) object.fields().get("alpha")).value()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> object.fields().put("blocked", new CanonicalValue.BooleanValue(false))
        );
    }

    @Test
    void irRejectsDuplicateDefinitionsAndExcessiveValueDepth() {
        var definition = fixtureDefinition("skills/physique.toml", 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> CanonicalIr.of(List.of(definition, definition), AliasMap.empty())
        );

        CanonicalValue nested = new CanonicalValue.TextValue("leaf");
        for (int depth = 0; depth < CanonicalDefinition.MAX_VALUE_DEPTH; depth++) {
            nested = new CanonicalValue.ObjectValue(Map.of("nested", nested));
        }
        var tooDeep = (CanonicalValue.ObjectValue) nested;

        assertThrows(
                IllegalArgumentException.class,
                () -> new CanonicalDefinition(
                        definition.header(),
                        tooDeep,
                        definition.provenance(),
                        SourceMap.empty()
                )
        );

        var maximumText = new CanonicalValue.TextValue(
                "x".repeat(CanonicalValue.MAX_TEXT_CODE_POINTS)
        );
        int repetitions = CanonicalDefinition.MAX_AGGREGATE_TEXT_CODE_POINTS
                / CanonicalValue.MAX_TEXT_CODE_POINTS + 1;
        var excessiveText = new CanonicalValue.ObjectValue(Map.of(
                "values",
                new CanonicalValue.ListValue(java.util.Collections.nCopies(repetitions, maximumText))
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CanonicalDefinition(
                        definition.header(),
                        excessiveText,
                        definition.provenance(),
                        SourceMap.empty()
                )
        );
    }

    @Test
    void canonicalNumericAndFieldBoundsRejectPathologicalValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CanonicalValue.DecimalValue(new BigDecimal("1e1000000000"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CanonicalValue.DecimalValue(new BigDecimal("123456789012345678901234567890123456789"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CanonicalValue.ObjectValue(Map.of(
                        "a".repeat(CanonicalValue.MAX_FIELD_NAME_CODE_POINTS + 1),
                        new CanonicalValue.BooleanValue(true)
                ))
        );
        assertThrows(IllegalArgumentException.class, () -> new CanonicalValue.TextValue("bad\u0000text"));
        assertThrows(IllegalArgumentException.class, () -> new CanonicalValue.TextValue("high\ud800"));
        assertThrows(IllegalArgumentException.class, () -> new CanonicalValue.TextValue("low\udc00"));
        assertEquals("valid \ud83d\udca5", new CanonicalValue.TextValue("valid \ud83d\udca5").value());
    }

    @Test
    void presentationSearchAliasesAreNormalizedSortedAndIsolated() {
        var aliases = new ArrayList<>(List.of("  Might  ", "Power"));
        var presentation = new DefinitionPresentation(
                ComponentSpec.literal("Physique"),
                Optional.empty(),
                fixtureIcon(),
                Set.copyOf(aliases)
        );
        aliases.add("Later");

        assertEquals(List.of("Might", "Power"), presentation.searchAliases().stream().toList());
        assertThrows(UnsupportedOperationException.class, () -> presentation.searchAliases().add("Blocked"));
    }

    @Test
    void nonPresentationalDefinitionsUseTheSameCanonicalHeader() {
        var key = DefinitionKey.parse(DefinitionKinds.PREDICATE, "mypack:nether_ready");
        var header = DefinitionHeader.withoutPresentation(SchemaVersion.CURRENT, key);

        assertTrue(header.presentation().isEmpty());
        assertEquals(key, header.key());
    }

    @Test
    void semanticSnapshotsRejectMapKeysThatContradictDefinitionHeaders() {
        var definition = fixtureDefinition("skills/physique.toml", 2).semanticProjection();
        var wrongKey = DefinitionKey.parse(DefinitionKinds.SKILL, "mypack:other");

        assertThrows(
                IllegalArgumentException.class,
                () -> new SemanticIr(Map.of(wrongKey, definition), AliasMap.empty())
        );
    }

    private static CanonicalDefinition fixtureDefinition(String sourcePath, int line) {
        var key = DefinitionKey.parse(DefinitionKinds.SKILL, "mypack:physique");
        var presentation = new DefinitionPresentation(
                ComponentSpec.literal("Physique"),
                Optional.of(ComponentSpec.literal("Raw physical conditioning.")),
                fixtureIcon(),
                Set.of("Strength")
        );
        var header = new DefinitionHeader(SchemaVersion.V2, key, presentation);
        var provenance = new Provenance(id("mypack:core"), sourcePath, "toml");
        var span = new SourceSpan(new SourcePosition(line, 1), new SourcePosition(line, 5));
        var sourceMap = SourceMap.builder()
                .put("max_level", new SourceReference(provenance, span))
                .build();
        return new CanonicalDefinition(
                header,
                new CanonicalValue.ObjectValue(Map.of(
                        "enabled", new CanonicalValue.BooleanValue(true),
                        "max_level", new CanonicalValue.IntegerValue(100)
                )),
                provenance,
                sourceMap
        );
    }

    private static IconSpec fixtureIcon() {
        return IconSpec.single(
                IconKind.ITEM,
                id("minecraft:iron_chestplate"),
                id("minecraft:barrier"),
                ComponentSpec.literal("Iron chestplate"),
                ComponentSpec.literal("Physique skill icon")
        );
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
