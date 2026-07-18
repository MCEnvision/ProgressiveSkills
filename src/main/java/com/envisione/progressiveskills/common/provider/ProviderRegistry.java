package com.envisione.progressiveskills.common.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class ProviderRegistry {
    private final Map<String, Entry> providers = new TreeMap<>();
    private final Clock clock;

    public ProviderRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static ProviderRegistry nativeRegistry() {
        var registry = new ProviderRegistry(Clock.systemUTC());
        registry.register(new NativeProvider());
        return registry;
    }

    public synchronized void register(IntegrationProvider provider) {
        registerAll(List.of(provider));
    }

    public synchronized void registerAll(Collection<? extends IntegrationProvider> additions) {
        Objects.requireNonNull(additions, "additions");
        var prepared = new LinkedHashMap<String, Entry>();
        for (IntegrationProvider provider : additions) {
            Objects.requireNonNull(provider, "provider");
            String id = provider.id().strip().toLowerCase(java.util.Locale.ROOT);
            if (!id.matches("[a-z0-9_.]{1,64}")) {
                throw new IllegalArgumentException("Provider id is invalid");
            }
            String version = Objects.requireNonNull(provider.version(), "provider version").strip();
            if (version.isEmpty() || version.length() > 128) {
                throw new IllegalArgumentException("Provider version is invalid");
            }
            Set<ProviderCapability> capabilities = Set.copyOf(
                    Objects.requireNonNull(provider.capabilities(), "provider capabilities"));
            Entry entry = new Entry(id, provider, version, capabilities,
                    new ProviderCircuitBreaker(clock, 3, Duration.ofSeconds(30)));
            if (providers.containsKey(id) || prepared.putIfAbsent(id, entry) != null) {
                throw new IllegalArgumentException("Provider id is already registered");
            }
        }
        providers.putAll(prepared);
    }

    public synchronized CompatibilityPlan resolve(CapabilityProfile profile) {
        Objects.requireNonNull(profile, "profile");
        var selected = new EnumMap<ProviderCapability, String>(ProviderCapability.class);
        var unavailable = new LinkedHashSet<ProviderCapability>();
        var fallback = new LinkedHashSet<ProviderCapability>();
        for (ProviderCapability capability : union(profile.required(), profile.preferred())) {
            List<Entry> candidates = providers.values().stream()
                    .filter(entry -> entry.capabilities().contains(capability))
                    .filter(entry -> entry.breaker().allowRequest())
                    .toList();
            if (candidates.isEmpty()) {
                unavailable.add(capability);
                continue;
            }
            Entry chosen = candidates.getFirst();
            selected.put(capability, chosen.id());
            if (chosen.provider() instanceof NativeProvider && capability != ProviderCapability.VANILLA_ATTRIBUTES) {
                fallback.add(capability);
            }
        }
        boolean usable = switch (profile.mode()) {
            case STRICT -> Collections.disjoint(profile.required(), unavailable)
                    && Collections.disjoint(profile.preferred(), unavailable);
            case PREFERRED, FALLBACK -> Collections.disjoint(profile.required(), unavailable);
        };
        return new CompatibilityPlan(usable, selected, unavailable, fallback);
    }

    public synchronized Map<String, ProviderStatus> probeAll() {
        var result = new LinkedHashMap<String, ProviderStatus>();
        providers.forEach((id, entry) -> {
            ProviderHealth health;
            if (!entry.breaker().allowRequest()) {
                health = new ProviderHealth(ProviderHealth.Status.CIRCUIT_OPEN,
                        "Provider circuit is open", Instant.now(clock));
            } else {
                try {
                    health = Objects.requireNonNull(entry.provider().probe(), "provider health");
                    if (health.status() == ProviderHealth.Status.HEALTHY) {
                        entry.breaker().success();
                    } else {
                        entry.breaker().failure();
                    }
                } catch (RuntimeException exception) {
                    entry.breaker().failure();
                    health = new ProviderHealth(ProviderHealth.Status.DEGRADED,
                            safeMessage(exception), Instant.now(clock));
                }
            }
            result.put(id, new ProviderStatus(entry.version(),
                    entry.capabilities(), health, entry.breaker().state()));
        });
        return Collections.unmodifiableMap(result);
    }

    public synchronized Set<String> providerIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(providers.keySet()));
    }

    private static Set<ProviderCapability> union(
            Set<ProviderCapability> first,
            Set<ProviderCapability> second
    ) {
        var result = new LinkedHashSet<>(first);
        result.addAll(second);
        return result;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public record ProviderStatus(
            String version,
            Set<ProviderCapability> capabilities,
            ProviderHealth health,
            ProviderCircuitBreaker.State circuit
    ) {
        public ProviderStatus {
            version = Objects.requireNonNull(version, "version").strip();
            capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
            Objects.requireNonNull(health, "health");
            Objects.requireNonNull(circuit, "circuit");
        }
    }

    private record Entry(
            String id,
            IntegrationProvider provider,
            String version,
            Set<ProviderCapability> capabilities,
            ProviderCircuitBreaker breaker
    ) {
    }

    private static final class NativeProvider implements IntegrationProvider {
        @Override
        public String id() {
            return "progressiveskills.native";
        }

        @Override
        public String version() {
            return "1";
        }

        @Override
        public Set<ProviderCapability> capabilities() {
            return Set.of(
                    ProviderCapability.VANILLA_ATTRIBUTES,
                    ProviderCapability.STAGE_OWNERSHIP,
                    ProviderCapability.PARTY_MEMBERSHIP,
                    ProviderCapability.SHARED_STORAGE,
                    ProviderCapability.CARRIER_SLOTS,
                    ProviderCapability.GUIDE_EXPORT
            );
        }

        @Override
        public ProviderHealth probe() {
            return new ProviderHealth(ProviderHealth.Status.HEALTHY,
                    "Native provider is available", Instant.now());
        }
    }
}
