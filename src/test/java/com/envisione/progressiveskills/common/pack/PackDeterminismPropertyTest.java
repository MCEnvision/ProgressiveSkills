package com.envisione.progressiveskills.common.pack;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackDeterminismPropertyTest {
    @Property(tries = 250)
    void sourceDigestIgnoresMapInsertionOrderAndSemverRoundTrips(
            @ForAll int first,
            @ForAll int second,
            @ForAll @IntRange(min = 0, max = 100_000) int major,
            @ForAll @IntRange(min = 0, max = 100_000) int minor,
            @ForAll @IntRange(min = 0, max = 100_000) int patch
    ) {
        var forward = new LinkedHashMap<String, byte[]>();
        forward.put("global_config/test/pack/first.toml", ByteBuffer.allocate(4).putInt(first).array());
        forward.put("global_config/test/pack/second.toml", ByteBuffer.allocate(4).putInt(second).array());
        var reverse = new LinkedHashMap<String, byte[]>();
        reverse.put("global_config/test/pack/second.toml", ByteBuffer.allocate(4).putInt(second).array());
        reverse.put("global_config/test/pack/first.toml", ByteBuffer.allocate(4).putInt(first).array());

        SourceBundle firstBundle = SourceBundle.of(forward);
        SourceBundle secondBundle = SourceBundle.of(reverse);
        assertEquals(firstBundle, secondBundle);
        assertEquals(firstBundle.digest(), secondBundle.digest());

        String version = major + "." + minor + "." + patch;
        assertEquals(version, SemanticVersion.parse(version).toString());
    }
}
