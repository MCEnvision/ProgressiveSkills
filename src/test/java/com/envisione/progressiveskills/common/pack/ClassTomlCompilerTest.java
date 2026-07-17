package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.classdef.ClassCatalog;
import com.envisione.progressiveskills.common.classdef.ClassCanonicalCodec;
import com.envisione.progressiveskills.common.classdef.ClassGrantType;
import com.envisione.progressiveskills.common.classdef.ClassSpellGrant;
import com.envisione.progressiveskills.common.classdef.ClassSpellLearningPolicy;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.server.pack.StarterPackInstaller;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassTomlCompilerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilerPreservesCoreCostsPrerequisitesGrantsAndSpellPolicy() {
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("display", Map.of("fallback", "Mage"));
        fields.put("icon", Map.of(
                "type", "item", "value", "minecraft:book",
                "fallback", "minecraft:barrier", "alt", "Book"
        ));
        fields.put("enabled", true);
        fields.put("access_required", true);
        fields.put("slot", "test:combat");
        fields.put("slot_cost", 0);
        fields.put("exclusive_tags", List.of("test:arcane"));
        fields.put("prerequisites", Map.of(
                "min_level", Map.of("test:arcana", 3),
                "nodes", List.of("test:tree/root"),
                "classes", List.of("test:student")
        ));
        fields.put("selection_cost", Map.of("currency", "test:points", "amount", 5));
        fields.put("respec_allowed", true);
        fields.put("respec_cost", Map.of("currency", "test:points", "amount", 2));
        fields.put("starter_kit", List.of("minecraft:book"));
        fields.put("grants", List.of(
                Map.of(
                        "id", "test:mage/scale", "type", "attribute",
                        "attribute", "minecraft:generic.scale", "operation", "add_value", "value", -0.1
                ),
                Map.of(
                        "id", "test:mage/fireball", "type", "spell", "spell", "test:fireball",
                        "level", 3, "selection", "virtual_source", "learning", "satisfy_while_owned"
                ),
                Map.of(
                        "id", "test:mage/tree", "type", "tree_access", "tree", "test:tree"
                )
        ));
        DefinitionKey key = new DefinitionKey(DefinitionKinds.CLASS, id("test:mage"));

        var canonical = ClassTomlCompiler.classDefinition(
                key,
                fields,
                new Provenance(id("test:pack"), "classes/mage.toml", "toml"),
                SourceMap.empty()
        );
        var definition = ClassCanonicalCodec.decodeClass(canonical);

        assertEquals(0, definition.slotCost());
        assertTrue(definition.accessRequired());
        assertEquals(3, definition.grants().size());
        assertEquals(id("test:mage/starter_kit"), definition.starterKit().orElseThrow().receiptId());
        ClassSpellGrant spell = assertInstanceOf(ClassSpellGrant.class, definition.grants().stream()
                .filter(grant -> grant.type() == ClassGrantType.SPELL).findFirst().orElseThrow());
        assertEquals(ClassSpellLearningPolicy.SATISFY_WHILE_OWNED, spell.learningPolicy());
    }

    @Test
    void compilerDefaultsSpellLearningSafelyAndRejectsAdvancedOrIrreversibleFields() {
        Map<String, Object> spell = Map.of(
                "id", "test:mage/fireball", "type", "spell", "spell", "test:fireball"
        );
        var fields = minimalClassFields();
        fields.put("grants", List.of(spell));
        var definition = ClassCanonicalCodec.decodeClass(ClassTomlCompiler.classDefinition(
                new DefinitionKey(DefinitionKinds.CLASS, id("test:mage")),
                fields,
                new Provenance(id("test:pack"), "classes/mage.toml", "toml"),
                SourceMap.empty()
        ));
        assertEquals(
                ClassSpellLearningPolicy.REQUIRE_EXISTING,
                assertInstanceOf(ClassSpellGrant.class, definition.grants().getFirst()).learningPolicy()
        );

        var irreversible = minimalClassFields();
        irreversible.put("grants", List.of(Map.of(
                "id", "test:mage/fireball", "type", "spell", "spell", "test:fireball",
                "learning", "permanently_learn"
        )));
        var irreversibleFailure = assertThrows(IllegalArgumentException.class, () -> compile(irreversible));
        assertTrue(irreversibleFailure.getMessage().contains("permanently_learn"));

        var ranked = minimalClassFields();
        ranked.put("max_rank", 20);
        var rankedFailure = assertThrows(IllegalArgumentException.class, () -> compile(ranked));
        assertTrue(rankedFailure.getMessage().contains("advanced or ranked field max_rank"));
    }

    @Test
    void installedStarterClassesCompileWithCapacitySynergyAndStableLineage() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        StarterPackInstaller.install(packs);
        StagingResult result = stage(packs);

        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        var ir = result.snapshot().orElseThrow().canonicalIr();
        SkillCatalog skills = SkillCatalog.from(ir);
        TreeCatalog trees = TreeCatalog.from(ir, skills);
        ClassCatalog classes = ClassCatalog.from(ir, skills, trees);

        assertEquals(1, classes.slots().size());
        assertEquals(2, classes.classes().size());
        assertEquals(1, classes.synergies().size());
        assertEquals(2, classes.slot(id("progressiveskills:combat")).orElseThrow().capacity());
        assertFalse(classes.conflicts(id("progressiveskills:warrior"), id("progressiveskills:scholar")));
        assertEquals(1, classes.activeSynergies(Set.of(
                id("progressiveskills:warrior"), id("progressiveskills:scholar")
        )).size());
        assertEquals(64, classes.classLineageFingerprint(id("progressiveskills:warrior")).length());
        assertEquals(
                id("progressiveskills:warrior/starter_kit"),
                classes.classDefinition(id("progressiveskills:warrior")).orElseThrow()
                        .starterKit().orElseThrow().receiptId()
        );
    }

    @Test
    void stagingRejectsCyclesMissingAccessTargetsAndCapacityViolations() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path pack = StarterPackInstaller.install(packs);
        Path slot = pack.resolve("class_slots/combat.toml");
        Path warrior = pack.resolve("classes/warrior.toml");
        Path scholar = pack.resolve("classes/scholar.toml");
        String slotSource = Files.readString(slot);
        String warriorSource = Files.readString(warrior);
        String scholarSource = Files.readString(scholar);

        Files.writeString(slot, slotSource.replace("capacity = 2", "capacity = 65"));
        assertFalse(stage(packs).valid());

        Files.writeString(slot, slotSource);
        Files.writeString(warrior, warriorSource.replace(
                "nodes = [], classes = []", "nodes = [], classes = [\"progressiveskills:scholar\"]"
        ));
        Files.writeString(scholar, scholarSource.replace(
                "nodes = [], classes = []", "nodes = [], classes = [\"progressiveskills:warrior\"]"
        ));
        var cycle = stage(packs);
        assertFalse(cycle.valid());
        assertTrue(cycle.diagnostics().diagnostics().stream().anyMatch(problem ->
                problem.message().contains("Class prerequisites contain a cycle")));

        Files.writeString(warrior, warriorSource);
        Files.writeString(scholar, scholarSource.replace(
                "tree = \"progressiveskills:physique_training\"",
                "tree = \"progressiveskills:missing\""
        ));
        var missingTree = stage(packs);
        assertFalse(missingTree.valid());
        assertTrue(missingTree.diagnostics().diagnostics().stream().anyMatch(problem ->
                problem.message().contains("references missing tree")));

        Files.writeString(scholar, scholarSource + """

                [[grants]]
                id = "progressiveskills:scholar/missing_class"
                type = "class_access"
                class = "progressiveskills:missing"
                """);
        var missingClass = stage(packs);
        assertFalse(missingClass.valid());
        assertTrue(missingClass.diagnostics().diagnostics().stream().anyMatch(problem ->
                problem.message().contains("references missing class")));
    }

    @Test
    void catalogRejectsMoreGrantsThanAnAtomicReconciliationCanApply() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path pack = StarterPackInstaller.install(packs);
        for (int classIndex = 0; classIndex < 6; classIndex++) {
            var source = new StringBuilder("""
                    schema_version = 2

                    [class]
                    display = { fallback = "Bound class" }
                    icon = { type = "item", value = "minecraft:stone", fallback = "minecraft:barrier", alt = "Stone" }
                    slot = "progressiveskills:combat"
                    slot_cost = 0
                    prerequisites = { min_level = {}, nodes = [], classes = [] }
                    """);
            for (int grantIndex = 0; grantIndex < 30; grantIndex++) {
                source.append("\n[[grants]]\n")
                        .append("id = \"progressiveskills:bound_").append(classIndex).append("/grant_")
                        .append(grantIndex).append("\"\n")
                        .append("type = \"attribute\"\n")
                        .append("attribute = \"minecraft:generic.armor\"\n")
                        .append("operation = \"add_value\"\n")
                        .append("value = 0.01\n");
            }
            Files.writeString(pack.resolve("classes/bound_" + classIndex + ".toml"), source);
        }

        StagingResult result = stage(packs);

        assertFalse(result.valid());
        assertTrue(result.diagnostics().diagnostics().stream().anyMatch(problem ->
                problem.message().contains("Class and synergy grant count exceeds " + ClassCatalog.MAX_TOTAL_GRANTS)));
    }

    private static java.util.LinkedHashMap<String, Object> minimalClassFields() {
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("display", Map.of("fallback", "Mage"));
        fields.put("icon", Map.of(
                "type", "item", "value", "minecraft:book",
                "fallback", "minecraft:barrier", "alt", "Book"
        ));
        fields.put("slot", "test:combat");
        fields.put("slot_cost", 1);
        fields.put("prerequisites", Map.of("min_level", Map.of(), "nodes", List.of(), "classes", List.of()));
        return fields;
    }

    private static void compile(Map<String, Object> fields) {
        ClassTomlCompiler.classDefinition(
                new DefinitionKey(DefinitionKinds.CLASS, id("test:mage")),
                fields,
                new Provenance(id("test:pack"), "classes/mage.toml", "toml"),
                SourceMap.empty()
        );
    }

    private static StagingResult stage(Path packs) {
        return new ContentPackLoader().stage(
                List.of(new PackRoot(PackRootTier.GLOBAL_CONFIG, "test", packs)),
                AvailableEnvironment.empty()
        );
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
