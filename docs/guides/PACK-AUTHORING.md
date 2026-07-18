# Detailed pack authoring examples

This guide builds a dependency free pack named `mypack:training`. Every file uses Schema v2 and path derived identity. Copy the examples into `config/progressiveskills/packs/my-training-pack/`, validate them, review the dry run, and publish only the reviewed candidate.

## Complete example layout

```text
my-training-pack/
├── pack.toml
├── abilities/
│   └── steady_breath.toml
├── class_slots/
│   └── discipline.toml
├── classes/
│   └── athlete.toml
├── combo_mastery/
│   └── mixed_training.toml
├── compatibility_profiles/
│   └── default.toml
├── context_effects/
│   └── focused_recovery.toml
├── conversions/
│   └── points_to_marks.toml
├── currencies/
│   ├── mastery_marks.toml
│   └── talent_points.toml
├── grant_bundles/
│   ├── endurance_reward.toml
│   └── speed_reward.toml
├── items/
│   └── endurance_tome.toml
├── milestone_choices/
│   └── endurance_five.toml
├── predicates/
│   └── has_focus.toml
├── prestige/
│   └── veteran.toml
├── profiles/
│   └── multiplayer.toml
├── reactive_procs/
│   └── recovery_proc.toml
├── resources/
│   └── focus.toml
├── rules/
│   └── natural_stone_endurance.toml
├── skills/
│   └── endurance.toml
├── stances/
│   └── defensive.toml
├── templates/
│   └── standard_skill.toml
├── training_contracts/
│   └── daily_endurance.toml
├── tree_ranks/
│   └── endurance_rank.toml
├── trees/
│   └── endurance_training.toml
└── variables/
    └── server_bonus.toml
```

You do not need every directory. Start with the manifest, one currency, one skill, and one award rule. Add references only after their targets exist.

## Manifest

File: `pack.toml`

```toml
schema_version = 2

[pack]
id = "mypack:training"
namespace = "mypack"
name = { key = "pack.mypack.training", fallback = "My Training Pack" }
description = "A complete ProgressiveSkills authoring example."
content_version = "1.0.0"
engine = ">=1.0.0 <2.0.0"
authors = ["Pack Team"]
license = "All-Rights-Reserved"
priority = 10
default_locale = "en_us"

[dependencies]
required_packs = []
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

`content_version` is strict SemVer. `engine` is a bounded engine range. A required dependency that is absent or outside its declared range rejects the pack before any of its definitions become live.

## Named currencies

File: `currencies/talent_points.toml`

```toml
schema_version = 2

[currency]
id = "mypack:talent_points"
display = { fallback = "Talent Points" }
description = { fallback = "Earned from new lifetime highest Endurance levels." }
icon = { type = "item", value = "minecraft:emerald", fallback = "minecraft:barrier", alt = "Emerald" }
minimum = 0
maximum = 1000000
initial = 0
scope = "character"
```

File: `currencies/mastery_marks.toml`

```toml
schema_version = 2

[currency]
id = "mypack:mastery_marks"
display = { fallback = "Mastery Marks" }
description = { fallback = "A prestige and conversion reward." }
icon = { type = "item", value = "minecraft:nether_star", fallback = "minecraft:barrier", alt = "Nether star" }
minimum = 0
maximum = 1000000
initial = 0
scope = "character"
```

Bounds are authoritative. A transaction that would cross either bound rejects before commit.

## Skill, curve, level effects, and highest level awards

File: `skills/endurance.toml`

```toml
schema_version = 2

[skill]
id = "mypack:endurance"
display = { fallback = "Endurance" }
description = { fallback = "Sustained physical training." }
icon = { type = "item", value = "minecraft:leather_boots", fallback = "minecraft:barrier", alt = "Leather boots" }
max_level = 20
enabled = true
overflow = "bank"
negative_xp_policy = "deny"

[curve]
type = "linear"
base = 100
step = 20
rounding = "ceil"

[[level_currency_awards]]
id = "mypack:endurance/talent_points"
amount_per_level = 1
currency = "mypack:talent_points"
award_basis = "lifetime_highest_level"
award_scope = "character"

[[xp_sources]]
id = "mypack:endurance/manual_training"
action = "custom"
key = "mypack:endurance_training"
amount = 25
repeat_policy = "always"

[[levels]]
id = "mypack:endurance/level_1"
level = 1

[[levels.effects]]
id = "mypack:endurance/level_1_health"
type = "attribute"
attribute = "minecraft:generic.max_health"
operation = "add_value"
value = 1.0

[[levels]]
id = "mypack:endurance/level_5"
level = 5
milestone = true

[[levels.effects]]
id = "mypack:endurance/level_5_speed"
type = "attribute"
attribute = "minecraft:generic.movement_speed"
operation = "add_multiplied_base"
value = 0.02

[[scaling]]
id = "mypack:endurance/health_scaling"
type = "attribute"
attribute = "minecraft:generic.max_health"
operation = "add_value"
per_level = 0.5
from_level = 2
to_level = 10
```

At level zero the next cost is 100 XP. Each following level adds 20. Reaching a new lifetime highest level grants one Talent Point. Losing and regaining the same level does not grant another point.

Test commands:

```text
/pskills xp @s mypack:endurance 100
/pskills skill get mypack:endurance
/pskills xp source @s mypack:endurance_training
```

## Block origin and repeat protection

File: `rules/natural_stone_endurance.toml`

```toml
schema_version = 2

[rule]
id = "mypack:natural_stone_endurance"
enabled = true
trigger = "progressiveskills:block_break"
priority = 100
stack_group = "mypack:endurance_block_training"
stack_rule = "highest"
credit = "actor"
match = ["id:minecraft:stone"]
base = 8
rounding = "floor"

[[rule.requirements]]
type = "skill_level"
subject = "actor"
missing = false
skill = "mypack:endurance"
op = ">="
value = 0

[[rule.multipliers]]
id = "mypack:natural_stone_endurance/training_bonus"
stage = "context"
group = "mypack:training_bonus"
mode = "add"
value = 0.25
priority = 0

[rule.anti_exploit]
fake_players = "deny"
allowed_block_origins = ["natural", "creative_placed"]
first_time = false
cooldown_ticks = 10
per_tick_cap = 10
per_minute_cap = 40
per_day_cap = 400
repeat_window_ticks = 100
repeat_decay = 0.5
minimum_multiplier = 0.25

[[rule.outputs]]
id = "mypack:natural_stone_endurance/xp"
type = "xp"
skill = "mypack:endurance"
amount_formula = "rule_amount"
```

The base eight receives a 25 percent additive context multiplier and becomes ten before anti exploit limits. Natural and creative placed stone are eligible. Survival placed, automation placed, and explicitly unknown tracked origins are not.

To preserve origin protection but remove cooldown and repeat decay:

```toml
[rule.anti_exploit]
fake_players = "deny"
allowed_block_origins = ["natural", "creative_placed"]
first_time = false
cooldown_ticks = 0
per_tick_cap = 0
per_minute_cap = 0
per_day_cap = 0
repeat_window_ticks = 0
repeat_decay = 1.0
minimum_multiplier = 1.0
```

Zero caps mean unlimited. A zero repeat window with both repeat multipliers at one disables repeat decay.

Test matrix:

| Action | Expected result |
| --- | --- |
| Break naturally generated stone | Award if all other gates pass. |
| Place stone in creative and break it | Award because `creative_placed` is listed. |
| Place stone in survival and break it | Reject because `survival_placed` is absent. |
| Repeat during a ten tick cooldown | Reject without changing XP. |
| Repeat inside the decay window after cooldown | Award at the configured decayed multiplier. |

Use `/pskills explain xp last` after every row.

## Tree with exact historical refunds

File: `trees/endurance_training.toml`

```toml
schema_version = 2

[tree]
id = "mypack:endurance_training"
display = { fallback = "Endurance Training" }
description = { fallback = "Spend Talent Points on sustained training." }
icon = { type = "item", value = "minecraft:map", fallback = "minecraft:barrier", alt = "Map" }
enabled = true
scope = "skill"
bind = "mypack:endurance"
currency = "mypack:talent_points"
dependency_policy = "cascade_refund"

[[nodes]]
id = "mypack:endurance_training/breathing"
display = { fallback = "Controlled Breathing" }
description = { fallback = "Begin disciplined endurance work." }
icon = { type = "item", value = "minecraft:feather", fallback = "minecraft:barrier", alt = "Feather" }
cost = 1
row = 0
col = 0
requires = []
requires_any = []
min_level = { "mypack:endurance" = 1 }

[[nodes.grants]]
id = "mypack:endurance_training/breathing/health"
type = "attribute"
attribute = "minecraft:generic.max_health"
operation = "add_value"
value = 1.0

[[nodes]]
id = "mypack:endurance_training/stride"
display = { fallback = "Efficient Stride" }
description = { fallback = "Convert breathing discipline into movement." }
icon = { type = "item", value = "minecraft:rabbit_foot", fallback = "minecraft:barrier", alt = "Rabbit foot" }
cost = 2
row = 1
col = 0
requires = ["mypack:endurance_training/breathing"]
requires_any = []
min_level = { "mypack:endurance" = 3 }

[[nodes.grants]]
id = "mypack:endurance_training/stride/speed"
type = "attribute"
attribute = "minecraft:generic.movement_speed"
operation = "add_multiplied_base"
value = 0.03
```

If Stride was purchased for two Talent Points and a later pack version changes its authored cost to three, its refund remains two because the paid cost record stores the original transaction.

## Class slot, class, and ability

File: `class_slots/discipline.toml`

```toml
schema_version = 2

[class_slot]
id = "mypack:discipline"
display = { fallback = "Training Disciplines" }
description = { fallback = "Compatible disciplines share two capacity." }
icon = { type = "item", value = "minecraft:armor_stand", fallback = "minecraft:barrier", alt = "Armor stand" }
capacity = 2
swap_policy = "allowed"
```

File: `abilities/steady_breath.toml`

```toml
schema_version = 2

[ability]
id = "mypack:steady_breath"
display = { fallback = "Steady Breath" }
description = { fallback = "Spend hunger to recover and move quickly." }
icon = { type = "item", value = "minecraft:sugar", fallback = "minecraft:barrier", alt = "Sugar" }
enabled = true
kind = "active"
slot_allowed = true
cooldown_group = "mypack:recovery"
cooldown_ticks = 300
max_charges = 2
recharge_ticks = 200

[targeting]
mode = "self"

[[costs]]
id = "mypack:steady_breath/hunger"
type = "hunger"
amount = 2

[[actions]]
id = "mypack:steady_breath/message"
type = "message"
message = { fallback = "Your breathing steadies." }

[[actions]]
id = "mypack:steady_breath/heal"
type = "heal"
amount = 2.0

[[actions]]
id = "mypack:steady_breath/speed"
type = "vanilla_effect"
effect = "minecraft:speed"
amplifier = 0
duration_ticks = 80
ambient = false
show_particles = true
show_icon = true
```

File: `classes/athlete.toml`

```toml
schema_version = 2

[class]
id = "mypack:athlete"
display = { fallback = "Athlete" }
description = { fallback = "A disciplined endurance specialist." }
icon = { type = "item", value = "minecraft:golden_boots", fallback = "minecraft:barrier", alt = "Golden boots" }
enabled = true
access_required = false
slot = "mypack:discipline"
slot_cost = 1
exclusive_tags = []
prerequisites = { min_level = { "mypack:endurance" = 3 }, nodes = ["mypack:endurance_training/breathing"], classes = [] }
selection_cost = { currency = "mypack:talent_points", amount = 1 }
respec_allowed = true
respec_cost = { currency = "mypack:talent_points", amount = 1 }
starter_kit = ["minecraft:bread"]

[[grants]]
id = "mypack:athlete/steady_breath"
type = "ability"
ability = "mypack:steady_breath"

[[grants]]
id = "mypack:athlete/speed"
type = "attribute"
attribute = "minecraft:generic.movement_speed"
operation = "add_multiplied_base"
value = 0.02
```

Test the complete chain:

```text
/pskills class preview select mypack:athlete
/pskills class select mypack:athlete
/pskills ability assign mypack:steady_breath 1
/pskills ability select 1
/pskills ability activate 1
```

## Carrier item

File: `items/endurance_tome.toml`

```toml
schema_version = 2

[item]
id = "mypack:endurance_tome"
display = { fallback = "Tome of Endurance" }
description = { fallback = "Grants 500 Endurance XP." }
icon = { type = "item", value = "minecraft:enchanted_book", fallback = "minecraft:barrier", alt = "Enchanted book" }
enabled = true
carrier = "progressiveskills:tome"
behavior_version = 1
migration_policy = "keep_pinned"
bind = "none"
delivery_policy = "pending_claim"
rarity = "rare"
glint = true
stack_size = 16
charges = 1
cooldown_ticks = 0

[[use_actions]]
id = "mypack:endurance_tome/grant_xp"
type = "xp"
skill = "mypack:endurance"
amount = 500
consume = 1
```

Changing `amount` or an action changes the behavior digest. Existing issued tomes retain the old archived behavior because `migration_policy` is `keep_pinned`.

## Compatibility profile

File: `compatibility_profiles/default.toml`

```toml
schema_version = 2

[compatibility_profile]
id = "mypack:default"
mode = "fallback"
required = ["vanilla_attributes"]
preferred = ["party_membership", "shared_storage", "carrier_slots"]
active = true
```

Required and preferred sets cannot overlap. Capability names are case insensitive during compilation and normalize to the closed provider enum.

## Creator variable, resource, and conversion

File: `variables/server_bonus.toml`

```toml
schema_version = 2

[variable]
id = "mypack:server_bonus"
value = 2
```

File: `resources/focus.toml`

```toml
schema_version = 2

[resource]
id = "mypack:focus"
minimum = 0
maximum = 100
initial = 25
decay_amount = 1
decay_interval_ticks = 200
```

File: `conversions/points_to_marks.toml`

```toml
schema_version = 2

[conversion]
id = "mypack:points_to_marks"
from = "mypack:talent_points"
to = "mypack:mastery_marks"
numerator = 1
denominator = 5
fee_basis_points = 500
maximum_input = 100
```

Converting five Talent Points begins with one Mastery Mark, then applies the five percent fee using checked integer arithmetic. If the output rounds below one, the conversion rejects rather than consuming input.

```text
/pskills creator simulate mypack:server_bonus*10
/pskills resource get mypack:focus
/pskills resource add mypack:focus 10
/pskills convert mypack:points_to_marks 10
```

## Grant bundles and milestone choices

File: `grant_bundles/endurance_reward.toml`

```toml
schema_version = 2

[grant_bundle]
id = "mypack:endurance_reward"
grants = [
  { currency = "mypack:talent_points", amount = 3 }
]
```

File: `grant_bundles/speed_reward.toml`

```toml
schema_version = 2

[grant_bundle]
id = "mypack:speed_reward"
grants = [
  { currency = "mypack:mastery_marks", amount = 1 }
]
```

File: `milestone_choices/endurance_five.toml`

```toml
schema_version = 2

[milestone_choice]
id = "mypack:endurance_five"
choices = [
  { id = "mypack:endurance_five/talent", reward_bundle = "mypack:endurance_reward" },
  { id = "mypack:endurance_five/mastery", reward_bundle = "mypack:speed_reward" }
]
respec_allowed = true
respec_policy = "replace_without_reward"
```

With `replace_without_reward`, the first selection grants its bundle. A later allowed choice change updates the selection but cannot farm another reward.

```text
/pskills milestone choose mypack:endurance_five mypack:endurance_five/talent
```

## Training contract and combo mastery

File: `training_contracts/daily_endurance.toml`

```toml
schema_version = 2

[training_contract]
id = "mypack:daily_endurance"
minimum_goal = 5
maximum_goal = 20
eligible_skills = ["mypack:endurance"]
```

File: `combo_mastery/mixed_training.toml`

```toml
schema_version = 2

[combo_mastery]
id = "mypack:mixed_training"
sequence = ["mypack:endurance", "progressiveskills:physique"]
timeout_ticks = 200
cooldown_ticks = 20
mastery_cap_per_window = 10
cap_window_ticks = 1200
max_repeated_awards = 4
```

Contract assignment is deterministic for its epoch. Combo awards must arrive in order within the timeout and remain subject to cooldown, window cap, and repeated award bounds.

## Predicates, context effects, and reactive procs

File: `predicates/has_focus.toml`

```toml
schema_version = 2

[predicate]
id = "mypack:has_focus"
type = "value"
value = "mypack:focus"
operator = "greater_or_equal"
threshold = 10
```

File: `context_effects/focused_recovery.toml`

```toml
schema_version = 2

[context_effect]
id = "mypack:focused_recovery"
predicate = "mypack:has_focus"
cooldown_ticks = 100
resource = "mypack:focus"
resource_amount = -5
```

File: `reactive_procs/recovery_proc.toml`

```toml
schema_version = 2

[reactive_proc]
id = "mypack:recovery_proc"
trigger = "progressiveskills:block_break"
chance_basis_points = 2500
cooldown_ticks = 200
resource = "mypack:focus"
resource_amount = 2
```

Predicate graphs can use `all`, `any`, `not`, `flag`, and `value`. Graph cycles and empty composite predicates fail publication. Reactive proc rolls combine the supplied event seed, player identity, and proc identity deterministically.

```text
/pskills predicate mypack:has_focus
/pskills context mypack:focused_recovery
/pskills proc mypack:recovery_proc progressiveskills:block_break 12345
```

## Ranks, stances, challenges, and prestige

File: `tree_ranks/endurance_rank.toml`

```toml
schema_version = 2

[tree_rank]
id = "mypack:endurance_rank"
maximum_rank = 5
base_cost = 1
cost_growth = 1
cost_currency = "mypack:talent_points"
exclusive_group = "mypack:endurance_specialization"
```

Rank one costs one, rank two costs two, and so on because cost is `base_cost + cost_growth * current_rank`.

File: `stances/defensive.toml`

```toml
schema_version = 2

[stance]
id = "mypack:defensive"
group = "mypack:combat_stance"
```

Selecting another stance in the same group revokes the old source and grants the new one in a single transaction.

File: `challenges/endurance_week.toml`

```toml
schema_version = 2

[challenge]
id = "mypack:endurance_week"
goal = 500
progress_per_xp = 1
skill = "mypack:endurance"
```

File: `prestige/veteran.toml`

```toml
schema_version = 2

[prestige]
id = "mypack:veteran"
minimum_total_level = 20
reset_currencies = ["mypack:talent_points"]
conversion_currency = "mypack:mastery_marks"
conversion_per_level = 1
reward_currency = "mypack:mastery_marks"
reward_currency_amount = 5
reward_resource = "mypack:focus"
reward_amount = 10
```

Prestige checks eligibility and every resulting bound before committing currency changes. The prestige counter and optional resource reward commit only after the transaction succeeds.

## Multiplayer profile

File: `profiles/multiplayer.toml`

```toml
schema_version = 2

[profile]
id = "mypack:multiplayer"
active = true
assist_skill = "mypack:endurance"
assist_xp_units_per_damage = 25
assist_max_award_units = 1000
assist_window_ticks = 200
assist_min_damage_milli = 1000
mentor_bonus_basis_points = 500
mentor_max_bonus_per_award = 1000
mentor_max_level_gap = 20
pvp_awards_enabled = true
pvp_pair_cooldown_ticks = 1200
pvp_pair_daily_cap_units = 2500
pvp_repeat_multiplier_basis_points = 2500
pvp_free_level_gap = 5
pvp_level_penalty_basis_points = 500
pvp_minimum_multiplier_basis_points = 1000
```

Basis points use 10000 as one hundred percent. The repeat multiplier of 2500 means repeated eligible PvP awards use 25 percent before other level gap bounds. Pair cooldown and daily cap are server authoritative and persistent.

## Templates and inheritance

File: `templates/standard_skill.toml`

```toml
schema_version = 2

[template]
id = "mypack:standard_skill"
target_kind = "progressiveskills:skill"
extends = []

[template.fields]
enabled = true
max_level = 20
overflow = "bank"
negative_xp_policy = "deny"
```

Template inheritance is deterministic. Parents apply in authored order and child fields override parent fields. Missing parents, target kind mismatches, and cycles reject the candidate.

## Overlay, replacement, patch, and disable examples

An overlay definition uses the same path and derived id as the target.

Replace the entire definition only when the existing target is intentional:

```toml
schema_version = 2
merge_intent = "replace"
expected_old_digest = "<complete_previous_semantic_digest>"

[variable]
id = "mypack:server_bonus"
value = 3
```

Patch one field:

```toml
schema_version = 2
merge_intent = "patch"

[[patches]]
op = "set"
path = "value"
value = 3
```

Disable while retaining identity and migration visibility:

```toml
schema_version = 2
merge_intent = "disable"
```

Never put definition fields beside `merge_intent = "patch"` or `disable`. Patch paths are canonical field paths, not arbitrary Java or NBT access.

## Datapack JSON equivalence

Studio and the pack loader can compile JSON into the same canonical IR. A JSON skill example is:

```json
{
  "schema_version": 2,
  "skill": {
    "id": "mypack:endurance",
    "display": {"fallback": "Endurance"},
    "description": {"fallback": "Sustained physical training."},
    "icon": {
      "type": "item",
      "value": "minecraft:leather_boots",
      "fallback": "minecraft:barrier",
      "alt": "Leather boots"
    },
    "max_level": 20,
    "enabled": true,
    "overflow": "bank",
    "negative_xp_policy": "deny"
  },
  "curve": {
    "type": "linear",
    "base": 100,
    "step": 20,
    "rounding": "ceil"
  }
}
```

Equivalent TOML and JSON produce the same semantic definition when their normalized values match. Provenance and source format do not change semantic equality.

## Studio authoring workflow

Use Studio for revision checked visual or text authoring, not as a way to skip validation.

```text
/pskills studio draft create mypack Training Update
/pskills studio draft status <draft_id>
/pskills studio file put <draft_id> 0 variables/server_bonus.json {"schema_version":2,"variable":{"id":"mypack:server_bonus","value":3}}
/pskills studio lint <draft_id>
/pskills studio diff <draft_id>
/pskills studio history <draft_id>
/pskills studio publish <draft_id> 1 <lint_digest>
```

Create returns a random stable id such as `mypack:studio/<uuid>`. Copy the complete returned value as `<draft_id>`. If live content changes before publish, rebase the current draft revision, lint again, and use the new digest.

## Validation checklist

Before publication:

1. Every file path derives the declared id.
2. Every referenced currency, skill, tree node, class, ability, resource, predicate, bundle, and provider capability exists.
3. Tree and template graphs are acyclic.
4. Every nested stateful entry has a stable id.
5. Currency, resource, amount, chance, cooldown, count, and collection values remain within documented bounds.
6. Block origin policy matches the intended farming rules.
7. Cooldown, repeat decay, and caps match the intended reward rate.
8. Physical rewards have an explicit safe delivery policy.
9. Optional capability behavior is declared instead of assumed.
10. Run validate, dry run, diff, publish, doctor, and the relevant command preview.

```text
/pskills validate
/pskills reload --dry-run
/pskills diff
/pskills reload --publish
/pskills doctor
```

After publication, test relog, death, dimension change, server restart, two client privacy, and a definition reload while the affected state is owned.
