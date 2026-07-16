package com.envisione.progressiveskills;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectIdentityTest {
    @Test
    void identityMatchesTheLockedPlan() {
        assertEquals("progressiveskills", ProjectIdentity.MOD_ID);
        assertEquals("ProgressiveSkills", ProjectIdentity.DISPLAY_NAME);
        assertEquals("com.envisione.progressiveskills", ProjectIdentity.ROOT_PACKAGE);
        assertEquals(ProjectIdentity.ROOT_PACKAGE, ProjectIdentity.class.getPackageName());
    }

    @Test
    void generatedMetadataUsesTheLockedIdentity() throws IOException {
        try (var input = getClass().getResourceAsStream("/META-INF/neoforge.mods.toml")) {
            assertNotNull(input, "generated NeoForge metadata must be on the test runtime classpath");
            var metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(metadata.contains("modId = \"progressiveskills\""));
            assertTrue(metadata.contains("displayName = \"ProgressiveSkills\""));
            assertTrue(metadata.contains("versionRange = \"[21.1.236,21.2)\""));
            assertTrue(metadata.contains("versionRange = \"[1.21.1]\""));
            assertFalse(metadata.contains("unrealskills"));
        }
    }

    @Test
    void testsRunOnTheLockedJavaToolchain() {
        assertEquals(21, Runtime.version().feature());
    }
}
