package com.envisione.progressiveskills.common.diagnostic;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, deterministically ordered diagnostic metadata catalog. */
public final class DiagnosticCatalog {
    private final Map<DiagnosticCode, DiagnosticDescriptor> descriptors;

    private DiagnosticCatalog(Map<DiagnosticCode, DiagnosticDescriptor> descriptors) {
        this.descriptors = descriptors;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Collection<DiagnosticDescriptor> descriptors() {
        return descriptors.values();
    }

    public DiagnosticDescriptor require(DiagnosticCode code) {
        var descriptor = descriptors.get(Objects.requireNonNull(code, "code"));
        if (descriptor == null) {
            throw new IllegalArgumentException("Unregistered diagnostic code: " + code);
        }
        return descriptor;
    }

    public boolean contains(DiagnosticCode code) {
        return descriptors.containsKey(code);
    }

    /** Mutable only until {@link #build()} freezes it. */
    public static final class Builder {
        private final Map<DiagnosticCode, DiagnosticDescriptor> descriptors = new LinkedHashMap<>();
        private boolean built;

        public Builder register(DiagnosticDescriptor descriptor) {
            ensureOpen();
            Objects.requireNonNull(descriptor, "descriptor");
            var previous = descriptors.putIfAbsent(descriptor.code(), descriptor);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate diagnostic code: " + descriptor.code());
            }
            return this;
        }

        public DiagnosticCatalog build() {
            ensureOpen();
            built = true;
            var sorted = new TreeMap<DiagnosticCode, DiagnosticDescriptor>();
            sorted.putAll(descriptors);
            return new DiagnosticCatalog(Collections.unmodifiableMap(new LinkedHashMap<>(sorted)));
        }

        private void ensureOpen() {
            if (built) {
                throw new IllegalStateException("Diagnostic catalog builder is already frozen");
            }
        }
    }
}
