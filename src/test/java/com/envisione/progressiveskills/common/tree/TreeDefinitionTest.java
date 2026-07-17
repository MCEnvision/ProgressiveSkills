package com.envisione.progressiveskills.common.tree;

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
import com.envisione.progressiveskills.common.skill.CurveRounding;
import com.envisione.progressiveskills.common.skill.SkillCanonicalCodec;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.skill.SkillCurve;
import com.envisione.progressiveskills.common.skill.SkillDefinition;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TreeDefinitionTest {
    private static final ResourceLocation TREE = id("test:training");
    private static final ResourceLocation NODE = id("test:training/root");
    private static final ResourceLocation SKILL = id("test:skill");
    private static final ResourceLocation CURRENCY = id("test:points");

    @Test
    void canonicalRoundTripRetainsTheBoundedCoreModel() {
        TreeDefinition tree = tree(1, 0, 1, AttributeOperation.ADD_VALUE, id("test:health"), id("test:grant"));
        var canonical = TreeCanonicalCodec.encode(
                new DefinitionKey(DefinitionKinds.TREE, TREE),
                tree,
                provenance("trees/training.toml"),
                SourceMap.empty()
        );
        assertEquals(tree, TreeCanonicalCodec.decode(canonical));
    }

    @Test
    void lineageIgnoresMutablePlanningValuesAndTracksGrantShape() {
        TreeCatalog first = catalog(tree(
                1, 0, 1, AttributeOperation.ADD_VALUE, id("test:health"), id("test:grant")
        ));
        TreeCatalog mutable = catalog(tree(
                9, 7, 8, AttributeOperation.ADD_VALUE, id("test:health"), id("test:grant")
        ));
        String expected = first.nodeLineageFingerprint(TREE, NODE);
        assertEquals(expected, mutable.nodeLineageFingerprint(TREE, NODE));
        assertEquals(64, expected.length());

        assertNotEquals(expected, catalog(tree(
                9, 7, 8, AttributeOperation.ADD_MULTIPLIED_BASE, id("test:health"), id("test:grant")
        )).nodeLineageFingerprint(TREE, NODE));
        assertNotEquals(expected, catalog(tree(
                9, 7, 8, AttributeOperation.ADD_VALUE, id("test:armor"), id("test:grant")
        )).nodeLineageFingerprint(TREE, NODE));
        assertNotEquals(expected, catalog(tree(
                9, 7, 8, AttributeOperation.ADD_VALUE, id("test:health"), id("test:other_grant")
        )).nodeLineageFingerprint(TREE, NODE));
    }

    @Test
    void missingAndCyclicSameTreePrerequisitesAreRejected() {
        TreeNodeDefinition missing = node(
                NODE, 1, 0, 0, List.of(id("test:training/missing")), List.of(), 1,
                AttributeOperation.ADD_VALUE, id("test:health"), id("test:grant")
        );
        assertThrows(IllegalArgumentException.class, () -> definition(List.of(missing)));

        TreeNodeDefinition first = node(
                NODE, 1, 0, 0, List.of(id("test:training/second")), List.of(), 1,
                AttributeOperation.ADD_VALUE, id("test:health"), id("test:first_grant")
        );
        TreeNodeDefinition second = node(
                id("test:training/second"), 1, 0, 1, List.of(), List.of(NODE), 1,
                AttributeOperation.ADD_VALUE, id("test:armor"), id("test:second_grant")
        );
        assertThrows(IllegalArgumentException.class, () -> definition(List.of(first, second)));
    }

    @Test
    void grantAndWorstCaseRefundMutationBoundsAreEnforcedAtDefinitionTime() {
        var tooManyGrants = new ArrayList<TreeAttributeGrant>();
        for (int index = 0; index <= TreeNodeDefinition.MAX_GRANTS; index++) {
            tooManyGrants.add(new TreeAttributeGrant(
                    id("test:oversized/grant_" + index),
                    id("test:attribute_" + index),
                    AttributeOperation.ADD_VALUE,
                    1
            ));
        }
        assertThrows(IllegalArgumentException.class, () -> new TreeNodeDefinition(
                id("test:oversized"), presentation("Oversized"), 1, 0, 0,
                List.of(), List.of(), Map.of(), tooManyGrants
        ));

        assertDoesNotThrow(() -> definition(mutationBoundNodes(15)));
        assertThrows(IllegalArgumentException.class, () -> definition(mutationBoundNodes(16)));
    }

    private static List<TreeNodeDefinition> mutationBoundNodes(int count) {
        var nodes = new ArrayList<TreeNodeDefinition>();
        for (int nodeIndex = 0; nodeIndex < count; nodeIndex++) {
            var grants = new ArrayList<TreeAttributeGrant>();
            for (int grantIndex = 0; grantIndex < TreeNodeDefinition.MAX_GRANTS; grantIndex++) {
                grants.add(new TreeAttributeGrant(
                        id("test:bounded/node_" + nodeIndex + "/grant_" + grantIndex),
                        id("test:attribute_" + grantIndex),
                        AttributeOperation.ADD_VALUE,
                        1
                ));
            }
            nodes.add(new TreeNodeDefinition(
                    id("test:bounded/node_" + nodeIndex),
                    presentation("Node " + nodeIndex),
                    1,
                    nodeIndex,
                    0,
                    List.of(),
                    List.of(),
                    Map.of(),
                    grants
            ));
        }
        return nodes;
    }

    private static TreeDefinition tree(
            long cost,
            int row,
            int minimum,
            AttributeOperation operation,
            ResourceLocation attribute,
            ResourceLocation grant
    ) {
        return definition(List.of(node(
                NODE, cost, row, 0, List.of(), List.of(), minimum, operation, attribute, grant
        )));
    }

    private static TreeDefinition definition(List<TreeNodeDefinition> nodes) {
        return new TreeDefinition(
                TREE,
                presentation("Training"),
                true,
                TreeScope.SKILL,
                Optional.of(SKILL),
                CURRENCY,
                TreeDependencyPolicy.CASCADE_REFUND,
                nodes
        );
    }

    private static TreeNodeDefinition node(
            ResourceLocation id,
            long cost,
            int row,
            int column,
            List<ResourceLocation> requires,
            List<ResourceLocation> requiresAny,
            int minimum,
            AttributeOperation operation,
            ResourceLocation attribute,
            ResourceLocation grant
    ) {
        return new TreeNodeDefinition(
                id,
                presentation("Node " + cost),
                cost,
                row,
                column,
                requires,
                requiresAny,
                Map.of(SKILL, minimum),
                List.of(new TreeAttributeGrant(grant, attribute, operation, cost * 1_000_000L))
        );
    }

    private static TreeCatalog catalog(TreeDefinition tree) {
        CurrencyDefinition currency = new CurrencyDefinition(
                CURRENCY, presentation("Points"), 0, 100, 0, "character"
        );
        SkillDefinition skill = new SkillDefinition(
                SKILL,
                presentation("Skill"),
                true,
                SkillCurve.flat(0, 10, CurveRounding.CEIL, BigDecimal.TEN),
                List.of(),
                List.of(),
                List.of()
        );
        var ir = CanonicalIr.of(List.of(
                SkillCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.CURRENCY, CURRENCY),
                        currency,
                        provenance("currencies/points.toml"),
                        SourceMap.empty()
                ),
                SkillCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.SKILL, SKILL),
                        skill,
                        provenance("skills/skill.toml"),
                        SourceMap.empty()
                ),
                TreeCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.TREE, TREE),
                        tree,
                        provenance("trees/training.toml"),
                        SourceMap.empty()
                )
        ), AliasMap.empty());
        SkillCatalog skills = SkillCatalog.from(ir);
        return TreeCatalog.from(ir, skills);
    }

    private static DefinitionPresentation presentation(String fallback) {
        ComponentSpec text = ComponentSpec.literal(fallback);
        return new DefinitionPresentation(
                text,
                Optional.empty(),
                IconSpec.single(IconKind.ITEM, id("minecraft:stone"), id("minecraft:barrier"), text),
                Set.of()
        );
    }

    private static Provenance provenance(String source) {
        return new Provenance(id("test:pack"), source, "toml");
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
