package com.envisione.progressiveskills.common.creator;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildShareCodeTest {
    private static final String DIGEST = "1".repeat(64);

    @Test
    void roundTripsCanonicalBuild() {
        var build = new BuildShareCode.Build(
                DIGEST,
                List.of(id("zeta"), id("alpha")),
                List.of(id("tree/two"), id("tree/one")),
                Map.of(7, id("ability/two"), 0, id("ability/one"))
        );

        assertEquals(build, BuildShareCode.decode(BuildShareCode.encode(build)));
    }

    @Test
    void rejectsTamperedAndNoncanonicalEnvelope() {
        String encoded = BuildShareCode.encode(new BuildShareCode.Build(
                DIGEST, List.of(), List.of(), Map.of()));
        String tampered = encoded.substring(0, 5) + "f" + encoded.substring(6);

        assertThrows(IllegalArgumentException.class, () -> BuildShareCode.decode(tampered));
        assertThrows(IllegalArgumentException.class, () -> BuildShareCode.decode(encoded + "="));
    }

    @Test
    void rejectsInvalidAndDuplicateBuildInputs() {
        assertThrows(IllegalArgumentException.class, () -> new BuildShareCode.Build(
                "unavailable", List.of(), List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BuildShareCode.Build(
                DIGEST, List.of(id("same"), id("same")), List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BuildShareCode.Build(
                DIGEST, List.of(), List.of(), Map.of(8, id("ability"))));
        assertThrows(IllegalArgumentException.class, () -> new BuildShareCode.Build(
                DIGEST, List.of(), List.of(), Map.of(0, id("ability"), 1, id("ability"))));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
