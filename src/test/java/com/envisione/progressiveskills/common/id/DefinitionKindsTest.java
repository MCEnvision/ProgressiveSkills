package com.envisione.progressiveskills.common.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionKindsTest {
    @Test
    void builtInKindsAreUniqueOrderedAndResolvable() {
        var kinds = DefinitionKinds.all();

        assertEquals(kinds.stream().sorted().toList(), kinds);
        assertEquals(kinds.size(), kinds.stream().map(DefinitionKind::id).distinct().count());
        assertTrue(kinds.size() >= 30);
        assertEquals(DefinitionKinds.SKILL, DefinitionKinds.require(DefinitionKinds.SKILL.id()));
        assertEquals("variables", DefinitionKinds.VARIABLE.sourceDirectory());
        assertTrue(kinds.contains(DefinitionKinds.VARIABLE));
    }
}
