package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.ability.AbilityAction;
import com.envisione.progressiveskills.common.ability.AbilityCatalog;
import com.envisione.progressiveskills.common.ability.AbilityCanonicalCodec;
import com.envisione.progressiveskills.common.ability.AbilityCostType;
import com.envisione.progressiveskills.common.ability.AbilityKind;
import com.envisione.progressiveskills.common.ability.AbilityTargetMode;
import com.envisione.progressiveskills.common.classdef.ClassCatalog;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityTomlCompilerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilerPreservesActiveCostsTargetingTimingAndAuthoredActions() {
        var fields = activeFields();
        fields.put("costs", List.of(
                Map.of(
                        "id", "test:burst/points", "type", "currency",
                        "currency", "test:points", "amount", 5
                ),
                Map.of("id", "test:burst/hunger", "type", "hunger", "amount", 3),
                Map.of("id", "test:burst/xp", "type", "experience", "amount", 7)
        ));
        fields.put("targeting", Map.of("mode", "entity", "range", 24, "line_of_sight", true));
        fields.put("cooldown_group", "test:shared");
        fields.put("cooldown_ticks", 80);
        fields.put("max_charges", 2);
        fields.put("recharge_ticks", 200);
        fields.put("actions", List.of(
                Map.of(
                        "id", "test:burst/z_message", "type", "message",
                        "message", Map.of("fallback", "Burst")
                ),
                Map.of("id", "test:burst/a_heal", "type", "heal", "amount", 4.0),
                Map.of(
                        "id", "test:burst/m_speed", "type", "vanilla_effect",
                        "effect", "minecraft:speed", "duration_ticks", 100,
                        "amplifier", 1, "ambient", false, "show_particles", true, "show_icon", true
                )
        ));

        var definition = AbilityCanonicalCodec.decode(AbilityTomlCompiler.ability(
                new DefinitionKey(DefinitionKinds.ABILITY, id("test:burst")),
                fields,
                new Provenance(id("test:pack"), "abilities/burst.toml", "toml"),
                SourceMap.empty()
        ));

        assertEquals(AbilityKind.ACTIVE, definition.kind());
        assertEquals(AbilityTargetMode.ENTITY, definition.targeting().mode());
        assertEquals(id("test:shared"), definition.cooldownGroup());
        assertEquals(3, definition.costs().size());
        assertTrue(definition.costs().stream().anyMatch(cost -> cost.type() == AbilityCostType.EXPERIENCE));
        assertEquals(
                List.of(id("test:burst/z_message"), id("test:burst/a_heal"), id("test:burst/m_speed")),
                definition.actions().stream().map(AbilityAction::id).toList()
        );
    }

    @Test
    void compilerRejectsCreatorKindsFieldsAndActions() {
        var proc = activeFields();
        proc.put("kind", "proc");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> compile(proc))
                .getMessage().contains("Unknown Core ability kind proc"));

        var formula = activeFields();
        formula.put("formula", "level * 2");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> compile(formula))
                .getMessage().contains("Creator field formula"));

        var command = activeFields();
        command.put("actions", List.of(Map.of(
                "id", "test:burst/command", "type", "command", "message", "say unsafe"
        )));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> compile(command))
                .getMessage().contains("Unknown Core ability action type command"));

        var oversizedTarget = activeFields();
        oversizedTarget.put("targeting", Map.of("mode", "block", "range", 65, "line_of_sight", true));
        assertThrows(IllegalArgumentException.class, () -> compile(oversizedTarget));
    }

    @Test
    void installedStarterAbilitiesResolveEveryClassGrant() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        StarterPackInstaller.install(packs);

        StagingResult result = stage(packs);

        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        var ir = result.snapshot().orElseThrow().canonicalIr();
        SkillCatalog skills = SkillCatalog.from(ir);
        TreeCatalog trees = TreeCatalog.from(ir, skills);
        ClassCatalog classes = ClassCatalog.from(ir, skills, trees);
        AbilityCatalog abilities = AbilityCatalog.from(ir, skills, classes);
        assertEquals(3, abilities.abilities().size());
        assertEquals(AbilityKind.TOGGLE,
                abilities.ability(id("progressiveskills:warrior_guard")).orElseThrow().kind());
        assertEquals(AbilityKind.PASSIVE,
                abilities.ability(id("progressiveskills:combat_insight")).orElseThrow().kind());
        assertEquals(AbilityKind.ACTIVE,
                abilities.ability(id("progressiveskills:second_wind")).orElseThrow().kind());
        assertEquals(64, abilities.abilityLineageFingerprint(id("progressiveskills:second_wind")).length());
    }

    @Test
    void stagingRejectsMissingClassAbilityAndMissingNamedCurrency() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path pack = StarterPackInstaller.install(packs);
        Path warrior = pack.resolve("classes/warrior.toml");
        Path secondWind = pack.resolve("abilities/second_wind.toml");
        String warriorSource = Files.readString(warrior);
        String abilitySource = Files.readString(secondWind);

        Files.writeString(warrior, warriorSource.replace(
                "ability = \"progressiveskills:warrior_guard\"",
                "ability = \"progressiveskills:missing_guard\""
        ));
        StagingResult missingAbility = stage(packs);
        assertFalse(missingAbility.valid());
        assertTrue(missingAbility.diagnostics().diagnostics().stream().anyMatch(problem ->
                problem.message().contains("references missing ability progressiveskills:missing_guard")));

        Files.writeString(warrior, warriorSource);
        Files.writeString(secondWind, abilitySource.replace(
                "type = \"hunger\"",
                "type = \"currency\"\ncurrency = \"progressiveskills:missing\""
        ));
        StagingResult missingCurrency = stage(packs);
        assertFalse(missingCurrency.valid());
        assertTrue(missingCurrency.diagnostics().diagnostics().stream().anyMatch(problem ->
                problem.message().contains("references missing currency progressiveskills:missing")));
    }

    @Test
    void stagingRejectsMissingAttributeAndVanillaEffect() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path pack = StarterPackInstaller.install(packs);
        Path guard = pack.resolve("abilities/warrior_guard.toml");
        Path secondWind = pack.resolve("abilities/second_wind.toml");
        String guardSource = Files.readString(guard);
        String secondWindSource = Files.readString(secondWind);

        Files.writeString(guard, guardSource.replace(
                "minecraft:generic.armor",
                "progressiveskills:missing_attribute"
        ));
        StagingResult missingAttribute = stage(packs);
        assertFalse(missingAttribute.valid());
        assertTrue(missingAttribute.diagnostics().diagnostics().stream().anyMatch(problem ->
                problem.message().contains("references missing attribute progressiveskills:missing_attribute")));

        Files.writeString(guard, guardSource);
        Files.writeString(secondWind, secondWindSource.replace(
                "minecraft:speed",
                "progressiveskills:missing_effect"
        ));
        StagingResult missingEffect = stage(packs);
        assertFalse(missingEffect.valid());
        assertTrue(missingEffect.diagnostics().diagnostics().stream().anyMatch(problem ->
                problem.message().contains("references missing vanilla effect progressiveskills:missing_effect")));

        Files.writeString(secondWind, secondWindSource);
        Files.writeString(guard, guardSource.replace("value = 2.0", "value = 1000.000001"));
        StagingResult unsafeAttribute = stage(packs);
        assertFalse(unsafeAttribute.valid());
        assertTrue(unsafeAttribute.diagnostics().diagnostics().stream().anyMatch(problem ->
                problem.message().contains("attribute effect exceeds its projection safety bound")));
    }

    private static LinkedHashMap<String, Object> activeFields() {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("display", Map.of("fallback", "Burst"));
        fields.put("icon", Map.of(
                "type", "item", "value", "minecraft:blaze_powder",
                "fallback", "minecraft:barrier", "alt", "Blaze powder"
        ));
        fields.put("kind", "active");
        fields.put("actions", List.of(Map.of(
                "id", "test:burst/heal", "type", "heal", "amount", 1.0
        )));
        return fields;
    }

    private static void compile(Map<String, Object> fields) {
        AbilityTomlCompiler.ability(
                new DefinitionKey(DefinitionKinds.ABILITY, id("test:burst")),
                fields,
                new Provenance(id("test:pack"), "abilities/burst.toml", "toml"),
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
