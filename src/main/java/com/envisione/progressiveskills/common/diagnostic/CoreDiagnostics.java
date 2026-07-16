package com.envisione.progressiveskills.common.diagnostic;

import java.util.Locale;

/** Phase 2 diagnostic metadata shared by validation, docs, and future editors. */
public final class CoreDiagnostics {
    public static final DiagnosticCode INVALID_SCHEMA_VERSION = code("PS-SCHEMA-001");
    public static final DiagnosticCode UNKNOWN_FIELD = code("PS-SCHEMA-002");
    public static final DiagnosticCode DUPLICATE_SCHEMA = code("PS-SCHEMA-003");
    public static final DiagnosticCode INVALID_SOURCE_SPAN = code("PS-SCHEMA-004");
    public static final DiagnosticCode INVALID_CANONICAL_VALUE = code("PS-SCHEMA-005");
    public static final DiagnosticCode INVALID_ICON = code("PS-SCHEMA-006");
    public static final DiagnosticCode INVALID_ID = code("PS-ID-001");
    public static final DiagnosticCode EXPLICIT_ID_MISMATCH = code("PS-ID-002");
    public static final DiagnosticCode INVALID_ALIAS = code("PS-ID-003");
    public static final DiagnosticCode INVALID_COMPONENT = code("PS-I18N-001");
    public static final DiagnosticCode INVALID_PLACEHOLDER = code("PS-I18N-002");
    public static final DiagnosticCode MISSING_ALT_TEXT = code("PS-A11Y-001");
    public static final DiagnosticCode UNSAFE_PRESENTATION = code("PS-SEC-001");

    private CoreDiagnostics() {
    }

    public static DiagnosticCatalog catalog() {
        return DiagnosticCatalog.builder()
                .register(descriptor(INVALID_SCHEMA_VERSION, DiagnosticSeverity.ERROR,
                        "Unsupported schema version",
                        "Definitions must compile through a known, versioned contract.",
                        "Use schema_version = 2 or run an available migration.", false))
                .register(descriptor(UNKNOWN_FIELD, DiagnosticSeverity.ERROR,
                        "Unknown schema field",
                        "Misspelled or future fields cannot be interpreted deterministically.",
                        "Use a documented field name for this schema version.", true))
                .register(descriptor(DUPLICATE_SCHEMA, DiagnosticSeverity.ERROR,
                        "Duplicate schema registration",
                        "Two owners cannot define the same schema identity safely.",
                        "Keep one registration or assign a distinct namespaced id.", false))
                .register(descriptor(INVALID_SOURCE_SPAN, DiagnosticSeverity.ERROR,
                        "Invalid source span",
                        "Diagnostics and provenance must point to a valid bounded source range.",
                        "Use a normalized relative source and a valid half-open line/column range.", false))
                .register(descriptor(INVALID_CANONICAL_VALUE, DiagnosticSeverity.ERROR,
                        "Invalid canonical value",
                        "Canonical IR accepts only bounded typed values and immutable collections.",
                        "Compile the field to its declared canonical value shape.", false))
                .register(descriptor(INVALID_ICON, DiagnosticSeverity.ERROR,
                        "Invalid icon specification",
                        "Icon kinds, references, fallbacks, and preview policy must form one bounded descriptor.",
                        "Use a documented icon kind with the required namespaced references and fallback.", false))
                .register(descriptor(INVALID_ID, DiagnosticSeverity.ERROR,
                        "Invalid stable identity",
                        "Persistence and references require an explicit, normalized namespaced id.",
                        "Use a lowercase namespace:path id derived from the definition path.", false))
                .register(descriptor(EXPLICIT_ID_MISMATCH, DiagnosticSeverity.ERROR,
                        "Explicit id does not match its source path",
                        "A mismatch makes file renames and persisted identity ambiguous.",
                        "Remove the explicit id or make it equal the path-derived id.", false))
                .register(descriptor(INVALID_ALIAS, DiagnosticSeverity.ERROR,
                        "Invalid identity alias",
                        "Ambiguous, cyclic, or cross-kind aliases can corrupt reference migration.",
                        "Use one acyclic same-kind old-id to new-id mapping.", false))
                .register(descriptor(INVALID_COMPONENT, DiagnosticSeverity.ERROR,
                        "Invalid component specification",
                        "Unbounded or malformed presentation data is unsafe to render or synchronize.",
                        "Provide a bounded localization key and fallback text.", true))
                .register(descriptor(INVALID_PLACEHOLDER, DiagnosticSeverity.ERROR,
                        "Invalid component placeholder",
                        "Placeholder names and types must agree across locales and call sites.",
                        "Declare each placeholder once with its canonical type.", true))
                .register(descriptor(MISSING_ALT_TEXT, DiagnosticSeverity.ERROR,
                        "Missing icon alternative text",
                        "Icons require a textual equivalent for narration and nonvisual use.",
                        "Add a short, meaningful alt component.", true))
                .register(descriptor(UNSAFE_PRESENTATION, DiagnosticSeverity.ERROR,
                        "Unsafe presentation content",
                        "Commands, URLs, selectors, NBT, and other interpreted content cross trust boundaries.",
                        "Use the bounded ComponentSpec subset only.", false))
                .build();
    }

    private static DiagnosticCode code(String value) {
        return new DiagnosticCode(value);
    }

    private static DiagnosticDescriptor descriptor(
            DiagnosticCode code,
            DiagnosticSeverity severity,
            String title,
            String why,
            String fix,
            boolean suppressible
    ) {
        return new DiagnosticDescriptor(
                code,
                severity,
                title,
                why,
                fix,
                "docs/reference/SCHEMA-V2.md#" + code.value().toLowerCase(Locale.ROOT),
                suppressible
        );
    }
}
