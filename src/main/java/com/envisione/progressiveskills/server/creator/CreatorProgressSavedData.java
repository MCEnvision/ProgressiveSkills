package com.envisione.progressiveskills.server.creator;

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
import java.nio.charset.StandardCharsets;

public final class CreatorProgressSavedData extends SavedData {
    public static final String FILE_ID = "progressiveskills_creator_progress";
    public static final int DATA_VERSION = 2;
    private static final int MAX_PLAYERS = 4096;
    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_SKILL_AWARD_RECEIPTS = 512;
    private final Map<UUID, PlayerState> players = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> skillAwardReceipts = new LinkedHashMap<>();
    private boolean quarantined;
    private String quarantineReason = "";

    public static SavedData.Factory<CreatorProgressSavedData> factory() {
        return new SavedData.Factory<>(CreatorProgressSavedData::new, CreatorProgressSavedData::load);
    }

    public static CreatorProgressSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public synchronized PlayerState state(UUID playerId) {
        requireActive();
        return players.getOrDefault(playerId, PlayerState.EMPTY);
    }

    public synchronized long adjustResource(
            UUID playerId,
            ResourceLocation resource,
            long delta,
            long minimum,
            long maximum,
            long initial
    ) {
        if (minimum > initial || initial > maximum) {
            throw new IllegalArgumentException("Resource bounds are invalid");
        }
        PlayerState state = state(playerId);
        long current = state.resources().getOrDefault(resource, initial);
        long next = Math.max(minimum, Math.min(maximum, Math.addExact(current, delta)));
        if (next == current) {
            return next;
        }
        put(playerId, state.withResource(resource, next));
        return next;
    }

    public synchronized TimedAwardResult applyTimedResourceChange(
            UUID playerId,
            ResourceLocation readyKey,
            long gameTick,
            long cooldownTicks,
            Optional<ResourceChange> change
    ) {
        if (gameTick < 0 || cooldownTicks < 0) {
            throw new IllegalArgumentException("Timed resource change clock is invalid");
        }
        PlayerState state = state(playerId);
        long ready = state.resources().getOrDefault(readyKey, 0L);
        if (gameTick < ready) {
            return new TimedAwardResult(false, ready - gameTick);
        }
        PlayerState updated = state;
        if (change.isPresent()) {
            ResourceChange value = change.orElseThrow();
            if (value.resource().equals(readyKey)) {
                throw new IllegalArgumentException("Timed resource and cooldown identities must differ");
            }
            long current = state.resources().getOrDefault(value.resource(), value.initial());
            long next = Math.max(value.minimum(), Math.min(
                    value.maximum(), Math.addExact(current, value.amount())));
            updated = updated.withResource(value.resource(), next);
        }
        long nextReady = Math.addExact(gameTick, cooldownTicks);
        updated = updated.withResource(readyKey, nextReady);
        put(playerId, updated);
        return new TimedAwardResult(true, cooldownTicks);
    }

    public synchronized int prestige(UUID playerId, ResourceLocation track) {
        PlayerState state = state(playerId);
        int next = Math.addExact(state.prestige().getOrDefault(track, 0), 1);
        put(playerId, state.withPrestige(track, next));
        return next;
    }

    public synchronized int commitPrestige(
            UUID playerId,
            ResourceLocation track,
            int expectedPrevious,
            Optional<ResourceReward> reward
    ) {
        PlayerState state = state(playerId);
        int current = state.prestige().getOrDefault(track, 0);
        int expectedNext = Math.addExact(expectedPrevious, 1);
        if (current == expectedNext) {
            return current;
        }
        if (current != expectedPrevious) {
            throw new IllegalStateException("Prestige state changed before commit");
        }
        PlayerState updated = state.withPrestige(track, Math.addExact(current, 1));
        if (reward.isPresent()) {
            ResourceReward value = reward.orElseThrow();
            long before = updated.resources().getOrDefault(value.resource(), value.initial());
            long next = Math.addExact(before, value.amount());
            if (next < value.minimum() || next > value.maximum()) {
                throw new IllegalStateException("Prestige resource reward exceeds its bounds");
            }
            updated = updated.withResource(value.resource(), next);
        }
        put(playerId, updated);
        return Math.addExact(current, 1);
    }

    public synchronized MilestoneReceipt choose(
            UUID playerId,
            ResourceLocation milestone,
            ResourceLocation choice,
            boolean respecAllowed
    ) {
        PlayerState state = state(playerId);
        if (state.milestoneChoices().containsKey(milestone) && !respecAllowed) {
            throw new IllegalStateException("Milestone choice cannot be changed");
        }
        MilestoneReceipt receipt = new MilestoneReceipt(
                UUID.nameUUIDFromBytes((playerId + ":" + milestone + ":" + choice)
                        .getBytes(StandardCharsets.UTF_8)),
                choice,
                Instant.now()
        );
        put(playerId, state.withChoice(milestone, choice, receipt));
        return receipt;
    }

    public synchronized ContractState assignContract(
            UUID playerId,
            ResourceLocation contract,
            List<ResourceLocation> eligibleSkills,
            long minimumGoal,
            long maximumGoal,
            long epoch
    ) {
        if (eligibleSkills.isEmpty() || minimumGoal < 1 || maximumGoal < minimumGoal) {
            throw new IllegalArgumentException("Training contract parameters are invalid");
        }
        PlayerState state = state(playerId);
        ContractState existing = state.contracts().get(contract);
        if (existing != null && existing.epoch() == epoch) {
            return existing;
        }
        List<ResourceLocation> sorted = eligibleSkills.stream().distinct()
                .sorted(ResourceLocation::compareNamespaced).toList();
        long seed = playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits()
                ^ contract.hashCode() ^ epoch;
        ResourceLocation skill = sorted.get(Math.floorMod(seed, sorted.size()));
        long span = Math.addExact(Math.subtractExact(maximumGoal, minimumGoal), 1L);
        long goal = Math.addExact(minimumGoal, Math.floorMod(Long.rotateLeft(seed, 17), span));
        ContractState created = new ContractState(skill, goal, 0, epoch, false);
        put(playerId, state.withContract(contract, created));
        return created;
    }

    public synchronized ContractState progressContract(
            UUID playerId,
            ResourceLocation contract,
            ResourceLocation skill,
            long amount,
            long epoch
    ) {
        if (amount < 0) {
            throw new IllegalArgumentException("Training progress must not be negative");
        }
        PlayerState state = state(playerId);
        ContractState current = Objects.requireNonNull(state.contracts().get(contract), "contract");
        if (current.epoch() != epoch || !current.skill().equals(skill)) {
            return current;
        }
        long progress = Math.min(current.goal(), Math.addExact(current.progress(), amount));
        ContractState updated = new ContractState(skill, current.goal(), progress, epoch,
                progress >= current.goal());
        put(playerId, state.withContract(contract, updated));
        return updated;
    }

    public synchronized ComboState awardCombo(
            UUID playerId,
            ResourceLocation combo,
            List<ResourceLocation> sequence,
            ResourceLocation award,
            long gameTick,
            long timeoutTicks,
            long cooldownTicks,
            int masteryCap,
            long capWindowTicks,
            int maxRepeatedAwards
    ) {
        if (sequence.size() < 2 || sequence.size() > 32 || timeoutTicks < 1
                || cooldownTicks < 0 || masteryCap < 1 || capWindowTicks < 1
                || maxRepeatedAwards < 1) {
            throw new IllegalArgumentException("Combo mastery parameters are invalid");
        }
        PlayerState state = state(playerId);
        ComboState current = state.combos().getOrDefault(combo, ComboState.EMPTY);
        if (gameTick < current.nextReadyTick()) {
            return current;
        }
        int repeatCount = current.lastAward().filter(award::equals).isPresent()
                ? Math.addExact(current.repeatCount(), 1) : 1;
        if (repeatCount > maxRepeatedAwards) {
            ComboState rejected = new ComboState(
                    0, gameTick, current.mastery(), current.nextReadyTick(),
                    current.windowStartTick(), current.windowCompletions(),
                    Optional.of(award), repeatCount);
            put(playerId, state.withCombo(combo, rejected));
            return rejected;
        }
        long windowStart = current.windowStartTick();
        int windowCompletions = current.windowCompletions();
        if (gameTick < windowStart || gameTick - windowStart > capWindowTicks) {
            windowStart = gameTick;
            windowCompletions = 0;
        }
        int index = gameTick < current.lastTick() || gameTick - current.lastTick() > timeoutTicks
                || current.index() >= sequence.size() ? 0 : current.index();
        if (!sequence.get(index).equals(award)) {
            index = sequence.getFirst().equals(award) ? 1 : 0;
        } else {
            index++;
        }
        long mastery = current.mastery();
        long nextReady = current.nextReadyTick();
        if (index >= sequence.size()) {
            if (windowCompletions < masteryCap) {
                mastery = Math.addExact(mastery, 1L);
                windowCompletions = Math.addExact(windowCompletions, 1);
            }
            nextReady = Math.addExact(gameTick, cooldownTicks);
            index = 0;
        }
        ComboState updated = new ComboState(
                index, gameTick, mastery, nextReady, windowStart, windowCompletions,
                Optional.of(award), repeatCount);
        put(playerId, state.withCombo(combo, updated));
        return updated;
    }

    public synchronized void saveLoadout(UUID playerId, ResourceLocation id, LoadoutState loadout) {
        put(playerId, state(playerId).withLoadout(id, loadout));
    }

    public synchronized Optional<LoadoutState> loadout(UUID playerId, ResourceLocation id) {
        return Optional.ofNullable(state(playerId).loadouts().get(id));
    }

    public synchronized long progressChallenge(
            UUID playerId,
            ResourceLocation challenge,
            long amount,
            long goal
    ) {
        if (amount < 0 || goal < 1) {
            throw new IllegalArgumentException("Challenge progress is invalid");
        }
        PlayerState state = state(playerId);
        long next = Math.min(goal, Math.addExact(state.challenges().getOrDefault(challenge, 0L), amount));
        put(playerId, state.withChallenge(challenge, next));
        return next;
    }

    public synchronized boolean runSkillAward(
            UUID playerId,
            UUID receiptId,
            Runnable operation
    ) {
        requireActive();
        Objects.requireNonNull(receiptId, "receiptId");
        Objects.requireNonNull(operation, "operation");
        LinkedHashSet<UUID> receipts = skillAwardReceipts.computeIfAbsent(
                playerId, ignored -> new LinkedHashSet<>());
        if (receipts.contains(receiptId)) {
            return false;
        }
        boolean existed = players.containsKey(playerId);
        PlayerState before = players.getOrDefault(playerId, PlayerState.EMPTY);
        try {
            operation.run();
            while (receipts.size() >= MAX_SKILL_AWARD_RECEIPTS) {
                receipts.remove(receipts.iterator().next());
            }
            receipts.add(receiptId);
            setDirty();
            return true;
        } catch (RuntimeException exception) {
            if (existed) {
                players.put(playerId, before);
            } else {
                players.remove(playerId);
            }
            if (receipts.isEmpty()) {
                skillAwardReceipts.remove(playerId);
            }
            throw exception;
        }
    }

    private void put(UUID playerId, PlayerState state) {
        if (!players.containsKey(playerId) && players.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Creator player capacity is full");
        }
        players.put(playerId, state);
        setDirty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag output, HolderLookup.Provider provider) {
        requireActive();
        output.putInt("data_version", DATA_VERSION);
        var list = new ListTag();
        players.forEach((playerId, state) -> {
            var tag = new CompoundTag();
            tag.putUUID("player", playerId);
            tag.put("resources", longs(state.resources()));
            tag.put("prestige", integers(state.prestige()));
            tag.put("choices", ids(state.milestoneChoices()));
            tag.put("milestone_receipts", milestoneReceipts(state.milestoneReceipts()));
            tag.put("contracts", contracts(state.contracts()));
            tag.put("combos", combos(state.combos()));
            tag.put("loadouts", loadouts(state.loadouts()));
            tag.put("challenges", longs(state.challenges()));
            tag.put("skill_award_receipts", receipts(skillAwardReceipts.getOrDefault(
                    playerId, new LinkedHashSet<>())));
            list.add(tag);
        });
        output.put("players", list);
        return output;
    }

    static CreatorProgressSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        var data = new CreatorProgressSavedData();
        try {
            int version = input.contains("data_version", Tag.TAG_INT) ? input.getInt("data_version") : 0;
            if (version < 0 || version > DATA_VERSION) {
                throw new IllegalArgumentException("Unsupported Creator data version " + version);
            }
            ListTag list = input.contains("players", Tag.TAG_LIST)
                    ? input.getList("players", Tag.TAG_COMPOUND) : new ListTag();
            if (list.size() > MAX_PLAYERS) {
                throw new IllegalArgumentException("Creator player count exceeds capacity");
            }
            for (int index = 0; index < list.size(); index++) {
                CompoundTag tag = list.getCompound(index);
                UUID playerId = tag.getUUID("player");
                Map<ResourceLocation, ResourceLocation> choices = readIds(
                        tag.getList("choices", Tag.TAG_COMPOUND));
                Map<ResourceLocation, MilestoneReceipt> milestoneReceipts = version == 0
                        ? legacyReceipts(playerId, choices)
                        : readMilestoneReceipts(tag.getList("milestone_receipts", Tag.TAG_COMPOUND));
                PlayerState state = new PlayerState(
                        readLongs(tag.getList("resources", Tag.TAG_COMPOUND)),
                        readIntegers(tag.getList("prestige", Tag.TAG_COMPOUND)),
                        choices,
                        milestoneReceipts,
                        readContracts(tag.getList("contracts", Tag.TAG_COMPOUND)),
                        readCombos(tag.getList("combos", Tag.TAG_COMPOUND)),
                        readLoadouts(tag.getList("loadouts", Tag.TAG_COMPOUND)),
                        readLongs(tag.getList("challenges", Tag.TAG_COMPOUND))
                );
                if (state.milestoneReceipts().keySet().equals(state.milestoneChoices().keySet())) {
                    putUnique(data.players, playerId, state, "player");
                    if (version >= 2) {
                        putUnique(data.skillAwardReceipts, playerId,
                                readReceipts(tag.getList("skill_award_receipts", Tag.TAG_COMPOUND)),
                                "skill award receipt set");
                    }
                } else {
                    throw new IllegalArgumentException("Milestone choices and receipts do not match");
                }
            }
        } catch (RuntimeException exception) {
            data.players.clear();
            data.skillAwardReceipts.clear();
            data.quarantined = true;
            data.quarantineReason = safeMessage(exception);
        }
        return data;
    }

    private static ListTag longs(Map<ResourceLocation, Long> values) {
        var list = new ListTag();
        values.forEach((id, value) -> {
            var tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putLong("value", value);
            list.add(tag);
        });
        return list;
    }

    private static ListTag integers(Map<ResourceLocation, Integer> values) {
        var list = new ListTag();
        values.forEach((id, value) -> {
            var tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putInt("value", value);
            list.add(tag);
        });
        return list;
    }

    private static ListTag ids(Map<ResourceLocation, ResourceLocation> values) {
        var list = new ListTag();
        values.forEach((id, value) -> {
            var tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putString("value", value.toString());
            list.add(tag);
        });
        return list;
    }

    private static ListTag milestoneReceipts(Map<ResourceLocation, MilestoneReceipt> values) {
        var list = new ListTag();
        values.forEach((id, value) -> {
            var tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putUUID("receipt", value.receiptId());
            tag.putString("choice", value.choice().toString());
            tag.putLong("selected_at", value.selectedAt().toEpochMilli());
            list.add(tag);
        });
        return list;
    }

    private static ListTag contracts(Map<ResourceLocation, ContractState> values) {
        var list = new ListTag();
        values.forEach((id, value) -> {
            var tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putString("skill", value.skill().toString());
            tag.putLong("goal", value.goal());
            tag.putLong("progress", value.progress());
            tag.putLong("epoch", value.epoch());
            tag.putBoolean("complete", value.complete());
            list.add(tag);
        });
        return list;
    }

    private static ListTag combos(Map<ResourceLocation, ComboState> values) {
        var list = new ListTag();
        values.forEach((id, value) -> {
            var tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putInt("index", value.index());
            tag.putLong("last_tick", value.lastTick());
            tag.putLong("mastery", value.mastery());
            tag.putLong("next_ready", value.nextReadyTick());
            tag.putLong("window_start", value.windowStartTick());
            tag.putInt("window_completions", value.windowCompletions());
            value.lastAward().ifPresent(award -> tag.putString("last_award", award.toString()));
            tag.putInt("repeat_count", value.repeatCount());
            list.add(tag);
        });
        return list;
    }

    private static ListTag loadouts(Map<ResourceLocation, LoadoutState> values) {
        var list = new ListTag();
        values.forEach((id, value) -> {
            var tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putString("digest", value.definitionDigest());
            tag.putString("build_code", value.buildCode());
            tag.putLong("saved_at", value.savedAt().toEpochMilli());
            list.add(tag);
        });
        return list;
    }

    private static ListTag receipts(Set<UUID> values) {
        var list = new ListTag();
        values.forEach(value -> {
            var tag = new CompoundTag();
            tag.putUUID("id", value);
            list.add(tag);
        });
        return list;
    }

    private static Map<ResourceLocation, Long> readLongs(ListTag list) {
        var result = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        bounded(list);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            long stored = tag.getLong("value");
            if (stored < 0) {
                throw new IllegalArgumentException("Creator long value is invalid");
            }
            putUnique(result, StableId.parse(tag.getString("id")), stored, "long value");
        });
        return result;
    }

    private static Map<ResourceLocation, Integer> readIntegers(ListTag list) {
        var result = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        bounded(list);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            int stored = tag.getInt("value");
            if (stored < 0) {
                throw new IllegalArgumentException("Creator integer value is invalid");
            }
            putUnique(result, StableId.parse(tag.getString("id")), stored, "integer value");
        });
        return result;
    }

    private static Map<ResourceLocation, ResourceLocation> readIds(ListTag list) {
        var result = new TreeMap<ResourceLocation, ResourceLocation>(ResourceLocation::compareNamespaced);
        bounded(list);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            putUnique(result, StableId.parse(tag.getString("id")), StableId.parse(tag.getString("value")),
                    "identity value");
        });
        return result;
    }

    private static Map<ResourceLocation, MilestoneReceipt> readMilestoneReceipts(ListTag list) {
        var result = new TreeMap<ResourceLocation, MilestoneReceipt>(ResourceLocation::compareNamespaced);
        bounded(list);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            putUnique(result, StableId.parse(tag.getString("id")), new MilestoneReceipt(
                    tag.getUUID("receipt"), StableId.parse(tag.getString("choice")),
                    Instant.ofEpochMilli(tag.getLong("selected_at"))), "milestone receipt");
        });
        return result;
    }

    private static Map<ResourceLocation, ContractState> readContracts(ListTag list) {
        var result = new TreeMap<ResourceLocation, ContractState>(ResourceLocation::compareNamespaced);
        bounded(list);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            putUnique(result, StableId.parse(tag.getString("id")), new ContractState(
                    StableId.parse(tag.getString("skill")), tag.getLong("goal"),
                    tag.getLong("progress"), tag.getLong("epoch"), tag.getBoolean("complete")),
                    "training contract");
        });
        return result;
    }

    private static Map<ResourceLocation, ComboState> readCombos(ListTag list) {
        var result = new TreeMap<ResourceLocation, ComboState>(ResourceLocation::compareNamespaced);
        bounded(list);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            Optional<ResourceLocation> lastAward = tag.contains("last_award", Tag.TAG_STRING)
                    ? Optional.of(StableId.parse(tag.getString("last_award"))) : Optional.empty();
            putUnique(result, StableId.parse(tag.getString("id")), new ComboState(
                    tag.getInt("index"), tag.getLong("last_tick"), tag.getLong("mastery"),
                    tag.getLong("next_ready"), tag.getLong("window_start"),
                    tag.getInt("window_completions"), lastAward, tag.getInt("repeat_count")),
                    "combo mastery");
        });
        return result;
    }

    private static Map<ResourceLocation, LoadoutState> readLoadouts(ListTag list) {
        var result = new TreeMap<ResourceLocation, LoadoutState>(ResourceLocation::compareNamespaced);
        bounded(list);
        list.forEach(value -> {
            CompoundTag tag = (CompoundTag) value;
            putUnique(result, StableId.parse(tag.getString("id")), new LoadoutState(
                    tag.getString("digest"), tag.getString("build_code"),
                    Instant.ofEpochMilli(tag.getLong("saved_at"))), "loadout");
        });
        return result;
    }

    private static LinkedHashSet<UUID> readReceipts(ListTag list) {
        if (list.size() > MAX_SKILL_AWARD_RECEIPTS) {
            throw new IllegalArgumentException("Creator skill award receipt count exceeds capacity");
        }
        var result = new LinkedHashSet<UUID>();
        list.forEach(value -> {
            UUID id = ((CompoundTag) value).getUUID("id");
            if (!result.add(id)) {
                throw new IllegalArgumentException("Creator skill award receipt is duplicated");
            }
        });
        return result;
    }

    private static void bounded(ListTag list) {
        if (list.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Creator state entry count exceeds capacity");
        }
    }

    public synchronized boolean active() {
        return !quarantined;
    }

    public synchronized Optional<String> quarantineReason() {
        return quarantined ? Optional.of(quarantineReason) : Optional.empty();
    }

    private void requireActive() {
        if (quarantined) {
            throw new IllegalStateException("Creator data is quarantined. " + quarantineReason);
        }
    }

    private static Map<ResourceLocation, MilestoneReceipt> legacyReceipts(
            UUID playerId,
            Map<ResourceLocation, ResourceLocation> choices
    ) {
        var result = new TreeMap<ResourceLocation, MilestoneReceipt>(ResourceLocation::compareNamespaced);
        choices.forEach((milestone, choice) -> result.put(milestone, new MilestoneReceipt(
                UUID.nameUUIDFromBytes((playerId + ":" + milestone + ":" + choice)
                        .getBytes(StandardCharsets.UTF_8)), choice, Instant.EPOCH)));
        return result;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static <K, V> void putUnique(Map<K, V> target, K key, V value, String name) {
        if (target.putIfAbsent(Objects.requireNonNull(key, name + " key"),
                Objects.requireNonNull(value, name + " value")) != null) {
            throw new IllegalArgumentException("Creator data contains a duplicate " + name);
        }
    }

    public record PlayerState(
            Map<ResourceLocation, Long> resources,
            Map<ResourceLocation, Integer> prestige,
            Map<ResourceLocation, ResourceLocation> milestoneChoices,
            Map<ResourceLocation, MilestoneReceipt> milestoneReceipts,
            Map<ResourceLocation, ContractState> contracts,
            Map<ResourceLocation, ComboState> combos,
            Map<ResourceLocation, LoadoutState> loadouts,
            Map<ResourceLocation, Long> challenges
    ) {
        public static final PlayerState EMPTY = new PlayerState(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

        public PlayerState {
            resources = immutable(resources);
            prestige = immutable(prestige);
            milestoneChoices = immutable(milestoneChoices);
            milestoneReceipts = immutable(milestoneReceipts);
            contracts = immutable(contracts);
            combos = immutable(combos);
            loadouts = immutable(loadouts);
            challenges = immutable(challenges);
            if (!milestoneChoices.keySet().equals(milestoneReceipts.keySet())) {
                throw new IllegalArgumentException("Creator milestone receipt set is invalid");
            }
            Map<ResourceLocation, MilestoneReceipt> receiptValues = milestoneReceipts;
            milestoneChoices.forEach((milestone, choice) -> {
                if (!receiptValues.get(milestone).choice().equals(choice)) {
                    throw new IllegalArgumentException("Creator milestone receipt choice is invalid");
                }
            });
            if (prestige.values().stream().anyMatch(value -> value < 0)
                    || challenges.values().stream().anyMatch(value -> value < 0)) {
                throw new IllegalArgumentException("Creator progression value is invalid");
            }
        }

        private static <T> Map<ResourceLocation, T> immutable(Map<ResourceLocation, T> values) {
            Objects.requireNonNull(values, "values");
            if (values.size() > MAX_ENTRIES) {
                throw new IllegalArgumentException("Creator state map exceeds capacity");
            }
            var result = new TreeMap<ResourceLocation, T>(ResourceLocation::compareNamespaced);
            values.forEach((id, value) -> result.put(
                    StableId.requireValid(id), Objects.requireNonNull(value, "Creator state value")));
            return Collections.unmodifiableMap(result);
        }

        PlayerState withResource(ResourceLocation id, long value) {
            var next = new TreeMap<>(resources);
            next.put(id, value);
            return new PlayerState(next, prestige, milestoneChoices, milestoneReceipts,
                    contracts, combos, loadouts, challenges);
        }

        PlayerState withPrestige(ResourceLocation id, int value) {
            var next = new TreeMap<>(prestige);
            next.put(id, value);
            return new PlayerState(resources, next, milestoneChoices, milestoneReceipts,
                    contracts, combos, loadouts, challenges);
        }

        PlayerState withChoice(ResourceLocation id, ResourceLocation value, MilestoneReceipt receipt) {
            var next = new TreeMap<>(milestoneChoices);
            next.put(id, value);
            var nextReceipts = new TreeMap<>(milestoneReceipts);
            nextReceipts.put(id, receipt);
            return new PlayerState(resources, prestige, next, nextReceipts,
                    contracts, combos, loadouts, challenges);
        }

        PlayerState withContract(ResourceLocation id, ContractState value) {
            var next = new TreeMap<>(contracts);
            next.put(id, value);
            return new PlayerState(resources, prestige, milestoneChoices, milestoneReceipts,
                    next, combos, loadouts, challenges);
        }

        PlayerState withCombo(ResourceLocation id, ComboState value) {
            var next = new TreeMap<>(combos);
            next.put(id, value);
            return new PlayerState(resources, prestige, milestoneChoices, milestoneReceipts,
                    contracts, next, loadouts, challenges);
        }

        PlayerState withLoadout(ResourceLocation id, LoadoutState value) {
            var next = new TreeMap<>(loadouts);
            next.put(id, value);
            return new PlayerState(resources, prestige, milestoneChoices, milestoneReceipts,
                    contracts, combos, next, challenges);
        }

        PlayerState withChallenge(ResourceLocation id, long value) {
            var next = new TreeMap<>(challenges);
            next.put(id, value);
            return new PlayerState(resources, prestige, milestoneChoices, milestoneReceipts,
                    contracts, combos, loadouts, next);
        }
    }

    public record ContractState(ResourceLocation skill, long goal, long progress, long epoch, boolean complete) {
        public ContractState {
            skill = StableId.requireValid(skill);
            if (goal < 1 || progress < 0 || progress > goal || epoch < 0
                    || complete != (progress >= goal)) {
                throw new IllegalArgumentException("Training contract state is invalid");
            }
        }
    }

    public record ComboState(
            int index,
            long lastTick,
            long mastery,
            long nextReadyTick,
            long windowStartTick,
            int windowCompletions,
            Optional<ResourceLocation> lastAward,
            int repeatCount
    ) {
        public static final ComboState EMPTY = new ComboState(
                0, 0, 0, 0, 0, 0, Optional.empty(), 0);

        public ComboState {
            lastAward = Objects.requireNonNull(lastAward, "lastAward").map(StableId::requireValid);
            if (index < 0 || index > 32 || lastTick < 0 || mastery < 0 || nextReadyTick < 0
                    || windowStartTick < 0 || windowCompletions < 0 || repeatCount < 0
                    || lastAward.isEmpty() && repeatCount != 0) {
                throw new IllegalArgumentException("Combo mastery state is invalid");
            }
        }
    }

    public record MilestoneReceipt(UUID receiptId, ResourceLocation choice, Instant selectedAt) {
        public MilestoneReceipt {
            Objects.requireNonNull(receiptId, "receiptId");
            choice = StableId.requireValid(choice);
            Objects.requireNonNull(selectedAt, "selectedAt");
        }
    }

    public record ResourceReward(
            ResourceLocation resource,
            long amount,
            long minimum,
            long maximum,
            long initial
    ) {
        public ResourceReward {
            resource = StableId.requireValid(resource);
            if (amount < 0 || minimum > initial || initial > maximum) {
                throw new IllegalArgumentException("Creator resource reward is invalid");
            }
        }
    }

    public record ResourceChange(
            ResourceLocation resource,
            long amount,
            long minimum,
            long maximum,
            long initial
    ) {
        public ResourceChange {
            resource = StableId.requireValid(resource);
            if (minimum > initial || initial > maximum) {
                throw new IllegalArgumentException("Creator resource change is invalid");
            }
        }
    }

    public record TimedAwardResult(boolean applied, long cooldownTicks) {
        public TimedAwardResult {
            if (cooldownTicks < 0) {
                throw new IllegalArgumentException("Creator timed award cooldown is invalid");
            }
        }
    }

    public record LoadoutState(String definitionDigest, String buildCode, Instant savedAt) {
        public LoadoutState {
            Objects.requireNonNull(definitionDigest, "definitionDigest");
            Objects.requireNonNull(buildCode, "buildCode");
            Objects.requireNonNull(savedAt, "savedAt");
            if (!definitionDigest.matches("[0-9a-f]{64}") || buildCode.length() > 32768) {
                throw new IllegalArgumentException("Loadout build code exceeds capacity");
            }
            if (!com.envisione.progressiveskills.common.creator.BuildShareCode.decode(buildCode)
                    .definitionDigest().equals(definitionDigest)) {
                throw new IllegalArgumentException("Loadout build code digest does not match");
            }
        }
    }
}
