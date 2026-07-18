# Complete Phase 1 through Phase 19 guide

This guide explains the approved Phase 19 release in implementation order. Each section answers four questions: what the phase added, how it is used, what a successful example looks like, and how it fails safely.

For exact Core field metadata, use the [generated Schema v2 reference](../reference/SCHEMA-V2.md). For every command syntax, use the [command reference](COMMANDS.md). For larger pack files, use the [pack authoring guide](PACK-AUTHORING.md).

## Phase 1. Scaffold, CI, and evidence lock

Phase 1 established the reproducible runtime and the rules that keep later gameplay code shippable.

Delivered behavior:

- Java 21, Minecraft 1.21.1, NeoForge 21.1.236, Parchment 2024.11.17, Gradle 8.8, and ModDevGradle 2.0.141 are pinned.
- Production, unit test, and GameTest source sets are separate.
- Client code cannot leak into common or dedicated server paths.
- Optional integration code cannot become a mandatory classloading dependency.
- The build fails if test sources disappear, no tests are discovered, schema output becomes stale, or the release JAR contains test classes or test libraries.
- Dedicated server, client title screen, and NeoForge GameTest boots are first class verification steps.

Full verification example:

```text
./gradlew clean build verifySchemaArtifacts --no-daemon --stacktrace
./gradlew runGameTestServer --no-daemon --stacktrace
bash .ci/smoke-server.sh
bash .ci/smoke-client.sh
bash .ci/verify-release-jar.sh releases/phase-19/progressiveskills-phase-19.jar
```

Expected result:

- Gradle exits successfully.
- Both required GameTests pass.
- The dedicated server reaches `Done` and shuts down cleanly.
- The headless client reaches the real title screen.
- Archive verification reports no GameTest, JUnit, jqwik, ArchUnit, fixture, or example MDK payload in the shipping JAR.

Safe failure example: if a class in `common` imports a class under `client`, the architecture test stops the build. Moving the import behind a client event subscriber fixes the physical side violation without weakening the test.

## Phase 2. Schema registry and canonical IR

Phase 2 made authored data deterministic before any gameplay system consumed it.

Delivered behavior:

- namespaced stable ids such as `mypack:endurance`;
- path derived definition identity;
- direct aliases and replacement history;
- portable source positions and half open source spans;
- immutable canonical values and definitions;
- field level provenance;
- safe localized text, style, and icon metadata;
- stable diagnostic codes;
- generated Markdown and editor JSON from the same registry used by tests.

Identity example:

```text
Pack namespace: mypack
Directory: skills
Relative file: endurance.toml
Derived id: mypack:endurance
Declared id: mypack:endurance
Result: valid
```

Changing the declared id to `mypack:sprinting` while leaving the file at `skills/endurance.toml` fails. Rename the file to `skills/sprinting.toml` or restore the matching id. This prevents a file move from silently changing one identity into another.

Schema maintenance example:

```text
./gradlew generateSchemaArtifacts --no-daemon
./gradlew verifySchemaArtifacts --no-daemon
```

The first command rewrites `docs/reference/SCHEMA-V2.md` and `docs/reference/schema-v2-editor.json`. The second regenerates them in `build` and requires an exact byte match.

## Phase 3. Content packs and staged publication

Phase 3 added discovery, manifests, dependency ordering, layering, semantic diffs, and last known good publication.

Pack roots:

```text
config/progressiveskills/packs/
<world>/serverconfig/progressiveskills/packs/
```

World packs have higher source precedence. Manifest priorities and dependency order are still validated, so precedence is explicit rather than file system accident.

Minimal manifest example:

```toml
schema_version = 2

[pack]
id = "mypack:training"
namespace = "mypack"
name = { fallback = "Training Pack" }
description = "Endurance progression for a survival server."
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

Publication example:

```text
/pskills validate
/pskills reload --dry-run
/pskills diff
/pskills reload --publish
/pskills status
```

Detailed flow:

1. `validate` discovers and compiles current files without replacing live definitions.
2. `reload --dry-run` records the exact source digest and semantic candidate.
3. `diff` shows added, changed, disabled, and removed definitions.
4. `reload --publish` rereads disk, checks that it still matches the reviewed candidate, reserves required carrier archive entries, and atomically swaps the generation.
5. If any step fails, the previous generation stays active.

Safe failure example: edit `skills/endurance.toml` after dry run and before publish. Publish refuses with a source changed message. Run dry run again and review the new diff.

## Phase 4. Transaction and lifecycle core

Phase 4 established one authority path for every progression mutation.

Every plan carries:

- target and actor identity;
- expected definition generation and semantic digest;
- expected player state revision;
- a stable idempotency key;
- checked balance and state mutations;
- persistent ownership changes;
- transition actions;
- receipt and audit behavior;
- deterministic child cascade order.

Lifecycle demonstration:

```text
/pskills lifecycle status
/pskills lifecycle demo
/pskills lifecycle demo
/pskills lifecycle coowner
/pskills lifecycle recompute
/pskills lifecycle revoke primary
/pskills lifecycle revoke secondary
/pskills lifecycle audit
/pskills lifecycle selftest
```

Expected behavior:

- The first demo call commits one demo point and one receipt protected item.
- Repeating the same logical operation cannot duplicate that physical reward.
- Two owners can contribute the same persistent health effect.
- Removing the first owner leaves the effect active.
- Removing the final owner removes the effect.
- Recompute projects persistent ownership only. It cannot replay transition rewards.

Safe failure example: if the inventory cannot accept a required preflighted item and no delivery contract exists, the transaction rejects before changing balances or ownership.

## Phase 5. Persistence, migrations, and quarantine

Phase 5 stores player authority in the versioned `progressiveskills:player_data` attachment and server scoped work in bounded SavedData.

Delivered behavior:

- raw NBT bounds are checked before typed decode;
- `data_version` controls a pure migration chain;
- current data version is 2;
- a bounded migration shadow preserves the previous representation through a clean save cycle;
- death clones copy progression while non death replacement semantics remain distinct;
- unloaded players receive pending operations instead of direct offline file edits;
- corrupt, oversized, foreign owner, and future version inputs become quarantined;
- snapshots and readable exports are written under the world directory.

Maintenance example:

```text
/pskills persistence status
/pskills persistence snapshot
/pskills persistence export
```

Use `snapshot` before a planned upgrade. It creates a verified machine oriented snapshot. Use `export` when a human readable record is also needed for diagnosis.

Death and restart example:

1. Award Physique XP and buy a tree node.
2. Run `/pskills persistence status` and record both storage and transaction revisions.
3. Die normally and respawn. Confirm skill, paid cost, and ownership remain.
4. Stop the server cleanly, restart, and confirm the same state.

Safe failure example: a future `data_version` is never decoded as current data. The player state is quarantined, excluded from gameplay projection, and reported with a reason and raw digest.

## Phase 6. Networking and sanitized client projection

Phase 6 added a bounded application protocol on top of NeoForge payload registration. The cumulative Phase 19 protocol and registrar version are both 7.

The handshake pins:

- persistent server identity;
- ephemeral session identity;
- required feature bitset;
- definition generation;
- semantic and presentation digests;
- owner visible storage and state revisions.

The client receives sanitized definitions and its own visible state. It does not receive source maps, raw owner sets, hidden requirements, paid cost history, idempotency results, audit records, archive behavior bodies, provider secrets, or Studio private signing identity.

Status example:

```text
/pskills network status
```

Expected healthy output includes:

```text
Protocol 7 session ACTIVE
```

It also reports definition generation, short semantic and presentation digests, cache hit state, sent and acknowledged revisions, delta count, full resync count, and cached intent results.

Recovery example:

```text
/pskills network resync
/pskills network status
```

Safe failure example: a client submits an ability activation created at state revision 41 after the server has advanced to 42. The server rejects the stale action, sends current state, and Safe Retry may rebuild the same safe intent after synchronization. It never trusts or reuses client supplied costs.

## Phase 7. Fixed point skill XP

Phase 7 delivered the first complete gameplay slice through the starter Physique skill.

Physique demonstrates:

- fixed point XP accounting;
- a linear curve with explicit rounding;
- maximum level and overflow policy;
- negative XP policy;
- lifetime highest level currency awards;
- level milestone effects;
- scaling effects;
- manual and named custom XP sources;
- persistent attribute ownership.

Starter curve:

```toml
[skill]
id = "progressiveskills:physique"
max_level = 10
enabled = true
overflow = "bank"
negative_xp_policy = "deny"

[curve]
type = "linear"
base = 100
step = 25
rounding = "ceil"
```

Level zero to one costs 100 XP units. Level one to two costs 125, then 150, and so on. XP beyond level ten is banked because the overflow policy is `bank`.

Command example:

```text
/pskills xp @s progressiveskills:physique 100
/pskills skill get progressiveskills:physique
/pskills xp source @s progressiveskills:physique_training
```

The first command is an operator manual award. The second prints the active level, lifetime highest level, active XP, progress into the current level, next cost, banked XP, and related currency balance. The third executes the pack defined custom source amount.

Highest level award example: reaching level three grants three lifetime Global Points. Dropping and regaining a previously reached level cannot mint the same highest level point again.

## Phase 8. Rules and anti exploit foundations

Phase 8 routes events through compiled trigger and matcher tables. A rule chooses candidates by trigger and subject, evaluates requirements, applies multipliers in stable stage and priority order, resolves stack groups, rounds, applies anti exploit memory, and commits outputs with that memory in the same transaction.

Starter stone protection:

```toml
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
```

Origin example:

1. Break naturally generated stone. The origin is `natural`, so the rule may award.
2. Place stone in creative and break it. The origin is `creative_placed`, so the rule may award if the pack permits it.
3. Place stone in survival and break it. The tracked origin is protected, so the starter rule rejects it.
4. Mine the same route repeatedly. Cooldown, caps, and repeat decay reduce or reject awards according to configuration.

Full XP without timeout:

```toml
cooldown_ticks = 0
repeat_window_ticks = 0
repeat_decay = 1.0
minimum_multiplier = 1.0
```

Set caps high enough for the intended server economy, or omit optional caps where the schema permits it. Block origin protection is independent, so a pack can allow full speed natural awards while still rejecting survival placement loops.

Explanation example:

```text
/pskills rule status
/pskills rule preview progressiveskills:physique_stone_training
/pskills explain xp last
```

## Phase 9. Requirements and expressions

Phase 9 added typed requirements, a dependency index, deterministic fixed point evaluation, explicit missing value policy, preview traces, and rounding.

Rule requirement example:

```toml
requirements = [
  { type = "skill_level", subject = "actor", missing = false, skill = "mypack:endurance", op = ">=", value = 5 },
  { type = "currency", subject = "actor", missing = false, currency = "mypack:talent_points", op = ">=", value = 2 }
]
```

Both rows must pass because the list is an AND list. The explicit `missing = false` means an unavailable dependency fails instead of becoming zero by accident.

Preview example:

```text
/pskills rule preview mypack:endurance_night_training
```

The result states whether requirements passed, how many were checked, dependency count, calculated amount, rounding mode, and the first failure. Preview never mutates rule memory or player balances.

Formula example in Creator:

```text
/pskills creator simulate max(10,progressiveskills:global_points*2)
```

The expression compiler uses a closed grammar and fixed point arithmetic. Unknown dependencies, overflow, invalid arity, unsupported functions, and malformed input fail before runtime execution.

## Phase 10. Trees and exact refunds

Phase 10 added single rank acyclic trees with paid cost history.

Important guarantees:

- purchase checks the current price and requirements in one transaction;
- the exact currency and amount actually paid are stored with the purchase;
- refund uses that historical record instead of the current definition price;
- dependent refund closure is deterministic;
- removing a node owner preserves grants that still have another owner;
- previews produce a digest that confirmation must repeat.

Starter workflow:

```text
/pskills tree list
/pskills tree info progressiveskills:physique_training
/pskills tree preview buy progressiveskills:physique_training progressiveskills:physique_training/conditioning
/pskills tree buy progressiveskills:physique_training progressiveskills:physique_training/conditioning
/pskills tree preview buy progressiveskills:physique_training progressiveskills:physique_training/resilience
/pskills tree buy progressiveskills:physique_training progressiveskills:physique_training/resilience
/pskills tree preview refund progressiveskills:physique_training progressiveskills:physique_training/conditioning
```

The refund preview for Conditioning includes Resilience because Resilience depends on it. Copy the preview digest into:

```text
/pskills tree refund progressiveskills:physique_training progressiveskills:physique_training/conditioning <digest>
```

Safe failure example: if a price, requirement, ownership record, definition digest, or player revision changes between preview and confirmation, the digest no longer matches and nothing is refunded.

## Phase 11. Classes and source owned entitlements

Phase 11 added weighted slots, selection and respec costs, class swaps, starter kits, grants, and synergies.

Starter combat slot:

```toml
[class_slot]
id = "progressiveskills:combat"
capacity = 2
swap_policy = "allowed"
```

Warrior and Scholar each cost one slot, so both fit. Their `Student of War` synergy grants Combat Insight only while both classes are active.

Workflow:

```text
/pskills class list
/pskills class preview select progressiveskills:warrior
/pskills class select progressiveskills:warrior
/pskills class select progressiveskills:scholar
/pskills class entitlements
/pskills ability list
```

Expected result:

- Warrior grants attack damage, Warrior Guard, Second Wind, and its starter kit receipt.
- Scholar grants tree access and a stage entitlement.
- Selecting both activates the Combat Insight synergy.
- The starter item is receipt protected and cannot be obtained repeatedly through respec.

Respec example:

```text
/pskills class preview respec progressiveskills:warrior
/pskills class respec progressiveskills:warrior <digest>
```

Safe ownership behavior: if another class or tree also owns an ability, removing Warrior removes only the Warrior source. The ability remains owned until its final source disappears.

## Phase 12. Ability bar and native actions

Phase 12 added passive, toggle, and active ability lifecycles.

Supported Core behavior includes:

- eight fixed client startup slots;
- ownership and slot eligibility;
- assignment, unassignment, and selection;
- persistent attribute and flag effects;
- named currency, hunger, and vanilla experience costs;
- self, entity, and block target policies;
- cooldown groups, charges, and recharge;
- message, heal, and vanilla effect actions.

Second Wind example:

```text
/pskills ability info progressiveskills:second_wind
/pskills ability assign progressiveskills:second_wind 1
/pskills ability select 1
/pskills ability activate 1
/pskills ability status
```

The server checks ownership, assignment, target, hunger cost, charge, and cooldown before committing. A successful activation spends four hunger points, consumes a charge, starts the recovery cooldown, sends a message, heals four health points, and applies Speed for 100 ticks.

Immediate second activation is rejected by cooldown without spending another charge or cost.

Toggle example:

```text
/pskills ability assign progressiveskills:warrior_guard 2
/pskills ability toggle progressiveskills:warrior_guard
```

The armor and guarding flag appear only while the toggle is active and owned.

## Phase 13. Carrier items, archives, and claims

Phase 13 added five pre registered carrier item kinds: artifact, charm, consumable, token, and tome. Definitions configure item presentation and behavior without registering new item types during reload.

Every issued stack pins:

- definition id;
- behavior version;
- canonical behavior digest;
- charges and use counter;
- binding state when configured.

The server behavior archive retains canonical snapshots by digest. Existing items continue using their pinned behavior after a live definition changes unless the pack and operator complete an explicit migration.

Workflow:

```text
/pskills item list
/pskills item info progressiveskills:tome_of_physique
/pskills give @s progressiveskills:tome_of_physique 1
/pskills item held
/pskills item archive status
/pskills item archive verify
```

Full inventory example:

1. Fill every inventory slot.
2. Run `/pskills give @s progressiveskills:tome_of_physique 1`.
3. Delivery creates a durable pending claim when configured for `pending_claim`.
4. Run `/pskills claim list`.
5. Free a slot and run `/pskills claim take all`.

Migration example:

```text
/pskills item migrate held preview
/pskills item migrate held confirm <preview_digest>
```

Safe failure example: edited components, repeated unique use counters, missing archive digests, stale migration previews, and client supplied behavior bodies fail closed.

## Phase 14. Baseline UI, guide, and accessibility

Phase 14 integrated progression into ordinary client screens while keeping every mutation server authoritative.

Press `P` to open the ten tab Progression screen:

1. Skills shows levels, XP, and progression details.
2. Trees links to the focused tree interaction screen.
3. Classes shows current state and preview or confirmation actions.
4. Abilities assigns, unassigns, toggles, and activates visible owned abilities.
5. Claims inspects held carriers and recovers pending claims.
6. Guide provides native searchable definition pages.
7. Compare holds two hypothetical build choices beside the current build.
8. Tests stores a local visual and input checklist.
9. Sync shows connection phase, revisions, short digests, resync, and Safe Retry.
10. Studio opens the authoring interface.

Keyboard example:

1. Press `P`.
2. Use Tab and Shift Tab to move through tab buttons, search, rows, and actions.
3. Use Enter or Space to activate the focused control.
4. Search by display name, id, or details.
5. Open the command palette with the grave accent key and type `doctor`, `retry`, `HUD`, or `Studio`.

Accessibility controls include high contrast, reduced motion, compact layout, 100, 125, and 150 percent text settings, narration messages, color independent state words, and a movable HUD with four anchors, bounded offsets, 75 through 150 percent scale, and 50, 75, or 100 percent opacity.

Local UI preferences are not sent as authority and do not change game outcomes.

## Phase 15. Provider capabilities and compatibility

Phase 15 added provider neutral contracts rather than pretending every optional mod version is compatible.

Capabilities include vanilla and modded attributes, spell ownership, stage ownership, party membership, shared storage, carrier slots, recipe viewer, guide export, and script builders.

Pack profile example:

```toml
schema_version = 2

[compatibility_profile]
id = "mypack:default_compatibility"
mode = "fallback"
required = ["vanilla_attributes"]
preferred = ["stage_ownership", "party_membership", "carrier_slots"]
active = true
```

Modes:

- `strict` requires every required capability and refuses an unusable plan.
- `preferred` selects healthy preferred providers when present while preserving required capability rules.
- `fallback` uses native or declared fallback behavior when optional capabilities are absent.

Commands:

```text
/pskills compatibility status
/pskills compatibility profile list
/pskills compatibility profile active
/pskills compatibility profile mypack:default_compatibility
/pskills compatibility profile strict
/pskills compatibility profile preferred
/pskills compatibility profile fallback
```

Provider self tests are bounded and nonmutating. Repeated provider failures open a circuit breaker. External integrations in the compatibility matrix remain unavailable until an exact artifact and supported range pass absent and present mod tests.

## Phase 16. Hardening, diagnostics, and mass testing

Phase 16 added operations tooling and final release gates.

Doctor example:

```text
/pskills doctor
/pskills doctor json
```

The readable form checks pack generation, transaction availability, carrier archive integrity, providers, performance guard state, player network session, and player data. The JSON form is suitable for log capture and external diagnostics.

Decision explanation:

```text
/pskills why latest
/pskills why verbose
```

The latest accepted or rejected XP, ability, social, or other recorded decision includes category, subject, allow or deny state, reason, time, and state revision.

Reproduction example:

```text
/pskills reproduce export
/pskills reproduce replay reproduction_<bundle_id>.json
```

The bundle captures a redacted bounded state, definition digest, seed, decision events, and per event state revisions. Replay uses only the captured bundle state. It never reads the current live player to decide whether the historical event should match.

Performance example:

```text
/pskills perf smoke
```

The locked PERF 001 driver compiles 10000 real rule definitions with five matchers each, compiled requirements, and rule stack resolution. It executes fixed 40 and 100 player warmup and capture schedules and reports percentile timing.

Persistent final test:

```text
/pskills check start
/pskills check pass natural stone awarded expected XP
/pskills check fail ability wheel did not open
/pskills check status
/pskills check finish
/pskills check export
```

The checklist is stored with the world and can resume after restart.

## Phase 17. Creator progression

Phase 17 exposes advanced generic definition kinds and deterministic runtime operations.

Implemented Creator families include:

- templates and inheritance;
- variables and formulas;
- reusable predicates;
- custom bounded resources with decay;
- currency conversions with ratios, fees, and maximum input;
- grant bundles and milestone choices;
- deterministic training contracts;
- cross skill combo mastery;
- prestige tracks and rewards;
- ranked tree and class purchases;
- mutually exclusive stances;
- reactive procs;
- context effects;
- solo challenges;
- saved loadouts and digest pinned build codes;
- simulation and trace commands.

Resource example:

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

Commands:

```text
/pskills resource get mypack:focus
/pskills resource add mypack:focus 10
```

Build example:

```text
/pskills loadout save mypack:boss_build
/pskills loadout code mypack:boss_build
/pskills build code
/pskills build inspect <code>
/pskills build apply <code>
```

Build codes contain a definition digest plus desired classes, tree nodes, and ability assignments. Apply creates one combined revision checked transaction. A code from another definition digest is rejected rather than partially applied.

Training example:

```text
/pskills contract assign mypack:daily_training
/pskills contract status
/pskills contract progress mypack:daily_training progressiveskills:physique 5
```

Contract selection and goal are deterministic for the definition, player, and assignment epoch. Repeating the same epoch cannot reroll a better task.

## Phase 18. Multiplayer progression

Phase 18 added native provider backed parties and teams, contribution accounting, mentoring, consent transfers, seasons, privacy, assists, and PvP anti boosting.

Party example with Alice and Bob:

```text
Alice: /pskills party create Dungeon Team
Alice: /pskills party invite Bob
Bob:   /pskills party accept
Alice: /pskills party ready true
Bob:   /pskills party ready true
Both:  /pskills party readiness
```

Each player controls whether role, build, resources, and cooldown readiness are visible. Readiness displays only consented categories.

Shared award example:

```text
/pskills contribution share mypack:boss_defeat 1000
/pskills contribution receipts
```

The allocator produces bounded contribution receipts explaining the source, total, shares, and privacy safe calculation. It does not expose another player's private progression state.

Consent transfer example:

```text
Alice: /pskills transfer offer Bob progressiveskills:global_points 5
Bob:   /pskills transfer accept
Bob:   /pskills transfer wallet progressiveskills:global_points
```

No currency moves at offer time. Acceptance rechecks both parties and current balances.

Season example:

```text
/pskills season status
/pskills season score Alice mypack:physique_season 250
/pskills season leaderboard mypack:physique_season 10
/pskills season rollover mypack:summer 2
```

Leaderboard privacy is honored. Rollover is epoch pinned and replay safe.

PvP awards use the active multiplayer profile. Pair cooldown, daily pair cap, repeat multiplier, level gap penalty, minimum multiplier, and enable switch are all pack controlled.

## Phase 19. Studio authoring and signed packs

Phase 19 provides operator only draft authoring. Studio never bypasses the Phase 3 compiler or publication transaction.

Core concepts:

- live content and drafts are separate;
- every draft write requires the current revision;
- lint creates the validation and confirmation digest;
- history retains bounded revisions;
- rebase detects a changed live base;
- publish is atomic and last known good aware;
- rollback restores a prior published state safely;
- TOML and datapack JSON compile to the same canonical IR;
- pspack paths, sizes, entry counts, and signatures are checked before extraction;
- Ed25519 signatures embed the portable public identity while the private key remains world local;
- import is an explicit permission level four trust action and records the signer fingerprint.

Text workflow:

```text
/pskills studio draft create mypack Balance Update
/pskills studio draft list
/pskills studio draft status <draft_id>
/pskills studio file put <draft_id> 0 skills/endurance.json {"schema_version":2,"skill":{"id":"mypack:endurance","display":{"fallback":"Endurance"},"description":{"fallback":"Sustained physical effort."},"icon":{"type":"item","value":"minecraft:leather_boots","fallback":"minecraft:barrier","alt":"Leather boots"},"enabled":true,"max_level":10,"overflow":"bank","negative_xp_policy":"deny"},"curve":{"type":"linear","base":100,"step":25,"rounding":"ceil"}}
/pskills studio lint <draft_id>
/pskills studio diff <draft_id>
/pskills studio history <draft_id>
/pskills studio publish <draft_id> <revision> <lint_digest>
```

Create returns a random stable id in the form `mypack:studio/<uuid>`. Copy that complete value as `<draft_id>`. The successful file write returns a new revision. Use that revision for later writes. Lint returns the digest that publish requires. Never use an older revision or a digest from an earlier draft state.

Rollback and export:

```text
/pskills studio rollback <draft_id> <current_revision>
/pskills studio export <draft_id>
/pskills studio import imported_namespace <file.pspack> Imported Pack
```

Visual workflow:

1. Press the grave accent key.
2. Choose Open authoring Studio.
3. Enter the draft id and current revision.
4. Choose a definition kind and complete the generated form.
5. Create the revision checked JSON file.
6. Use Lint, Diff, History, Rebase, Preview, and Graph before publishing.
7. Confirm the lint digest through the text command when ready.

Studio recorder example:

```text
/pskills studio record start
/pskills studio record mark xp progressiveskills:physique 100
/pskills studio record mark ability progressiveskills:second_wind 1
/pskills studio record stop <draft_id> <revision> mypack:second_wind_fixture
```

The recorder writes a redacted declarative fixture into the draft. It never publishes the recording directly.

## Cumulative end to end example

This scenario crosses the complete stack:

1. Boot the verified Phase 19 JAR and confirm `/pskills status`.
2. Publish a pack with one skill, rule, tree, class, ability, carrier, and multiplayer profile.
3. Break a natural block and inspect `/pskills explain xp last`.
4. Confirm fixed point XP commits and persists.
5. Spend the highest level currency on a tree node.
6. Select a class that owns an ability.
7. Assign and activate the ability.
8. Receive a carrier with a full inventory, then recover its claim.
9. Relog, die, change dimension, and restart. Confirm revisions and ownership survive.
10. Form a party with a second client, enable only selected readiness fields, and share an award.
11. Save a loadout and apply its build code after a state change.
12. Author a Studio draft, lint it, publish its confirmed digest, and verify the client receives the new generation.
13. Export and replay a reproduction bundle for the session.
14. Complete `/pskills check`, export the report, and retain it with the tested JAR checksum.

If any stage fails, capture the exact command, expected result, observed result, latest client and server logs, and whether relog or restart changes the behavior.
