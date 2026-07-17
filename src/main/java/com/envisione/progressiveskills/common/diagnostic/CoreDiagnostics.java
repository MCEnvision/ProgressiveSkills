package com.envisione.progressiveskills.common.diagnostic;

import java.util.Locale;

/** Core diagnostic metadata shared by validation, docs, commands, and future editors. */
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
    public static final DiagnosticCode PACK_DISCOVERY_FAILED = code("PS-PACK-001");
    public static final DiagnosticCode INVALID_PACK_MANIFEST = code("PS-PACK-002");
    public static final DiagnosticCode MISSING_PACK_DEPENDENCY = code("PS-PACK-003");
    public static final DiagnosticCode PACK_DEPENDENCY_CYCLE = code("PS-PACK-004");
    public static final DiagnosticCode PACK_COLLISION = code("PS-PACK-005");
    public static final DiagnosticCode MERGE_CONFLICT = code("PS-PACK-006");
    public static final DiagnosticCode PACK_PRECEDENCE_CONFLICT = code("PS-PACK-007");
    public static final DiagnosticCode CROSS_KIND_ID_WARNING = code("PS-PACK-008");
    public static final DiagnosticCode INVALID_TOML = code("PS-TOML-001");
    public static final DiagnosticCode SOURCE_LIMIT_EXCEEDED = code("PS-TOML-002");
    public static final DiagnosticCode UNSUPPORTED_DEFINITION_SCHEMA = code("PS-SCHEMA-007");
    public static final DiagnosticCode RELOAD_NOT_STAGED = code("PS-RELOAD-001");
    public static final DiagnosticCode RELOAD_BLOCKED = code("PS-RELOAD-002");
    public static final DiagnosticCode LAST_KNOWN_GOOD_FAILED = code("PS-RELOAD-003");
    public static final DiagnosticCode STALE_TRANSACTION_STATE = code("PS-TX-001");
    public static final DiagnosticCode BALANCE_TRANSACTION_REJECTED = code("PS-TX-002");
    public static final DiagnosticCode STALE_TRANSACTION_DEFINITION = code("PS-TX-003");
    public static final DiagnosticCode INVALID_LIFECYCLE_OWNERSHIP = code("PS-TX-004");
    public static final DiagnosticCode TRANSITION_ACTION_REJECTED = code("PS-TX-005");
    public static final DiagnosticCode TRANSACTION_LEDGER_FULL = code("PS-TX-006");
    public static final DiagnosticCode PERSISTENT_PROJECTION_FAILED = code("PS-TX-007");
    public static final DiagnosticCode TRANSACTION_ROLLBACK_REJECTED = code("PS-TX-008");
    public static final DiagnosticCode PLAYER_DATA_QUARANTINED = code("PS-DATA-001");
    public static final DiagnosticCode PLAYER_DATA_LIMIT_EXCEEDED = code("PS-DATA-002");
    public static final DiagnosticCode PLAYER_DATA_MIGRATION_FAILED = code("PS-DATA-003");
    public static final DiagnosticCode PLAYER_STATE_ORPHANED = code("PS-DATA-004");
    public static final DiagnosticCode OFFLINE_OPERATION_QUARANTINED = code("PS-DATA-005");
    public static final DiagnosticCode SNAPSHOT_EXPORT_FAILED = code("PS-DATA-006");
    public static final DiagnosticCode PLAYER_DATA_IDENTITY_MISMATCH = code("PS-DATA-007");
    public static final DiagnosticCode NETWORK_PROTOCOL_MISMATCH = code("PS-NET-001");
    public static final DiagnosticCode NETWORK_TRANSFER_REJECTED = code("PS-NET-002");
    public static final DiagnosticCode NETWORK_STALE_REVISION = code("PS-NET-003");
    public static final DiagnosticCode NETWORK_RATE_LIMITED = code("PS-NET-004");
    public static final DiagnosticCode NETWORK_RESYNC_REQUIRED = code("PS-NET-005");
    public static final DiagnosticCode INVALID_SKILL = code("PS-SKILL-001");
    public static final DiagnosticCode INVALID_SKILL_CURVE = code("PS-SKILL-002");
    public static final DiagnosticCode INVALID_XP_AWARD = code("PS-SKILL-003");
    public static final DiagnosticCode INVALID_ATTRIBUTE_GRANT = code("PS-SKILL-004");
    public static final DiagnosticCode SKILL_STATE_DRIFT = code("PS-SKILL-005");
    public static final DiagnosticCode INVALID_CURRENCY = code("PS-CURRENCY-001");
    public static final DiagnosticCode INVALID_TREE = code("PS-TREE-001");
    public static final DiagnosticCode TREE_PURCHASE_DENIED = code("PS-TREE-002");
    public static final DiagnosticCode TREE_REFUND_DENIED = code("PS-TREE-003");
    public static final DiagnosticCode TREE_ORPHANED_PURCHASE = code("PS-TREE-004");
    public static final DiagnosticCode PAID_COST_LEDGER_INVALID = code("PS-TREE-005");
    public static final DiagnosticCode INVALID_RULE = code("PS-RULE-001");
    public static final DiagnosticCode UNKNOWN_RULE_TRIGGER = code("PS-RULE-002");
    public static final DiagnosticCode INVALID_RULE_MATCHER = code("PS-RULE-003");
    public static final DiagnosticCode INVALID_RULE_STACK = code("PS-RULE-004");
    public static final DiagnosticCode INVALID_RULE_ANTI_EXPLOIT = code("PS-RULE-005");
    public static final DiagnosticCode RULE_EVENT_REJECTED = code("PS-RULE-006");

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
                .register(descriptor(PACK_DISCOVERY_FAILED, DiagnosticSeverity.ERROR,
                        "Content-pack discovery failed",
                        "A pack root or source path could not be inspected safely.",
                        "Use readable regular directories and files without symbolic links.", false))
                .register(descriptor(INVALID_PACK_MANIFEST, DiagnosticSeverity.ERROR,
                        "Invalid content-pack manifest",
                        "Pack identity, compatibility, dependencies, and policy must be known before definitions load.",
                        "Correct pack.toml using the generated manifest schema.", false))
                .register(descriptor(MISSING_PACK_DEPENDENCY, DiagnosticSeverity.ERROR,
                        "Missing or incompatible pack dependency",
                        "Loading without a required compatible pack would leave unresolved content.",
                        "Install a version matching the declared range or update the dependency declaration.", false))
                .register(descriptor(PACK_DEPENDENCY_CYCLE, DiagnosticSeverity.ERROR,
                        "Content-pack dependency cycle",
                        "A deterministic load order cannot be produced from a dependency cycle.",
                        "Remove one dependency edge and use explicit layered merge intent instead.", false))
                .register(descriptor(PACK_COLLISION, DiagnosticSeverity.ERROR,
                        "Content-pack identity collision",
                        "Two discovered packs cannot own the same stable pack identity.",
                        "Assign one pack a distinct namespaced [pack].id.", false))
                .register(descriptor(MERGE_CONFLICT, DiagnosticSeverity.ERROR,
                        "Definition merge conflict",
                        "A collision without compatible explicit merge semantics would make load order ambiguous.",
                        "Choose add, replace, merge, patch, or disable and satisfy that operation's preconditions.", false))
                .register(descriptor(PACK_PRECEDENCE_CONFLICT, DiagnosticSeverity.ERROR,
                        "Pack dependency contradicts precedence",
                        "A dependency cannot safely load first when its root tier or explicit priority says it must load later.",
                        "Move the dependency to an equal/lower tier and priority, or raise the dependent pack's precedence.", false))
                .register(descriptor(CROSS_KIND_ID_WARNING, DiagnosticSeverity.WARNING,
                        "Definition id is shared across kinds",
                        "Typed keys remain unambiguous, but identical ids across kinds make references and provenance harder to read.",
                        "Give one definition a distinct path/id unless the shared spelling is deliberate.", true))
                .register(descriptor(INVALID_TOML, DiagnosticSeverity.ERROR,
                        "Malformed TOML source",
                        "Invalid TOML cannot compile into deterministic canonical data.",
                        "Correct the reported file and field using a TOML-aware editor.", false))
                .register(descriptor(SOURCE_LIMIT_EXCEEDED, DiagnosticSeverity.ERROR,
                        "Content source exceeds a safety limit",
                        "Unbounded file counts, nesting, or bytes can exhaust server resources during reload.",
                        "Split or reduce the pack so it stays within the documented hard ceilings.", false))
                .register(descriptor(UNSUPPORTED_DEFINITION_SCHEMA, DiagnosticSeverity.ERROR,
                        "Definition schema is not available",
                        "Accepting a definition before its typed compiler exists would create false validation claims.",
                        "Use a definition kind implemented by this build or wait for its feature phase.", false))
                .register(descriptor(RELOAD_NOT_STAGED, DiagnosticSeverity.ERROR,
                        "No validated reload is staged",
                        "Publishing must use the exact snapshot that was reviewed during dry-run.",
                        "Run /ps reload --dry-run, resolve errors, then publish that staged snapshot.", false))
                .register(descriptor(RELOAD_BLOCKED, DiagnosticSeverity.ERROR,
                        "Staged reload is blocked",
                        "A snapshot containing structural errors cannot replace the live last-known-good registry.",
                        "Run /ps validate, correct every error, and stage again.", false))
                .register(descriptor(LAST_KNOWN_GOOD_FAILED, DiagnosticSeverity.ERROR,
                        "Last-known-good recovery failed",
                        "Neither current content nor a verified recovery bundle could produce a safe live snapshot.",
                        "Restore a valid pack source or a complete world backup and validate again.", false))
                .register(descriptor(STALE_TRANSACTION_STATE, DiagnosticSeverity.ERROR,
                        "Transaction state revision is stale",
                        "Applying a plan to a different state revision could duplicate costs, rewards, or ownership changes.",
                        "Refresh the target state, rebuild the plan, and submit it with a new idempotency key.", false))
                .register(descriptor(BALANCE_TRANSACTION_REJECTED, DiagnosticSeverity.ERROR,
                        "Checked balance mutation was rejected",
                        "A debit, credit, bound, or checked-arithmetic operation could not commit safely.",
                        "Correct the amount or affordability condition and rebuild the complete transaction plan.", false))
                .register(descriptor(STALE_TRANSACTION_DEFINITION, DiagnosticSeverity.ERROR,
                        "Transaction definition generation is stale",
                        "A plan cannot execute after its pinned gameplay definitions or semantic digest change.",
                        "Rebuild the plan against the current live definition generation.", false))
                .register(descriptor(INVALID_LIFECYCLE_OWNERSHIP, DiagnosticSeverity.ERROR,
                        "Persistent lifecycle ownership is invalid",
                        "Conflicting resolvers or malformed ownership would make effective values nondeterministic.",
                        "Use one registered resolver for every source contributing to the same entitlement.", false))
                .register(descriptor(TRANSITION_ACTION_REJECTED, DiagnosticSeverity.ERROR,
                        "Transition action was rejected or failed",
                        "Transition actions run only on explicit edges and must pass bounded physical-adapter validation.",
                        "Correct the target, payload, delivery policy, or capacity issue and submit a fresh transaction.", false))
                .register(descriptor(TRANSACTION_LEDGER_FULL, DiagnosticSeverity.ERROR,
                        "Exact transaction ledger is full",
                        "A transaction cannot proceed without durable idempotency or receipt truth.",
                        "Increase the configured hard capacity or complete the planned persistence/archive maintenance.", false))
                .register(descriptor(PERSISTENT_PROJECTION_FAILED, DiagnosticSeverity.ERROR,
                        "Persistent projection failed",
                        "The source-resolved value could not be applied atomically to its physical target.",
                        "Inspect the target adapter and retry only with a newly validated transaction plan.", false))
                .register(descriptor(TRANSACTION_ROLLBACK_REJECTED, DiagnosticSeverity.ERROR,
                        "Transaction rollback is unavailable",
                        "Transition actions or later mutations cross the safe reversible boundary.",
                        "Rollback only the latest retained action-free transaction or apply an explicit compensation.", false))
                .register(descriptor(PLAYER_DATA_QUARANTINED, DiagnosticSeverity.ERROR,
                        "Player progression data is quarantined",
                        "Unknown, malformed, or unsafe persisted data cannot be projected without risking corruption.",
                        "Export the quarantined evidence, restore a verified snapshot, or install a compatible migration.", false))
                .register(descriptor(PLAYER_DATA_LIMIT_EXCEEDED, DiagnosticSeverity.ERROR,
                        "Player progression data exceeds a safety limit",
                        "Unbounded NBT depth, entries, strings, arrays, or bytes can exhaust server resources.",
                        "Restore a bounded snapshot or reduce the persisted payload before importing it.", false))
                .register(descriptor(PLAYER_DATA_MIGRATION_FAILED, DiagnosticSeverity.ERROR,
                        "Player progression data migration failed",
                        "The stored data version could not be transformed into the current attachment contract.",
                        "Keep the migration shadow, restore a backup, and provide every required version step.", false))
                .register(descriptor(PLAYER_STATE_ORPHANED, DiagnosticSeverity.WARNING,
                        "Persisted definition state is orphaned",
                        "Its definition is missing, incompatible, or lacks an explicit identity replacement.",
                        "Restore the compatible definition or declare an unambiguous same-kind alias/replacement.", false))
                .register(descriptor(OFFLINE_OPERATION_QUARANTINED, DiagnosticSeverity.ERROR,
                        "Pending offline operation is quarantined",
                        "The operation expired, exceeded a limit, or no longer matches its pinned definition.",
                        "Review the retained evidence and enqueue a newly validated operation if appropriate.", false))
                .register(descriptor(SNAPSHOT_EXPORT_FAILED, DiagnosticSeverity.ERROR,
                        "Player data snapshot or export failed",
                        "The bounded attachment could not be written and verified atomically.",
                        "Check world storage access and free space, then retry without modifying the source attachment.", false))
                .register(descriptor(PLAYER_DATA_IDENTITY_MISMATCH, DiagnosticSeverity.ERROR,
                        "Player data identity does not match its owner",
                        "Loading one player's attachment for another player could transfer progression or receipts.",
                        "Quarantine the payload and restore data whose embedded UUID matches the attachment owner.", false))
                .register(descriptor(NETWORK_PROTOCOL_MISMATCH, DiagnosticSeverity.ERROR,
                        "Networking protocol or feature mismatch",
                        "A client and server cannot exchange authoritative state under incompatible contracts.",
                        "Install matching ProgressiveSkills versions and reconnect.", false))
                .register(descriptor(NETWORK_TRANSFER_REJECTED, DiagnosticSeverity.ERROR,
                        "Bounded network transfer was rejected",
                        "A chunk count, byte ceiling, timeout, decompression cap, or digest check failed.",
                        "Reconnect; if the error repeats, inspect both endpoints for mismatched or malformed payloads.", false))
                .register(descriptor(NETWORK_STALE_REVISION, DiagnosticSeverity.WARNING,
                        "Network intent revision is stale",
                        "Executing an intent against different definitions or player state could duplicate or misprice it.",
                        "Wait for the targeted full resync, then submit a fresh intent.", false))
                .register(descriptor(NETWORK_RATE_LIMITED, DiagnosticSeverity.WARNING,
                        "Network intent rate limit exceeded",
                        "Unbounded client requests could consume server tick time or memory.",
                        "Wait briefly and retry a single current intent.", false))
                .register(descriptor(NETWORK_RESYNC_REQUIRED, DiagnosticSeverity.WARNING,
                        "Visible state resynchronization is required",
                        "A revision gap, out-of-order delta, unknown path, or digest mismatch broke continuity.",
                        "Allow the bounded full-state transfer to complete before making another mutation.", false))
                .register(descriptor(INVALID_SKILL, DiagnosticSeverity.ERROR,
                        "Skill definition is invalid",
                        "A skill must have bounded levels, presentation, overflow policy, and typed progression fields.",
                        "Correct the skill file using the generated skill definition schema.", false))
                .register(descriptor(INVALID_SKILL_CURVE, DiagnosticSeverity.ERROR,
                        "Skill XP curve is invalid",
                        "Every rounded level cost must be positive, nondecreasing, deterministic, and fit checked long totals.",
                        "Correct the curve type and values at the first reported invalid level.", false))
                .register(descriptor(INVALID_XP_AWARD, DiagnosticSeverity.ERROR,
                        "Skill XP award is invalid",
                        "Negative, overflowing, stale, or unknown XP awards cannot mutate authoritative progression.",
                        "Use a positive fixed point amount and a current enabled skill or custom source.", false))
                .register(descriptor(INVALID_ATTRIBUTE_GRANT, DiagnosticSeverity.ERROR,
                        "Skill attribute grant is invalid",
                        "Unknown attributes, operations, level ranges, or unsafe values cannot be projected atomically.",
                        "Use a registered player attribute and a bounded supported operation.", false))
                .register(descriptor(SKILL_STATE_DRIFT, DiagnosticSeverity.ERROR,
                        "Stored skill state does not match its XP coordinate",
                        "Cached level, highest level, bank, and source ownership must derive exactly from fixed point state.",
                        "Reconcile the player against the current definition generation before gameplay resumes.", false))
                .register(descriptor(INVALID_CURRENCY, DiagnosticSeverity.ERROR,
                        "Named currency definition is invalid",
                        "Currency scope, initial value, and checked bounds must form one consistent contract.",
                        "Use character scope and keep the initial value inside the declared minimum and maximum.", false))
                .register(descriptor(INVALID_TREE, DiagnosticSeverity.ERROR,
                        "Tree definition is invalid",
                        "A Core tree must be bounded, acyclic, single rank, and use valid same tree prerequisites.",
                        "Correct the tree and node fields using the generated tree schemas.", false))
                .register(descriptor(TREE_PURCHASE_DENIED, DiagnosticSeverity.WARNING,
                        "Tree purchase was denied",
                        "Unknown, disabled, owned, unaffordable, or requirement blocked nodes cannot be purchased.",
                        "Review the purchase blockers and submit a fresh intent against current state.", true))
                .register(descriptor(TREE_REFUND_DENIED, DiagnosticSeverity.WARNING,
                        "Tree refund was denied",
                        "A refund requires current ownership, exact historical cost evidence, and a matching cascade preview.",
                        "Request a fresh refund preview and resolve every reported blocker before confirming.", true))
                .register(descriptor(TREE_ORPHANED_PURCHASE, DiagnosticSeverity.WARNING,
                        "Tree purchase is orphaned",
                        "Persisted purchase evidence no longer maps to the same tree node lineage.",
                        "Restore a compatible definition or review the orphan before an explicit migration or refund.", false))
                .register(descriptor(PAID_COST_LEDGER_INVALID, DiagnosticSeverity.ERROR,
                        "Paid cost ledger is invalid",
                        "Missing, malformed, conflicting, or overflowing historical payment evidence prevents an exact refund.",
                        "Restore verified paid cost records before allowing a purchase mutation or refund.", false))
                .register(descriptor(INVALID_RULE, DiagnosticSeverity.ERROR,
                        "Rule definition is invalid",
                        "A gameplay route must compile to one bounded deterministic transaction path.",
                        "Correct the rule using the generated rule definition schema.", false))
                .register(descriptor(UNKNOWN_RULE_TRIGGER, DiagnosticSeverity.ERROR,
                        "Rule trigger has no provider",
                        "A rule cannot run without one registered server side trigger provider.",
                        "Use a trigger supported by the installed provider registry.", false))
                .register(descriptor(INVALID_RULE_MATCHER, DiagnosticSeverity.ERROR,
                        "Rule matcher is invalid",
                        "Unknown prefixes or subject incompatible matchers cannot compile into a safe route table.",
                        "Use a documented prefix supported by the selected trigger subject.", false))
                .register(descriptor(INVALID_RULE_STACK, DiagnosticSeverity.ERROR,
                        "Rule stack group is invalid",
                        "Rules and literal multipliers in one group require one deterministic stack policy.",
                        "Use one stack policy per stable group and unique multiplier ids.", false))
                .register(descriptor(INVALID_RULE_ANTI_EXPLOIT, DiagnosticSeverity.ERROR,
                        "Rule anti exploit policy is invalid",
                        "Cooldowns, rate caps, fake player policy, and repeat decay must stay bounded.",
                        "Correct the anti exploit windows, caps, and fixed point multipliers.", false))
                .register(descriptor(RULE_EVENT_REJECTED, DiagnosticSeverity.WARNING,
                        "Rule event was rejected",
                        "Dedupe, eligibility, cooldown, first time memory, fake player policy, or a rate cap denied the event.",
                        "Inspect the bounded last XP explanation before changing the rule.", true))
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
