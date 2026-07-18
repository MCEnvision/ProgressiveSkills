package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.common.social.CombatContributionPolicy;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PvpAwardLedgerSavedData extends SavedData {
    public static final String FILE_ID = "progressiveskills_pvp_awards";
    public static final int DATA_VERSION = 1;
    public static final int MAX_PAIRS = 65_536;
    private final Map<Pair, PairState> pairs = new LinkedHashMap<>();
    private boolean quarantined;
    private String quarantineReason = "";

    public static SavedData.Factory<PvpAwardLedgerSavedData> factory() {
        return new SavedData.Factory<>(PvpAwardLedgerSavedData::new, PvpAwardLedgerSavedData::load);
    }

    public static PvpAwardLedgerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public synchronized LimitResult limit(
            UUID attacker,
            UUID victim,
            long nowEpochMillis,
            long epochDay,
            long requestedUnits,
            int attackerLevel,
            int victimLevel,
            CombatContributionPolicy policy
    ) {
        requireActive();
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(policy, "policy");
        if (attacker.equals(victim) || nowEpochMillis < 0 || epochDay < 0 || requestedUnits < 0) {
            throw new IllegalArgumentException("Pvp award ledger request is invalid");
        }
        if (!policy.pvpAwardsEnabled() || requestedUnits == 0 || policy.pvpPairDailyCapUnits() == 0) {
            return new LimitResult(0L, requestedUnits, false, true);
        }
        Pair key = new Pair(attacker, victim);
        PairState previous = pairs.get(key);
        if (previous != null && previous.epochDay() > epochDay) {
            quarantine("Pvp award ledger time moved backwards");
            throw new IllegalStateException("Pvp award ledger is quarantined. " + quarantineReason);
        }
        long awardedToday = previous == null || previous.epochDay() != epochDay
                ? 0L : previous.awardedUnits();
        long scaled = policy.applyPvpLevelScaling(requestedUnits, attackerLevel, victimLevel);
        boolean repeated = previous != null && previous.epochDay() == epochDay
                && nowEpochMillis >= previous.lastAwardEpochMillis()
                && nowEpochMillis - previous.lastAwardEpochMillis() < policy.cooldownMillis();
        if (previous != null && nowEpochMillis < previous.lastAwardEpochMillis()) {
            quarantine("Pvp award ledger timestamp moved backwards");
            throw new IllegalStateException("Pvp award ledger is quarantined. " + quarantineReason);
        }
        if (repeated) {
            scaled = BigInteger.valueOf(scaled)
                    .multiply(BigInteger.valueOf(policy.pvpRepeatMultiplierBasisPoints()))
                    .divide(BigInteger.valueOf(10_000L)).longValueExact();
        }
        long remaining = Math.max(0L, policy.pvpPairDailyCapUnits() - awardedToday);
        long allowed = Math.min(scaled, remaining);
        if (allowed > 0) {
            if (previous == null && pairs.size() >= MAX_PAIRS) {
                pairs.entrySet().removeIf(entry -> entry.getValue().epochDay() < epochDay);
            }
            if (previous == null && pairs.size() >= MAX_PAIRS) {
                throw new IllegalStateException("Pvp award ledger capacity is full");
            }
            pairs.put(key, new PairState(epochDay, nowEpochMillis,
                    Math.addExact(awardedToday, allowed)));
            setDirty();
        }
        return new LimitResult(allowed, requestedUnits - allowed, repeated, allowed == remaining);
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
        var entries = new ListTag();
        pairs.forEach((pair, state) -> {
            var tag = new CompoundTag();
            tag.putUUID("attacker", pair.attacker());
            tag.putUUID("victim", pair.victim());
            tag.putLong("epoch_day", state.epochDay());
            tag.putLong("last_award", state.lastAwardEpochMillis());
            tag.putLong("awarded", state.awardedUnits());
            entries.add(tag);
        });
        output.put("pairs", entries);
        return output;
    }

    static PvpAwardLedgerSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        var data = new PvpAwardLedgerSavedData();
        try {
            int version = input.contains("data_version", Tag.TAG_INT) ? input.getInt("data_version") : 0;
            if (version < 0 || version > DATA_VERSION) {
                throw new IllegalArgumentException("Unsupported pvp award ledger version " + version);
            }
            ListTag entries = input.getList("pairs", Tag.TAG_COMPOUND);
            if (entries.size() > MAX_PAIRS) {
                throw new IllegalArgumentException("Pvp award ledger exceeds capacity");
            }
            entries.forEach(value -> {
                CompoundTag tag = (CompoundTag) value;
                Pair pair = new Pair(tag.getUUID("attacker"), tag.getUUID("victim"));
                PairState state = new PairState(
                        tag.getLong("epoch_day"), tag.getLong("last_award"), tag.getLong("awarded"));
                if (data.pairs.putIfAbsent(pair, state) != null) {
                    throw new IllegalArgumentException("Pvp award ledger contains a duplicate pair");
                }
            });
        } catch (RuntimeException exception) {
            data.quarantine(safeMessage(exception));
        }
        return data;
    }

    private void requireActive() {
        if (quarantined) {
            throw new IllegalStateException("Pvp award ledger is quarantined. " + quarantineReason);
        }
    }

    private void quarantine(String reason) {
        pairs.clear();
        quarantined = true;
        quarantineReason = reason == null || reason.isBlank() ? "Unknown pvp award ledger failure" : reason;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record Pair(UUID attacker, UUID victim) {
        private Pair {
            Objects.requireNonNull(attacker, "attacker");
            Objects.requireNonNull(victim, "victim");
            if (attacker.equals(victim)) {
                throw new IllegalArgumentException("Pvp award pair is invalid");
            }
        }
    }

    private record PairState(long epochDay, long lastAwardEpochMillis, long awardedUnits) {
        private PairState {
            if (epochDay < 0 || lastAwardEpochMillis < 0 || awardedUnits < 0) {
                throw new IllegalArgumentException("Pvp award pair state is invalid");
            }
        }
    }

    public record LimitResult(long allowedUnits, long deniedUnits, boolean repeated, boolean capped) {
        public LimitResult {
            if (allowedUnits < 0 || deniedUnits < 0) {
                throw new IllegalArgumentException("Pvp award limit result is invalid");
            }
        }
    }
}
