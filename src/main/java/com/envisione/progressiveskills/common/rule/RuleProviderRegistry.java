package com.envisione.progressiveskills.common.rule;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class RuleProviderRegistry {
    private final Map<ResourceLocation, ResourceLocation> providersByTrigger;

    private RuleProviderRegistry(Map<ResourceLocation, ResourceLocation> providersByTrigger) {
        this.providersByTrigger = Collections.unmodifiableMap(new LinkedHashMap<>(providersByTrigger));
    }

    public static RuleProviderRegistry core() {
        return builder().register(
                ResourceLocation.fromNamespaceAndPath("progressiveskills", "neoforge"),
                Set.of(RuleTriggerRegistry.BLOCK_BREAK)
        ).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ResourceLocation requireProvider(ResourceLocation trigger) {
        ResourceLocation provider = providersByTrigger.get(trigger);
        if (provider == null) {
            throw new IllegalArgumentException("No rule provider owns trigger " + trigger);
        }
        return provider;
    }

    public Map<ResourceLocation, ResourceLocation> providersByTrigger() {
        return providersByTrigger;
    }

    public static final class Builder {
        private final Map<ResourceLocation, ResourceLocation> providers = new TreeMap<>(
                ResourceLocation::compareNamespaced
        );

        public Builder register(ResourceLocation provider, Set<ResourceLocation> triggers) {
            ResourceLocation checkedProvider = StableId.requireValid(provider);
            for (ResourceLocation trigger : Set.copyOf(Objects.requireNonNull(triggers, "triggers"))) {
                ResourceLocation checkedTrigger = StableId.requireValid(trigger);
                ResourceLocation previous = providers.putIfAbsent(checkedTrigger, checkedProvider);
                if (previous != null) {
                    throw new IllegalArgumentException("Rule trigger " + trigger + " has multiple providers");
                }
            }
            return this;
        }

        public RuleProviderRegistry build() {
            if (providers.isEmpty()) {
                throw new IllegalArgumentException("Rule provider registry must not be empty");
            }
            return new RuleProviderRegistry(new TreeMap<>(providers));
        }
    }
}
