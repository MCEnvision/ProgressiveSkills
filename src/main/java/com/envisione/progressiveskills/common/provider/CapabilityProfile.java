package com.envisione.progressiveskills.common.provider;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record CapabilityProfile(
        ResourceLocation id,
        Mode mode,
        Set<ProviderCapability> required,
        Set<ProviderCapability> preferred
) {
    public CapabilityProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mode, "mode");
        required = immutable(required, "required");
        preferred = immutable(preferred, "preferred");
        if (!Collections.disjoint(required, preferred)) {
            throw new IllegalArgumentException("Capability profile requirements overlap preferences");
        }
    }

    private static Set<ProviderCapability> immutable(Set<ProviderCapability> values, String name) {
        Objects.requireNonNull(values, name);
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    public enum Mode {
        STRICT,
        PREFERRED,
        FALLBACK
    }
}
