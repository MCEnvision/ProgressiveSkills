package com.envisione.progressiveskills.server.rule;

import com.envisione.progressiveskills.common.data.NbtDataLimits;
import com.envisione.progressiveskills.common.rule.BlockOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class BlockProvenanceSavedData extends SavedData {
    public static final String FILE_ID = "progressiveskills_block_provenance";
    public static final int DATA_VERSION = 1;
    public static final int MAX_TRACKED_BLOCKS = 90_000;
    public static final int MAX_DIMENSIONS = 2_048;
    private static final int MAX_ISSUE_LENGTH = 256;

    private final Map<ResourceLocation, Map<Long, BlockOrigin>> dimensions = new HashMap<>();
    private final int capacity;
    private int trackedCount;
    private boolean reliable = true;
    private Optional<String> issue = Optional.empty();

    public BlockProvenanceSavedData() {
        this(MAX_TRACKED_BLOCKS);
    }

    BlockProvenanceSavedData(int capacity) {
        if (capacity < 1 || capacity > MAX_TRACKED_BLOCKS) {
            throw new IllegalArgumentException("Block provenance capacity is outside its bound");
        }
        this.capacity = capacity;
    }

    public static SavedData.Factory<BlockProvenanceSavedData> factory() {
        return new SavedData.Factory<>(BlockProvenanceSavedData::new, BlockProvenanceSavedData::load);
    }

    public static BlockProvenanceSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public synchronized void recordPlacement(
            ResourceLocation dimension,
            BlockPos position,
            BlockOrigin origin
    ) {
        put(dimension, position.asLong(), origin);
    }

    public synchronized BlockOrigin originAt(ResourceLocation dimension, BlockPos position) {
        return storedOrigin(dimension, position.asLong());
    }

    public synchronized BlockOrigin consume(ResourceLocation dimension, BlockPos position) {
        long packed = position.asLong();
        BlockOrigin result = storedOrigin(dimension, packed);
        Map<Long, BlockOrigin> entries = dimensions.get(dimension);
        if (entries != null && entries.remove(packed) != null) {
            trackedCount--;
            if (entries.isEmpty()) {
                dimensions.remove(dimension);
            }
            setDirty();
        }
        return result;
    }

    public synchronized void move(
            ResourceLocation dimension,
            List<BlockPos> sources,
            List<BlockPos> destroyed,
            Direction direction
    ) {
        var moved = new ArrayList<MovedBlock>(sources.size());
        for (BlockPos source : sources) {
            moved.add(new MovedBlock(source.relative(direction).asLong(), storedOrigin(dimension, source.asLong())));
        }
        for (BlockPos source : sources) {
            remove(dimension, source.asLong());
        }
        for (BlockPos position : destroyed) {
            remove(dimension, position.asLong());
        }
        for (MovedBlock block : moved) {
            put(dimension, block.destination(), block.origin());
        }
    }

    public synchronized void markUnreliable(String reason) {
        reliable = false;
        if (issue.isEmpty()) {
            issue = Optional.of(boundedReason(reason));
        }
        setDirty();
    }

    public synchronized boolean reliable() {
        return reliable;
    }

    public synchronized int trackedCount() {
        return trackedCount;
    }

    public synchronized Optional<String> issue() {
        return issue;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag output, HolderLookup.Provider provider) {
        output.putInt("data_version", DATA_VERSION);
        output.putBoolean("reliable", reliable);
        issue.ifPresent(value -> output.putString("issue", value));
        var dimensionTags = new ListTag();
        new TreeMap<>(dimensions).forEach((dimension, entries) -> {
            var tag = new CompoundTag();
            tag.putString("id", dimension.toString());
            for (BlockOrigin origin : BlockOrigin.values()) {
                if (origin == BlockOrigin.NATURAL) {
                    continue;
                }
                long[] positions = entries.entrySet().stream()
                        .filter(entry -> entry.getValue() == origin)
                        .mapToLong(Map.Entry::getKey)
                        .sorted()
                        .toArray();
                if (positions.length > 0) {
                    tag.putLongArray(origin.serializedName(), positions);
                }
            }
            dimensionTags.add(tag);
        });
        output.put("dimensions", dimensionTags);
        NbtDataLimits.rejection(output).ifPresent(reason -> {
            throw new IllegalStateException("Refusing to serialize oversized block provenance data " + reason);
        });
        return output;
    }

    static BlockProvenanceSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        var result = new BlockProvenanceSavedData();
        Optional<String> rejection = NbtDataLimits.rejection(input);
        if (rejection.isPresent()) {
            result.markUnreliable(rejection.orElseThrow());
            return result;
        }
        try {
            if (!input.contains("data_version", Tag.TAG_INT) || input.getInt("data_version") != DATA_VERSION) {
                throw new IllegalArgumentException("Unsupported block provenance data version");
            }
            if (!input.contains("reliable", Tag.TAG_BYTE)) {
                throw new IllegalArgumentException("Missing block provenance reliability field");
            }
            result.reliable = input.getBoolean("reliable");
            if (input.contains("issue", Tag.TAG_STRING)) {
                result.issue = Optional.of(boundedReason(input.getString("issue")));
            }
            ListTag dimensions = input.contains("dimensions", Tag.TAG_LIST)
                    ? input.getList("dimensions", Tag.TAG_COMPOUND) : new ListTag();
            if (dimensions.size() > MAX_DIMENSIONS) {
                throw new IllegalArgumentException("Block provenance dimension count exceeds capacity");
            }
            for (int index = 0; index < dimensions.size(); index++) {
                if (!(dimensions.get(index) instanceof CompoundTag tag)) {
                    throw new IllegalArgumentException("Block provenance dimensions contain a non compound entry");
                }
                ResourceLocation dimension = ResourceLocation.tryParse(requiredString(tag, "id"));
                if (dimension == null || result.dimensions.containsKey(dimension)) {
                    throw new IllegalArgumentException("Invalid or duplicate block provenance dimension");
                }
                var entries = new HashMap<Long, BlockOrigin>();
                for (BlockOrigin origin : BlockOrigin.values()) {
                    if (origin == BlockOrigin.NATURAL || !tag.contains(origin.serializedName(), Tag.TAG_LONG_ARRAY)) {
                        continue;
                    }
                    for (long position : tag.getLongArray(origin.serializedName())) {
                        if (entries.putIfAbsent(position, origin) != null) {
                            throw new IllegalArgumentException("Duplicate block provenance position");
                        }
                        result.trackedCount++;
                        if (result.trackedCount > result.capacity) {
                            throw new IllegalArgumentException("Block provenance count exceeds capacity");
                        }
                    }
                }
                if (!entries.isEmpty()) {
                    result.dimensions.put(dimension, entries);
                }
            }
        } catch (RuntimeException exception) {
            result.dimensions.clear();
            result.trackedCount = 0;
            result.reliable = false;
            result.issue = Optional.of(boundedReason(exception.getMessage()));
            result.setDirty();
        }
        return result;
    }

    private BlockOrigin storedOrigin(ResourceLocation dimension, long position) {
        Map<Long, BlockOrigin> entries = dimensions.get(dimension);
        BlockOrigin stored = entries == null ? null : entries.get(position);
        return stored == null ? reliable ? BlockOrigin.NATURAL : BlockOrigin.UNKNOWN : stored;
    }

    private void put(ResourceLocation dimension, long position, BlockOrigin origin) {
        if (origin == BlockOrigin.NATURAL) {
            remove(dimension, position);
            return;
        }
        Map<Long, BlockOrigin> entries = dimensions.get(dimension);
        if (entries == null) {
            if (dimensions.size() >= MAX_DIMENSIONS) {
                markUnreliable("Block provenance dimension capacity is full");
                return;
            }
            entries = new HashMap<>();
            dimensions.put(dimension, entries);
        }
        BlockOrigin existing = entries.get(position);
        if (existing == origin) {
            return;
        }
        if (existing == null && trackedCount >= capacity) {
            if (entries.isEmpty()) {
                dimensions.remove(dimension);
            }
            markUnreliable("Block provenance capacity is full");
            return;
        }
        entries.put(position, origin);
        if (existing == null) {
            trackedCount++;
        }
        setDirty();
    }

    private void remove(ResourceLocation dimension, long position) {
        Map<Long, BlockOrigin> entries = dimensions.get(dimension);
        if (entries == null || entries.remove(position) == null) {
            return;
        }
        trackedCount--;
        if (entries.isEmpty()) {
            dimensions.remove(dimension);
        }
        setDirty();
    }

    private static String requiredString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing block provenance string field " + key);
        }
        return tag.getString(key);
    }

    private static String boundedReason(String reason) {
        String value = reason == null || reason.isBlank() ? "Unknown block provenance failure" : reason;
        return value.substring(0, Math.min(value.length(), MAX_ISSUE_LENGTH));
    }

    private record MovedBlock(long destination, BlockOrigin origin) {
    }
}
