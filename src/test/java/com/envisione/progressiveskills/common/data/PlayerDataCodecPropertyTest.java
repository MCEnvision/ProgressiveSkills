package com.envisione.progressiveskills.common.data;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataCodecPropertyTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000005ff");

    @Property(tries = 1000)
    void boundedMalformedRawTagsAlwaysDecodeToSafeSerializableState(
            @ForAll("versions") int version,
            @ForAll("boundedText") String playerId,
            @ForAll("boundedText") String unknown
    ) {
        var raw = new CompoundTag();
        raw.putInt("data_version", version);
        raw.putString("player_id", playerId);
        raw.putString("unknown_property_value", unknown);

        ProgressiveSkillsData decoded = ProgressiveSkillsDataSerializer.decode(PLAYER, raw);
        CompoundTag encoded = ProgressiveSkillsDataSerializer.encode(decoded);

        assertTrue(NbtDataLimits.rejection(encoded).isEmpty());
        assertTrue(decoded.active() || decoded.view().quarantine().isPresent());
    }

    @Provide
    Arbitrary<Integer> versions() {
        return Arbitraries.integers().between(-4, 8);
    }

    @Provide
    Arbitrary<String> boundedText() {
        return Arbitraries.strings().ascii().ofMaxLength(96);
    }
}
