package com.envisione.progressiveskills.common.provider;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public final class ExternalProviderMarker implements IntegrationProvider {
    private final String id;
    private final String version;
    private final boolean modPresent;
    private final boolean adapterVerified;

    public ExternalProviderMarker(String id, String version, boolean modPresent, boolean adapterVerified) {
        this.id = Objects.requireNonNull(id, "id");
        this.version = Objects.requireNonNull(version, "version");
        this.modPresent = modPresent;
        this.adapterVerified = adapterVerified;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public Set<ProviderCapability> capabilities() {
        return Set.of();
    }

    @Override
    public ProviderHealth probe() {
        String message = !modPresent
                ? "Optional mod is absent"
                : adapterVerified ? "Verified adapter is available" : "Adapter artifact is not verified";
        return new ProviderHealth(
                adapterVerified ? ProviderHealth.Status.HEALTHY : ProviderHealth.Status.UNAVAILABLE,
                message,
                Instant.now()
        );
    }
}
