package com.envisione.progressiveskills.server.carrier;

import com.envisione.progressiveskills.common.carrier.CarrierBehaviorSnapshot;
import com.envisione.progressiveskills.common.carrier.CarrierBindPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierCatalog;
import com.envisione.progressiveskills.common.carrier.CarrierDefinition;
import com.envisione.progressiveskills.common.carrier.CarrierDeliveryPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierIdentity;
import com.envisione.progressiveskills.common.carrier.CarrierStackState;
import com.envisione.progressiveskills.common.carrier.PendingCarrierClaim;
import com.envisione.progressiveskills.common.carrier.PendingClaimBatchResult;
import com.envisione.progressiveskills.common.carrier.PendingClaimTakeStatus;
import com.envisione.progressiveskills.common.carrier.PsCarrierComponents;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public final class CarrierDeliveryService {
    public static final int MAX_COMMAND_COUNT = 64;

    private CarrierDeliveryService() {
    }

    public static Map<ResourceLocation, CarrierDefinition> liveDefinitions() {
        var sorted = new TreeMap<ResourceLocation, CarrierDefinition>(ResourceLocation::compareNamespaced);
        PackRuntime.service().filter(service -> service.live().generation() > 0).ifPresent(service -> {
            var canonical = service.live().snapshot().canonicalIr();
            SkillCatalog skills = SkillCatalog.from(canonical);
            TreeCatalog trees = TreeCatalog.from(canonical, skills);
            CarrierCatalog.from(canonical, skills, trees).carriers().forEach((id, definition) -> {
                if (definition.enabled()) {
                    sorted.put(id, definition);
                }
            });
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    public static Optional<CarrierDefinition> liveDefinition(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(liveDefinitions().get(id));
    }

    public static AcquisitionResult acquire(ServerPlayer target, ResourceLocation definitionId, int count) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(definitionId, "definitionId");
        if (count < 1 || count > MAX_COMMAND_COUNT) {
            return AcquisitionResult.rejected(
                    AcquisitionStatus.REFUSED,
                    count,
                    "Carrier count must be between 1 and " + MAX_COMMAND_COUNT
            );
        }
        CarrierDefinition definition = liveDefinition(definitionId).orElse(null);
        if (definition == null) {
            return AcquisitionResult.rejected(
                    AcquisitionStatus.REFUSED,
                    count,
                    "Unknown live carrier " + definitionId
            );
        }
        CarrierBehaviorSnapshot behavior = definition.behaviorSnapshot();
        if (count > behavior.stackSize()) {
            return AcquisitionResult.rejected(
                    AcquisitionStatus.REFUSED,
                    count,
                    "Carrier count exceeds its stack size of " + behavior.stackSize()
            );
        }
        ProgressiveSkillsData data = target.getData(PsDataAttachments.PLAYER_DATA);
        if (!data.active()) {
            return AcquisitionResult.rejected(
                    AcquisitionStatus.REFUSED,
                    count,
                    "Player progression data is unavailable"
            );
        }
        String packDigest = PackRuntime.service()
                .filter(service -> service.live().generation() > 0)
                .map(service -> service.live().snapshot().contentDigest())
                .orElseThrow(() -> new IllegalStateException("Live content pack is unavailable"));
        Optional<UUID> owner = creationOwner(definition.bindPolicy(), target.getUUID());
        ItemStack stack = CarrierStackService.create(
                target.getServer(), behavior, packDigest, owner, count
        );
        if (insertExact(target, stack)) {
            return new AcquisitionResult(
                    true,
                    AcquisitionStatus.DELIVERED,
                    count,
                    count,
                    List.of(),
                    "Delivered " + count + " carrier items"
            );
        }
        return overflow(target, definition, behavior, stack, count);
    }

    public static ClaimDeliveryResult takeClaim(ServerPlayer player, UUID claimId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(claimId, "claimId");
        ProgressiveSkillsData data = player.getData(PsDataAttachments.PLAYER_DATA);
        if (!data.active()) {
            return new ClaimDeliveryResult(
                    false,
                    PendingClaimTakeStatus.DELIVERY_REJECTED,
                    "Player progression data is unavailable"
            );
        }
        var failure = new StringBuilder();
        PendingClaimTakeStatus status = data.takePendingClaim(claimId, claim -> {
            try {
                ItemStack stack = CarrierStackService.materialize(player, claim);
                if (!insertExact(player, stack)) {
                    failure.append("Inventory has no room for the complete claim");
                    return false;
                }
                return true;
            } catch (RuntimeException exception) {
                failure.append(safeMessage(exception));
                return false;
            }
        });
        return switch (status) {
            case DELIVERED -> new ClaimDeliveryResult(true, status, "Delivered pending claim " + claimId);
            case NOT_FOUND -> new ClaimDeliveryResult(false, status, "Unknown pending claim " + claimId);
            case DELIVERY_REJECTED -> new ClaimDeliveryResult(
                    false,
                    status,
                    failure.isEmpty() ? "Pending claim delivery was rejected" : failure.toString()
            );
        };
    }

    public static ClaimBatchDeliveryResult takeAllClaims(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        ProgressiveSkillsData data = player.getData(PsDataAttachments.PLAYER_DATA);
        if (!data.active()) {
            return new ClaimBatchDeliveryResult(
                    false, 0, data.pendingClaims().size(), "Player progression data is unavailable"
            );
        }
        if (data.pendingClaims().isEmpty()) {
            return new ClaimBatchDeliveryResult(false, 0, 0, "No pending claims are available");
        }
        var failure = new StringBuilder();
        PendingClaimBatchResult result = data.takeAllPendingClaims(claim -> {
            try {
                ItemStack stack = CarrierStackService.materialize(player, claim);
                if (!insertExact(player, stack)) {
                    failure.append("Inventory has no room for the next complete claim");
                    return false;
                }
                return true;
            } catch (RuntimeException exception) {
                failure.append(safeMessage(exception));
                return false;
            }
        });
        boolean complete = result.retained() == 0;
        String message = complete
                ? "Delivered all " + result.delivered() + " pending claims"
                : "Delivered " + result.delivered() + " pending claims and retained "
                + result.retained() + ". "
                + (failure.isEmpty() ? "The next claim was rejected" : failure);
        return new ClaimBatchDeliveryResult(complete, result.delivered(), result.retained(), message);
    }

    static OverflowRoute overflowRoute(CarrierDeliveryPolicy policy, boolean safeDrop) {
        Objects.requireNonNull(policy, "policy");
        return switch (policy) {
            case REFUSE_TRANSACTION -> OverflowRoute.REFUSE;
            case PENDING_CLAIM -> OverflowRoute.PENDING_CLAIM;
            case DROP_IF_SAFE -> safeDrop ? OverflowRoute.DROP : OverflowRoute.REFUSE;
            case PROVIDER_MAIL -> OverflowRoute.UNSUPPORTED;
        };
    }

    private static AcquisitionResult overflow(
            ServerPlayer target,
            CarrierDefinition definition,
            CarrierBehaviorSnapshot behavior,
            ItemStack stack,
            int count
    ) {
        boolean safeDrop = target.isAlive()
                && !target.isRemoved()
                && target.serverLevel().isLoaded(target.blockPosition());
        OverflowRoute route = overflowRoute(definition.deliveryPolicy(), safeDrop);
        if (route == OverflowRoute.PENDING_CLAIM) {
            CarrierIdentity identity = stack.get(PsCarrierComponents.IDENTITY.get());
            CarrierStackState state = stack.get(PsCarrierComponents.STATE.get());
            if (identity == null || state == null) {
                return AcquisitionResult.rejected(
                        AcquisitionStatus.REFUSED,
                        count,
                        "Carrier stack could not be materialized for a pending claim"
                );
            }
            String packDigest = PackRuntime.service()
                    .filter(service -> service.live().generation() > 0)
                    .map(service -> service.live().snapshot().contentDigest())
                    .orElseThrow(() -> new IllegalStateException("Live content pack is unavailable"));
            Optional<UUID> owner = creationOwner(definition.bindPolicy(), target.getUUID());
            List<PendingCarrierClaim> claims = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                ItemStack claimStack = index == 0
                        ? stack
                        : CarrierStackService.create(
                                target.getServer(), behavior, packDigest, owner, 1
                        );
                CarrierIdentity claimIdentity = claimStack.get(PsCarrierComponents.IDENTITY.get());
                CarrierStackState claimState = claimStack.get(PsCarrierComponents.STATE.get());
                if (claimIdentity == null || claimState == null) {
                    return AcquisitionResult.rejected(
                            AcquisitionStatus.REFUSED,
                            count,
                            "Carrier stack could not be materialized for a pending claim"
                    );
                }
                claims.add(new PendingCarrierClaim(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        claimIdentity,
                        behavior.carrier(),
                        claimState,
                        behavior,
                        Instant.now(),
                        "Inventory full during carrier delivery"
                ));
            }
            ProgressiveSkillsData data = target.getData(PsDataAttachments.PLAYER_DATA);
            Optional<String> rejection = data.pendingClaimsRejection(claims);
            if (rejection.isPresent()) {
                return AcquisitionResult.rejected(
                        AcquisitionStatus.REFUSED,
                        count,
                        rejection.orElseThrow()
                );
            }
            data.addPendingClaims(claims);
            List<UUID> claimIds = claims.stream().map(PendingCarrierClaim::claimId).toList();
            return new AcquisitionResult(
                    true,
                    AcquisitionStatus.PENDING_CLAIM,
                    count,
                    0,
                    claimIds,
                    "Inventory is full. Stored " + claimIds.size() + " pending claims"
            );
        }
        if (route == OverflowRoute.DROP) {
            ItemEntity entity = target.drop(stack.copy(), false);
            if (entity != null) {
                return new AcquisitionResult(
                        true,
                        AcquisitionStatus.DROPPED,
                        count,
                        count,
                        List.of(),
                        "Inventory is full. Dropped " + count + " carrier items at the player"
                );
            }
            return AcquisitionResult.rejected(
                    AcquisitionStatus.REFUSED,
                    count,
                    "Inventory is full and the carrier could not be dropped safely"
            );
        }
        if (route == OverflowRoute.UNSUPPORTED) {
            return AcquisitionResult.rejected(
                    AcquisitionStatus.UNSUPPORTED,
                    count,
                    "Inventory is full and provider mail is unavailable"
            );
        }
        return AcquisitionResult.rejected(
                AcquisitionStatus.REFUSED,
                count,
                "Inventory has no room for the complete carrier delivery"
        );
    }

    private static Optional<UUID> creationOwner(CarrierBindPolicy policy, UUID playerId) {
        return switch (policy) {
            case ON_PICKUP, ON_CRAFT -> Optional.of(playerId);
            case NONE, ON_USE -> Optional.empty();
        };
    }

    private static boolean insertExact(ServerPlayer player, ItemStack offered) {
        if (offered.isEmpty()) {
            return false;
        }
        var inventory = player.getInventory();
        List<ItemStack> before = new ArrayList<>(inventory.items.size());
        inventory.items.forEach(stack -> before.add(stack.copy()));
        ItemStack candidate = offered.copy();
        try {
            if (!inventory.add(candidate) || !candidate.isEmpty()) {
                restore(inventory.items, before);
                inventory.setChanged();
                return false;
            }
            inventory.setChanged();
            return true;
        } catch (RuntimeException exception) {
            restore(inventory.items, before);
            inventory.setChanged();
            throw exception;
        }
    }

    private static void restore(List<ItemStack> target, List<ItemStack> before) {
        if (target.size() != before.size()) {
            throw new IllegalStateException("Player inventory size changed during carrier delivery");
        }
        for (int index = 0; index < target.size(); index++) {
            target.set(index, before.get(index));
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public enum OverflowRoute {
        REFUSE,
        PENDING_CLAIM,
        DROP,
        UNSUPPORTED
    }

    public enum AcquisitionStatus {
        DELIVERED,
        PENDING_CLAIM,
        DROPPED,
        REFUSED,
        UNSUPPORTED
    }

    public record AcquisitionResult(
            boolean accepted,
            AcquisitionStatus status,
            int requested,
            int delivered,
            List<UUID> claimIds,
            String message
    ) {
        public AcquisitionResult {
            Objects.requireNonNull(status, "status");
            claimIds = List.copyOf(Objects.requireNonNull(claimIds, "claimIds"));
            message = Objects.requireNonNull(message, "message");
            if (requested < 1 || delivered < 0 || delivered > requested) {
                throw new IllegalArgumentException("Carrier delivery counts are invalid");
            }
            if (accepted && status == AcquisitionStatus.PENDING_CLAIM
                    && claimIds.size() != requested) {
                throw new IllegalArgumentException("Pending carrier delivery requires one claim per item");
            }
        }

        private static AcquisitionResult rejected(
                AcquisitionStatus status,
                int requested,
                String message
        ) {
            return new AcquisitionResult(
                    false, status, Math.max(1, requested), 0, List.of(), message
            );
        }
    }

    public record ClaimDeliveryResult(
            boolean accepted,
            PendingClaimTakeStatus status,
            String message
    ) {
        public ClaimDeliveryResult {
            Objects.requireNonNull(status, "status");
            message = Objects.requireNonNull(message, "message");
        }
    }

    public record ClaimBatchDeliveryResult(
            boolean accepted,
            int delivered,
            int remaining,
            String message
    ) {
        public ClaimBatchDeliveryResult {
            message = Objects.requireNonNull(message, "message");
            if (delivered < 0 || remaining < 0) {
                throw new IllegalArgumentException("Pending claim delivery counts must not be negative");
            }
        }
    }
}
