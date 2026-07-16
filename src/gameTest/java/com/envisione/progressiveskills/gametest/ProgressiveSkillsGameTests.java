package com.envisione.progressiveskills.gametest;

import com.envisione.progressiveskills.ProjectIdentity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Minimal executable proof that the mod and GameTest harness load together. */
@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProgressiveSkillsGameTests {
    private ProgressiveSkillsGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void harnessLoads(GameTestHelper helper) {
        helper.assertTrue(
                ModList.get().isLoaded(ProjectIdentity.MOD_ID),
                "ProgressiveSkills must be loaded"
        );
        helper.succeed();
    }
}
