package com.envisione.progressiveskills.gametest;

import com.envisione.progressiveskills.ProjectIdentity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/** Minimal executable proof that the mod and GameTest harness load together. */
@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProgressiveSkillsGameTests {
    private ProgressiveSkillsGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void harnessLoads(GameTestHelper helper) {
        helper.assertTrue(
                ModList.get().isLoaded(ProjectIdentity.MOD_ID),
                "ProgressiveSkills must be loaded"
        );
        try {
            var server = helper.getLevel().getServer();
            var source = server.createCommandSourceStack().withPermission(4);
            int status = server.getCommands().getDispatcher().execute("ps status", source);
            int validate = server.getCommands().getDispatcher().execute("ps validate", source);
            int dryRun = server.getCommands().getDispatcher().execute("ps reload --dry-run", source);
            int diff = server.getCommands().getDispatcher().execute("ps diff", source);
            int info = server.getCommands().getDispatcher().execute(
                    "ps info progressiveskills:component_spec progressiveskills:engine_name --provenance",
                    source
            );
            helper.assertTrue(status == 1, "/ps status must report a live Phase 3 generation");
            helper.assertTrue(validate == 1, "/ps validate must accept the generated Core starter pack");
            helper.assertTrue(dryRun == 1, "/ps reload --dry-run must stage without mutating live content");
            helper.assertTrue(diff == 1, "/ps diff must report the reviewed staged snapshot");
            helper.assertTrue(info == 1, "/ps info must inspect the starter definition and provenance");
            helper.succeed();
        } catch (CommandSyntaxException exception) {
            helper.fail("Phase 3 command execution failed: " + exception.getMessage());
        }
    }
}
