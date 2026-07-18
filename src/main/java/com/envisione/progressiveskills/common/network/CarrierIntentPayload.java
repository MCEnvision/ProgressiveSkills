package com.envisione.progressiveskills.common.network;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CarrierIntentPayload(
        Optional<UUID> claimId,
        Optional<String> previewDigest
) {
    public CarrierIntentPayload {
        claimId = Objects.requireNonNull(claimId, "claimId");
        previewDigest = Objects.requireNonNull(previewDigest, "previewDigest")
                .map(value -> NetworkLimits.requireDigest(value, "carrier preview digest"));
    }

    public static CarrierIntentPayload takeClaim(UUID claimId) {
        return new CarrierIntentPayload(Optional.of(claimId), Optional.empty());
    }

    public static CarrierIntentPayload takeAllClaims() {
        return new CarrierIntentPayload(Optional.empty(), Optional.empty());
    }

    public static CarrierIntentPayload inspect() {
        return new CarrierIntentPayload(Optional.empty(), Optional.empty());
    }

    public static CarrierIntentPayload migratePreview() {
        return new CarrierIntentPayload(Optional.empty(), Optional.empty());
    }

    public static CarrierIntentPayload migrateConfirm(String previewDigest) {
        return new CarrierIntentPayload(Optional.empty(), Optional.of(previewDigest));
    }

    public String encode(NetworkPayloads.IntentType intentType) {
        requireShape(intentType);
        return switch (intentType) {
            case CLAIM_TAKE -> claimId.orElseThrow().toString();
            case CARRIER_MIGRATE_CONFIRM -> previewDigest.orElseThrow();
            case CLAIM_TAKE_ALL, CARRIER_INSPECT, CARRIER_MIGRATE_PREVIEW -> "";
            default -> throw new IllegalArgumentException("Intent is not a carrier intent");
        };
    }

    public static CarrierIntentPayload decode(
            NetworkPayloads.IntentType intentType,
            String encoded
    ) {
        Objects.requireNonNull(intentType, "intentType");
        NetworkLimits.requireBoundedText(
                encoded, NetworkLimits.MAX_INTENT_BYTES, "carrier intent payload");
        CarrierIntentPayload result;
        try {
            result = switch (intentType) {
                case CLAIM_TAKE -> new CarrierIntentPayload(
                        Optional.of(UUID.fromString(encoded)), Optional.empty());
                case CARRIER_MIGRATE_CONFIRM -> new CarrierIntentPayload(
                        Optional.empty(), Optional.of(encoded));
                case CLAIM_TAKE_ALL, CARRIER_INSPECT, CARRIER_MIGRATE_PREVIEW ->
                        new CarrierIntentPayload(Optional.empty(), Optional.empty());
                default -> throw new IllegalArgumentException("Intent is not a carrier intent");
            };
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Carrier intent payload is invalid", exception);
        }
        result.requireShape(intentType);
        if (!result.encode(intentType).equals(encoded)) {
            throw new IllegalArgumentException("Carrier intent payload is not canonical");
        }
        return result;
    }

    private void requireShape(NetworkPayloads.IntentType intentType) {
        Objects.requireNonNull(intentType, "intentType");
        boolean valid = switch (intentType) {
            case CLAIM_TAKE -> claimId.isPresent() && previewDigest.isEmpty();
            case CARRIER_MIGRATE_CONFIRM -> claimId.isEmpty() && previewDigest.isPresent();
            case CLAIM_TAKE_ALL, CARRIER_INSPECT, CARRIER_MIGRATE_PREVIEW ->
                    claimId.isEmpty() && previewDigest.isEmpty();
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("Carrier intent payload does not match its intent type");
        }
    }
}
