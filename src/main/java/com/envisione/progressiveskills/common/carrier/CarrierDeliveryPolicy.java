package com.envisione.progressiveskills.common.carrier;

import java.util.Arrays;

public enum CarrierDeliveryPolicy {
    REFUSE_TRANSACTION("refuse_transaction"),
    PENDING_CLAIM("pending_claim"),
    DROP_IF_SAFE("drop_if_safe"),
    PROVIDER_MAIL("provider_mail");

    private final String serializedName;

    CarrierDeliveryPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CarrierDeliveryPolicy parse(String value) {
        if (value == null || value.length() > 32) {
            throw new IllegalArgumentException("Carrier delivery policy is missing or exceeds its bound");
        }
        return Arrays.stream(values())
                .filter(policy -> policy.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown carrier delivery policy " + value));
    }
}
