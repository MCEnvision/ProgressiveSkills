# Content Packs and Staged Loading

Status: Phase 3 staging implemented and extended through the cumulative Phase 19 beta. TOML and bounded JSON now cover Core gameplay, carriers, capability profiles, Creator definitions, multiplayer profiles, and Studio draft publication through one canonical IR.

Phase 10 tree authoring is implemented as a beta checkpoint. The supported boundary is documented in [TREES_AND_REFUNDS.md](TREES_AND_REFUNDS.md), with automated evidence in [PHASE-10.md](../verification/PHASE-10.md).

## Runtime result

ProgressiveSkills now discovers real content-pack directories, validates their manifests and dependencies, compiles supported TOML definitions into immutable canonical IR, and publishes a whole registry generation. A first launch seeds one dependency-free starter pack without overwriting later operator edits.

The current compiler has strict typed adapters for Core component, icon, skill, currency, rule, tree, class slot, class, ability, carrier item, and compatibility profile definitions. Creator and multiplayer definition directories compile bounded generic values into the same immutable IR and then pass their runtime catalog validators. The complete directory catalog is generated in `schema-v2-editor.json`. A file in an unknown directory or a definition that has no implemented compiler fails rather than receiving placeholder behavior.

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

TOML component and icon shapes normalize into the same immutable `ComponentSpec` and `IconSpec` records established in Phase 2. Skill files accept root `[curve]`, `[[level_currency_awards]]`, `[[xp_sources]]`, `[[levels]]`, and `[[scaling]]` companions. Rule files accept a rule-level `rounding` value plus nested `[rule.anti_exploit]`, `[[rule.requirements]]`, `[[rule.multipliers]]`, and `[[rule.outputs]]` tables. Block rules can select any combination of `natural`, `creative_placed`, `survival_placed`, `automation_placed`, and `unknown` through `allowed_block_origins`; omission safely permits only natural and creative-placed blocks. Each source compiles into one normalized immutable definition rather than independent runtime paths. Source spans and provenance are retained separately and excluded from semantic equality and content digests.

Phase 9 Core requirements are intentionally direct and flat. Every `[[rule.requirements]]` object declares `type`, `subject`, symbolic `op`, and `value`, plus exactly one `skill` or `currency` target matching its type. The optional boolean `missing` result defaults to `false`. Core accepts only actor `skill_level` and actor named `currency` leaves, and every object in the list must pass. A currency requirement reads a balance but does not spend it.

```toml
[rule]
rounding = "floor"

[[rule.requirements]]
type = "skill_level"
subject = "actor"
missing = false
skill = "progressiveskills:physique"
op = ">="
value = 5

[[rule.requirements]]
type = "currency"
subject = "actor"
missing = false
currency = "progressiveskills:global_points"
op = ">="
value = 2
```

These entries compile into the internal typed requirement AST and deterministic dependency index. Core does not accept nested `all`, `any`, or `not` authoring, named predicate references, standalone requirement definitions, or general formula strings. The internal composition model exists for later Core systems, while its reusable author-facing language remains gated until Creator. See [REQUIREMENTS_AND_EXPRESSIONS.md](REQUIREMENTS_AND_EXPRESSIONS.md).

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

## Phase 10 tree files

Phase 10 assigns `trees/<id>.toml` to a typed tree compiler. Its Core surface is limited to stable single-rank nodes, literal named-currency costs, authored grid positions, acyclic `requires` AND edges, `requires_any` OR edges, bounded minimum skill levels, transitive cascade refund, and source-owned attribute grants.

The compiler must reject the complete staged snapshot when a tree has a duplicate or dangling node ID, invalid scope or bind, unknown currency or skill, cycle, self edge, invalid cost, unsupported grant, unsupported Creator field, or legal refund closure that cannot fit the atomic transaction limits. Array order is never node identity.

Tree cost and layout changes participate in semantic digest and staged diff. A published cost change affects future purchases only. Every completed purchase retains its original currency and amount in a durable paid cost record, so reload cannot rewrite refund history. A tree file cannot supply or reconstruct that player accounting state.

Tree and node presentation plus disclosed graph fields enter the sanitized definition projection. Paid cost records, raw grant owners, hidden policies, and server-only evaluation data remain outside content projection. See [TREES_AND_REFUNDS.md](TREES_AND_REFUNDS.md) for the authority, lineage, reconciliation, and accessibility contract.

## Staging and publication

The live registry and dry-run candidate are separate immutable snapshots:

```text
discover -> parse -> dependencies -> derive IDs -> merge/patch
         -> typed validation -> aliases -> semantic digest -> diff
         -> reviewed candidate -> atomic generation publication
```

`/pskills validate` runs the full compiler without arming publication. `/pskills reload` and `/pskills reload --dry-run` stage an exact candidate and semantic diff. `/pskills reload --publish` re-reads every source before publication and rejects semantic or source-only changes made after review. The live reference swaps only after the new last-known-good journal is safely written; any error leaves the prior generation untouched.

## Operator commands

| Command | Permission | Result |
|---|---:|---|
| `/pskills help` | everyone | Lists Phase 3 commands. |
| `/pskills status` | 2 | Shows live generation, pack/definition counts, digest, and recovery state. |
| `/pskills validate` | 2 | Validates current disk content without staging it for publish. |
| `/pskills reload` | 2 | Safe alias of dry-run. |
| `/pskills reload --dry-run` | 2 | Stages and reports the exact semantic diff without mutating live state. |
| `/pskills diff` | 2 | Reprints the current reviewed diff. |
| `/pskills reload --publish` | 4 | Revalidates and publishes only the reviewed source snapshot. |
| `/pskills info pack <id>` | 2 | Shows live manifest version, namespace, priority, and engine range. |
| `/pskills info <kind> <id> [--provenance]` | 2 | Shows definition state/digest/value summary and optional field sources. |

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

## Cumulative Phase 19 boundary

The cumulative beta implements skills, currencies, rules, direct requirements, rounding, trees, classes, abilities, carriers, compatibility profiles, Creator generic definitions, multiplayer profiles, bounded datapack JSON, Studio drafts, and signed pspack import and export. Unknown content never receives placeholder runtime behavior.

External physical provider adapters remain unavailable until their exact target artifacts, supported version ranges, absent mod boots, and present mod tests pass. The native provider contracts and fallbacks work without those mods. Resource pack deployment and a remote editor protocol are not claimed by this checkpoint.
