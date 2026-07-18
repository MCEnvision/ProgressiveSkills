package com.envisione.progressiveskills.common.carrier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.regex.Pattern;

public record CarrierIdentity(ResourceLocation definitionId, String behaviorDigest) {
    public static final int DIGEST_LENGTH = 64;
    private static final Pattern DIGEST_PATTERN = Pattern.compile("[0-9a-f]{64}");
    public static final Codec<String> DIGEST_CODEC = Codec.STRING.validate(CarrierIdentity::validateDigest);
    public static final Codec<CarrierIdentity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition_id").forGetter(CarrierIdentity::definitionId),
            DIGEST_CODEC.fieldOf("behavior_digest").forGetter(CarrierIdentity::behaviorDigest)
    ).apply(instance, CarrierIdentity::new));

    public CarrierIdentity {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(behaviorDigest, "behaviorDigest");
        validateDigestForState(behaviorDigest);
    }

    static void validateDigestForState(String value) {
        if (value == null || !DIGEST_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Carrier digest must be lowercase SHA 256");
        }
    }

    private static DataResult<String> validateDigest(String value) {
        return value != null && DIGEST_PATTERN.matcher(value).matches()
                ? DataResult.success(value)
                : DataResult.error(() -> "Carrier digest must be lowercase SHA 256");
    }
}
