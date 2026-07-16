package com.envisione.progressiveskills;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PropertyHarnessTest {
    @Property(tries = 250)
    void validGeneratedPathsRemainInTheProgressiveSkillsNamespace(
            @ForAll("validResourcePaths") String path
    ) {
        var id = ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, path);

        assertEquals(ProjectIdentity.MOD_ID, id.getNamespace());
        assertEquals(path, id.getPath());
    }

    @Provide
    Arbitrary<String> validResourcePaths() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('_', '-', '.', '/')
                .ofMinLength(1)
                .ofMaxLength(80);
    }
}
