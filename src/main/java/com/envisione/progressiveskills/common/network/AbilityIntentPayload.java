package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record AbilityIntentPayload(
        Optional<ResourceLocation> abilityId,
        OptionalInt slot
) {
    public AbilityIntentPayload {
        abilityId = Objects.requireNonNull(abilityId, "abilityId").map(StableId::requireValid);
        slot = Objects.requireNonNull(slot, "slot");
        slot.ifPresent(value -> {
            if (value < 0 || value >= NetworkLimits.FIXED_ABILITY_SLOTS) {
                throw new IllegalArgumentException("Ability slot is outside the fixed slot range");
            }
        });
    }

    public static AbilityIntentPayload assign(ResourceLocation abilityId, int slot) {
        return new AbilityIntentPayload(Optional.of(abilityId), OptionalInt.of(slot));
    }

    public static AbilityIntentPayload unassign(int slot) {
        return new AbilityIntentPayload(Optional.empty(), OptionalInt.of(slot));
    }

    public static AbilityIntentPayload select(int slot) {
        return new AbilityIntentPayload(Optional.empty(), OptionalInt.of(slot));
    }

    public static AbilityIntentPayload toggle(ResourceLocation abilityId) {
        return new AbilityIntentPayload(Optional.of(abilityId), OptionalInt.empty());
    }

    public static AbilityIntentPayload activate(int slot) {
        return new AbilityIntentPayload(Optional.empty(), OptionalInt.of(slot));
    }

    public String encode(NetworkPayloads.IntentType intentType) {
        requireShape(intentType);
        return abilityId.map(Object::toString).orElse("") + "\n"
                + (slot.isPresent() ? Integer.toString(slot.getAsInt()) : "");
    }

    public static AbilityIntentPayload decode(NetworkPayloads.IntentType intentType, String encoded) {
        Objects.requireNonNull(intentType, "intentType");
        NetworkLimits.requireBoundedText(
                encoded, NetworkLimits.MAX_INTENT_BYTES, "ability intent payload");
        String[] fields = encoded.split("\n", -1);
        if (fields.length != 2) {
            throw new IllegalArgumentException("Ability intent payload field count is invalid");
        }
        Optional<ResourceLocation> abilityId = fields[0].isEmpty()
                ? Optional.empty() : Optional.of(StableId.parse(fields[0]));
        OptionalInt slot;
        try {
            slot = fields[1].isEmpty()
                    ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(fields[1]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Ability intent slot is invalid", exception);
        }
        var result = new AbilityIntentPayload(abilityId, slot);
        result.requireShape(intentType);
        if (!result.encode(intentType).equals(encoded)) {
            throw new IllegalArgumentException("Ability intent payload is not canonical");
        }
        return result;
    }

    private void requireShape(NetworkPayloads.IntentType intentType) {
        Objects.requireNonNull(intentType, "intentType");
        boolean valid = switch (intentType) {
            case ABILITY_ASSIGN -> abilityId.isPresent() && slot.isPresent();
            case ABILITY_UNASSIGN, ABILITY_SELECT, ABILITY_ACTIVATE ->
                    abilityId.isEmpty() && slot.isPresent();
            case ABILITY_TOGGLE -> abilityId.isPresent() && slot.isEmpty();
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("Ability intent payload does not match its intent type");
        }
    }
}
