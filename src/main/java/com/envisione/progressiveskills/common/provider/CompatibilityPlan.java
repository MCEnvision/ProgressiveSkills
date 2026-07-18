package com.envisione.progressiveskills.common.provider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CompatibilityPlan(
        boolean usable,
        Map<ProviderCapability, String> selectedProviders,
        Set<ProviderCapability> unavailable,
        Set<ProviderCapability> usingFallback
) {
    public CompatibilityPlan {
        selectedProviders = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(selectedProviders, "selectedProviders")));
        unavailable = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(unavailable, "unavailable")));
        usingFallback = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(usingFallback, "usingFallback")));
    }
}
