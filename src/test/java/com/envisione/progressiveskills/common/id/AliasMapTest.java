package com.envisione.progressiveskills.common.id;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasMapTest {
    private static final DefinitionKind SKILL = DefinitionKind.of("progressiveskills:skill", "skills");
    private static final DefinitionKind TREE = DefinitionKind.of("progressiveskills:tree", "trees");

    @Test
    void manyOldIdsMayConvergeOnOneTerminalWithoutChaining() {
        DefinitionKey old = skill("mypack:old");
        DefinitionKey older = skill("mypack:older");
        DefinitionKey current = skill("mypack:current");
        AliasMap aliases = AliasMap.of(List.of(
                new Alias(older, current),
                new Alias(old, current),
                new Alias(old, current)
        ));

        AliasResolution resolution = aliases.resolve(old);
        assertEquals(current, aliases.resolveTerminal(old));
        assertEquals(List.of(old, current), resolution.path());
        assertEquals(1, resolution.hops());
        assertEquals(current, aliases.resolve(current).terminal());
        assertEquals(2, aliases.aliases().size(), "identical duplicate declarations are idempotent");
        assertThrows(UnsupportedOperationException.class, () -> aliases.mappings().put(current, old));
        assertThrows(UnsupportedOperationException.class, () -> resolution.path().add(old));
    }

    @Test
    void invalidAliasShapesProduceStableIssueCodesAndNoMap() {
        DefinitionKey source = skill("mypack:source");
        DefinitionKey targetOne = skill("mypack:target_one");
        DefinitionKey targetTwo = skill("mypack:target_two");
        DefinitionKey treeTarget = new DefinitionKey(TREE, targetOne.id());
        AliasValidationResult result = AliasMap.validate(List.of(
                new Alias(source, source),
                new Alias(source, treeTarget),
                new Alias(source, targetOne),
                new Alias(source, targetTwo)
        ));

        assertFalse(result.isValid());
        assertTrue(result.aliasMap().isEmpty());
        assertEquals(
                List.of(
                        AliasIssueCode.CROSS_KIND,
                        AliasIssueCode.SELF_ALIAS,
                        AliasIssueCode.CONFLICTING_TARGET
                ),
                result.issues().stream().map(AliasIssue::code).toList()
        );
        assertThrows(IllegalArgumentException.class, result::orThrow);
        assertThrows(IllegalArgumentException.class, () -> AliasMap.of(List.of(new Alias(source, treeTarget))));
    }

    @Test
    void cyclesAreRejectedOncePerCycle() {
        DefinitionKey first = skill("mypack:first");
        DefinitionKey second = skill("mypack:second");
        DefinitionKey third = skill("mypack:third");
        AliasValidationResult result = AliasMap.validate(List.of(
                new Alias(first, second),
                new Alias(second, third),
                new Alias(third, first)
        ));

        assertFalse(result.isValid());
        assertEquals(1L, result.issues().stream().filter(issue -> issue.code() == AliasIssueCode.CYCLE).count());
    }

    @Test
    void chainedReplacementAliasesAreRejectedEvenWhenAcyclic() {
        AliasValidationResult result = AliasMap.validate(chain(2));

        assertFalse(result.isValid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == AliasIssueCode.CHAINED_ALIAS));
    }

    @Test
    void overlongChainsReportTheResolutionBoundExplicitly() {
        AliasValidationResult result = AliasMap.validate(chain(AliasMap.MAX_RESOLUTION_HOPS + 1));

        assertFalse(result.isValid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == AliasIssueCode.CHAIN_TOO_LONG));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == AliasIssueCode.CHAINED_ALIAS));
    }

    @Test
    void declarationVolumeIsBoundedBeforeDuplicateNormalization() {
        Alias repeated = new Alias(skill("mypack:old"), skill("mypack:current"));
        AliasValidationResult result = AliasMap.validate(Collections.nCopies(
                AliasMap.MAX_ALIAS_DECLARATIONS + 1,
                repeated
        ));

        assertFalse(result.isValid());
        assertEquals(List.of(AliasIssueCode.TOO_MANY_ALIASES),
                result.issues().stream().map(AliasIssue::code).toList());
        assertTrue(result.issues().getFirst().relatedKeys().isEmpty());
    }

    @Test
    void issueOrderingDoesNotDependOnDeclarationOrder() {
        DefinitionKey source = skill("mypack:source");
        List<Alias> forward = List.of(
                new Alias(source, skill("mypack:target_two")),
                new Alias(source, skill("mypack:target_one")),
                new Alias(skill("mypack:self"), skill("mypack:self"))
        );
        List<Alias> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);

        assertEquals(AliasMap.validate(forward).issues(), AliasMap.validate(reverse).issues());
    }

    @Test
    void constructionTakesADeterministicDefensiveSnapshot() {
        List<Alias> proposals = new ArrayList<>();
        proposals.add(new Alias(skill("zeta:old"), skill("zeta:new")));
        proposals.add(new Alias(skill("alpha:old"), skill("alpha:new")));
        AliasMap aliases = AliasMap.of(proposals);
        proposals.clear();

        assertEquals(2, aliases.mappings().size());
        assertEquals(
                List.of("alpha:old", "zeta:old"),
                aliases.mappings().keySet().stream().map(key -> key.id().toString()).toList()
        );
    }

    private static List<Alias> chain(int hops) {
        List<Alias> aliases = new ArrayList<>();
        for (int index = 0; index < hops; index++) {
            aliases.add(new Alias(
                    skill("mypack:chain_" + index),
                    skill("mypack:chain_" + (index + 1))
            ));
        }
        return aliases;
    }

    private static DefinitionKey skill(String id) {
        return DefinitionKey.parse(SKILL, id);
    }
}
