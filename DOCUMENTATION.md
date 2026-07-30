# ProgressiveSkills documentation

ProgressiveSkills is a server authoritative progression platform for Minecraft 1.21.1 on NeoForge 21.1.236. The approved Phase 19 release contains the complete Phase 1 through Phase 19 delivery train. It includes content packs, deterministic transactions, persistent skills, rules, trees, classes, abilities, carrier items, native screens, provider capabilities, diagnostics, Creator progression, multiplayer systems, and Studio authoring.

Phase 19 is the current approved release on `main`.

## Choose a starting point

| Goal | Read this |
| --- | --- |
| Understand what every phase added | [Complete Phase 1 through Phase 19 guide](docs/guides/PHASES-1-19.md) |
| Build a content pack from scratch | [Detailed pack authoring examples](docs/guides/PACK-AUTHORING.md) |
| Find a command and see a complete workflow | [Command reference](docs/guides/COMMANDS.md) |
| Look up every Core schema field and diagnostic | [Generated Schema v2 reference](docs/reference/SCHEMA-V2.md) |
| Integrate with another mod safely | [Compatibility matrix](docs/compatibility/COMPATIBILITY_MATRIX.md) |
| Run the final player test | [Phase 19 verification and mass check](docs/verification/PHASE-19.md) |
| Understand an internal subsystem | [Architecture index](#architecture-index) |
| Compare implementation with the original design | [Master plan](docs/plan.md) |

## Supported runtime

| Component | Locked value |
| --- | --- |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.236` |
| Java | `21` |
| Gradle | `8.8` |
| ModDevGradle | `2.0.141` |
| Parchment | `1.21.1:2024.11.17` |
| Mod id | `progressiveskills` |
| Root Java package | `com.envisione.progressiveskills` |
| Authoring schema | `2` |
| Application protocol | `7` |

## Player quick start

1. Install the Phase 19 JAR on both the server and every client.
2. Remove every older ProgressiveSkills JAR. Two versions must never be loaded together.
3. Start a new cheats enabled test world. The dependency free starter pack is installed once on first launch.
4. Run `/pskills status`. A healthy result reports a live generation, pack count, definition count, and content digest.
5. Run `/pskills network status`. A fully synchronized player reports `Protocol 7 session ACTIVE`.
6. Press `P` to open the Progression screen.
7. Break one natural stone block. Run `/pskills explain xp last` to see why the rule did or did not award XP.
8. Run `/pskills skill get progressiveskills:physique` to inspect level, XP, banked XP, and Global Points.
9. Use the Trees, Classes, Abilities, Claims, Guide, Compare, Tests, Sync, and Studio tabs from the same screen.
10. Run `/pskills check start` when ready to begin the persistent final mass test.

The starter stone rule awards XP only for natural blocks and blocks placed by a creative player. Survival placed protected blocks are excluded, so silk touch placement loops cannot farm XP. Its cooldown and repeat decay are pack configuration. A pack can set both to their disabled values when full XP without a timeout is desired. See the [anti exploit examples](docs/guides/PACK-AUTHORING.md#block-origin-and-repeat-protection).

## Pack developer quick start

Global packs live in:

```text
config/progressiveskills/packs/<pack-directory>/
```

World overlays live in:

```text
<world>/serverconfig/progressiveskills/packs/<pack-directory>/
```

Minimum pack layout:

```text
my-training-pack/
├── pack.toml
├── skills/
│   └── endurance.toml
├── currencies/
│   └── talent_points.toml
└── rules/
    └── endurance_sprinting.toml
```

Safe publication workflow:

```text
/pskills validate
/pskills reload --dry-run
/pskills diff
/pskills reload --publish
/pskills status
```

`validate` compiles without arming publication. `reload --dry-run` records the exact reviewed candidate. `reload --publish` rereads the files and refuses publication if either source bytes or the semantic digest changed after review.

## Operator quick start

Run these in order after a new install, pack change, or upgrade:

```text
/pskills doctor
/pskills status
/pskills validate
/pskills network status
/pskills persistence status
/pskills item archive verify
/pskills compatibility status
```

Expected healthy state:

- pack and transaction runtimes are available;
- the carrier archive verifies every retained behavior snapshot;
- the player attachment is active rather than quarantined;
- the network session is active with matching sent and acknowledged revisions;
- native providers are healthy;
- unavailable optional providers are reported honestly instead of being silently assumed.

## Phase coverage

| Phase | Delivered system | Primary example |
| --- | --- | --- |
| 1 | Reproducible build, side boundaries, CI, GameTest, server and client smoke, release archive checks | Run the complete verification command set. |
| 2 | Schema registry, stable ids, source spans, canonical IR, generated references | Regenerate Schema v2 and compare byte for byte. |
| 3 | Layered TOML and JSON packs, manifests, dependencies, dry run, diff, atomic publication and recovery | Create a pack and publish only its reviewed digest. |
| 4 | Revision checked transactions, ownership, receipts, idempotency, cascades, audits | Run the lifecycle co owner demonstration. |
| 5 | Versioned player attachments, migration, quarantine, death copy, offline operations, snapshots | Export a readable player snapshot before an upgrade. |
| 6 | Protocol handshake, sanitized projection, chunking, deltas, replay guards and resync | Inspect an active Protocol 7 session. |
| 7 | Fixed point skills, curves, levels, attributes, custom XP and highest level currency awards | Progress the starter Physique skill. |
| 8 | Routed event rules, matchers, multipliers, caps, cooldowns, first time memory and block provenance | Compare natural, creative placed, and survival placed stone. |
| 9 | Typed requirements, fixed point formulas, deterministic rounding, dependency indexes, preview and explanation | Add a minimum skill and currency requirement to an XP rule. |
| 10 | Acyclic trees, exact historical costs, purchases, cascade refunds and respec previews | Buy Conditioning, buy Resilience, then cascade refund Conditioning. |
| 11 | Weighted class slots, selection, swap, respec, source owned grants, synergies and starter receipts | Select Warrior and Scholar to activate Student of War. |
| 12 | Passive, toggle and active abilities, fixed slots, costs, targets, cooldowns, charges and native actions | Assign and activate Second Wind. |
| 13 | Pinned carrier behaviors, behavior archive, delivery, claims, replay protection and explicit migration | Give and consume a Tome of Physique with a full inventory fallback. |
| 14 | Integrated screens, search, guide, compare, test center, sync doctor, HUD editor, command palette and accessibility | Open every tab and complete the Test Center using only the keyboard. |
| 15 | Capability profiles, native providers, absent provider safety, health probes and circuit breakers | Preview strict, preferred, and fallback capability behavior. |
| 16 | Doctor, why traces, reproduction bundles, performance guard, synthetic workloads and persistent mass testing | Export and replay a captured decision bundle. |
| 17 | Templates, predicates, formulas, resources, conversions, prestige, ranks, stances, challenges, contracts, combos, loadouts and build codes | Create a deterministic training contract and share a build code. |
| 18 | Parties, teams, shared progress, contribution receipts, assists, mentoring, consent transfers, seasons, privacy and PvP anti boosting | Form a two player party and inspect a shared award receipt. |
| 19 | Revision checked Studio drafts, forms, graph and curve previews, lint, history, rebase, publish, rollback, JSON and signed pspack import and export | Author a draft, lint it, publish its confirmed digest, then roll it back. |

Every phase has a full description, commands, expected results, failure behavior, and a hands on example in the [complete phase guide](docs/guides/PHASES-1-19.md).

## UI and key mappings

| Action | Default key | Notes |
| --- | --- | --- |
| Open Progression | `P` | Available after synchronization becomes active. |
| Open tree screen | `K` | Opens the focused tree view. |
| Ability wheel | Left Alt | Displays assigned slots. |
| Previous ability | Left bracket | Selects the previous assigned slot. |
| Next ability | Right bracket | Selects the next assigned slot. |
| Use selected ability | `R` | Activates an active ability or changes a toggle. |
| Command palette | Grave accent | Opens keyboard first navigation and safe command actions. |
| Direct ability slots one through eight | Unbound | Bind only the slots you want in Minecraft Controls. |

The Progression screen has ten tabs: Skills, Trees, Classes, Abilities, Claims, Guide, Compare, Tests, Sync, and Studio. High contrast, reduced motion, compact layout, text scaling, HUD position, HUD scale, and HUD opacity are local client preferences. They are stored in `config/progressiveskills-client.properties` and never alter server authority.

## Authority and safety model

- Pack files describe content. They do not directly mutate players.
- The server validates all requirements, balances, targets, costs, cooldowns, revisions, and provider readiness.
- A successful mutation commits through one bounded transaction and then persists before synchronization.
- Idempotency keys and receipts prevent duplicate physical rewards and repeated carrier use.
- Definitions are published as an atomic generation. A failed reload leaves the last known good generation active.
- Clients receive sanitized presentation and owner visible state. They do not receive raw ownership maps, hidden requirements, receipts, audit bodies, provider secrets, or Studio signing identities.
- Stale sessions, definitions, player revisions, preview digests, and draft revisions fail closed.
- Safe Retry remembers only an action that can be rebuilt against synchronized current state. It never resends arbitrary client supplied economics.
- Unknown, corrupt, oversized, or future persistence data is quarantined rather than guessed into a current model.
- External adapters are unavailable until their exact artifacts and supported ranges pass present mod tests. The base mod remains safe without them.

## Build and verification

```text
./gradlew clean build verifySchemaArtifacts --no-daemon --stacktrace
./gradlew runGameTestServer --no-daemon --stacktrace
bash .ci/smoke-server.sh
bash .ci/smoke-client.sh
bash .ci/verify-release-jar.sh releases/phase-19/progressiveskills-phase-19.jar
```

The full gate covers Java compilation, architecture boundaries, unit tests, property tests, schema freshness, real rule performance fixtures, NeoForge GameTests, dedicated server startup, headless client startup, and runtime JAR contents.

GitHub Actions in the repository-specific foundation workflow are pinned to reviewed full commit SHAs. The shared organization workflows remain pinned to one reviewed central workflow commit. Updating either pin requires reviewing the upstream change and rerunning the complete gate.

The release JAR is:

```text
releases/phase-19/progressiveskills-phase-19.jar
```

Its recorded SHA 256 is:

```text
a4dc3435359774f2aeec2d121cd040feb810804957fea88312d15f911255d67a
```

## Troubleshooting

| Symptom | Meaning | Next action |
| --- | --- | --- |
| `/pskills status` says unavailable | No valid primary or recovery generation loaded | Inspect server diagnostics, repair the pack, validate, dry run, and publish. |
| Publish says sources changed | Files changed after dry run | Repeat dry run and review the new diff. |
| A mutation reports stale state | The client built it against an older session, definition, or player revision | Wait for Sync to become active, then use Safe Retry or submit it again. |
| `/pskills network status` is not active | Handshake, transfer, state snapshot, or acknowledgement is incomplete | Reconnect or use `/pskills network resync`, then inspect `/pskills doctor`. |
| Player data is quarantined | Input was malformed, oversized, corrupt, foreign, or from a future data version | Preserve the digest and export, restore a compatible backup, and never force the data into gameplay. |
| A carrier is inert | Its definition or pinned behavior digest is unavailable, invalid, or deliberately invalidated | Run `/pskills item held` and `/pskills item archive verify`, then use explicit migration if offered. |
| A carrier delivery is missing | Inventory delivery could not complete | Run `/pskills claim list`, free slots, then `/pskills claim take all`. |
| A compatibility profile is unusable | A required capability has no healthy provider | Install an exact supported adapter or change the pack profile intentionally. |
| Studio rejects a write | Draft revision is stale or the path or contents violate bounds | Run draft status, rebase if appropriate, and retry with the current revision. |
| Studio refuses publish | Lint digest, revision, permissions, or live base changed | Lint again, inspect the diff, rebase, and confirm the new digest. |
| Shared data is hidden | The other player disabled that privacy category | Treat the redaction as authoritative. Do not infer hidden values. |
| Client smoke cannot start | Headless Linux lacks Xvfb | Install `xvfb-run` or run `./gradlew runClient` in a graphical session. |

## Architecture index

- [Schema and immutable IR](docs/architecture/SCHEMA_AND_IR.md)
- [Content packs and atomic publication](docs/architecture/CONTENT_PACKS.md)
- [Transactions and lifecycles](docs/architecture/TRANSACTIONS_AND_LIFECYCLES.md)
- [Persistence and migrations](docs/architecture/PERSISTENCE_AND_MIGRATIONS.md)
- [Networking and client projection](docs/architecture/NETWORKING.md)
- [Skill XP](docs/architecture/SKILL_XP.md)
- [Rule engine and anti exploit behavior](docs/architecture/RULE_ENGINE.md)
- [Requirements and expressions](docs/architecture/REQUIREMENTS_AND_EXPRESSIONS.md)
- [Trees and exact refunds](docs/architecture/TREES_AND_REFUNDS.md)
- [Classes and entitlements](docs/architecture/CLASSES_AND_ENTITLEMENTS.md)
- [Abilities and actions](docs/architecture/ABILITIES_AND_ACTIONS.md)
- [Carrier items and claims](docs/architecture/CARRIER_ITEMS_AND_CLAIMS.md)
- [Package and physical side boundaries](docs/architecture/PACKAGE_BOUNDARIES.md)
- [Performance fixture PERF 001](docs/performance/PERF-001.md)

## Evidence index

Historical evidence for Phases 1 through 12 remains in `docs/verification/PHASE-N.md`. Phases 13 through 18 have explicit cumulative implementation records in the same directory, and their combined automated evidence, exact JAR, checksum, and player checklist are in [PHASE-19.md](docs/verification/PHASE-19.md). Historical records describe the boundary at that checkpoint; the complete guide describes current cumulative behavior.
