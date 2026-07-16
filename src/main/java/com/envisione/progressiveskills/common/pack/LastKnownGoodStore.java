package com.envisione.progressiveskills.common.pack;

import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;

/** Checksummed two-generation source-bundle journal for safe startup recovery. */
public final class LastKnownGoodStore {
    private static final int MAGIC = 0x50534C4B;
    private static final int FORMAT = 2;
    private static final long MAX_LOCK_BYTES = 4L * 1024L * 1024L;
    private static final String CURRENT_BUNDLE = "progressiveskills.sources";
    private static final String CURRENT_LOCK = "progressiveskills.lock";
    private static final String PREVIOUS_BUNDLE = "progressiveskills.previous.sources";
    private static final String PREVIOUS_LOCK = "progressiveskills.previous.lock";
    private final Path directory;

    public LastKnownGoodStore(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    public synchronized StoredPackBundle save(long generation, PackSnapshot snapshot) throws IOException {
        return save(generation, snapshot, AvailableEnvironment.empty());
    }

    public synchronized StoredPackBundle save(
            long generation,
            PackSnapshot snapshot,
            AvailableEnvironment environment
    ) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(environment, "environment");
        if (generation < 1) {
            throw new IllegalArgumentException("Generation must be positive");
        }
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("Last-known-good directory cannot be a symbolic link: " + directory);
        }
        byte[] bundleBytes = encode(snapshot.sourceBundle());
        String bundleDigest = sha256(bundleBytes);
        Instant createdAt = Instant.now();
        PackLockMetadata metadata = PackLockMetadata.capture(environment, snapshot);
        byte[] lockBytes = lockBytes(generation, snapshot, metadata, bundleDigest, createdAt);
        if (lockBytes.length > MAX_LOCK_BYTES) {
            throw new IOException("Generated lock metadata exceeds " + MAX_LOCK_BYTES + " bytes");
        }

        Path incomingBundle = directory.resolve("incoming.sources.tmp");
        Path incomingLock = directory.resolve("incoming.lock.tmp");
        writeForced(incomingBundle, bundleBytes);
        writeForced(incomingLock, lockBytes);
        verifyPair(incomingBundle, incomingLock);

        snapshotCurrentAsPrevious();
        atomicMove(incomingBundle, directory.resolve(CURRENT_BUNDLE));
        atomicMove(incomingLock, directory.resolve(CURRENT_LOCK));
        forceDirectory(directory);
        return new StoredPackBundle(
                generation,
                snapshot.contentDigest(),
                bundleDigest,
                createdAt,
                metadata,
                snapshot.sourceBundle()
        );
    }

    public synchronized Optional<StoredPackBundle> loadCurrent() throws IOException {
        return load("current");
    }

    public synchronized Optional<StoredPackBundle> loadPrevious() throws IOException {
        return load("previous");
    }

    public List<PackRoot> materialize(StoredPackBundle stored, Path targetDirectory) throws IOException {
        Objects.requireNonNull(stored, "stored");
        Path target = Objects.requireNonNull(targetDirectory, "targetDirectory").toAbsolutePath().normalize();
        deleteOwnedTree(target);
        Files.createDirectories(target);
        var roots = new TreeMap<String, PackRoot>();
        for (var entry : stored.sourceBundle().files().entrySet()) {
            String[] segments = entry.getKey().split("/", 4);
            if (segments.length != 4) {
                throw new IOException("Invalid stored bundle path: " + entry.getKey());
            }
            PackRootTier tier;
            try {
                tier = PackRootTier.valueOf(segments[0].toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Unknown stored root tier: " + segments[0], exception);
            }
            String rootName = segments[1];
            Path rootPath = target.resolve(segments[0]).resolve(rootName).normalize();
            if (!rootPath.startsWith(target)) {
                throw new IOException("Stored root escapes recovery directory");
            }
            roots.putIfAbsent(segments[0] + "/" + rootName, new PackRoot(tier, rootName, rootPath));
            Path output = rootPath.resolve(segments[2]).resolve(segments[3]).normalize();
            if (!output.startsWith(rootPath)) {
                throw new IOException("Stored source escapes recovery root: " + entry.getKey());
            }
            Files.createDirectories(output.getParent());
            Files.write(output, entry.getValue(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        return List.copyOf(roots.values());
    }

    private Optional<StoredPackBundle> load(String name) throws IOException {
        Path bundle = directory.resolve(name.equals("current") ? CURRENT_BUNDLE : PREVIOUS_BUNDLE);
        Path lock = directory.resolve(name.equals("current") ? CURRENT_LOCK : PREVIOUS_LOCK);
        if (!Files.exists(bundle, LinkOption.NOFOLLOW_LINKS) && !Files.exists(lock, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        return Optional.of(verifyPair(bundle, lock));
    }

    private static StoredPackBundle verifyPair(Path bundlePath, Path lockPath) throws IOException {
        if (Files.isSymbolicLink(bundlePath) || Files.isSymbolicLink(lockPath)
                || !Files.isRegularFile(bundlePath, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Last-known-good journal pair is incomplete or symbolic");
        }
        long maximumEncodedBytes = SourceBundle.MAX_TOTAL_BYTES + (2_056L * SourceBundle.MAX_FILES) + 16L;
        if (Files.size(bundlePath) > maximumEncodedBytes) {
            throw new IOException("Stored source bundle exceeds its encoded size ceiling");
        }
        if (Files.size(lockPath) > MAX_LOCK_BYTES) {
            throw new IOException("Stored lock metadata exceeds " + MAX_LOCK_BYTES + " bytes");
        }
        byte[] bundleBytes = Files.readAllBytes(bundlePath);
        Properties lock = new Properties();
        try {
            lock.load(new StringReader(decodeUtf8(Files.readAllBytes(lockPath), "lock metadata")));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid last-known-good lock encoding", exception);
        }
        if (!Integer.toString(FORMAT).equals(lock.getProperty("format"))) {
            throw new IOException("Unsupported last-known-good lock format");
        }
        String expectedBundleDigest = requireDigest(lock, "bundle_sha256");
        String actualBundleDigest = sha256(bundleBytes);
        if (!expectedBundleDigest.equals(actualBundleDigest)) {
            throw new IOException("Last-known-good bundle checksum mismatch");
        }
        try {
            SourceBundle sourceBundle = decode(bundleBytes);
            String expectedSourceDigest = requireDigest(lock, "source_bundle_digest");
            if (!expectedSourceDigest.equals(sourceBundle.digest())) {
                throw new IOException("Last-known-good canonical source digest mismatch");
            }
            PackLockMetadata metadata = parseMetadata(lock);
            return new StoredPackBundle(
                    Long.parseLong(require(lock, "generation")),
                    requireDigest(lock, "content_digest"),
                    actualBundleDigest,
                    Instant.parse(require(lock, "created_utc")),
                    metadata,
                    sourceBundle
            );
        } catch (RuntimeException exception) {
            throw new IOException("Invalid last-known-good lock metadata", exception);
        }
    }

    private static byte[] encode(SourceBundle bundle) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT);
            output.writeInt(bundle.files().size());
            for (var entry : bundle.files().entrySet()) {
                writeBytes(output, entry.getKey().getBytes(StandardCharsets.UTF_8));
                writeBytes(output, entry.getValue());
            }
        }
        return bytes.toByteArray();
    }

    private static SourceBundle decode(byte[] encoded) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC || input.readInt() != FORMAT) {
                throw new IOException("Unsupported last-known-good source bundle");
            }
            int count = input.readInt();
            if (count < 0 || count > SourceBundle.MAX_FILES) {
                throw new IOException("Invalid stored source file count: " + count);
            }
            var files = new LinkedHashMap<String, byte[]>();
            long total = 0;
            for (int index = 0; index < count; index++) {
                String path = decodeUtf8(readBytes(input, 2_048), "stored source path");
                byte[] value = readBytes(input, SourceBundle.MAX_FILE_BYTES);
                total = Math.addExact(total, value.length);
                if (total > SourceBundle.MAX_TOTAL_BYTES || files.putIfAbsent(path, value) != null) {
                    throw new IOException("Invalid or duplicate stored source path: " + path);
                }
            }
            if (input.read() != -1) {
                throw new IOException("Trailing bytes in stored source bundle");
            }
            return SourceBundle.of(files);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            throw new IOException("Invalid stored source bundle", exception);
        }
    }

    private static byte[] lockBytes(
            long generation,
            PackSnapshot snapshot,
            PackLockMetadata metadata,
            String bundleDigest,
            Instant createdAt
    ) {
        var value = new StringBuilder()
                .append("format=").append(FORMAT).append('\n')
                .append("generation=").append(generation).append('\n')
                .append("schema_version=").append(metadata.schemaVersion()).append('\n')
                .append("engine_version=").append(metadata.engineVersion()).append('\n')
                .append("content_digest=").append(snapshot.contentDigest()).append('\n')
                .append("source_bundle_digest=").append(snapshot.sourceBundle().digest()).append('\n')
                .append("bundle_sha256=").append(bundleDigest).append('\n')
                .append("created_utc=").append(createdAt).append('\n')
                .append("definition_count=").append(snapshot.canonicalIr().definitions().size()).append('\n')
                .append("pack_count=").append(metadata.packVersions().size()).append('\n');
        int packIndex = 0;
        for (var entry : metadata.packVersions().entrySet()) {
            value.append("pack.").append(packIndex).append(".id=").append(entry.getKey()).append('\n')
                    .append("pack.").append(packIndex).append(".version=").append(entry.getValue()).append('\n')
                    .append("pack.").append(packIndex).append(".source_sha256=")
                    .append(metadata.packSourceDigests().get(entry.getKey())).append('\n');
            packIndex++;
        }
        value.append("mod_count=").append(metadata.modVersions().size()).append('\n');
        int modIndex = 0;
        for (var entry : metadata.modVersions().entrySet()) {
            value.append("mod.").append(modIndex).append(".id=").append(entry.getKey()).append('\n')
                    .append("mod.").append(modIndex).append(".version_base64=")
                    .append(Base64.getUrlEncoder().withoutPadding().encodeToString(
                            entry.getValue().getBytes(StandardCharsets.UTF_8)
                    )).append('\n');
            modIndex++;
        }
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static PackLockMetadata parseMetadata(Properties lock) throws IOException {
        int schemaVersion = requireInt(lock, "schema_version", 2, 2);
        SemanticVersion engineVersion = SemanticVersion.parse(require(lock, "engine_version"));
        int packCount = requireInt(lock, "pack_count", 0, ContentPackDiscoverer.MAX_PACKS);
        var packVersions = new TreeMap<ResourceLocation, SemanticVersion>(ResourceLocation::compareNamespaced);
        var sourceDigests = new TreeMap<ResourceLocation, String>(ResourceLocation::compareNamespaced);
        for (int index = 0; index < packCount; index++) {
            ResourceLocation id = com.envisione.progressiveskills.common.id.StableId.parse(
                    require(lock, "pack." + index + ".id")
            );
            if (packVersions.putIfAbsent(
                    id,
                    SemanticVersion.parse(require(lock, "pack." + index + ".version"))
            ) != null) {
                throw new IOException("Duplicate pack id in last-known-good lock: " + id);
            }
            sourceDigests.put(id, requireDigest(lock, "pack." + index + ".source_sha256"));
        }
        int modCount = requireInt(lock, "mod_count", 0, AvailableEnvironment.MAX_MODS);
        var mods = new TreeMap<String, String>();
        for (int index = 0; index < modCount; index++) {
            String id = AvailableEnvironment.requireModId(require(lock, "mod." + index + ".id"));
            String encodedVersion = require(lock, "mod." + index + ".version_base64", 512);
            String version;
            try {
                version = decodeUtf8(Base64.getUrlDecoder().decode(encodedVersion), "locked mod version");
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid base64 mod version in last-known-good lock: " + id, exception);
            }
            if (mods.putIfAbsent(id, AvailableEnvironment.requireVersionText(version)) != null) {
                throw new IOException("Duplicate mod id in last-known-good lock: " + id);
            }
        }
        requireInt(lock, "definition_count", 0, SourceBundle.MAX_FILES);
        return new PackLockMetadata(schemaVersion, engineVersion, mods, packVersions, sourceDigests);
    }

    private static void writeForced(Path path, byte[] bytes) throws IOException {
        Files.deleteIfExists(path);
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private void snapshotCurrentAsPrevious() throws IOException {
        Path currentBundle = directory.resolve(CURRENT_BUNDLE);
        Path currentLock = directory.resolve(CURRENT_LOCK);
        if (!Files.exists(currentBundle, LinkOption.NOFOLLOW_LINKS)
                && !Files.exists(currentLock, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            verifyPair(currentBundle, currentLock);
        } catch (IOException corruptCurrent) {
            // Incoming is already verified. Preserve any previous pair and replace the corrupt current pair.
            return;
        }
        Path previousBundleTemp = directory.resolve("previous.sources.tmp");
        Path previousLockTemp = directory.resolve("previous.lock.tmp");
        writeForced(previousBundleTemp, Files.readAllBytes(currentBundle));
        writeForced(previousLockTemp, Files.readAllBytes(currentLock));
        verifyPair(previousBundleTemp, previousLockTemp);
        atomicMove(previousBundleTemp, directory.resolve(PREVIOUS_BUNDLE));
        atomicMove(previousLockTemp, directory.resolve(PREVIOUS_LOCK));
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void forceDirectory(Path directory) {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // Some filesystems cannot open directories; file fsyncs and atomic replacements still apply.
        }
    }

    private static void deleteOwnedTree(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Recovery target cannot be a symbolic link: " + target);
        }
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Recovery tree contains a symbolic link: " + path);
                }
                Files.delete(path);
            }
        }
    }

    private static void writeBytes(DataOutputStream output, byte[] bytes) throws IOException {
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static byte[] readBytes(DataInputStream input, int maximum) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximum) {
            throw new IOException("Stored field length exceeds " + maximum + ": " + length);
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new IOException("Stored field ended early");
        }
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static String decodeUtf8(byte[] bytes, String label) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("Invalid UTF-8 in " + label, exception);
        }
    }

    private static String require(Properties properties, String key) throws IOException {
        return require(properties, key, 256);
    }

    private static String require(Properties properties, String key, int maximumLength) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IOException("Missing or invalid lock property: " + key);
        }
        return value;
    }

    private static String requireDigest(Properties properties, String key) throws IOException {
        String value = require(properties, key);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid lock digest: " + key);
        }
        return value;
    }

    private static int requireInt(Properties properties, String key, int minimum, int maximum) throws IOException {
        try {
            int value = Integer.parseInt(require(properties, key));
            if (value < minimum || value > maximum) {
                throw new IOException("Lock property " + key + " must be within " + minimum + ".." + maximum);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid integer lock property: " + key, exception);
        }
    }
}
