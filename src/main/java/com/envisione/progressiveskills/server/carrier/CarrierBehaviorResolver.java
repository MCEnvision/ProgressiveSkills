package com.envisione.progressiveskills.server.carrier;

import com.envisione.progressiveskills.common.carrier.CarrierBehaviorSnapshot;
import com.envisione.progressiveskills.common.carrier.CarrierBindPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierIdentity;
import com.envisione.progressiveskills.common.carrier.CarrierKind;
import com.envisione.progressiveskills.common.carrier.CarrierMigrationPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierStackState;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CarrierBehaviorResolver {
    private CarrierBehaviorResolver() {
    }

    public static Resolution resolve(
            CarrierIdentity identity,
            CarrierStackState state,
            CarrierKind physicalKind,
            UUID playerId,
            BehaviorArchiveSavedData archive,
            Optional<CarrierBehaviorSnapshot> currentBehavior
    ) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(physicalKind, "physicalKind");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(archive, "archive");
        currentBehavior = Objects.requireNonNull(currentBehavior, "currentBehavior");
        if (!archive.status().reliable()) {
            return rejected(Code.ARCHIVE_UNAVAILABLE, "Carrier behavior archive is unavailable");
        }
        Optional<CarrierBehaviorSnapshot> stored = archive.resolve(identity.behaviorDigest());
        if (stored.isEmpty()) {
            return rejected(Code.ARCHIVED_BEHAVIOR_MISSING, "Carrier behavior is not present in the archive");
        }
        CarrierBehaviorSnapshot behavior = stored.orElseThrow();
        if (!behavior.definitionId().equals(identity.definitionId())) {
            return rejected(Code.IDENTITY_MISMATCH, "Carrier definition does not match its archived behavior");
        }
        if (behavior.carrier() != physicalKind) {
            return rejected(Code.KIND_MISMATCH, "Carrier item kind does not match its archived behavior");
        }
        if (state.behaviorVersion() != behavior.behaviorVersion()) {
            return rejected(Code.STATE_VERSION_MISMATCH, "Carrier state version does not match its archived behavior");
        }
        if (state.charges() < 1) {
            return rejected(Code.DEPLETED, "Carrier has no remaining charges");
        }
        if (state.charges() > behavior.charges()) {
            return rejected(Code.CHARGE_TAMPER, "Carrier charges exceed the archived behavior");
        }
        Resolution binding = binding(behavior.bindPolicy(), state, playerId);
        if (!binding.allowed()) {
            return binding;
        }
        Optional<CarrierBehaviorSnapshot> matchingCurrent = currentBehavior
                .filter(current -> current.definitionId().equals(identity.definitionId()));
        CarrierMigrationPolicy driftPolicy = matchingCurrent
                .map(CarrierBehaviorSnapshot::migrationPolicy)
                .orElse(behavior.migrationPolicy());
        if (driftPolicy == CarrierMigrationPolicy.ACCEPT_LIVE) {
            return rejected(Code.UNSAFE_LIVE_POLICY, "Carrier live behavior acceptance is disabled");
        }
        Optional<String> warning = Optional.empty();
        boolean currentMatches = matchingCurrent
                .map(CarrierBehaviorSnapshot::digest)
                .filter(identity.behaviorDigest()::equals)
                .isPresent();
        if (!currentMatches) {
            switch (driftPolicy) {
                case KEEP_PINNED -> {
                }
                case WARN -> warning = Optional.of("Carrier uses an archived behavior that differs from live content");
                case INVALIDATE -> {
                    return rejected(Code.INVALIDATED, "Carrier was invalidated by a live content change");
                }
                case MIGRATE -> {
                    return rejected(Code.MIGRATION_REQUIRED, "Carrier requires explicit migration before use");
                }
                case ACCEPT_LIVE -> throw new IllegalStateException("Unsafe live policy was not rejected");
            }
        }
        int requiredCharges;
        try {
            requiredCharges = behavior.useActions().stream()
                    .mapToInt(action -> action.consume())
                    .reduce(0, Math::addExact);
        } catch (ArithmeticException exception) {
            return rejected(Code.CHARGE_TAMPER, "Carrier action charge requirement overflowed");
        }
        if (requiredCharges > state.charges()) {
            return rejected(Code.INSUFFICIENT_CHARGES, "Carrier does not have enough charges for its actions");
        }
        CarrierStackState afterState;
        try {
            afterState = state.afterUse(state.charges() - requiredCharges);
            if (behavior.bindPolicy() == CarrierBindPolicy.ON_USE) {
                afterState = afterState.bind(playerId);
            }
        } catch (RuntimeException exception) {
            return rejected(Code.STATE_INVALID, "Carrier state could not advance safely");
        }
        return new Resolution(
                Code.ALLOWED,
                Optional.of(behavior),
                Optional.of(afterState),
                requiredCharges,
                warning,
                "Carrier use is allowed"
        );
    }

    private static Resolution binding(
            CarrierBindPolicy policy,
            CarrierStackState state,
            UUID playerId
    ) {
        if (state.boundOwner().isPresent() && !state.boundOwner().orElseThrow().equals(playerId)) {
            return rejected(Code.WRONG_OWNER, "Carrier is bound to another player");
        }
        if ((policy == CarrierBindPolicy.ON_PICKUP || policy == CarrierBindPolicy.ON_CRAFT)
                && state.boundOwner().isEmpty()) {
            return rejected(Code.MISSING_OWNER, "Carrier is missing its required owner binding");
        }
        if (policy == CarrierBindPolicy.NONE && state.boundOwner().isPresent()
                && state.migrationMarker().isEmpty()) {
            return rejected(Code.UNEXPECTED_OWNER, "Carrier has an unexpected owner binding");
        }
        return new Resolution(
                Code.ALLOWED,
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty(),
                "Carrier binding is valid"
        );
    }

    private static Resolution rejected(Code code, String message) {
        return new Resolution(
                code,
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty(),
                message
        );
    }

    public enum Code {
        ALLOWED,
        ARCHIVE_UNAVAILABLE,
        ARCHIVED_BEHAVIOR_MISSING,
        IDENTITY_MISMATCH,
        KIND_MISMATCH,
        STATE_VERSION_MISMATCH,
        STATE_INVALID,
        DEPLETED,
        CHARGE_TAMPER,
        INSUFFICIENT_CHARGES,
        WRONG_OWNER,
        MISSING_OWNER,
        UNEXPECTED_OWNER,
        UNSAFE_LIVE_POLICY,
        INVALIDATED,
        MIGRATION_REQUIRED
    }

    public record Resolution(
            Code code,
            Optional<CarrierBehaviorSnapshot> behavior,
            Optional<CarrierStackState> afterState,
            int requiredCharges,
            Optional<String> warning,
            String message
    ) {
        public Resolution {
            Objects.requireNonNull(code, "code");
            behavior = Objects.requireNonNull(behavior, "behavior");
            afterState = Objects.requireNonNull(afterState, "afterState");
            warning = Objects.requireNonNull(warning, "warning");
            message = Objects.requireNonNull(message, "message");
            if (requiredCharges < 0) {
                throw new IllegalArgumentException("Carrier required charges must not be negative");
            }
        }

        public boolean allowed() {
            return code == Code.ALLOWED;
        }
    }
}
