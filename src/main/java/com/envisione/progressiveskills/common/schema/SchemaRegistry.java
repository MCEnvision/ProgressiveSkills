package com.envisione.progressiveskills.common.schema;

import com.envisione.progressiveskills.common.diagnostic.DiagnosticCatalog;
import com.envisione.progressiveskills.common.id.DefinitionKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Frozen schema metadata used by docs, diagnostics, and future compilers/editors. */
public final class SchemaRegistry {
    private final DiagnosticCatalog diagnostics;
    private final Map<ResourceLocation, DefinitionKind> definitionKinds;
    private final Map<ResourceLocation, SchemaDescriptor> schemas;

    private SchemaRegistry(
            DiagnosticCatalog diagnostics,
            Map<ResourceLocation, DefinitionKind> definitionKinds,
            Map<ResourceLocation, SchemaDescriptor> schemas
    ) {
        this.diagnostics = diagnostics;
        this.definitionKinds = definitionKinds;
        this.schemas = schemas;
    }

    public static Builder builder(DiagnosticCatalog diagnostics) {
        return new Builder(diagnostics);
    }

    public DiagnosticCatalog diagnostics() {
        return diagnostics;
    }

    public Collection<DefinitionKind> definitionKinds() {
        return definitionKinds.values();
    }

    public DefinitionKind requireKind(ResourceLocation id) {
        var kind = definitionKinds.get(Objects.requireNonNull(id, "id"));
        if (kind == null) {
            throw new IllegalArgumentException("Unregistered definition kind: " + id);
        }
        return kind;
    }

    public Collection<SchemaDescriptor> schemas() {
        return schemas.values();
    }

    public SchemaDescriptor require(ResourceLocation id) {
        var schema = schemas.get(Objects.requireNonNull(id, "id"));
        if (schema == null) {
            throw new IllegalArgumentException("Unregistered schema: " + id);
        }
        return schema;
    }

    /** Mutable only while registrations are being assembled. */
    public static final class Builder {
        private final DiagnosticCatalog diagnostics;
        private final Map<ResourceLocation, DefinitionKind> definitionKinds = new LinkedHashMap<>();
        private final Map<String, DefinitionKind> definitionKindsByDirectory = new LinkedHashMap<>();
        private final Map<ResourceLocation, SchemaDescriptor> schemas = new LinkedHashMap<>();
        private boolean built;

        private Builder(DiagnosticCatalog diagnostics) {
            this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        }

        /**
         * Registers a definition kind. Repeating the same id-to-directory mapping is idempotent,
         * while assigning a second directory to an existing id is rejected as a provider collision.
         */
        public Builder registerKind(DefinitionKind kind) {
            ensureOpen();
            Objects.requireNonNull(kind, "kind");
            var previous = definitionKinds.get(kind.id());
            if (previous != null && !previous.sourceDirectory().equals(kind.sourceDirectory())) {
                throw new IllegalArgumentException(
                        "Definition kind " + kind.id() + " maps to conflicting source directories: "
                                + previous.sourceDirectory() + " and " + kind.sourceDirectory()
                );
            }
            var directoryOwner = definitionKindsByDirectory.get(kind.sourceDirectory());
            if (directoryOwner != null && !directoryOwner.id().equals(kind.id())) {
                throw new IllegalArgumentException(
                        "Definition source directory " + kind.sourceDirectory() + " is shared by "
                                + directoryOwner.id() + " and " + kind.id()
                );
            }
            definitionKinds.putIfAbsent(kind.id(), kind);
            definitionKindsByDirectory.putIfAbsent(kind.sourceDirectory(), kind);
            return this;
        }

        public Builder register(SchemaDescriptor schema) {
            ensureOpen();
            Objects.requireNonNull(schema, "schema");
            for (var field : schema.fields()) {
                if (!diagnostics.contains(field.diagnosticCode())) {
                    throw new IllegalArgumentException(
                            "Schema field " + field.path() + " uses an unregistered diagnostic "
                                    + field.diagnosticCode()
                    );
                }
            }
            for (var constraint : schema.constraints()) {
                if (!diagnostics.contains(constraint.diagnosticCode())) {
                    throw new IllegalArgumentException(
                            "Schema constraint uses an unregistered diagnostic "
                                    + constraint.diagnosticCode()
                    );
                }
            }
            var previous = schemas.putIfAbsent(schema.id(), schema);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate schema id: " + schema.id());
            }
            return this;
        }

        public SchemaRegistry build() {
            ensureOpen();
            built = true;
            var sortedKinds = new TreeMap<ResourceLocation, DefinitionKind>(ResourceLocation::compareNamespaced);
            sortedKinds.putAll(definitionKinds);
            var sorted = new TreeMap<ResourceLocation, SchemaDescriptor>(
                    (left, right) -> left.toString().compareTo(right.toString())
            );
            sorted.putAll(schemas);
            return new SchemaRegistry(
                    diagnostics,
                    Collections.unmodifiableMap(new LinkedHashMap<>(sortedKinds)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(sorted))
            );
        }

        private void ensureOpen() {
            if (built) {
                throw new IllegalStateException("Schema registry builder is already frozen");
            }
        }
    }
}
