package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ClassSynergyDefinition(
        ResourceLocation id,
        DefinitionPresentation presentation,
        boolean enabled,
        List<ResourceLocation> requiredClasses,
        List<ClassGrant> grants
) implements Comparable<ClassSynergyDefinition> {
    public static final ResourceLocation OWNER_KIND = ResourceLocation.fromNamespaceAndPath(
            "progressiveskills", "class_synergy"
    );
    public static final int MAX_REQUIRED_CLASSES = 16;
    public static final int MAX_GRANTS = 32;

    public ClassSynergyDefinition {
        id = StableId.requireValid(id);
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(requiredClasses, "requiredClasses");
        requiredClasses = requiredClasses.stream().map(StableId::requireValid)
                .sorted(ResourceLocation::compareNamespaced).toList();
        if (requiredClasses.size() < 2 || requiredClasses.size() > MAX_REQUIRED_CLASSES) {
            throw new IllegalArgumentException("Class synergy requirement count must be within 2 and "
                    + MAX_REQUIRED_CLASSES);
        }
        if (new HashSet<>(requiredClasses).size() != requiredClasses.size()) {
            throw new IllegalArgumentException("Class synergy requirements contain duplicates");
        }
        Objects.requireNonNull(grants, "grants");
        grants = grants.stream().sorted().toList();
        if (grants.isEmpty() || grants.size() > MAX_GRANTS) {
            throw new IllegalArgumentException("Class synergy grant count must be within 1 and " + MAX_GRANTS);
        }
        if (new HashSet<>(grants.stream().map(ClassGrant::id).toList()).size() != grants.size()) {
            throw new IllegalArgumentException("Class synergy grant ids contain duplicates");
        }
    }

    @Override
    public int compareTo(ClassSynergyDefinition other) {
        return id.compareNamespaced(other.id);
    }

    public GrantSourceId grantSource(ClassGrant grant) {
        Objects.requireNonNull(grant, "grant");
        if (grants.stream().noneMatch(candidate -> candidate.id().equals(grant.id()))) {
            throw new IllegalArgumentException("Grant does not belong to class synergy " + id);
        }
        return grant.source(OWNER_KIND, id);
    }
}
