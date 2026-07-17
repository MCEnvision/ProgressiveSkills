# ProgressiveSkills Schema Reference v2

> Generated from `CoreSchemas`; edit the registry metadata, then regenerate this file.

Schema v2 includes the shared immutable IR, authoring schemas, and internal runtime contracts implemented through Phase 11. Gameplay definition schemas arrive with their implementation phases.

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

## Class respec or swap preview payload

- Schema ID: `progressiveskills:class_change_preview`
- Version: `2`
- Audience: `internal`

Clientbound revision pinned affected classes, authoritative currency costs, blockers, and confirmation digest.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `affected_classes` (required) | `list` | — | — | Bounded stable set of classes whose selected or active state changes. | `["mypack:mage", "mypack:warrior"]` | `PS-CLASS-005` | `object` | `client_visible` | `replace` |
| `blockers` (required) | `list` | — | — | Bounded reasons that prevent confirmation. | `[]` | `PS-CLASS-005` | `object` | `client_visible` | `replace` |
| `class_id` (required) | `resource_location` | — | — | Selected class to remove. | `mypack:mage` | `PS-CLASS-002` | `object` | `client_visible` | `replace` |
| `cost_balances` (required) | `map` | — | — | Authoritative nonnegative named currency totals charged by the change. | `{ "progressiveskills:global_points" = 2 }` | `PS-CLASS-005` | `object` | `client_visible` | `replace` |
| `definition_generation` (required) | `integer` | — | — | Definition generation used to calculate the preview. | `11` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `intent_type` (required) | `enum` | `class_respec_preview \| class_swap_preview` | — | Class respec or swap preview family. | `class_swap_preview` | `PS-NET-001` | `object` | `client_visible` | `replace` |
| `preview_digest` (required) | `string` | — | — | Lowercase SHA 256 covering the complete authoritative change. | `dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd` | `PS-CLASS-005` | `object` | `client_visible` | `replace` |
| `replacement_class_id` | `resource_location` | — | — | Replacement class present only for a swap. | `mypack:warrior` | `PS-CLASS-002` | `object` | `client_visible` | `replace` |
| `request_id` (required) | `integer` | — | — | Preview request identity returned to the requesting client. | `14` | `PS-NET-004` | `object` | `client_visible` | `replace` |
| `semantic_digest` (required) | `string` | — | — | Gameplay definition digest used by the preview. | `eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `session_id` (required) | `string` | — | — | Current connection session UUID. | `00000000-0000-0000-0000-000000000711` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `state_revision` (required) | `integer` | — | — | Authoritative state revision used by the preview. | `8` | `PS-NET-003` | `object` | `client_visible` | `replace` |

## Class definition

- Schema ID: `progressiveskills:class_definition`
- Version: `2`
- Audience: `authoring`

Bounded Core class with weighted slot use, prerequisites, costs, grants, and receipt protected starter kit.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `access_required` | `boolean` | — | `false` | Require a source owned class access entitlement before selection. | `false` | `PS-CLASS-002` | `checkbox` | `client_visible` | `replace` |
| `description` | `component` | — | — | Localized class description. | `{ fallback = "Arcane specialist." }` | `PS-CLASS-002` | `component` | `client_visible` | `replace` |
| `display` (required) | `component` | — | — | Localized class name. | `{ fallback = "Mage" }` | `PS-CLASS-002` | `component` | `client_visible` | `replace` |
| `enabled` | `boolean` | — | `true` | Whether new selections are accepted and retained prerequisites may remain active. | `true` | `PS-CLASS-002` | `checkbox` | `client_visible` | `replace` |
| `exclusive_tags` | `list` | — | `[]` | Stable coexistence tags that cannot overlap another selected class. | `["mypack:arcane_primary"]` | `PS-CLASS-002` | `list` | `client_visible` | `set` |
| `grants` | `list` | — | `[]` | Up to thirty two source owned persistent grants merged by stable grant id. | `[{ id = "mypack:mage/tree", type = "tree_access", tree = "mypack:arcane_tree" }]` | `PS-CLASS-007` | `list` | `client_visible` | `merge_by_key` |
| `icon` (required) | `icon` | — | — | Class icon with fallback and alternative text. | `{ type = "item", value = "minecraft:enchanted_book", fallback = "minecraft:barrier", alt = "Enchanted book" }` | `PS-CLASS-002` | `icon` | `client_visible` | `replace` |
| `prerequisites.classes` | `list` | — | `[]` | Selected classes that must all remain active. | `["mypack:apprentice"]` | `PS-CLASS-002` | `list` | `client_visible` | `set` |
| `prerequisites.min_level` | `map` | — | `{}` | Up to thirty two skill ids mapped to nonnegative minimum levels. | `{ "mypack:arcana" = 15 }` | `PS-CLASS-002` | `key_value` | `client_visible` | `replace` |
| `prerequisites.nodes` | `list` | — | `[]` | Owned Core tree nodes that must all remain owned. | `["mypack:arcane/root"]` | `PS-CLASS-002` | `list` | `client_visible` | `set` |
| `respec_allowed` | `boolean` | — | `true` | Whether a selected class may be removed by player respec or swap. | `true` | `PS-CLASS-002` | `checkbox` | `client_visible` | `replace` |
| `respec_cost` | `object` | — | — | Optional named currency charge for an allowed removal. | `{ currency = "progressiveskills:global_points", amount = 2 }` | `PS-CLASS-002` | `object` | `client_visible` | `replace` |
| `search_aliases` | `list` | — | `[]` | Bounded alternate class search terms. | `["Caster"]` | `PS-CLASS-002` | `list` | `client_visible` | `set` |
| `selection_cost` | `object` | — | — | Optional named currency charge sunk on selection and never refunded implicitly. | `{ currency = "progressiveskills:global_points", amount = 5 }` | `PS-CLASS-002` | `object` | `client_visible` | `replace` |
| `slot` (required) | `resource_location` | — | — | Pack defined class slot consumed by this class. | `mypack:combat` | `PS-CLASS-002` | `resource_location` | `client_visible` | `replace` |
| `slot_cost` (required) | `integer` | — | — | Weighted slot use. Zero represents an explicit background class. | `1` | `PS-CLASS-002` | `integer` | `client_visible` | `replace` |
| `starter_kit` | `list` | — | `[]` | Up to thirty two item ids delivered only on the first successful selection receipt. | `["minecraft:book"]` | `PS-CLASS-002` | `list` | `client_visible` | `ordered` |
| `synergy` | `list` | — | `[]` | Named bounded synergy definitions nested under their owning class. | `[{ id = "mypack:spellblade", requires_classes = ["mypack:mage", "mypack:warrior"] }]` | `PS-CLASS-003` | `list` | `client_visible` | `merge_by_key` |

## Class persistent grant

- Schema ID: `progressiveskills:class_grant`
- Version: `2`
- Audience: `authoring`

Source owned attribute, ability, spell, stage, tree access, or class access contribution.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `ability` | `resource_location` | — | — | Ability target required by ability grants. | `mypack:arcane_surge` | `PS-CLASS-007` | `resource_location` | `client_visible` | `replace` |
| `attribute` | `resource_location` | — | — | Attribute target required by attribute grants. | `minecraft:generic.max_health` | `PS-CLASS-007` | `resource_location` | `client_visible` | `replace` |
| `class` | `resource_location` | — | — | Class access target required by class access grants. | `mypack:berserker` | `PS-CLASS-007` | `resource_location` | `client_visible` | `replace` |
| `id` (required) | `resource_location` | — | — | Globally unique stable grant source identity. | `mypack:mage/arcane_tree` | `PS-CLASS-007` | `resource_location` | `client_visible` | `replace` |
| `learning` | `enum` | `require_existing \| satisfy_while_owned` | `"require_existing"` | Reversible spell learning satisfaction policy. | `require_existing` | `PS-CLASS-007` | `select` | `client_visible` | `replace` |
| `level` | `integer` | — | `1` | Bounded virtual spell level from one through two hundred fifty five. | `3` | `PS-CLASS-007` | `integer` | `client_visible` | `replace` |
| `operation` | `enum` | `add_value \| add_multiplied_base \| add_multiplied_total` | — | Deterministic attribute operation required by attribute grants. | `add_value` | `PS-CLASS-007` | `select` | `client_visible` | `replace` |
| `selection` | `enum` | `virtual_source` | `"virtual_source"` | Core spell selection ownership mode. | `virtual_source` | `PS-CLASS-007` | `select` | `client_visible` | `replace` |
| `spell` | `resource_location` | — | — | Spell target required by spell grants. | `irons_spellbooks:fireball` | `PS-CLASS-007` | `resource_location` | `client_visible` | `replace` |
| `stage` | `resource_location` | — | — | Stage target required by stage grants. | `mypack:arcane_access` | `PS-CLASS-007` | `resource_location` | `client_visible` | `replace` |
| `tree` | `resource_location` | — | — | Tree target required by tree access grants. | `mypack:arcane_tree` | `PS-CLASS-007` | `resource_location` | `client_visible` | `replace` |
| `type` (required) | `enum` | `attribute \| ability \| spell \| stage \| tree_access \| class_access` | — | Closed Core class grant type. | `tree_access` | `PS-CLASS-007` | `select` | `client_visible` | `replace` |
| `value` | `decimal` | — | — | Nonzero fixed point attribute value. Other typed values are derived. | `2.0` | `PS-CLASS-007` | `decimal` | `client_visible` | `replace` |

## Class mutation intent payload

- Schema ID: `progressiveskills:class_intent`
- Version: `2`
- Audience: `internal`

Bounded serverbound class selection with no client supplied costs, grants, capacity, or outcomes.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `class_id` (required) | `resource_location` | — | — | Selected class or class removed by a swap. | `mypack:mage` | `PS-CLASS-002` | `object` | `server_only` | `replace` |
| `preview_digest` | `string` | — | — | Required only by respec and swap confirmations. | `cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc` | `PS-CLASS-005` | `object` | `server_only` | `replace` |
| `replacement_class_id` | `resource_location` | — | — | Required only by swap preview and confirmation. | `mypack:warrior` | `PS-CLASS-002` | `object` | `server_only` | `replace` |

## Class slot definition

- Schema ID: `progressiveskills:class_slot_definition`
- Version: `2`
- Audience: `authoring`

Named weighted capacity bucket used by Core class selection and swap policy.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `capacity` (required) | `integer` | — | — | Positive capacity from one through sixty four. | `2` | `PS-CLASS-001` | `integer` | `client_visible` | `replace` |
| `description` | `component` | — | — | Localized class slot description. | `{ fallback = "Combat specializations." }` | `PS-CLASS-001` | `component` | `client_visible` | `replace` |
| `display` | `component` | — | — | Optional localized class slot name. | `{ fallback = "Combat" }` | `PS-CLASS-001` | `component` | `client_visible` | `replace` |
| `icon` | `icon` | — | — | Optional class slot icon with alternative text. | `{ type = "item", value = "minecraft:iron_sword", fallback = "minecraft:barrier", alt = "Iron sword" }` | `PS-CLASS-001` | `icon` | `client_visible` | `replace` |
| `search_aliases` | `list` | — | `[]` | Bounded alternate terms for later class search. | `["Role"]` | `PS-CLASS-001` | `list` | `client_visible` | `set` |
| `swap_policy` | `enum` | `allowed \| disabled` | `"allowed"` | Whether an atomic confirmed replacement is permitted in this slot. | `allowed` | `PS-CLASS-001` | `select` | `client_visible` | `replace` |

Cross-field constraints:

- `required_together` → `display`, `icon` (`PS-CLASS-001`): Class slot display and icon are declared together.

## Class synergy

- Schema ID: `progressiveskills:class_synergy`
- Version: `2`
- Audience: `authoring`

Named source owned grants active only while every required selected class remains active.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `description` | `component` | — | — | Optional localized synergy description. | `{ fallback = "Arcane martial training." }` | `PS-CLASS-003` | `component` | `client_visible` | `replace` |
| `display` | `component` | — | — | Optional localized synergy name. | `{ fallback = "Spellblade" }` | `PS-CLASS-003` | `component` | `client_visible` | `replace` |
| `enabled` | `boolean` | — | `true` | Whether the synergy contributes grants when every requirement is active. | `true` | `PS-CLASS-003` | `checkbox` | `client_visible` | `replace` |
| `grants` (required) | `list` | — | — | One through thirty two globally unique class grant entries. | `[{ id = "mypack:spellblade/stance", type = "ability", ability = "mypack:spellblade_stance" }]` | `PS-CLASS-003` | `list` | `client_visible` | `merge_by_key` |
| `icon` | `icon` | — | — | Optional synergy icon with alternative text. | `{ type = "item", value = "minecraft:golden_sword", fallback = "minecraft:barrier", alt = "Golden sword" }` | `PS-CLASS-003` | `icon` | `client_visible` | `replace` |
| `id` (required) | `resource_location` | — | — | Globally unique stable synergy identity. | `mypack:spellblade` | `PS-CLASS-003` | `resource_location` | `client_visible` | `replace` |
| `requires_classes` (required) | `list` | — | — | Two through sixteen known classes that must all remain active. | `["mypack:mage", "mypack:warrior"]` | `PS-CLASS-003` | `list` | `client_visible` | `set` |
| `search_aliases` | `list` | — | `[]` | Bounded alternate synergy terms. | `["Hybrid"]` | `PS-CLASS-003` | `list` | `client_visible` | `set` |

Cross-field constraints:

- `required_together` → `display`, `icon` (`PS-CLASS-003`): Synergy display and icon are declared together.

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

## Named currency definition

- Schema ID: `progressiveskills:currency_definition`
- Version: `2`
- Audience: `authoring`

Checked character scoped integer balance referenced by progression definitions.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `description` | `component` | — | — | Localized currency description. | `{ fallback = "Points from skill levels." }` | `PS-CURRENCY-001` | `component` | `client_visible` | `replace` |
| `display` (required) | `component` | — | — | Localized currency name. | `{ fallback = "Global Points" }` | `PS-CURRENCY-001` | `component` | `client_visible` | `replace` |
| `icon` (required) | `icon` | — | — | Currency icon with fallback and alternative text. | `{ type = "item", value = "minecraft:emerald", fallback = "minecraft:barrier", alt = "Emerald" }` | `PS-CURRENCY-001` | `icon` | `client_visible` | `replace` |
| `initial` | `integer` | — | `0` | Balance installed when the currency is first reconciled. | `0` | `PS-CURRENCY-001` | `integer` | `client_visible` | `replace` |
| `maximum` | `integer` | — | — | Inclusive checked upper balance bound. | `1000000000` | `PS-CURRENCY-001` | `integer` | `client_visible` | `replace` |
| `minimum` | `integer` | — | `0` | Inclusive checked lower balance bound. | `0` | `PS-CURRENCY-001` | `integer` | `client_visible` | `replace` |
| `scope` | `enum` | `character` | `"character"` | Phase 7 authority scope. | `character` | `PS-CURRENCY-001` | `select` | `client_visible` | `replace` |

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

Client-safe identity, presentation, disclosed tree graph, class selection semantics, and synergy summaries without authority internals.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `class_definition` | `object` | — | — | Bounded class slot use, disclosed requirements, costs, starter kit, and resolved grant summaries. | `{ slot_id = "mypack:combat", slot_cost = 1, enabled = true }` | `PS-CLASS-002` | `object` | `client_visible` | `replace` |
| `class_slot` | `object` | — | — | Bounded capacity and swap policy for a class slot definition. | `{ capacity = 2, swap_policy = "allowed" }` | `PS-CLASS-001` | `object` | `client_visible` | `replace` |
| `class_synergies` (required) | `map` | — | — | Bounded named class synergy summaries keyed by stable synergy id. | `{ "mypack:spellblade" = { required_classes = ["mypack:mage", "mypack:warrior"] } }` | `PS-CLASS-003` | `object` | `client_visible` | `replace` |
| `description` | `component` | — | — | Optional localized description with bounded fallback. | `{ key = "skill.mypack.physique.desc", fallback = "Raw power." }` | `PS-I18N-001` | `object` | `client_visible` | `replace` |
| `display` | `component` | — | — | Optional localized display component with bounded fallback. | `{ key = "skill.mypack.physique", fallback = "Physique" }` | `PS-I18N-001` | `object` | `client_visible` | `replace` |
| `icon` | `icon` | — | — | Optional bounded icon, fallback, alt text, and narration. | `{ type = "item", value = "minecraft:iron_chestplate" }` | `PS-SCHEMA-006` | `object` | `client_visible` | `replace` |
| `key` (required) | `string` | — | — | Typed definition kind and namespaced identity. | `progressiveskills:skill[mypack:physique]` | `PS-ID-001` | `object` | `client_visible` | `replace` |
| `search_aliases` (required) | `list` | — | — | Bounded presentation-only search terms. | `["Strength", "Might"]` | `PS-I18N-001` | `object` | `client_visible` | `replace` |
| `tree` | `object` | — | — | Bounded disclosed tree graph without grants or historical paid costs. | `{ scope = "skill", currency = "progressiveskills:global_points" }` | `PS-TREE-001` | `object` | `client_visible` | `replace` |

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
| `features` (required) | `integer` | — | — | Required bounded protocol feature bitset. | `63` | `PS-NET-001` | `object` | `client_visible` | `replace` |
| `presentation_digest` (required) | `string` | — | — | SHA-256 of the exact sanitized definition projection bytes. | `bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb` | `PS-NET-002` | `object` | `client_visible` | `replace` |
| `presentation_revision` (required) | `integer` | — | — | Monotonic presentation generation negotiated independently. | `7` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `protocol_version` (required) | `integer` | — | — | ProgressiveSkills application protocol version. | `3` | `PS-NET-001` | `object` | `client_visible` | `replace` |
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
| `intent_type` (required) | `enum` | `noop_test \| tree_buy \| tree_refund_preview \| tree_refund_confirm \| class_select \| class_respec_preview \| class_respec_confirm \| class_swap_preview \| class_swap_confirm` | — | Closed server-registered intent family. | `tree_buy` | `PS-NET-001` | `object` | `server_only` | `replace` |
| `payload` (required) | `string` | — | — | Small type-specific bounded selection payload; never effect amounts or commands. | `""` | `PS-NET-002` | `object` | `server_only` | `replace` |
| `request_id` (required) | `integer` | — | — | Monotonic request id covered by the bounded replay/result window. | `12` | `PS-NET-004` | `object` | `server_only` | `replace` |
| `semantic_digest` (required) | `string` | — | — | Client-observed gameplay SHA-256. | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` | `PS-NET-003` | `object` | `server_only` | `replace` |
| `session_id` (required) | `string` | — | — | Current connection session UUID. | `00000000-0000-0000-0000-000000000602` | `PS-NET-003` | `object` | `server_only` | `replace` |
| `state_revision` (required) | `integer` | — | — | Client-observed authoritative state revision. | `3` | `PS-NET-003` | `object` | `server_only` | `replace` |

## Numeric expression

- Schema ID: `progressiveskills:numeric_expression`
- Version: `2`
- Audience: `internal`

Exact fixed point program with bounded evaluation and one final rounding step.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `dependencies` (required) | `list` | — | — | Sorted typed dependency keys. | `[]` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |
| `max_depth` (required) | `integer` | — | — | Hard expression depth ceiling. | `16` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |
| `max_nodes` (required) | `integer` | — | — | Hard expression node ceiling. | `64` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |
| `rounding` (required) | `string` | — | — | Final fixed point rounding policy. | `floor` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |

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

## Historical paid cost record

- Schema ID: `progressiveskills:paid_cost_record`
- Version: `2`
- Audience: `internal`

Immutable purchase identity, definition lineage, exact paid balances, and persistent grant sources.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `definition_revision` (required) | `object` | — | — | Definition generation and semantic digest active when the purchase committed. | `{ generation = 10, semantic_digest = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }` | `PS-TREE-005` | `object` | `server_only` | `replace` |
| `instance_id` (required) | `object` | — | — | Owner kind, owner id, purchase id, and one based rank identity. | `{ owner_kind = "progressiveskills:tree", owner_id = "mypack:mining", purchase_id = "mypack:mining/root", rank = 1 }` | `PS-TREE-005` | `object` | `server_only` | `replace` |
| `owner_lineage` (required) | `string` | — | — | Lowercase SHA 256 of the tree and node grant lineage that owns this purchase. | `bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb` | `PS-TREE-004` | `object` | `server_only` | `replace` |
| `paid_balances` (required) | `map` | — | — | Up to eight positive exact currency debits preserved for refund. | `{ "progressiveskills:global_points" = 3 }` | `PS-TREE-005` | `object` | `server_only` | `replace` |
| `persistent_sources` (required) | `list` | — | — | Up to thirty two source owned persistent grants installed by the purchase. | `["progressiveskills:tree[mypack:mining]/mypack:mining/root/toughness"]` | `PS-TREE-005` | `object` | `server_only` | `replace` |
| `purchase_transaction_id` (required) | `string` | — | — | Transaction UUID that originally committed the exact payment. | `00000000-0000-0000-0000-000000000710` | `PS-TREE-005` | `object` | `server_only` | `replace` |

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

## Requirement expression

- Schema ID: `progressiveskills:requirement_expression`
- Version: `2`
- Audience: `internal`

Bounded typed boolean program with deterministic dependencies and explanations.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `dependencies` (required) | `list` | — | — | Sorted typed dependency keys. | `["skill_level:mypack:mining"]` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |
| `max_depth` (required) | `integer` | — | — | Hard expression depth ceiling. | `16` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |
| `max_nodes` (required) | `integer` | — | — | Hard expression node ceiling. | `64` | `PS-SCHEMA-005` | `object` | `server_only` | `replace` |

## Gameplay rule definition

- Schema ID: `progressiveskills:rule_definition`
- Version: `2`
- Audience: `authoring`

Compiled trigger route with literal fixed point output and bounded anti exploit memory.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `allow_custom_name` | `boolean` | — | `false` | Explicit opt in for normalized player controlled custom name matching. | `false` | `PS-RULE-003` | `checkbox` | `server_only` | `replace` |
| `anti_exploit.allowed_block_origins` | `list` | `natural \| creative_placed \| survival_placed \| automation_placed \| unknown` | — | Allowed block origins. Omission permits natural and creative placed blocks. | `["natural", "creative_placed"]` | `PS-RULE-005` | `list` | `server_only` | `set` |
| `anti_exploit.cooldown_ticks` | `integer` | — | `0` | Minimum world ticks between committed awards from this source. | `10` | `PS-RULE-005` | `integer` | `server_only` | `replace` |
| `anti_exploit.fake_players` | `enum` | `deny \| allow` | `"deny"` | Whether automation identities may receive this route. | `deny` | `PS-RULE-005` | `select` | `server_only` | `replace` |
| `anti_exploit.first_time` | `boolean` | — | `false` | Persist one receipt like source marker and reject later awards. | `false` | `PS-RULE-005` | `checkbox` | `server_only` | `replace` |
| `anti_exploit.minimum_multiplier` | `decimal` | — | — | Floor for repeated source decay. | `0.25` | `PS-RULE-005` | `decimal` | `server_only` | `replace` |
| `anti_exploit.per_day_cap` | `decimal` | — | — | Maximum fixed point XP from this source per Minecraft day bucket. | `400` | `PS-RULE-005` | `decimal` | `server_only` | `replace` |
| `anti_exploit.per_minute_cap` | `decimal` | — | — | Maximum fixed point XP from this source per 1200 tick bucket. | `40` | `PS-RULE-005` | `decimal` | `server_only` | `replace` |
| `anti_exploit.per_tick_cap` | `decimal` | — | — | Maximum fixed point XP from this source in one world tick. | `10` | `PS-RULE-005` | `decimal` | `server_only` | `replace` |
| `anti_exploit.repeat_decay` | `decimal` | — | — | Multiplier applied for each repeated source event inside the repeat window. | `0.5` | `PS-RULE-005` | `decimal` | `server_only` | `replace` |
| `anti_exploit.repeat_window_ticks` | `integer` | — | `0` | Bounded source repetition window in world ticks. | `100` | `PS-RULE-005` | `integer` | `server_only` | `replace` |
| `base` (required) | `decimal` | — | — | Positive literal fixed point amount before multiplier stacks and caps. | `8` | `PS-RULE-001` | `decimal` | `server_only` | `replace` |
| `credit` | `enum` | `actor` | `"actor"` | Phase 8 credit subject. | `actor` | `PS-RULE-001` | `select` | `server_only` | `replace` |
| `enabled` | `boolean` | — | `true` | Whether this rule is present in its compiled trigger table. | `true` | `PS-RULE-001` | `checkbox` | `server_only` | `replace` |
| `match` | `list` | — | `[]` | OR matched subjects with bare ids, prefixes, and optional leading negation filters. | `["id:minecraft:stone", "tag:minecraft:logs"]` | `PS-RULE-003` | `list` | `server_only` | `set` |
| `multipliers` | `list` | — | `[]` | Literal modifiers resolved by fixed stage and stable stack group. | `[{ id = "mypack:training/context", stage = "context", group = "mypack:training", mode = "add", value = 0.25 }]` | `PS-RULE-004` | `list` | `server_only` | `merge_by_key` |
| `multipliers.mode` | `enum` | `add \| multiply \| highest \| lowest \| replace` | — | Stack resolution within one literal multiplier group. | `add` | `PS-RULE-004` | `select` | `server_only` | `replace` |
| `multipliers.stage` | `enum` | `context \| equipment \| party_team \| rested_catch_up \| prestige_season \| global_difficulty` | — | Fixed multiplier pipeline stage. | `context` | `PS-RULE-004` | `select` | `server_only` | `replace` |
| `outputs` (required) | `list` | — | — | Exactly one Phase 9 XP output using rule_amount. | `[{ id = "mypack:stone/xp", type = "xp", skill = "mypack:mining", amount_formula = "rule_amount" }]` | `PS-RULE-001` | `list` | `server_only` | `merge_by_key` |
| `priority` | `integer` | — | `0` | Higher priority wins first and exclusive route selection. | `100` | `PS-RULE-004` | `integer` | `server_only` | `replace` |
| `requirements` | `list` | — | `[]` | Flat actor requirement list combined with logical all. | `[{ type = "skill_level", subject = "actor", missing = false, skill = "mypack:mining", op = ">=", value = 5 }]` | `PS-RULE-001` | `list` | `server_only` | `set` |
| `requirements.currency` | `resource_location` | — | — | Currency target for a currency requirement. | `mypack:points` | `PS-RULE-001` | `resource_location` | `server_only` | `replace` |
| `requirements.missing` | `boolean` | — | `false` | Result used only when the actor context is unavailable. | `false` | `PS-RULE-001` | `checkbox` | `server_only` | `replace` |
| `requirements.op` | `enum` | `< \| <= \| == \| != \| >= \| >` | `">="` | Integer comparison operator. | `>=` | `PS-RULE-001` | `select` | `server_only` | `replace` |
| `requirements.skill` | `resource_location` | — | — | Skill target for a skill level requirement. | `mypack:mining` | `PS-RULE-001` | `resource_location` | `server_only` | `replace` |
| `requirements.subject` | `enum` | `actor` | `"actor"` | Phase 9 requirement subject. | `actor` | `PS-RULE-001` | `select` | `server_only` | `replace` |
| `requirements.type` (required) | `enum` | `skill_level \| currency` | — | Direct requirement value kind. | `skill_level` | `PS-RULE-001` | `select` | `server_only` | `replace` |
| `requirements.value` (required) | `integer` | — | — | Integer threshold compared with the selected actor value. | `5` | `PS-RULE-001` | `integer` | `server_only` | `replace` |
| `rounding` | `enum` | `floor \| ceil \| nearest \| bankers` | `"floor"` | One final fixed point rounding policy after every literal multiplier group. | `floor` | `PS-RULE-001` | `select` | `server_only` | `replace` |
| `stack_group` | `resource_location` | — | — | Stable group for overlapping matching rules. | `mypack:ore_mining` | `PS-RULE-004` | `resource_location` | `server_only` | `replace` |
| `stack_rule` | `enum` | `sum \| highest \| first \| exclusive \| diminishing` | `"sum"` | Deterministic overlap policy for the stable route group. | `highest` | `PS-RULE-004` | `select` | `server_only` | `replace` |
| `trigger` (required) | `resource_location` | — | — | Registered server side event route. | `progressiveskills:block_break` | `PS-RULE-002` | `resource_location` | `server_only` | `replace` |

## Skill definition

- Schema ID: `progressiveskills:skill_definition`
- Version: `2`
- Audience: `authoring`

Fixed point XP progression with an exact curve, named currency awards, and source owned attributes.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `curve.base` | `decimal` | — | — | Base value used by flat, linear, polynomial, and exponential curves. | `100` | `PS-SKILL-002` | `decimal` | `client_visible` | `replace` |
| `curve.coefficient` | `decimal` | — | — | Polynomial coefficient multiplied by the level offset power. | `15` | `PS-SKILL-002` | `decimal` | `client_visible` | `replace` |
| `curve.custom_table` | `list` | — | — | One exact outgoing XP cost for every level below the hard cap. | `[100, 125, 150]` | `PS-SKILL-002` | `list` | `client_visible` | `ordered` |
| `curve.factor` | `decimal` | — | — | Positive exponential multiplier raised to the level offset. | `1.15` | `PS-SKILL-002` | `decimal` | `client_visible` | `replace` |
| `curve.power` | `integer` | — | — | Nonnegative bounded integer polynomial power. | `2` | `PS-SKILL-002` | `integer` | `client_visible` | `replace` |
| `curve.rounding` | `enum` | `ceil \| floor \| nearest \| bankers` | `"ceil"` | One final rounding operation applied after the complete level cost expression. | `ceil` | `PS-SKILL-002` | `select` | `client_visible` | `replace` |
| `curve.step` | `decimal` | — | — | Linear amount multiplied by the level offset. | `25` | `PS-SKILL-002` | `decimal` | `client_visible` | `replace` |
| `curve.type` (required) | `enum` | `flat \| linear \| polynomial \| exponential \| custom_table` | — | Exact Core XP curve family. | `linear` | `PS-SKILL-002` | `select` | `client_visible` | `replace` |
| `description` | `component` | — | — | Localized skill description. | `{ fallback = "Raw physical conditioning." }` | `PS-SKILL-001` | `component` | `client_visible` | `replace` |
| `display` (required) | `component` | — | — | Localized skill name used by feedback and presentation. | `{ fallback = "Physique" }` | `PS-SKILL-001` | `component` | `client_visible` | `replace` |
| `enabled` | `boolean` | — | `true` | Whether the skill accepts XP and projects grants. | `true` | `PS-SKILL-001` | `checkbox` | `client_visible` | `replace` |
| `icon` (required) | `icon` | — | — | Skill icon with fallback and alternative text. | `{ type = "item", value = "minecraft:iron_chestplate", fallback = "minecraft:barrier", alt = "Iron chestplate" }` | `PS-SKILL-001` | `icon` | `client_visible` | `replace` |
| `level_currency_awards` | `list` | — | `[]` | Named currency entitlements awarded only for newly crossed lifetime highest levels. | `[{ id = "mypack:physique/points", currency = "progressiveskills:global_points", amount_per_level = 1 }]` | `PS-CURRENCY-001` | `list` | `client_visible` | `merge_by_key` |
| `levels` | `list` | — | `[]` | Discrete source owned attribute effects activated at exact levels. | `[{ id = "mypack:physique/level_1", level = 1, effects = [] }]` | `PS-SKILL-004` | `list` | `client_visible` | `merge_by_key` |
| `max_level` (required) | `integer` | — | — | Inclusive hard skill level cap. | `10` | `PS-SKILL-001` | `integer` | `client_visible` | `replace` |
| `min_level` | `integer` | — | `0` | Initial level and curve offset origin. | `0` | `PS-SKILL-001` | `integer` | `client_visible` | `replace` |
| `negative_xp_policy` | `enum` | `deny` | `"deny"` | Phase 7 negative XP behavior. | `deny` | `PS-SKILL-003` | `select` | `client_visible` | `replace` |
| `overflow` | `enum` | `bank` | `"bank"` | Destination for XP earned at the hard cap. | `bank` | `PS-SKILL-003` | `select` | `client_visible` | `replace` |
| `scaling` | `list` | — | `[]` | Uniform per level source owned attribute grants over a bounded range. | `[{ id = "mypack:physique/health", type = "attribute", attribute = "minecraft:generic.max_health", operation = "add_value", per_level = 2.0 }]` | `PS-SKILL-004` | `list` | `client_visible` | `merge_by_key` |
| `xp_sources` | `list` | — | `[]` | Stable custom XP routes compiled into authoritative fixed point awards. | `[{ id = "mypack:physique/training", action = "custom", key = "mypack:training", amount = 25 }]` | `PS-SKILL-003` | `list` | `client_visible` | `merge_by_key` |

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
| `changed_node_ranks` (required) | `map` | — | — | Changed or added visible Core node ranks. | `{}` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `changed_selected_classes` (required) | `map` | — | — | Changed or added visible selected class states. | `{}` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `new_revision` (required) | `integer` | — | — | Strictly newer resulting storage revision. | `6` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `removed_balances` (required) | `list` | — | — | Removed visible balance paths. | `[]` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `removed_effective_values` (required) | `list` | — | — | Removed effective-value paths. | `[]` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `removed_node_ranks` (required) | `list` | — | — | Removed visible Core node ids. | `[]` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `removed_selected_classes` (required) | `list` | — | — | Removed selected class ids. | `[]` | `PS-NET-005` | `object` | `client_visible` | `replace` |
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

## Tree definition

- Schema ID: `progressiveskills:tree_definition`
- Version: `2`
- Audience: `authoring`

Bounded single rank Core progression tree with exact currency costs and cascade refunds.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `bind` | `resource_location` | — | — | Skill identity required for skill scope and forbidden for global scope. | `mypack:mining` | `PS-TREE-001` | `resource_location` | `client_visible` | `replace` |
| `currency` (required) | `resource_location` | — | — | Named character currency debited by node purchases and restored by exact refunds. | `progressiveskills:global_points` | `PS-TREE-001` | `resource_location` | `client_visible` | `replace` |
| `dependency_policy` | `enum` | `cascade_refund` | `"cascade_refund"` | Policy used when refunding a node with owned transitive dependents. | `cascade_refund` | `PS-TREE-001` | `select` | `client_visible` | `replace` |
| `description` | `component` | — | — | Localized tree description. | `{ fallback = "A practical mining specialization." }` | `PS-TREE-001` | `component` | `client_visible` | `replace` |
| `display` (required) | `component` | — | — | Localized tree name used by commands and presentation. | `{ fallback = "Mining Paths" }` | `PS-TREE-001` | `component` | `client_visible` | `replace` |
| `enabled` | `boolean` | — | `true` | Whether the tree accepts purchases and retains valid ownership during reconciliation. Refunds remain available. | `true` | `PS-TREE-001` | `checkbox` | `client_visible` | `replace` |
| `icon` (required) | `icon` | — | — | Tree icon with fallback and alternative text. | `{ type = "item", value = "minecraft:iron_pickaxe", fallback = "minecraft:barrier", alt = "Iron pickaxe" }` | `PS-TREE-001` | `icon` | `client_visible` | `replace` |
| `nodes` (required) | `list` | — | — | One to sixty four stable acyclic node entries merged by node id. | `[{ id = "mypack:mining/root", cost = 1, row = 0, col = 0 }]` | `PS-TREE-001` | `list` | `client_visible` | `merge_by_key` |
| `scope` (required) | `enum` | `global \| skill` | — | Global tree or tree bound to one skill. | `skill` | `PS-TREE-001` | `select` | `client_visible` | `replace` |
| `search_aliases` | `list` | — | `[]` | Bounded alternate terms used by tree search. | `["Mining", "Ore"]` | `PS-TREE-001` | `list` | `client_visible` | `set` |

## Tree mutation intent payload

- Schema ID: `progressiveskills:tree_intent`
- Version: `2`
- Audience: `internal`

Bounded serverbound node selection with no client supplied cost, grant, balance, or refund amount.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `node_id` (required) | `resource_location` | — | — | Selected stable node identity interpreted only inside the selected tree. | `mypack:mining/root` | `PS-TREE-001` | `object` | `server_only` | `replace` |
| `preview_digest` | `string` | — | — | Required only when the enclosing network intent is tree_refund_confirm and forbidden otherwise. | `cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc` | `PS-TREE-003` | `object` | `server_only` | `replace` |
| `tree_id` (required) | `resource_location` | — | — | Selected stable tree identity. | `mypack:mining` | `PS-TREE-001` | `object` | `server_only` | `replace` |

## Tree node

- Schema ID: `progressiveskills:tree_node`
- Version: `2`
- Audience: `authoring`

Stable single rank node with bounded prerequisites, exact cost, and persistent attribute grants.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `col` (required) | `integer` | — | — | Horizontal grid coordinate between negative and positive four thousand ninety six. | `1` | `PS-TREE-001` | `integer` | `client_visible` | `replace` |
| `cost` (required) | `integer` | — | — | Positive named currency amount recorded exactly when purchased. | `3` | `PS-TREE-001` | `integer` | `client_visible` | `replace` |
| `description` | `component` | — | — | Localized node description. | `{ fallback = "Improves mining endurance." }` | `PS-TREE-001` | `component` | `client_visible` | `replace` |
| `display` (required) | `component` | — | — | Localized node name. | `{ fallback = "Stone Sense" }` | `PS-TREE-001` | `component` | `client_visible` | `replace` |
| `grants` | `list` | — | `[]` | Up to thirty two stable persistent attribute grants merged by grant id. | `[{ id = "mypack:mining/root/toughness", type = "attribute", attribute = "minecraft:generic.armor", operation = "add_value", value = 1.0 }]` | `PS-TREE-001` | `list` | `client_visible` | `merge_by_key` |
| `grants.attribute` (required) | `resource_location` | — | — | Registered player attribute targeted by this grant. | `minecraft:generic.armor` | `PS-TREE-001` | `resource_location` | `client_visible` | `replace` |
| `grants.id` (required) | `resource_location` | — | — | Stable source identity for this persistent grant. | `mypack:mining/root/toughness` | `PS-TREE-001` | `resource_location` | `client_visible` | `replace` |
| `grants.operation` (required) | `enum` | `add_value \| add_multiplied_base \| add_multiplied_total` | — | Supported deterministic attribute operation. | `add_value` | `PS-TREE-001` | `select` | `client_visible` | `replace` |
| `grants.type` (required) | `enum` | `attribute` | — | Core tree grant type. | `attribute` | `PS-TREE-001` | `select` | `client_visible` | `replace` |
| `grants.value` (required) | `decimal` | — | — | Nonzero fixed point attribute contribution. | `1.0` | `PS-TREE-001` | `decimal` | `client_visible` | `replace` |
| `icon` (required) | `icon` | — | — | Node icon with fallback and alternative text. | `{ type = "item", value = "minecraft:stone", fallback = "minecraft:barrier", alt = "Stone" }` | `PS-TREE-001` | `icon` | `client_visible` | `replace` |
| `id` (required) | `resource_location` | — | — | Stable node identity unique across the complete live tree catalog. | `mypack:mining/root` | `PS-TREE-001` | `resource_location` | `client_visible` | `replace` |
| `min_level` | `map` | — | `{}` | Up to thirty two skill ids mapped to nonnegative minimum levels. | `{ "mypack:mining" = 5 }` | `PS-TREE-001` | `key_value` | `client_visible` | `replace` |
| `requires` | `list` | — | `[]` | Same tree node ids that must all be owned. | `["mypack:mining/root"]` | `PS-TREE-001` | `list` | `client_visible` | `set` |
| `requires_any` | `list` | — | `[]` | Same tree node ids of which at least one must be owned when nonempty. | `["mypack:mining/left", "mypack:mining/right"]` | `PS-TREE-001` | `list` | `client_visible` | `set` |
| `row` (required) | `integer` | — | — | Vertical grid coordinate between negative and positive four thousand ninety six. | `0` | `PS-TREE-001` | `integer` | `client_visible` | `replace` |
| `search_aliases` | `list` | — | `[]` | Bounded alternate terms used by node search. | `["Armor"]` | `PS-TREE-001` | `list` | `client_visible` | `set` |

## Tree cascade refund preview payload

- Schema ID: `progressiveskills:tree_refund_preview`
- Version: `2`
- Audience: `internal`

Clientbound revision pinned affected nodes, exact historical refunds, blockers, and confirmation digest.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `affected_nodes` (required) | `list` | — | — | Up to sixty four selected and owned dependent nodes in authoritative reverse topological refund order. | `["mypack:mining/deep", "mypack:mining/root"]` | `PS-TREE-003` | `object` | `client_visible` | `replace` |
| `blockers` (required) | `list` | — | — | Up to sixty four bounded reasons that make confirmation unavailable. | `[]` | `PS-TREE-003` | `object` | `client_visible` | `replace` |
| `definition_generation` (required) | `integer` | — | — | Definition generation used to calculate the preview. | `10` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `node_id` (required) | `resource_location` | — | — | Node selected for cascade refund. | `mypack:mining/root` | `PS-TREE-003` | `object` | `client_visible` | `replace` |
| `preview_digest` (required) | `string` | — | — | Lowercase SHA 256 covering the selected cascade and historical payment evidence. | `dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd` | `PS-TREE-003` | `object` | `client_visible` | `replace` |
| `refund_balances` (required) | `map` | — | — | Up to sixty four exact nonnegative currency totals recovered from affected paid cost records. | `{ "progressiveskills:global_points" = 6 }` | `PS-TREE-005` | `object` | `client_visible` | `replace` |
| `request_id` (required) | `integer` | — | — | Serverbound preview request identity returned to the requesting client. | `14` | `PS-NET-004` | `object` | `client_visible` | `replace` |
| `semantic_digest` (required) | `string` | — | — | Gameplay definition digest used to calculate the preview. | `eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `session_id` (required) | `string` | — | — | Current connection session UUID. | `00000000-0000-0000-0000-000000000711` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `state_revision` (required) | `integer` | — | — | Authoritative player state revision used to calculate the preview. | `8` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `tree_id` (required) | `resource_location` | — | — | Tree containing every affected purchase. | `mypack:mining` | `PS-TREE-003` | `object` | `client_visible` | `replace` |

## Visible selected class state

- Schema ID: `progressiveskills:visible_class_selection`
- Version: `2`
- Audience: `internal`

Owner visible class identity, weighted slot use, and active or suspended status without raw source ownership.

| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |
|---|---|---|---|---|---|---|---|---|---|
| `activity` (required) | `enum` | `active \| suspended` | — | Explicit noncolor active or suspended status. | `active` | `PS-CLASS-006` | `object` | `client_visible` | `replace` |
| `class_id` (required) | `resource_location` | — | — | Selected stable class identity. | `mypack:mage` | `PS-CLASS-002` | `object` | `client_visible` | `replace` |
| `slot_cost` (required) | `integer` | — | — | Visible weighted slot use including zero cost background classes. | `1` | `PS-CLASS-002` | `object` | `client_visible` | `replace` |
| `slot_id` | `resource_location` | — | — | Slot occupied by a known selected class. Missing definitions remain visibly suspended. | `mypack:combat` | `PS-CLASS-001` | `object` | `client_visible` | `replace` |

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
| `node_ranks` (required) | `map` | — | — | Owned Core node ranks without historical paid cost records. | `{ "mypack:mining/root" = 1 }` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `operation_receipt_count` (required) | `integer` | — | — | Visible diagnostic count without receipt contents. | `1` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `orphan_count` (required) | `integer` | — | — | Visible diagnostic count without orphan payload contents. | `0` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `player_id` (required) | `string` | — | — | UUID of the session owner. | `00000000-0000-0000-0000-000000000601` | `PS-NET-005` | `object` | `client_visible` | `replace` |
| `presentation_revision` (required) | `integer` | — | — | Pinned sanitized presentation generation. | `7` | `PS-NET-003` | `object` | `client_visible` | `replace` |
| `selected_classes` (required) | `map` | — | — | Selected class ids mapped to visible slot use and active or suspended state. | `{ "mypack:mage" = { slot_id = "mypack:combat", slot_cost = 1, activity = "active" } }` | `PS-NET-005` | `object` | `client_visible` | `replace` |
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

<a id="ps-class-001"></a>

### PS-CLASS-001 — Class slot definition is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A class slot requires a bounded positive capacity and one supported swap policy.
- Suggested fix: Correct the class slot using the generated class slot schema.

<a id="ps-class-002"></a>

### PS-CLASS-002 — Class definition is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A class must use a known slot, bounded weight, valid prerequisites, costs, and typed grants.
- Suggested fix: Correct the class using the generated class definition and grant schemas.

<a id="ps-class-003"></a>

### PS-CLASS-003 — Class synergy definition is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A synergy requires at least two known classes and bounded globally unique grants.
- Suggested fix: Correct the required class set and grant identities.

<a id="ps-class-004"></a>

### PS-CLASS-004 — Class selection was denied

- Default severity: `warning`
- Suppressible: `true`
- Why it matters: Unknown, disabled, inaccessible, conflicting, over capacity, unaffordable, or requirement blocked classes cannot be selected.
- Suggested fix: Review the class preview blockers and submit a fresh current intent.

<a id="ps-class-005"></a>

### PS-CLASS-005 — Class respec or swap was denied

- Default severity: `warning`
- Suppressible: `true`
- Why it matters: A respec or swap requires current ownership, allowed policy, exact costs, and a matching preview.
- Suggested fix: Request a fresh preview and resolve every blocker before confirming.

<a id="ps-class-006"></a>

### PS-CLASS-006 — Selected class requires reconciliation

- Default severity: `warning`
- Suppressible: `false`
- Why it matters: Reloaded slot capacity, prerequisites, or lineage no longer permits the selected class to remain active.
- Suggested fix: Review the suspended class and publish or run an explicit migration only after preview.

<a id="ps-class-007"></a>

### PS-CLASS-007 — Class entitlement projection is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A malformed grant, resolver, source identity, or target would make class ownership unsafe.
- Suggested fix: Use supported attribute, ability, spell, stage, tree access, or class access grants.

<a id="ps-currency-001"></a>

### PS-CURRENCY-001 — Named currency definition is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Currency scope, initial value, and checked bounds must form one consistent contract.
- Suggested fix: Use character scope and keep the initial value inside the declared minimum and maximum.

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

<a id="ps-rule-001"></a>

### PS-RULE-001 — Rule definition is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A gameplay route must compile to one bounded deterministic transaction path.
- Suggested fix: Correct the rule using the generated rule definition schema.

<a id="ps-rule-002"></a>

### PS-RULE-002 — Rule trigger has no provider

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A rule cannot run without one registered server side trigger provider.
- Suggested fix: Use a trigger supported by the installed provider registry.

<a id="ps-rule-003"></a>

### PS-RULE-003 — Rule matcher is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Unknown prefixes or subject incompatible matchers cannot compile into a safe route table.
- Suggested fix: Use a documented prefix supported by the selected trigger subject.

<a id="ps-rule-004"></a>

### PS-RULE-004 — Rule stack group is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Rules and literal multipliers in one group require one deterministic stack policy.
- Suggested fix: Use one stack policy per stable group and unique multiplier ids.

<a id="ps-rule-005"></a>

### PS-RULE-005 — Rule anti exploit policy is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Cooldowns, rate caps, fake player policy, and repeat decay must stay bounded.
- Suggested fix: Correct the anti exploit windows, caps, and fixed point multipliers.

<a id="ps-rule-006"></a>

### PS-RULE-006 — Rule event was rejected

- Default severity: `warning`
- Suppressible: `true`
- Why it matters: Dedupe, eligibility, cooldown, first time memory, fake player policy, or a rate cap denied the event.
- Suggested fix: Inspect the bounded last XP explanation before changing the rule.

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

<a id="ps-skill-001"></a>

### PS-SKILL-001 — Skill definition is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A skill must have bounded levels, presentation, overflow policy, and typed progression fields.
- Suggested fix: Correct the skill file using the generated skill definition schema.

<a id="ps-skill-002"></a>

### PS-SKILL-002 — Skill XP curve is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Every rounded level cost must be positive, nondecreasing, deterministic, and fit checked long totals.
- Suggested fix: Correct the curve type and values at the first reported invalid level.

<a id="ps-skill-003"></a>

### PS-SKILL-003 — Skill XP award is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Negative, overflowing, stale, or unknown XP awards cannot mutate authoritative progression.
- Suggested fix: Use a positive fixed point amount and a current enabled skill or custom source.

<a id="ps-skill-004"></a>

### PS-SKILL-004 — Skill attribute grant is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Unknown attributes, operations, level ranges, or unsafe values cannot be projected atomically.
- Suggested fix: Use a registered player attribute and a bounded supported operation.

<a id="ps-skill-005"></a>

### PS-SKILL-005 — Stored skill state does not match its XP coordinate

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Cached level, highest level, bank, and source ownership must derive exactly from fixed point state.
- Suggested fix: Reconcile the player against the current definition generation before gameplay resumes.

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

<a id="ps-tree-001"></a>

### PS-TREE-001 — Tree definition is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: A Core tree must be bounded, acyclic, single rank, and use valid same tree prerequisites.
- Suggested fix: Correct the tree and node fields using the generated tree schemas.

<a id="ps-tree-002"></a>

### PS-TREE-002 — Tree purchase was denied

- Default severity: `warning`
- Suppressible: `true`
- Why it matters: Unknown, disabled, owned, unaffordable, or requirement blocked nodes cannot be purchased.
- Suggested fix: Review the purchase blockers and submit a fresh intent against current state.

<a id="ps-tree-003"></a>

### PS-TREE-003 — Tree refund was denied

- Default severity: `warning`
- Suppressible: `true`
- Why it matters: A refund requires current ownership, exact historical cost evidence, and a matching cascade preview.
- Suggested fix: Request a fresh refund preview and resolve every reported blocker before confirming.

<a id="ps-tree-004"></a>

### PS-TREE-004 — Tree purchase is orphaned

- Default severity: `warning`
- Suppressible: `false`
- Why it matters: Persisted purchase evidence no longer maps to the same tree node lineage.
- Suggested fix: Restore a compatible definition or review the orphan before an explicit migration or refund.

<a id="ps-tree-005"></a>

### PS-TREE-005 — Paid cost ledger is invalid

- Default severity: `error`
- Suppressible: `false`
- Why it matters: Missing, malformed, conflicting, or overflowing historical payment evidence prevents an exact refund.
- Suggested fix: Restore verified paid cost records before allowing a purchase mutation or refund.

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
