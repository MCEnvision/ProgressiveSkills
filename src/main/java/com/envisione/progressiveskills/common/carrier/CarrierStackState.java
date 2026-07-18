package com.envisione.progressiveskills.common.carrier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record CarrierStackState(
        int dataVersion,
        int behaviorVersion,
        int charges,
        String creationPackDigest,
        UUID instanceId,
        long useCounter,
        Optional<UUID> boundOwner,
        Optional<String> migrationMarker
) {
    public static final int CURRENT_DATA_VERSION = 1;
    public static final int MAX_BEHAVIOR_VERSION = 1_000_000;
    public static final int MAX_CHARGES = 1_000_000;
    public static final int MAX_MIGRATION_MARKER_LENGTH = 128;
    private static final Pattern MARKER_PATTERN = Pattern.compile("[a-z0-9_./]{1,128}");
    private static final Codec<Integer> DATA_VERSION_CODEC = Codec.INT.validate(value ->
            value == CURRENT_DATA_VERSION
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Unsupported carrier stack data version"));
    private static final Codec<Integer> BEHAVIOR_VERSION_CODEC = Codec.INT.validate(value ->
            value >= 1 && value <= MAX_BEHAVIOR_VERSION
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Carrier behavior version is outside its bound"));
    private static final Codec<Integer> CHARGES_CODEC = Codec.INT.validate(value ->
            value >= 0 && value <= MAX_CHARGES
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Carrier charges are outside their bound"));
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(UUID.fromString(value));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Invalid bound owner UUID");
                }
            },
            UUID::toString
    );
    private static final Codec<Long> USE_COUNTER_CODEC = Codec.LONG.validate(value ->
            value >= 0
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Carrier use counter is outside its bound"));
    private static final Codec<String> MARKER_CODEC = Codec.STRING.validate(value ->
            value != null && MARKER_PATTERN.matcher(value).matches()
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Carrier migration marker is outside its bound"));
    public static final Codec<CarrierStackState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DATA_VERSION_CODEC.fieldOf("data_version").forGetter(CarrierStackState::dataVersion),
            BEHAVIOR_VERSION_CODEC.fieldOf("behavior_version").forGetter(CarrierStackState::behaviorVersion),
            CHARGES_CODEC.fieldOf("charges").forGetter(CarrierStackState::charges),
            CarrierIdentity.DIGEST_CODEC.fieldOf("creation_pack_digest")
                    .forGetter(CarrierStackState::creationPackDigest),
            UUID_CODEC.fieldOf("instance_id").forGetter(CarrierStackState::instanceId),
            USE_COUNTER_CODEC.fieldOf("use_counter").forGetter(CarrierStackState::useCounter),
            UUID_CODEC.optionalFieldOf("bound_owner").forGetter(CarrierStackState::boundOwner),
            MARKER_CODEC.optionalFieldOf("migration_marker").forGetter(CarrierStackState::migrationMarker)
    ).apply(instance, CarrierStackState::new));

    public CarrierStackState {
        Objects.requireNonNull(creationPackDigest, "creationPackDigest");
        Objects.requireNonNull(instanceId, "instanceId");
        boundOwner = Objects.requireNonNull(boundOwner, "boundOwner");
        migrationMarker = Objects.requireNonNull(migrationMarker, "migrationMarker");
        if (dataVersion != CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported carrier stack data version");
        }
        if (behaviorVersion < 1 || behaviorVersion > MAX_BEHAVIOR_VERSION) {
            throw new IllegalArgumentException("Carrier behavior version is outside its bound");
        }
        if (charges < 0 || charges > MAX_CHARGES) {
            throw new IllegalArgumentException("Carrier charges are outside their bound");
        }
        if (useCounter < 0) {
            throw new IllegalArgumentException("Carrier use counter is outside its bound");
        }
        CarrierIdentity.validateDigestForState(creationPackDigest);
        migrationMarker.ifPresent(value -> {
            if (!MARKER_PATTERN.matcher(value).matches()) {
                throw new IllegalArgumentException("Carrier migration marker is outside its bound");
            }
        });
    }

    public static CarrierStackState fresh(
            int behaviorVersion,
            int charges,
            String packDigest,
            UUID instanceId
    ) {
        return new CarrierStackState(
                CURRENT_DATA_VERSION,
                behaviorVersion,
                charges,
                packDigest,
                instanceId,
                0,
                Optional.empty(),
                Optional.empty()
        );
    }

    public CarrierStackState withCharges(int nextCharges) {
        return new CarrierStackState(
                dataVersion,
                behaviorVersion,
                nextCharges,
                creationPackDigest,
                instanceId,
                useCounter,
                boundOwner,
                migrationMarker
        );
    }

    public CarrierStackState bind(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        if (boundOwner.isPresent() && !boundOwner.orElseThrow().equals(owner)) {
            throw new IllegalStateException("Carrier is already bound to another player");
        }
        return new CarrierStackState(
                dataVersion,
                behaviorVersion,
                charges,
                creationPackDigest,
                instanceId,
                useCounter,
                Optional.of(owner),
                migrationMarker
        );
    }

    public CarrierStackState migrated(int nextBehaviorVersion, int nextCharges, String marker) {
        return new CarrierStackState(
                dataVersion,
                nextBehaviorVersion,
                nextCharges,
                creationPackDigest,
                instanceId,
                useCounter,
                boundOwner,
                Optional.of(marker)
        );
    }

    public CarrierStackState afterUse(int nextCharges) {
        return new CarrierStackState(
                dataVersion,
                behaviorVersion,
                nextCharges,
                creationPackDigest,
                instanceId,
                Math.addExact(useCounter, 1),
                boundOwner,
                migrationMarker
        );
    }
}
