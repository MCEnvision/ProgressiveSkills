package com.envisione.progressiveskills.server.carrier;

import com.envisione.progressiveskills.common.carrier.CarrierBehaviorCodec;
import com.envisione.progressiveskills.common.carrier.CarrierBehaviorSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class BehaviorArchiveSavedData extends SavedData {
    public static final String FILE_ID = "progressiveskills_carrier_behaviors";
    public static final int DATA_VERSION = 1;
    public static final int MAX_ENTRIES = 4_096;
    public static final int MAX_ENTRY_BYTES = 65_536;
    public static final int MAX_TOTAL_BYTES = 16_777_216;
    private static final int MAX_ISSUE_LENGTH = 256;

    private final Map<String, ArchivedBehavior> entries = new TreeMap<>();
    private final int entryCapacity;
    private final int byteCapacity;
    private int totalBytes;
    private Optional<String> quarantine = Optional.empty();

    public BehaviorArchiveSavedData() {
        this(MAX_ENTRIES, MAX_TOTAL_BYTES);
    }

    BehaviorArchiveSavedData(int entryCapacity, int byteCapacity) {
        if (entryCapacity < 1 || entryCapacity > MAX_ENTRIES) {
            throw new IllegalArgumentException("Behavior archive entry capacity is outside its bound");
        }
        if (byteCapacity < 1 || byteCapacity > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("Behavior archive byte capacity is outside its bound");
        }
        this.entryCapacity = entryCapacity;
        this.byteCapacity = byteCapacity;
    }

    public static SavedData.Factory<BehaviorArchiveSavedData> factory() {
        return new SavedData.Factory<>(BehaviorArchiveSavedData::new, BehaviorArchiveSavedData::load);
    }

    public static BehaviorArchiveSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public synchronized Reservation previewReserve(Collection<CarrierBehaviorSnapshot> snapshots) {
        return reservation(snapshots, false);
    }

    public synchronized Reservation reserve(Collection<CarrierBehaviorSnapshot> snapshots) {
        return reservation(snapshots, true);
    }

    public synchronized Optional<CarrierBehaviorSnapshot> resolve(String digest) {
        requireDigest(digest);
        if (quarantine.isPresent()) {
            return Optional.empty();
        }
        ArchivedBehavior archived = entries.get(digest);
        return archived == null ? Optional.empty() : Optional.of(archived.snapshot());
    }

    public synchronized boolean contains(String digest) {
        requireDigest(digest);
        return quarantine.isEmpty() && entries.containsKey(digest);
    }

    public synchronized Status status() {
        return new Status(entries.size(), totalBytes, entryCapacity, byteCapacity, quarantine);
    }

    public synchronized Verification verify() {
        if (quarantine.isPresent()) {
            return new Verification(false, entries.size(), quarantine);
        }
        try {
            for (ArchivedBehavior archived : entries.values()) {
                validateEntry(archived.digest(), archived.checksum(), archived.payload());
            }
            return new Verification(true, entries.size(), Optional.empty());
        } catch (RuntimeException exception) {
            return new Verification(false, entries.size(), Optional.of(boundedReason(exception)));
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag output, HolderLookup.Provider provider) {
        output.putInt("data_version", DATA_VERSION);
        quarantine.ifPresent(value -> output.putString("quarantine", value));
        var list = new ListTag();
        for (ArchivedBehavior archived : entries.values()) {
            var tag = new CompoundTag();
            tag.putString("digest", archived.digest());
            tag.putString("checksum", archived.checksum());
            tag.putByteArray("payload", archived.payload());
            list.add(tag);
        }
        output.put("entries", list);
        return output;
    }

    static BehaviorArchiveSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        var result = new BehaviorArchiveSavedData();
        try {
            requireOnlyKeys(input, Set.of("data_version", "quarantine", "entries"));
            if (!input.contains("data_version", Tag.TAG_INT) || input.getInt("data_version") != DATA_VERSION) {
                throw new IllegalArgumentException("Unsupported behavior archive data version");
            }
            if (input.contains("quarantine") && !input.contains("quarantine", Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Behavior archive quarantine field has the wrong type");
            }
            if (input.contains("quarantine", Tag.TAG_STRING)) {
                String reason = input.getString("quarantine");
                if (reason.length() > MAX_ISSUE_LENGTH) {
                    throw new IllegalArgumentException("Behavior archive quarantine reason exceeds its bound");
                }
                result.quarantine = Optional.of(boundedReason(reason));
            }
            if (input.contains("entries") && !input.contains("entries", Tag.TAG_LIST)) {
                throw new IllegalArgumentException("Behavior archive entries field has the wrong type");
            }
            ListTag list = input.contains("entries", Tag.TAG_LIST)
                    ? input.getList("entries", Tag.TAG_COMPOUND) : new ListTag();
            if (list.size() > MAX_ENTRIES) {
                throw new IllegalArgumentException("Behavior archive entry count exceeds capacity");
            }
            for (int index = 0; index < list.size(); index++) {
                if (!(list.get(index) instanceof CompoundTag tag)) {
                    throw new IllegalArgumentException("Behavior archive contains a non compound entry");
                }
                requireOnlyKeys(tag, Set.of("digest", "checksum", "payload"));
                String digest = requiredString(tag, "digest");
                String checksum = requiredString(tag, "checksum");
                if (!tag.contains("payload", Tag.TAG_BYTE_ARRAY)) {
                    throw new IllegalArgumentException("Behavior archive entry is missing its payload");
                }
                byte[] payload = tag.getByteArray("payload");
                ArchivedBehavior archived = validateEntry(digest, checksum, payload);
                if (result.entries.putIfAbsent(digest, archived) != null) {
                    throw new IllegalArgumentException("Behavior archive contains a duplicate digest");
                }
                result.totalBytes = Math.addExact(result.totalBytes, payload.length);
                if (result.totalBytes > result.byteCapacity) {
                    throw new IllegalArgumentException("Behavior archive byte count exceeds capacity");
                }
            }
            if (result.quarantine.isPresent() && !result.entries.isEmpty()) {
                throw new IllegalArgumentException("Quarantined behavior archive must not retain entries");
            }
        } catch (RuntimeException exception) {
            result.entries.clear();
            result.totalBytes = 0;
            result.quarantine(exception);
        }
        return result;
    }

    private Reservation reservation(Collection<CarrierBehaviorSnapshot> snapshots, boolean apply) {
        Objects.requireNonNull(snapshots, "snapshots");
        if (quarantine.isPresent()) {
            throw new IllegalStateException("Behavior archive is quarantined");
        }
        var additions = new TreeMap<String, ArchivedBehavior>();
        for (CarrierBehaviorSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "snapshot");
            byte[] payload = CarrierBehaviorCodec.encodeSnapshot(snapshot);
            String digest = CarrierBehaviorCodec.digest(snapshot);
            ArchivedBehavior candidate = validateEntry(digest, checksum(payload), payload);
            ArchivedBehavior existing = entries.get(digest);
            if (existing != null && !Arrays.equals(existing.payload(), candidate.payload())) {
                throw new IllegalStateException("Behavior archive digest collision");
            }
            ArchivedBehavior pending = additions.putIfAbsent(digest, candidate);
            if (pending != null && !Arrays.equals(pending.payload(), candidate.payload())) {
                throw new IllegalStateException("Behavior reservation digest collision");
            }
        }
        additions.keySet().removeAll(entries.keySet());
        int addedBytes = 0;
        for (ArchivedBehavior addition : additions.values()) {
            addedBytes = Math.addExact(addedBytes, addition.payload().length);
        }
        int resultingEntries = Math.addExact(entries.size(), additions.size());
        int resultingBytes = Math.addExact(totalBytes, addedBytes);
        if (resultingEntries > entryCapacity) {
            throw new IllegalStateException("Behavior archive entry capacity is full");
        }
        if (resultingBytes > byteCapacity) {
            throw new IllegalStateException("Behavior archive byte capacity is full");
        }
        if (apply && !additions.isEmpty()) {
            entries.putAll(additions);
            totalBytes = resultingBytes;
            setDirty();
        }
        return new Reservation(additions.size(), addedBytes, resultingEntries, resultingBytes);
    }

    private void quarantine(Object reason) {
        entries.clear();
        totalBytes = 0;
        quarantine = Optional.of(boundedReason(reason));
        setDirty();
    }

    private static ArchivedBehavior validateEntry(String digest, String checksum, byte[] payload) {
        requireDigest(digest);
        requireDigest(checksum);
        Objects.requireNonNull(payload, "payload");
        if (payload.length < 1 || payload.length > MAX_ENTRY_BYTES) {
            throw new IllegalArgumentException("Behavior archive payload is outside its bound");
        }
        byte[] stored = payload.clone();
        String actualChecksum = checksum(stored);
        if (!actualChecksum.equals(checksum)) {
            throw new IllegalArgumentException("Behavior archive checksum does not match its payload");
        }
        CarrierBehaviorSnapshot snapshot = CarrierBehaviorCodec.decodeSnapshot(stored);
        if (!CarrierBehaviorCodec.digest(snapshot).equals(digest)) {
            throw new IllegalArgumentException("Behavior archive digest does not match its snapshot");
        }
        byte[] canonical = CarrierBehaviorCodec.encodeSnapshot(snapshot);
        if (!Arrays.equals(stored, canonical)) {
            throw new IllegalArgumentException("Behavior archive payload is not canonical");
        }
        return new ArchivedBehavior(digest, checksum, stored, snapshot);
    }

    private static void requireDigest(String digest) {
        Objects.requireNonNull(digest, "digest");
        if (digest.length() != 64) {
            throw new IllegalArgumentException("Behavior digest must contain 64 hexadecimal characters");
        }
        for (int index = 0; index < digest.length(); index++) {
            char value = digest.charAt(index);
            if ((value < '0' || value > '9') && (value < 'a' || value > 'f')) {
                throw new IllegalArgumentException("Behavior digest must use lowercase hexadecimal characters");
            }
        }
    }

    private static String requiredString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Behavior archive is missing string field " + key);
        }
        return tag.getString(key);
    }

    private static void requireOnlyKeys(CompoundTag tag, Set<String> allowed) {
        if (!allowed.containsAll(tag.getAllKeys())) {
            throw new IllegalArgumentException("Behavior archive contains an unknown field");
        }
    }

    private static String checksum(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static String boundedReason(Object reason) {
        String value;
        if (reason instanceof Throwable throwable) {
            value = throwable.getMessage();
        } else {
            value = Objects.toString(reason, "");
        }
        if (value == null || value.isBlank()) {
            value = "Unknown behavior archive failure";
        }
        return value.substring(0, Math.min(value.length(), MAX_ISSUE_LENGTH));
    }

    private record ArchivedBehavior(
            String digest,
            String checksum,
            byte[] payload,
            CarrierBehaviorSnapshot snapshot
    ) {
        private ArchivedBehavior {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    public record Reservation(int addedEntries, int addedBytes, int resultingEntries, int resultingBytes) {
        public Reservation {
            if (addedEntries < 0 || addedBytes < 0 || resultingEntries < 0 || resultingBytes < 0) {
                throw new IllegalArgumentException("Behavior archive reservation values must not be negative");
            }
        }
    }

    public record Status(
            int entries,
            int bytes,
            int entryCapacity,
            int byteCapacity,
            Optional<String> quarantine
    ) {
        public Status {
            quarantine = Objects.requireNonNull(quarantine, "quarantine");
        }

        public boolean reliable() {
            return quarantine.isEmpty();
        }
    }

    public record Verification(boolean valid, int checkedEntries, Optional<String> issue) {
        public Verification {
            if (checkedEntries < 0) {
                throw new IllegalArgumentException("Behavior archive checked entry count must not be negative");
            }
            issue = Objects.requireNonNull(issue, "issue");
        }
    }
}
