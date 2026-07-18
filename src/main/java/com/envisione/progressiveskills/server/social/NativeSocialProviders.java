package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.common.social.PartyProvider;
import com.envisione.progressiveskills.common.social.PartyReadiness;
import com.envisione.progressiveskills.common.social.SharedProgressionProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NativeSocialProviders {
    private NativeSocialProviders() {
    }

    public static PartyProvider parties(MinecraftServer server) {
        return new NativePartyProvider(MultiplayerSavedData.get(server));
    }

    public static SharedProgressionProvider shared(MinecraftServer server) {
        return new NativeSharedProvider(TeamSavedData.get(server));
    }

    private record NativePartyProvider(MultiplayerSavedData data) implements PartyProvider {
        @Override
        public String providerId() {
            return "progressiveskills.native_party";
        }

        @Override
        public boolean available() {
            return data.active();
        }

        @Override
        public Optional<PartySnapshot> party(UUID playerId) {
            return data.party(playerId).map(value -> new PartySnapshot(
                    value.id(), value.owner(), value.name(), value.members()));
        }

        @Override
        public Map<UUID, PartyReadiness> readiness(UUID requester) {
            return data.readiness(requester);
        }

        @Override
        public PartySnapshot create(UUID owner, String name) {
            return snapshot(data.createParty(owner, name));
        }

        @Override
        public void invite(UUID owner, UUID target) {
            data.invite(owner, target);
        }

        @Override
        public PartySnapshot accept(UUID target) {
            return snapshot(data.acceptInvite(target));
        }

        @Override
        public void leave(UUID player) {
            data.leave(player);
        }

        @Override
        public PartySnapshot kick(UUID owner, UUID target) {
            data.kick(owner, target);
            return snapshot(data.party(owner).orElseThrow());
        }

        @Override
        public PartySnapshot transferOwnership(UUID owner, UUID target) {
            data.transferOwnership(owner, target);
            return snapshot(data.party(target).orElseThrow());
        }

        @Override
        public void disband(UUID owner) {
            data.disband(owner);
        }

        @Override
        public void setReadiness(UUID player, PartyReadiness readiness) {
            data.setReadiness(player, readiness);
        }

        @Override
        public long progressSharedChallenge(
                UUID player,
                ResourceLocation challenge,
                long amount,
                long goal
        ) {
            return data.progressSharedChallenge(player, challenge, amount, goal);
        }

        private static PartySnapshot snapshot(MultiplayerSavedData.Party value) {
            return new PartySnapshot(value.id(), value.owner(), value.name(), value.members());
        }
    }

    private record NativeSharedProvider(TeamSavedData data) implements SharedProgressionProvider {
        @Override
        public String providerId() {
            return "progressiveskills.native_shared";
        }

        @Override
        public boolean available() {
            return data.active();
        }

        @Override
        public Optional<ResourceLocation> scopeId(UUID playerId) {
            return data.team(playerId).map(TeamSavedData.Team::id);
        }

        @Override
        public Optional<SharedSnapshot> snapshot(ResourceLocation scopeId) {
            return data.teamById(scopeId).map(NativeSharedProvider::snapshot);
        }

        @Override
        public SharedSnapshot create(UUID owner, ResourceLocation scopeId) {
            return snapshot(data.create(owner, scopeId));
        }

        @Override
        public void invite(UUID owner, UUID target) {
            data.invite(owner, target);
        }

        @Override
        public SharedSnapshot accept(UUID target) {
            return snapshot(data.accept(target));
        }

        @Override
        public void leave(UUID player) {
            data.leave(player);
        }

        @Override
        public SharedSnapshot remove(UUID owner, UUID target) {
            return snapshot(data.kick(owner, target));
        }

        @Override
        public SharedSnapshot transferOwnership(UUID owner, UUID target) {
            return snapshot(data.transferOwnership(owner, target));
        }

        @Override
        public void delete(UUID owner) {
            data.disband(owner);
        }

        @Override
        public SharedSnapshot contribute(
                UUID playerId,
                ResourceLocation balanceId,
                long amount,
                long expectedRevision
        ) {
            return snapshot(data.contribute(playerId, balanceId, amount, expectedRevision));
        }

        @Override
        public long contributeCommunity(ResourceLocation goalId, long amount, long maximum) {
            return data.community(goalId, amount, maximum);
        }

        private static SharedSnapshot snapshot(TeamSavedData.Team value) {
            return new SharedSnapshot(value.id(), value.owner(), value.revision(), Map.of(
                    TeamSavedData.TEAM_XP, value.xp()), value.contributions());
        }
    }
}
