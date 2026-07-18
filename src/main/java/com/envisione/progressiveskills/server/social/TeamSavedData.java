package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class TeamSavedData extends SavedData {
    public static final String FILE_ID = "progressiveskills_teams";
    public static final ResourceLocation TEAM_XP =
            com.envisione.progressiveskills.common.social.SharedProgressionProvider.DEFAULT_XP_BALANCE;
    public static final int DATA_VERSION = 2;
    public static final int MAX_TEAMS = 4096;
    public static final int MAX_MEMBERS = 128;
    public static final int MAX_PLAYERS = 16_384;
    public static final int MAX_INVITES = 16_384;
    public static final int MAX_COMMUNITY_GOALS = 4096;
    private final Map<ResourceLocation, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, ResourceLocation> membership = new LinkedHashMap<>();
    private final Map<UUID, Invite> invites = new LinkedHashMap<>();
    private final Map<ResourceLocation, Long> communityGoals = new LinkedHashMap<>();
    private boolean quarantined;
    private String quarantineReason = "";

    public static SavedData.Factory<TeamSavedData> factory() {
        return new SavedData.Factory<>(TeamSavedData::new, TeamSavedData::load);
    }

    public static TeamSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public synchronized Team create(UUID owner, ResourceLocation id) {
        requireActive();
        id = StableId.requireValid(id);
        if (teams.size() >= MAX_TEAMS || teams.containsKey(id) || membership.containsKey(owner)
                || membership.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Team creation is unavailable");
        }
        Team team = new Team(id, owner, Set.of(owner), Map.of(owner, 0L), 0L, 0L, 0);
        teams.put(id, team);
        membership.put(owner, id);
        setDirty();
        return team;
    }

    public synchronized Invite invite(UUID owner, UUID target) {
        requireActive();
        if (!invites.containsKey(target) && invites.size() >= MAX_INVITES) {
            throw new IllegalStateException("Team invite capacity is full");
        }
        Team team = owned(owner);
        if (membership.containsKey(target)) {
            throw new IllegalStateException("Player already belongs to a team");
        }
        Invite invite = new Invite(UUID.randomUUID(), team.id(), owner, target, Instant.now().plusSeconds(300));
        invites.put(target, invite);
        setDirty();
        return invite;
    }

    public synchronized Team accept(UUID target) {
        requireActive();
        if (membership.containsKey(target)) {
            throw new IllegalStateException("Player already belongs to a team");
        }
        Invite invite = invites.get(target);
        if (invite == null) {
            throw new IllegalStateException("Team invitation is unavailable");
        }
        if (Instant.now().isAfter(invite.expiresAt())) {
            invites.remove(target);
            setDirty();
            throw new IllegalStateException("Team invitation expired");
        }
        Team team = required(invite.team());
        if (team.members().size() >= MAX_MEMBERS) {
            throw new IllegalStateException("Team is full");
        }
        if (membership.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Team player capacity is full");
        }
        invites.remove(target);
        Team updated = team.withMember(target);
        teams.put(team.id(), updated);
        membership.put(target, team.id());
        setDirty();
        return updated;
    }

    public synchronized void leave(UUID player) {
        requireActive();
        Team team = team(player).orElseThrow(() -> new IllegalStateException("Player is not in a team"));
        if (team.owner().equals(player)) {
            throw new IllegalStateException("Team owner cannot leave before transferring ownership");
        }
        teams.put(team.id(), team.withoutMember(player));
        membership.remove(player);
        setDirty();
    }

    public synchronized Team transferOwnership(UUID owner, UUID target) {
        requireActive();
        Team team = owned(owner);
        if (!team.members().contains(target)) {
            throw new IllegalArgumentException("Team owner target must be a member");
        }
        Team updated = team.withOwner(target);
        teams.put(team.id(), updated);
        setDirty();
        return updated;
    }

    public synchronized Team kick(UUID owner, UUID target) {
        requireActive();
        Team team = owned(owner);
        if (owner.equals(target) || !team.members().contains(target)) {
            throw new IllegalArgumentException("Team kick target is invalid");
        }
        Team updated = team.withoutMember(target);
        teams.put(team.id(), updated);
        membership.remove(target);
        setDirty();
        return updated;
    }

    public synchronized void disband(UUID owner) {
        requireActive();
        Team team = owned(owner);
        teams.remove(team.id());
        team.members().forEach(membership::remove);
        invites.values().removeIf(invite -> invite.team().equals(team.id()));
        setDirty();
    }

    public synchronized Team contribute(UUID player, long amount) {
        Team team = team(player).orElseThrow(() -> new IllegalStateException("Player is not in a team"));
        return contribute(player, TEAM_XP, amount, team.revision());
    }

    public synchronized Team contribute(
            UUID player,
            ResourceLocation balance,
            long amount,
            long expectedRevision
    ) {
        requireActive();
        if (!TEAM_XP.equals(StableId.requireValid(balance)) || amount < 0 || expectedRevision < 0) {
            throw new IllegalArgumentException("Team contribution must not be negative");
        }
        Team team = team(player).orElseThrow(() -> new IllegalStateException("Player is not in a team"));
        if (team.revision() != expectedRevision) {
            throw new IllegalStateException("Shared progression revision changed");
        }
        Team updated = team.withContribution(player, amount);
        teams.put(team.id(), updated);
        setDirty();
        return updated;
    }

    public synchronized long community(ResourceLocation goal, long amount, long maximum) {
        requireActive();
        goal = StableId.requireValid(goal);
        if (amount < 0 || maximum < 1) {
            throw new IllegalArgumentException("Community goal progress is invalid");
        }
        if (!communityGoals.containsKey(goal) && communityGoals.size() >= MAX_COMMUNITY_GOALS) {
            throw new IllegalStateException("Community goal capacity is full");
        }
        long next = Math.min(maximum, Math.addExact(communityGoals.getOrDefault(goal, 0L), amount));
        communityGoals.put(goal, next);
        setDirty();
        return next;
    }

    public synchronized Optional<Team> team(UUID player) {
        requireActive();
        ResourceLocation id = membership.get(player);
        return id == null ? Optional.empty() : Optional.of(required(id));
    }

    public synchronized Optional<Team> teamById(ResourceLocation id) {
        requireActive();
        return Optional.ofNullable(teams.get(StableId.requireValid(id)));
    }

    public synchronized Map<ResourceLocation, Long> communityGoals() {
        requireActive();
        return Collections.unmodifiableMap(new TreeMap<>(communityGoals));
    }

    public synchronized boolean active() {
        return !quarantined;
    }

    public synchronized Optional<String> quarantineReason() {
        return quarantined ? Optional.of(quarantineReason) : Optional.empty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag output, HolderLookup.Provider provider) {
        requireActive();
        output.putInt("data_version", DATA_VERSION);
        var teamList = new ListTag();
        teams.values().forEach(team -> {
            var tag = new CompoundTag();
            tag.putString("id", team.id().toString());
            tag.putUUID("owner", team.owner());
            tag.putLong("revision", team.revision());
            tag.putLong("xp", team.xp());
            tag.putInt("level", team.level());
            var members = new ListTag();
            team.members().forEach(member -> {
                var memberTag = new CompoundTag();
                memberTag.putUUID("id", member);
                memberTag.putLong("contribution", team.contributions().getOrDefault(member, 0L));
                members.add(memberTag);
            });
            tag.put("members", members);
            teamList.add(tag);
        });
        output.put("teams", teamList);
        var inviteList = new ListTag();
        invites.values().forEach(invite -> {
            var tag = new CompoundTag();
            tag.putUUID("id", invite.id());
            tag.putString("team", invite.team().toString());
            tag.putUUID("sender", invite.sender());
            tag.putUUID("target", invite.target());
            tag.putLong("expires", invite.expiresAt().toEpochMilli());
            inviteList.add(tag);
        });
        output.put("invites", inviteList);
        var goals = new ListTag();
        communityGoals.forEach((id, progress) -> {
            var tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putLong("progress", progress);
            goals.add(tag);
        });
        output.put("community", goals);
        return output;
    }

    static TeamSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        var data = new TeamSavedData();
        try {
            int version = input.contains("data_version", Tag.TAG_INT) ? input.getInt("data_version") : 0;
            if (version < 0 || version > DATA_VERSION) {
                throw new IllegalArgumentException("Unsupported team data version " + version);
            }
            ListTag teams = input.getList("teams", Tag.TAG_COMPOUND);
            bounded(teams, MAX_TEAMS, "team count");
            teams.forEach(value -> {
                CompoundTag tag = (CompoundTag) value;
                var members = new LinkedHashSet<UUID>();
                var contributions = new LinkedHashMap<UUID, Long>();
                ListTag memberList = tag.getList("members", Tag.TAG_COMPOUND);
                bounded(memberList, MAX_MEMBERS, "team member count");
                memberList.forEach(memberValue -> {
                    CompoundTag member = (CompoundTag) memberValue;
                    UUID id = member.getUUID("id");
                    long contribution = member.getLong("contribution");
                    if (contribution < 0 || !members.add(id)
                            || contributions.putIfAbsent(id, contribution) != null) {
                        throw new IllegalArgumentException("Team member entry is invalid");
                    }
                });
                long revision = version >= 2 ? tag.getLong("revision") : 0L;
                Team team = new Team(StableId.parse(tag.getString("id")), tag.getUUID("owner"),
                        members, contributions, revision, tag.getLong("xp"), tag.getInt("level"));
                putUnique(data.teams, team.id(), team, "team");
                team.members().forEach(member -> {
                    if (data.membership.size() >= MAX_PLAYERS) {
                        throw new IllegalArgumentException("Team player count exceeds capacity");
                    }
                    putUnique(data.membership, member, team.id(), "team membership");
                });
            });
            ListTag invites = input.getList("invites", Tag.TAG_COMPOUND);
            bounded(invites, MAX_INVITES, "team invite count");
            invites.forEach(value -> {
                CompoundTag tag = (CompoundTag) value;
                Invite invite = new Invite(tag.getUUID("id"), StableId.parse(tag.getString("team")),
                        tag.getUUID("sender"), tag.getUUID("target"),
                        Instant.ofEpochMilli(tag.getLong("expires")));
                Team team = data.required(invite.team());
                if (!team.members().contains(invite.sender()) || data.membership.containsKey(invite.target())) {
                    throw new IllegalArgumentException("Team invite references invalid membership");
                }
                putUnique(data.invites, invite.target(), invite, "team invite");
            });
            ListTag community = input.getList("community", Tag.TAG_COMPOUND);
            bounded(community, MAX_COMMUNITY_GOALS, "community goal count");
            community.forEach(value -> {
                CompoundTag tag = (CompoundTag) value;
                long progress = tag.getLong("progress");
                if (progress < 0) {
                    throw new IllegalArgumentException("Community goal progress is invalid");
                }
                putUnique(data.communityGoals, StableId.parse(tag.getString("id")), progress,
                        "community goal");
            });
        } catch (RuntimeException exception) {
            data.teams.clear();
            data.membership.clear();
            data.invites.clear();
            data.communityGoals.clear();
            data.quarantined = true;
            data.quarantineReason = safeMessage(exception);
        }
        return data;
    }

    private void requireActive() {
        if (quarantined) {
            throw new IllegalStateException("Team data is quarantined. " + quarantineReason);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static void bounded(ListTag list, int maximum, String name) {
        if (list.size() > maximum) {
            throw new IllegalArgumentException(name + " exceeds capacity");
        }
    }

    private static <K, V> void putUnique(Map<K, V> target, K key, V value, String name) {
        if (target.putIfAbsent(Objects.requireNonNull(key, name + " key"),
                Objects.requireNonNull(value, name + " value")) != null) {
            throw new IllegalArgumentException("Team data contains a duplicate " + name);
        }
    }

    private Team owned(UUID owner) {
        Team team = team(owner).orElseThrow(() -> new IllegalStateException("Player is not in a team"));
        if (!team.owner().equals(owner)) {
            throw new IllegalStateException("Only the team owner can do that");
        }
        return team;
    }

    private Team required(ResourceLocation id) {
        Team team = teams.get(id);
        if (team == null) {
            throw new IllegalArgumentException("Unknown team " + id);
        }
        return team;
    }

    public record Team(
            ResourceLocation id,
            UUID owner,
            Set<UUID> members,
            Map<UUID, Long> contributions,
            long revision,
            long xp,
            int level
    ) {
        public Team {
            id = StableId.requireValid(id);
            Objects.requireNonNull(owner, "owner");
            members = Collections.unmodifiableSet(new LinkedHashSet<>(members));
            contributions = Collections.unmodifiableMap(new TreeMap<>(contributions));
            if (members.isEmpty() || members.size() > MAX_MEMBERS || !members.contains(owner)
                    || !contributions.keySet().equals(members)
                    || contributions.values().stream().anyMatch(value -> value < 0)
                    || revision < 0 || xp < 0 || level < 0) {
                throw new IllegalArgumentException("Team state is invalid");
            }
        }

        Team withMember(UUID player) {
            var nextMembers = new LinkedHashSet<>(members);
            nextMembers.add(player);
            var nextContributions = new LinkedHashMap<>(contributions);
            nextContributions.put(player, 0L);
            return new Team(id, owner, nextMembers, nextContributions,
                    Math.addExact(revision, 1L), xp, level);
        }

        Team withoutMember(UUID player) {
            var nextMembers = new LinkedHashSet<>(members);
            nextMembers.remove(player);
            var nextContributions = new LinkedHashMap<>(contributions);
            nextContributions.remove(player);
            return new Team(id, owner, nextMembers, nextContributions,
                    Math.addExact(revision, 1L), xp, level);
        }

        Team withOwner(UUID nextOwner) {
            return new Team(id, nextOwner, members, contributions,
                    Math.addExact(revision, 1L), xp, level);
        }

        Team withContribution(UUID player, long amount) {
            var next = new LinkedHashMap<>(contributions);
            next.put(player, Math.addExact(next.getOrDefault(player, 0L), amount));
            long nextXp = Math.addExact(xp, amount);
            int nextLevel = Math.toIntExact(Math.min(Integer.MAX_VALUE, nextXp / 1000L));
            return new Team(id, owner, members, next,
                    Math.addExact(revision, 1L), nextXp, nextLevel);
        }
    }

    public record Invite(UUID id, ResourceLocation team, UUID sender, UUID target, Instant expiresAt) {
        public Invite {
            Objects.requireNonNull(id, "id");
            team = StableId.requireValid(team);
            Objects.requireNonNull(sender, "sender");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (sender.equals(target)) {
                throw new IllegalArgumentException("Team invite is invalid");
            }
        }
    }
}
