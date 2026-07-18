package com.envisione.progressiveskills.server.hardening;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MassCheckSavedData extends SavedData {
    public static final String FILE_ID = "progressiveskills_mass_checks";
    public static final int DATA_VERSION = 1;
    public static final int MAX_SESSIONS = 256;
    public static final List<String> CHECKS = List.of(
            "Server starts without progression route errors.",
            "Client joins and reaches active synchronization.",
            "Natural block XP awards the expected amount.",
            "Creative placed block XP follows its configured policy.",
            "Survival placed protected blocks do not farm XP.",
            "XP timeout and full XP modes follow configuration.",
            "Manual and custom XP commands are authoritative.",
            "Tree purchases enforce currency and requirements.",
            "Dependent tree refunds use original paid costs.",
            "Class selection enforces weighted slot capacity.",
            "Class swap and respec preserve other grant owners.",
            "Ability assignment uses eight fixed slots.",
            "Ability cooldowns charges costs and targets are authoritative.",
            "Carrier items retain their pinned behavior after reload.",
            "Carrier tampering and duplicate use counters fail closed.",
            "Inventory overflow creates and recovers a pending claim.",
            "Explicit carrier migration shows and confirms a preview.",
            "Progression screen opens every integrated tab.",
            "Tree screen and ability wheel work by keyboard.",
            "HUD high contrast reduced motion and text size remain usable.",
            "Command palette and safe retry tray work after resync.",
            "Compatibility profile reports native and unavailable providers honestly.",
            "Provider health and circuit state are readable.",
            "Doctor reports pack data network archive and providers.",
            "Why explains the latest accepted or rejected decision.",
            "Creator formulas templates and predicates are deterministic.",
            "Milestone choice training contract and combo mastery persist.",
            "Prestige conversion resource and build code operations are bounded.",
            "Party invite join leave and readiness privacy work in chat.",
            "Shared awards produce contribution receipts without private state.",
            "Mentoring transfers and shared challenges require consent.",
            "Season leaderboard privacy and rollover are deterministic.",
            "Studio drafts lint diff history and conflict checks work.",
            "Studio publish rollback and recovery preserve the last known good pack.",
            "Datapack JSON and pspack import reject unsafe paths and oversize input.",
            "Death relog dimension restart and two client replay checks remain correct."
    );
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private boolean quarantined;
    private String quarantineReason = "";

    public static SavedData.Factory<MassCheckSavedData> factory() {
        return new SavedData.Factory<>(MassCheckSavedData::new, MassCheckSavedData::load);
    }

    public static MassCheckSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public synchronized Session start(UUID playerId) {
        requireActive();
        if (!sessions.containsKey(playerId) && sessions.size() >= MAX_SESSIONS) {
            throw new IllegalStateException("Mass check session capacity is full");
        }
        Session session = Session.fresh();
        sessions.put(Objects.requireNonNull(playerId, "playerId"), session);
        setDirty();
        return session;
    }

    public synchronized Optional<Session> session(UUID playerId) {
        requireActive();
        return Optional.ofNullable(sessions.get(playerId));
    }

    public synchronized Session mark(UUID playerId, Result result, String note) {
        requireActive();
        Session current = required(playerId);
        if (current.finished()) {
            throw new IllegalStateException("Mass check is already finished");
        }
        var results = new ArrayList<>(current.results());
        var notes = new ArrayList<>(current.notes());
        results.set(current.index(), Objects.requireNonNull(result, "result"));
        notes.set(current.index(), boundedNote(note));
        int next = Math.min(CHECKS.size() - 1, current.index() + 1);
        Session updated = new Session(current.startedAt(), Instant.now(), next,
                List.copyOf(results), List.copyOf(notes), false);
        sessions.put(playerId, updated);
        setDirty();
        return updated;
    }

    public synchronized Session next(UUID playerId) {
        requireActive();
        Session current = required(playerId);
        int next = Math.min(CHECKS.size() - 1, current.index() + 1);
        Session updated = new Session(current.startedAt(), Instant.now(), next,
                current.results(), current.notes(), current.finished());
        sessions.put(playerId, updated);
        setDirty();
        return updated;
    }

    public synchronized Session finish(UUID playerId) {
        requireActive();
        Session current = required(playerId);
        Session updated = new Session(current.startedAt(), Instant.now(), current.index(),
                current.results(), current.notes(), true);
        sessions.put(playerId, updated);
        setDirty();
        return updated;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag output, HolderLookup.Provider provider) {
        requireActive();
        output.putInt("data_version", DATA_VERSION);
        var values = new ListTag();
        sessions.forEach((playerId, session) -> {
            var tag = new CompoundTag();
            tag.putUUID("player", playerId);
            tag.putLong("started_at", session.startedAt().toEpochMilli());
            tag.putLong("updated_at", session.updatedAt().toEpochMilli());
            tag.putInt("index", session.index());
            tag.putBoolean("finished", session.finished());
            tag.putIntArray("results", session.results().stream().mapToInt(Result::ordinal).toArray());
            var notes = new ListTag();
            session.notes().forEach(note -> {
                var noteTag = new CompoundTag();
                noteTag.putString("value", note);
                notes.add(noteTag);
            });
            tag.put("notes", notes);
            values.add(tag);
        });
        output.put("sessions", values);
        return output;
    }

    static MassCheckSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        var data = new MassCheckSavedData();
        try {
            int version = input.contains("data_version", Tag.TAG_INT)
                    ? input.getInt("data_version") : 0;
            if (version < 0 || version > DATA_VERSION) {
                throw new IllegalArgumentException("Unsupported mass check data version " + version);
            }
            ListTag values = input.contains("sessions", Tag.TAG_LIST)
                    ? input.getList("sessions", Tag.TAG_COMPOUND) : new ListTag();
            if (values.size() > MAX_SESSIONS) {
                throw new IllegalArgumentException("Mass check session count exceeds capacity");
            }
            for (int index = 0; index < values.size(); index++) {
                CompoundTag tag = values.getCompound(index);
                UUID playerId = tag.getUUID("player");
                int current = tag.getInt("index");
                int[] stored = tag.getIntArray("results");
                ListTag noteTags = tag.getList("notes", Tag.TAG_COMPOUND);
                if (stored.length != CHECKS.size() || noteTags.size() != CHECKS.size()) {
                    throw new IllegalArgumentException("Mass check session shape is invalid");
                }
                var results = new ArrayList<Result>();
                var notes = new ArrayList<String>();
                for (int item = 0; item < CHECKS.size(); item++) {
                    if (stored[item] < 0 || stored[item] >= Result.values().length) {
                        throw new IllegalArgumentException("Mass check result is invalid");
                    }
                    results.add(Result.values()[stored[item]]);
                    notes.add(boundedNote(noteTags.getCompound(item).getString("value")));
                }
                Session session = new Session(
                        Instant.ofEpochMilli(tag.getLong("started_at")),
                        Instant.ofEpochMilli(tag.getLong("updated_at")),
                        current,
                        results,
                        notes,
                        tag.getBoolean("finished")
                );
                if (data.sessions.putIfAbsent(playerId, session) != null) {
                    throw new IllegalArgumentException("Mass check data contains a duplicate player");
                }
            }
        } catch (RuntimeException exception) {
            data.sessions.clear();
            data.quarantined = true;
            data.quarantineReason = safeMessage(exception);
        }
        return data;
    }

    public synchronized boolean active() {
        return !quarantined;
    }

    public synchronized Optional<String> quarantineReason() {
        return quarantined ? Optional.of(quarantineReason) : Optional.empty();
    }

    private void requireActive() {
        if (quarantined) {
            throw new IllegalStateException("Mass check data is quarantined. " + quarantineReason);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private Session required(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session == null) {
            throw new IllegalStateException("Mass check has not started");
        }
        return session;
    }

    private static String boundedNote(String note) {
        String value = Objects.requireNonNull(note, "note").strip();
        return value.substring(0, Math.min(512, value.length()));
    }

    public record Session(
            Instant startedAt,
            Instant updatedAt,
            int index,
            List<Result> results,
            List<String> notes,
            boolean finished
    ) {
        public Session {
            Objects.requireNonNull(startedAt, "startedAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(startedAt) || index < 0 || index >= CHECKS.size()) {
                throw new IllegalArgumentException("Mass check index is invalid");
            }
            results = List.copyOf(results);
            notes = List.copyOf(notes);
            if (results.size() != CHECKS.size() || notes.size() != CHECKS.size()) {
                throw new IllegalArgumentException("Mass check session shape is invalid");
            }
        }

        static Session fresh() {
            Instant now = Instant.now();
            return new Session(now, now, 0,
                    Collections.nCopies(CHECKS.size(), Result.PENDING),
                    Collections.nCopies(CHECKS.size(), ""), false);
        }

        public int passed() {
            return Collections.frequency(results, Result.PASS);
        }

        public int failed() {
            return Collections.frequency(results, Result.FAIL);
        }
    }

    public enum Result {
        PENDING,
        PASS,
        FAIL,
        SKIP
    }
}
