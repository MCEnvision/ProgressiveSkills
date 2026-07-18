package com.envisione.progressiveskills.common.social;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PartyProvider {
    String providerId();

    boolean available();

    Optional<PartySnapshot> party(UUID playerId);

    Map<UUID, PartyReadiness> readiness(UUID requester);

    PartySnapshot create(UUID owner, String name);

    void invite(UUID owner, UUID target);

    PartySnapshot accept(UUID target);

    void leave(UUID player);

    PartySnapshot kick(UUID owner, UUID target);

    PartySnapshot transferOwnership(UUID owner, UUID target);

    void disband(UUID owner);

    void setReadiness(UUID player, PartyReadiness readiness);

    long progressSharedChallenge(UUID player, net.minecraft.resources.ResourceLocation challenge, long amount, long goal);

    record PartySnapshot(UUID stableId, UUID owner, String name, Set<UUID> members) {
        public PartySnapshot {
            java.util.Objects.requireNonNull(stableId, "stableId");
            java.util.Objects.requireNonNull(owner, "owner");
            name = java.util.Objects.requireNonNull(name, "name");
            members = Set.copyOf(members);
            if (!members.contains(owner)) {
                throw new IllegalArgumentException("Party provider snapshot has no owner member");
            }
        }
    }
}
