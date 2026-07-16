package com.envisione.progressiveskills.common.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Owns separate staged/live snapshots and the definition-generation publication barrier. */
public final class DefinitionRegistryService {
    private final List<PackRoot> roots;
    private final AvailableEnvironment environment;
    private final LastKnownGoodStore store;
    private final Path recoveryDirectory;
    private final ContentPackLoader loader;
    private final AtomicReference<LivePackState> live = new AtomicReference<>(LivePackState.empty());
    private final AtomicReference<StageAttempt> staged = new AtomicReference<>();

    public DefinitionRegistryService(
            Collection<PackRoot> roots,
            AvailableEnvironment environment,
            LastKnownGoodStore store,
            Path recoveryDirectory
    ) {
        this(roots, environment, store, recoveryDirectory, new ContentPackLoader());
    }

    DefinitionRegistryService(
            Collection<PackRoot> roots,
            AvailableEnvironment environment,
            LastKnownGoodStore store,
            Path recoveryDirectory,
            ContentPackLoader loader
    ) {
        this.roots = roots.stream().map(root -> Objects.requireNonNull(root, "root")).sorted().toList();
        this.environment = Objects.requireNonNull(environment, "environment");
        this.store = Objects.requireNonNull(store, "store");
        this.recoveryDirectory = Objects.requireNonNull(recoveryDirectory, "recoveryDirectory").toAbsolutePath().normalize();
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public synchronized StartupLoadResult start() throws IOException {
        StagingResult primary = loader.stage(roots, environment);
        Optional<StoredPackBundle> stored;
        try {
            stored = loadFirstVerified();
        } catch (IOException journalFailure) {
            if (!primary.valid()) {
                throw journalFailure;
            }
            stored = Optional.empty();
        }
        if (primary.valid()) {
            PackSnapshot snapshot = primary.snapshot().orElseThrow();
            if (stored.isPresent()
                    && stored.orElseThrow().contentDigest().equals(snapshot.contentDigest())
                    && stored.orElseThrow().sourceBundle().digest().equals(snapshot.sourceBundle().digest())
                    && stored.orElseThrow().lockMetadata().matches(environment, snapshot)
                    && isVerifiedCurrent(stored.orElseThrow())) {
                StoredPackBundle same = stored.orElseThrow();
                var state = new LivePackState(same.generation(), snapshot, same.createdAt(), false);
                live.set(state);
                return new StartupLoadResult(Optional.of(state), primary, false);
            }
            long generation = Math.addExact(stored.map(StoredPackBundle::generation).orElse(0L), 1L);
            StoredPackBundle saved = store.save(generation, snapshot, environment);
            var state = new LivePackState(generation, snapshot, saved.createdAt(), false);
            live.set(state);
            return new StartupLoadResult(Optional.of(state), primary, false);
        }

        if (stored.isPresent()) {
            var candidates = new java.util.ArrayList<StoredPackBundle>();
            candidates.add(stored.orElseThrow());
            try {
                store.loadPrevious().filter(previous -> candidates.stream().noneMatch(candidate ->
                        candidate.bundleDigest().equals(previous.bundleDigest())
                )).ifPresent(candidates::add);
            } catch (IOException previousFailure) {
                // The verified current candidate can still recover independently.
            }
            IOException lastFailure = null;
            for (StoredPackBundle recovery : candidates) {
                try {
                    LivePackState state = recover(recovery);
                    live.set(state);
                    return new StartupLoadResult(Optional.of(state), primary, true);
                } catch (IOException recoveryFailure) {
                    lastFailure = recoveryFailure;
                }
            }
            throw Objects.requireNonNull(lastFailure, "lastFailure");
        }
        live.set(LivePackState.empty());
        return new StartupLoadResult(Optional.empty(), primary, false);
    }

    public StageAttempt stageDryRun() {
        StagingResult result = loader.stage(roots, environment);
        Optional<SemanticDiff> diff = result.snapshot().map(snapshot -> SemanticDiff.between(live.get().snapshot(), snapshot));
        var attempt = new StageAttempt(result, diff, Instant.now());
        staged.set(attempt);
        return attempt;
    }

    /** Runs the same whole-snapshot validation without creating a publishable review candidate. */
    public StagingResult validate() {
        return loader.stage(roots, environment);
    }

    public synchronized PublishResult publishStaged() throws IOException {
        StageAttempt candidate = staged.get();
        if (candidate == null) {
            return new PublishResult(false, "No dry-run snapshot is staged", Optional.empty());
        }
        if (!candidate.result().valid()) {
            return new PublishResult(false, "The staged snapshot contains blocking validation errors", Optional.empty());
        }
        StagingResult fresh = loader.stage(roots, environment);
        if (!fresh.valid()) {
            staged.set(new StageAttempt(fresh, Optional.empty(), Instant.now()));
            return new PublishResult(false, "Pack sources changed and now fail validation; dry-run again", Optional.empty());
        }
        PackSnapshot reviewed = candidate.result().snapshot().orElseThrow();
        PackSnapshot currentSource = fresh.snapshot().orElseThrow();
        if (!reviewed.contentDigest().equals(currentSource.contentDigest())
                || !reviewed.sourceBundle().digest().equals(currentSource.sourceBundle().digest())) {
            var refreshed = new StageAttempt(
                    fresh,
                    Optional.of(SemanticDiff.between(live.get().snapshot(), currentSource)),
                    Instant.now()
            );
            staged.set(refreshed);
            return new PublishResult(false, "Pack sources changed after dry-run; review the refreshed diff", Optional.empty());
        }
        PackSnapshot currentLive = live.get().snapshot();
        if (reviewed.contentDigest().equals(currentLive.contentDigest())
                && reviewed.sourceBundle().digest().equals(currentLive.sourceBundle().digest())) {
            staged.set(null);
            return new PublishResult(false, "The staged snapshot is already live; no generation was published", Optional.empty());
        }
        long generation = Math.addExact(live.get().generation(), 1L);
        StoredPackBundle saved = store.save(generation, reviewed, environment);
        var published = new LivePackState(generation, reviewed, saved.createdAt(), false);
        live.set(published);
        staged.set(null);
        return new PublishResult(true, "Published definition generation " + generation, Optional.of(published));
    }

    public LivePackState live() {
        return live.get();
    }

    public Optional<StageAttempt> staged() {
        return Optional.ofNullable(staged.get());
    }

    private Optional<StoredPackBundle> loadFirstVerified() throws IOException {
        try {
            Optional<StoredPackBundle> current = store.loadCurrent();
            if (current.isPresent()) {
                return current;
            }
        } catch (IOException currentFailure) {
            Optional<StoredPackBundle> previous = store.loadPrevious();
            if (previous.isPresent()) {
                return previous;
            }
            throw currentFailure;
        }
        return store.loadPrevious();
    }

    private boolean isVerifiedCurrent(StoredPackBundle candidate) {
        try {
            return store.loadCurrent().map(current ->
                    current.generation() == candidate.generation()
                            && current.bundleDigest().equals(candidate.bundleDigest())
            ).orElse(false);
        } catch (IOException currentFailure) {
            return false;
        }
    }

    private LivePackState recover(StoredPackBundle recovery) throws IOException {
        List<PackRoot> recoveryRoots = store.materialize(recovery, recoveryDirectory);
        StagingResult recoveredResult = loader.stage(recoveryRoots, environment);
        if (!recoveredResult.valid()) {
            String detail = recoveredResult.diagnostics().diagnostics().stream().findFirst()
                    .map(diagnostic -> diagnostic.descriptor().code() + ": " + diagnostic.message())
                    .orElse("unknown compiler error");
            throw new IOException("Last-known-good sources no longer compile: " + detail);
        }
        PackSnapshot snapshot = recoveredResult.snapshot().orElseThrow();
        if (!snapshot.contentDigest().equals(recovery.contentDigest())) {
            throw new IOException("Recovered semantic digest does not match the last-known-good lock");
        }
        if (!recovery.lockMetadata().matches(environment, snapshot)) {
            throw new IOException("Recovered pack/environment facts do not match the last-known-good lock");
        }
        return new LivePackState(recovery.generation(), snapshot, recovery.createdAt(), true);
    }
}
