package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.common.id.Alias;
import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionStateReconciliationTest {
    private static final String OLD_LINEAGE = "a".repeat(64);
    private static final String NEW_LINEAGE = "b".repeat(64);
    private static final DefinitionKey OLD = new DefinitionKey(DefinitionKinds.SKILL, id("old_skill"));
    private static final DefinitionKey RENAMED = new DefinitionKey(DefinitionKinds.SKILL, id("renamed_skill"));

    @Test
    void explicitReplacementCarriesStateAndMissingDefinitionQuarantinesThenRestores() {
        ProgressiveSkillsData data = ProgressiveSkillsData.empty(UUID.randomUUID());
        var payload = new CompoundTag();
        payload.putLong("xp", 42);
        data.putDefinitionState(StoredDefinitionState.create(OLD, OLD_LINEAGE, 1, payload));

        var alias = AliasMap.of(Set.of(new Alias(OLD, RENAMED)));
        var renamed = data.reconcileDefinitions(
                Set.of(RENAMED),
                Map.of(RENAMED, NEW_LINEAGE),
                alias
        );
        assertEquals(1, renamed.renamed());
        assertEquals(42, data.view().definitionStates().get(RENAMED).payload().getLong("xp"));
        assertEquals(OLD_LINEAGE, data.view().definitionStates().get(RENAMED).originLineage());

        var missing = data.reconcileDefinitions(Set.of(), Map.of(), AliasMap.empty());
        assertEquals(1, missing.orphaned());
        assertTrue(data.view().definitionStates().isEmpty());
        assertEquals(1, data.view().orphans().size());

        var restored = data.reconcileDefinitions(
                Set.of(RENAMED),
                Map.of(RENAMED, NEW_LINEAGE),
                AliasMap.empty()
        );
        assertEquals(1, restored.restored());
        assertEquals(42, data.view().definitionStates().get(RENAMED).payload().getLong("xp"));
        assertTrue(data.view().orphans().isEmpty());
    }

    @Test
    void reusingAnIdWithAnotherLineageRemainsQuarantined() {
        ProgressiveSkillsData data = ProgressiveSkillsData.empty(UUID.randomUUID());
        data.putDefinitionState(StoredDefinitionState.create(OLD, OLD_LINEAGE, 1, new CompoundTag()));

        data.reconcileDefinitions(Set.of(OLD), Map.of(OLD, NEW_LINEAGE), AliasMap.empty());

        assertTrue(data.view().definitionStates().isEmpty());
        assertEquals(1, data.view().orphans().size());
        assertTrue(data.view().orphans().get(OLD).reason().contains("incompatible lineage"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
