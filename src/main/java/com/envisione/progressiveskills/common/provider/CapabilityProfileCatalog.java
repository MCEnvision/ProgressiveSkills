package com.envisione.progressiveskills.common.provider;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.ir.CanonicalValue;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class CapabilityProfileCatalog {
    private final Map<ResourceLocation, Entry> profiles;
    private final Optional<ResourceLocation> active;

    private CapabilityProfileCatalog(Map<ResourceLocation, Entry> profiles, Optional<ResourceLocation> active) {
        this.profiles = profiles;
        this.active = active;
    }

    public static CapabilityProfileCatalog from(CanonicalIr ir) {
        Objects.requireNonNull(ir, "ir");
        var profiles = new TreeMap<ResourceLocation, Entry>(ResourceLocation::compareNamespaced);
        ResourceLocation active = null;
        for (var definition : ir.definitions().entrySet()) {
            if (!definition.getKey().kind().equals(DefinitionKinds.COMPATIBILITY_PROFILE)) {
                continue;
            }
            Map<String, CanonicalValue> fields = definition.getValue().fields().fields();
            CapabilityProfile profile = new CapabilityProfile(
                    definition.getKey().id(),
                    CapabilityProfile.Mode.valueOf(text(fields, "mode").toUpperCase(java.util.Locale.ROOT)),
                    capabilities(fields, "required"),
                    capabilities(fields, "preferred")
            );
            boolean selected = bool(fields, "active", false);
            if (selected && active != null) {
                throw new IllegalArgumentException("Multiple compatibility profiles are active");
            }
            if (selected) {
                active = profile.id();
            }
            profiles.put(profile.id(), new Entry(profile, selected));
        }
        return new CapabilityProfileCatalog(
                Collections.unmodifiableMap(new LinkedHashMap<>(profiles)), Optional.ofNullable(active));
    }

    public Map<ResourceLocation, Entry> profiles() {
        return profiles;
    }

    public Optional<Entry> active() {
        return active.map(profiles::get);
    }

    public Optional<Entry> find(ResourceLocation id) {
        return Optional.ofNullable(profiles.get(id));
    }

    private static String text(Map<String, CanonicalValue> fields, String name) {
        if (fields.get(name) instanceof CanonicalValue.TextValue value) {
            return value.value();
        }
        throw new IllegalArgumentException("Compatibility profile field " + name + " is unavailable");
    }

    private static boolean bool(Map<String, CanonicalValue> fields, String name, boolean fallback) {
        CanonicalValue value = fields.get(name);
        if (value == null) {
            return fallback;
        }
        if (value instanceof CanonicalValue.BooleanValue result) {
            return result.value();
        }
        throw new IllegalArgumentException("Compatibility profile field " + name + " is invalid");
    }

    private static java.util.Set<ProviderCapability> capabilities(
            Map<String, CanonicalValue> fields,
            String name
    ) {
        CanonicalValue value = fields.get(name);
        if (value == null) {
            return java.util.Set.of();
        }
        if (!(value instanceof CanonicalValue.ListValue list)) {
            throw new IllegalArgumentException("Compatibility profile capability list is invalid");
        }
        var result = new LinkedHashSet<ProviderCapability>();
        for (CanonicalValue entry : list.values()) {
            if (!(entry instanceof CanonicalValue.TextValue text)
                    || !result.add(ProviderCapability.parse(text.value()))) {
                throw new IllegalArgumentException("Compatibility profile capability is invalid");
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public record Entry(CapabilityProfile profile, boolean active) {
        public Entry {
            Objects.requireNonNull(profile, "profile");
        }
    }
}
