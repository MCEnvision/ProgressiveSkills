# ProgressiveSkills Schema Reference v2

> Generated from `CoreSchemas`; edit the registry metadata, then regenerate this file.

Schema v2 includes the shared immutable IR, authoring schemas, and internal runtime contracts implemented through Phase 6. Gameplay definition schemas arrive with their implementation phases.

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

## Transaction audit record

- Schema ID: `progressiveskills:audit_record`
- Version: `2`
- Audience: `internal`

Bounded terminal mutation evidence retaining provenance, revisions, outputs, and rollback classification.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `action_results` (required) | `list` | — | — | Ordered transition dispositions and delivery details. | `[]` | `PS-TX-005` | `object` | `server_only` | `replace` |
| `after_revision` (required) | `integer` | — | — | Monotonic committed revision or unchanged rejection revision. | `13` | `PS-TX-001` | `object` | `server_only` | `replace` |
| `before_revision` (required) | `integer` | — | — | Captured target revision. | `12` | `PS-TX-001` | `object` | `server_only` | `replace` |
| `completed_at` (required) | `string` | — | — | Authoritative server completion instant. | `2026-07-16T12:00:00Z` | `PS-TX-004` | `object` | `server_only` | `replace` |
| `definition_revision` (required) | `object` | — | — | Generation and semantic digest used by the plan. | `{ generation = 7 }` | `PS-TX-003` | `object` | `server_only` | `replace` |
| `projection_changes` (required) | `list` | — | — | Source-resolved persistent diff. | `[]` | `PS-TX-007` | `object` | `server_only` | `replace` |
| `reversible` (required) | `boolean` | — | — | Whether this retained action-free boundary can still be rolled back. | `false` | `PS-TX-008` | `object` | `server_only` | `replace` |
| `status` (required) | `enum` | `committed \| committed_with_action_failures \| rejected` | — | Terminal transaction state. | `committed` | `PS-TX-004` | `object` | `server_only` | `replace` |
| `transaction_id` (required) | `string` | — | — | Stable transaction UUID derived from the target and idempotency key. | `00000000-0000-0000-0000-000000000004` | `PS-TX-006` | `object` | `server_only` | `replace` |

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

## Sanitized definition projection

- Schema ID: `progressiveskills:definition_projection`
- Version: `2`
- Audience: `internal`

Client-safe typed definition identity and presentation; gameplay fields and provenance are absent.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `description` (required) | `component` | — | — | Optional localized description with bounded fallback. | `{ key = "skill.mypack.physique.desc", fallback = "Raw power." }` | `PS-I18N-001` | `object` | `client_visible` | `replace` |
| `display` (required) | `component` | — | — | Optional localized display component with bounded fallback. | `{ key = "skill.mypack.physique", fallback = "Physique" }` | `PS-I18N-001` | `object` | `client_visible` | `replace` |
| `icon` (required) | `icon` | — | — | Optional bounded icon, fallback, alt text, and narration. | `{ type = "item", value = "minecraft:iron_chestplate" }` | `PS-SCHEMA-006` | `object` | `client_visible` | `replace` |
| `key` (required) | `string` | — | — | Typed definition kind and namespaced identity. | `progressiveskills:skill[mypack:physique]` | `PS-ID-001` | `object` | `client_visible` | `replace` |
| `search_aliases` (required) | `list` | — | — | Bounded presentation-only search terms. | `["Strength", "Might"]` | `PS-I18N-001` | `object` | `client_visible` | `replace` |

## Persistent entitlement contribution

- Schema ID: `progressiveskills:entitlement_contribution`
- Version: `2`
- Audience: `internal`

One source-owned long value resolved with every co-owner before physical projection.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `key` (required) | `object` | — | — | Typed persistent target. | `{ target_type = "progressiveskills:attribute", target_id = "minecraft:generic.max_health" }` | `PS-TX-004` | `object` | `server_only` | `replace` |
| `resolver` (required) | `enum` | `additive \| highest \| lowest \| boolean_union` | — | Shared deterministic co-owner resolver. | `highest` | `PS-TX-004` | `object` | `server_only` | `replace` |
| `source` (required) | `object` | — | — | Typed grant source whose revocation removes only its contribution. | `{ owner_kind = "progressiveskills:class", owner_id = "mypack:warrior" }` | `PS-TX-004` | `object` | `server_only` | `replace` |
| `value` (required) | `integer` | — | — | Checked contribution value. | `4` | `PS-TX-004` | `object` | `server_only` | `replace` |

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

## Network handshake

- Schema ID: `progressiveskills:network_handshake`
- Version: `2`
- Audience: `internal`

Connection-scoped protocol, feature, server identity, and semantic/presentation revision contract.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `definition_generation` (required) | `integer` | — | — | Monotonic server gameplay-definition generation. | `7` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `features` (required) | `integer` | — | — | Required bounded protocol feature bitset. | `15` | `PS-NET-001` | `object` | `client_visible` | `replace` |
| `presentation_digest` (required) | `string` | — | — | SHA-256 of the exact sanitized definition projection bytes. | `bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb` | `PS-NET-002` | `object` | `client_visible` | `replace` |
| `presentation_revision` (required) | `integer` | — | — | Monotonic presentation generation negotiated independently. | `7` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `protocol_version` (required) | `integer` | — | — | ProgressiveSkills application protocol version. | `1` | `PS-NET-001` | `object` | `client_visible` | `replace` |
| `semantic_digest` (required) | `string` | — | — | SHA-256 of the authoritative gameplay definition snapshot. | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `server_identity` (required) | `string` | — | — | Persistent world/server UUID that scopes the local definition cache. | `00000000-0000-0000-0000-000000000601` | `PS-NET-001` | `object` | `client_visible` | `replace` |
| `session_id` (required) | `string` | — | — | Ephemeral connection session UUID required on every later payload. | `00000000-0000-0000-0000-000000000602` | `PS-NET-003` | `object` | `client_visible` | `replace` |

## Bounded client intent

- Schema ID: `progressiveskills:network_intent`
- Version: `2`
- Audience: `internal`

Serverbound request identity and stale guards; clients never provide costs, XP, or effect amounts.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `definition_generation` (required) | `integer` | — | — | Client-observed gameplay definition generation. | `7` | `PS-NET-003` | `object` | `server_only` | `replace` |
| `intent_type` (required) | `enum` | `noop_test` | — | Closed server-registered intent family. | `noop_test` | `PS-NET-001` | `object` | `server_only` | `replace` |
| `payload` (required) | `string` | — | — | Small type-specific bounded selection payload; never effect amounts or commands. | `""` | `PS-NET-002` | `object` | `server_only` | `replace` |
| `request_id` (required) | `integer` | — | — | Monotonic request id covered by the bounded replay/result window. | `12` | `PS-NET-004` | `object` | `server_only` | `replace` |
| `semantic_digest` (required) | `string` | — | — | Client-observed gameplay SHA-256. | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` | `PS-NET-003` | `object` | `server_only` | `replace` |
| `session_id` (required) | `string` | — | — | Current connection session UUID. | `00000000-0000-0000-0000-000000000602` | `PS-NET-003` | `object` | `server_only` | `replace` |
| `state_revision` (required) | `integer` | — | — | Client-observed authoritative state revision. | `3` | `PS-NET-003` | `object` | `server_only` | `replace` |

## Durable operation receipt

- Schema ID: `progressiveskills:operation_receipt`
- Version: `2`
- Audience: `internal`

Bounded same-attachment evidence that an offline or death-copy operation completed.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `applied_at` (required) | `integer` | — | — | Authoritative completion epoch milliseconds. | `1784203200000` | `PS-DATA-001` | `object` | `server_only` | `replace` |
| `detail` (required) | `string` | — | — | Bounded operator-readable completion detail. | `Pending offline operation applied` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `operation_id` (required) | `string` | — | — | Stable operation identity used for exact replay suppression. | `offline/fixture/1` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `operation_type` (required) | `string` | — | — | Bounded operation family. | `offline` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `resulting_revision` (required) | `integer` | — | — | Transaction revision that contains the completed operation. | `12` | `PS-TX-001` | `object` | `server_only` | `replace` |
| `transaction_id` (required) | `string` | — | — | Transaction identity associated with the operation. | `00000000-0000-0000-0000-000000000003` | `PS-TX-006` | `object` | `server_only` | `replace` |

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

## Pending offline progression operation

- Schema ID: `progressiveskills:pending_progression_operation`
- Version: `2`
- Audience: `internal`

Version- and definition-pinned operation applied only while the target player is online.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `balance_id` (required) | `resource_location` | — | — | Canonical balance definition identity. | `mypack:renown` | `PS-TX-003` | `object` | `server_only` | `replace` |
| `created_at` (required) | `integer` | — | — | Authoritative creation epoch milliseconds. | `1784203200000` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `definition_digest` (required) | `string` | — | — | Pinned lowercase SHA-256 definition digest. | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` | `PS-TX-003` | `object` | `server_only` | `replace` |
| `definition_generation` (required) | `integer` | — | — | Pinned live definition generation. | `7` | `PS-TX-003` | `object` | `server_only` | `replace` |
| `delta` (required) | `integer` | — | — | Checked signed balance delta. | `25` | `PS-TX-002` | `object` | `server_only` | `replace` |
| `expires_at` (required) | `integer` | — | — | Hard expiry epoch milliseconds after which the operation is quarantined. | `1784289600000` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `issuer_id` (required) | `string` | — | — | Authoritative issuer UUID. | `00000000-0000-0000-0000-000000000001` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `last_attempt_id` | `string` | — | — | Login-attempt UUID retained until later receipt confirmation. | `00000000-0000-0000-0000-000000000004` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `maximum` (required) | `integer` | — | — | Inclusive checked post-mutation ceiling. | `1000` | `PS-TX-002` | `object` | `server_only` | `replace` |
| `minimum` (required) | `integer` | — | — | Inclusive checked post-mutation floor. | `0` | `PS-TX-002` | `object` | `server_only` | `replace` |
| `operation_id` (required) | `string` | — | — | Stable queue and receipt identity. | `offline/fixture/1` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `quarantine_reason` | `string` | — | — | Bounded evidence when the operation cannot apply safely. | `Pending operation expired` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `reward_eligible` (required) | `boolean` | — | — | Whether the operation may participate in an explicitly allowed reward path. | `false` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `status` (required) | `enum` | `PENDING \| QUARANTINED` | — | Pending or quarantined queue state. | `PENDING` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `target_id` (required) | `string` | — | — | Target player UUID; no offline player NBT is opened. | `00000000-0000-0000-0000-000000000002` | `PS-DATA-007` | `object` | `server_only` | `replace` |

## Versioned player data attachment

- Schema ID: `progressiveskills:player_data_attachment`
- Version: `2`
- Audience: `internal`

Bounded durable progression state with transaction truth, migration evidence, and fail-closed quarantine.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `data_version` (required) | `integer` | — | — | Persisted attachment contract version. | `2` | `PS-DATA-003` | `object` | `server_only` | `replace` |
| `death_marker` | `object` | — | — | Two-step death-copy operation marker and completion receipt. | `{ transaction_id = "00000000-0000-0000-0000-000000000004" }` | `PS-DATA-001` | `object` | `server_only` | `replace` |
| `definition_states` (required) | `list` | — | — | Bounded typed states keyed by stable definition identity and lineage. | `[]` | `PS-DATA-004` | `object` | `server_only` | `replace` |
| `extensions` | `map` | — | — | Unknown bounded fields retained for forward-compatible round trips. | `{}` | `PS-DATA-002` | `object` | `server_only` | `replace` |
| `migration_shadow` | `object` | — | — | Bounded pre-migration raw evidence retained through the first successful save. | `{ source_version = 1 }` | `PS-DATA-003` | `object` | `server_only` | `replace` |
| `operation_receipts` (required) | `list` | — | — | Exact same-attachment receipts for death and offline operation completion. | `[]` | `PS-DATA-005` | `object` | `server_only` | `replace` |
| `orphans` (required) | `list` | — | — | Persisted definition state awaiting a compatible definition or explicit replacement. | `[]` | `PS-DATA-004` | `object` | `server_only` | `replace` |
| `player_id` (required) | `string` | — | — | UUID that must match the attachment owner. | `00000000-0000-0000-0000-000000000001` | `PS-DATA-007` | `object` | `server_only` | `replace` |
| `quarantine` | `object` | — | — | Fail-closed reason, digest, and bounded raw evidence. | `{ reason = "future data version" }` | `PS-DATA-001` | `object` | `server_only` | `replace` |
| `state_definition` | `object` | — | — | Definition generation and digest associated with the persisted account. | `{ generation = 7 }` | `PS-TX-003` | `object` | `server_only` | `replace` |
| `status` (required) | `enum` | `ACTIVE \| QUARANTINED` | — | Active or quarantined projection state. | `ACTIVE` | `PS-DATA-001` | `object` | `server_only` | `replace` |
| `storage_revision` (required) | `integer` | — | — | Monotonic attachment mutation revision. | `12` | `PS-TX-001` | `object` | `server_only` | `replace` |
| `transaction` (required) | `object` | — | — | Exact revision, balances, ownership, receipts, replay results, and audit state. | `{ state_revision = 12 }` | `PS-TX-006` | `object` | `server_only` | `replace` |

## Player data snapshot

- Schema ID: `progressiveskills:player_data_snapshot`
- Version: `2`
- Audience: `internal`

Atomically written, reread, and digest-verified recovery envelope for one attachment.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `created_at` (required) | `integer` | — | — | Authoritative snapshot epoch milliseconds. | `1784203200000` | `PS-DATA-006` | `object` | `server_only` | `replace` |
| `player_data` (required) | `object` | — | — | Bounded serialized player attachment. | `{ data_version = 2 }` | `PS-DATA-001` | `object` | `server_only` | `replace` |
| `player_data_digest` (required) | `string` | — | — | SHA-256 digest verified after the atomic write. | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` | `PS-DATA-006` | `object` | `server_only` | `replace` |
| `player_id` (required) | `string` | — | — | UUID whose attachment is enclosed. | `00000000-0000-0000-0000-000000000001` | `PS-DATA-007` | `object` | `server_only` | `replace` |
| `snapshot_version` (required) | `integer` | — | — | Snapshot envelope contract version. | `1` | `PS-DATA-006` | `object` | `server_only` | `replace` |

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

## Visible state delta

- Schema ID: `progressiveskills:state_delta`
- Version: `2`
- Audience: `internal`

Typed changed/removed paths applied only across an exact base-to-new revision edge.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `base_revision` (required) | `integer` | — | — | Required current client storage revision. | `5` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `changed_balances` (required) | `map` | — | — | Changed or added visible balance paths. | `{ "mypack:points" = 5 }` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `changed_effective_values` (required) | `map` | — | — | Changed or added effective-value paths. | `{}` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `new_revision` (required) | `integer` | — | — | Strictly newer resulting storage revision. | `6` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `removed_balances` (required) | `list` | — | — | Removed visible balance paths. | `[]` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `removed_effective_values` (required) | `list` | — | — | Removed effective-value paths. | `[]` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `resulting_state_digest` (required) | `string` | — | — | SHA-256 of the exact post-application full visible state. | `dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd` | `PS-NET-005` | `object` | `client_visible` | `replace` |

## Stored definition state

- Schema ID: `progressiveskills:stored_definition_state`
- Version: `2`
- Audience: `internal`

Versioned per-definition payload whose stable lineage supports explicit aliases and compatible restoration.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `id` (required) | `resource_location` | — | — | Stable definition identity. | `mypack:physique` | `PS-DATA-004` | `object` | `server_only` | `replace` |
| `kind` (required) | `resource_location` | — | — | Typed definition-kind identity. | `progressiveskills:skill` | `PS-DATA-004` | `object` | `server_only` | `replace` |
| `kind_directory` (required) | `string` | — | — | Source directory retained for provider-defined kinds. | `skills` | `PS-DATA-004` | `object` | `server_only` | `replace` |
| `lineage` (required) | `string` | — | — | Semantic lineage required for compatibility checks. | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` | `PS-DATA-004` | `object` | `server_only` | `replace` |
| `origin_lineage` (required) | `string` | — | — | Original lineage retained across an explicit identity replacement. | `bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb` | `PS-DATA-004` | `object` | `server_only` | `replace` |
| `payload` (required) | `object` | — | — | Bounded definition-specific NBT. | `{ level = 4 }` | `PS-DATA-002` | `object` | `server_only` | `replace` |
| `payload_version` (required) | `integer` | — | — | Definition payload contract version. | `1` | `PS-DATA-003` | `object` | `server_only` | `replace` |

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

## Progression transaction plan

- Schema ID: `progressiveskills:transaction_plan`
- Version: `2`
- Audience: `internal`

Bounded, revision- and definition-pinned root plan validated before any mutation.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `actor_id` (required) | `string` | — | — | Authoritative actor UUID. | `00000000-0000-0000-0000-000000000001` | `PS-TX-001` | `object` | `server_only` | `replace` |
| `cause` (required) | `enum` | `gameplay \| character_creation \| admin \| offline_operation \| migration \| reload \| reconcile` | — | Typed progression origin. | `gameplay` | `PS-TX-004` | `object` | `server_only` | `replace` |
| `definition_generation` (required) | `integer` | — | — | Pinned live definition generation. | `7` | `PS-TX-003` | `object` | `server_only` | `replace` |
| `expected_state_revision` (required) | `integer` | — | — | Compare-and-swap target revision. | `12` | `PS-TX-001` | `object` | `server_only` | `replace` |
| `idempotency_key` (required) | `string` | — | — | Bounded stable request identity. | `packet/session-1/request-42` | `PS-TX-006` | `object` | `server_only` | `replace` |
| `queued_children` (required) | `list` | — | — | Fully expanded bounded child steps in deterministic order. | `[]` | `PS-TX-004` | `object` | `server_only` | `replace` |
| `reason` (required) | `string` | — | — | Bounded audit reason. | `Award gameplay XP` | `PS-TX-004` | `object` | `server_only` | `replace` |
| `root_step` (required) | `object` | — | — | Root balance, ownership, and transition mutations. | `{ origin = "mypack:rule" }` | `PS-TX-004` | `object` | `server_only` | `replace` |
| `semantic_digest` (required) | `string` | — | — | Pinned lowercase SHA-256 definition digest. | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` | `PS-TX-003` | `object` | `server_only` | `replace` |
| `target_id` (required) | `string` | — | — | Authoritative target UUID. | `00000000-0000-0000-0000-000000000002` | `PS-TX-001` | `object` | `server_only` | `replace` |

## Bounded transfer envelope

- Schema ID: `progressiveskills:transfer_envelope`
- Version: `2`
- Audience: `internal`

Atomic compressed definition/full-state transfer split below conservative clientbound ceilings.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `chunk_count` (required) | `integer` | — | — | Declared total chunk count checked before allocation. | `4` | `PS-NET-002` | `object` | `client_visible` | `replace` |
| `compressed_bytes` (required) | `integer` | — | — | Total compressed bytes under the hard aggregate cap. | `49152` | `PS-NET-002` | `object` | `client_visible` | `replace` |
| `digest` (required) | `string` | — | — | SHA-256 of the uncompressed payload verified before activation. | `cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc` | `PS-NET-002` | `object` | `client_visible` | `replace` |
| `kind` (required) | `enum` | `definitions \| full_state` | — | Closed transfer family. | `definitions` | `PS-NET-002` | `object` | `client_visible` | `replace` |
| `session_id` (required) | `string` | — | — | Owning negotiated session UUID. | `00000000-0000-0000-0000-000000000602` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `transfer_id` (required) | `string` | — | — | Unique transfer UUID used by every chunk and ACK. | `00000000-0000-0000-0000-000000000603` | `PS-NET-002` | `object` | `client_visible` | `replace` |
| `uncompressed_bytes` (required) | `integer` | — | — | Expected output bytes bounded before decompression. | `65536` | `PS-NET-002` | `object` | `client_visible` | `replace` |

## Transition action

- Schema ID: `progressiveskills:transition_action`
- Version: `2`
- Audience: `internal`

Typed edge-only action with explicit repeat, delivery, and failure contracts.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `amount` (required) | `integer` | — | — | Positive bounded action quantity. | `1` | `PS-TX-005` | `object` | `server_only` | `replace` |
| `delivery_contract` (required) | `enum` | `effectively_once \| at_least_once \| best_effort` | — | Honest external delivery guarantee. | `effectively_once` | `PS-TX-005` | `object` | `server_only` | `replace` |
| `failure_policy` (required) | `enum` | `continue \| stop` | — | Whether later actions continue after post-commit failure. | `stop` | `PS-TX-005` | `object` | `server_only` | `replace` |
| `payload` (required) | `string` | — | — | Bounded adapter-specific typed payload. | `minecraft:gold_ingot` | `PS-TX-005` | `object` | `server_only` | `replace` |
| `repeat_policy` (required) | `enum` | `always \| once_per_transaction \| once_per_character` | — | Exact receipt scope or explicit always-repeat behavior. | `once_per_character` | `PS-TX-006` | `object` | `server_only` | `replace` |
| `source` (required) | `object` | — | — | Typed owner, definition, and nested grant identity. | `{ owner_kind = "progressiveskills:skill", owner_id = "mypack:physique" }` | `PS-TX-004` | `object` | `server_only` | `replace` |
| `type` (required) | `resource_location` | — | — | Registered physical action adapter type. | `progressiveskills:item` | `PS-TX-005` | `object` | `server_only` | `replace` |

## Visible player state

- Schema ID: `progressiveskills:visible_player_state`
- Version: `2`
- Audience: `internal`

Owner-only authoritative state projection without durable ledgers, provenance, or hidden definitions.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `balances` (required) | `map` | — | — | Bounded namespaced visible balances. | `{ "mypack:points" = 4 }` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `definition_generation` (required) | `integer` | — | — | Pinned gameplay definition generation. | `7` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `effective_values` (required) | `map` | — | — | Bounded effective values needed by current client presentation. | `{ "minecraft:attribute[minecraft:generic.max_health]" = 4 }` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `operation_receipt_count` (required) | `integer` | — | — | Visible diagnostic count without receipt contents. | `1` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `orphan_count` (required) | `integer` | — | — | Visible diagnostic count without orphan payload contents. | `0` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `player_id` (required) | `string` | — | — | UUID of the session owner. | `00000000-0000-0000-0000-000000000601` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `presentation_revision` (required) | `integer` | — | — | Pinned sanitized presentation generation. | `7` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `semantic_digest` (required) | `string` | — | — | Pinned gameplay SHA-256. | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `state_revision` (required) | `integer` | — | — | Authoritative transaction compare-and-swap revision. | `3` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `storage_revision` (required) | `integer` | — | — | Monotonic visible-state continuity revision. | `5` | `PS-NET-005` | `object` | `client_visible` | `replace` |

## Diagnostic catalog

<a id="ps-a11y-001"></a>

### PS-A11Y-001 — Missing icon alternative text

- Default severity: `error`
- Suppressible: `true`
- Why it matters: Icons require a textual equivalent for narration and nonvisual use.
- Suggested fix: Add a short, meaningful alt component.

<a id="ps-data-001"></a>

### PS-DATA-001 — Player progression data is quarantined

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Unknown, malformed, or unsafe persisted data cannot be projected without risking corruption.
- Suggested fix: Export the quarantined evidence, restore a verified snapshot, or install a compatible migration.

<a id="ps-data-002"></a>

### PS-DATA-002 — Player progression data exceeds a safety limit

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Unbounded NBT depth, entries, strings, arrays, or bytes can exhaust server resources.
- Suggested fix: Restore a bounded snapshot or reduce the persisted payload before importing it.

<a id="ps-data-003"></a>

### PS-DATA-003 — Player progression data migration failed

- Default severity: `error`
- Suppressible: `false`
- Why it matters: The stored data version could not be transformed into the current attachment contract.
- Suggested fix: Keep the migration shadow, restore a backup, and provide every required version step.

<a id="ps-data-004"></a>

### PS-DATA-004 — Persisted definition state is orphaned

- Default severity: `warning`
- Suppressible: `false`
- Why it matters: Its definition is missing, incompatible, or lacks an explicit identity replacement.
- Suggested fix: Restore the compatible definition or declare an unambiguous same-kind alias/replacement.

<a id="ps-data-005"></a>

### PS-DATA-005 — Pending offline operation is quarantined

- Default severity: `error`
- Suppressible: `false`
- Why it matters: The operation expired, exceeded a limit, or no longer matches its pinned definition.
- Suggested fix: Review the retained evidence and enqueue a newly validated operation if appropriate.

<a id="ps-data-006"></a>

### PS-DATA-006 — Player data snapshot or export failed

- Default severity: `error`
- Suppressible: `false`
- Why it matters: The bounded attachment could not be written and verified atomically.
- Suggested fix: Check world storage access and free space, then retry without modifying the source attachment.

<a id="ps-data-007"></a>

### PS-DATA-007 — Player data identity does not match its owner

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Loading one player's attachment for another player could transfer progression or receipts.
- Suggested fix: Quarantine the payload and restore data whose embedded UUID matches the attachment owner.

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

<a id="ps-net-001"></a>

### PS-NET-001 — Networking protocol or feature mismatch

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A client and server cannot exchange authoritative state under incompatible contracts.
- Suggested fix: Install matching ProgressiveSkills versions and reconnect.

<a id="ps-net-002"></a>

### PS-NET-002 — Bounded network transfer was rejected

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A chunk count, byte ceiling, timeout, decompression cap, or digest check failed.
- Suggested fix: Reconnect; if the error repeats, inspect both endpoints for mismatched or malformed payloads.

<a id="ps-net-003"></a>

### PS-NET-003 — Network intent revision is stale

- Default severity: `warning`
- Suppressible: `false`
- Why it matters: Executing an intent against different definitions or player state could duplicate or misprice it.
- Suggested fix: Wait for the targeted full resync, then submit a fresh intent.

<a id="ps-net-004"></a>

### PS-NET-004 — Network intent rate limit exceeded

- Default severity: `warning`
- Suppressible: `false`
- Why it matters: Unbounded client requests could consume server tick time or memory.
- Suggested fix: Wait briefly and retry a single current intent.

<a id="ps-net-005"></a>

### PS-NET-005 — Visible state resynchronization is required

- Default severity: `warning`
- Suppressible: `false`
- Why it matters: A revision gap, out-of-order delta, unknown path, or digest mismatch broke continuity.
- Suggested fix: Allow the bounded full-state transfer to complete before making another mutation.

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

<a id="ps-tx-001"></a>

### PS-TX-001 — Transaction state revision is stale

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Applying a plan to a different state revision could duplicate costs, rewards, or ownership changes.
- Suggested fix: Refresh the target state, rebuild the plan, and submit it with a new idempotency key.

<a id="ps-tx-002"></a>

### PS-TX-002 — Checked balance mutation was rejected

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A debit, credit, bound, or checked-arithmetic operation could not commit safely.
- Suggested fix: Correct the amount or affordability condition and rebuild the complete transaction plan.

<a id="ps-tx-003"></a>

### PS-TX-003 — Transaction definition generation is stale

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A plan cannot execute after its pinned gameplay definitions or semantic digest change.
- Suggested fix: Rebuild the plan against the current live definition generation.

<a id="ps-tx-004"></a>

### PS-TX-004 — Persistent lifecycle ownership is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Conflicting resolvers or malformed ownership would make effective values nondeterministic.
- Suggested fix: Use one registered resolver for every source contributing to the same entitlement.

<a id="ps-tx-005"></a>

### PS-TX-005 — Transition action was rejected or failed

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Transition actions run only on explicit edges and must pass bounded physical-adapter validation.
- Suggested fix: Correct the target, payload, delivery policy, or capacity issue and submit a fresh transaction.

<a id="ps-tx-006"></a>

### PS-TX-006 — Exact transaction ledger is full

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A transaction cannot proceed without durable idempotency or receipt truth.
- Suggested fix: Increase the configured hard capacity or complete the planned persistence/archive maintenance.

<a id="ps-tx-007"></a>

### PS-TX-007 — Persistent projection failed

- Default severity: `error`
- Suppressible: `false`
- Why it matters: The source-resolved value could not be applied atomically to its physical target.
- Suggested fix: Inspect the target adapter and retry only with a newly validated transaction plan.

<a id="ps-tx-008"></a>

### PS-TX-008 — Transaction rollback is unavailable

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Transition actions or later mutations cross the safe reversible boundary.
- Suggested fix: Rollback only the latest retained action-free transaction or apply an explicit compensation.
