package com.envisione.progressiveskills.common.provider;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRegistryTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void batchRegistrationIsAtomic() {
        var registry = new ProviderRegistry(CLOCK);

        assertThrows(IllegalArgumentException.class, () -> registry.registerAll(List.of(
                provider("same", Set.of(ProviderCapability.CARRIER_SLOTS), false),
                provider("same", Set.of(ProviderCapability.GUIDE_EXPORT), false))));

        assertTrue(registry.providerIds().isEmpty());
    }

    @Test
    void capabilitiesArePinnedAtRegistrationAndFailuresOpenTheCircuit() {
        var registry = new ProviderRegistry(CLOCK);
        registry.register(provider("test", Set.of(ProviderCapability.CARRIER_SLOTS), true));
        var profile = new CapabilityProfile(
                ResourceLocation.fromNamespaceAndPath("test", "profile"),
                CapabilityProfile.Mode.STRICT,
                Set.of(ProviderCapability.CARRIER_SLOTS),
                Set.of());

        assertTrue(registry.resolve(profile).usable());
        assertEquals(ProviderHealth.Status.DEGRADED,
                registry.probeAll().get("test").health().status());
        registry.probeAll();
        assertEquals(ProviderCircuitBreaker.State.OPEN,
                registry.probeAll().get("test").circuit());
        assertEquals(ProviderHealth.Status.CIRCUIT_OPEN,
                registry.probeAll().get("test").health().status());
        assertFalse(registry.resolve(profile).usable());
    }

    private static IntegrationProvider provider(
            String id,
            Set<ProviderCapability> capabilities,
            boolean failProbe
    ) {
        return new IntegrationProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String version() {
                return "1";
            }

            @Override
            public Set<ProviderCapability> capabilities() {
                return capabilities;
            }

            @Override
            public ProviderHealth probe() {
                if (failProbe) {
                    throw new IllegalStateException("failed");
                }
                return new ProviderHealth(ProviderHealth.Status.HEALTHY, "healthy", Instant.now(CLOCK));
            }
        };
    }
}
