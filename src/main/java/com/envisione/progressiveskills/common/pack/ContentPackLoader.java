package com.envisione.progressiveskills.common.pack;

import com.electronwill.nightconfig.core.io.ParsingException;
import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import com.envisione.progressiveskills.common.diagnostic.DiagnosticSeverity;
import com.envisione.progressiveskills.common.diagnostic.DiagnosticReport;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.source.Provenance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** End-to-end bounded TOML pack staging pipeline for the schemas available in this build. */
public final class ContentPackLoader {
    private static final int MAX_SOURCE_DEPTH = 64;
    private static final int MAX_ENTRIES_PER_PACK = 32_768;
    private final ContentPackDiscoverer packDiscoverer = new ContentPackDiscoverer();
    private final PackManifestParser manifestParser = new PackManifestParser();
    private final PackDependencyResolver dependencyResolver = new PackDependencyResolver();
    private final PackFileDiscoverer fileDiscoverer = new PackFileDiscoverer();
    private final DefinitionLayerParser definitionParser = new DefinitionLayerParser();
    private final ReplacementAliasParser aliasParser = new ReplacementAliasParser();
    private final DefinitionResolver definitionResolver = new DefinitionResolver();

    public StagingResult stage(Collection<PackRoot> roots, AvailableEnvironment environment) {
        return stage(roots, environment, Set.of());
    }

    public StagingResult stage(
            Collection<PackRoot> roots,
            AvailableEnvironment environment,
            Collection<Path> excludedSources
    ) {
        var problems = new ArrayList<PackProblem>();
        List<PackSource> sources;
        try {
            Set<Path> excluded = excludedSources.stream()
                    .map(path -> path.toAbsolutePath().normalize())
                    .collect(Collectors.toUnmodifiableSet());
            sources = packDiscoverer.discover(roots).stream()
                    .filter(source -> !excluded.contains(source.directory().toAbsolutePath().normalize()))
                    .toList();
        } catch (IOException | IllegalArgumentException exception) {
            problems.add(problemWithoutSource(
                    CoreDiagnostics.PACK_DISCOVERY_FAILED,
                    "Unable to discover content packs: " + exception.getMessage()
            ));
            return StagingResult.invalid(problems);
        }

        var bundleFiles = new LinkedHashMap<String, byte[]>();
        var packs = new ArrayList<PackLayer>();
        for (PackSource source : sources) {
            try {
                collectSourceBundle(source, bundleFiles);
            } catch (IOException | IllegalArgumentException exception) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.SOURCE_LIMIT_EXCEEDED,
                        "Unable to retain source bundle for " + source.directory() + ": " + exception.getMessage(),
                        unresolvedProvenance(source)
                ));
            }
            try {
                TomlDocument document = TomlDocument.read(source.manifestPath());
                packs.add(manifestParser.parse(source, document));
            } catch (IOException | ParsingException | IllegalArgumentException exception) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.INVALID_PACK_MANIFEST,
                        "Invalid " + source.manifestPath() + ": " + safeMessage(exception),
                        unresolvedProvenance(source)
                ));
            }
        }

        PackDependencyResolution dependencyResolution = dependencyResolver.resolve(packs, environment);
        problems.addAll(dependencyResolution.problems());
        List<PackLayer> orderedPacks = dependencyResolution.valid()
                ? dependencyResolution.orderedPacks() : packs.stream().sorted().toList();

        var definitionLayers = new ArrayList<ParsedDefinitionLayer>();
        var aliases = new ArrayList<ParsedAlias>();
        for (PackLayer pack : orderedPacks) {
            try {
                for (PackFileDiscoverer.DefinitionFile file : fileDiscoverer.discover(pack)) {
                    try {
                        definitionLayers.add(file.json()
                                ? definitionParser.parseJson(
                                pack, file.kind(), file.path(), JsonDefinitionDocument.read(file.path()))
                                : definitionParser.parse(
                                pack, file.kind(), file.path(), TomlDocument.read(file.path())));
                    } catch (IOException | ParsingException | IllegalArgumentException exception) {
                        String relative = pack.source().directory().relativize(file.path()).toString().replace('\\', '/');
                        problems.add(PackProblem.error(
                                CoreDiagnostics.INVALID_TOML,
                                "Invalid definition " + relative + ": " + safeMessage(exception),
                                new Provenance(pack.manifest().id(), relative, "toml")
                        ));
                    }
                }
            } catch (IOException | IllegalArgumentException exception) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.PACK_DISCOVERY_FAILED,
                        "Unable to discover definitions in " + pack.manifest().id() + ": " + safeMessage(exception),
                        pack.provenance()
                ));
            }
            Path replacements = pack.source().directory().resolve("replacements.toml");
            if (Files.exists(replacements, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    aliases.addAll(aliasParser.parse(pack, TomlDocument.read(replacements)));
                } catch (IOException | ParsingException | IllegalArgumentException exception) {
                    problems.add(PackProblem.error(
                            CoreDiagnostics.INVALID_TOML,
                            "Invalid replacements.toml: " + safeMessage(exception),
                            new Provenance(pack.manifest().id(), "replacements.toml", "toml")
                    ));
                }
            }
        }

        DefinitionResolution definitions = definitionResolver.resolve(definitionLayers, aliases, orderedPacks);
        problems.addAll(definitions.problems());
        addCrossKindIdWarnings(definitions, problems);
        if (problems.stream().noneMatch(problem -> problem.severity() == DiagnosticSeverity.ERROR)) {
            com.envisione.progressiveskills.common.tree.TreeCatalog trees = null;
            com.envisione.progressiveskills.common.classdef.ClassCatalog classes = null;
            com.envisione.progressiveskills.common.ability.AbilityCatalog abilities = null;
            try {
                var skills = com.envisione.progressiveskills.common.skill.SkillCatalog.from(
                        definitions.canonicalIr()
                );
                com.envisione.progressiveskills.common.rule.RuleCatalog.from(
                        definitions.canonicalIr(), skills
                );
                trees = com.envisione.progressiveskills.common.tree.TreeCatalog.from(
                        definitions.canonicalIr(), skills
                );
                classes = com.envisione.progressiveskills.common.classdef.ClassCatalog.from(
                        definitions.canonicalIr(), skills, trees
                );
                abilities = com.envisione.progressiveskills.common.ability.AbilityCatalog.from(
                        definitions.canonicalIr(), skills, classes
                );
                com.envisione.progressiveskills.common.carrier.CarrierCatalog.from(
                        definitions.canonicalIr(), skills, trees
                );
                com.envisione.progressiveskills.common.creator.CreatorCatalog.from(
                        definitions.canonicalIr()
                );
                com.envisione.progressiveskills.common.provider.CapabilityProfileCatalog.from(
                        definitions.canonicalIr()
                );
            } catch (IllegalArgumentException | ArithmeticException exception) {
                definitions.canonicalIr().definitions().values().stream().findFirst().ifPresent(definition ->
                        problems.add(PackProblem.error(
                                CoreDiagnostics.INVALID_TOML,
                                "Invalid gameplay catalog: " + safeMessage(exception),
                                definition.provenance()
                        ))
                );
            }
            if (trees != null && classes != null && abilities != null) {
                try {
                    var projection = com.envisione.progressiveskills.common.network.DefinitionProjection.from(
                            definitions.canonicalIr(), trees, classes, abilities
                    );
                    com.envisione.progressiveskills.common.network.DefinitionProjectionCodec.encode(projection);
                } catch (IllegalArgumentException | ArithmeticException exception) {
                    definitions.canonicalIr().definitions().values().stream().findFirst().ifPresent(definition ->
                            problems.add(PackProblem.error(
                                    CoreDiagnostics.SOURCE_LIMIT_EXCEEDED,
                                    "Client definition projection exceeds its staging contract: "
                                            + safeMessage(exception),
                                    definition.provenance()
                            ))
                    );
                }
            }
        }
        SourceBundle sourceBundle;
        try {
            sourceBundle = SourceBundle.of(bundleFiles);
        } catch (IllegalArgumentException exception) {
            problems.add(problemWithoutSource(CoreDiagnostics.SOURCE_LIMIT_EXCEEDED, exception.getMessage()));
            sourceBundle = SourceBundle.empty();
        }
        if (problems.stream().anyMatch(problem -> problem.severity() == DiagnosticSeverity.ERROR)) {
            return StagingResult.invalid(boundedProblems(problems));
        }
        return StagingResult.valid(
                PackSnapshot.create(
                        orderedPacks,
                        definitions.canonicalIr(),
                        definitions.disabledDefinitions(),
                        sourceBundle
                ),
                boundedProblems(problems)
        );
    }

    private static List<PackProblem> boundedProblems(List<PackProblem> problems) {
        var ordered = problems.stream().sorted().toList();
        if (ordered.size() <= DiagnosticReport.MAX_DIAGNOSTICS) {
            return ordered;
        }
        var bounded = new ArrayList<PackProblem>(DiagnosticReport.MAX_DIAGNOSTICS);
        bounded.addAll(ordered.subList(0, DiagnosticReport.MAX_DIAGNOSTICS - 1));
        bounded.add(problemWithoutSource(
                CoreDiagnostics.SOURCE_LIMIT_EXCEEDED,
                "Diagnostics exceeded " + DiagnosticReport.MAX_DIAGNOSTICS
                        + " entries; additional problems were deterministically omitted"
        ));
        return List.copyOf(bounded);
    }

    private static void addCrossKindIdWarnings(
            DefinitionResolution resolution,
            List<PackProblem> problems
    ) {
        var byId = new TreeMap<net.minecraft.resources.ResourceLocation, List<com.envisione.progressiveskills.common.id.DefinitionKey>>(
                net.minecraft.resources.ResourceLocation::compareNamespaced
        );
        resolution.canonicalIr().definitions().keySet().forEach(key ->
                byId.computeIfAbsent(key.id(), ignored -> new ArrayList<>()).add(key));
        byId.values().stream().filter(keys -> keys.size() > 1).forEach(keys -> {
            keys.sort(com.envisione.progressiveskills.common.id.DefinitionKey::compareTo);
            var first = resolution.canonicalIr().definitions().get(keys.getFirst());
            problems.add(PackProblem.warning(
                    CoreDiagnostics.CROSS_KIND_ID_WARNING,
                    "Definition id " + keys.getFirst().id() + " is shared by kinds "
                            + keys.stream().map(key -> key.kind().id().toString()).toList(),
                    first.provenance(),
                    keys.getFirst()
            ));
        });
    }

    private static void collectSourceBundle(PackSource source, Map<String, byte[]> target) throws IOException {
        Set<String> kindDirectories = DefinitionKinds.all().stream()
                .map(kind -> kind.sourceDirectory()).collect(Collectors.toUnmodifiableSet());
        long totalBytes = target.values().stream().mapToLong(bytes -> bytes.length).sum();
        int entries = 0;
        try (var paths = Files.walk(source.directory())) {
            for (Path path : paths.sorted().toList()) {
                if (path.equals(source.directory())) {
                    continue;
                }
                entries++;
                if (entries > MAX_ENTRIES_PER_PACK) {
                    throw new IllegalArgumentException("Pack entries exceed " + MAX_ENTRIES_PER_PACK);
                }
                int depth = source.directory().relativize(path).getNameCount();
                if (depth > MAX_SOURCE_DEPTH) {
                    throw new IllegalArgumentException("Pack path depth exceeds " + MAX_SOURCE_DEPTH + ": " + path);
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("Symbolic links are not allowed: " + path);
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("Pack entry must be a regular file: " + path);
                }
                String relative = source.directory().relativize(path).toString().replace('\\', '/');
                validatePackFilePath(relative, kindDirectories);
                long fileSize = Files.size(path);
                if (fileSize > SourceBundle.MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("Pack file exceeds " + SourceBundle.MAX_FILE_BYTES
                            + " bytes: " + relative);
                }
                if (totalBytes + fileSize > SourceBundle.MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException("Source bundle exceeds " + SourceBundle.MAX_TOTAL_BYTES + " bytes");
                }
                byte[] bytes;
                try (var input = Files.newInputStream(path)) {
                    bytes = input.readNBytes(SourceBundle.MAX_FILE_BYTES + 1);
                }
                if (bytes.length > SourceBundle.MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("Pack file exceeds " + SourceBundle.MAX_FILE_BYTES
                            + " bytes: " + relative);
                }
                if (bytes.length != fileSize || Files.size(path) != fileSize) {
                    throw new IllegalArgumentException("Pack file changed while it was being read: " + relative);
                }
                totalBytes += bytes.length;
                String bundlePath = source.bundlePrefix() + "/" + relative;
                if (target.putIfAbsent(bundlePath, bytes) != null) {
                    throw new IllegalArgumentException("Duplicate bundle path: " + bundlePath);
                }
                if (target.size() > SourceBundle.MAX_FILES) {
                    throw new IllegalArgumentException("Source files exceed " + SourceBundle.MAX_FILES);
                }
            }
        }
    }

    private static void validatePackFilePath(String relative, Set<String> kindDirectories) {
        String first = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : relative;
        boolean rootFile = relative.equals("pack.toml") || relative.equals("replacements.toml")
                || relative.equals("README.md") || relative.equals("LICENSE") || relative.equals("LICENSE.txt");
        boolean contentFile = kindDirectories.contains(first);
        if (!rootFile && !contentFile) {
            throw new IllegalArgumentException("Unknown top-level pack path: " + relative);
        }
        if (contentFile && !(relative.endsWith(".toml") || relative.endsWith(".json"))) {
            throw new IllegalArgumentException("Pack content files must use lowercase TOML or JSON: " + relative);
        }
    }

    private static Provenance unresolvedProvenance(PackSource source) {
        String id = "progressiveskills:unresolved/" + source.root().name() + "/" + source.directoryName();
        return new Provenance(StableId.parse(id), "pack.toml", "toml");
    }

    private static PackProblem problemWithoutSource(
            com.envisione.progressiveskills.common.diagnostic.DiagnosticCode code,
            String message
    ) {
        return new PackProblem(
                code,
                DiagnosticSeverity.ERROR,
                message,
                Optional.empty(),
                Optional.empty()
        );
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
