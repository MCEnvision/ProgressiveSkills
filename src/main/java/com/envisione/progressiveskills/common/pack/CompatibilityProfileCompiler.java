package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.provider.CapabilityProfile;
import com.envisione.progressiveskills.common.provider.ProviderCapability;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class CompatibilityProfileCompiler {
    private static final Set<String> FIELDS = Set.of("mode", "required", "preferred", "active");

    private CompatibilityProfileCompiler() {
    }

    static CanonicalDefinition compile(
            DefinitionKey key,
            Map<String, Object> fields,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        TomlValues.rejectUnknown(fields, FIELDS, key + " compatibility profile");
        CapabilityProfile.Mode.valueOf(TomlValues.string(fields, "mode").strip().toUpperCase(
                java.util.Locale.ROOT));
        Set<ProviderCapability> required = capabilities(fields, "required");
        Set<ProviderCapability> preferred = capabilities(fields, "preferred");
        if (!java.util.Collections.disjoint(required, preferred)) {
            throw new IllegalArgumentException("Compatibility profile capability sets overlap");
        }
        TomlValues.optionalBoolean(fields, "active", false);
        return GenericDefinitionCompiler.compile(key, fields, provenance, sourceMap);
    }

    private static Set<ProviderCapability> capabilities(Map<String, Object> fields, String name) {
        var result = new LinkedHashSet<ProviderCapability>();
        for (String value : TomlValues.stringList(fields, name)) {
            if (!result.add(ProviderCapability.parse(value))) {
                throw new IllegalArgumentException("Compatibility profile contains a duplicate capability");
            }
        }
        return Set.copyOf(result);
    }
}
