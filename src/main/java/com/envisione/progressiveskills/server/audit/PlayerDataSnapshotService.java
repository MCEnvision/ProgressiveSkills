package com.envisione.progressiveskills.server.audit;

import com.envisione.progressiveskills.common.data.NbtDataLimits;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsDataSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Atomic, bounded, digest-verified snapshot/export primitive for one loaded attachment. */
public final class PlayerDataSnapshotService {
    public static final int SNAPSHOT_VERSION = 1;
    public static final long MAX_FILE_BYTES = 2L * NbtDataLimits.MAX_ENCODED_BYTES;

    private final Clock clock;

    public PlayerDataSnapshotService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static PlayerDataSnapshotService systemClock() {
        return new PlayerDataSnapshotService(Clock.systemUTC());
    }

    public PlayerDataExportResult snapshot(MinecraftServer server, ProgressiveSkillsData data) throws IOException {
        CompoundTag envelope = envelope(data);
        String digest = envelope.getString("player_data_digest");
        Path target = target(server, data, "snapshots", ".nbt", digest);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.createDirectories(target.getParent());
        NbtIo.writeCompressed(envelope, temporary);
        enforceFileLimit(temporary);
        moveAtomically(temporary, target);
        CompoundTag verified = NbtIo.readCompressed(
                target,
                new NbtAccounter(MAX_FILE_BYTES, NbtDataLimits.MAX_DEPTH + 8)
        );
        verifyEnvelope(verified, digest, data.playerId().toString());
        return new PlayerDataExportResult(target, digest, Files.size(target));
    }

    public PlayerDataExportResult exportReadable(MinecraftServer server, ProgressiveSkillsData data)
            throws IOException {
        CompoundTag envelope = envelope(data);
        String digest = envelope.getString("player_data_digest");
        Path target = target(server, data, "exports", ".snbt", digest);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.createDirectories(target.getParent());
        byte[] content = envelope.toString().getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_FILE_BYTES) {
            throw new IOException("Readable player-data export exceeds " + MAX_FILE_BYTES + " bytes");
        }
        Files.write(temporary, content);
        moveAtomically(temporary, target);
        if (Files.size(target) != content.length) {
            throw new IOException("Readable player-data export failed byte-count verification");
        }
        return new PlayerDataExportResult(target, digest, content.length);
    }

    private CompoundTag envelope(ProgressiveSkillsData data) {
        CompoundTag playerData = ProgressiveSkillsDataSerializer.encode(data);
        String digest = NbtDataLimits.digest(playerData);
        var envelope = new CompoundTag();
        envelope.putInt("snapshot_version", SNAPSHOT_VERSION);
        envelope.putString("player_id", data.playerId().toString());
        envelope.putLong("created_at", Instant.now(clock).toEpochMilli());
        envelope.putString("player_data_digest", digest);
        envelope.put("player_data", playerData);
        return envelope;
    }

    private static Path target(
            MinecraftServer server,
            ProgressiveSkillsData data,
            String kind,
            String suffix,
            String digest
    ) {
        Path root = server.getWorldPath(LevelResource.ROOT)
                .resolve("progressiveskills")
                .resolve(kind)
                .toAbsolutePath()
                .normalize();
        Path playerRoot = root.resolve(data.playerId().toString()).normalize();
        if (!playerRoot.startsWith(root)) {
            throw new IllegalStateException("Player-data export path escaped the world export root");
        }
        String fileName = Long.toUnsignedString(data.view().storageRevision())
                + "-" + digest.substring(0, 16) + suffix;
        return playerRoot.resolve(fileName).normalize();
    }

    private static void verifyEnvelope(CompoundTag envelope, String digest, String playerId) throws IOException {
        if (!envelope.contains("snapshot_version", Tag.TAG_INT)
                || envelope.getInt("snapshot_version") != SNAPSHOT_VERSION
                || !envelope.contains("player_id", Tag.TAG_STRING)
                || !envelope.getString("player_id").equals(playerId)
                || !envelope.contains("player_data", Tag.TAG_COMPOUND)
                || !envelope.contains("player_data_digest", Tag.TAG_STRING)
                || !envelope.getString("player_data_digest").equals(digest)
                || !NbtDataLimits.digest(envelope.getCompound("player_data")).equals(digest)) {
            throw new IOException("Player-data snapshot failed digest or identity verification");
        }
    }

    private static void enforceFileLimit(Path path) throws IOException {
        if (Files.size(path) > MAX_FILE_BYTES) {
            Files.deleteIfExists(path);
            throw new IOException("Compressed player-data snapshot exceeds " + MAX_FILE_BYTES + " bytes");
        }
    }

    private static void moveAtomically(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
