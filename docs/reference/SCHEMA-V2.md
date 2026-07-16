# ProgressiveSkills Schema Reference v2

> Generated from `CoreSchemas`; edit the registry metadata, then regenerate this file.

Schema v2 includes the shared immutable IR contracts and the authoring schemas implemented through Phase 3. Gameplay definition schemas arrive with their implementation phases.

## Definition-kind catalog

| Kind ID | Source directory |
|---|---|
| `progressiveskills:ability` | `abilities` |
| `progressiveskills:anti_exploit_profile` | `anti_exploit_profiles` |
| `progressiveskills:category` | `categories` |
| `progressiveskills:challenge` | `challenges` |
| `progressiveskills:class` | `classes` |
| `progressiveskills:class_slot` | `class_slots` |
| `progressiveskills:component_spec` | `component_specs` |
| `progressiveskills:conversion` | `conversions` |
| `progressiveskills:cost_bundle` | `cost_bundles` |
| `progressiveskills:currency` | `currencies` |
| `progressiveskills:curve` | `curves` |
| `progressiveskills:global_level` | `global_levels` |
| `progressiveskills:grant_bundle` | `grant_bundles` |
| `progressiveskills:icon_spec` | `icon_specs` |
| `progressiveskills:item` | `items` |
| `progressiveskills:item_stack_spec` | `item_stack_specs` |
| `progressiveskills:layout` | `layouts` |
| `progressiveskills:notification_profile` | `notification_profiles` |
| `progressiveskills:predicate` | `predicates` |
| `progressiveskills:prestige` | `prestige` |
| `progressiveskills:profile` | `profiles` |
| `progressiveskills:requirement` | `requirements` |
| `progressiveskills:resource` | `resources` |
| `progressiveskills:rule` | `rules` |
| `progressiveskills:season` | `seasons` |
| `progressiveskills:skill` | `skills` |
| `progressiveskills:station` | `stations` |
| `progressiveskills:targeting_profile` | `targeting_profiles` |
| `progressiveskills:template` | `templates` |
| `progressiveskills:theme` | `themes` |
| `progressiveskills:tree` | `trees` |
| `progressiveskills:variable` | `variables` |

## Definition alias

- Schema ID: `progressiveskills:alias`
- Version: `2`
- Audience: `authoring`

A same-kind old identity mapped to one canonical replacement identity.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `kind` (required) | `resource_location` | — | — | Definition kind shared by the old and replacement identities. | `progressiveskills:skill` | `PS-ID-003` | `resource_location` | `server_only` | `replace` |
| `new_id` (required) | `resource_location` | — | — | Canonical replacement identity. | `mypack:combat/physique` | `PS-ID-003` | `resource_location` | `server_only` | `replace` |
| `old_id` (required) | `resource_location` | — | — | Retired identity retained for migration and reference resolution. | `mypack:combat/strength` | `PS-ID-003` | `resource_location` | `server_only` | `replace` |

## Canonical definition envelope

- Schema ID: `progressiveskills:canonical_definition`
- Version: `2`
- Audience: `internal`

Immutable typed semantic fields paired with source maps and provenance.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `fields` (required) | `map` | — | — | Deterministically ordered typed canonical values. | `{ max_level = 100 }` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |
| `header` (required) | `object` | — | — | Schema version, typed stable key, and safe presentation. | `{ schema_version = 2, id = "mypack:physique" }` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |
| `provenance` (required) | `object` | — | — | Adapter and source metadata excluded from semantic projection. | `{ adapter = "toml" }` | `PS-SCHEMA-004` | `object` | `server_only` | `replace` |
| `source_map` (required) | `map` | — | — | Field path to source-span mapping excluded from semantic projection. | `{ max_level = { source = "skills/physique.toml" } }` | `PS-SCHEMA-004` | `key_value` | `server_only` | `merge_by_key` |

## Component specification

- Schema ID: `progressiveskills:component_spec`
- Version: `2`
- Audience: `authoring`

Safe localized text descriptor stored in IR instead of a mutable vanilla Component.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `children` | `list` | — | `[]` | Bounded child component specifications rendered in order. | `[{ fallback = "!", style = { color = "gold" } }]` | `PS-I18N-001` | `list` | `client_visible` | `ordered` |
| `fallback` (required) | `string` | — | — | Bounded text used when a locale key cannot be resolved. | `Physique` | `PS-I18N-001` | `multi_line` | `client_visible` | `replace` |
| `key` | `string` | — | — | Pack locale key; omission creates a literal safe component. | `skill.mypack.physique` | `PS-I18N-001` | `single_line` | `client_visible` | `replace` |
| `placeholders` | `map` | — | `{}` | Stable placeholder names mapped to declared value types. | `{ level = "integer", skill = "component" }` | `PS-I18N-002` | `key_value` | `client_visible` | `merge_by_key` |
| `style` | `object` | — | `{}` | Allowlisted, non-interpreting text style. | `{ color = "red", bold = true }` | `PS-SEC-001` | `object` | `client_visible` | `replace` |

## Definition header

- Schema ID: `progressiveskills:definition_header`
- Version: `2`
- Audience: `authoring`

Versioned identity and presentation fields shared by every definition.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `description` | `component` | — | — | Localized long-form description. | `{ key = "skill.mypack.physique.desc", fallback = "Raw power." }` | `PS-I18N-001` | `component` | `client_visible` | `replace` |
| `display` | `component` | — | — | Localized player-facing display name. | `{ key = "skill.mypack.physique", fallback = "Physique" }` | `PS-I18N-001` | `component` | `client_visible` | `replace` |
| `icon` | `icon` | — | — | Typed icon descriptor with required fallback and alternative text. | `{ type = "item", value = "minecraft:iron_chestplate", fallback = "minecraft:barrier", alt = "Iron chestplate" }` | `PS-A11Y-001` | `icon` | `client_visible` | `replace` |
| `id` | `resource_location` | — | — | Stable typed identity; when written explicitly it must match the source-derived id. | `mypack:combat/physique` | `PS-ID-002` | `resource_location` | `client_visible` | `replace` |
| `schema_version` (required) | `integer` | `2` | — | Authoring schema version compiled into the canonical IR. | `2` | `PS-SCHEMA-001` | `integer` | `client_visible` | `replace` |
| `search_aliases` | `list` | — | `[]` | Bounded, normalized alternate terms used by presentation search. | `["Strength", "Might"]` | `PS-I18N-001` | `list` | `client_visible` | `set` |

Cross-field constraints:

- `required_together` → `display`, `icon` (`PS-I18N-001`): Display and icon are either both present or both omitted.
- `requires` from `description` → `display`, `icon` (`PS-I18N-001`): A description is presentation metadata and requires display plus icon.
- `requires` from `search_aliases` → `display`, `icon` (`PS-I18N-001`): Non-default search aliases require display plus icon.

## Definition layer metadata

- Schema ID: `progressiveskills:definition_layer`
- Version: `2`
- Audience: `authoring`

Reserved top-level metadata controlling deterministic definition collisions.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `expected_old_digest` | `string` | — | — | Optional lowercase SHA-256 precondition for replace. | `7a9f3d3d2d7d30fc4ff59ef1dbacb9bcf1f0f198f7e0c5d618d66c5c8b112233` | `PS-PACK-006` | `single_line` | `client_visible` | `replace` |
| `merge_intent` | `enum` | `add \| replace \| merge \| patch \| disable` | `"add"` | Explicit collision behavior for this source file. | `add` | `PS-PACK-006` | `select` | `client_visible` | `replace` |
| `patches` | `list` | — | `[]` | Ordered explicit patches; valid only for patch intent. | `[{ op = "set", path = "fallback", value = "Updated" }]` | `PS-PACK-006` | `list` | `client_visible` | `ordered` |
| `schema_version` (required) | `integer` | `2` | — | Definition authoring schema version. | `2` | `PS-SCHEMA-001` | `integer` | `client_visible` | `replace` |

Cross-field constraints:

- `requires` from `expected_old_digest` → `merge_intent` (`PS-PACK-006`): An old-digest precondition is meaningful only with replace intent.

## Definition patch operation

- Schema ID: `progressiveskills:definition_patch`
- Version: `2`
- Audience: `authoring`

One bounded path-addressed mutation applied before typed schema validation.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `op` (required) | `enum` | `set \| remove \| append \| prepend \| replace_by_id` | — | Patch operation. | `set` | `PS-PACK-006` | `select` | `client_visible` | `replace` |
| `path` (required) | `string` | — | — | Dotted schema field path. | `style.color` | `PS-PACK-006` | `single_line` | `client_visible` | `replace` |
| `target_id` | `resource_location` | — | — | Stable nested id selected by replace_by_id. | `mypack:tree/node` | `PS-ID-001` | `resource_location` | `client_visible` | `replace` |
| `value` | `any` | — | — | Typed replacement or list value; forbidden for remove. | `red` | `PS-SCHEMA-005` | `object` | `client_visible` | `replace` |

## Icon specification

- Schema ID: `progressiveskills:icon_spec`
- Version: `2`
- Audience: `authoring`

Registry-neutral visual descriptor resolved only at a later presentation boundary.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `alt` (required) | `component` | — | — | Required narration and nonvisual equivalent. | `Iron chestplate` | `PS-A11Y-001` | `component` | `client_visible` | `replace` |
| `entity_preview_opt_in` | `boolean` | — | `false` | Explicitly permits an entity preview descriptor. | `false` | `PS-SCHEMA-006` | `checkbox` | `client_visible` | `replace` |
| `fallback` (required) | `resource_location` | — | — | Safe fallback reference used when the preferred icon cannot resolve. | `minecraft:barrier` | `PS-SCHEMA-006` | `resource_location` | `client_visible` | `replace` |
| `narration` | `component` | — | `field(alt)` | Optional narration text; defaults to the alt component. | `Physique skill icon` | `PS-A11Y-001` | `component` | `client_visible` | `replace` |
| `type` (required) | `enum` | `item \| block \| texture \| atlas_sprite \| player_head \| entity_preview \| cycling_tag \| composite_badge` | — | Tagged icon descriptor kind. | `item` | `PS-SCHEMA-006` | `select` | `client_visible` | `replace` |
| `value` | `resource_location` | — | — | Single namespaced registry, texture, tag, or typed pack-spec identity. | `minecraft:iron_chestplate` | `PS-SCHEMA-006` | `resource_location` | `client_visible` | `replace` |
| `values` | `list` | — | — | Ordered layer references used only by composite badges. | `["mypack:base", "mypack:badge"]` | `PS-SCHEMA-006` | `list` | `client_visible` | `ordered` |

Cross-field constraints:

- `exactly_one` → `value`, `values` (`PS-SCHEMA-006`): Use value for a single/tag identity or values for composite badge layers.

## Content-pack manifest

- Schema ID: `progressiveskills:pack_manifest`
- Version: `2`
- Audience: `authoring`

Identity, compatibility, dependencies, precedence, and fail-closed policy for one content pack.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `dependencies.incompatible_mods` | `list` | — | `[]` | Loaded mod ids that make this pack invalid. | `["incompatible_mod"]` | `PS-PACK-003` | `list` | `client_visible` | `set` |
| `dependencies.optional_mods` | `list` | — | `[]` | Optional mod ids used only by explicitly guarded branches. | `["curios"]` | `PS-PACK-003` | `list` | `client_visible` | `set` |
| `dependencies.optional_packs` | `list` | — | `[]` | Optional pack ids with optional @version constraints. | `["mypack:magic@>=1.0.0"]` | `PS-PACK-003` | `list` | `client_visible` | `set` |
| `dependencies.required_mods` | `list` | — | `[]` | Mod ids that must be loaded for this pack. | `["examplemod"]` | `PS-PACK-003` | `list` | `client_visible` | `set` |
| `dependencies.required_packs` | `list` | — | `[]` | Pack ids that must load first and satisfy optional @version constraints. | `["progressiveskills:base@>=1.0.0"]` | `PS-PACK-003` | `list` | `client_visible` | `set` |
| `pack.authors` | `list` | — | `[]` | Bounded author display names. | `["Pack Team"]` | `PS-PACK-002` | `list` | `client_visible` | `ordered` |
| `pack.changelog_url` | `string` | — | — | Optional bounded changelog location retained as pack metadata. | `https://example.invalid/mypack/changelog` | `PS-PACK-002` | `single_line` | `client_visible` | `replace` |
| `pack.content_version` (required) | `string` | — | — | Strict SemVer content version used by pack dependencies. | `3.2.0` | `PS-PACK-002` | `single_line` | `client_visible` | `replace` |
| `pack.default_layout` | `resource_location` | — | — | Optional default layout definition used by later presentation phases. | `mypack:character_default` | `PS-PACK-002` | `resource_location` | `client_visible` | `replace` |
| `pack.default_locale` | `string` | — | `"en_us"` | Lowercase language_country fallback locale. | `en_us` | `PS-PACK-002` | `single_line` | `client_visible` | `replace` |
| `pack.default_theme` | `resource_location` | — | — | Optional default theme definition used by later presentation phases. | `mypack:dark_rpg` | `PS-PACK-002` | `resource_location` | `client_visible` | `replace` |
| `pack.description` | `string` | — | — | Optional bounded pack description retained as metadata. | `An example progression pack.` | `PS-PACK-002` | `multi_line` | `client_visible` | `replace` |
| `pack.engine` (required) | `string` | — | — | Bounded engine SemVer range. | `>=1.0.0 <2.0.0` | `PS-PACK-002` | `single_line` | `client_visible` | `replace` |
| `pack.exported_asset_pack_id` | `resource_location` | — | — | Optional identity of a separately deployed client asset pack. | `mypack:client_assets` | `PS-PACK-002` | `resource_location` | `client_visible` | `replace` |
| `pack.feature_flags` | `list` | — | `[]` | Declared feature labels retained for compatibility diagnostics. | `["core_progression"]` | `PS-PACK-002` | `list` | `client_visible` | `set` |
| `pack.homepage` | `string` | — | — | Optional bounded project homepage retained as pack metadata. | `https://example.invalid/mypack` | `PS-PACK-002` | `single_line` | `client_visible` | `replace` |
| `pack.id` (required) | `resource_location` | — | — | Stable content-pack identity whose namespace owns definitions. | `mypack:core` | `PS-PACK-002` | `resource_location` | `client_visible` | `replace` |
| `pack.license` (required) | `string` | — | — | Pack redistribution license label. | `All-Rights-Reserved` | `PS-PACK-002` | `single_line` | `client_visible` | `replace` |
| `pack.name` (required) | `component` | — | — | Localized pack display name with required fallback. | `{ key = "pack.mypack.core", fallback = "My Pack" }` | `PS-I18N-001` | `component` | `client_visible` | `replace` |
| `pack.namespace` (required) | `string` | — | — | Definition namespace; must equal the pack id namespace. | `mypack` | `PS-PACK-002` | `single_line` | `client_visible` | `replace` |
| `pack.priority` | `integer` | — | `0` | Precedence within one root tier; larger values apply later. | `100` | `PS-PACK-002` | `integer` | `client_visible` | `replace` |
| `pack.source` | `string` | — | — | Optional bounded source repository location retained as pack metadata. | `https://example.invalid/mypack/source` | `PS-PACK-002` | `single_line` | `client_visible` | `replace` |
| `pack.trusted_scripts` | `boolean` | — | `false` | Declares that the pack contains trusted-script content; it never grants trust by itself. | `false` | `PS-PACK-002` | `checkbox` | `client_visible` | `replace` |
| `policies.duplicate_id` | `enum` | `error` | `"error"` | Duplicate definition policy; Core fails closed. | `error` | `PS-PACK-006` | `select` | `client_visible` | `replace` |
| `policies.merge_conflict` | `enum` | `error` | `"error"` | Ambiguous merge policy; Core fails closed. | `error` | `PS-PACK-006` | `select` | `client_visible` | `replace` |
| `policies.missing_optional` | `enum` | `skip_declared_branch` | `"skip_declared_branch"` | Behavior for declared optional branches. | `skip_declared_branch` | `PS-PACK-003` | `select` | `client_visible` | `replace` |
| `policies.missing_required` | `enum` | `reject_pack` | `"reject_pack"` | Behavior for missing required dependencies; Core rejects the pack. | `reject_pack` | `PS-PACK-003` | `select` | `client_visible` | `replace` |
| `policies.secret_projection` | `enum` | `redact` | `"redact"` | Server-only field handling for future client projections. | `redact` | `PS-PACK-002` | `select` | `client_visible` | `replace` |
| `policies.unknown_field` | `enum` | `error` | `"error"` | Unknown manifest/definition field handling. | `error` | `PS-SCHEMA-002` | `select` | `client_visible` | `replace` |
| `schema_version` (required) | `integer` | `2` | — | Manifest authoring schema version. | `2` | `PS-SCHEMA-001` | `integer` | `client_visible` | `replace` |

## Source span

- Schema ID: `progressiveskills:source_span`
- Version: `2`
- Audience: `internal`

Normalized field provenance kept outside semantic equality and future digests.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `end` (required) | `object` | — | — | Exclusive one-based ending position. | `{ line = 4, column = 8 }` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |
| `source` (required) | `string` | — | — | POSIX relative source identifier without a host path. | `skills/combat/physique.toml` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |
| `start` (required) | `object` | — | — | Inclusive one-based starting position. | `{ line = 4, column = 1 }` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |

## Safe style specification

- Schema ID: `progressiveskills:style_spec`
- Version: `2`
- Audience: `authoring`

Allowlisted visual styling with no click actions, selectors, NBT, or URLs.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `bold` | `boolean` | — | `false` | Render text with bold emphasis. | `true` | `PS-SEC-001` | `checkbox` | `client_visible` | `replace` |
| `color` | `string` | — | — | Named vanilla color or six-digit RGB color. | `red` | `PS-SEC-001` | `single_line` | `client_visible` | `replace` |
| `font` | `resource_location` | — | — | Optional namespaced font reference. | `minecraft:default` | `PS-SEC-001` | `resource_location` | `client_visible` | `replace` |
| `italic` | `boolean` | — | `false` | Render text with italic emphasis. | `false` | `PS-SEC-001` | `checkbox` | `client_visible` | `replace` |
| `obfuscated` | `boolean` | — | `false` | Render text with vanilla obfuscation. | `false` | `PS-SEC-001` | `checkbox` | `client_visible` | `replace` |
| `strikethrough` | `boolean` | — | `false` | Render text with a strike line. | `false` | `PS-SEC-001` | `checkbox` | `client_visible` | `replace` |
| `underlined` | `boolean` | — | `false` | Render text with an underline. | `false` | `PS-SEC-001` | `checkbox` | `client_visible` | `replace` |

## Diagnostic catalog

<a id="ps-a11y-001"></a>

### PS-A11Y-001 — Missing icon alternative text

- Default severity: `error`
- Suppressible: `true`
- Why it matters: Icons require a textual equivalent for narration and nonvisual use.
- Suggested fix: Add a short, meaningful alt component.

<a id="ps-i18n-001"></a>

### PS-I18N-001 — Invalid component specification

- Default severity: `error`
- Suppressible: `true`
- Why it matters: Unbounded or malformed presentation data is unsafe to render or synchronize.
- Suggested fix: Provide a bounded localization key and fallback text.

<a id="ps-i18n-002"></a>

### PS-I18N-002 — Invalid component placeholder

- Default severity: `error`
- Suppressible: `true`
- Why it matters: Placeholder names and types must agree across locales and call sites.
- Suggested fix: Declare each placeholder once with its canonical type.

<a id="ps-id-001"></a>

### PS-ID-001 — Invalid stable identity

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Persistence and references require an explicit, normalized namespaced id.
- Suggested fix: Use a lowercase namespace:path id derived from the definition path.

<a id="ps-id-002"></a>

### PS-ID-002 — Explicit id does not match its source path

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A mismatch makes file renames and persisted identity ambiguous.
- Suggested fix: Remove the explicit id or make it equal the path-derived id.

<a id="ps-id-003"></a>

### PS-ID-003 — Invalid identity alias

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Ambiguous, cyclic, or cross-kind aliases can corrupt reference migration.
- Suggested fix: Use one acyclic same-kind old-id to new-id mapping.

<a id="ps-pack-001"></a>

### PS-PACK-001 — Content-pack discovery failed

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A pack root or source path could not be inspected safely.
- Suggested fix: Use readable regular directories and files without symbolic links.

<a id="ps-pack-002"></a>

### PS-PACK-002 — Invalid content-pack manifest

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Pack identity, compatibility, dependencies, and policy must be known before definitions load.
- Suggested fix: Correct pack.toml using the generated manifest schema.

<a id="ps-pack-003"></a>

### PS-PACK-003 — Missing or incompatible pack dependency

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Loading without a required compatible pack would leave unresolved content.
- Suggested fix: Install a version matching the declared range or update the dependency declaration.

<a id="ps-pack-004"></a>

### PS-PACK-004 — Content-pack dependency cycle

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A deterministic load order cannot be produced from a dependency cycle.
- Suggested fix: Remove one dependency edge and use explicit layered merge intent instead.

<a id="ps-pack-005"></a>

### PS-PACK-005 — Content-pack identity collision

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Two discovered packs cannot own the same stable pack identity.
- Suggested fix: Assign one pack a distinct namespaced [pack].id.

<a id="ps-pack-006"></a>

### PS-PACK-006 — Definition merge conflict

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A collision without compatible explicit merge semantics would make load order ambiguous.
- Suggested fix: Choose add, replace, merge, patch, or disable and satisfy that operation's preconditions.

<a id="ps-pack-007"></a>

### PS-PACK-007 — Pack dependency contradicts precedence

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A dependency cannot safely load first when its root tier or explicit priority says it must load later.
- Suggested fix: Move the dependency to an equal/lower tier and priority, or raise the dependent pack's precedence.

<a id="ps-pack-008"></a>

### PS-PACK-008 — Definition id is shared across kinds

- Default severity: `warning`
- Suppressible: `true`
- Why it matters: Typed keys remain unambiguous, but identical ids across kinds make references and provenance harder to read.
- Suggested fix: Give one definition a distinct path/id unless the shared spelling is deliberate.

<a id="ps-reload-001"></a>

### PS-RELOAD-001 — No validated reload is staged

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Publishing must use the exact snapshot that was reviewed during dry-run.
- Suggested fix: Run /ps reload --dry-run, resolve errors, then publish that staged snapshot.

<a id="ps-reload-002"></a>

### PS-RELOAD-002 — Staged reload is blocked

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A snapshot containing structural errors cannot replace the live last-known-good registry.
- Suggested fix: Run /ps validate, correct every error, and stage again.

<a id="ps-reload-003"></a>

### PS-RELOAD-003 — Last-known-good recovery failed

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Neither current content nor a verified recovery bundle could produce a safe live snapshot.
- Suggested fix: Restore a valid pack source or a complete world backup and validate again.

<a id="ps-schema-001"></a>

### PS-SCHEMA-001 — Unsupported schema version

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Definitions must compile through a known, versioned contract.
- Suggested fix: Use schema_version = 2 or run an available migration.

<a id="ps-schema-002"></a>

### PS-SCHEMA-002 — Unknown schema field

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Misspelled or future fields cannot be interpreted deterministically.
- Suggested fix: Use a documented field name for this schema version.

<a id="ps-schema-003"></a>

### PS-SCHEMA-003 — Duplicate schema registration

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Two owners cannot define the same schema identity safely.
- Suggested fix: Keep one registration or assign a distinct namespaced id.

<a id="ps-schema-004"></a>

### PS-SCHEMA-004 — Invalid source span

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Diagnostics and provenance must point to a valid bounded source range.
- Suggested fix: Use a normalized relative source and a valid half-open line/column range.

<a id="ps-schema-005"></a>

### PS-SCHEMA-005 — Invalid canonical value

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Canonical IR accepts only bounded typed values and immutable collections.
- Suggested fix: Compile the field to its declared canonical value shape.

<a id="ps-schema-006"></a>

### PS-SCHEMA-006 — Invalid icon specification

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Icon kinds, references, fallbacks, and preview policy must form one bounded descriptor.
- Suggested fix: Use a documented icon kind with the required namespaced references and fallback.

<a id="ps-schema-007"></a>

### PS-SCHEMA-007 — Definition schema is not available

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Accepting a definition before its typed compiler exists would create false validation claims.
- Suggested fix: Use a definition kind implemented by this build or wait for its feature phase.

<a id="ps-sec-001"></a>

### PS-SEC-001 — Unsafe presentation content

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Commands, URLs, selectors, NBT, and other interpreted content cross trust boundaries.
- Suggested fix: Use the bounded ComponentSpec subset only.

<a id="ps-toml-001"></a>

### PS-TOML-001 — Malformed TOML source

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Invalid TOML cannot compile into deterministic canonical data.
- Suggested fix: Correct the reported file and field using a TOML-aware editor.

<a id="ps-toml-002"></a>

### PS-TOML-002 — Content source exceeds a safety limit

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Unbounded file counts, nesting, or bytes can exhaust server resources during reload.
- Suggested fix: Split or reduce the pack so it stays within the documented hard ceilings.
