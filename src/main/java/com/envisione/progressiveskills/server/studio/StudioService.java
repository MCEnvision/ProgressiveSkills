package com.envisione.progressiveskills.server.studio;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.pack.JsonDefinitionDocument;
import com.envisione.progressiveskills.common.pack.PackRoot;
import com.envisione.progressiveskills.common.pack.PackRootTier;
import com.envisione.progressiveskills.common.pack.PublishResult;
import com.envisione.progressiveskills.common.pack.TomlDocument;
import com.envisione.progressiveskills.common.studio.StudioDiff;
import com.envisione.progressiveskills.common.studio.StudioDraft;
import com.envisione.progressiveskills.common.studio.PspackSignature;
import com.envisione.progressiveskills.server.pack.CarrierPublicationGuard;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.hardening.HardeningRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class StudioService {
    public static final int MAX_DRAFTS = 256;
    public static final int MAX_FILES = 8192;
    public static final long MAX_FILE_BYTES = 1_048_576L;
    public static final long MAX_TOTAL_BYTES = 33_554_432L;
    public static final int MAX_HISTORY = 128;
    private static final int MAX_TREE_ENTRIES = MAX_FILES * 4;

    private StudioService() {
    }

    public static synchronized StudioDraft create(
            MinecraftServer server,
            UUID owner,
            String namespace,
            String name
    ) throws IOException {
        String validNamespace = StableId.requireNamespace(namespace);
        if (list(server).size() >= MAX_DRAFTS) {
            throw new IllegalStateException("Studio draft capacity is full");
        }
        UUID random = UUID.randomUUID();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                validNamespace, "studio/" + random.toString().toLowerCase(Locale.ROOT));
        Path directory = draftDirectory(server, id);
        requireSafeAncestry(directory.getParent(), worldRoot(server));
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Studio draft identity already exists");
        }
        try {
            Files.createDirectories(directory.resolve("pack"));
            Instant now = Instant.now();
            String baseDigest = PackRuntime.service().map(value -> value.live().snapshot().contentDigest())
                    .orElse("unavailable");
            StudioDraft draft = new StudioDraft(id, owner, validNamespace, boundedName(name), baseDigest,
                    0, StudioDraft.Status.EDITING, now, now);
            atomicWrite(directory.resolve("pack/pack.toml"), manifest(draft, random));
            writeMetadata(directory, draft);
            return draft;
        } catch (RuntimeException | IOException exception) {
            deleteTree(directory);
            throw exception;
        }
    }

    public static synchronized List<StudioDraft> list(MinecraftServer server) throws IOException {
        Path drafts = root(server).resolve("drafts");
        if (!Files.exists(drafts, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        requireSafeAncestry(drafts, worldRoot(server));
        if (Files.isSymbolicLink(drafts) || !Files.isDirectory(drafts, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Studio draft root is unsafe");
        }
        var result = new ArrayList<StudioDraft>();
        try (var paths = Files.list(drafts)) {
            List<Path> entries = paths.limit(MAX_DRAFTS + 1L).sorted().toList();
            if (entries.size() > MAX_DRAFTS) {
                throw new IllegalArgumentException("Studio draft count exceeds capacity");
            }
            for (Path path : entries) {
                if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("Studio draft storage contains an unsafe entry");
                }
                result.add(readMetadata(path));
            }
        }
        return result.stream().sorted(Comparator.comparing(StudioDraft::id,
                ResourceLocation::compareNamespaced)).toList();
    }

    public static synchronized Optional<StudioDraft> find(MinecraftServer server, ResourceLocation id)
            throws IOException {
        Path directory = draftDirectory(server, id);
        requireSafeAncestry(directory, worldRoot(server));
        return Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(directory)
                ? Optional.of(readMetadata(directory)) : Optional.empty();
    }

    public static synchronized StudioDraft write(
            MinecraftServer server,
            ResourceLocation id,
            UUID actor,
            long expectedRevision,
            String relativePath,
            String contents
    ) throws IOException {
        Path directory = requireDraftDirectory(server, id);
        StudioDraft draft = requireActor(readMetadata(directory), actor);
        requireRevision(draft, expectedRevision);
        Path file = safePackPath(directory, relativePath);
        byte[] bytes = Objects.requireNonNull(contents, "contents").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Studio file exceeds capacity");
        }
        validateReplacementBounds(directory.resolve("pack"), file, bytes.length);
        Optional<byte[]> previous = Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        snapshotBeforeEdit(directory, draft.revision(), file);
        try {
            Files.createDirectories(file.getParent());
            atomicWrite(file, bytes);
            StudioDraft updated = update(draft, draft.revision() + 1, StudioDraft.Status.EDITING,
                    draft.baseDigest());
            writeMetadata(directory, updated);
            pruneHistory(directory);
            return updated;
        } catch (RuntimeException | IOException exception) {
            restoreFile(file, previous);
            deleteTree(directory.resolve("history").resolve(Long.toString(draft.revision())));
            throw exception;
        }
    }

    public static synchronized StudioDraft delete(
            MinecraftServer server,
            ResourceLocation id,
            UUID actor,
            long expectedRevision,
            String relativePath
    ) throws IOException {
        Path directory = requireDraftDirectory(server, id);
        StudioDraft draft = requireActor(readMetadata(directory), actor);
        requireRevision(draft, expectedRevision);
        Path file = safePackPath(directory, relativePath);
        if (file.getFileName().toString().equals("pack.toml")) {
            throw new IllegalArgumentException("Studio pack manifest cannot be deleted");
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Studio file is unavailable");
        }
        byte[] previous = Files.readAllBytes(file);
        snapshotBeforeEdit(directory, draft.revision(), file);
        try {
            Files.delete(file);
            StudioDraft updated = update(draft, draft.revision() + 1, StudioDraft.Status.EDITING,
                    draft.baseDigest());
            writeMetadata(directory, updated);
            pruneHistory(directory);
            return updated;
        } catch (RuntimeException | IOException exception) {
            restoreFile(file, Optional.of(previous));
            deleteTree(directory.resolve("history").resolve(Long.toString(draft.revision())));
            throw exception;
        }
    }

    public static synchronized LintResult lint(MinecraftServer server, ResourceLocation id) throws IOException {
        Path directory = requireDraftDirectory(server, id);
        Path pack = directory.resolve("pack");
        var issues = new ArrayList<String>();
        boolean valid = true;
        try {
            validatePack(pack);
            StudioDraft draft = readMetadata(directory);
            Path validationRoot = directory.resolve("staging").resolve("lint_" + UUID.randomUUID());
            Path validationPack = validationRoot.resolve("candidate");
            try {
                copyTree(pack, validationPack);
                var service = PackRuntime.service().orElseThrow(
                        () -> new IllegalStateException("Definition registry is unavailable"));
                var staged = service.validateWithReplacement(
                        publishedPackDirectory(server, draft),
                        new PackRoot(PackRootTier.STUDIO_OVERLAY, "studio_lint", validationRoot));
                valid = staged.valid();
                staged.diagnostics().diagnostics().forEach(diagnostic -> issues.add(
                        diagnostic.descriptor().code() + ". " + diagnostic.message()));
            } finally {
                deleteTree(validationRoot);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            valid = false;
            issues.add(safeMessage(exception));
        }
        StudioDiff diff = diff(server, id);
        return new LintResult(valid, diff.digest(), List.copyOf(issues), diff.bytes(), diff.entries().size());
    }

    public static synchronized StudioDiff diff(MinecraftServer server, ResourceLocation id) throws IOException {
        Path pack = requireDraftDirectory(server, id).resolve("pack");
        var entries = new ArrayList<StudioDiff.Entry>();
        long total = 0;
        var digestBytes = new ByteArrayOutputStream();
        for (Path file : files(pack)) {
            String relative = pack.relativize(file).toString().replace('\\', '/');
            byte[] bytes = Files.readAllBytes(file);
            total = Math.addExact(total, bytes.length);
            String digest = digest(bytes);
            entries.add(new StudioDiff.Entry(relative, digest, bytes.length));
            digestBytes.write(relative.getBytes(StandardCharsets.UTF_8));
            digestBytes.write(0);
            digestBytes.write(digest.getBytes(StandardCharsets.UTF_8));
            digestBytes.write(0);
        }
        return new StudioDiff(digest(digestBytes.toByteArray()), total, entries);
    }

    public static synchronized List<Long> history(MinecraftServer server, ResourceLocation id) throws IOException {
        Path history = requireDraftDirectory(server, id).resolve("history");
        if (!Files.isDirectory(history, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        requireSafeAncestry(history, root(server));
        try (var paths = Files.list(history)) {
            List<Path> entries = paths.limit(MAX_HISTORY + 1L).toList();
            if (entries.size() > MAX_HISTORY) {
                throw new IllegalArgumentException("Studio history exceeds capacity");
            }
            var revisions = new ArrayList<Long>();
            for (Path path : entries) {
                String name = path.getFileName().toString();
                if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        || !name.matches("0|[1-9][0-9]{0,18}")) {
                    throw new IllegalArgumentException("Studio history contains an unsafe entry");
                }
                revisions.add(Long.parseLong(name));
            }
            revisions.sort(Long::compareTo);
            return revisions;
        }
    }

    public static synchronized StudioDraft restoreHistory(
            MinecraftServer server,
            ResourceLocation id,
            UUID actor,
            long expectedRevision,
            long historyRevision
    ) throws IOException {
        Path directory = requireDraftDirectory(server, id);
        StudioDraft draft = requireActor(readMetadata(directory), actor);
        requireRevision(draft, expectedRevision);
        Path snapshot = directory.resolve("history").resolve(Long.toString(historyRevision)).resolve("pack");
        requireSafeAncestry(snapshot, root(server));
        if (!Files.isDirectory(snapshot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(snapshot)) {
            throw new IllegalArgumentException("Studio history revision is unavailable");
        }
        validatePack(snapshot);
        Path staging = directory.resolve("staging").resolve("history_" + UUID.randomUUID());
        copyTree(snapshot, staging);
        snapshotBeforeEdit(directory, draft.revision(), directory.resolve("pack/pack.toml"));
        try {
            swapDirectory(directory.resolve("pack"), staging);
            StudioDraft updated = update(
                    draft, Math.addExact(draft.revision(), 1), StudioDraft.Status.EDITING, draft.baseDigest());
            writeMetadata(directory, updated);
            pruneHistory(directory);
            return updated;
        } catch (RuntimeException | IOException exception) {
            Path previous = directory.resolve("history").resolve(Long.toString(draft.revision())).resolve("pack");
            restoreTarget(directory.resolve("pack"), previous);
            deleteTree(directory.resolve("history").resolve(Long.toString(draft.revision())));
            throw exception;
        } finally {
            deleteTree(staging);
        }
    }

    public static synchronized StudioDraft rebase(
            MinecraftServer server,
            ResourceLocation id,
            UUID actor,
            long expectedRevision
    ) throws IOException {
        Path directory = requireDraftDirectory(server, id);
        StudioDraft draft = requireActor(readMetadata(directory), actor);
        requireRevision(draft, expectedRevision);
        String current = currentDigest();
        Path validationRoot = directory.resolve("staging").resolve("rebase_" + UUID.randomUUID());
        Path validationPack = validationRoot.resolve("candidate");
        requireSafeAncestry(validationRoot, root(server));
        Path target = publishedPackDirectory(server, draft);
        requireSafeAncestry(target.getParent(), worldRoot(server));
        try {
            copyTree(directory.resolve("pack"), validationPack);
            var service = PackRuntime.service().orElseThrow(
                    () -> new IllegalStateException("Definition registry is unavailable"));
            var result = service.validateWithReplacement(target, new PackRoot(
                    PackRootTier.STUDIO_OVERLAY, "studio_rebase", validationRoot));
            if (!result.valid()) {
                String issue = result.diagnostics().diagnostics().stream().findFirst()
                        .map(value -> value.descriptor().code() + ". " + value.message())
                        .orElse("Unknown candidate conflict");
                StudioDraft conflicted = update(
                        draft, draft.revision(), StudioDraft.Status.CONFLICTED, draft.baseDigest());
                writeMetadata(directory, conflicted);
                throw new IllegalStateException("Studio rebase conflict. " + issue);
            }
        } finally {
            deleteTree(validationRoot);
        }
        StudioDraft updated = update(draft, draft.revision() + 1, StudioDraft.Status.EDITING, current);
        writeMetadata(directory, updated);
        return updated;
    }

    public static synchronized PublishResult publish(
            MinecraftServer server,
            ResourceLocation id,
            UUID actor,
            long expectedRevision,
            String confirmationDigest
    ) throws IOException {
        Path directory = requireDraftDirectory(server, id);
        StudioDraft draft = requireActor(readMetadata(directory), actor);
        requireRevision(draft, expectedRevision);
        if (!draft.baseDigest().equals(currentDigest())) {
            StudioDraft conflicted = update(draft, draft.revision(), StudioDraft.Status.CONFLICTED,
                    draft.baseDigest());
            writeMetadata(directory, conflicted);
            throw new IllegalStateException("Studio draft base changed. Rebase and review the diff");
        }
        LintResult lint = lint(server, id);
        if (!lint.valid() || !lint.digest().equals(confirmationDigest)) {
            throw new IllegalArgumentException("Studio lint or confirmation digest is invalid");
        }
        Path target = publishedPackDirectory(server, draft);
        requireSafeAncestry(target.getParent(), worldRoot(server));
        Path publication = directory.resolve("publication").resolve(Long.toString(draft.revision()));
        Path backup = publication.resolve("previous");
        Path journal = directory.resolve("publish.journal");
        deleteTree(publication);
        Files.createDirectories(publication);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            copyTree(target, backup);
        } else {
            atomicWrite(publication.resolve("previous.absent"), new byte[0]);
        }
        writeJournal(journal, "publish", "prepared", target, backup, draft.revision(), Optional.empty());
        Path next = publication.resolve("next");
        copyTree(directory.resolve("pack"), next);
        swapDirectory(target, next);
        writeJournal(journal, "publish", "sources_swapped", target, backup,
                draft.revision(), Optional.empty());
        boolean liveChanged = false;
        long started = System.nanoTime();
        try {
            var service = PackRuntime.service().orElseThrow(
                    () -> new IllegalStateException("Definition registry is unavailable"));
            var staged = service.stageDryRun();
            if (!staged.result().valid()) {
                throw new IllegalStateException("Studio candidate fails whole pack validation");
            }
            String candidateDigest = staged.result().snapshot().orElseThrow().contentDigest();
            String candidateSourceDigest = staged.result().snapshot().orElseThrow().sourceBundle().digest();
            writeJournal(journal, "publish", "live_publish_started", target, backup, draft.revision(),
                    Optional.of(candidateDigest), Optional.of(candidateSourceDigest));
            CarrierPublicationGuard.reserve(server, staged.result().snapshot().orElseThrow());
            PublishResult result = service.publishStaged();
            if (!result.published()) {
                throw new IllegalStateException(result.message());
            }
            liveChanged = true;
            writeJournal(journal, "publish", "live_published", target, backup, draft.revision(),
                    Optional.of(candidateDigest), Optional.of(candidateSourceDigest));
            TransactionRuntime.onDefinitionsPublished(server);
            String nextDigest = result.liveState().orElseThrow().snapshot().contentDigest();
            StudioDraft published = update(draft, draft.revision() + 1, StudioDraft.Status.PUBLISHED, nextDigest);
            writeMetadata(directory, published);
            writeJournal(journal, "publish", "complete", target, backup, draft.revision(),
                    Optional.of(candidateDigest), Optional.of(candidateSourceDigest));
            Files.deleteIfExists(journal);
            return result;
        } catch (RuntimeException | IOException exception) {
            if (!liveChanged) {
                restoreTarget(target, backup);
                Files.deleteIfExists(journal);
            }
            throw exception;
        } finally {
            HardeningRuntime.performance().record(
                    "studio_publish", 500_000_000L, System.nanoTime() - started);
        }
    }

    public static synchronized PublishResult rollback(
            MinecraftServer server,
            ResourceLocation id,
            UUID actor,
            long expectedRevision
    ) throws IOException {
        Path directory = requireDraftDirectory(server, id);
        StudioDraft draft = requireActor(readMetadata(directory), actor);
        requireRevision(draft, expectedRevision);
        Path publications = directory.resolve("publication");
        if (!Files.isDirectory(publications, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Studio draft has no published revision to roll back");
        }
        requireSafeAncestry(publications, root(server));
        Path latest;
        try (var paths = Files.list(publications)) {
            List<Path> entries = paths.limit(MAX_HISTORY + 1L).toList();
            if (entries.size() > MAX_HISTORY) {
                throw new IllegalArgumentException("Studio publication history exceeds capacity");
            }
            var candidates = new ArrayList<Path>();
            for (Path path : entries) {
                String name = path.getFileName().toString();
                if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        || !name.matches("0|[1-9][0-9]{0,18}")) {
                    throw new IllegalArgumentException("Studio publication history contains an unsafe entry");
                }
                candidates.add(path);
            }
            latest = candidates.stream().max(Comparator.comparingLong(path ->
                    Long.parseLong(path.getFileName().toString()))).orElseThrow();
        }
        Path backup = latest.resolve("previous");
        Path absent = latest.resolve("previous.absent");
        if (!Files.isDirectory(backup, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(absent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Studio publication has no previous source snapshot");
        }
        Path target = publishedPackDirectory(server, draft);
        requireSafeAncestry(target.getParent(), worldRoot(server));
        Path rollback = directory.resolve("rollback").resolve(Long.toString(draft.revision()));
        Path current = rollback.resolve("previous");
        Path candidate = rollback.resolve("candidate");
        Path journal = directory.resolve("rollback.journal");
        deleteTree(rollback);
        Files.createDirectories(rollback);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            copyTree(target, current);
        } else {
            atomicWrite(rollback.resolve("previous.absent"), new byte[0]);
        }
        if (Files.isDirectory(backup, LinkOption.NOFOLLOW_LINKS)) {
            copyTree(backup, candidate);
        }
        writeJournal(journal, "rollback", "prepared", target, current,
                draft.revision(), Optional.empty());
        restoreTarget(target, candidate);
        writeJournal(journal, "rollback", "sources_swapped", target, current,
                draft.revision(), Optional.empty());
        boolean liveChanged = false;
        long started = System.nanoTime();
        try {
            var service = PackRuntime.service().orElseThrow();
            var staged = service.stageDryRun();
            if (!staged.result().valid()) {
                throw new IllegalStateException("Studio rollback source fails whole pack validation");
            }
            String candidateDigest = staged.result().snapshot().orElseThrow().contentDigest();
            String candidateSourceDigest = staged.result().snapshot().orElseThrow().sourceBundle().digest();
            writeJournal(journal, "rollback", "live_publish_started", target, current,
                    draft.revision(), Optional.of(candidateDigest), Optional.of(candidateSourceDigest));
            CarrierPublicationGuard.reserve(server, staged.result().snapshot().orElseThrow());
            PublishResult result = service.publishStaged();
            if (!result.published()) {
                throw new IllegalStateException(result.message());
            }
            liveChanged = true;
            writeJournal(journal, "rollback", "live_published", target, current,
                    draft.revision(), Optional.of(candidateDigest), Optional.of(candidateSourceDigest));
            TransactionRuntime.onDefinitionsPublished(server);
            StudioDraft updated = update(draft, draft.revision() + 1, StudioDraft.Status.PUBLISHED,
                    result.liveState().orElseThrow().snapshot().contentDigest());
            writeMetadata(directory, updated);
            writeJournal(journal, "rollback", "complete", target, current,
                    draft.revision(), Optional.of(candidateDigest), Optional.of(candidateSourceDigest));
            Files.deleteIfExists(journal);
            return result;
        } catch (RuntimeException | IOException exception) {
            if (!liveChanged) {
                restoreTarget(target, current);
                Files.deleteIfExists(journal);
            }
            throw exception;
        } finally {
            HardeningRuntime.performance().record(
                    "studio_rollback", 500_000_000L, System.nanoTime() - started);
        }
    }

    public static synchronized Path exportPack(
            MinecraftServer server,
            ResourceLocation id,
            UUID actor
    ) throws IOException {
        Path directory = requireDraftDirectory(server, id);
        requireActor(readMetadata(directory), actor);
        LintResult lint = lint(server, id);
        if (!lint.valid()) {
            throw new IllegalStateException("Studio draft must pass lint before export");
        }
        Path exports = root(server).resolve("exports");
        requireSafeAncestry(exports, worldRoot(server));
        Files.createDirectories(exports);
        Path output = exports.resolve(folderName(id) + ".pspack");
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        prepareTemporary(temporary);
        var manifest = new StringBuilder();
        Path pack = directory.resolve("pack");
        Map<String, byte[]> exported = new LinkedHashMap<>();
        for (Path file : files(pack)) {
            String relative = pack.relativize(file).toString().replace('\\', '/');
            byte[] bytes = Files.readAllBytes(file);
            exported.put(relative, bytes);
            manifest.append(digest(bytes)).append(' ').append(relative).append('\n');
        }
        byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
        byte[] signature = PspackSignature.sign(signingIdentity(server), manifestBytes)
                .getBytes(StandardCharsets.UTF_8);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(
                temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS))) {
            for (Map.Entry<String, byte[]> entry : exported.entrySet()) {
                zip.putNextEntry(new ZipEntry("pack/" + entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("META-INF/PSPACK.SHA256"));
            zip.write(manifestBytes);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/PSPACK.SIG"));
            zip.write(signature);
            zip.closeEntry();
        }
        moveReplace(temporary, output);
        return output;
    }

    public static synchronized StudioDraft importPack(
            MinecraftServer server,
            UUID owner,
            String namespace,
            String name,
            Path archive
    ) throws IOException {
        Path imports = importDirectory(server);
        Path safeArchive = archive.toAbsolutePath().normalize();
        requireSafeAncestry(safeArchive, imports);
        if (Files.isSymbolicLink(safeArchive)
                || !Files.isRegularFile(safeArchive, LinkOption.NOFOLLOW_LINKS)
                || Files.size(safeArchive) > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("Studio import archive is invalid");
        }
        Map<String, byte[]> entries = readArchive(safeArchive);
        byte[] manifestBytes = entries.remove("META-INF/PSPACK.SHA256");
        byte[] signatureBytes = entries.remove("META-INF/PSPACK.SIG");
        if (manifestBytes == null) {
            throw new IllegalArgumentException("Studio import has no checksum manifest");
        }
        if (signatureBytes == null) {
            throw new IllegalArgumentException("Studio import has no signature");
        }
        PspackSignature.Signer signer = PspackSignature.verify(manifestBytes, signatureBytes);
        Map<String, String> expected = checksumManifest(manifestBytes);
        Path staging = root(server).resolve("staging").resolve("import_" + UUID.randomUUID())
                .toAbsolutePath().normalize();
        requireSafeAncestry(staging, root(server));
        Files.createDirectories(staging);
        try {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (!entry.getKey().startsWith("pack/")) {
                    throw new IllegalArgumentException("Studio import contains an unknown root entry");
                }
                String relative = entry.getKey().substring("pack/".length());
                if (!digest(entry.getValue()).equals(expected.get(relative))) {
                    throw new IllegalArgumentException("Studio import checksum mismatch for " + relative);
                }
                Path target = safeArchivePath(staging, relative);
                Files.createDirectories(target.getParent());
                atomicWrite(target, entry.getValue());
            }
            if (!expected.keySet().equals(entries.keySet().stream()
                    .map(value -> value.substring("pack/".length())).collect(java.util.stream.Collectors.toSet()))) {
                throw new IllegalArgumentException("Studio import checksum manifest does not match its entries");
            }
            validatePack(staging);
            StudioDraft draft = create(server, owner, namespace, name);
            Path directory = requireDraftDirectory(server, draft.id());
            try {
                deleteTree(directory.resolve("pack"));
                swapDirectory(directory.resolve("pack"), staging);
                StudioDraft updated = update(draft, draft.revision() + 1, StudioDraft.Status.EDITING,
                        draft.baseDigest());
                writeMetadata(directory, updated);
                trustSigner(server, signer);
                return updated;
            } catch (RuntimeException | IOException exception) {
                deleteTree(directory);
                throw exception;
            }
        } finally {
            deleteTree(staging);
        }
    }

    public static synchronized void recover(MinecraftServer server) throws IOException {
        for (StudioDraft draft : list(server)) {
            Path directory = requireDraftDirectory(server, draft.id());
            recoverRebase(server, directory, draft);
            StudioDraft current = readMetadata(directory);
            recoverPublication(server, directory, current, directory.resolve("publish.journal"), "publish");
            current = readMetadata(directory);
            recoverPublication(server, directory, current, directory.resolve("rollback.journal"), "rollback");
        }
    }

    private static void recoverRebase(MinecraftServer server, Path directory, StudioDraft draft)
            throws IOException {
        Path journal = directory.resolve("rebase.journal");
        if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Properties values = loadProperties(journal);
        String operation = values.getProperty("operation", "");
        String phase = values.getProperty("phase", "");
        long revision = Long.parseLong(values.getProperty("revision", ""));
        Path target = journalPath(values, "target");
        Path backup = journalPath(values, "backup");
        Path expectedBackup = directory.resolve("rebase").resolve(Long.toString(revision))
                .resolve("previous").toAbsolutePath().normalize();
        if (!operation.equals("rebase") || !phase.equals("prepared") && !phase.equals("sources_removed")
                || draft.revision() != revision || !target.equals(publishedPackDirectory(server, draft))
                || !backup.equals(expectedBackup)) {
            throw new IllegalArgumentException("Studio rebase recovery journal is invalid");
        }
        requireSafeAncestry(target, worldRoot(server));
        requireSafeAncestry(backup, root(server));
        restoreTarget(target, backup);
        synchronizeSources(server);
        Files.deleteIfExists(journal);
    }

    private static void recoverPublication(
            MinecraftServer server,
            Path directory,
            StudioDraft draft,
            Path journal,
            String requiredOperation
    ) throws IOException {
        if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Properties values = loadProperties(journal);
        String operation = values.getProperty("operation", "");
        String phase = values.getProperty("phase", "");
        long revision = Long.parseLong(values.getProperty("revision", ""));
        Path target = journalPath(values, "target");
        Path backup = journalPath(values, "backup");
        Path expectedBackup = directory.resolve(operation.equals("rollback") ? "rollback" : "publication")
                .resolve(Long.toString(revision)).resolve("previous").toAbsolutePath().normalize();
        String candidateDigest = values.getProperty("candidate_digest", "");
        String candidateSourceDigest = values.getProperty("candidate_source_digest", "");
        boolean metadataCommitted = draft.revision() == revision + 1
                && draft.status() == StudioDraft.Status.PUBLISHED
                && draft.baseDigest().equals(candidateDigest);
        boolean committedPhase = phase.equals("live_published") || phase.equals("complete")
                || phase.equals("live_publish_started")
                && candidateDigest.equals(currentDigest())
                && candidateSourceDigest.equals(currentSourceDigest());
        boolean revisionValid = committedPhase
                ? draft.revision() == revision || metadataCommitted
                : draft.revision() == revision;
        if (!operation.equals(requiredOperation) || !target.equals(publishedPackDirectory(server, draft))
                || !backup.equals(expectedBackup) || !revisionValid) {
            throw new IllegalArgumentException("Studio publication recovery journal identity is invalid");
        }
        requireSafeAncestry(target, worldRoot(server));
        requireSafeAncestry(backup, root(server));
        if (committedPhase) {
            if (!candidateDigest.matches("[0-9a-f]{64}") || !candidateDigest.equals(currentDigest())
                    || !candidateSourceDigest.matches("[0-9a-f]{64}")
                    || !candidateSourceDigest.equals(currentSourceDigest())) {
                throw new IllegalStateException("Studio committed publication digest is unavailable");
            }
            if (!metadataCommitted) {
                StudioDraft published = update(
                        draft, Math.addExact(draft.revision(), 1), StudioDraft.Status.PUBLISHED, candidateDigest);
                writeMetadata(directory, published);
            }
            TransactionRuntime.onDefinitionsPublished(server);
        } else {
            if (!phase.equals("prepared") && !phase.equals("sources_swapped")
                    && !phase.equals("live_publish_started")) {
                throw new IllegalArgumentException("Studio publication recovery journal phase is invalid");
            }
            restoreTarget(target, backup);
            synchronizeSources(server);
        }
        Files.deleteIfExists(journal);
    }

    private static void synchronizeSources(MinecraftServer server) throws IOException {
        var service = PackRuntime.service().orElseThrow(
                () -> new IllegalStateException("Definition registry is unavailable during Studio recovery"));
        var staged = service.stageDryRun();
        if (!staged.result().valid()) {
            throw new IllegalStateException("Recovered Studio sources fail whole pack validation");
        }
        var candidate = staged.result().snapshot().orElseThrow();
        var live = service.live().snapshot();
        boolean changed = !candidate.contentDigest().equals(live.contentDigest())
                || !candidate.sourceBundle().digest().equals(live.sourceBundle().digest());
        if (changed) {
            CarrierPublicationGuard.reserve(server, candidate);
        }
        PublishResult result = service.publishStaged();
        if (changed && !result.published()) {
            throw new IllegalStateException("Recovered Studio sources could not be published. " + result.message());
        }
        if (changed) {
            TransactionRuntime.onDefinitionsPublished(server);
        }
    }

    private static Path journalPath(Properties values, String key) {
        String value = values.getProperty(key, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Studio recovery journal path is unavailable");
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    public static Path importDirectory(MinecraftServer server) throws IOException {
        Path value = root(server).resolve("imports");
        requireSafeAncestry(value, worldRoot(server));
        Files.createDirectories(value);
        return value;
    }

    private static StudioDraft readMetadata(Path directory) throws IOException {
        Properties values = loadProperties(directory.resolve("draft.properties"));
        ResourceLocation id = StableId.requireValid(ResourceLocation.parse(required(values, "id")));
        String namespace = StableId.requireNamespace(required(values, "namespace"));
        String name = required(values, "name");
        String baseDigest = required(values, "base_digest");
        if (!namespace.equals(id.getNamespace()) || !folderName(id).equals(directory.getFileName().toString())
                || !id.getPath().startsWith("studio/")
                || !isCanonicalUuid(id.getPath().substring("studio/".length()))
                || !boundedName(name).equals(name)
                || !(baseDigest.equals("unavailable") || baseDigest.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("Studio draft metadata identity is invalid");
        }
        StudioDraft draft = new StudioDraft(
                id,
                UUID.fromString(required(values, "owner")),
                namespace,
                name,
                baseDigest,
                Long.parseLong(required(values, "revision")),
                StudioDraft.Status.valueOf(required(values, "status")),
                Instant.parse(required(values, "created_at")),
                Instant.parse(required(values, "updated_at"))
        );
        if (draft.updatedAt().isBefore(draft.createdAt())) {
            throw new IllegalArgumentException("Studio draft timestamps are invalid");
        }
        return draft;
    }

    private static String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Studio properties field is unavailable " + key);
        }
        return value;
    }

    private static boolean isCanonicalUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void writeMetadata(Path directory, StudioDraft draft) throws IOException {
        var values = new Properties();
        values.setProperty("id", draft.id().toString());
        values.setProperty("owner", draft.owner().toString());
        values.setProperty("namespace", draft.namespace());
        values.setProperty("name", draft.name());
        values.setProperty("base_digest", draft.baseDigest());
        values.setProperty("revision", Long.toString(draft.revision()));
        values.setProperty("status", draft.status().name());
        values.setProperty("created_at", draft.createdAt().toString());
        values.setProperty("updated_at", draft.updatedAt().toString());
        atomicProperties(directory.resolve("draft.properties"), values);
    }

    private static Properties loadProperties(Path file) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > 65_536) {
            throw new IllegalArgumentException("Studio properties file is invalid");
        }
        var values = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
        }
        return values;
    }

    private static void atomicProperties(Path file, Properties values) throws IOException {
        var output = new ByteArrayOutputStream();
        values.store(output, null);
        atomicWrite(file, output.toByteArray());
    }

    private static void writeJournal(
            Path journal,
            String operation,
            String phase,
            Path target,
            Path backup,
            long revision,
            Optional<String> candidateDigest
    ) throws IOException {
        writeJournal(journal, operation, phase, target, backup, revision, candidateDigest, Optional.empty());
    }

    private static void writeJournal(
            Path journal,
            String operation,
            String phase,
            Path target,
            Path backup,
            long revision,
            Optional<String> candidateDigest,
            Optional<String> candidateSourceDigest
    ) throws IOException {
        var values = new Properties();
        values.setProperty("operation", operation);
        values.setProperty("phase", phase);
        values.setProperty("target", target.toAbsolutePath().normalize().toString());
        values.setProperty("backup", backup.toAbsolutePath().normalize().toString());
        values.setProperty("revision", Long.toString(revision));
        candidateDigest.ifPresent(value -> values.setProperty("candidate_digest", value));
        candidateSourceDigest.ifPresent(value -> values.setProperty("candidate_source_digest", value));
        atomicProperties(journal, values);
    }

    private static void snapshotBeforeEdit(Path directory, long revision, Path file) throws IOException {
        Path history = directory.resolve("history").resolve(Long.toString(revision));
        if (Files.exists(history, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Studio history revision already exists");
        }
        copyTree(directory.resolve("pack"), history.resolve("pack"));
    }

    private static void pruneHistory(Path directory) throws IOException {
        Path history = directory.resolve("history");
        if (!Files.isDirectory(history, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        requireSafeAncestry(history, directory);
        List<Path> values;
        try (var paths = Files.list(history)) {
            values = new ArrayList<>(paths.limit(MAX_HISTORY + 2L).toList());
        }
        if (values.size() > MAX_HISTORY + 1) {
            throw new IllegalArgumentException("Studio history exceeds its pruning capacity");
        }
        for (Path path : values) {
            String name = path.getFileName().toString();
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                    || !name.matches("0|[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException("Studio history contains an unsafe entry");
            }
        }
        values.sort(Comparator.comparingLong(path -> Long.parseLong(path.getFileName().toString())));
        for (int index = 0; index < values.size() - MAX_HISTORY; index++) {
            deleteTree(values.get(index));
        }
    }

    private static void enforcePackBounds(Path pack) throws IOException {
        List<Path> files = files(pack);
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Studio pack file count exceeds capacity");
        }
        long total = 0;
        for (Path file : files) {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Studio pack file exceeds capacity");
            }
            total = Math.addExact(total, size);
            if (total > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("Studio pack byte count exceeds capacity");
            }
        }
    }

    private static void validateReplacementBounds(Path pack, Path replaced, long replacementBytes)
            throws IOException {
        List<Path> current = files(pack);
        boolean exists = Files.isRegularFile(replaced, LinkOption.NOFOLLOW_LINKS);
        long fileCount = current.size() + (exists ? 0 : 1);
        if (fileCount > MAX_FILES) {
            throw new IllegalArgumentException("Studio pack file count exceeds capacity");
        }
        long total = replacementBytes;
        for (Path file : current) {
            if (!file.equals(replaced)) {
                total = Math.addExact(total, Files.size(file));
            }
        }
        if (total > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("Studio pack byte count exceeds capacity");
        }
    }

    private static void validatePack(Path pack) throws IOException {
        enforcePackBounds(pack);
        for (Path file : files(pack)) {
            String relative = pack.relativize(file).toString().replace('\\', '/');
            if (relative.endsWith(".toml")) {
                TomlDocument.read(file);
            } else if (relative.endsWith(".json")) {
                JsonDefinitionDocument.read(file);
            } else if (!relative.equals("README.md") && !relative.equals("LICENSE")
                    && !relative.equals("LICENSE.txt")) {
                throw new IllegalArgumentException("Unsupported pack file " + relative);
            }
        }
        if (!Files.isRegularFile(pack.resolve("pack.toml"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Studio pack manifest is unavailable");
        }
    }

    private static void restoreFile(Path file, Optional<byte[]> previous) throws IOException {
        if (previous.isPresent()) {
            atomicWrite(file, previous.orElseThrow());
        } else {
            Files.deleteIfExists(file);
        }
    }

    private static void requireSafeAncestry(Path path, Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Studio path escapes its trusted root");
        }
        Path current = normalizedRoot;
        if (Files.isSymbolicLink(current)) {
            throw new IllegalArgumentException("Studio trusted root is a symbolic link");
        }
        for (Path element : normalizedRoot.relativize(normalized)) {
            current = current.resolve(element);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Studio path ancestry contains a symbolic link");
            }
        }
    }

    private static List<Path> files(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Studio pack root is invalid");
        }
        try (var paths = Files.walk(root)) {
            var result = new ArrayList<Path>();
            List<Path> entries = new ArrayList<>(paths.limit(MAX_TREE_ENTRIES + 1L).toList());
            if (entries.size() > MAX_TREE_ENTRIES) {
                throw new IllegalArgumentException("Studio pack entry count exceeds capacity");
            }
            entries.sort(Comparator.comparing(Path::toString));
            for (Path path : entries) {
                if (path.equals(root)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("Studio pack contains a symbolic link");
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    result.add(path);
                } else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("Studio pack contains an unsafe entry");
                }
            }
            return List.copyOf(result);
        }
    }

    private static Path safePackPath(Path directory, String relative) {
        String value = Objects.requireNonNull(relative, "relative").replace('\\', '/');
        if (value.isBlank() || value.startsWith("/") || value.contains("..")
                || !(value.endsWith(".toml") || value.endsWith(".json")
                || value.equals("README.md") || value.equals("LICENSE") || value.equals("LICENSE.txt"))) {
            throw new IllegalArgumentException("Studio pack path is invalid");
        }
        Path root = directory.resolve("pack").toAbsolutePath().normalize();
        Path result = root.resolve(value).normalize();
        if (!result.startsWith(root)) {
            throw new IllegalArgumentException("Studio pack path escapes its root");
        }
        requireSafeAncestry(result.getParent(), root);
        return result;
    }

    private static Path safeArchivePath(Path root, String relative) {
        if (relative.isBlank() || relative.startsWith("/") || relative.contains("..")) {
            throw new IllegalArgumentException("Studio archive path is invalid");
        }
        Path canonicalRoot = root.toAbsolutePath().normalize();
        Path result = canonicalRoot.resolve(relative).normalize();
        if (!result.startsWith(canonicalRoot)) {
            throw new IllegalArgumentException("Studio archive path escapes its root");
        }
        return result;
    }

    private static Map<String, byte[]> readArchive(Path archive) throws IOException {
        var result = new LinkedHashMap<String, byte[]>();
        long total = 0;
        int entryCount = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_TREE_ENTRIES) {
                    throw new IllegalArgumentException("Studio archive entry count exceeds capacity");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..") || result.size() >= MAX_FILES) {
                    throw new IllegalArgumentException("Studio archive entry is unsafe");
                }
                byte[] bytes = zip.readNBytes((int) MAX_FILE_BYTES + 1);
                if (bytes.length > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("Studio archive entry exceeds capacity");
                }
                total = Math.addExact(total, bytes.length);
                if (total > MAX_TOTAL_BYTES || result.putIfAbsent(name, bytes) != null) {
                    throw new IllegalArgumentException("Studio archive exceeds capacity or contains duplicates");
                }
            }
        }
        return result;
    }

    private static Map<String, String> checksumManifest(byte[] bytes) {
        var result = new LinkedHashMap<String, String>();
        String text = new String(bytes, StandardCharsets.UTF_8);
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(" ", 2);
            if (parts.length != 2 || !parts[0].matches("[0-9a-f]{64}")
                    || parts[1].isBlank() || parts[1].startsWith("/") || parts[1].contains("..")
                    || result.size() >= MAX_FILES || result.putIfAbsent(parts[1], parts[0]) != null) {
                throw new IllegalArgumentException("Studio checksum manifest is invalid");
            }
        }
        return result;
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            deleteTree(target);
        }
        Files.createDirectories(target);
        for (Path path : files(source)) {
            Path relative = source.relativize(path);
            Path destination = target.resolve(relative).normalize();
            if (!destination.startsWith(target.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Studio copy path escapes its root");
            }
            Files.createDirectories(destination.getParent());
            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void swapDirectory(Path target, Path next) throws IOException {
        Files.createDirectories(target.getParent());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            deleteTree(target);
        }
        try {
            Files.move(next, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(next, target);
        }
    }

    private static void restoreTarget(Path target, Path backup) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            deleteTree(target);
        }
        if (Files.isDirectory(backup, LinkOption.NOFOLLOW_LINKS)) {
            copyTree(backup, target);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("Studio refuses to delete a symbolic link");
        }
        try (var paths = Files.walk(root)) {
            List<Path> entries = new ArrayList<>(paths.limit(MAX_TREE_ENTRIES + 1L).toList());
            if (entries.size() > MAX_TREE_ENTRIES) {
                throw new IllegalArgumentException("Studio tree exceeds deletion capacity");
            }
            for (Path path : entries) {
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("Studio refuses to delete a symbolic link");
                }
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("Studio refuses to delete an unsafe entry");
                }
            }
            entries.sort(Comparator.reverseOrder());
            for (Path path : entries) {
                Files.delete(path);
            }
        }
    }

    private static void atomicWrite(Path file, String contents) throws IOException {
        atomicWrite(file, contents.getBytes(StandardCharsets.UTF_8));
    }

    private static void atomicWrite(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        prepareTemporary(temporary);
        Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        moveReplace(temporary, file);
    }

    private static void prepareTemporary(Path temporary) throws IOException {
        if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(temporary)
                    || !Files.isRegularFile(temporary, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Studio temporary path is unsafe");
            }
            Files.delete(temporary);
        }
    }

    private static void moveReplace(Path temporary, Path file) throws IOException {
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String manifest(StudioDraft draft, UUID random) {
        String packId = draft.namespace() + ":studio/" + random.toString().replace("-", "");
        return "schema_version = 2\n\n"
                + "[pack]\n"
                + "id = \"" + packId + "\"\n"
                + "namespace = \"" + draft.namespace() + "\"\n"
                + "name = { fallback = \"" + escapeToml(draft.name()) + "\" }\n"
                + "description = \"Studio authored content pack.\"\n"
                + "content_version = \"1.0.0\"\n"
                + "engine = \">=1.0.0 <2.0.0\"\n"
                + "authors = [\"EnVy\"]\n"
                + "license = \"All Rights Reserved\"\n"
                + "priority = 100\n"
                + "default_locale = \"en_us\"\n\n"
                + "[dependencies]\n"
                + "required_packs = []\noptional_packs = []\nrequired_mods = []\n"
                + "optional_mods = []\nincompatible_mods = []\n\n"
                + "[policies]\n"
                + "missing_required = \"reject_pack\"\n"
                + "missing_optional = \"skip_declared_branch\"\n"
                + "unknown_field = \"error\"\n"
                + "duplicate_id = \"error\"\n"
                + "merge_conflict = \"error\"\n"
                + "secret_projection = \"redact\"\n";
    }

    private static String escapeToml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    private static StudioDraft update(
            StudioDraft draft,
            long revision,
            StudioDraft.Status status,
            String baseDigest
    ) {
        return new StudioDraft(draft.id(), draft.owner(), draft.namespace(), draft.name(), baseDigest,
                revision, status, draft.createdAt(), Instant.now());
    }

    private static StudioDraft requireActor(StudioDraft draft, UUID actor) {
        if (!draft.owner().equals(actor)) {
            throw new IllegalStateException("Only the Studio draft owner can modify it");
        }
        return draft;
    }

    private static void requireRevision(StudioDraft draft, long expected) {
        if (draft.revision() != expected) {
            throw new IllegalStateException("Studio draft revision conflict. Current revision " + draft.revision());
        }
    }

    private static Path requireDraftDirectory(MinecraftServer server, ResourceLocation id) {
        Path directory = draftDirectory(server, id);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw new IllegalArgumentException("Unknown Studio draft " + id);
        }
        requireSafeAncestry(directory, root(server));
        requireSafeAncestry(directory, worldRoot(server));
        return directory;
    }

    private static Path draftDirectory(MinecraftServer server, ResourceLocation id) {
        return root(server).resolve("drafts").resolve(folderName(id)).toAbsolutePath().normalize();
    }

    private static Path publishedPackDirectory(MinecraftServer server, StudioDraft draft) {
        return worldRoot(server).resolve("serverconfig/progressiveskills/packs")
                .resolve("studio_" + folderName(draft.id())).toAbsolutePath().normalize();
    }

    private static Path root(MinecraftServer server) {
        return worldRoot(server).resolve("progressiveskills/studio").toAbsolutePath().normalize();
    }

    private static Path worldRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    private static String folderName(ResourceLocation id) {
        return digest(id.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 24);
    }

    private static String currentDigest() {
        return PackRuntime.service().map(value -> value.live().snapshot().contentDigest()).orElse("unavailable");
    }

    private static String currentSourceDigest() {
        return PackRuntime.service().map(value -> value.live().snapshot().sourceBundle().digest())
                .orElse("unavailable");
    }

    private static PspackSignature.SigningIdentity signingIdentity(MinecraftServer server) throws IOException {
        Path keyFile = root(server).resolve("signing.key");
        requireSafeAncestry(keyFile, worldRoot(server));
        if (!Files.exists(keyFile, LinkOption.NOFOLLOW_LINKS)) {
            writeSigningIdentity(keyFile, PspackSignature.generate());
        }
        if (Files.isSymbolicLink(keyFile)
                || !Files.isRegularFile(keyFile, LinkOption.NOFOLLOW_LINKS)
                || Files.size(keyFile) < 1L || Files.size(keyFile) > 1_024L) {
            throw new IllegalArgumentException("Studio signing key is invalid");
        }
        String encoded = Files.readString(keyFile, StandardCharsets.UTF_8);
        if (encoded.matches("[0-9a-f]{64}\\n")) {
            writeSigningIdentity(keyFile, PspackSignature.generate());
            encoded = Files.readString(keyFile, StandardCharsets.UTF_8);
        }
        String[] lines = encoded.split("\n", -1);
        if (lines.length != 3 || !lines[0].startsWith("private_key ")
                || !lines[1].startsWith("public_key ") || !lines[2].isEmpty()) {
            throw new IllegalArgumentException("Studio signing key is invalid");
        }
        try {
            return PspackSignature.identity(
                    Base64.getDecoder().decode(lines[0].substring("private_key ".length())),
                    Base64.getDecoder().decode(lines[1].substring("public_key ".length())));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Studio signing key is invalid", exception);
        }
    }

    private static void writeSigningIdentity(
            Path keyFile,
            PspackSignature.SigningIdentity identity
    ) throws IOException {
        atomicWrite(keyFile,
                "private_key " + Base64.getEncoder().encodeToString(identity.privateKey()) + "\n"
                        + "public_key " + Base64.getEncoder().encodeToString(identity.publicKey()) + "\n");
    }

    private static void trustSigner(MinecraftServer server, PspackSignature.Signer signer) throws IOException {
        Path file = root(server).resolve("trusted_signers.txt");
        requireSafeAncestry(file, worldRoot(server));
        Set<String> trusted = new java.util.TreeSet<>();
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(file) > 131_072L) {
                throw new IllegalArgumentException("Studio signer trust store is invalid");
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.matches("[0-9a-f]{64}") || trusted.size() >= 1_024) {
                    throw new IllegalArgumentException("Studio signer trust store is invalid");
                }
                trusted.add(line);
            }
        }
        trusted.add(signer.fingerprint());
        if (trusted.size() > 1_024) {
            throw new IllegalStateException("Studio signer trust store capacity is full");
        }
        atomicWrite(file, String.join("\n", trusted) + "\n");
    }

    private static String boundedName(String name) {
        String value = Objects.requireNonNull(name, "name").strip();
        if (value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException("Studio draft name is invalid");
        }
        return value;
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    public record LintResult(boolean valid, String digest, List<String> issues, long bytes, int files) {
        public LintResult {
            issues = List.copyOf(issues);
        }
    }
}
