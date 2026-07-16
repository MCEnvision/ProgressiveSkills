package com.envisione.progressiveskills.common.id;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StableIdPropertyTest {
    @Property(tries = 250)
    void strictIdsRoundTripWithoutMinecraftNamespaceDefaults(
            @ForAll("namespaces") String namespace,
            @ForAll("segments") String segment
    ) {
        String encoded = namespace + ":" + segment;

        assertEquals(encoded, StableId.parse(encoded).toString());
    }

    @Property(tries = 250)
    void canonicalSkillPathsDerivePredictably(
            @ForAll("namespaces") String namespace,
            @ForAll("segments") String segment
    ) {
        DefinitionKind kind = DefinitionKind.of("progressiveskills:skill", "skills");

        assertEquals(
                namespace + ":" + segment,
                StableId.derive(kind, namespace, "skills/" + segment + ".toml").toString()
        );
    }

    @Provide
    Arbitrary<String> namespaces() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('_', '-', '.')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(value -> !value.equals(".") && !value.equals(".."));
    }

    @Provide
    Arbitrary<String> segments() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('_', '-', '.')
                .ofMinLength(1)
                .ofMaxLength(StableId.MAX_PATH_SEGMENT_LENGTH)
                .filter(value -> !value.equals(".") && !value.equals(".."));
    }
}
