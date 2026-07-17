# Content Packs and Staged Loading

Status: Phase 3 staging implemented, with Phase 8 skill, currency, and rule compilers added to the TOML adapter.

## Runtime result

ProgressiveSkills now discovers real content-pack directories, validates their manifests and dependencies, compiles supported TOML definitions into immutable canonical IR, and publishes a whole registry generation. A first launch seeds one dependency-free starter pack without overwriting later operator edits.

The current typed compiler accepts `component_specs/`, `icon_specs/`, `skills/`, `currencies/`, and `rules/`. Other planned gameplay directories remain discoverable identities, but a file in one of those directories fails with `PS-SCHEMA-007` until its implementation phase supplies a concrete schema and compiler.

## Server roots

The active server runtime loads these low-to-high roots:

1. `config/progressiveskills/packs/` — global modpack packs.
2. `<world>/serverconfig/progressiveskills/packs/` — world overlays.

The data model also reserves engine-fallback, mod-provided, Studio, and ephemeral runtime tiers in the master-plan order. Those providers are not wired into Phase 3 and cannot silently affect the live registry.

Only direct child directories containing a regular `pack.toml` are packs. Discovery is sorted and bounded. Symbolic links, unknown top-level paths, non-regular entries, excessive depth/count/bytes, malformed UTF-8, and non-lowercase `.toml` content files fail closed.

## Starter pack

On the first server/world launch, ProgressiveSkills creates:

```text
config/progressiveskills/packs/progressiveskills-core/
  pack.toml
  component_specs/engine_name.toml
  currencies/global_points.toml
  skills/physique.toml
  rules/physique_first_log.toml
  rules/physique_stone_training.toml
```

Existing regular files are never overwritten. A symbolic or non-directory destination is rejected before any resource is copied.

## Manifest contract

Every pack starts with schema-v2 metadata:

```toml
schema_version = 2

[pack]
id = "mypack:core"
namespace = "mypack"
name = { key = "pack.mypack.core", fallback = "My Pack" }
content_version = "1.0.0"
engine = ">=1.0.0 <2.0.0"
authors = ["Pack Team"]
license = "All-Rights-Reserved"
priority = 100
default_locale = "en_us"

[dependencies]
required_packs = ["progressiveskills:core@>=1.0.0"]
optional_packs = []
required_mods = []
optional_mods = []
incompatible_mods = []

[policies]
missing_required = "reject_pack"
missing_optional = "skip_declared_branch"
unknown_field = "error"
duplicate_id = "error"
merge_conflict = "error"
secret_projection = "redact"
```

Pack IDs are unique, the declared namespace must equal the pack-ID namespace, content versions use strict SemVer, and engine ranges require both lower and upper bounds. Dependencies are resolved before their dependents. A dependency whose tier or priority contradicts that order is rejected rather than weakening root precedence.

The optional manifest metadata listed in the generated schema reference is parsed and retained. Policy values are limited to behavior actually implemented by Core; unsupported permissive modes such as `unknown_field = "warn"` are rejected rather than advertised.

## Definition identity and TOML

The definition ID comes from its pack namespace and source path. For example:

```text
component_specs/ui/engine_name.toml -> mypack:ui/engine_name
```

An explicit table `id` is optional, but when present it must exactly match the derived ID.

```toml
schema_version = 2

[component_spec]
id = "mypack:ui/engine_name"
key = "text.mypack.engine_name"
fallback = "My Progression Engine"
```

TOML component and icon shapes normalize into the same immutable `ComponentSpec` and `IconSpec` records established in Phase 2. Skill files accept root `[curve]`, `[[level_currency_awards]]`, `[[xp_sources]]`, `[[levels]]`, and `[[scaling]]` companions. Rule files accept nested `[rule.anti_exploit]`, `[[rule.multipliers]]`, and `[[rule.outputs]]` tables. Block rules can select any combination of `natural`, `creative_placed`, `survival_placed`, `automation_placed`, and `unknown` through `allowed_block_origins`; omission safely permits only natural and creative-placed blocks. Each source compiles into one normalized immutable definition rather than independent runtime paths. Source spans and provenance are retained separately and excluded from semantic equality and content digests.

## Explicit layering

Definitions are applied in resolved pack order. A collision must declare one operation:

- `add` requires the definition to be new.
- `replace` requires an existing definition and may require its exact old semantic digest.
- `merge` recursively merges maps; a changed list requires an explicit patch.
- `patch` applies bounded `set`, `remove`, `append`, `prepend`, or `replace_by_id` operations.
- `disable` keeps the compiled identity and migration visibility but marks it unavailable.

Example patch:

```toml
schema_version = 2
merge_intent = "patch"

[[patches]]
op = "set"
path = "fallback"
value = "Updated display text"
```

`replacements.toml` declares direct, same-kind old-to-terminal aliases. Sources must be retired, targets must exist, IDs must stay in the owning namespace, and conflicting, chained, cyclic, cross-kind, or dangling replacements fail validation.

## Staging and publication

The live registry and dry-run candidate are separate immutable snapshots:

```text
discover -> parse -> dependencies -> derive IDs -> merge/patch
         -> typed validation -> aliases -> semantic digest -> diff
         -> reviewed candidate -> atomic generation publication
```

`/ps validate` runs the full compiler without arming publication. `/ps reload` and `/ps reload --dry-run` stage an exact candidate and semantic diff. `/ps reload --publish` re-reads every source before publication and rejects semantic or source-only changes made after review. The live reference swaps only after the new last-known-good journal is safely written; any error leaves the prior generation untouched.

## Operator commands

| Command | Permission | Result |
|---|---:|---|
| `/ps help` | everyone | Lists Phase 3 commands. |
| `/ps status` | 2 | Shows live generation, pack/definition counts, digest, and recovery state. |
| `/ps validate` | 2 | Validates current disk content without staging it for publish. |
| `/ps reload` | 2 | Safe alias of dry-run. |
| `/ps reload --dry-run` | 2 | Stages and reports the exact semantic diff without mutating live state. |
| `/ps diff` | 2 | Reprints the current reviewed diff. |
| `/ps reload --publish` | 4 | Revalidates and publishes only the reviewed source snapshot. |
| `/ps info pack <id>` | 2 | Shows live manifest version, namespace, priority, and engine range. |
| `/ps info <kind> <id> [--provenance]` | 2 | Shows definition state/digest/value summary and optional field sources. |

Command suggestions come from the live kind, pack, and definition registries. Diagnostics include stable codes, portable source paths, and generated corrective help; chat output is bounded.

## Last-known-good journal

Each successful startup publication or operator publication stores these files below the world's `data/progressiveskills/definitions/` directory:

```text
progressiveskills.lock
progressiveskills.sources
progressiveskills.previous.lock
progressiveskills.previous.sources
```

The lock records schema/engine version, relevant declared mod versions, pack IDs/versions, per-pack source digests, final semantic digest, definition count, generation, and timestamp. The companion source bundle is quota-bounded and checksummed. Writes use temporary files, forced file contents, verified pairs, and atomic replacement where supported; the previous verified generation is retained.

If primary sources fail at startup, recovery accepts a journal only after strict UTF-8/bounds/checksum decoding, environment-lock comparison, complete recompilation, and semantic/source verification. A corrupt current pair falls back to the previous pair. If neither primary nor a verified journal is valid, no definition generation is exposed.

## Hard bounds

The implementation currently enforces, among the lower per-record bounds inherited from Phase 2:

- at most 64 roots and 1,024 packs;
- at most 16,384 retained source files and 64 MiB total source bytes;
- at most 4 MiB per source file;
- at most 8,192 definition files per pack and depth 64;
- at most 100,000 parsed TOML nodes and TOML depth 64;
- at most 10,000 diagnostics and 10,000 replacement aliases; and
- bounded lock, dependency, component, icon, source-map, and canonical-value collections.

## Deferred boundaries

Only the Phase 7 skill and character-currency schemas plus the bounded Phase 8 XP rule schema are implemented. General predicates and formulas, trees, requirements, classes, abilities, locale tables, datapack JSON, external providers, Studio overlays, optional-integration branches and capabilities, `.pspack` import/export, and resource-pack deployment remain assigned to later phases. Unknown content never receives placeholder runtime behavior.
