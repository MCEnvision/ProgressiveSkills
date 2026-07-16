package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

/** Validates manifest compatibility and produces a stable dependency-first precedence order. */
public final class PackDependencyResolver {
    public PackDependencyResolution resolve(Collection<PackLayer> candidates, AvailableEnvironment environment) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(environment, "environment");
        var problems = new ArrayList<PackProblem>();
        var byId = new TreeMap<ResourceLocation, PackLayer>(ResourceLocation::compareNamespaced);
        for (PackLayer pack : candidates.stream().sorted().toList()) {
            PackLayer previous = byId.putIfAbsent(pack.manifest().id(), pack);
            if (previous != null) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.PACK_COLLISION,
                        "Pack id " + pack.manifest().id() + " is declared by both "
                                + previous.source().directory() + " and " + pack.source().directory(),
                        pack.provenance()
                ));
            }
            validateEnvironment(pack, environment, problems);
        }
        for (PackLayer pack : byId.values()) {
            validateRequirements(pack, byId, problems);
        }
        if (!problems.isEmpty()) {
            return new PackDependencyResolution(List.of(), problems);
        }

        var outgoing = new HashMap<ResourceLocation, Set<ResourceLocation>>();
        var indegree = new HashMap<ResourceLocation, Integer>();
        byId.keySet().forEach(id -> {
            outgoing.put(id, new HashSet<>());
            indegree.put(id, 0);
        });
        for (PackLayer pack : byId.values()) {
            for (PackRequirement requirement : pack.manifest().requiredPacks()) {
                if (outgoing.get(requirement.packId()).add(pack.manifest().id())) {
                    indegree.compute(pack.manifest().id(), (ignored, value) -> Objects.requireNonNull(value) + 1);
                }
            }
            for (PackRequirement requirement : pack.manifest().optionalPacks()) {
                if (byId.containsKey(requirement.packId())
                        && outgoing.get(requirement.packId()).add(pack.manifest().id())) {
                    indegree.compute(pack.manifest().id(), (ignored, value) -> Objects.requireNonNull(value) + 1);
                }
            }
        }
        var ready = new PriorityQueue<PackLayer>();
        indegree.forEach((id, count) -> {
            if (count == 0) {
                ready.add(byId.get(id));
            }
        });
        var ordered = new ArrayList<PackLayer>();
        while (!ready.isEmpty()) {
            PackLayer next = ready.remove();
            ordered.add(next);
            for (ResourceLocation dependent : outgoing.get(next.manifest().id()).stream()
                    .sorted(ResourceLocation::compareNamespaced).toList()) {
                int remaining = indegree.compute(dependent,
                        (ignored, value) -> Objects.requireNonNull(value) - 1);
                if (remaining == 0) {
                    ready.add(byId.get(dependent));
                }
            }
        }
        if (ordered.size() != byId.size()) {
            var cyclic = byId.keySet().stream().filter(id -> indegree.get(id) > 0).toList();
            for (ResourceLocation id : cyclic) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.PACK_DEPENDENCY_CYCLE,
                        "Pack dependency cycle includes " + cyclic,
                        byId.get(id).provenance()
                ));
            }
            return new PackDependencyResolution(List.of(), problems);
        }
        return new PackDependencyResolution(ordered, List.of());
    }

    private static void validateEnvironment(
            PackLayer pack,
            AvailableEnvironment environment,
            List<PackProblem> problems
    ) {
        if (!pack.manifest().engine().contains(environment.engineVersion())) {
            problems.add(PackProblem.error(
                    CoreDiagnostics.INVALID_PACK_MANIFEST,
                    "Pack " + pack.manifest().id() + " requires engine " + pack.manifest().engine().expression()
                            + " but this engine is " + environment.engineVersion(),
                    pack.provenance()
            ));
        }
        for (String required : pack.manifest().requiredMods()) {
            if (!environment.mods().containsKey(required)) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.MISSING_PACK_DEPENDENCY,
                        "Pack " + pack.manifest().id() + " requires missing mod " + required,
                        pack.provenance()
                ));
            }
        }
        for (String incompatible : pack.manifest().incompatibleMods()) {
            if (environment.mods().containsKey(incompatible)) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.MISSING_PACK_DEPENDENCY,
                        "Pack " + pack.manifest().id() + " is incompatible with loaded mod " + incompatible,
                        pack.provenance()
                ));
            }
        }
    }

    private static void validateRequirements(
            PackLayer pack,
            Map<ResourceLocation, PackLayer> packs,
            List<PackProblem> problems
    ) {
        for (PackRequirement requirement : pack.manifest().requiredPacks()) {
            validateRequirement(pack, requirement, packs, true, problems);
        }
        for (PackRequirement requirement : pack.manifest().optionalPacks()) {
            validateRequirement(pack, requirement, packs, false, problems);
        }
    }

    private static void validateRequirement(
            PackLayer owner,
            PackRequirement requirement,
            Map<ResourceLocation, PackLayer> packs,
            boolean required,
            List<PackProblem> problems
    ) {
        PackLayer target = packs.get(requirement.packId());
        if (target == null) {
            if (required) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.MISSING_PACK_DEPENDENCY,
                        "Pack " + owner.manifest().id() + " requires missing pack " + requirement.packId(),
                        owner.provenance()
                ));
            }
            return;
        }
        if (loadsAfterOwner(target, owner)) {
            problems.add(PackProblem.error(
                    CoreDiagnostics.PACK_PRECEDENCE_CONFLICT,
                    "Pack " + owner.manifest().id() + " depends on " + target.manifest().id()
                            + " but the dependency has higher root/priority precedence",
                    owner.provenance()
            ));
        }
        if (requirement.version().isPresent()
                && !requirement.version().orElseThrow().contains(target.manifest().contentVersion())) {
            problems.add(PackProblem.error(
                    CoreDiagnostics.MISSING_PACK_DEPENDENCY,
                    "Pack " + owner.manifest().id() + " requires " + requirement.packId() + " "
                            + requirement.version().orElseThrow().expression() + " but found "
                            + target.manifest().contentVersion(),
                    owner.provenance()
            ));
        }
    }

    private static boolean loadsAfterOwner(PackLayer dependency, PackLayer owner) {
        int tier = dependency.source().root().tier().compareTo(owner.source().root().tier());
        if (tier != 0) {
            return tier > 0;
        }
        return dependency.manifest().priority() > owner.manifest().priority();
    }
}
