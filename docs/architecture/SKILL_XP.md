# Skill XP and Linear Attribute Progression

Status: Phase 7 accepted. Phase 8 now routes the first ordinary block events into the same award transaction.

## Authoritative state

Skill XP uses checked signed `long` storage with a fixed scale of `1,000,000` units per XP. Decimal input is accepted only when it can be represented exactly at six decimal places. Negative XP is denied in Phase 7.

For every skill, the transaction balance map stores four derived identities:

| State | Balance path |
|---|---|
| Active XP | `progressiveskills:skill_xp/<skill namespace>/<skill path>` |
| Overflow bank | `progressiveskills:skill_bank/<skill namespace>/<skill path>` |
| Current level | `progressiveskills:skill_level/<skill namespace>/<skill path>` |
| Lifetime highest level | `progressiveskills:skill_highest/<skill namespace>/<skill path>` |

Active XP is the authoritative coordinate. Current level is the greatest level whose checked cumulative threshold is at most active XP. The stored level must equal that derivation. XP at the hard cap enters the separate bank and does not create another highest-level reward.

## Exact curves

All five Core curve types compile to a complete immutable outgoing-cost table before publication:

| Type | Exact expression before final rounding |
|---|---|
| `flat` | `base` |
| `linear` | `base + step * n` |
| `polynomial` | `base + coefficient * n^power` |
| `exponential` | `base * factor^n` |
| `custom_table` | authored entry `n` |

The complete expression is rounded once with `ceil`, `floor`, `nearest`, or `bankers`. Every resulting cost must be at least one and nondecreasing. The table length must equal `max_level - min_level`, the level span is capped at 10,000, polynomial power is capped at 32, and every fixed-point cumulative threshold must fit a signed `long`. Validation reports the first invalid definition instead of exposing a partial snapshot.

## One atomic award

An XP award captures the live definition generation and current player revision, then builds one ordinary progression transaction containing:

1. active XP and cap-bank deltas;
2. current and lifetime-highest level deltas;
3. aggregate named-currency awards for newly crossed lifetime-highest levels; and
4. source-owned attribute contribution changes for the resulting level.

The transaction prevalidates the complete physical attribute projection. Any stale revision, arithmetic overflow, currency bound, unsupported attribute target, or projector failure rejects the whole award. A successful commit persists before the Phase 6 visible-state sync runs.

`progressiveskills:global_points` is an ordinary character-scoped currency definition with checked minimum, maximum, and initial value. It is not a hard-coded global pool. The bundled Physique award grants one point for each newly crossed lifetime-highest level.

## Linear grants and projection

`[[levels]]` defines discrete effects that become owned at an exact level. `[[scaling]]` defines one per-level contribution over a bounded range. Both compile into `GrantSourceId` identities owned by the skill and nested grant ID. Reload reconciliation updates or removes only those sources.

Phase 7 supports these attribute operations:

- `add_value`
- `add_multiplied_base`
- `add_multiplied_total`

Contributions sharing one attribute and operation resolve additively into one deterministic transient modifier. Fixed-point values become doubles only at the final Minecraft attribute boundary. Separate operation groups never overwrite each other or the retained Phase 4 fixture modifier. Removing or changing a grant reprojects every online player during publication; login and respawn restore the attachment, reconcile ownership, and then force the complete physical projection.

## Starter Physique slice

The installer adds missing starter files without overwriting existing operator files. Physique has:

- ten levels with linear costs `100 + 25 * n`;
- a repeatable custom route `progressiveskills:physique_training` worth 25 XP;
- one `progressiveskills:global_points` award per new lifetime-highest level;
- a discrete `+2` max-health grant at level 1;
- `+2` max health per level from levels 2 through 5; and
- a `+0.15 add_multiplied_base` jump-strength grant at level 6.

At exactly 975 active XP, Physique is level 6, has awarded six lifetime points, adds ten max-health points, and applies the jump modifier.

## Commands and feedback

| Command | Permission | Result |
|---|---:|---|
| `/pskills skill get <skill>` | player | Shows the caller's active XP, level progress, highest level, bank, and award-currency balances. |
| `/pskills xp <player> <skill> <amount>` | 2 | Awards an exact positive fixed-point amount through the full transaction. |
| `/pskills xp source <player> <key>` | 2 | Fires one configured Phase 7 custom XP route. |

Committed awards show an action-bar XP summary. Level changes send one bounded chat summary and play the vanilla player-level sound. Banked XP and new currency rewards are reported separately.

## Phase boundary

Phase 8 adds one ordinary block break provider with persistent block origin policy plus first time, cooldown, repeat, and fixed rate cap memory. Its rule XP and memory deltas use the same atomic award transaction described above. Later cumulative phases add trees, classes, abilities, carriers, Creator formulas, challenges, assists, and shared contribution paths while continuing to use the same award and transaction authority. Arbitrary modded mover provenance still requires a tested provider. See [RULE_ENGINE.md](RULE_ENGINE.md).
