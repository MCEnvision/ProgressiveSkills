# Rule Engine and Anti Exploit Foundation

Status: Phase 8 gameplay route verified. Phase 9 requirement and expression verification is tracked separately in [PHASE-9.md](../verification/PHASE-9.md).

## Runtime pipeline

Phase 8 adds the first ordinary gameplay route through one fixed server-side pipeline:

```text
uncancelled block break event
  -> persistent block origin lookup and consumption
  -> compiled block route lookup
  -> event dedupe
  -> actor, block origin, and fake player policy
  -> cooldown and first time eligibility
  -> direct requirement and exact amount evaluation
  -> stable rule stack resolution
  -> repeat decay and rate caps
  -> one atomic skill XP transaction with source memory
```

The NeoForge provider currently binds only `progressiveskills:block_break`. The listener is registered once at startup and observes an uncancelled `BlockEvent.BreakEvent` at lowest priority. Placement and piston listeners maintain provenance independently of whether an XP route currently matches. Pack reloads replace immutable compiled route tables; definitions never add or remove event listeners at runtime.

## Registries and validation

The trigger, matcher, and provider registries are frozen after construction and reject duplicate identities. Phase 8 registers the block subject and the canonical `id`, `tag`, `mod`, and `translation_key` block matchers. `custom_name` remains opt-in and is not supported by this binding. `school` exists in matcher metadata but is rejected for a block subject.

Every rule has one stable resource ID, one registered trigger, actor credit, bounded lists of matchers, direct requirements, and multipliers, one stack group and policy, and exactly one XP output referencing an existing skill. Phase 9 keeps positive fixed-point literals as the Core authoring surface while compiling them through the internal exact numeric evaluator. General formula authoring remains gated until Creator.

Positive matchers in one rule use OR semantics. A matching negated entry excludes the rule. Exact IDs, namespaces, translation keys, and tag keys are compiled at publication time. The event path returns immediately when no route is enabled or no index can match.

## Deterministic values and stacking

Base amounts and multiplier values retain the same six-decimal fixed-point representation as skill XP. Literal multiplier groups resolve in fixed stage order:

1. context
2. equipment
3. party and team
4. rested and catch up
5. prestige and season
6. global difficulty

Groups support `add`, `multiply`, `highest`, `lowest`, and priority-based `replace`. Intermediate products remain exact integer ratios and divide only once at the final rule amount. Overlapping candidate rules resolve by stable priority and rule ID through `sum`, `highest`, `first`, `exclusive`, or `diminishing` stack policies.

## Requirements and amount evaluation

Phase 9 inserts requirement evaluation after matcher, origin, fake-player, and anti-exploit eligibility checks and before stack selection. Core rule TOML accepts only a flat AND list of direct actor `skill_level` and named `currency` requirements. A requirement is a gate, not a debit. Missing values and evaluator failures follow the leaf's validated policy and fail closed by default.

The runtime model is richer than the Core authoring surface: immutable typed `all`, `any`, and `not` nodes compose typed leaves internally. Every tree carries a deterministic, sorted dependency index, so runtime snapshots fetch only the skill levels and currency balances the route can read. Node count, depth, aggregate fanout, dependencies, evaluator work, and retained trace entries are limited by implementation constants before and during evaluation.

Rule arithmetic uses an internal bounded numeric AST with exact fixed-point values and checked intermediates. The complete amount expression receives one final rule-level `rounding` operation; individual arithmetic nodes do not round independently. Division by zero, overflow, domain errors, and budget exhaustion deny the candidate without a transaction.

Preview and live execution share the same evaluator. Preview reads an immutable snapshot and cannot spend currency, update source memory, or award XP. The live explanation retains bounded requirement outcomes, dependency values where disclosure permits, operation use, rounding policy, and rounded final amount. See [REQUIREMENTS_AND_EXPRESSIONS.md](REQUIREMENTS_AND_EXPRESSIONS.md) for the complete Phase 9 boundary.

## Source memory and atomicity

Each rule derives bounded internal state keys from a SHA-256 digest of its stable ID. The state records first-time completion, the last awarded tick, repeat timing/count, and tick, minute, and Minecraft-day rate windows. These balances are persisted with the player account but are filtered from client state projection.

Eligibility is checked before stacking. A selected rule then computes repeat decay and remaining rate capacity. The awarded XP and every changed source-memory value enter the same progression transaction. A rejected or failed transaction changes neither XP nor source memory.

World ticks are stored with an explicit presence encoding, so tick zero is a valid award time rather than an absent marker. Clock rollback and corrupted cap state fail closed.

Event dedupe uses a bounded per-player set for the current tick and rejects the same dimension, block position, and tick token twice. The set permits at most 256 distinct matched tokens per player tick. Fake players are denied by default and can be allowed only explicitly by the rule.

A real player route also requires active attachment state pinned to the live definition revision. A failed login or publication reconciliation therefore leaves that player's gameplay routes closed instead of awarding against stale state.

## Block origin policy

Every block-break rule accepts an `allowed_block_origins` set containing any combination of `natural`, `creative_placed`, `survival_placed`, `automation_placed`, and `unknown`. Omitting the setting uses the safe default of natural and creative-placed blocks. The starter routes state that default explicitly.

Player placement events are stored as creative or survival origin. Fake-player and non-player placement events are stored as automation origin. Multi-block placement records every replaced position. A successful observed break consumes its position entry. Piston pre and post events transfer origins from every source position to its destination and remove destroyed entries.

The ledger is persisted in overworld saved data and tracks at most 90,000 non-natural positions across dimensions. Positions that predate this feature and have no entry are inferred as natural while the ledger is reliable. If decoding, capacity, or piston correlation fails, the ledger becomes unreliable and every untracked position resolves to unknown. The safe starter policy denies unknown, so loss of provenance cannot silently become a natural reward.

Natural world changes that do not emit a player placement remain inferred natural. A non-player `EntityPlaceEvent`, including a falling-block landing, is conservatively classified as automation. Modded movement systems that do not emit NeoForge piston or placement events need a future registered provenance adapter before they can preserve a placed origin exactly.

## Starter routes

The installer adds missing rule files without overwriting operator edits:

| Rule | Match | Award and policy |
|---|---|---|
| `progressiveskills:physique_stone_training` | exact `minecraft:stone` | Natural or creative-placed blocks only. 8 base plus a 25 percent context modifier, producing 10 XP. Ten-tick cooldown, 100-tick repeat window, 0.5 repeat decay with a 0.25 floor, and tick, minute, and day caps. |
| `progressiveskills:physique_first_log` | `minecraft:logs` tag | Natural or creative-placed blocks only. 20 XP once per player, persisted across relog. |

The persistent origin ledger closes the ordinary Silk Touch place-and-break loop. Existing worlds cannot prove the origin of blocks placed before this feature, so previously untracked blocks are initially inferred natural. Packs with high-value resource economies should also account for modded movers and generators that bypass the covered NeoForge placement and piston events.

A minimal copy-paste route uses the same typed shape:

```toml
schema_version = 2

[rule]
id = "mypack:first_stone"
enabled = true
trigger = "progressiveskills:block_break"
priority = 100
stack_group = "mypack:mining_rewards"
stack_rule = "highest"
credit = "actor"
match = ["minecraft:stone"]
base = 5

[rule.anti_exploit]
fake_players = "deny"
allowed_block_origins = ["natural", "creative_placed"]
first_time = true
cooldown_ticks = 0
per_tick_cap = 5
per_minute_cap = 5
per_day_cap = 5
repeat_window_ticks = 0
repeat_decay = 1
minimum_multiplier = 1

[[rule.outputs]]
id = "mypack:first_stone/xp"
type = "xp"
skill = "progressiveskills:physique"
amount_formula = "rule_amount"
```

To allow every origin, list all five values explicitly. To keep every accepted award at full value with no cooldown, repeat timeout, or rate cap, use:

```toml
[rule.anti_exploit]
fake_players = "deny"
allowed_block_origins = ["natural", "creative_placed", "survival_placed", "automation_placed", "unknown"]
first_time = false
cooldown_ticks = 0
per_tick_cap = 0
per_minute_cap = 0
per_day_cap = 0
repeat_window_ticks = 0
repeat_decay = 1
minimum_multiplier = 1
```

A zero cap means unlimited. A zero repeat window requires both repeat multipliers to be one, which disables repeat decay completely.

## Commands

| Command | Result |
|---|---|
| `/ps rule status` | Reports total and enabled compiled rules plus tracked block count, ledger reliability, and any fail-closed issue. |
| `/ps explain xp last` | Shows the caller's most recent matched block route, block origin, bounded requirement result, rounding result, candidate, eligibility and selection counts, final award, outcome, and committed transaction ID. |

The explanation is intentionally bounded. Core shows direct requirement and final-rounding evidence; reusable predicate traces and general formula source remain unavailable until their Creator authoring surface exists.

## Troubleshooting

- Run `/ps validate` first. Unknown triggers, subject-incompatible matchers, missing skills, conflicting stack policies, and unbounded anti exploit values reject the candidate generation with the definition path and reason.
- Run `/ps explain xp last` after a matched block. A zero award can be a denied block origin, an active cooldown, an already claimed first-time route, repeat decay to zero, an exhausted cap, a duplicate token, or fake-player denial.
- A failed direct requirement names its safe type and comparison in the explanation. If the value is unavailable, confirm the skill or currency ID exists in the same staged generation and that the subject is `actor`.
- A rounding or evaluator-budget failure is not recoverable at event time. Run `/ps validate`, reduce the expression or requirement structure, and publish a corrected generation.
- Run `/ps rule status` when origin behavior is unexpected. `Reliable false` means untracked positions resolve to unknown until the ledger is repaired or intentionally reset while the server is stopped.
- A disabled or nonmatching block does not replace the previous explanation because it never enters the matched hot path. Compare XP and `/ps rule status` when testing a disabled route.
- Tag routes require the server's current tag registry. A normal server start or data reload provides vanilla and datapack tag membership.

## Phase boundary

Phase 9 adds the typed internal requirement tree, deterministic dependency index, exact numeric AST, one final rounding policy, and shared preview and explanation hooks to the Phase 8 route. Core authoring intentionally exposes only flat actor skill-level and named-currency gates plus literal bounded amounts. It does not expose nested or reusable predicates, named requirement definitions, general formula text, target/assist/team credit, arbitrary modded mover adapters, combat/crafting/movement providers, fractional carry, full reference-hardware soak evidence, or the complete Creator surface.
