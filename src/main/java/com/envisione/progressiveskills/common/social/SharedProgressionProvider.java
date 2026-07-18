package com.envisione.progressiveskills.common.social;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SharedProgressionProvider {
    ResourceLocation DEFAULT_XP_BALANCE = ResourceLocation.fromNamespaceAndPath(
            "progressiveskills", "team_xp");

    String providerId();

    boolean available();

    Optional<ResourceLocation> scopeId(UUID playerId);

    Optional<SharedSnapshot> snapshot(ResourceLocation scopeId);

    SharedSnapshot create(UUID owner, ResourceLocation scopeId);

    void invite(UUID owner, UUID target);

    SharedSnapshot accept(UUID target);

    void leave(UUID player);

    SharedSnapshot remove(UUID owner, UUID target);

    SharedSnapshot transferOwnership(UUID owner, UUID target);

    void delete(UUID owner);

    SharedSnapshot contribute(
            UUID playerId,
            ResourceLocation balanceId,
            long amount,
            long expectedRevision
    );

    long contributeCommunity(ResourceLocation goalId, long amount, long maximum);

    record SharedSnapshot(
            ResourceLocation scopeId,
            UUID owner,
            long revision,
            Map<ResourceLocation, Long> balances,
            Map<UUID, Long> contributions
    ) {
        public SharedSnapshot {
            scopeId = com.envisione.progressiveskills.common.id.StableId.requireValid(scopeId);
            java.util.Objects.requireNonNull(owner, "owner");
            if (revision < 0) {
                throw new IllegalArgumentException("Shared progression revision is invalid");
            }
            balances = Map.copyOf(balances);
            contributions = Map.copyOf(contributions);
        }
    }
}
