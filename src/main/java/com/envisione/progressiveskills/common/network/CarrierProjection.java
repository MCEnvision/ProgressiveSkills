package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.carrier.CarrierIdentity;
import com.envisione.progressiveskills.common.carrier.CarrierKind;
import com.envisione.progressiveskills.common.carrier.PendingCarrierClaim;
import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record CarrierProjection(
        Optional<HeldCarrier> held,
        List<PendingClaimSummary> pendingClaims
) {
    public static final CarrierProjection EMPTY = new CarrierProjection(Optional.empty(), List.of());
    private static final Pattern STATUS_PATTERN = Pattern.compile("[a-z0-9_]{1,64}");

    public CarrierProjection {
        held = Objects.requireNonNull(held, "held");
        pendingClaims = new ArrayList<>(Objects.requireNonNull(pendingClaims, "pendingClaims"));
        if (pendingClaims.size() > NetworkLimits.MAX_PENDING_CLAIM_SUMMARIES) {
            throw new IllegalArgumentException("Pending claim projection exceeds capacity");
        }
        pendingClaims.sort(PendingClaimSummary.ORDER);
        var ids = new HashSet<UUID>();
        for (PendingClaimSummary claim : pendingClaims) {
            Objects.requireNonNull(claim, "pending claim summary");
            if (!ids.add(claim.claimId())) {
                throw new IllegalArgumentException("Pending claim projection contains a duplicate id");
            }
        }
        pendingClaims = List.copyOf(pendingClaims);
    }

    public static PendingClaimSummary summarize(PendingCarrierClaim claim) {
        Objects.requireNonNull(claim, "claim");
        return new PendingClaimSummary(
                claim.claimId(), claim.identity().definitionId(), claim.kind(),
                claim.state().behaviorVersion(), claim.state().charges(),
                claim.createdAt().toEpochMilli(), claim.reason()
        );
    }

    public record HeldCarrier(
            ResourceLocation definitionId,
            CarrierKind kind,
            String behaviorDigest,
            int behaviorVersion,
            int charges,
            boolean bound,
            boolean boundToPlayer,
            boolean migrated,
            String status,
            String message
    ) {
        public HeldCarrier {
            definitionId = StableId.requireValid(definitionId);
            Objects.requireNonNull(kind, "kind");
            behaviorDigest = NetworkLimits.requireDigest(behaviorDigest, "carrier behavior digest");
            if (behaviorVersion < 1 || behaviorVersion > NetworkLimits.MAX_CARRIER_BEHAVIOR_VERSION
                    || charges < 0 || charges > NetworkLimits.MAX_CARRIER_CHARGES) {
                throw new IllegalArgumentException("Held carrier version or charges exceed capacity");
            }
            if (boundToPlayer && !bound) {
                throw new IllegalArgumentException("Held carrier cannot match an owner without being bound");
            }
            if (status == null || !STATUS_PATTERN.matcher(status).matches()) {
                throw new IllegalArgumentException("Held carrier status exceeds its safe contract");
            }
            message = NetworkLimits.requireBoundedText(
                    message, NetworkLimits.MAX_RESYNC_REASON_BYTES, "held carrier message");
        }
    }

    public record PendingClaimSummary(
            UUID claimId,
            ResourceLocation definitionId,
            CarrierKind kind,
            int behaviorVersion,
            int charges,
            long createdAtEpochMillis,
            String reason
    ) {
        public static final java.util.Comparator<PendingClaimSummary> ORDER = java.util.Comparator
                .comparingLong(PendingClaimSummary::createdAtEpochMillis)
                .thenComparing(PendingClaimSummary::claimId);

        public PendingClaimSummary {
            Objects.requireNonNull(claimId, "claimId");
            definitionId = StableId.requireValid(definitionId);
            Objects.requireNonNull(kind, "kind");
            if (behaviorVersion < 1 || behaviorVersion > NetworkLimits.MAX_CARRIER_BEHAVIOR_VERSION
                    || charges < 0 || charges > NetworkLimits.MAX_CARRIER_CHARGES
                    || createdAtEpochMillis < 0) {
                throw new IllegalArgumentException("Pending claim summary exceeds its numeric bounds");
            }
            reason = NetworkLimits.requireBoundedText(
                    reason, NetworkLimits.MAX_CLAIM_REASON_BYTES, "pending claim reason").strip();
            if (reason.isEmpty() || reason.getBytes(StandardCharsets.UTF_8).length
                    > NetworkLimits.MAX_CLAIM_REASON_BYTES) {
                throw new IllegalArgumentException("Pending claim reason is empty");
            }
        }
    }
}
