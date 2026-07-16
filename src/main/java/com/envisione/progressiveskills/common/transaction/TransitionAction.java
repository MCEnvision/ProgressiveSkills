package com.envisione.progressiveskills.common.transaction;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Bounded typed action executed only on a transaction edge, never by recompute. */
public record TransitionAction(
        ResourceLocation type,
        GrantSourceId source,
        String payload,
        long amount,
        RepeatPolicy repeatPolicy,
        DeliveryContract deliveryContract,
        TransitionFailurePolicy failurePolicy
) {
    public static final int MAX_PAYLOAD_LENGTH = 512;

    public TransitionAction {
        type = StableId.requireValid(type);
        Objects.requireNonNull(source, "source");
        payload = Objects.requireNonNull(payload, "payload").strip();
        if (payload.isEmpty() || payload.length() > MAX_PAYLOAD_LENGTH || payload.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Transition payload must be bounded printable text");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("Transition action amount must be positive");
        }
        Objects.requireNonNull(repeatPolicy, "repeatPolicy");
        Objects.requireNonNull(deliveryContract, "deliveryContract");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        if (repeatPolicy == RepeatPolicy.ALWAYS && deliveryContract == DeliveryContract.EFFECTIVELY_ONCE) {
            throw new IllegalArgumentException("Effectively-once actions require a receipt policy");
        }
    }
}
