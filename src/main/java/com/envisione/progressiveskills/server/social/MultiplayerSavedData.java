package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.common.social.ContributionReceipt;
import com.envisione.progressiveskills.common.social.PartyReadiness;
import com.envisione.progressiveskills.common.social.PrivacySettings;
import com.envisione.progressiveskills.common.social.SharedAwardAllocator;
import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class MultiplayerSavedData extends SavedData {
    public static final String FILE_ID = "progressiveskills_multiplayer";
    public static final int DATA_VERSION = 3;
    public static final int MAX_PARTY_MEMBERS = 16;
    public static final int MAX_PARTIES = 4096;
    public static final int MAX_RECEIPTS = 4096;
    public static final int MAX_PLAYERS = 16_384;
    public static final int MAX_OFFERS = 16_384;
    public static final int MAX_BOARDS = 4096;
    public static final int MAX_BOARD_SCORES = 16_384;
    public static final int MAX_TOTAL_BOARD_SCORES = 65_536;
    public static final int MAX_RESOURCE_VALUES = 256;
    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    private final Map<UUID, UUID> membership = new LinkedHashMap<>();
    private final Map<UUID, Invite> invites = new LinkedHashMap<>();
    private final Map<UUID, PrivacySettings> privacy = new LinkedHashMap<>();
    private final Map<UUID, PartyReadiness> readiness = new LinkedHashMap<>();
    private final Map<UUID, ContributionReceipt> receipts = new LinkedHashMap<>();
    private final Map<UUID, UUID> pendingContributions = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> contributionDeliveries = new LinkedHashMap<>();
    private final Map<UUID, UUID> mentors = new LinkedHashMap<>();
    private final Map<UUID, MentorOffer> mentorOffers = new LinkedHashMap<>();
    private final Map<UUID, TransferOffer> transferOffers = new LinkedHashMap<>();
    private final Map<ResourceLocation, Map<UUID, Long>> leaderboards = new LinkedHashMap<>();
    private final Map<ResourceLocation, Map<UUID, Long>> previousLeaderboards = new LinkedHashMap<>();
    private ResourceLocation season = ResourceLocation.fromNamespaceAndPath("progressiveskills", "unseasoned");
    private long seasonEpoch;
    private boolean quarantined;
    private String quarantineReason = "";

    public static SavedData.Factory<MultiplayerSavedData> factory() {
        return new SavedData.Factory<>(MultiplayerSavedData::new, MultiplayerSavedData::load);
    }

    public static MultiplayerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public synchronized Party createParty(UUID owner, String name) {
        requireActive();
        requireNoParty(owner);
        if (membership.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Party player capacity is full");
        }
        if (parties.size() >= MAX_PARTIES) {
            throw new IllegalStateException("Party capacity is full");
        }
        Party party = new Party(UUID.randomUUID(), owner, boundedName(name), Set.of(owner), Map.of(), Map.of());
        parties.put(party.id(), party);
        membership.put(owner, party.id());
        setDirty();
        return party;
    }

    public synchronized Invite invite(UUID owner, UUID target) {
        requireActive();
        if (!invites.containsKey(target) && invites.size() >= MAX_OFFERS) {
            throw new IllegalStateException("Party invite capacity is full");
        }
        Party party = ownedParty(owner);
        requireNoParty(target);
        Invite invite = new Invite(UUID.randomUUID(), party.id(), owner, target,
                Instant.now().plusSeconds(300));
        invites.put(target, invite);
        setDirty();
        return invite;
    }

    public synchronized Party acceptInvite(UUID target) {
        requireActive();
        requireNoParty(target);
        Invite invite = Objects.requireNonNull(invites.get(target), "invite");
        if (Instant.now().isAfter(invite.expiresAt())) {
            invites.remove(target);
            setDirty();
            throw new IllegalStateException("Party invitation expired");
        }
        Party party = requiredParty(invite.partyId());
        requireNoPendingContribution(party);
        if (party.members().size() >= MAX_PARTY_MEMBERS) {
            throw new IllegalStateException("Party is full");
        }
        if (membership.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Party player capacity is full");
        }
        invites.remove(target);
        party = party.withMember(target);
        parties.put(party.id(), party);
        membership.put(target, party.id());
        setDirty();
        return party;
    }

    public synchronized void leave(UUID player) {
        requireActive();
        Party party = party(player).orElseThrow(() -> new IllegalStateException("Player is not in a party"));
        requireNoPendingContribution(party);
        if (party.owner().equals(player)) {
            if (party.members().size() == 1) {
                disband(player);
                return;
            }
            throw new IllegalStateException("Party owner must transfer ownership or disband");
        }
        parties.put(party.id(), party.withoutMember(player));
        membership.remove(player);
        readiness.remove(player);
        setDirty();
    }

    public synchronized void kick(UUID owner, UUID target) {
        requireActive();
        Party party = ownedParty(owner);
        requireNoPendingContribution(party);
        if (owner.equals(target) || !party.members().contains(target)) {
            throw new IllegalArgumentException("Party kick target is invalid");
        }
        parties.put(party.id(), party.withoutMember(target));
        membership.remove(target);
        readiness.remove(target);
        setDirty();
    }

    public synchronized void transferOwnership(UUID owner, UUID target) {
        requireActive();
        Party party = ownedParty(owner);
        if (!party.members().contains(target)) {
            throw new IllegalArgumentException("New party owner must be a member");
        }
        parties.put(party.id(), party.withOwner(target));
        setDirty();
    }

    public synchronized void disband(UUID owner) {
        requireActive();
        Party party = ownedParty(owner);
        requireNoPendingContribution(party);
        parties.remove(party.id());
        party.members().forEach(member -> {
            membership.remove(member);
            readiness.remove(member);
        });
        invites.values().removeIf(invite -> invite.partyId().equals(party.id()));
        setDirty();
    }

    public synchronized Optional<Party> party(UUID player) {
        requireActive();
        UUID partyId = membership.get(player);
        return partyId == null ? Optional.empty() : Optional.of(requiredParty(partyId));
    }

    public synchronized void setPrivacy(UUID player, PrivacySettings settings) {
        requireActive();
        if (!privacy.containsKey(player) && privacy.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Privacy capacity is full");
        }
        privacy.put(player, Objects.requireNonNull(settings, "settings"));
        setDirty();
    }

    public synchronized PrivacySettings privacy(UUID player) {
        requireActive();
        return privacy.getOrDefault(player, PrivacySettings.DEFAULT);
    }

    public synchronized PartyReadiness setReadiness(UUID player, PartyReadiness value) {
        requireActive();
        if (!readiness.containsKey(player) && readiness.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Readiness capacity is full");
        }
        Party party = party(player).orElseThrow(() -> new IllegalStateException("Player is not in a party"));
        Objects.requireNonNull(party, "party");
        readiness.put(player, Objects.requireNonNull(value, "value"));
        setDirty();
        return value;
    }

    public synchronized Map<UUID, PartyReadiness> readiness(UUID requester) {
        requireActive();
        Party party = party(requester).orElseThrow(() -> new IllegalStateException("Player is not in a party"));
        var result = new TreeMap<UUID, PartyReadiness>();
        party.members().forEach(member -> {
            PartyReadiness value = readiness.get(member);
            if (value != null) {
                result.put(member, redact(member, requester, value));
            }
        });
        return Collections.unmodifiableMap(result);
    }

    public synchronized ContributionReceipt share(
            UUID actor,
            ResourceLocation source,
            long total,
            Map<UUID, Long> weights
    ) {
        requireActive();
        Party party = party(actor).orElseThrow(() -> new IllegalStateException("Player is not in a party"));
        if (!party.members().containsAll(weights.keySet())) {
            throw new IllegalArgumentException("Contribution contains a nonmember");
        }
        Map<UUID, Long> shares = SharedAwardAllocator.allocate(total, weights);
        return shareExact(actor, source, shares, "Deterministic weighted party allocation");
    }

    public synchronized ContributionReceipt shareExact(
            UUID actor,
            ResourceLocation source,
            Map<UUID, Long> shares,
            String explanation
    ) {
        requireActive();
        Party party = party(actor).orElseThrow(() -> new IllegalStateException("Player is not in a party"));
        return shareExactAuthorized(actor, party.id(), party.members(), source, shares, explanation);
    }

    public synchronized ContributionReceipt shareExactAuthorized(
            UUID actor,
            UUID groupId,
            Set<UUID> authorizedMembers,
            ResourceLocation source,
            Map<UUID, Long> shares,
            String explanation
    ) {
        requireActive();
        Objects.requireNonNull(groupId, "groupId");
        authorizedMembers = Set.copyOf(Objects.requireNonNull(authorizedMembers, "authorizedMembers"));
        if (!authorizedMembers.contains(actor) || !authorizedMembers.containsAll(shares.keySet())
                || shares.values().stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("Contribution contains an invalid share");
        }
        long total = 0;
        for (long amount : shares.values()) {
            total = Math.addExact(total, amount);
        }
        UUID pendingId = pendingContributions.get(actor);
        if (pendingId != null) {
            ContributionReceipt pending = Objects.requireNonNull(receipts.get(pendingId), "pending contribution");
            if (!pending.groupId().equals(groupId) || !pending.source().equals(source)) {
                throw new IllegalStateException("A different contribution is still pending delivery");
            }
            return pending;
        }
        ContributionReceipt receipt = new ContributionReceipt(
                UUID.randomUUID(), groupId, source, total, shares,
                explanation, Instant.now());
        if (receipts.size() >= MAX_RECEIPTS) {
            UUID first = receipts.keySet().stream()
                    .filter(id -> !pendingContributions.containsValue(id)).findFirst().orElseThrow(
                            () -> new IllegalStateException("Contribution receipt capacity is full"));
            receipts.remove(first);
            contributionDeliveries.remove(first);
        }
        receipts.put(receipt.receiptId(), receipt);
        pendingContributions.put(actor, receipt.receiptId());
        contributionDeliveries.put(receipt.receiptId(), new LinkedHashSet<>());
        setDirty();
        return receipt;
    }

    public synchronized Map<UUID, Long> pendingShares(UUID actor, UUID receiptId) {
        requireActive();
        if (!Objects.equals(pendingContributions.get(actor), receiptId)) {
            throw new IllegalStateException("Contribution receipt is not pending for this actor");
        }
        ContributionReceipt receipt = Objects.requireNonNull(receipts.get(receiptId), "contribution receipt");
        Set<UUID> delivered = contributionDeliveries.getOrDefault(receiptId, Set.of());
        var result = new TreeMap<UUID, Long>();
        receipt.shares().forEach((player, amount) -> {
            if (amount > 0 && !delivered.contains(player)) {
                result.put(player, amount);
            }
        });
        return Collections.unmodifiableMap(result);
    }

    public synchronized void markContributionDelivered(UUID actor, UUID receiptId, UUID recipient) {
        requireActive();
        if (!Objects.equals(pendingContributions.get(actor), receiptId)) {
            throw new IllegalStateException("Contribution receipt is not pending for this actor");
        }
        ContributionReceipt receipt = Objects.requireNonNull(receipts.get(receiptId), "contribution receipt");
        if (receipt.shares().getOrDefault(recipient, 0L) < 1) {
            throw new IllegalArgumentException("Contribution recipient has no positive share");
        }
        contributionDeliveries.computeIfAbsent(receiptId, ignored -> new LinkedHashSet<>()).add(recipient);
        setDirty();
    }

    public synchronized void completeContribution(UUID actor, UUID receiptId) {
        requireActive();
        if (!pendingShares(actor, receiptId).isEmpty()) {
            throw new IllegalStateException("Contribution delivery is incomplete");
        }
        pendingContributions.remove(actor);
        contributionDeliveries.remove(receiptId);
        setDirty();
    }

    public synchronized List<ContributionReceipt> receipts(UUID player) {
        requireActive();
        Optional<Party> party = party(player);
        if (party.isEmpty()) {
            return List.of();
        }
        return receipts.values().stream().filter(value -> value.groupId().equals(party.orElseThrow().id()))
                .toList();
    }

    public synchronized List<ContributionReceipt> receiptsForGroup(UUID groupId) {
        requireActive();
        Objects.requireNonNull(groupId, "groupId");
        return receipts.values().stream().filter(value -> value.groupId().equals(groupId)).toList();
    }

    public synchronized Optional<ContributionReceipt> receipt(UUID receiptId) {
        requireActive();
        return Optional.ofNullable(receipts.get(receiptId));
    }

    public synchronized MentorOffer offerMentor(UUID mentor, UUID mentee) {
        requireActive();
        if (!mentorOffers.containsKey(mentee) && mentorOffers.size() >= MAX_OFFERS) {
            throw new IllegalStateException("Mentor offer capacity is full");
        }
        if (mentor.equals(mentee)) {
            throw new IllegalArgumentException("Player cannot mentor themselves");
        }
        MentorOffer offer = new MentorOffer(UUID.randomUUID(), mentor, mentee, Instant.now().plusSeconds(300));
        mentorOffers.put(mentee, offer);
        setDirty();
        return offer;
    }

    public synchronized void acceptMentor(UUID mentee) {
        requireActive();
        MentorOffer offer = Objects.requireNonNull(mentorOffers.get(mentee), "mentor offer");
        if (Instant.now().isAfter(offer.expiresAt())) {
            mentorOffers.remove(mentee);
            setDirty();
            throw new IllegalStateException("Mentor offer expired");
        }
        if (!mentors.containsKey(mentee) && mentors.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Mentor link capacity is full");
        }
        mentorOffers.remove(mentee);
        mentors.put(mentee, offer.mentor());
        setDirty();
    }

    public synchronized Optional<UUID> mentor(UUID mentee) {
        requireActive();
        return Optional.ofNullable(mentors.get(mentee));
    }

    public synchronized void removeMentor(UUID mentee) {
        requireActive();
        mentors.remove(mentee);
        setDirty();
    }

    public synchronized TransferOffer offerTransfer(
            UUID sender,
            UUID recipient,
            ResourceLocation currency,
            long amount
    ) {
        requireActive();
        if (!transferOffers.containsKey(recipient) && transferOffers.size() >= MAX_OFFERS) {
            throw new IllegalStateException("Transfer offer capacity is full");
        }
        if (sender.equals(recipient) || amount < 1) {
            throw new IllegalArgumentException("Transfer offer is invalid");
        }
        TransferOffer existing = transferOffers.get(recipient);
        if (existing != null) {
            if (existing.phase() == TransferPhase.OFFERED && Instant.now().isAfter(existing.expiresAt())) {
                transferOffers.remove(recipient);
            } else {
                throw new IllegalStateException("Recipient already has a pending transfer");
            }
        }
        TransferOffer offer = new TransferOffer(UUID.randomUUID(), sender, recipient, currency, amount,
                Instant.now().plusSeconds(300), TransferPhase.OFFERED);
        transferOffers.put(recipient, offer);
        setDirty();
        return offer;
    }

    public synchronized TransferOffer pendingTransfer(UUID recipient) {
        requireActive();
        TransferOffer offer = transferOffers.get(recipient);
        if (offer == null) {
            throw new IllegalStateException("Transfer offer is unavailable");
        }
        if (offer.phase() == TransferPhase.OFFERED && Instant.now().isAfter(offer.expiresAt())) {
            transferOffers.remove(recipient);
            setDirty();
            throw new IllegalStateException("Transfer offer expired");
        }
        return offer;
    }

    public synchronized TransferOffer markTransferDebited(UUID recipient, UUID offerId) {
        requireActive();
        TransferOffer offer = pendingTransfer(recipient);
        if (!offer.id().equals(offerId)) {
            throw new IllegalStateException("Transfer offer changed before debit commit");
        }
        if (offer.phase() == TransferPhase.DEBITED) {
            return offer;
        }
        TransferOffer updated = offer.withPhase(TransferPhase.DEBITED);
        transferOffers.put(recipient, updated);
        setDirty();
        return updated;
    }

    public synchronized void completeTransfer(UUID recipient, UUID offerId) {
        requireActive();
        TransferOffer offer = pendingTransfer(recipient);
        if (!offer.id().equals(offerId) || offer.phase() != TransferPhase.DEBITED) {
            throw new IllegalStateException("Transfer offer is not ready to complete");
        }
        transferOffers.remove(recipient);
        setDirty();
    }

    public synchronized void cancelTransfer(UUID recipient, UUID offerId) {
        requireActive();
        TransferOffer offer = Objects.requireNonNull(transferOffers.get(recipient), "transfer offer");
        if (!offer.id().equals(offerId)) {
            throw new IllegalStateException("Transfer offer changed before cancellation");
        }
        if (offer.phase() != TransferPhase.OFFERED) {
            throw new IllegalStateException("Debited transfer requires compensation before cancellation");
        }
        transferOffers.remove(recipient);
        setDirty();
    }

    public synchronized void cancelCompensatedTransfer(UUID recipient, UUID offerId) {
        requireActive();
        TransferOffer offer = Objects.requireNonNull(transferOffers.get(recipient), "transfer offer");
        if (!offer.id().equals(offerId) || offer.phase() != TransferPhase.DEBITED) {
            throw new IllegalStateException("Compensated transfer identity is invalid");
        }
        transferOffers.remove(recipient);
        setDirty();
    }

    public synchronized long progressSharedChallenge(
            UUID player,
            ResourceLocation challenge,
            long amount,
            long goal
    ) {
        requireActive();
        if (amount < 0 || goal < 1) {
            throw new IllegalArgumentException("Shared challenge progress is invalid");
        }
        Party party = party(player).orElseThrow(() -> new IllegalStateException("Player is not in a party"));
        long current = party.sharedChallenges().getOrDefault(challenge, 0L);
        long next = Math.min(goal, Math.addExact(current, amount));
        parties.put(party.id(), party.withSharedChallenge(challenge, next));
        setDirty();
        return next;
    }

    public synchronized void recordScore(UUID player, ResourceLocation board, long score) {
        requireActive();
        if (score < 0) {
            throw new IllegalArgumentException("Leaderboard score must not be negative");
        }
        if (!privacy(player).leaderboardOptIn()) {
            throw new IllegalStateException("Player has not opted into leaderboards");
        }
        if (!leaderboards.containsKey(board) && leaderboards.size() >= MAX_BOARDS) {
            throw new IllegalStateException("Leaderboard capacity is full");
        }
        Map<UUID, Long> values = leaderboards.computeIfAbsent(
                StableId.requireValid(board), ignored -> new LinkedHashMap<>());
        if (!values.containsKey(player) && values.size() >= MAX_BOARD_SCORES) {
            throw new IllegalStateException("Leaderboard score capacity is full");
        }
        if (!values.containsKey(player) && boardScoreCount(leaderboards) >= MAX_TOTAL_BOARD_SCORES) {
            throw new IllegalStateException("Total leaderboard score capacity is full");
        }
        values.put(player, score);
        setDirty();
    }

    public synchronized List<Map.Entry<UUID, Long>> leaderboard(ResourceLocation board, int limit) {
        requireActive();
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Leaderboard limit is invalid");
        }
        return leaderboards.getOrDefault(board, Map.of()).entrySet().stream()
                .filter(entry -> privacy(entry.getKey()).leaderboardOptIn())
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(limit).map(entry -> Map.entry(entry.getKey(), entry.getValue())).toList();
    }

    public synchronized void rollover(ResourceLocation nextSeason, long nextEpoch) {
        requireActive();
        if (nextEpoch <= seasonEpoch || nextSeason.equals(season)) {
            throw new IllegalArgumentException("Season rollover identity is invalid");
        }
        previousLeaderboards.clear();
        leaderboards.forEach((board, values) -> previousLeaderboards.put(board, Map.copyOf(values)));
        leaderboards.clear();
        season = nextSeason;
        seasonEpoch = nextEpoch;
        setDirty();
    }

    public synchronized SeasonStatus seasonStatus() {
        requireActive();
        return new SeasonStatus(season, seasonEpoch, leaderboards.size(), previousLeaderboards.size());
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
        output.putString("season", season.toString());
        output.putLong("season_epoch", seasonEpoch);
        output.put("parties", saveParties());
        output.put("invites", saveInvites());
        output.put("privacy", savePrivacy());
        output.put("readiness", saveReadiness());
        output.put("receipts", saveReceipts());
        output.put("mentors", saveMentors());
        output.put("mentor_offers", saveMentorOffers());
        output.put("transfer_offers", saveTransferOffers());
        output.put("leaderboards", saveBoards(leaderboards));
        output.put("previous_leaderboards", saveBoards(previousLeaderboards));
        return output;
    }

    static MultiplayerSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        var data = new MultiplayerSavedData();
        try {
            int version = input.contains("data_version", Tag.TAG_INT) ? input.getInt("data_version") : 0;
            if (version < 0 || version > DATA_VERSION) {
                throw new IllegalArgumentException("Unsupported multiplayer data version " + version);
            }
            if (input.contains("season", Tag.TAG_STRING)) {
                data.season = StableId.parse(input.getString("season"));
                data.seasonEpoch = input.getLong("season_epoch");
                if (data.seasonEpoch < 0) {
                    throw new IllegalArgumentException("Season epoch is invalid");
                }
            }
            data.loadParties(input.getList("parties", Tag.TAG_COMPOUND));
            data.loadInvites(input.getList("invites", Tag.TAG_COMPOUND));
            data.loadPrivacy(input.getList("privacy", Tag.TAG_COMPOUND));
            data.loadReadiness(input.getList("readiness", Tag.TAG_COMPOUND));
            data.loadReceipts(input.getList("receipts", Tag.TAG_COMPOUND));
            data.loadMentors(input.getList("mentors", Tag.TAG_COMPOUND));
            data.loadMentorOffers(input.getList("mentor_offers", Tag.TAG_COMPOUND));
            data.loadTransferOffers(input.getList("transfer_offers", Tag.TAG_COMPOUND));
            data.loadBoards(input.getList("leaderboards", Tag.TAG_COMPOUND), data.leaderboards);
            data.loadBoards(input.getList("previous_leaderboards", Tag.TAG_COMPOUND), data.previousLeaderboards);
        } catch (RuntimeException exception) {
            data.clearLoadedState();
            data.quarantined = true;
            data.quarantineReason = safeMessage(exception);
        }
        return data;
    }

    private ListTag saveParties() {
        var list = new ListTag();
        parties.values().forEach(party -> {
            var tag = new CompoundTag();
            tag.putUUID("id", party.id());
            tag.putUUID("owner", party.owner());
            tag.putString("name", party.name());
            tag.put("members", uuids(party.members()));
            tag.put("challenges", resourceLongs(party.sharedChallenges()));
            list.add(tag);
        });
        return list;
    }

    private void loadParties(ListTag list) {
        bounded(list, MAX_PARTIES);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            Party party = new Party(tag.getUUID("id"), tag.getUUID("owner"), tag.getString("name"),
                    readUuids(tag.getList("members", Tag.TAG_COMPOUND), MAX_PARTY_MEMBERS), Map.of(),
                    readResourceLongs(
                            tag.getList("challenges", Tag.TAG_COMPOUND), MAX_RESOURCE_VALUES));
            putUnique(parties, party.id(), party, "party");
            party.members().forEach(member -> {
                if (membership.size() >= MAX_PLAYERS) {
                    throw new IllegalArgumentException("Party player count exceeds capacity");
                }
                putUnique(membership, member, party.id(), "party membership");
            });
        });
    }

    private ListTag saveInvites() {
        var list = new ListTag();
        invites.values().forEach(invite -> {
            var tag = new CompoundTag();
            tag.putUUID("id", invite.id());
            tag.putUUID("party", invite.partyId());
            tag.putUUID("sender", invite.sender());
            tag.putUUID("target", invite.target());
            tag.putLong("expires", invite.expiresAt().toEpochMilli());
            list.add(tag);
        });
        return list;
    }

    private void loadInvites(ListTag list) {
        bounded(list, MAX_OFFERS);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            Invite invite = new Invite(tag.getUUID("id"), tag.getUUID("party"), tag.getUUID("sender"),
                    tag.getUUID("target"), Instant.ofEpochMilli(tag.getLong("expires")));
            Party party = requiredParty(invite.partyId());
            if (!party.members().contains(invite.sender())) {
                throw new IllegalArgumentException("Party invite sender is not a member");
            }
            putUnique(invites, invite.target(), invite, "party invite");
        });
    }

    private ListTag savePrivacy() {
        var list = new ListTag();
        privacy.forEach((player, settings) -> {
            var tag = new CompoundTag();
            tag.putUUID("player", player);
            tag.putString("visibility", settings.profileVisibility().name());
            tag.putBoolean("role", settings.shareRole());
            tag.putBoolean("build", settings.shareBuild());
            tag.putBoolean("resources", settings.shareResources());
            tag.putBoolean("cooldowns", settings.shareCooldowns());
            tag.putBoolean("leaderboard", settings.leaderboardOptIn());
            list.add(tag);
        });
        return list;
    }

    private void loadPrivacy(ListTag list) {
        bounded(list, MAX_PLAYERS);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            putUnique(privacy, tag.getUUID("player"), new PrivacySettings(
                    PrivacySettings.Visibility.valueOf(tag.getString("visibility")),
                    tag.getBoolean("role"), tag.getBoolean("build"), tag.getBoolean("resources"),
                    tag.getBoolean("cooldowns"), tag.getBoolean("leaderboard")), "privacy");
        });
    }

    private ListTag saveReadiness() {
        var list = new ListTag();
        readiness.forEach((player, state) -> {
            var tag = new CompoundTag();
            tag.putUUID("player", player);
            tag.putBoolean("ready", state.ready());
            tag.putString("role", state.role());
            tag.putString("build", state.build());
            tag.putString("resources", state.resources());
            tag.putString("cooldowns", state.cooldowns());
            tag.putLong("updated", state.updatedAt().toEpochMilli());
            list.add(tag);
        });
        return list;
    }

    private void loadReadiness(ListTag list) {
        bounded(list, MAX_PLAYERS);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            UUID player = tag.getUUID("player");
            if (!membership.containsKey(player)) {
                throw new IllegalArgumentException("Readiness player is not in a party");
            }
            putUnique(readiness, player, new PartyReadiness(tag.getBoolean("ready"),
                    tag.getString("role"), tag.getString("build"), tag.getString("resources"),
                    tag.getString("cooldowns"), Instant.ofEpochMilli(tag.getLong("updated"))), "readiness");
        });
    }

    private ListTag saveReceipts() {
        var list = new ListTag();
        receipts.values().forEach(receipt -> {
            var tag = new CompoundTag();
            tag.putUUID("id", receipt.receiptId());
            tag.putUUID("group", receipt.groupId());
            tag.putString("source", receipt.source().toString());
            tag.putLong("total", receipt.total());
            tag.put("shares", uuidLongs(receipt.shares()));
            tag.putString("explanation", receipt.explanation());
            tag.putLong("created", receipt.createdAt().toEpochMilli());
            pendingContributions.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(receipt.receiptId())).findFirst()
                    .ifPresent(entry -> tag.putUUID("pending_actor", entry.getKey()));
            Set<UUID> delivered = contributionDeliveries.get(receipt.receiptId());
            if (delivered != null) {
                tag.put("delivered", uuids(delivered));
            }
            list.add(tag);
        });
        return list;
    }

    private void loadReceipts(ListTag list) {
        bounded(list, MAX_RECEIPTS);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            ContributionReceipt receipt = new ContributionReceipt(tag.getUUID("id"), tag.getUUID("group"),
                    StableId.parse(tag.getString("source")), tag.getLong("total"),
                    readUuidLongs(tag.getList("shares", Tag.TAG_COMPOUND), MAX_PARTY_MEMBERS),
                    tag.getString("explanation"),
                    Instant.ofEpochMilli(tag.getLong("created")));
            putUnique(receipts, receipt.receiptId(), receipt, "contribution receipt");
            if (tag.contains("pending_actor")) {
                UUID actor = tag.getUUID("pending_actor");
                Party party = requiredParty(receipt.groupId());
                if (!party.members().contains(actor)) {
                    throw new IllegalArgumentException("Pending contribution actor is not a party member");
                }
                Set<UUID> delivered = readUuids(
                        tag.getList("delivered", Tag.TAG_COMPOUND), MAX_PARTY_MEMBERS);
                if (!receipt.shares().keySet().containsAll(delivered)
                        || delivered.stream().anyMatch(player -> receipt.shares().getOrDefault(player, 0L) < 1)) {
                    throw new IllegalArgumentException("Pending contribution delivery set is invalid");
                }
                putUnique(pendingContributions, actor, receipt.receiptId(), "pending contribution");
                putUnique(contributionDeliveries, receipt.receiptId(), delivered,
                        "contribution delivery set");
            }
        });
    }

    private ListTag saveMentors() {
        var list = new ListTag();
        mentors.forEach((mentee, mentor) -> {
            var tag = new CompoundTag();
            tag.putUUID("mentee", mentee);
            tag.putUUID("mentor", mentor);
            list.add(tag);
        });
        return list;
    }

    private void loadMentors(ListTag list) {
        bounded(list, MAX_PLAYERS);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            UUID mentee = tag.getUUID("mentee");
            UUID mentor = tag.getUUID("mentor");
            if (mentee.equals(mentor)) {
                throw new IllegalArgumentException("Mentor link is invalid");
            }
            putUnique(mentors, mentee, mentor, "mentor link");
        });
    }

    private ListTag saveMentorOffers() {
        var list = new ListTag();
        mentorOffers.values().forEach(offer -> {
            var tag = new CompoundTag();
            tag.putUUID("id", offer.id());
            tag.putUUID("mentor", offer.mentor());
            tag.putUUID("mentee", offer.mentee());
            tag.putLong("expires", offer.expiresAt().toEpochMilli());
            list.add(tag);
        });
        return list;
    }

    private void loadMentorOffers(ListTag list) {
        bounded(list, MAX_OFFERS);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            MentorOffer offer = new MentorOffer(tag.getUUID("id"), tag.getUUID("mentor"),
                    tag.getUUID("mentee"), Instant.ofEpochMilli(tag.getLong("expires")));
            if (offer.mentor().equals(offer.mentee())) {
                throw new IllegalArgumentException("Mentor offer is invalid");
            }
            putUnique(mentorOffers, offer.mentee(), offer, "mentor offer");
        });
    }

    private ListTag saveTransferOffers() {
        var list = new ListTag();
        transferOffers.values().forEach(offer -> {
            var tag = new CompoundTag();
            tag.putUUID("id", offer.id());
            tag.putUUID("sender", offer.sender());
            tag.putUUID("recipient", offer.recipient());
            tag.putString("currency", offer.currency().toString());
            tag.putLong("amount", offer.amount());
            tag.putLong("expires", offer.expiresAt().toEpochMilli());
            tag.putString("phase", offer.phase().name());
            list.add(tag);
        });
        return list;
    }

    private void loadTransferOffers(ListTag list) {
        bounded(list, MAX_OFFERS);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            TransferOffer offer = new TransferOffer(tag.getUUID("id"), tag.getUUID("sender"),
                    tag.getUUID("recipient"), StableId.parse(tag.getString("currency")),
                    tag.getLong("amount"), Instant.ofEpochMilli(tag.getLong("expires")),
                    tag.contains("phase", Tag.TAG_STRING)
                            ? TransferPhase.valueOf(tag.getString("phase")) : TransferPhase.OFFERED);
            if (offer.sender().equals(offer.recipient()) || offer.amount() < 1) {
                throw new IllegalArgumentException("Transfer offer is invalid");
            }
            putUnique(transferOffers, offer.recipient(), offer, "transfer offer");
        });
    }

    private static ListTag saveBoards(Map<ResourceLocation, Map<UUID, Long>> boards) {
        var list = new ListTag();
        boards.forEach((board, values) -> {
            var tag = new CompoundTag();
            tag.putString("board", board.toString());
            tag.put("scores", uuidLongs(values));
            list.add(tag);
        });
        return list;
    }

    private void loadBoards(ListTag list, Map<ResourceLocation, Map<UUID, Long>> target) {
        bounded(list, MAX_BOARDS);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            Map<UUID, Long> scores = new LinkedHashMap<>(
                    readUuidLongs(tag.getList("scores", Tag.TAG_COMPOUND), MAX_BOARD_SCORES));
            if ((long) boardScoreCount(target) + scores.size() > MAX_TOTAL_BOARD_SCORES) {
                throw new IllegalArgumentException("Total leaderboard score count exceeds capacity");
            }
            putUnique(target, StableId.parse(tag.getString("board")), scores, "leaderboard");
        });
    }

    private static int boardScoreCount(Map<ResourceLocation, Map<UUID, Long>> boards) {
        long total = 0;
        for (Map<UUID, Long> board : boards.values()) {
            total += board.size();
            if (total >= MAX_TOTAL_BOARD_SCORES) {
                return MAX_TOTAL_BOARD_SCORES;
            }
        }
        return (int) total;
    }

    private static ListTag uuids(Set<UUID> values) {
        var list = new ListTag();
        values.forEach(value -> {
            var tag = new CompoundTag();
            tag.putUUID("value", value);
            list.add(tag);
        });
        return list;
    }

    private static Set<UUID> readUuids(ListTag list, int maximum) {
        bounded(list, maximum);
        var result = new LinkedHashSet<UUID>();
        list.forEach(value -> {
            if (!result.add(((CompoundTag) value).getUUID("value"))) {
                throw new IllegalArgumentException("Multiplayer data contains a duplicate player");
            }
        });
        return result;
    }

    private static ListTag uuidLongs(Map<UUID, Long> values) {
        var list = new ListTag();
        values.forEach((id, amount) -> {
            var tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putLong("amount", amount);
            list.add(tag);
        });
        return list;
    }

    private static Map<UUID, Long> readUuidLongs(ListTag list, int maximum) {
        bounded(list, maximum);
        var result = new TreeMap<UUID, Long>();
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            long amount = tag.getLong("amount");
            if (amount < 0 || result.putIfAbsent(tag.getUUID("id"), amount) != null) {
                throw new IllegalArgumentException("Multiplayer player amount is invalid");
            }
        });
        return result;
    }

    private static ListTag resourceLongs(Map<ResourceLocation, Long> values) {
        var list = new ListTag();
        values.forEach((id, amount) -> {
            var tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putLong("amount", amount);
            list.add(tag);
        });
        return list;
    }

    private static Map<ResourceLocation, Long> readResourceLongs(ListTag list, int maximum) {
        bounded(list, maximum);
        var result = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            ResourceLocation id = StableId.parse(tag.getString("id"));
            long amount = tag.getLong("amount");
            if (amount < 0 || result.putIfAbsent(id, amount) != null) {
                throw new IllegalArgumentException("Multiplayer resource amount is invalid");
            }
        });
        return result;
    }

    private PartyReadiness redact(UUID player, UUID requester, PartyReadiness value) {
        PrivacySettings settings = privacy(player);
        if (player.equals(requester)) {
            return value;
        }
        boolean visible = settings.profileVisibility() != PrivacySettings.Visibility.PRIVATE;
        return new PartyReadiness(
                value.ready(),
                visible && settings.shareRole() ? value.role() : "hidden",
                visible && settings.shareBuild() ? value.build() : "hidden",
                visible && settings.shareResources() ? value.resources() : "hidden",
                visible && settings.shareCooldowns() ? value.cooldowns() : "hidden",
                value.updatedAt()
        );
    }

    private void clearLoadedState() {
        parties.clear();
        membership.clear();
        invites.clear();
        privacy.clear();
        readiness.clear();
        receipts.clear();
        pendingContributions.clear();
        contributionDeliveries.clear();
        mentors.clear();
        mentorOffers.clear();
        transferOffers.clear();
        leaderboards.clear();
        previousLeaderboards.clear();
    }

    private void requireActive() {
        if (quarantined) {
            throw new IllegalStateException("Multiplayer data is quarantined. " + quarantineReason);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static <K, V> void putUnique(Map<K, V> target, K key, V value, String name) {
        if (target.putIfAbsent(Objects.requireNonNull(key, name + " key"),
                Objects.requireNonNull(value, name + " value")) != null) {
            throw new IllegalArgumentException("Multiplayer data contains a duplicate " + name);
        }
    }

    private Party ownedParty(UUID owner) {
        Party party = party(owner).orElseThrow(() -> new IllegalStateException("Player is not in a party"));
        if (!party.owner().equals(owner)) {
            throw new IllegalStateException("Only the party owner can do that");
        }
        return party;
    }

    private Party requiredParty(UUID partyId) {
        Party party = parties.get(partyId);
        if (party == null) {
            throw new IllegalStateException("Party is unavailable");
        }
        return party;
    }

    private void requireNoParty(UUID player) {
        if (membership.containsKey(player)) {
            throw new IllegalStateException("Player is already in a party");
        }
    }

    private void requireNoPendingContribution(Party party) {
        if (party.members().stream().anyMatch(pendingContributions::containsKey)) {
            throw new IllegalStateException("Party membership cannot change during contribution delivery");
        }
    }

    private static String boundedName(String name) {
        String value = Objects.requireNonNull(name, "name").strip();
        if (value.isEmpty() || value.length() > 48) {
            throw new IllegalArgumentException("Party name is invalid");
        }
        return value;
    }

    private static void bounded(ListTag list, int maximum) {
        if (list.size() > maximum) {
            throw new IllegalArgumentException("Multiplayer data exceeds capacity");
        }
    }

    public record Party(
            UUID id,
            UUID owner,
            String name,
            Set<UUID> members,
            Map<UUID, String> roles,
            Map<ResourceLocation, Long> sharedChallenges
    ) {
        public Party {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
            name = boundedName(name);
            members = Collections.unmodifiableSet(new LinkedHashSet<>(members));
            roles = Map.copyOf(roles);
            sharedChallenges = Collections.unmodifiableMap(new TreeMap<>(sharedChallenges));
            if (members.isEmpty() || members.size() > MAX_PARTY_MEMBERS || !members.contains(owner)) {
                throw new IllegalArgumentException("Party membership is invalid");
            }
            if (roles.size() > MAX_PARTY_MEMBERS || !members.containsAll(roles.keySet())
                    || sharedChallenges.size() > MAX_RESOURCE_VALUES
                    || sharedChallenges.values().stream().anyMatch(value -> value < 0)) {
                throw new IllegalArgumentException("Party nested state is invalid");
            }
        }

        Party withMember(UUID player) {
            var next = new LinkedHashSet<>(members);
            next.add(player);
            return new Party(id, owner, name, next, roles, sharedChallenges);
        }

        Party withoutMember(UUID player) {
            var next = new LinkedHashSet<>(members);
            next.remove(player);
            return new Party(id, owner, name, next, roles, sharedChallenges);
        }

        Party withOwner(UUID player) {
            return new Party(id, player, name, members, roles, sharedChallenges);
        }

        Party withSharedChallenge(ResourceLocation challenge, long progress) {
            var next = new TreeMap<>(sharedChallenges);
            next.put(challenge, progress);
            return new Party(id, owner, name, members, roles, next);
        }
    }

    public record Invite(UUID id, UUID partyId, UUID sender, UUID target, Instant expiresAt) {
        public Invite {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(partyId, "partyId");
            Objects.requireNonNull(sender, "sender");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (sender.equals(target)) {
                throw new IllegalArgumentException("Party invite is invalid");
            }
        }
    }

    public record MentorOffer(UUID id, UUID mentor, UUID mentee, Instant expiresAt) {
        public MentorOffer {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(mentor, "mentor");
            Objects.requireNonNull(mentee, "mentee");
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (mentor.equals(mentee)) {
                throw new IllegalArgumentException("Mentor offer is invalid");
            }
        }
    }

    public record TransferOffer(
            UUID id,
            UUID sender,
            UUID recipient,
            ResourceLocation currency,
            long amount,
            Instant expiresAt,
            TransferPhase phase
    ) {
        public TransferOffer {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sender, "sender");
            Objects.requireNonNull(recipient, "recipient");
            currency = StableId.requireValid(currency);
            Objects.requireNonNull(expiresAt, "expiresAt");
            Objects.requireNonNull(phase, "phase");
            if (sender.equals(recipient) || amount < 1) {
                throw new IllegalArgumentException("Transfer offer is invalid");
            }
        }

        TransferOffer withPhase(TransferPhase next) {
            return new TransferOffer(id, sender, recipient, currency, amount, expiresAt, next);
        }
    }

    public enum TransferPhase {
        OFFERED,
        DEBITED
    }

    public record SeasonStatus(ResourceLocation season, long epoch, int activeBoards, int archivedBoards) {
        public SeasonStatus {
            season = StableId.requireValid(season);
            if (epoch < 0 || activeBoards < 0 || archivedBoards < 0) {
                throw new IllegalArgumentException("Season status is invalid");
            }
        }
    }
}
