# ProgressiveSkills — **Master Product & Engineering Plan v2.0**

> **Revision intent:** this edition keeps the original “configuration is the content” vision, but makes the engine technically honest about frozen registries, dynamic keybinds, one-shot rewards, networking limits, optional-mod classloading, offline players, and reload safety. It also expands the design into a true progression platform: layered content packs, formulas, reusable predicates, ranked/exclusive trees, class evolution, resources, ability bars, challenges, seasons, social progression, an authoring studio, diagnostics, simulations, snapshots, and a public adapter API.
>
> **Normative rule:** every copy-paste example in this document must conform to the canonical schema. A compact legacy/shorthand form is allowed only when it is explicitly labeled as compiler sugar and has identical lifecycle/identity semantics. All authoring adapters compile into one versioned internal representation.

> A fully data-driven skill / class / attribute progression mod for **Minecraft 1.21.1 / NeoForge**.
> Part of the Progressive family (pairs with **ProgressiveStages**): same DNA, same conventions.
> Solo-Leveling-style: level individual skills, gain guaranteed per-level power **and/or** spend currencies in optional skill trees, combine classes through pack-defined slots/capacity, drive vanilla/modded attributes, hook Iron's Spells 'n Spellbooks, and gate content through ProgressiveStages.
>
> **Rule #1 — Everything is customizable by the modpack developer.** No hardcoded skills, classes, trees, curves, items, or bindings ship as "the content." The base mod ships an *engine* + example TOML.
> **Rule #2 — First-class ProgressiveStages integration**, mirroring its friendly per-definition authoring and command conventions while using version-pinned, ownership-safe capabilities. Localized Components are canonical; `&` text is only shorthand.
> **Rule #3 — Maximum customization without impossible runtime promises.** Minecraft registry objects and client key mappings are registered during startup. Runtime definitions therefore configure generic carriers, fixed ability slots, render descriptors, and synced behavior; they do not pretend to create arbitrary new registry ids or Controls-menu entries after registries/events have closed.

### Project Identity & Environment (locked)

| Field | Value |
|---|---|
| Mod name | **ProgressiveSkills** |
| Mod ID | `progressiveskills` |
| Root package | `com.envisione.progressiveskills` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.236` (latest published 21.1 build in the official Maven index at plan revision time) |
| Java toolchain | Java 21 |
| Loader | NeoForge |
| Build status | original plan reports a green ~6s scaffold; repository was not attached to this review, so CI must re-run after the verified `21.1.236` repin before this is treated as evidence |
| Tests | none yet (`test NO-SOURCE`) — establish the harness in Phase 1 |
| Plan revision | **2.0 — expanded and feasibility-corrected** |
| Definition schema | Versioned (`schema_version`), compiled to a canonical immutable IR |
| Content promise | Core 1.0 is shippable; the complete north-star catalog is staged across release trains (§49) |

**Known cleanup (Phase 1):** four non-blocking deprecation warnings for `EventBusSubscriber.Bus.MOD`. Fix = drop the `bus = ...` parameter (NeoForge auto-detects the bus per event since 1.21.1); for mod-bus-only events (keybinds, registrations) register via `modEventBus.addListener(...)` in the constructor. Warnings only — not build-breaking.

---

## 0. Design Pillars (non-negotiable)

1. **Config is the content.** The jar is an interpreter. A pack dev with zero Java defines the entire experience in `config/progressiveskills/`.
2. **TOML is the default authoring format**, one definition per file inside namespaced content packs. Bootstrap/common, world/server, and client settings are separate; all definition adapters compile to one IR (§11, §32).
3. **One typed vocabulary, explicit lifecycles.** Skills, nodes, classes, abilities, items, challenges, and rules use the same registered effect/action types. Persistent effects, transition actions, and numeric transactions share validation/provenance but execute according to their lifecycle matrix (§35).
4. **Linear and/or tree — the dev's choice, and both can coexist.** A skill may provide guaranteed threshold/scaling effects and independently award a named currency used by optional trees. A pack can use either or both (§4.5, §37).
5. **Gameplay/content is toggleable & tunable.** Enforcement policies, attribute mappings, XP sources, items, and presentation expose pack controls within immutable server-authority, security, persistence, and hard resource-ceiling invariants. Packet/import bounds and anti-corruption guarantees are not disable switches.
6. **Maximum compatibility, soft dependencies only.** The base mod runs without Iron's Spells, ProgressiveStages, KubeJS, Curios, FTB, JEI/EMI, Jade, Patchouli, or attribute addons. Each shipped bridge is version-pinned, isolated behind a capability interface, and follows the pack's explicit missing-integration policy; reflection is used only for unstable gaps, not as a blanket promise.
7. **Server-authoritative, client-mirrored.** All progression state on the server (SavedData + attachment). Client gets sync payloads for GUI/HUD only. No client-trusted XP.
8. **Deterministic & inspectable.** `/pskills validate` catches every malformed def at load; `/pskills info` dumps any resolved definition; debug logging is a toggle.
9. **📖 DOCUMENTATION IS A DELIVERABLE — KISS.** Every feature ships with schema-generated reference docs, copy-paste examples, troubleshooting, and native in-game encyclopedia coverage in the same change. Patchouli is an optional renderer/export. **Code without docs/schema/editor/guide metadata is a bug.**
10. **Stable identity everywhere.** Every definition, grant, XP source, rule, node rank, synergy, receipt, and migration target has a stable `ResourceLocation` id. Array order is presentation only and is never persistence identity.
11. **Effects have explicit lifecycles.** Durable/reversible outputs are recomputed; transition outputs run only on a validated edge; repeatable actions run only inside a transaction. Reconciliation never executes transition actions; crash guarantees follow each action's documented delivery contract (§35).
12. **One canonical IR, many authoring surfaces.** TOML remains the friendly default, while datapack JSON, KubeJS builders, Java adapters, templates, and the Authoring Studio compile to the same typed model. Behavior cannot differ by authoring format (§32).
13. **Customization is layered, not destructive.** Content packs declare namespaces, dependencies, versions, priorities, and merge intent (`add`, `merge`, `replace`, `patch`, `disable`). Conflicts are deterministic and explainable (§32–§33).
14. **Safe by construction.** Client requests are intents, commands use bounded/escaped placeholders, expressions are sandboxed and compiled, packets are size/rate limited, optional-mod code is classloader-isolated, and every state mutation is transactional (§35, §46).
15. **Vanilla-faithful is the default theme, not a prison.** The zero-asset default follows vanilla exactly; resource packs may opt into custom themes, sprites, layouts, sounds, and HUD profiles with graceful fallbacks (§44).

---

## 1. North-Star Scope and Release Boundaries

The full document specifies a progression **platform**: skills, rules, currencies/resources, linear rewards, trees, classes, abilities, carrier items, challenges, social/seasonal progression, UI/themes/HUD, authoring tools, diagnostics, and provider integrations. Specification does not imply every feature is in Core 1.0; each release below has a closed acceptance gate (§49).

**Release discipline:** the engine is designed for the entire north-star catalog, but releases are sliced vertically so the project can actually ship.

- **Core 1.0:** canonical IR, TOML packs, stable ids, transactional progression, selected core XP rules, linear effects/rewards, basic trees, class slots/capacity, fixed-slot abilities, generic carrier items, persistence/migration, sync, validation, native guide, baseline UI, commands, public events, and only compatibility bridges whose spikes/version matrix pass.
- **1.1 “Creator”:** templates/inheritance, reusable predicates and grant bundles, formulas, contextual grants, prestige, custom resources, loadouts, richer rule bindings, solo/local challenges and contracts, HUD editor, export/import, snapshots, simulation/trace, and resource-pack themes.
- **1.2 “Multiplayer”:** party/team XP, assist credit, mentor/catch-up, team/guild progression, seasonal ladders, shared/community/seasonal challenges, privacy controls, and optional shared-storage adapters.
- **2.0 “Studio”:** op-only visual Authoring Studio, live graph editing, transactional publish/rollback, datapack JSON adapter, visual bundle composition/signing, and optional remote editor protocol.

The original advanced mechanics (§28) remain fully specified and are implemented when their release train reaches them; they are not allowed to destabilize the Core 1.0 vertical slice. See §49 for acceptance gates.

---

## 2. Terminology & Data Model

| Term | Meaning |
|---|---|
| **Skill** | A named leveled track. Has XP, level, cap, curve, XP sources, per-level outputs, and point award rate. |
| **Per-level Effect/Reward** | A persistent effect active at a threshold or a transition reward fired by an explicit crossing policy. |
| **Currency** | A named checked numeric balance used for tree/class costs, mastery, reputation, prestige, or other pack economies. |
| **Skill Tree** | An optional node graph. Nodes gate on levels/named currencies/requirements and grant Outputs. Global / per-skill / per-class. |
| **Node** | A tree entry: cost, prerequisites, grants. |
| **Class** | A selectable archetype occupying pack-defined slot/capacity, with ranks/evolution, prereqs, costs, respec, grants, and synergies. |
| **Ability / Toggle** | A named passive or togglable perk. |
| **Carrier Item** | A pre-registered generic item stack whose data component selects pack-defined behavior/presentation (§9.5). |
| **Output** | A registered typed persistent effect, transition action, or numeric transaction with a supported lifecycle (§10, §35). |
| **Global Level** | A pack-defined aggregate formula/curve, not an assumed sum (§37.3). |

**Player progression state (attached to player, saved):**
```
ProgressiveSkillsData {
  int dataVersion                                  // player-state migration version
  UUID playerId
  Map<ResourceLocation, SkillState> skills         // earned/effective level, XP/bank, run+lifetime peaks, accounted XP, remainder, timestamps
  Map<ResourceLocation, Long> currencies           // points, mastery, favor, pack-defined integer currencies
  Map<ResourceLocation, ClassState> classes        // selected, class level/xp, slot, selectedAt
  Map<ResourceLocation, NodeRankState> nodeRanks   // tree/node stable id -> current rank
  Map<ResourceLocation, AbilityState> abilities    // ownership, toggle, charges, cooldowns
  Map<UUID, ActiveCastState> activeCasts            // activation tx, generation, reservations, phase, recovery policy
  Map<ResourceLocation, ResourceMeterState> meters // stamina/rage/focus/etc. (§37)
  List<ResourceLocation> abilitySlots              // fixed client action slots, not dynamic key mappings
  Map<ResourceLocation, LoadoutState> loadouts     // build/ability layouts; active loadout id
  Map<ResourceLocation, PrestigeState> prestige    // one or many named prestige tracks
  Set<DefinitionKey> discoveries                    // key=(definition kind, id); hidden content revealed
  Map<ReceiptKey, GrantReceipt> receipts            // key=(typed GrantSourceId, policy scope/target/epoch)
  Map<RewardProgressKey, LevelRewardProgress> levelRewardProgress // per grant+scope watermarks, lossless suppressed ranges, crossing epoch
  Map<UUID, OperationReceipt> operationReceipts     // death/offline/admin idempotency; mutation + receipt share attachment save
  Map<UUID, PendingClaim> pendingClaims             // durable, version-pinned value delivery/outbox records
  Map<TimedInstanceKey, TimedEntitlement> timedEntitlements // target + typed source + activation instance/stack
  Map<EntitlementKey, Map<GrantSourceId, ManualOwnership>> manualOwners // admin/API owners separate from derived owners
  Map<PurchaseInstanceId, PaidCostRecord> paidCosts // typed owner/purchase/rank + transaction; historical refund basis
  Map<ResourceLocation, RuleMemory> ruleMemory     // first-time, streak, cooldown, caps, anti-farm state
  Map<ResourceLocation, Long> activeCooldownGroups
  Map<ResourceLocation, ForeignLearnMutationRecord> foreignLearnWrites // irreversible inserted/preexisting evidence only
  Map<GrantSourceId, Set<ResourceLocation>> emittedStagesBySource
  Map<DefinitionKey, OrphanRecord> orphans          // quarantined raw state + last seen schema
  RestedXpState rested
  CharacterInitializationState characterInit       // profile + once-only creation transaction/receipt marker
  SeasonState season
  PrivacyState privacy
  DefinitionDigest stateDefinitionDigest           // digest last projected into this attachment
  long stateGeneration                             // last projected world generation; mismatch blocks routes until reconcile
  long stateRevision                               // monotonic compare-and-swap / sync revision
}
```

**Storage split:** the entity attachment is authoritative for online player progression and uses `copyOnDeath()` (or a carefully tested clone handler). Overworld `SavedData` stores only server-scoped state: definition digest/lockfile, immutable carrier `BehaviorArchive`, season metadata, leaderboard snapshots, team/guild state, audit index, and **pending offline operations**. An offline mutation is queued transactionally and applied on next login; the plan does not directly rewrite arbitrary offline player NBT. Every migration preserves an embedded pre-migration shadow until the next successful save and login, so rollback does not depend on pretending the server can snapshot every unloaded attachment at once.

**Numeric policy:** XP/currencies use signed `long` internally with checked arithmetic, explicit floors/caps, and no silent wrap. Fractional formulas use deterministic fixed-point math (default scale 1,000,000) and a stored remainder where needed; floating-point values never become persistence identity.

**Progress accounting:** `SkillState` distinguishes spendable/current XP, overflow bank, `earnedLevel`, resolved `effectiveLevel`, `runAccountedXp` (conserved progression allocation used by repeatable run/prestige gates), `runEarnedXp`, lifetime history, `runHighestLevel`, and `lifetimeHighestLevel`. Its `ProgressAllocationState` losslessly aggregates bounded run-epoch/accounting-class tranches with `(activeRaw, bankRaw, activeAccounted, bankAccounted, fixedRemainder)`. The invariant is `runAccountedXp = activeAccounted + bankAccounted`; accounted units may differ from raw XP after excluded tokens or curve migration, but can never exist in both active and bank tranches. Every incoming award assigns its accounted delta exactly once. Under overflow policy `bank`, only the **excess** raw/accounted tranche moves to the bank; every other policy declares its raw/accounted discard/conversion/provider semantics. Release moves that same bank tranche back and creates zero accounted value. Transfers/conversions debit an exact declared tranche split before crediting no more than the debited accounted amount after fees; sacrifice destroys it. Decay says whether it destroys or suppresses allocation; level tokens declare an accounted value or enter with zero. Lifetime history is never the default requirement for a repeatable prestige.

**Stable-id policy:** every persisted identity is a typed key containing full `ResourceLocation`s. `DefinitionKey(kind,id)` distinguishes kinds; `GrantSourceId(ownerKind,ownerId,grantId)` identifies a source; policy/target/epoch/activation components add multiplicity where required. Nested ids remain full (`mypack:warrior/cleave/rank_2`), never array indexes or display names. Aliases/replacements work for every kind (§33).

---

## 3. File / Config Layout

```
config/
  progressiveskills-common.toml       # safe common/bootstrap settings
  progressiveskills-client.toml       # each player's HUD/UI/accessibility preferences
  progressiveskills/
    packs/
      mypack/
        pack.toml                     # namespace, versions, dependencies, priority, policies
        replacements.toml             # typed old-id -> new-id lineage mappings
        locales/ en_us.toml es_mx.toml
        variables/ balance.toml
        predicates/ underground.toml night_fighter.toml
        requirements/ endgame.toml
        curves/ normal.toml expert.toml
        cost_bundles/ costly_respec.toml
        grant_bundles/ tank_stats.toml
        notification_profiles/ milestones.toml
        component_specs/ common_text.toml
        icon_specs/ skill_icons.toml
        item_stack_specs/ starter_kits.toml
        targeting_profiles/ short_cone.toml
        anti_exploit_profiles/ natural_resource.toml
        categories/ combat.toml gathering.toml
        profiles/ normal.toml hardcore.toml
        global_levels/ run_power.toml
        skills/ strength.toml mining.toml arcana.toml
        currencies/ global_points.toml mastery.toml
        rules/ mining/natural_ores.toml combat/assists.toml
        trees/ warrior_tree.toml arcane_tree.toml
        class_slots/ origin.toml combat.toml
        classes/ builder.toml mage.toml warrior.toml
        abilities/ builder_reach.toml arcane_surge.toml
        resources/ stamina.toml rage.toml
        prestige/ rebirth.toml
        items/ tome_of_strength.toml xp_potion.toml miners_ring.toml
        conversions/ arcana_to_mining.toml
        challenges/ first_diamond.toml daily_hunter.toml
        seasons/ season_3.toml
        stations/ training_altar.toml
        themes/ dark_rpg.toml
        layouts/ compact.toml
        tests/ mypack_cases.toml
```
For a dedicated world, global modpack packs live under `config/progressiveskills/packs/`; world files under `world/serverconfig/progressiveskills/` are **overlays**, not a second silently copied full catalog. `defaultconfigs` may seed only a small world manifest/overlay on world creation. Existing worlds never automatically inherit a newly changed seed: `/pskills pack rebase --dry-run` compares the recorded base digest to the new base, performs a three-way merge, reports conflicts/migrations/player impact, and requires an explicit publish. Client-only preferences remain local and are never forced over the network except for server disclosure/policy limits.

First launch generates config plus one enabled, dependency-free **Core starter pack**. Other small presets (hybrid RPG, ISS magic, hardcore/decay, accessibility) are exported as manifest-disabled examples and cannot enter the live registry until deliberately enabled with satisfied dependencies. Stage safe definition changes with `/pskills reload --dry-run`, inspect them, then commit with `/pskills reload --publish`; keys marked `restart_required` are validated but not hot-applied. See §32 for exact root precedence, pack manifests, JSON/datapack adapters, and merge semantics.

Every definition starts with `schema_version` and normally derives its id from `<pack namespace>:<relative path>`. Writing an explicit `id` is allowed for readability, but a mismatch with the derived id is an error. This prevents accidental rename/fork identity bugs.

---

## 4. Skill Definition Schema (`skills/<id>.toml`)

```toml
# Full dependency-free Core schema-v2 example.
schema_version = 2

[skill]
id            = "mypack:physique"
display       = { key = "skill.mypack.physique", fallback = "&cPhysique" }
description   = { key = "skill.mypack.physique.desc", fallback = "Raw physical conditioning." }
icon          = { type = "item", value = "minecraft:iron_chestplate", fallback = "minecraft:barrier", alt = "Iron chestplate" }
max_level     = 100
enabled       = true

[curve]
type        = "polynomial"     # flat | linear | polynomial | exponential | custom_table
base        = 100
coefficient = 15
power       = 2                # Core polynomial powers are non-negative integers
# custom_table has exactly `max_level - min_level` positive entries when selected

# Optional named-currency entitlement per legitimate level reached; omit for no award.
[[level_currency_awards]]
id = "mypack:physique/level_points"
amount_per_level = 1
currency  = "progressiveskills:global_points"   # any defined currency
award_basis = "lifetime_highest_level"          # current_level | run_highest_level | lifetime_highest_level
award_scope = "character"                       # character | prestige_run | season

# ---- XP SOURCES (see §5 / §5.1) ----
[[xp_sources]]
id = "mypack:physique/undead_kills"
action = "kill_entity"
match  = ["tag:minecraft:skeletons", "id:minecraft:zombie"]
amount = 10
[[xp_sources]]
id = "mypack:physique/deep_mining"
action = "mine_block"
match  = ["tag:minecraft:mineable/pickaxe"]
amount = 2
depth_scaling = { below_y = 0, multiplier = 2.0 }   # §5.1 depth
[[xp_sources]]
id        = "mypack:physique/challenge"
action    = "custom"
key       = "mypack:physique_challenge"              # stable ResourceLocation fired by command/API/KubeJS
amount    = 250
repeat_policy = "once_per_character"                # stable source id backs the rule-memory key
```

`[[xp_sources]]` is Core convenience syntax that compiles to the canonical Rule IR (§36); its `action`, simple match/filter, amount, and repeat fields are versioned aliases, not a second runtime engine.

### 4.5 Per-Level Outputs — LINEAR PROGRESSION (and coexisting with trees)

The `[[levels]]` blocks make a skill grant power automatically as it levels—no tree required. This linear path coexists freely with `[[level_currency_awards]]` plus a bound tree: the player gets guaranteed grants **and** currency for optional specialization.

```toml
# Fragment (schema v2): append to the `mypack:physique` skill above.
# Your exact example: hearts 1->5, higher jump 6->10.
[[levels]]
id = "mypack:physique/level_1"
level = 1
[[levels.effects]]
id = "mypack:physique/level_1_health"
type = "attribute"
attribute = "minecraft:generic.max_health"
operation = "add_value"
value = 2.0                       # +1 heart

[[levels]]
id = "mypack:physique/level_2"
level = 2
[[levels.effects]]
id = "mypack:physique/level_2_health"
type = "attribute"
attribute = "minecraft:generic.max_health"
operation = "add_value"
value = 2.0                       # +1 heart (cumulative +2)

[[levels]]
id = "mypack:physique/level_3"
level = 3
[[levels.effects]]
id = "mypack:physique/level_3_health"
type = "attribute"
attribute = "minecraft:generic.max_health"
operation = "add_value"
value = 2.0                       # another +1 heart (cumulative +3)

[[levels]]
id = "mypack:physique/level_4"
level = 4
[[levels.effects]]
id = "mypack:physique/level_4_health"
type = "attribute"
attribute = "minecraft:generic.max_health"
operation = "add_value"
value = 2.0

[[levels]]
id = "mypack:physique/level_5"
level = 5
[[levels.effects]]
id = "mypack:physique/level_5_health"
type = "attribute"
attribute = "minecraft:generic.max_health"
operation = "add_value"
value = 2.0                       # cumulative +5 hearts

[[levels]]
id = "mypack:physique/level_6"
level = 6
[[levels.effects]]
id = "mypack:physique/level_6_jump"
type = "attribute"
attribute = "minecraft:generic.jump_strength"
operation = "add_multiplied_base"
value = 0.15                      # higher jump

[[levels]]
id = "mypack:physique/level_10"
level = 10
milestone = true                  # §12 milestone: fanfare + optional big grant
[[levels.effects]]
id = "mypack:physique/level_10_luck"
type = "attribute"
attribute = "minecraft:generic.luck"
operation = "add_value"
value = 1.0                       # dependency-free Core milestone effect
[[levels.rewards]]
id = "mypack:physique/level_10_title"
type = "title"
title = { key = "milestone.mypack.peak_physique", fallback = "PEAK PHYSIQUE" }
lifecycle = "on_first_reach"
repeat_policy = "once_per_character"
[[levels.rewards]]
id = "mypack:physique/level_10_sound"
type = "sound"
sound = "minecraft:ui.toast.challenge_complete"
lifecycle = "on_first_reach"
repeat_policy = "once_per_character"
```

**Shorthand for uniform curves** (avoids writing 100 `[[levels]]` blocks) — attach a per-level attribute in `[skill]`-adjacent form:
```toml
# Fragment (schema v2): append to the same skill definition.
[[scaling]]
id = "mypack:physique/health_scaling"
type = "attribute"
attribute = "minecraft:generic.max_health"
operation = "add_value"
per_level = 0.4                   # +0.4 health every level, applied continuously
from_level = 1
to_level   = 20                   # optional cap
```
`[[levels]]` = discrete steps at specific levels; `[[scaling]]` = smooth per-level growth. A skill can use either, both, or neither, alongside a tree. **One engine.**

**Canonical match prefixes:** `id:`, `mod:`, `tag:`, `translation_key:`, and opt-in `custom_name:` (bare = `id:`); `school:` exists only for spell-aware matchers. `custom_name:` is player-controlled/localization-sensitive and produces a validation warning on value-bearing rules. One typed resolver is shared everywhere.

---

## 5. Built-in XP Action Bindings

Each shipped binding is a route in a startup-registered stable listener (or a version-pinned provider), not a config-time subscription. Disabled bindings compile to empty routes. Every binding documents attempt/success/extraction semantics and supports the canonical matcher, amount/formula, cooldown, and shared predicates.

| `action` | Fires on | `match` targets |
|---|---|---|
| `mine_block` | block broken w/ correct tool | block id/tag |
| `break_block` | any block broken | block id/tag |
| `place_block` | block placed | block id/tag |
| `kill_entity` | entity death credited to player | entity id/tag |
| `deal_damage` | successful final post-mitigation damage, credited once | entity id/tag |
| `take_damage` | damage taken | damage type/source |
| `cast_spell` | confirmed successful cast from the pinned spell-provider adapter | spell id / school |
| `craft_item` | actual player result extraction, with recipe/result fingerprint | result id/tag |
| `smelt_item` | actual player result extraction, not recipe completion/automation | result id/tag |
| `consume_item` | food/potion consumed | item id/tag |
| `breed_animal` | breeding success | entity id/tag |
| `tame_animal` | taming success | entity id/tag |
| `travel_distance` | per N blocks moved (walk/swim/fly/elytra) | movement mode |
| `enchant_item` | enchant applied | enchant id |
| `advancement` | advancement earned | advancement id |
| `custom` | KubeJS / command / Java API only | freeform `key` |

### 5.1 Advanced XP Modifiers (all opt-in per source)

- **First-time bonus** — `repeat_policy = "once_per_character"` grants once ever, keyed by the stable source id in `RuleMemory` plus a non-evictable claim tombstone. "First diamond mined → +500." Great for guided progression.
- **Depth / altitude scaling** — `depth_scaling = { below_y = 0, multiplier = 2.0 }` (or `above_y`). Deep mining pays more.
- **Biome / dimension / structure scoping** — `dimension = "minecraft:the_nether"`, `biome = "tag:minecraft:is_forest"`, `structure = "minecraft:ancient_city"`. XP only counts (or is multiplied) there.
- **Streak / combo** — `streak = { window_seconds = 5, per_stack = 0.1, max_stacks = 10 }`. Consecutive qualifying actions ramp a multiplier, capped. Configurable, off by default.
- **Tool / enchant gating** — `requires_enchant = "minecraft:silk_touch"` or `requires_tool = "tag:..."`. Only Silk-Touch mining feeds "Geology," etc.
- **Rested XP** — engine-wide `[xp].rested` config: XP accrues into a rested pool while logged off; on return, gains are boosted until the pool drains. WoW-style. Toggle + rate + cap.

---

## 6. Attribute Mapping Layer

1. **Attribute modifiers.** Stable modifier id derives from owner + the grant's required authoring `id` (called `grantId` in IR), then the desired projection is diffed (§22). Covers any attribute present on the player, including vanilla and compatible modded registries; a registered id alone does not guarantee an entity has an `AttributeInstance`.
2. **ProgressiveStages emission.** Use a version-pinned adapter and source-aware ownership if the API supports it. If external ownership cannot be distinguished, a pack chooses `managed`, `sticky`, or `grant_only`; unsafe revoke is never assumed. `while_eligible` is legal only for a capability proven to support source-aware revoke. A sticky/`grant_only` stage instead compiles to an explicitly irreversible `on_gain` transition with a permanent receipt, `allow_irreversible_on_respec = true`, validation/UI warnings, and no automatic refund of the source by default.

Optional **soft caps / diminishing returns** per attribute in `[attributes]` config so growth doesn't run unbounded (ties to safety clamps).

---

## 7. Skill Tree Schema (`trees/<id>.toml`)

Trees are **optional**—a skill with no bound tree and no `[[level_currency_awards]]` is pure linear (§4.5). Trees exist when the dev wants player choice.

```toml
# Full dependency-free Core schema-v2 example.
schema_version = 2

[tree]
id           = "mypack:warrior_tree"
display      = { key = "tree.mypack.warrior", fallback = "&cPath of the Warrior" }
description  = { key = "tree.mypack.warrior.desc", fallback = "A martial specialization tree." }
scope        = "class"            # global | skill | class
bind         = "mypack:warrior"   # skill id (scope=skill) or class id (scope=class)
currency     = "progressiveskills:global_points"

[[nodes]]
id          = "mypack:warrior_tree/cleave"
display     = { key = "node.mypack.cleave", fallback = "Cleave" }
icon        = { type = "item", value = "minecraft:iron_axe", fallback = "minecraft:barrier", alt = "Iron axe" }
cost        = 3
row = 0
col = 1
requires    = []                  # AND
requires_any= []                  # OR
min_level   = { "mypack:physique" = 10 }  # shorthand compiled to the shared requirement AST
[[nodes.grants]]
id = "mypack:warrior_tree/cleave/damage"
type = "attribute"
attribute = "minecraft:generic.attack_damage"
operation = "add_multiplied_base"
value = 0.10
[[nodes.grants]]
id = "mypack:warrior_tree/cleave/whirlwind"
type = "ability"
ability = "mypack:whirlwind"

[[nodes]]
id = "mypack:warrior_tree/berserker"
cost = 5
requires = ["mypack:warrior_tree/cleave"]
[[nodes.grants]]
id = "mypack:warrior_tree/berserker/access"
type = "class_access"
class = "mypack:berserker"
```

Rendered as a scrollable node-graph GUI (grid + `requires` edges). Node grants use the exact Output resolver as everything else.

---

## 8. Class Schema (`classes/<id>.toml`) — slots, capacity, coexistence and legacy global cap

```toml
# Full schema-v2 example from the separate ISS fixture; its manifest declares an exact tested ISS requirement.
schema_version = 2

[class]
id            = "mypack:mage"
display       = { key = "class.mypack.mage", fallback = "&9Mage" }
description   = { key = "class.mypack.mage.desc", fallback = "Masters of the arcane." }
icon          = { type = "item", value = "minecraft:enchanted_book", fallback = "minecraft:barrier", alt = "Enchanted book" }
enabled       = true

slot               = "mypack:combat"  # pack-defined slot category
slot_cost          = 1                 # supports weighted capacity
exclusive_tags     = []                # explicit coexistence rules; no ambiguous "stackable" boolean
prerequisites      = { min_level = { "mypack:arcana" = 15 }, nodes = [], classes = [] }
selection_cost     = { currency = "progressiveskills:global_points", amount = 5 }
respec_allowed     = true
respec_cost        = { currency = "progressiveskills:global_points", amount = 2 }
starter_kit        = ["minecraft:book", "irons_spellbooks:wooden_spell_book"]   # sugar for receipt-protected on_first_select rewards

# Standard grants
[[grants]]
id = "mypack:mage/arcane_tree_access"
type = "tree_access"
tree = "mypack:arcane_tree"
[[grants]]
id = "mypack:mage/scale"
type = "attribute"
attribute = "minecraft:generic.scale"
operation = "add_value"
value = -0.1                      # slightly smaller, if the pack wants flavor

# ---- Iron's Spells features (§8.1) ----
[[grants]]
id = "mypack:mage/fireball"
type = "spell"
spell = "irons_spellbooks:fireball"   # source-granted while this class remains owned
level = 3
selection = "virtual_source"
learning = "satisfy_while_owned"      # persistent grant: require_existing | satisfy_while_owned
[[grants]]
id = "mypack:mage/mana"
type = "mana"
max_mana   = 50                   # +50 max mana
mana_regen = 0.2                  # +regen
[[grants]]
id = "mypack:mage/fire_mastery"
type = "attribute"
attribute = "irons_spellbooks:fire_spell_power"   # school mastery: fire power up
operation = "add_multiplied_base"
value = 0.25
[[grants]]
id = "mypack:mage/arcane_surge"
type = "ability"
ability = "mypack:arcane_surge"   # class-exclusive ACTIVE ability on cooldown (§9)
```

**Capacity model:** Core may ship a simple `combat` slot with capacity 2 for the starter example, but the normative model is pack-defined class slots/capacity (§40). Version-1 `counts_toward_cap`/`max_classes` are migration-only sugar that compile into one `progressiveskills:legacy_default` slot; a pack that declares explicit slots may not also use either legacy field. Mixed models are a staging error, preventing double enforcement. Over-cap selection is refused with a localized explanation. Named synergy definitions let compatible classes unlock bonus grants.

**Starter-kit identity:** the inline `starter_kit` list is explicit compiler sugar for one transition action with stable id `<class-id>/starter_kit`, lifecycle `on_gain`, `repeat_policy = once_per_character`, and a materialized `ItemStackSpec` bundle. Reordering the item list never changes the receipt; per-item partial delivery is tracked inside the same pending claim. Authors who need independent repeat/migration behavior use explicit `transition_rewards` with per-action ids.

**Irreversible spell policy:** `permanently_learn` is a transition action, never a persistent class grant. It requires a stable action id, `on_gain` trigger, permanent receipt, explicit irreversible confirmation in docs/UI, and is rejected on a freely respeccable owner unless `allow_irreversible_on_respec = true`. Reversible classes should use `require_existing` or capability-tested `satisfy_while_owned`.

### 8.1 Iron's Spells class features (version-pinned optional compat)
- **`spell` grant** — provides a source-owned virtual spell selection and a declared learning policy. Default is reversible while the class/node remains active; it never blindly removes an independently learned spell on respec.
- **`mana` grant** — `max_mana` + `mana_regen` additions (mapped to ISS mana attributes).
- **School mastery** — any attribute id proved by the pinned ISS build (`fire_spell_power`, `cooldown_reduction`, `spell_resist`, …) as an `attribute` grant; validation uses the exact target registry, never a guessed spelling.
- **Class-exclusive active ability** — an active ability assignable to the fixed action bar/wheel, with validated costs/cooldown/target/actions (§9, §41).

### 8.2 Class synergy (optional)
```toml
# Fragment (schema v2): append at pack/class-definition scope.
[[synergy]]
id = "mypack:spellblade"
requires_classes = ["mypack:mage", "mypack:warrior"]
[[synergy.grants]]
id = "mypack:spellblade/stance"
type = "ability"
ability = "mypack:spellblade_stance"
```
Applied only while the player holds ALL listed classes; removed cleanly if either is respecced.


### 8.3 Iron's Spells 'n Spellbooks — Integration Contract (verified against 1.21.1 API)

*This contract is pinned and smoke-tested against the exact ISS build shipped by the target pack, with a tested version range recorded in the compatibility matrix. Current 1.21 source changes are not assumed to match older 1.21.1 releases. Optional classes remain isolated so ProgressiveSkills loads without ISS.*

**Dependency setup (soft):** ISS publishes a stable API jar. In `build.gradle`:
```gradle
repositories {
  maven { name = "Iron's Maven - Release"; url = "https://code.redspace.io/releases" }
}
dependencies {
  compileOnly "io.redspace:irons_spellbooks:${irons_spells_version}:api"   // stable API only
  // runtime provided by the pack; we never hard-depend
}
```
Compile against `io.redspace.ironsspellbooks.api.**` (stable). Anything outside `api.` risks breaking between versions — avoid unless necessary.

**Key facts that shape the design (current 1.21 branch):**
- Spells extend `AbstractSpell`, registered via `DeferredRegister<AbstractSpell>` against `SpellRegistry.SPELL_REGISTRY_KEY`. Every spell has a `ResourceLocation` id and a `SchoolType` (nine schools).
- ISS now has persistent `LearnedSpellData` **and** source aggregation through `SpellSelectionManager`. Some spells may require learned status, so selection injection alone is not proof that casting will succeed. Treat “visible/selectable” and “satisfies learning requirement” as two separate compat capabilities.
- Attributes are ordinary registered attributes in `AttributeRegistry`: `max_mana`, `cooldown_reduction`, `cast_time_reduction`, `spell_power`, `spell_resist`, plus per-school power via `SchoolType.getPowerFor(entity)`. Drive-able with normal vanilla `AttributeModifier`s.
- Cast events: `SpellPreCastEvent` and `SpellOnCastEvent` (split from the old `SpellCastEvent`). `SpellSelectionManager.SpellSelectionEvent` lets modders **add custom spell sources to a player**.

**Feature → implementation mapping:**

| PS feature | ISS mechanism | How we implement it |
|---|---|---|
| **`mana` grant** (`max_mana`, `mana_regen`) | `AttributeRegistry.MAX_MANA`, mana-regen attribute | Pure `attribute` output pointing at the ISS attribute id. **No new machinery** — reuses ModifierManager. `mana` in the schema is just sugar that expands to attribute modifiers. |
| **School mastery** (fire power up, cooldown down) | `spell_power`, per-school power, `cooldown_reduction`, `cast_time_reduction`, `spell_resist` | Use the generic attribute path only when the exact pinned registry id resolves and the player has an `AttributeInstance`; otherwise follow the declared missing-target policy. |
| **`spell` selection grant** | `SpellSelectionManager.SpellSelectionEvent` | If the player has a source-owned PS spell entitlement, inject a virtual spell source at the resolved level. Injection must occur consistently on logical client and server and be refreshed after entitlement sync. |
| **Learned-spell requirement** | `LearnedSpellData` + the pinned spell pre-cast requirement path | Policy is explicit: `require_existing` (selection is present but normal learning is still required), `satisfy_while_owned` (preferred, reversible source-aware capability/hook), or `permanently_learn` (opt-in irreversible transition). Never remove from ISS learned data unless PS can prove it originally inserted the entry and no independent learning occurred. If the pinned API cannot safely supply `satisfy_while_owned`, validation degrades/refuses that policy instead of mutating foreign state. |
| **`cast_spell` XP source** | `SpellOnCastEvent` | Through the pinned isolated adapter, read spell id/school/level only after the event represents a successful cast, then route a typed trigger into the rule engine. |
| **Class-exclusive active ability that casts a spell** | Pinned public cast entry/capability | The activation transaction validates target, cast state, selection/learning policy, mana cost, ISS cooldown, PS cooldown group, cast/channel time, cancellation/interruption, and side. Default respects both systems. |

**New `school:` prefix** for `match` lists in `cast_spell` sources and anywhere spells are referenced, e.g. `match = ["school:irons_spellbooks:fire", "id:irons_spellbooks:fireball"]`. Resolves via `SchoolRegistry`.

**Compat discipline:** a common-code factory checks mod id + version before classloading an isolated `IronsSpellsCompat` implementation. Use direct compile-only calls to pinned public APIs where stable; reserve reflection/mixins for gaps, capability-test them, and trip a circuit breaker after repeated failures. Absent/unsupported ISS follows the definition/pack's declared `missing_policy` (`reject_pack`, `disable_def`, `skip_declared_branch`, `hide`, or `warn`).

**`/pskills diagnose ironsspells`** dumps: loaded/version/supported range; capabilities for cast event, selection injection, learned requirement, attributes, active cast; circuit-breaker status; and referencing defs. Required tests cover learned-required spells, independently learned overlap, client/server selection, instant/channelled spells, mana, both cooldown systems, invalid targets, respec, and relog.


---

## 9. Ability / Toggle Schema (`abilities/<id>.toml`)

```toml
# Full schema-v2 example from the manifest-gated ISS fixture, not the dependency-free Core starter.
schema_version = 2

[ability]
id           = "mypack:arcane_surge"
display      = { key = "ability.mypack.arcane_surge", fallback = "&dArcane Surge" }
kind         = "active"           # passive | toggle | active | proc | aura | channel | stance | combo
slot_allowed = true               # may be placed in a fixed ability slot/action wheel
cooldown_ticks = 600               # active only

[targeting]
mode = "self"

# active: what firing it does
[[actions]]
id = "mypack:arcane_surge/cast"
type = "cast_spell"               # version-pinned ISS adapter
spell = "irons_spellbooks:blessing_of_life"
[[actions]]
id = "mypack:arcane_surge/particles"
type = "particle"
particle = "minecraft:enchant"
offset = [1.0, 1.0, 1.0]
speed = 0.1
count = 40
```

`kind`, `persistent_effects`, `actions`, `costs`, `targeting`, and `triggers` are the canonical v2 ability dialect (§41). Only passive/toggle/aura/stance kinds may declare `persistent_effects`; only toggle/stance may declare `default_on`; active/proc/channel/combo kinds execute transaction actions. Version-1 `type`/`effects`/`on_activate` fields are migration aliases only and are not used in new examples. Runtime definitions map to a fixed client-registered bar/wheel.

---

### 9.5 Config-Defined Items — Frozen-Registry-Safe Carrier Model

Minecraft items are registry objects and cannot be invented by a TOML reload after registration has completed. Therefore `items/<id>.toml` defines **behavioral item stacks**, not new `Item` registry ids.

The jar registers a small, stable carrier set at startup:

- `progressiveskills:tome`, `token`, `charm`, `consumable`, and `artifact` (exact final list minimized before 1.0).
- `progressiveskills:definition_id` persistent/network-synced data component identifies the configured behavior.
- Optional components hold tier, charges, bound owner, creation pack digest, **behavior version/materialized reward digest**, expiration, and authenticity/version.
- Display name, lore, rarity, glint, stack size policy, cooldown, use animation, sound, and rendered icon are resolved from the synced definition.
- A resource pack can supply custom-model-data/model selectors; without it, the carrier renders a configured item/texture icon over a vanilla-safe fallback.
- JEI/EMI subtype identity includes `definition_id`, so two configured tomes are not collapsed into one ingredient.

```toml
schema_version = 2

[item]
id = "mypack:tome_of_might"
carrier = "progressiveskills:tome"
behavior_version = 1
migration_policy = "keep_pinned"
display = { key = "item.mypack.tome_of_might", fallback = "Tome of Might" }
icon = { type = "item", value = "minecraft:enchanted_book", fallback = "minecraft:barrier", alt = "Enchanted book" }
rarity = "rare"
glint = true
stack_size = 16
bind = "none"                 # none | on_pickup | on_use | on_craft
charges = 1

[[use_actions]]
id = "mypack:tome_of_might/grant_xp"
type = "xp"
skill = "mypack:physique"
amount = 500
consume = 1
```

Recipes, loot injection, quest rewards, trades, and commands create a carrier `ItemStack` with the component set. Datapack recipe/loot helpers and generated examples are supplied. A config-defined “altar” likewise uses a pre-registered generic station block or an integration with an existing block; config reload never registers a new block.

**Tamper/reload policy:** presentation may resolve through the current definition, but economic behavior defaults to an immutable canonical `BehaviorSnapshot` pinned when the stack/claim was created. The default implementation stores that bounded action spec in a world `BehaviorArchive` keyed by cryptographic behavior digest; the stack carries definition id + digest, and a pending claim additionally materializes the snapshot in its durable record. A reload cannot silently make an existing tome more valuable.

The archive has checksums, backups, schema migration, size/version quotas, and no age-based garbage collection: unknown stacks may exist in unloaded/modded containers. At its ceiling, creating a new behavior version fails closed until an operator previews an explicit world scan/migrate/invalidate/archive operation; an archive entry is removed only when absence of references is proven or all old digests are deliberately invalidated. A bounded signed embedded snapshot may be an opt-in portability mode, but client-provided actions/amounts are never trusted. Adopting new behavior requires an explicit migration with preview; impact reports count known claims/loaded inventories and label unindexed storage unknown. Unknown ids/digests are inert and diagnosable. Policies are `keep_pinned` (default), `migrate`, `warn`, `invalidate`, or unsafe `accept_live`.

**Authenticity boundary.** On every economic use, the server resolves the behavior digest from its archive and validates all bounded component fields; component-supplied action specs, amounts, owners, and signatures are never authority. High-value/unique carriers may opt into a world-issued nonce plus HMAC over canonical identity/behavior/owner fields. The exact issuance/spend ledger is quota-reserved and fail-closed, making a copied nonce usable at most according to its recorded charge policy; ordinary stackable carriers rely on trusted acquisition channels and do not pretend to be uncopyable. Use while the player is in creative is denied/audited by default, but the server generally cannot identify an ordinary valid stack that was copied or edited earlier; only issuance-ledger/nonce policy can detect an unknown or reused unique instance. HMAC cannot defeat ops, world editors, server plugins, or copying of a still-valid ordinary stack.

World secrets live in backed-up server-only storage with key ids, never in client projection/export/logs. Rotation retains a bounded verification key ring, issues only under the newest key, and offers dry-run re-sign/invalidate migration; losing all applicable keys makes signed carriers safely inert and recoverable only through an audited operator migration. Restoring/moving a world must include the archive, nonce ledger, and key store as one backup unit.

---

## 10. Output Vocabulary (shared core — used EVERYWHERE)

One typed output compiler accepts the unified vocabulary from **per-level grants, `[[scaling]]`, tree nodes, classes, class synergy, abilities, rules, challenges, conversions, and carrier items**. Runtime application is split by lifecycle (§35); “one vocabulary” does **not** mean “re-fire everything during recompute.”

| Output `type` | Effect |
|---|---|
| `attribute` | register/refresh AttributeModifier (deterministic id) |
| `stage` | source-tracked grant/revoke through a version-pinned ProgressiveStages capability/fallback policy |
| `ability` | add/remove a source-owned ability entitlement; project or slot it by kind |
| `spell` | source-owned ISS virtual selection + declared learning policy (version-pinned compat) |
| `mana` | ISS max-mana + regen additions |
| `command` | run server command, templated (`{player}` …) — gated by `allow_command_outputs` |
| `kubejs` | fire a KubeJS event hook by `key` |
| `currency` | checked add/remove/transfer against a named currency (`skill_point` is v1 migration sugar only) |
| `item` | give item(s) (starter kits, rewards) |
| `flag` | registered source-owned boolean entitlement/policy only (for example PS flight/no-fall); numeric and status mechanics use typed attributes/effects/providers |
| `tree_access` / `class_access` | unlock GUI availability |

Expanded native typed outputs (phased by §49) include `xp`, `level`, `currency`, `resource`, `effect`, `damage`, `heal`, `exhaustion`, `teleport`, `velocity`, `cooldown`, `charges`, `message`, `toast`, `title`, `sound`, `particle`, `recipe`, `advancement`, `scoreboard`, `loot`, `function`, `choice`, and weighted `random`. Native outputs are preferred over `command` because they can be validated, previewed, permissioned, audited, and assigned an explicit rollback/delivery class. Only reversible types support undo; damage/heal/teleport/world actions and audiovisual effects are normally nonrollbackable or compensating-only. `command`/KubeJS remain escape hatches.

Every output has a stable `id`, `lifecycle`, optional `conditions`, optional `formula`, `missing_target_policy`, and a declared rollback/receipt policy. The schema registry publishes an explicit compatibility table showing which lifecycles each output supports; invalid combinations fail validation.

Full recompute applies **only persistent/ref-counted outputs**. Edge and transactional outputs are handled by `ProgressionTransaction` with receipts, idempotency keys, and audit records (§35). This preserves the “one contract, many categories” goal without item/command duplication.

---

## 11. Engine Settings — Common, Server/World, and Client Authority

Settings are split deliberately:

- **Common/bootstrap:** protocol toggles, optional integrations, hard safety ceilings, and features whose registration shape is fixed at startup.
- **Server/world:** gameplay, balance, permissions, disclosure, pack roots, anti-exploit, and default presentation policy. The server sends only the client projection it needs.
- **Client:** HUD position/layout, scale, theme preference, notification density, accessibility, and key mappings. Accessibility controls cannot be server-locked.
- **Definition packs:** almost all actual skills/classes/rules/content. These use the staged definition loader, not `ModConfigSpec` as a fake dynamic registry.

Every setting is annotated `hot_reloadable`, `next_login`, `next_world_load`, or `restart_required`; `/pskills reload --dry-run` reports requested changes that cannot be applied live.

- `[general]` — `debug_logging`, starting profile/entitlements, respec policy, command-output safety, disclosure policy, active global-level definition, death-loss profile, and pack roots. Point pools are ordinary named currencies (§37), not a special `global|per_skill` mode.
- `[classes]` — default class-slot preset/capacity, `allow_class_swap`, default respec cost, synergy enable, starter-kit-once enforcement. Version-1 `max_classes` is accepted only when no explicit class slots exist.
- `[xp]` — global multiplier, per-action master toggles, anti-farm cooldowns, creative-bypass; **`[xp.rested]`** (enable, accrual rate, cap, boost multiplier); **`[xp.streak]`** global defaults.
- `[attributes]` — master enable, per-attribute clamp ranges (`allow_uncapped` override), diminishing-returns curves.
- `[items]` — carrier master policy, behavior-archive ceiling, delivery/default binding, XP-consumable multiplier/stack group, and provider equipment-slot allowlists.
- `[gui.defaults]` (server) — recommended screen/layout/theme/HUD defaults and information-disclosure limits; `[gui]` (client) — personal HUD/widget positions, notification toggles, scale, reduced motion, theme preference, and fixed action-slot key mappings (§44).
- `[messages]` — optional `ComponentSpec` overrides for built-in keys, with typed placeholders (`{skill}`, `{level}`, `{class}`, `{currency}`, `{node}`, `{player}`, `{xp}`, `{next}`, `{cap}`, `{spell}`, `{mana}`). Per-locale pack files are canonical; `&` formatting is fallback shorthand, not the localization system.
- `[integration.*]` — enable flags: `ironsspells`, `progressivestages`, `kubejs`, `curios`, `ftbquests`, `emi`, `jei`, `jade`, `patchouli`, `apotheosis`.
- `[patchouli]` — optional renderer/export settings (book id, categories, static-resource output, required reload behavior). The native guide is always available and does not depend on generated client resources.
- `[performance]` — recompute batching, condition evaluation buckets, rule budgets, sync throttle, payload chunk size, cache toggles, audit retention, and safety ceilings. Hard packet/data caps are not removable by untrusted client config.

---

## 12. Progression Feedback & Milestones

- **Level-up feedback** — configurable toast + sound + optional particle burst + optional `command` output (fireworks, titles). All in `[gui]` / per-`[[levels]]`.
- **Milestones** — `[[levels]]` with `milestone = true` (or every-Nth via config) triggers bigger fanfare + intended for large grants/stages. Feels great, cheap to build.
- **XP floaties** — small "+5 Mining" near crosshair, toggleable.
- **Global level** — active named aggregate definition (run-accounted XP curve by shipped default, or pack formula); usable as a gate and shown in UI/HUD (§37.3).
- **Cross-skill requirements** — v2 shorthand uses full ids, e.g. `min_level = { "mypack:arcana" = 20, "mypack:physique" = 10 }`, and compiles to the shared requirement AST.
- **`/pskills top`** — simple per-skill server leaderboard.
- **Death penalty** — optional configurable XP loss on death.

---

## 13. Commands (`/pskills`)

Command availability is release-tagged: **[Core]** 1.0, **[Creator]** 1.1, **[Multiplayer]** 1.2, **[Studio]** 2.0. A generated `/pskills help` hides commands not present in the running build/capability set.

| Command | Perm | Description |
|---|---|---|
| `/pskills skill get\|set <player> <skill> [level]` | OP | Read/set level |
| `/pskills xp <player> <skill> <amount>` | OP | Award/remove XP (command API) |
| `/pskills currency get\|add\|set <player> <currency> [amount]` | OP | Inspect/mutate a named currency (`/pskills points` is a deprecated v1 alias) |
| `/pskills convert skill <player> <from> <to> <amount>` | OP/self-policy | **[Creator]** Preview/execute a defined conversion edge; never accepts a client-calculated rate |
| `/pskills sacrifice <player> <conversion> <amount>` | OP/self-policy | **[Creator]** Execute a defined sacrifice/sink transaction with explicit confirmation |
| `/pskills transfer request\|accept\|deny <player> <currency> <amount>` | any/policy | **[Multiplayer]** Consent-based player transfer with tax/caps/cooldown |
| `/pskills class add\|remove\|list <player> [class]` | OP | Manage classes (respects cap) |
| `/pskills node unlock\|lock <player> <tree> <node>` | OP | Force node state |
| `/pskills ability toggle\|grant <player> <ability>` | OP | Manage abilities |
| `/pskills spell grant\|revoke <player> <spell>` | OP | Manage PS-owned virtual spell entitlements/policies |
| `/pskills give <player> <item_def> [count]` | OP | Spawn a validated carrier/item-behavior definition with a pinned behavior digest |
| `/pskills respec <player> [tree\|class\|all]` | OP | Refund & reset |
| `/pskills info <definition_kind> <id> [--provenance]` | OP | Dump any schema-registered definition kind; suggestions are generated from the registry |
| `/pskills tree` | OP | Print prereq/tree graph |
| `/pskills top <skill>` | any | Leaderboard |
| `/pskills validate` | OP | Validate every TOML |
| `/pskills reload --dry-run` | OP | Parse, stage, validate, diff, and report only; bare `/pskills reload` aliases this safe mode |
| `/pskills diagnose <integration_id\|all>` | OP | Generated capability/version/classloading status for built-in and provider-registered integrations |
| `/pskills explain <player> <lock\|stat\|xp\|output> <id>` | self/OP | **[Core]** Human-readable “why?” trace; secret logic uses redacted server explanations |
| `/pskills trace <player> <xp\|rules\|conditions\|outputs> [seconds]` | OP | **[Creator]** Bounded live trace with source ids |
| `/pskills simulate <profile|player> <action> ...` | OP | **[Creator]** Dry-run transaction; no state mutation |
| `/pskills reload --publish` | OP | Publish the already validated staged snapshot through the generation barrier |
| `/pskills diff <live\|draft\|digest>` | OP | Definition/config semantic diff |
| `/pskills snapshot definitions create\|list\|restore` | OP | **[Creator]** Definition/lockfile snapshots; no player value rollback |
| `/pskills snapshot players-online create\|restore` | OP | **[Creator]** Freeze-required online attachment snapshot; preserves monotonic claim tombstones |
| `/pskills backup maintenance status\|verify` | OP | **[Creator]** Coordinate/verify an operator-owned full world backup; PS does not fake one |
| `/pskills audit [player|transaction]` | OP | Mutation audit history |
| `/pskills undo <transaction_id>` | OP | Undo a reversible admin transaction |
| `/pskills freeze [on|off]` | OP | Pause progression mutations for maintenance |
| `/pskills reconcile <player|all> [--dry-run]` | OP | Rebuild derived state and repair drift/orphans |
| `/pskills export <pack|schema|docs|diagnostics>` | OP | **[Creator]** Export portable, sanitized artifacts |
| `/pskills import <bundle> --preview\|overlay\|replace\|remap <namespace>` | OP | **[Creator]** Sanitize and stage a `.pspack`; never publishes implicitly |
| `/pskills pack rebase --dry-run\|--publish` | OP | **[Creator]** Three-way rebase a world overlay against a changed base pack |
| `/pskills studio` | OP | **[Studio]** Open the staged Authoring Studio (§44.8) |

Every mutating command accepts a structured `--reason`, supports `--silent` where safe, emits a transaction id, and can target selectors when the operation is atomic across all selected players. Destructive bulk operations require dry-run/confirmation. Console/RCON gets stable machine-readable JSON output; players get localized Components. Every output line is generated from schema/diagnostic metadata rather than an ever-drifting hand list.

---

## 14. KubeJS / Java API + Roadmap

**KubeJS bindings** (version-pinned optional adapter):
```js
ProgressiveSkillsEvents.custom(e => { /* on 'custom' xp keys */ })
ProgressiveSkillsEvents.levelUp(e => { /* e.skill, e.level, e.player */ })
player.progressiveskills.addXp('mypack:mining', 500)
player.progressiveskills.getLevel('mypack:mining')
player.progressiveskills.addClass('mypack:mage')
player.progressiveskills.grantSpell('irons_spellbooks:fireball')
player.progressiveskills.hasNode('mypack:arcane_tree', 'mypack:arcane_tree/fireball_mastery')
```
**Java event bus:** explicit families such as cancellable/modifiable `SkillXpPre`, `ClassChangePre`, `NodePurchasePre`, `AbilityActivatePre` and immutable `SkillXpPost`, `SkillLevelChangePost`, `ClassChangePost`, `NodePurchasePost`, `AbilityStatePost`, `SpellEntitlementChangePost`. Every post event carries transaction/generation/provenance and never implies a permanent foreign spell learn.
**FTB Quests — both directions:** through a version-pinned optional adapter, quests award XP/currency/classes as a reward type and may require a skill/class/node.

**Roadmap:** party/proximity shared XP, team/guild progression, seasons, JSON/datapack authoring, the visual Studio, external editor protocol, and storage-provider adapters are staged in §49. **Server→client definition projection is not deferred:** it is required in Core 1.0 because a dedicated server's TOML drives the client's GUI, native guide, icons, formulas, and localized fallback text. Only sanitized presentation/requirement data is synced; server-only commands, secrets, anti-exploit rules, and hidden conditions remain server-side.

---

## 15. Architecture (PS-style package map)

```
common/
  config/PsConfig                  — authority/reload metadata + cached typed getters
  schema/SchemaRegistry            — one metadata source for codecs, validation, docs, forms, suggestions
  ir/*                             — immutable canonical definitions compiled from every authoring adapter
  id/{StableId,AliasMap,Provenance} — stable identity, replacements, resolved source spans
  pack/{PackManifest,PackResolver,MergeEngine,ContentDigest}
  expression/{ExprParser,TypedAst,CompiledFormula,FixedPoint}
  predicate/{PredicateDef,PredicateCompiler,ConditionDependencyIndex}
  data/ProgressiveSkillsData       — per-player attachment (state, RuleMemory, receipts, claims, timed owners)
  data/PsDataAttachment            — AttachmentType reg + save/load + version migration
  def/PrefixEntry                  — typed id:/mod:/tag:/translation_key:/custom_name: resolver
  def/{SkillDef,TreeDef,ClassDef,AbilityDef,ItemDef,NodeDef,Output,LevelGrant,ScalingGrant,Synergy}
  def/CurveFn                      — flat|linear|poly|exp|table
  registry/DefinitionRegistry      — parsed defs + validation + /pskills info/validate backing
  output/{OutputCompiler,PersistentOutputProjector,TransitionOutputExecutor}
  transaction/{ProgressionTransaction,TransactionPlan,ReceiptLedger,AuditRecord}
  attribute/ModifierManager        — deterministic-id apply/refresh/remove + clamps/diminishing
  network/NetworkHandler           — SkillSync, ClassSync, TreeStateSync, AbilitySync, HudSync, DefsSync
  event/*Event                     — public Java event bus
  util/{TextUtil,InfoDisclosure}

server/
  xp/XpSourceEngine + xp/bindings/*Binding   — config-gated action bindings (§5)
  xp/modifiers/{FirstTime,DepthScale,ScopeFilter,Streak,Rested,ToolGate}   — §5.1
  progression/ProgressionManager   — level-up, per-level outputs, point award, de-level revoke, milestones
  progression/{CurrencyManager,ResourceMeterManager,PrestigeManager,LoadoutManager}
  rule/{RuleEngine,TriggerRegistry,MatcherRegistry,AntiExploitPolicy}
  condition/ContextWatcher         — event-indexed + bucketed reevaluation; no blanket every-tick scans
  class/ClassManager               — slots/capacity, ranks/evolution, prereqs, respec, synergy, kit receipts
  tree/TreeManager                 — node validation, point spend, grants
  ability/AbilityManager + FlagDispatcher + ActiveAbilityHandler   — toggles, flags, cooldown actives
  item/{CarrierItemManager,DefinitionComponent,ItemStackFactory}
  loader/{DefinitionLoader,StagingRegistry,LastKnownGoodStore,ImpactAnalyzer}
  offline/PendingOperationStore    — SavedData queue applied on login; never unsafe offline NBT mutation
  season/{SeasonManager,ChallengeManager,LeaderboardService}
  social/{PartyAdapter,TeamProgressionManager,MentorManager}
  audit/{AuditService,SnapshotService,UndoService}
  command/PsCommand                — §13
  leaderboard/TopService           — /pskills top

client/
  gui/PsScreenHost                 — tabbed shell (§17)
  gui/{SkillsScreen,TreeScreen,ClassScreen}
  hud/SkillHudOverlay              — XP bar, level toast, floaties, active-cooldown pips
  keybind/PsKeybinds
  ability/{AbilityBar,AbilityWheel,TargetingPreview}
  theme/{ThemeRegistry,LayoutRegistry,AssetFallbacks}
  hud/{HudWidgetRegistry,HudEditor,ClientProfileStore}
  guide/{NativeEncyclopedia,SearchIndex,BuildPlanner}
  studio/*                         — op-authorized draft editor; all writes remain server-validated
  ClientPsCache
  ClientModBusEvents

compat/  (isolated, version-pinned capability modules; absent behavior follows pack policy)
  ironsspells/  — cast hook, spell unlock, mana attrs, school attrs
  progressivestages/ — stage grant/revoke bridge
  kubejs/       — bindings + events (§14)
  curios/       — XP rings/amulets + ability curios
  ftbquests/    — reward type + require-skill task
  patchouli/    — optional GuideDocument renderer/static-resource exporter (§18)
  jade/         — XP-per-block block tooltip
  emi/ jei/     — XP-item info panels
  apotheosis/   — (free: attributes already supported; diagnose only)

mixin/  — only where NeoForge events don't exist

api/
  ProgressiveSkillsApi            — stable service lookup; no internal implementation leakage
  provider/{Trigger,Matcher,Predicate,Output,FormulaVariable,Icon,GuideRenderer,Diagnostic}Provider
  event/*                          — cancellable pre + immutable post events with transaction/provenance ids
```

---

## 16. Pack-Dev Authoring Workflow

Zero → working custom system without touching Java:
1. **Drop jar, launch once** → engine writes split common/client settings plus one manifest-enabled Core starter pack. Other presets are exported as disabled examples and cannot accidentally join the live registry or fail on absent optional mods.
2. **Copy an example, rename**, edit `id` + fields. Every example works.
3. **Author a skill** — pick curve, XP sources (§5/§5.1), and choose your progression shape:
   - *Pure linear:* fill `[[levels]]`/`[[scaling]]`; omit level-currency awards and the tree.
   - *Pure tree:* no per-level effects; add `[[level_currency_awards]]` and bind a tree.
   - *Both:* per-level effects **and** a named-currency award **and** a tree—guaranteed power + optional spec.
4. **Author abilities** (passive/toggle/active), **trees** (graph + requirements + grants), **classes** (prereqs, cost, slots, optional integration branches, synergy), and carrier items. Creator 1.1 adds templates, richer formulas, and tier generation.
5. **`/pskills validate`** — reports every bad id, cycle, unknown attribute/spell, dangling ref, with file+line and a plain-English fix. *This makes "anyone can author" real.*
6. **`/pskills reload --dry-run`** → inspect the semantic/player-impact diff → **`/pskills reload --publish`** → test live; iterate.
7. **Ship** the pack folder plus any declared client resource pack. The native encyclopedia renders from the synced projection; optional Patchouli output follows its documented export/resource-reload workflow.

**Guarantees:** no recompilation for pack content; reference validated registry/provider targets with an available entity capability/`AttributeInstance`; one Output vocabulary everywhere; fail-loud validation; full per-field docs (§18).


---

## 16.5 Pack-Dev Feature Catalog — The Full Creative Surface

*The complete north-star menu of what a modpack developer can build, purely in TOML unless an optional provider is named. Tags show the first intended train: **[Core]**, **[Creator]**, **[Multiplayer]**, or **[Studio]**. The schema/guide for an installed build shows only supported features; this catalog is not a promise that every item ships in 1.0.*

| Catalog area | Core 1.0 slice | Later expansion |
|---|---|---|
| Skills/rules | exact curves, linear outputs, named award, selected triggers | formulas, contexts, mastery, rich triggers in Creator; sharing in Multiplayer |
| Trees/classes | basic graph, purchase/refund, explicit slots, entitlements | ranks/exclusivity/evolution/loadouts in Creator; roles/auras in Multiplayer |
| Abilities/items | passive/toggle/active subset, fixed slots, pinned carriers | reactive graph/resources/stations/tier generation in Creator |
| UI/docs | vanilla screen/HUD/native guide/accessibility | themes/layout editor/planner in Creator; visual authoring in Studio |
| Integrations | only adapters whose exact spike/version gate passes | each additional adapter releases independently through §48–§49 gates |

### A. Skill design
- **Pack-defined skills within hard safety ceilings**, each with its own icon, localized/styled name, description, and level cap.
- **Five XP curve types**: `flat`, `linear`, `polynomial`, `exponential`, or a fully hand-authored `custom_table` (exact XP per level).
- **Choose the progression shape per skill**: pure linear (per-level grants), pure tree (spend points), or **both at once** on the same skill.
- **Per-level step grants** (`[[levels]]`) — exact effects/rewards at exact levels (Lv2 → +1 heart, Lv10 → a stage).
- **Smooth per-level scaling** (`[[scaling]]`) — continuous growth (+0.4 attack/level) without writing 100 blocks.
- **Milestone levels** — flagged levels fire bigger fanfare + intended for large grants.
- **Named currencies** — each skill can award any character/prestige/season-scoped currency; multiple skills may feed one balance.
- **Cross-skill prerequisites** — a skill level can require other skill levels ("Arcana 20 needs Physique 10").
- **Global (character) level** — one or more named pack-defined aggregate formulas/curves (the shipped default uses conserved run-accounted XP), usable anywhere as a gate.

### B. XP sources (how players earn it)
- **Selected proven Core bindings**, followed by the **[Creator]** expanded/provider trigger catalog (§36); a trigger ships only with reliable success semantics and tests.
- **Prefix-matched targets** — `id:`, `mod:`, `tag:`, `translation_key:`, opt-in `custom_name:`, plus `school:` for spells. Player-controlled custom-name matching is warned for economic rules.
- **First-time-only bonuses** — one-time XP the first time a player does X ever.
- **Depth/altitude scaling** — XP scales above/below a Y level.
- **Biome / dimension / structure scoping** — XP only (or extra) in the Nether, in forests, inside an Ancient City.
- **Streak/combo multipliers** — consecutive actions ramp a capped multiplier.
- **Tool/enchant gating** — only Silk-Touch mining, only with a tagged tool.
- **Rested XP** — accrues while logged off, boosts gains on return.
- **Per-source cooldowns & flat/`per_point` scaling** — anti-farm and value shaping.
- **Global + per-action XP multipliers** — server-wide tuning knobs.
- **Death XP penalty** — optional loss on death for hardcore packs.

### C. What leveling can DO (the Output vocabulary — usable from levels, trees, classes, abilities, items)
- **`attribute`** — drive a registered attribute that the target entity actually exposes: vanilla health/speed/`minecraft:generic.scale`/`minecraft:player.block_interaction_range`/attack/jump/etc., plus validated modded/ISS attributes. Missing `AttributeInstance` follows explicit target policy. Three operations (`add_value`, `add_multiplied_base`, `add_multiplied_total`).
- **`stage`** — source-aware grant/revoke when the pinned ProgressiveStages capability proves ownership; otherwise an explicitly sticky/`grant_only`, irreversible, receipt-protected fallback with respec warnings.
- **`ability`** — grant a passive, toggle, or active ability.
- **`spell`** — grant a source-owned Iron's Spells virtual spell source with an explicit learned-requirement policy (§8.3).
- **`mana`** — add ISS max mana + regen.
- **`command`** — run an operator-enabled, allow/deny-listed server command under a fixed permission/source policy, using typed escaped placeholders such as `{player}`; it is a trusted nonrollbackable escape hatch, not arbitrary client text.
- **`kubejs`** — fire a KubeJS hook by key for arbitrary custom logic.
- **`currency`** — mutate a named checked currency; version-1 `skill_point` syntax compiles to this form.
- **`item`** — give item(s) (starter kits, level rewards).
- **`flag`** — registered source-owned boolean policies only, such as PS flight/no-fall/fire-immunity switches. Reach/step height use attributes or typed handlers; night vision/water breathing/slow fall use `effect` or a proven provider; spell-cost reduction and saturation use numeric provider outputs.
- **`effect`** — apply a timed vanilla/modded potion effect (§28.2).
- **`conditions` predicate** — attach the shared predicate AST to any grant. Version-1 `when` maps are documented compiler sugar (§28.1).

### D. Skill trees
- **Pack-defined trees within hard graph/state ceilings**, scoped `global`, per-`skill`, or per-`class`.
- **Grid-laid node graphs** (`row`/`col`) with `requires` (AND) and `requires_any` (OR) edges.
- **Per-node cost** from any named currency, with per-node level/requirement gates (including cross-skill).
- **Any Output as a node grant** — a node can grant attributes, abilities, spells, stages, or even **unlock a class** (`class_access`) or **another tree** (`tree_access`).
- **Multiple trees per skill/class** with an in-GUI tree selector.

### E. Classes
- **Pack-defined class definitions within hard ceilings**, organized into pack-defined slots/capacity. A simple global cap is accepted only as v1 migration sugar and cannot mix with explicit slots.
- **Zero-cost/background slots** — model free background classes explicitly; legacy `counts_toward_cap` is v1-only and cannot mix with declared slots.
- **Prerequisites** — skill levels, owned nodes, owned classes.
- **Selection & respec costs** — in points, levels, or XP; respec per-tree/per-class/all.
- **Starter kits** — items granted once on selection.
- **Any Output as a class grant**, including the ISS features below.
- **Iron's Spells class features** (§8.3): virtual/source-granted spell, learned-policy capability, max-mana + regen, school mastery, class-exclusive active ability.
- **Class synergy** — holding two (or more) specific classes unlocks bonus grants (Mage + Warrior → Spellblade). Emergent build-crafting.

### F. Abilities
- **Core's three kinds**: `passive` (always on while owned), `toggle` (player switches on/off through a fixed binding), and `active` (assigned to a fixed slot/wheel, cooldown/charge driven). **[Creator]** adds proc, aura, channel, stance, and combo authoring.
- **Attribute effects + engine `flag` effects** on passives/toggles.
- **Typed `[[actions]]`** — Core exposes a small validated active subset; **[Creator]** unlocks the general active/proc/channel action graph for cast, effect, resource, damage/heal, movement, feedback, or trusted escape hatches.
- **Fixed rebindable action slots/wheel controls**; runtime abilities are assigned to slots instead of registering new key mappings.
- **HUD cooldown pips** for actives; toggle indicators for toggles.

### G. XP items, potions & curios (all config-defined; **[Creator]** tier generation via templates)
- **Instant XP items** — tomes/orbs granting flat or % XP to one skill or all skills.
- **Skill-picker orbs** — right-click to choose which skill receives the XP.
- **XP-multiplier potions** — timed buffs (2× all XP for N minutes); stacking configurable (highest vs multiply).
- **Passive Curios XP rings/amulets** — % XP boost while worn, optionally scoped to one skill; configurable slot type.
- **Respec items** — refund a tree or reset a class from an item, not just a command.
- **Level tokens** — grant a guaranteed level (not XP) — great as quest/boss rewards.
- **[Creator] Tier templates** — one definition → Lesser/Greater/Supreme variants via a typed multiplier parameter.

### G2. Advanced progression mechanics
- **[Creator] Conditional/contextual grants (`conditions`)** — any persistent grant can depend on night, underground, biome/dimension/structure, equipment tags, weather, health, effects, stages, or reusable predicates (§28.1).
- **Timed buff outputs (`effect`)** — grant vanilla/modded potion effects on level-up or ability activation (§28.2).
- **[Creator] XP transfer / sacrifice** — convert XP between skills (with loss), forget-for-currency reallocation, or sacrifice XP for rewards—all pack-defined through named conversion edges (§28.3).
- **[Creator] Per-dimension skill sets** — skills that only exist/level/apply in certain dimensions; bonuses switch off when you leave (§28.4).
- **[Creator] Skill decay / upkeep** — optional, opt-in atrophy of unused skills for hardcore packs (§28.5).
- **[Creator] Prestige / rebirth** — marquee reset-for-permanent-multipliers system with a configurable reset/keep matrix and stacking rewards (§28.6).

### H. Feedback, UI & discovery
- **Level-up feedback** — configurable toast + sound + particle burst + optional command (fireworks/titles).
- **XP floaties** — "+5 Mining" near the crosshair, toggleable.
- **Full tabbed Character GUI** (§17) — Skills / Trees / Classes, showing *resolved* grants so players see exactly what everything does.
- **Compact HUD** — pinned skill XP bar, level toasts, cooldown pips.
- **Native generated encyclopedia** — an in-game manual built from the synced client projection; Patchouli is an optional alternate renderer/export.
- **`/pskills top`** leaderboards per skill.

### I. Full theming & localization
- **Localized `ComponentSpec`s** with runtime pack-locale fallback, typed placeholders, plural/select variants, and optional `&` shorthand.
- **[Creator] Themes/layouts/HUD profiles** backed by a declared client resource pack, with a vanilla fallback when assets are absent or declined.
- **Op-only spoiler control** — hide stage/def names from non-ops (PS parity).

### J. Integration hooks (maximum compatibility)
- **ProgressiveStages** — leveling uses the pinned ownership-safe stage capability, or the declared sticky irreversible fallback; full lock engine reused where supported.
- **Iron's Spells** — cast XP, source-granted spell selection/learning policy, mana/school attributes, spell-casting abilities (§8.3).
- **KubeJS** — bindings + events for logic beyond TOML (custom XP triggers, conditional grants, cross-mod reactions).
- **FTB Quests — both directions** — quests award XP/named currency/classes; quests can require a skill level/class/node.
- **Curios** — XP rings/amulets + ability-granting curios.
- **Jade/WTHIT** — XP-per-block shown on block tooltips.
- **Apotheosis & other attribute mods** — registered ids become candidate targets, but apply only when the actual player/entity has a compatible `AttributeInstance`; diagnostics show missing instances.
- **Patchouli/Modonomicon** — optional guide renderer/export, limited by the pinned provider's lifecycle.
- **EMI/JEI** — XP-item info panels.

### K. Admin & authoring safety
- **`/pskills validate`** — catches every malformed def at load (bad ids, cycles, unknown attributes/spells, dangling refs) with file+line and a plain-English fix.
- **`/pskills reload --dry-run` / `--publish`** — stage and inspect first; publish only fields classified reloadable through the proper barrier, reject restart-required changes, and preserve last-known-good state.
- **`/pskills info <definition_kind> <id> [--provenance]`** — dump any schema-registered resolved definition and its source/merge lineage.
- **Full `/pskills` command tree** (§13) for granting/removing/inspecting everything.
- **Deterministic, drift-resistant** — dirty persistent projections are diffed from authoritative state; transition actions never run during reconcile; respec/de-level uses source-aware revoke.


---

## 17. UI / UX Design Spec — Vanilla-Faithful Default, Fully Themeable Platform

**Default requirement:** `progressiveskills:vanilla` must look and behave like a polished vanilla Minecraft screen, require no custom art, remain resource-pack-friendly, and serve as the guaranteed fallback. **Maximum-customization requirement:** a pack may select a synced `ThemeDef`/`LayoutDef` that references client-known resource locations for custom nine-slices, sprites, fonts, colors, sounds, spacing, states, and animations. Missing assets fall back per component to the vanilla preset. Servers do not stream arbitrary textures through progression packets; custom assets arrive through an installed/server resource pack.

### 17.0 Vanilla preset rendering contract (authoritative for the default preset)

**Textures/widgets in `progressiveskills:vanilla`:**
- Use public vanilla widgets and GUI sprites where NeoForge/Minecraft exposes a stable reusable contract (`Button`, `ImageButton`, tooltips, item rendering, narration/focus).
- PS owns its ordinary-screen panel, tab, scroller, slot-like frame, graph, and progress-bar widgets where vanilla has no generic reusable component; their default sprites/metrics deliberately imitate vanilla without pretending a chest/creative screen implementation is a public widget API.
- Every PS-owned widget has hover/focus/disabled/pressed states, narration, GUI-scale/reflow behavior, and a resource-pack-addressable sprite with a built-in fallback.
- Item icons render through `GuiGraphics.renderItem`/decorations. Tooltips use the supported screen tooltip APIs. A resource location copied from a specific vanilla screen is used only after the baseline spike verifies licensing, atlas location, mappings, and behavior for the locked version.

**Colors — vanilla constants, via the vanilla color function:**
- The default renders vanilla `ChatFormatting`/`Style`. A literal `ComponentSpec.fallback` may use `&` shorthand, which `TextUtil` converts to safe vanilla styles (`&c`, `&l`, etc.); localized pack keys remain the canonical text path (§33.4).
- Default label color = vanilla `0x404040` for dark-on-panel text and `0xFFFFFF` for light-on-slot text — the same constants vanilla screens use for inventory titles and slot labels.
- Tooltip text, error red, and "can't do that" greying all use the vanilla `ChatFormatting` values (`RED`, `GRAY`, `DARK_GRAY`), never hand-picked hex.
- The optional `[gui]` theme accents are **opt-in overlays** on top of the vanilla base — off by default, so out-of-the-box everything is pure vanilla color.

**Fonts & text:**
- The default uses the vanilla `Font`. Optional theme font ids require client resource-pack assets and always fall back to the vanilla font; missing glyphs never make gameplay information disappear.

**Layout primitives:**
- Character/guide/planner views extend ordinary `Screen`; PS owns responsive panel bounds and recomputes them in `init()` after resize/GUI-scale changes. `imageWidth`, `leftPos`, and related container fields are not assumed on a plain `Screen`.
- `AbstractContainerScreen` is used only with a real registered `AbstractContainerMenu` (for example a station with slots). Default spacing follows vanilla proportions but reflows for long locales, large text, and small windows.

**Sounds:**
- Vanilla UI sounds only: `UI_BUTTON_CLICK` on button presses, the vanilla page/tab sound on tab switch, `EXPERIENCE_ORB_PICKUP` (or a vanilla level-up chime) for level-ups. No custom audio.

**Rendering pipeline:**
- All draws go through `GuiGraphics` (1.21's blit/fill/drawString API) exactly as vanilla screens do — same `blit`, `fill`, `renderTooltip`, `drawString`/`drawCenteredString`. Item icons render via `GuiGraphics.renderItem` + `renderItemDecorations`, identical to how the vanilla inventory draws stacks (so counts/durability/enchant glint appear exactly as in a chest).

**Net effect:** the default intentionally matches vanilla visual language while explicitly implementing and testing its own ordinary-screen layout behavior. Optional themes layer on the same semantic widget/state model, so an author cannot remove the underlying narration label or locked/available state.

### 17.1 Screen shell (`PsScreenHost`) — vanilla-styled ordinary screen
```
┌─────────────────────────────────────────────────────────┐   ← vanilla container bg
│  Progressive Skills — <Player>       [Skills][Trees][Classes] │   ← vanilla tabs
├─────────────────────────────────────────────────────────┤
│                    (active tab)                             │
├─────────────────────────────────────────────────────────┤
│  Points: 12 (global) | Classes: 1/2 | Global Lv 34 | [?]Help │   ← vanilla font, 0x404040
└─────────────────────────────────────────────────────────┘
```
Extends ordinary client `Screen` because this character viewer has no inventory/container data holder and therefore does not need an `AbstractContainerMenu`. Slot-like frames are PS-owned themed sprites containing `ItemStack` icons; unlock/select actions are serverbound intent packets. A real `AbstractContainerScreen` is introduced only for a registered altar/station that actually owns slots. Tabs use accessible PS widgets styled like vanilla; the `[?]` opens the native encyclopedia.

### 17.2 Skills tab — vanilla list + slot-framed icons
- Left: a scrollable list of skill rows using the PS scroller's vanilla preset. Each skill icon sits in an 18×18 slot-like themed frame and is drawn with `renderItem`. Level + XP bar appear to the right using the preset's vanilla-styled progress sprite.
- Right: detail panel showing *resolved* grants (so players see exactly what the skill does), rendered as vanilla wrapped text via `renderComponentTooltip`-style layout. "Open Tree" is a vanilla `Button`.

### 17.3 Trees tab — node graph with vanilla slots + connectors
- Nodes are PS-owned 18×18 slot-like widgets containing the node icon via `renderItem`. The vanilla preset uses familiar normal/highlight/disabled visual states plus icon/text labels.
- Edges between nodes drawn with `GuiGraphics.fill` lines in vanilla `DARK_GRAY`/`GRAY` — simple, vanilla-toned, no custom sprites.
- Detail panel + `[Unlock]` vanilla button; disabled state uses the vanilla disabled-button sprite with a vanilla tooltip explaining why.
- Pan/scroll uses bounded PS controls; an accessible PS menu/list selects among multiple trees. No nonexistent generic vanilla dropdown is assumed.

### 17.4 Classes tab — vanilla card grid with cap
- Class cards are slot-framed icons (`renderItem`) in a grid. State chips (**Held / Available / Locked**) rendered as vanilla-colored text (`GREEN`/`YELLOW`/`GRAY` via `ChatFormatting`). Header `held/cap` in vanilla font.
- Detail panel shows prereqs with vanilla ✔/✗ coloring, cost, resolved grants (incl. ISS spell/mana), and which slot it consumes. `[Select]`/`[Respec]` vanilla buttons; over-cap `[Select]` uses the disabled sprite + the `class_cap_reached` tooltip.

### 17.5 HUD (`SkillHudOverlay`) — vanilla HUD styling
- Rendered in the HUD render event via `GuiGraphics`, styled like vanilla HUD elements: the XP bar uses the **actual vanilla XP-bar sprite**; level-up toast uses the **vanilla `Toast` system** (the same slide-in frame as advancement/recipe toasts) so it's indistinguishable from a vanilla toast; cooldown pips drawn like the vanilla item-cooldown sweep. Corner-anchored per `[gui].hud_position`, toggleable. XP floaties use the vanilla font with vanilla shadow.

### 17.6 Keybinds — fixed vanilla `KeyMapping` slots
- Register at client startup: Character Screen, Ability Wheel, Next/Previous Ability, Use Selected Ability, and a compile-time/bootstrap maximum of direct slots (for example eight, default unmapped where conflicts are likely). The server may expose no more than that negotiated maximum. Lowering the locally enabled direct-slot count is restart-required; a server can never demand a Controls entry the client did not register. These mappings appear in Controls and are fully rebindable.
- Server-defined abilities are assigned to those slots/wheel entries by the player or pack defaults. Reloading a definition changes the slot's content, not the registered key mapping.
- Optional restart-time addon modules may register dedicated static binds, but Core never claims it can create unlimited server-defined Controls entries after connection.

### 17.7 Accessibility baseline
- Vanilla `Screen`/widgets/`Font` provide GUI-scale and narrator primitives, but accessibility is still an implementation obligation: every custom graph/list widget supplies narration, logical focus order, keyboard activation, focus restoration, non-color state cues, reflow tests, and reduced-motion behavior.
- Keyboard navigation ships in Core. Controller behavior is tested explicitly where vanilla supports it and otherwise integrates with controller mods through adapters; the plan does not promise automatic controller support merely because a vanilla widget is used. See §29.3 and §44.7.


---

## 18. Documentation — Generated Reference + Native Encyclopedia + Optional Patchouli Renderer

**One schema metadata source, three renderers.** Field descriptions, types, defaults, examples, diagnostic codes, editor widgets, command suggestions, and documentation tables are registered once in `SchemaRegistry`; hand-maintained duplicate enum lists are forbidden.

**A) `DOCUMENTATION.md`** — exhaustive, KISS, updated in the same commit as each feature. Sections:
1. What is ProgressiveSkills? + 3-minute quickstart.
2. Core concepts (each in one sentence).
3. Install & first launch.
4. Prefix model, 4 worked examples.
5. Common/server-world/client settings, authority, reloadability, and complete key reference.
6. **Skill files** — every field; all `[curve].type`; the full §5 action list (row per action); §5.1 modifiers; **`[[levels]]` vs `[[scaling]]` vs trees** with the "linear / tree / both" decision explained and pictured; every output.
7. Ability files — canonical `kind`, persistent effects, costs, targeting, triggers, actions, charges, cooldown groups, and lifecycle rules.
8. Tree files — fields, `scope`, node grid, `requires`/`requires_any`, grants, ASCII layout.
9. Class files — fields, cap system, prereqs/cost/respec, **ISS features (spell/mana/school/active)**, synergy, starter kits.
10. **XP item files** — tomes/orbs, multiplier potions, Curios rings, respec/level tokens, tier templates.
11. Output vocabulary — every registered type and its supported lifecycle/rollback matrix (generated, never a hand-counted total).
12. Commands — every `/pskills` with example invocation + output.
13. Messages & theming — every `messages.*` key, placeholders, `&`-code table.
14. UI guide — annotated per tab.
15. KubeJS + Java API — every binding/event with snippets.
16. Integrations — ISS, PS, Curios, FTB Quests (both ways), Jade, Patchouli, Apotheosis: what turns on, flags, gotchas.
17. **Troubleshooting table** — "skill won't level / attribute did nothing / class won't select / spell didn't unlock / validate says X" → cause → fix. The KISS payoff.
18. Full worked example pack — 2 skills (one linear, one tree, showing both), 2 classes, 2 abilities, 3 XP items — end to end.

Every reference field uses: `| Key | Type | Allowed values | Default | Description | Example |`.

**B) Native in-game encyclopedia (always available)** — generated client-side from the server's redacted presentation projection. It has full-text search, categories, reverse references (“what unlocks this?”), resolved formulas for the current player, “why locked?”, next milestone, XP-source explanations, integration fallbacks, secret-content policies, and links into the build planner. It requires no optional mod and updates immediately when a staged reload publishes.

**C) Patchouli adapter (optional alternate renderer)** — when Patchouli is present, render the same intermediate `GuideDocument` model through Patchouli where its runtime API supports it. If a concrete Patchouli version requires static resource JSON or a resource reload, generate/export the resource pack and state that reload requirement honestly; do not promise that server TOML magically becomes client resources. The native encyclopedia remains canonical and cannot be disabled by a missing integration.

**D) IDE/CI artifacts** — generate Taplo-compatible TOML schema metadata where possible, JSON Schema for the JSON adapter, code snippets, a machine-readable definition catalog, and `validate --json|--sarif` output from the same source.

**Docs-complete checklist (feature not merged until all true):**
- [ ] Every new config key in the right §5–§13 reference table (type/values/default/example).
- [ ] Every new enum value (action, flag, output, curve, scope, item kind) documented as its own row.
- [ ] ≥1 copy-paste example exercises it end-to-end.
- [ ] New command/GUI element in §13/§17/§44 as applicable.
- [ ] Troubleshooting entry if it has a common failure mode.
- [ ] Native encyclopedia, generated reference, Studio form, and optional Patchouli adapter cover the new def type.
- [ ] `/pskills validate` emits a clear message for every misconfiguration path.

---

## 19. Build Order (each phase ships code + matching docs)

1. **Scaffold, CI, and evidence lock** — fix deprecations; pin NeoForge/MC mappings and an optional-mod compatibility matrix; build client/server/GameTest runs; JUnit/property-test harness; package boundaries and no-client-on-server classloading test.
2. **Schema registry + canonical IR** — source spans, schema versions, stable ids, aliases, typed Components/icons, immutable definitions, generated docs/diagnostics/editor metadata. No gameplay yet.
3. **Content packs + staged loader** — manifests, namespaces, roots/precedence, deterministic merges, dependency policies, staging registry, semantic diff, last-known-good persistence, `/pskills validate`, `/pskills reload --dry-run`.
4. **Transaction and lifecycle core** — progression transaction, state revision, plans/costs, persistent projector, transition executor, receipt/idempotency ledger, audit record, rollback boundary, cascade queue. This phase must prove a recompute never duplicates an item/command/point.
5. **Persistence and migrations** — versioned attachment with `copyOnDeath`, quarantine, aliases/replacements, embedded migration shadow, pending offline operation SavedData, snapshot/export primitives, fuzzed codecs and size ceilings.
6. **Networking handshake + definition projection** — Core 1.0 requirement: protocol negotiation, digest, sanitized presentation DTO, bounded/chunked payloads, full + delta state sync, stale revision rejection, reload invalidation, malformed-packet tests.
7. **Skill XP vertical slice** — fixed-point XP, curves, one named currency, manual/custom XP rule, highest-level point entitlement, linear levels/scaling, attributes, feedback, `/pskills xp`, exact drift tests. Prove Physique end to end.
8. **Rule engine and anti-exploit foundation** — trigger/matcher/provider registries, stable rule ids, compiled route tables, multiplier order/stack groups, caps, first-time, cooldown, source memory, fake-player policy, event dedupe. Add bindings incrementally with tests.
9. **Requirement/expression foundation** — implement the typed internal evaluator, dependency index, fixed-point rounding, preview/explain hooks, and performance budgets needed by Core. Core authoring exposes only selected direct requirements and literal/simple bounded amounts; reusable predicate definitions and the general formula DSL remain feature-gated until Creator.
10. **Trees and exact refunds** — single-rank acyclic Core nodes, dependency policy, historical-cost ledger, cascade preview, transactions, and tree UI. Prove level loss/respec cannot create currency. Ranked nodes, exclusion groups, generated layouts, and formula costs remain Creator features.
11. **Classes and entitlements** — typed class slots/capacity, source-aware abilities/spells/stages/access, synergy defs, selection/swap/respec policies, once-only kit receipt. Prove two sources can own one entitlement without premature revoke.
12. **Ability bar and minimal action subset** — fixed registered mappings, wheel/slots, cooldown groups, charges, optional named-currency or vanilla hunger/XP costs, validated targets, and basic passive/toggle/active actions. Internal origin tags are present from day one, but authorable reactive procs, custom resource meters, and the general action graph remain Creator features. No unlimited dynamic keybind promise.
13. **Carrier items and acquisition** — pre-registered carriers + data components, stack factory, recipes/loot helpers, inventory-full delivery queue, Curios adapter, JEI/EMI subtype handling, changed-digest policy.
14. **Baseline UI + native encyclopedia** — ordinary `Screen`, vanilla preset, skills/trees/classes/abilities, search, why-locked, build preview, accessibility/navigation, client HUD profiles. Patchouli is optional.
15. **Highest-value compatibility** — ProgressiveStages ownership-safe bridge, one pinned ISS version then tested version range, KubeJS provider/bindings, FTB Quests, Curios; optional types isolated and dedicated-server classloading-tested.
16. **Core 1.0 hardening/release** — GameTests, optional-mod CI matrix, property/fuzz/network tests, 40–100 player synthetic load, migration rehearsal, docs/schema generation, vanilla-lite + hybrid example packs, release checklist (§49).
17. **Creator 1.1** — templates/mixins/variables, advanced formulas, contextual grants, resources/currencies, advanced trees/classes, prestige, conversions/decay, solo/local challenges, loadouts/build codes, theme/layout/HUD editor, simulation/trace, export/import.
18. **Multiplayer 1.2** — assist credit, party/team/guild progress, mentor/catch-up, shared/community/seasonal challenges, seasons, privacy-aware boards, PvP anti-boosting, shared-storage provider API.
19. **Studio 2.0** — visual authoring, draft/live split, graph editor, impact analysis, atomic publish/rollback, datapack JSON adapter, visual `.pspack` composition/signing, external editor protocol.

---

## 20. Locked Shipped Defaults and Explicit Release Blockers

- **Starter currency:** `progressiveskills:global_points`, character-scoped in Core. Packs may replace it with any named currency; there is no special `global` pool enum.
- **Starter class capacity:** explicit `progressiveskills:combat` slot with capacity 2. There is no v2 `max_classes` setting in the starter pack.
- **Negative XP/delevel:** `deny` by default. Packs that choose `allow_delevel` receive immediate persistent-effect/dependency recompute and only explicit `on_loss` actions.
- **Reach safety:** cap PS modifier amounts per operation group; the Core starter permits at most +64 `add_value` for `minecraft:player.block_interaction_range`. This is not a final-value clamp against foreign mods.
- **XP multiplier stacking:** `highest` within a named stack group by default; opt-in multiplication is bounded by the group ceiling and visible in explain/simulation.
- **Rested XP (Creator):** off unless enabled. The starter preset caps the pool at `1.5 × C(currentLevel)` and adds +50% to eligible XP until drained; pacing playtests may change these numbers only through a recorded balance decision/migration preview.
- **Iron's Spells adapter:** exact target version/range remains a release blocker for that optional adapter (§8.3, §48.4), not for Core. Selection and learned-requirement satisfaction remain separate capabilities.
- **Authoring adapters:** TOML is Core 1.0; datapack JSON is Studio 2.0 unless a signed release decision and parity tests move it (§32.3, §49).


---

## 21. Persistence, Migration & Orphaned-State Policy

*The #1 source of "my save broke" reports. Explicit policy so a pack dev iterating on config never destroys player progress.*

**Storage model.** Per-player state (`ProgressiveSkillsData`, §2) is a serialized NeoForge **data attachment** on the player with an explicit data version and `copyOnDeath()` (plus a Clone regression test covering death versus End return). It is authoritative while the player is loaded. Overworld `SavedData` holds only server-scoped state and pending offline transactions; it does not duplicate every live player field.

**Raw migration ingress.** Do not attach only a strict current-model Codec that can fail before migration sees old data. Register a bounded custom `IAttachmentSerializer<ProgressiveSkillsData>` for the pinned NeoForge baseline: read a raw `CompoundTag`, enforce depth/count/string/byte limits, inspect `dataVersion` first, quarantine and preserve corrupt/future-version raw data, run a pure `tagVn → tagVn+1` chain, then decode the current typed model. Writing emits the current version plus any bounded migration shadow/unknown-extension bucket. No gameplay projection runs from undecoded/quarantined state.

**Death/clone recovery order (callback-idempotent, persistently effectively-once):** a real server death creates a unique `deathTransactionId` marker on the old state; NeoForge copies/clones the attachment to the replacement player; the replacement coordinator applies loss and writes an `OperationReceipt` in the **same attachment mutation**, then recomputes/syncs. Duplicate callbacks see the receipt and no-op. If a crash occurs before that attachment save, both loss and receipt roll back and recovery retries; if it occurs after, both survive. This is not marketed as a universal cross-file exactly-once commit (§35.6). End return/non-death replacement has no death marker and incurs no penalty. Tests cover keep-inventory, duplicates, disconnect, and each crash checkpoint.

**The orphaned-state rule: QUARANTINE, never silently drop.** When the config no longer defines something a player owns (a deleted skill, class, node, ability), the mod:
1. **Keeps the raw data** in the attachment (does not delete it).
2. **Stops applying its effects** (the orphaned class grants no attributes, the orphaned skill grants nothing) so a removed def has no live effect.
3. **Marks it orphaned** and lists it under `/pskills debug <player>` and on reload logs ("player X holds 3 orphaned defs: …").
4. **Restores only with compatible lineage** — same recorded definition lineage/fingerprint or an explicit replacement/restore declaration. Reusing an old id for unrelated content stays quarantined for operator review.

Rationale: pack devs rename/refactor constantly; losing progress on a typo is unacceptable. Quarantine makes config edits reversible. A pack dev who *wants* to purge orphans runs `/pskills prune <player|all>` (explicit, op-only, confirmed).

**Specific migration cases (each with a defined behavior):**

| Change a pack dev makes | What happens to existing players |
|---|---|
| **Rename any stateful id** | Without a pack-level replacement it becomes orphan + new state. `replacements.toml` maps the old typed/full id to the new id and carries state/receipts/provenance after cycle/conflict validation (§33.1). |
| **Delete a skill/class/node/ability** | Orphaned; effects removed and data retained. Automatic restore requires compatible lineage/fingerprint or explicit operator/replacement approval. |
| **Lower `max_level` below a player's level** | Player clamped to the new cap for effect purposes; **banked overflow XP retained** but inert; if cap is raised again, they resume. Never lose XP. |
| **Change the XP curve/cap** | Default `preserve_level_fraction` preserves level, exact within-level progress fraction, and earned/accounted/lifetime history; it recalculates raw `xpIntoLevel/currentTotalXp` against the new thresholds. Other named policies require impact preview. Migration/reconcile crossings suppress gameplay transition rewards; operators may issue a separate receipt-protected “grant missing eligible rewards” transaction. |
| **Change/remove an `[[levels]]`/`[[scaling]]` grant** | Recomputed for every online player inside the publish generation barrier before new routes reopen (§35.7); offline players reconcile before activation on login. Removing a grant cleanly removes its modifier. |
| **Lower an explicit class-slot capacity below current occupancy** | Player keeps existing classes grandfathered but cannot add in that slot; a warning is shown. An optional confirmed migration may force-respec to fit after a full refund/dependency preview. |
| **Change a referenced target to an invalid id** | Structural validation rejects the owning definition (or the pack, by manifest atomicity) in staging. Only an explicitly declared optional integration branch may skip/fallback. Live state keeps the last-known-good registry. |
| **Mod version upgrade (schema bump)** | `dataVersion` drives the bounded raw-tag migration chain before current-model decode. Each migration is a pure function; **a world backup reminder** is logged on first load of a new schema. Downgrade/future data is quarantined with a clear message, not decoded as current state. |

**Backup discipline.** A schema bump cannot truthfully snapshot every unloaded attachment without walking and rewriting arbitrary player files. Instead: (1) log a world-backup requirement before startup migration; (2) migrate each player lazily when their attachment loads; (3) retain a bounded embedded `preMigrationShadow` + old data version until that player completes a clean save/login cycle; (4) provide explicit `/pskills snapshot`/export for planned maintenance; and (5) retain the last-known-good definition set and content digest. Never claim a bulk `.psbak` exists unless the implementation has actually enumerated and verified every target.

**KubeJS/command-granted state** is stored identically and quarantined the same way if the referenced def disappears.

---

## 22. Attribute Conflict & Modifier-ID Model

*The most technically dangerous subsystem. 1.21 replaced UUID-keyed attribute modifiers with `ResourceLocation`-keyed ones — if two of our sources derive the same id, they silently overwrite instead of stacking. This section defines the id scheme, stacking rules, and cross-mod conflict behavior, with worked math.*

**Deterministic modifier ids.** Every persistent attribute grant has a required stable authoring `id` (`grantId` in IR). The modifier id is derived from `(ownerDefinitionId, grantId)`—not merely `(source, attribute)`, which would collide when one skill intentionally grants the same attribute twice, uses two operations, or has conditional variants:
```
progressiveskills:g/<short-stable-hash(owner-kind + owner-id + grant-id)>
  e.g. owner=skill/mypack:physique, grant=mypack:physique/health_per_level
       owner=class/mypack:mage,     grant=mypack:mage/max_mana
```
The hash keeps the `ResourceLocation` valid and length-bounded; a runtime provenance table maps it back to file, source span, owner, grant id, attribute, operation, conditions, and resolved amount for `/pskills explain`. Collisions are detected during compilation and are fatal to the staging set. Multiple compatible grants may optionally be aggregated for performance, but diagnostics retain every contributing grant. Two sources stack; the same stable grant is idempotent.

**Operation semantics (vanilla 1.21):** first let `X = base + sum(ADD_VALUE)`. Then `Y = X + X × sum(ADD_MULTIPLIED_BASE)`. Finally apply each `ADD_MULTIPLIED_TOTAL` as `Y = Y × (1 + amount)` in deterministic modifier order, followed by the attribute's own sanitization. We expose these as `add_value` / `add_multiplied_base` / `add_multiplied_total`. Pack docs (§18) explain the difference with the worked example below so devs pick correctly.

**Worked math — a player leveling Physique with multiple sources on `max_health` (base 20):**
- Skill per-level grant: `add_value +8` (from `[[scaling]]` at their level).
- Class "Juggernaut": `add_value +6`.
- Node "Ironhide": `add_multiplied_base +0.10`.
- Worn "Vitality Amulet" curio: `add_multiplied_total +0.05`.

Vanilla computes: `X = 20 + 14 = 34`; `Y = 34 + (34 × 0.10) = 37.4`; total multiplication gives `37.4 × 1.05 = 39.27` → **39.27 max health (19.635 hearts).** Removing the amulet yields `37.4`. Crucially, `ADD_MULTIPLIED_BASE` uses the base **after all flat additions**, including foreign ones; `ADD_MULTIPLIED_TOTAL` amplifies the whole running value.

**Cross-mod conflict policy.** Other mods (Apotheosis gear, attribute-editing tools) normally apply their *own* namespaced ids to the same attributes, so legitimate contributions stack per vanilla rules. PS reserves `progressiveskills:*`, proves every compiled PS modifier id is unique, and diagnoses any observed modifier from an unknown source that uses that namespace; a buggy/malicious mod can still forge a colliding id, so “never collide” is not a security claim. We do **not** clamp or fight foreign modifiers; we only own ours. Two consequences, both documented:
- If a pack stacks our grants with heavy gear, values can balloon. `[attributes]` caps/diminishing returns constrain **our modifier amounts per operation group before insertion**; they cannot isolate a final scalar “our contribution,” because multiplied-base/total operations also amplify foreign flats. Diagnostics warn on extreme predicted final values. A separate invasive hard-final-cap mode requires an explicit mixin/compat policy.
- We never strip or override foreign modifiers. `/pskills debug <player>` lists **all** modifiers on an attribute with their source namespace, so a dev can see exactly who contributed what (ours vs Apotheosis vs vanilla gear).

**Modifier-amount caps / diminishing returns (`[attributes]`).** Configure separate limits/curves for the summed PS `add_value` amounts, summed PS `add_multiplied_base` amounts, and PS `add_multiplied_total` product/factors before modifiers enter vanilla calculation. Reports show both those bounded amounts and the observed final value with foreign modifiers. Default absolute amount ceilings (especially reach, scale, speed, and total multipliers) prevent catastrophic typos; operators may relax gameplay caps within compile-time limits. This is never described as a separable contribution or final effective-value cap.

**Refresh model.** A dirty projection computes the desired modifiers and diffs against the last applied set: remove stale ids, update changed ids, leave identical ids alone. This preserves idempotency with less churn. Equipment adapters invalidate only relevant sources.

---

## 23. Determinism, Output Ordering & Re-entrancy Guards

*Prevents nondeterministic, unreproducible bugs when many outputs fire at once or outputs trigger more outputs.*

**Full-recompute model for persistent effects only.** Progression state is the source of truth; **durable/ref-counted effects are derived by a recompute** from that state. Level up, respec, equip a charm, gain a class — all invalidate a projection which:
1. Recollects active **persistent** grants from eligible skills, node ranks, classes, synergies, abilities, equipped carriers/Curios, prestige, teams, and contexts.
2. Produces a source-aware entitlement projection and desired modifier map in a **fixed, documented order**.
3. Diffs desired versus currently applied state: remove stale ids, update changed ids, leave identical ids untouched.

`item`, `command`, `function`, `kubejs`, timed `effect`, particles/sounds/messages, XP/currency/resource mutations, and other transition actions are **never executed by recompute**. They execute only inside a named transaction edge and record a receipt/outbox entry when policy requires it (§35). This guarantees reconcile/reload idempotency; cross-save/external crash semantics are declared separately.

**Fixed projection order** (so provenance and conflict resolution are deterministic):
`skills → node ranks → classes → synergies → abilities → equipment/carriers → prestige → team/world → admin/manual`, and within a bucket by stable definition then grant id. Pure attribute math is order-independent; explicit conflict/stack groups resolve by priority then id. Transition actions preserve the list order inside their originating transaction after validation.

**Level-jump ordering.** A single XP award crossing multiple thresholds processes level edges in ascending order. Persistent `effects` become part of the final projection; transition `rewards` execute in their listed order subject to repeat receipts. Milestone fanfares fire once per configured crossing policy.

**Re-entrancy & loop guards.** Outputs can trigger outputs (a `command` output that runs `/pskills xp`, a `kubejs` hook that grants a class). Guards:
- A per-player **re-entrancy depth counter**; `recomputeAll` and XP application are non-reentrant — nested triggers are **queued and drained** after the current pass, not applied mid-pass.
- A native `CascadePlan` expands the full statically/procedurally knowable child closure, costs, refunds, receipts, outbox capacity, and value deltas **before the first mutation**. Depth, breadth, action, value, and time-estimate budgets are reservations, not truncation points. If expansion cycles, cannot finish, or exceeds any budget, reject the entire native transaction with zero committed prefix; never “drop everything after depth 8” after an earlier mint.
- `command`, server `function`, KubeJS, and opaque providers are marked side-effecting and execute only after the committed native plan, with explicit non-atomic delivery semantics. By default an opaque callback cannot synchronously re-enter a value-bearing PS mutation; the origin-chain circuit breaker rejects/quarantines that child **before it commits**. A provider that wants atomic child value must register a bounded typed child plan during planning. An explicit trusted unsafe re-entry mode remains economy-unproven, has strict per-source quotas/circuit breaking, and may never claim that a later sink will compensate an already-run external mint.
- Queued non-value feedback still drains under a bounded budget. Hitting that feedback budget coalesces/drops only audiovisual messages with diagnostics; it never truncates a currency/XP/item/cost chain.
- `/pskills validate` **statically detects** obvious loops (a level grant that runs a command that grants XP to the same skill; circular class synergy; A-unlocks-B-unlocks-A node cycles) and warns at load.

**Source-aware entitlement rule.** Stages, abilities, virtual spells, **PS virtual recipe gates**, tree/class access, flags, titles, and similar PS/provider entitlements are represented as `target -> set<GrantSource>`. Removing one source never removes an entitlement another source still owns. Manual/admin entitlements use a separate source namespace and survive config recompute until explicitly revoked. For valued entitlements (for example the same ISS spell granted at several levels), a resolver such as `highest`, `sum`, or explicit priority selects the effective value while retaining all owners. ProgressiveStages revoke is used only if its pinned API can preserve external ownership; otherwise the compat falls back to grant-only or a documented adapter-owned stage namespace. Vanilla recipe knowledge is sticky because it cannot prove owners.

---

## 24. Networking Sync Protocol & XP Hot-Path Performance Budget

*Keeps a 40-player server smooth and the XP event loop allocation-free.*

**What syncs, and when:**
- **During configuration/join:** negotiate protocol/features plus `semanticDigest/definitionGeneration` and `clientPresentationDigest/presentationRevision`, then send a bounded, sanitized definition projection only when the cache entry for this exact server identity and digest pair differs. A presentation-only change can refresh UI/locales/themes without pretending gameplay state changed. Definitions are compressed and chunked below NeoForge/vanilla payload ceilings; no single clientbound payload approaches the 1 MiB hard limit. Chunks have transfer id, index/count, uncompressed-size cap, digest, timeout, and total-count cap. The client validates and atomically assembles, then ACKs the digest pair; state/UI activation waits for that ACK or fails with a bounded retry/friendly disconnect.
- **On join / respawn / dimension change:** send the player's full visible state snapshot with monotonic revision/generation; dimension changes ordinarily need contextual deltas, not a redundant definition upload. If a highly customized state exceeds the conservative single-payload ceiling, it uses the same bounded transfer envelope as definitions (transfer id, chunk/total/uncompressed caps, digest, atomic assembly, ACK/abort). Mutation UI stays disabled until state ACK; an over-hard-limit state fails safely with an operator diagnostic rather than allocating unbounded memory.
- **On state change:** normally send a small semantic delta with gameplay `definitionGeneration/semanticDigest`, current `presentationRevision`, `baseRevision → newRevision`, and changed typed paths. The client applies only when gameplay generation/digest and `baseRevision` match, refreshes presentation independently when needed, ignores an already-applied identical result, and requests a bounded full resync on a gap, out-of-order revision, unknown path, or hash mismatch. Never resend the whole blob for a single XP tick when continuity is intact.
- **XP-per-tick churn (movement/mining):** the server accumulates XP and syncs the *display* value on a **throttle** (config `sync_interval`, default every N ticks or on level-change, whichever first). The authoritative value is server-side; the client bar interpolates between syncs for smoothness. A level-up always syncs immediately (it's rare and important).
- **Client → server:** only bounded **intent** messages (spend node rank, select class, assign/activate slot, toggle ability, confirm respec). Every request carries negotiated `sessionId`, a monotonically increasing `clientRequestId`, definition generation/digest, and state revision. The server stores `highestSeen` plus a bounded replay window/result cache: an in-window duplicate returns the identical result/transaction id, a request older than the retained window is rejected and **never re-executed**, and a future jump beyond the bound is invalid. Reconnect-safe value delivery uses a separate durable transaction/idempotency key; it does not rely on an expired connection cache. The server recomputes validity and rejects stale/oversized/rate-exceeded intent with a targeted resync. The client never supplies effect amounts, XP, costs, targets outside allowed encoding, or arbitrary command text.

NeoForge's directions are intentionally asymmetric: every serverbound intent codec is tested against the documented **less-than-32-KiB** payload ceiling and a smaller PS per-intent hard limit; the 1-MiB figure applies to clientbound custom payloads, not requests. No serverbound chunking endpoint accepts general files/definitions, and fuzz tests reject oversized lengths before allocation.

**Payload discipline.** All payloads are NeoForge `CustomPacketPayload`s with explicit bounded `StreamCodec`s and registrar protocol version. Collection/string/definition counts are checked **before allocation**; unknown enum/type ids fail closed; decompression has an absolute output cap; serverbound messages have per-player token-bucket rate limits. A full definition projection may be simpler and safer than a semantic delta for Core 1.0; add delta-def sync only after profiling proves it valuable. Opening a read-only screen uses the current cache, while mutations always go to the server. Player state, session, and request-result caches are cleared on disconnect. Only the sanitized definition/presentation projection may persist under a bounded TTL/LRU, keyed by the user's local server-list identity + protocol + semantic/presentation digest; it stays inactive until the new handshake proves that exact key, is never shown during a different join, and has a clear-cache control. Thus a reconnect can be a definition-cache hit without carrying authority or another server's player state.

**XP hot-path performance contract** (the `mine_block`/`deal_damage` path fires thousands of times):
- **No avoidable per-event allocation** in the hit loop. Match sets are **precompiled at load** into fast structures: exact `id:` → hashed `Set<ResourceLocation>`; `tag:` → resolved `HolderSet` membership checks; `mod:` → interned namespace compare; `translation_key:` → precomputed stable string; opt-in `custom_name:` → bounded normalized matcher. An event does O(1)–O(k) lookups with no regex compilation or diagnostic formatting at runtime.
- **One stable listener per supported NeoForge event family** is registered at startup and routes through an atomically swapped compiled table. A disabled action has an empty table and a single predictable early return. This is compatible with hot reload; dynamic listener unregister/re-register is not assumed to be safe.
- **Per-source cooldowns and streak windows** use primitive long timestamps in the attachment, not object timers.
- **XP is integer/long** (no floating error accumulation); level-up check is a single comparison against the cached next-threshold, not a curve recompute.
- **Recompute is triggered on level change / structural change only**, never per XP tick — a player mining for an hour triggers recompute only when they actually level.
- **Batch window:** every authoritative award transaction commits in order so later same-tick transactions observe it. Only projection invalidations, feedback, and display-sync deltas coalesce at end of tick; gameplay mutations are never delayed into a stale aggregate.
- The XP path must meet a written hardware/scenario budget under sustained mining/combat; verify with matcher microbenchmarks plus dedicated-server JFR/soak profiling (§46.7), not a vague “negligible” claim.

---

## 25. Testing & QA Strategy

*Scaffolded in Phase 1, not bolted on at the end. For a mod this stateful, tests are load-bearing.*

**Unit tests (JUnit, no game):**
- Curve math: every `[curve].type` — nondecreasing by default, no overflow before cap, `custom_table` boundaries, XP-to-next correctness; Creator's explicit decreasing-cost opt-in stays positive/bounded and passes separate edge tests.
- Modifier id derivation: uniqueness per owner-kind/owner-id/grant-id, collision detection, idempotency, provenance lookup.
- Output ordering: fixed order honored; level-jump ascending; union/refcount stage semantics.
- Prefix resolver: `id:`/`mod:`/`tag:`/`translation_key:`/opt-in `custom_name:`/`school:` matching, including negation, normalization, and player-controlled-name warnings.
- Migration functions: each `migrateVNtoVN+1` is a pure function with fixture inputs/outputs; alias remapping; cap-lower clamping preserves XP.

**NeoForge gametests (in-game, automated):**
- Each XP action binding: spawn a world, perform the action (break a block, kill a mob, cast if ISS present), assert the right skill gained the right XP.
- Attribute application: grant a level, assert the vanilla attribute value equals the worked-math expectation; unequip a curio, assert clean removal.
- Class cap: add classes to the cap, assert the next add is refused; respec, assert grants removed.
- Tree unlock: spend points, assert node grants apply and points deduct; assert prereq gating.
- Stage emission: level to a threshold, assert the ProgressiveStages stage is granted; de-level, assert revoked (when PS present).

**Drift tests (the critical stateful guarantee):**
- Level up → capture canonical semantic state + effective values → relog → assert semantic equality and identical effective values (serialized map/NBT ordering need not be byte-identical).
- Respec → re-spec to the same nodes → assert identical.
- De-level then re-level → assert identical.
- Add/remove a def from config (orphan/restore) → assert quarantine keeps data and restore recovers exactly.

**Performance work:** benchmark pure matcher/formula code separately, then simulate N players firing mining/combat/movement events on a dedicated server and inspect JFR/allocation/tick percentiles. Avoid flaky wall-clock assertions inside GameTests.

**Manual smoke checklist per phase** (documented, run before merge): a short scripted play session exercising that phase's feature end-to-end (e.g. Phase 7: "make a linear Physique skill, level it, confirm hearts then jump appear on schedule and survive relog").

**CI:** `./gradlew build test` on every push; gametests run in the NeoForge test harness. Green build + green tests is the merge gate (with the docs-complete checklist, §18).

---

## 26. Multiplayer, Permissions & Offline Semantics

**Server authority.** All progression is server-side (§24). Singleplayer runs an integrated server, so the same code path applies — no separate SP logic.

**Who can do what (Core numeric levels; server-configurable upward):**
- A player always opens **their own** Character screen (client-side open, no permission needed) and sees their own skills/trees/classes.
- Normal self-service purchases, class choices, ability slots/toggles, claims, and allowed respecs are server-validated gameplay intents and do not require op.
- Routine admin inspection/grants/sets default to permission level 2.
- Destructive bulk mutation/prune/migration/snapshot restore defaults to level 3 and confirmation.
- Reload publish/rollback, freeze, pack import, and Studio write/publish default to level 4. Read-only validation/dry-run may be delegated separately.
- **Read commands** (`/pskills top`, and a player-scoped `/pskills skill get` on *self*) are available to everyone; inspecting *other* players requires op.
- **`/pskills debug <player>`** is op-only (it exposes full modifier/source breakdowns).

**Granular permission integration.** Vanilla numeric permission levels are the Core fallback. Named nodes such as `progressiveskills.command.xp` require a tested NeoForge permission-node/provider adapter; compatibility with LuckPerms or another provider is version-pinned and capability-diagnosed, never inferred from vanilla alone.

**Offline / rested semantics.**
- **Rested XP** accrues based on logout timestamp; on login the boost pool is computed from elapsed offline time (capped). No server tick cost while offline (it's timestamp math on login).
- Offline players have no loaded attachment. If allowed, an op mutation creates a durable-id `PendingProgressionOperation` in overworld `SavedData`. Creation validates the request, but **application validates again** against the current definition generation/digest and replacements, resolved target UUID/scope, character/prestige/season epoch, expiry, permission/issuer policy, caps, affordability, and requested reward eligibility. A materially changed operation is deterministically rebaseable only when its stored policy and aliases prove equivalent; otherwise it expires/quarantines for operator review instead of leaking an old grant into a new season or definition. Then use a two-phase recovery protocol: (1) apply the mutation and same-id `OperationReceipt` together in the player attachment; (2) mark the SavedData operation consumed. A crash before the attachment save retries both; a crash after it sees the receipt and only completes phase 2. This is crash-recoverable/effectively-once, not an atomic commit across two stores. It does **not** claim to modify unloaded NBT immediately. Unknown players are refused unless a previously joined UUID resolves.
- Leaderboard (`/pskills top`) reflects last-known stored values for offline players.

**LAN / dedicated / integrated:** identical authority path. Dimension changes send context/state deltas and a full state only when revision continuity is lost; definitions resend only when the digest differs.

**Team integration (1.2).** Party XP and team/guild scopes route through the provider-neutral contract (§43); FTB Teams is one optional adapter. Core 1.0 only exposes the provider hooks and ordinary character progression.

---

## 27. Balance & Sanity Warnings in `/pskills validate`

*`/pskills validate` checks not just structural validity but **balance sanity** — the KISS pillar applied to authoring. Structural errors block the def; sanity issues warn but load (a dev may mean it).*

**Structural errors (owner is invalid in staging; default whole-snapshot publish is rejected, with file+line + plain-English fix):**
- Unknown attribute / spell / stage / ability / tree / class / node id referenced.
- Dependency cycles: node A↔B, class synergy loops, skill-prereq cycles.
- Malformed TOML, missing required fields, wrong types.
- `bind`/`scope` mismatch (e.g. `scope=class` with a `bind` that isn't a class).
- Duplicate ids.
- Referencing an absent/unsupported ISS spell, attribute, Curios slot, or any provider target is a **structural error by default**. It becomes a warning plus the declared `disable_def`, `skip_declared_branch`, or `hide` behavior only when the owning manifest branch explicitly names that optional dependency/policy.

**Sanity / balance warnings (loads, but flagged):**
- **Numeric overflow:** "curve `physique` exceeds Long.MAX around level 78 — cap it or lower `factor`."
- **Extreme grants:** "grant on `max_health` is 400× the vanilla base (20) — intended?"; "reach grant of 128 exceeds the configured soft cap (64) — will be clamped unless `allow_uncapped`."
- **Loop risk:** "level-up on `mining` runs a command that awards `mining` XP — possible feedback loop; the native `CascadePlan` rejects an unbounded cycle before commit, and opaque re-entry is blocked unless explicitly unsafe. Remove or bound the loop."
- **Unreachable content:** "node `bladestorm` requires `berserker` which no tree/level can grant — unreachable."; "class `archmage` prereq needs `arcana 200` but `arcana` cap is 100 — unobtainable."
- **Capacity conflicts:** "slot `background` has zero-cost classes whose combined level-1 grants include flight—intended?" Legacy cap fields mixed with explicit slots are errors, not balance warnings.
- **Free power at level 1:** "skill `physique` grants flight at level 1 via `[[levels]] level=1` — sanity check."
- **Empty/no-op defs:** "skill `mining` has no XP sources — it can never level."; "tree `x` has no nodes."
- **Optional slot issue:** "optional branch `mypack:curios_items` targets unavailable slot `ring`; declared policy hides that branch." An undeclared missing slot is an error, never a silent no-op.

Every warning names the file, the field, the concern, and (where possible) the fix. `/pskills validate` prints a summary (`N errors, M warnings`) and exit-style status so a dev knows at a glance. Runs automatically at load (logging) and on demand.

---

## 28. New Spec'd Features (promoted from ideas)

### 28.1 Conditional / Contextual Grants
Any Output can carry a **`when` condition** — it applies only while the condition holds, reusing the XP-scope filter infra:
```toml
# Fragment (schema v2): append to an owner with `mypack:is_night` defined in `predicates/`.
[[grants]]
id = "mypack:nightstalker/speed"
type = "attribute"
attribute = "minecraft:generic.movement_speed"
operation = "add_multiplied_base"
value = 0.20
conditions = "mypack:is_night"
# Other predicate leaves include dimension, biome/tag, y_range, structure,
# equipment, weather, health_fraction, effect, and stage.
```
Contextual **persistent** grants use the shared predicate AST and dependency-indexed watcher (§34), applying/removing by stable grant id as the condition flips. Contextual transition actions must declare `on_enter`/`on_exit` plus repeat/receipt policy; they are never fired by an ordinary projection refresh.

### 28.2 Temporary Timed Buffs as an Output
A new Output `type = effect` applies a **timed potion effect** (vanilla or modded `MobEffect`) on the triggering event:
```toml
# Fragment (schema v2): append to an ability `actions` container or milestone reward.
[[actions]]                           # or an explicit on_first_reach milestone reward
id = "mypack:berserk/strength"
type = "effect"
effect = "minecraft:strength"
duration_ticks = 200
amplifier = 1
```
Great for an active ability's `[[actions]]` and level milestones (`on_first_reach`). It uses a transition/timed lifecycle with a transaction/receipt as appropriate and is never emitted merely because a class/level remains eligible during recompute.

### 28.3 XP Transfer / Sacrifice
Config-defined conversion mechanics so players can reshape progression:
- **Skill-to-skill transfer** — `/pskills convert skill <player> <from_skill> <to_skill> <amount>` and an optional conversion carrier/station that trades XP through a named conversion edge (with loss/tax).
- **Forget-for-currency** — an op action/item that converts a skill's progress into a named currency at a configured ratio (distinct from tree respec).
- **XP sacrifice** — convert skill XP into a `command`/`item` output (e.g. sacrifice Arcana XP at an altar for a reward), all pack-defined.
All conversions are server-validated, rate/loss configurable, and off unless a pack defines them. Documented with exchange-rate examples.

### 28.4 Per-Dimension Skill Sets
A skill (or class, or the whole system) can be **scoped to dimensions**:
```toml
schema_version = 2

[skill]
id = "mypack:void_attunement"
dimensions = { only = ["minecraft:the_end"] }   # exists/levels only in the End
# or: dimensions = { except = ["minecraft:overworld"] }
```
- **Scoped existence:** the skill's XP sources only fire, and its GUI entry only shows (greyed with a note elsewhere), in the allowed dimensions.
- **Scoped effects:** its grants apply only while the player is in-scope (compiled as an implicit shared `conditions` predicate on its outputs). Leave the End → bonuses switch off; return → back on.
Pairs naturally with stage-gated, dimension-driven packs (your ProgressiveStages style). Fully documented.

### 28.5 Skill Decay / Upkeep (optional, opt-in)
For hardcore/attrition packs, a skill can slowly **decay if unused**:
```toml
# Fragment (schema v2): append to a skill definition.
[decay]
enabled = true
clock = "active_play_ticks"   # active_play_ticks | server_game_ticks | wall_clock
grace = 72000                 # clock units; example = 1 active-play hour
rate = 50                     # XP lost per configured interval
interval = 24000
floor_level = 5                # never decays below this level
max_catchup_intervals = 7
```
Default decay uses accumulated **active play ticks**, avoiding `/time set`, sleep, downtime, and system-clock rollback surprises. Server-game and wall-clock modes are explicit alternatives with clock-rollback handling and capped catch-up loss. Decay updates through transactions, defines its effect on run-accounted XP, and previews dependency cascades. It is off by default.

### 28.6 Prestige / Rebirth (designed-in now)
A first-class, marquee **Creator 1.1** progression feature whose state/lifecycle is designed into Core migrations now:
```toml
schema_version = 2

[prestige]
id = "mypack:rebirth"
enabled = true
requires = { global_level = 100 }        # or per-skill/all-skills-at-cap
resets = ["skills", "currencies", "node_ranks"] # resolved by the explicit reset matrix
keeps   = ["classes"]                     # what survives
[[prestige.persistent_rewards]]
id = "mypack:rebirth/luck"
type = "attribute"
attribute = "minecraft:generic.luck"
operation = "add_value"
value_formula = "prestige_level('mypack:rebirth') * 1.0" # +1 luck per rank in this track
[[prestige.transition_rewards]]
id = "mypack:rebirth/hook"
type = "kubejs"
key = "mypack:prestige_reward"            # namespaced trusted-script hook id
lifecycle = "on_prestige"
repeat_policy = "once_per_prestige"
```
- A named prestige state is separate from schema/data version.
- On prestige, chosen state resets transactionally. Persistent rewards scale from prestige state and project like other persistent grants; transition rewards use a prestige-scoped receipt/outbox and their declared crash-delivery semantics.
- Persistent attributes/multipliers/titles/stages and transition hooks/rewards use only lifecycle-compatible output types.
- Prestige is shown in the Character GUI header and on `/pskills top` (prestige-then-level sort).
- Fully documented, including the reset/keep matrix and reward-scaling examples, plus a validate warning if `requires` is unreachable.

---

## 29. Developer Experience, Observability, Error UX & Accessibility

### 29.1 Observability & Debug Tooling
- **`/pskills debug <player>`** (op) — dumps: every skill (earned/effective level, dynamic cap source, XP/bank/next), named currency balance/reservations, held classes + occupied slots, owned nodes/abilities/spells, active toggles, **every active attribute modifier with its source id and current value**, orphans, rested/decay timestamps, prestige level, and active contextual (`conditions`) grants.
- **"Why is my stat this value?" breakdown** — in the Skills/Classes GUI detail panel, an optional expandable list per affected attribute showing each contributing modifier, its source (skill/node/class/ability/curio/**foreign mod**), operation, and amount, ending with the vanilla-computed final value (the §22 worked math, live, for this player). Answers "why do I have 34 hearts" with a button.
- **XP-gain log** — `debug_logging` (or `/pskills debug xp <player> on`) streams each XP award with source action + amount + resulting level to the log, for tuning curves.
- **`/pskills diagnose <integration>`** — per-integration status (ISS, PS, Curios, KubeJS, FTB Quests, Patchouli, Jade): loaded y/n, hooks resolved, def-reference counts, and any degraded features.

### 29.2 Error UX Philosophy (invalid content cannot replace known-good state)
- **A broken definition/config is contained and cannot replace the last valid snapshot or silently corrupt progression.** One structurally invalid grant rejects its owner in staging, and the default runtime publish then rejects the **entire staged snapshot**. A pack may explicitly mark an optional integration branch with `missing_policy = "skip_declared_branch"`; only that named branch may be omitted. Explicit partial-pack mode validates dependency closure and impact before publish. Unexpected engine/JVM/mod faults are still real faults and are logged/reported honestly—“never crash under any condition” is not a credible guarantee.
- **Where each audience sees problems:**
  - *Pack dev:* clear `[ProgressiveSkills]`-prefixed log lines at load; the `/pskills validate` report (errors + sanity warnings, §27) with file+line+fix; an **op chat summary on dry-run/publish** ("staged 12 skills, 3 trees; 2 warnings — run /pskills validate" / "published generation 42"); a toast to ops on join if the last load had errors.
  - *Player (non-op):* never a stack trace or a crash. At most a generic, friendly message if they touch something misconfigured ("That skill isn't available right now."). Broken bits are silently inert, not exploding.
- **Escalation ladder:** declared optional branch fallback → warn (sanity/degraded optional integration) → error+reject definition (structural) → error+reject dependent pack where manifest policy requires atomicity → keep last-known-good live set. The mod process still loads, but it never pretends a half-valid power definition is fine.
- **Validate-before-apply on reload:** runtime `/pskills reload --publish` is whole-snapshot atomic by default: any blocking error keeps the complete old registry. Partial startup leniency or partial-pack publish exists only as an explicit manifest/operator mode, requires dependency-closure validation plus an impact report, and never silently substitutes a valid subset for live content.

### 29.3 Accessibility & Internationalization
- **Built-in mod text** uses normal `assets/progressiveskills/lang/*.json` resources. **Dynamic pack text** uses the explicit `PsLocaleResolver` (§33.4): synced pack-locale tables are not falsely injected into Minecraft's resource-loaded `Language` map. Client UI resolves against its selected language/fallback; server chat resolves a safe literal Component per recipient using that player's reported locale. A declared/accepted resource pack may provide real translation keys, but raw keys are never shown when it is absent.
- **Narrator/screen-reader:** because the GUI uses vanilla `Screen`/widgets (§17), widgets expose `Component` messages the vanilla narrator reads; we set meaningful narration messages on tabs, buttons, list entries, and node/class states ("Cleave, available, costs 3 points"). Focus order is logical for keyboard/narrator traversal.
- **Colorblind-safe state cues:** tree/class states (owned/available/locked) are conveyed by **shape/icon + text label**, not color alone — the vanilla slot frame, the vanilla highlight, and the vanilla greyed sprite differ structurally, and each state carries a text chip ("Locked"). Nothing relies solely on red-vs-green.
- **Keyboard navigation:** every custom list/graph/canvas implements explicit focus traversal; tree arrows move parent/child/sibling, focus is restored after tab/reload/dialog changes, and all actions work without a mouse. Controller support is a tested adapter/capability, not assumed from vanilla widgets.
- **GUI scale & text-background opacity:** inherited from vanilla (§17); text remains legible at all GUI scales and with the accessibility text-background setting on.
- **Reduced-motion consideration:** level-up toasts use the vanilla toast system (respecting vanilla's own timing); XP floaties and particle bursts are individually toggleable in `[gui]` for players who prefer minimal motion.
- **Player-owned controls:** large-text/reflow mode, high-contrast and color-vision presets, reduced motion/flashing/shake, toast duration, tooltip delay, scroll speed, hold-vs-toggle, sound captions/visual equivalents, HUD opacity/scale, and notification batching are client choices and cannot be server-locked.
- **Authoring validation:** themes/defs warn on missing alt/narration text, color-only state, insufficient contrast, fast flashing, overflow at long translations, broken right-to-left layout, and absent asset fallbacks.



---

## 30. Normative Worked Fixtures — Core First, Integrations Isolated

*The reference implementation is a suite of small dependency-honest fixtures, not one impossible mini-pack. Each fixture names its release and required capabilities, validates independently, and is reproduced in generated docs/tests.*

**`progressiveskills:core_fixture` — Core 1.0, no optional mods:**

- `physique` linear skill with exact Core curve semantics, stable XP rules, threshold/scaling attributes, milestone feedback, and a named point currency.
- `craft` tree with acyclic single-rank prerequisites, exact historical-cost refund, and a vanilla attribute/ability reward.
- `warrior` and `scholar` classes in an explicit `combat` slot, a source-aware synergy, and receipt-protected carrier starter kit.
- `builder_reach` toggle plus one charge/cooldown-based active ability assigned to a fixed action slot; no custom resource meter is required by Core.
- `tome_of_physique`, respec token, and level token carrier stacks with pinned behavior versions and pending-claim delivery.
- Tests: death copy, relog/reconcile, stale generation, respec/refund, duplicate request, inventory-full claim, and absent-optional-mod dedicated boot.

**Separate manifest-gated fixtures:**

- `progressiveskills:creator_fixture` — Creator 1.1 formulas/templates, contextual grants, prestige, decay, loadouts, themes, import/rebase, simulation.
- `progressiveskills:iss_fixture` — exact pinned ISS range; cast XP, virtual selection versus learned requirement, mana/school attributes, channelled active spell.
- `progressiveskills:stages_fixture` — exact pinned ProgressiveStages ownership/revoke capability and fallback modes.
- `progressiveskills:curios_fixture` — exact pinned equipment provider; equipped carrier invalidation/subtype behavior.
- `progressiveskills:multiplayer_fixture` — 1.2 party attribution, privacy, seasons, and rollover.

Only the Core fixture is manifest-enabled on first launch. Optional fixtures remain disabled examples until their dependency/version contract passes, so a clean install never loads dangling Mining/Curios/ISS/template/prestige references.

---

## 31. Authoritative Feasibility Contracts

These contracts prevent the plan from promising behavior Minecraft/NeoForge cannot safely provide. They are release-blocking.

| Area | Contract |
|---|---|
| Registries | Items, blocks, menus, data components, key mappings, particle types, etc. are registered during startup. Runtime content configures pre-registered generic types or references existing registry entries; it never hot-invents registry ids. |
| Dynamic items | Pack-defined carrier `ItemStack`s (within hard definition/state ceilings) are identified by a synced/persistent data component (§9.5), with models supplied by installed/server resource packs. |
| Ability input | A fixed set of client-startup key mappings drives an action wheel/bar/slots. Server definitions never register arbitrary Controls entries after join/reload. |
| Character GUI | Use an ordinary `Screen` plus intent packets. Use `AbstractContainerScreen` only where an actual registered menu and slot-holding data owner exist. |
| Definitions | Dedicated-server definitions are authoritative and Core 1.0 syncs a redacted client projection. Clients never read the server filesystem. |
| Packets | Respect clientbound/serverbound payload limits, bound collection/string sizes before allocation, chunk large projections, cap decompression, rate-limit intents, and reject stale revisions. |
| Reload | Startup listeners remain registered; hot reload atomically swaps immutable compiled route tables. Runtime subscribe/unsubscribe behavior is not assumed. |
| Effects | Recompute is only for durable/ref-counted effects. Transition actions and currency mutations run in transactions with stable ids and receipts (§35). |
| Optional mods | No optional class appears in the constant pool of always-loaded code. Guard, then load an isolated compat entry point; test dedicated server with every optional dependency absent. |
| Attachments | Serialized player attachments use explicit death-copy/clone semantics and manual sync. Mutating a retrieved mutable attachment must follow the correct dirty/save path. |
| Offline players | Queue an operation in server `SavedData` and apply it on login; never claim that an unloaded entity attachment was mutated live. |
| Config roots | Common/client/server/world authority and reloadability are explicit. `SERVER`-style world overrides and definition roots are not confused with local client config. |
| Stages | Revoke only when the integration can distinguish ProgressiveSkills ownership from other grantors. Otherwise use grant-only/fallback policy. |
| ISS spells | Call these **virtual/source-granted spells**, not inherently permanent. Availability ends when the final owning source disappears; effective spell level has a resolver. |
| Guidebooks | Native encyclopedia is canonical. Patchouli output follows the capabilities/reload behavior of the pinned Patchouli version; it is not magic server-to-client file generation. |
| Clamps | Default clamps bound ProgressiveSkills' contribution. A true final-value clamp would require invasive enforcement and can conflict with foreign modifiers, so it is separate, opt-in, and loudly documented. |
| Controller | Keyboard/focus accessibility is implemented and tested. Controller support is never inferred automatically from using vanilla widgets. |

**Pinned dependency policy.** Each optional integration has: a tested minimum/maximum version, API symbols used, fallback behavior, smoke GameTest, and `/pskills diagnose` resolver report. “Reflective” is an implementation technique, not a substitute for a compatibility contract.

---

## 32. Content Packs, Manifests, Roots & Layering

The unit of distribution is a **ProgressiveSkills content pack**, not a loose pile of unrelated TOML files.

### 32.1 `pack.toml`

```toml
schema_version = 2

[pack]
id = "mypack:core"
namespace = "mypack"
name = { key = "pack.mypack.core", fallback = "My RPG Progression" }
content_version = "3.2.0"
engine = ">=1.0.0 <2.0.0"
authors = ["Pack Team"]
license = "All-Rights-Reserved"
priority = 100
default_locale = "en_us"
default_theme = "mypack:dark_rpg"
default_layout = "mypack:character_default"

[dependencies]
required_packs = ["progressiveskills:base@>=1.0.0"]
optional_packs = ["mypack:magic"]
required_mods = []
optional_mods = [] # an ISS fixture adds its exact bounded tested range; never an open-ended future version
incompatible_mods = []

[policies]
missing_required = "reject_pack"
missing_optional = "skip_declared_branch"
unknown_field = "error"
duplicate_id = "error"
merge_conflict = "error"
secret_projection = "redact"
```

The manifest also records homepage/source, description, changelog URL, namespace ownership, feature flags, exported asset pack id, and whether trusted scripts are present.

#### 32.1.1 Canonical optional-integration branches

Every optional dependency has a manifest alias containing mod/provider id, **bounded tested version range**, test-profile id, and missing default. A skippable branch then names that alias/capabilities and enumerates typed stable members; no grant is silently inferred to be optional by its namespace.

```toml
# Fragment (schema v2): `iss` is an optional dependency alias already declared in this pack manifest.
[[integration_branches]]
id = "mypack:iss_features"
dependency = "iss"
required_capabilities = [
  "irons_spellbooks:spell_selection",
  "irons_spellbooks:learned_requirement"
]
missing_policy = "skip_declared_branch"
degraded_policy = "reject_pack"
client_visibility = "hide"
members = [
  { kind = "grant", owner_kind = "class", owner_id = "mypack:mage", id = "mypack:mage/fireball" },
  { kind = "ability", id = "mypack:arcane_surge" }
]
```

Members may be whole definitions or nested `GrantSourceId`s. A reference into an optional branch must be in the same/compatible guarded branch or declare a typed fallback; otherwise it is a dangling-reference error. Overlapping branches with incompatible policies, an unbounded dependency version, missing member ids, or a `skip_declared_branch` that would violate owner schema are staging errors. `disable_def` disables the explicitly named whole definition; `hide` is presentation-only and cannot make unsafe server behavior valid.

### 32.2 Load roots and precedence

Lowest to highest precedence:

1. Engine fallback definitions (minimal and normally disabled).
2. Installed mod/addon-provided content packs.
3. Global modpack packs under `config/progressiveskills/packs/`.
4. World packs/overlays under `world/serverconfig/progressiveskills/packs/`.
5. Operator-published Studio overlay.
6. Ephemeral runtime API overlays (never silently persisted; shown in diagnostics).

Client presentation preferences are a different axis and never override server gameplay. Accessibility settings always remain player-controlled.

### 32.3 Authoring adapters

- **TOML:** default human-authored format and first 1.0 adapter.
- **Datapack JSON:** 2.0 adapter using the same codecs/IR; participates in datapack ordering and `/reload` semantics.
- **KubeJS builders:** programmatic authoring that emits typed builder objects, not unvalidated raw maps.
- **Java provider API:** addons contribute definitions/types through the same compiler.
- **Studio:** writes a staged TOML/JSON overlay with source spans and revision history.

Adapter parity is tested: equivalent TOML/JSON/builder fixtures must compile to the same **semantic projection/digest after excluding adapter-specific source spans and provenance**. Provenance/source-map correctness is tested separately; raw objects need not be byte-equivalent.

### 32.4 Resolution pipeline

`discover → parse → bounded envelope/template-AST validation → manifest/dependency resolve → derive ids → resolve/expand typed templates → merge/patch → full schema/type validation → resolve aliases/references → compile formulas/predicates → graph checks → integration checks → balance/security/accessibility diagnostics → client redaction → digest → impact diff → atomic publish`

Every step preserves source spans and provenance. The live registry is immutable. Reload builds an entirely separate staging registry; only a successful whole-snapshot publish crosses the definition-generation barrier and reconciles every online player before new routes reopen (§35.7).

### 32.5 Merge semantics

No ambiguous “last file happened to win.” Every collision declares one of:

- `add` — target must not exist.
- `replace` — replace the whole definition, with optional expected old digest.
- `merge` — merge named fields/maps; list operations must be explicit.
- `patch` — path-addressed `set`, `remove`, `append`, `prepend`, `replace_by_id` operations.
- `disable` — retain identity/migration visibility but make it unavailable.

All nested collections merge by stable id, never array position. `/pskills info --provenance` reports the exact pack/file/template/patch that supplied every resolved field.

### 32.6 World lockfile and last-known-good

On publish, store `progressiveskills.lock` metadata in world data: pack ids/versions, source digests, engine/schema version, optional-mod versions, final content digest, and timestamp. Metadata alone cannot recover edited/deleted sources, so the same publish atomically writes a checksummed, quota-bounded canonical source bundle plus an optional versioned compiled-IR cache and retains the previous successful generation. Write temp → fsync/close → checksum → atomic replace where the platform supports it, with recoverable journal markers.

A changed digest produces an impact report before mutation. Startup may recover the last-known-good bundle only after checksum, schema/compiler, registry, and optional-capability lock validation, then recompiles/verifies its semantic digest. An incompatible/corrupt LKG enters a safe disabled mode with progression mutations blocked and clear recovery/export commands; it is never decoded optimistically.

### 32.7 `.pspack` bundle

A portable bundle may contain manifest, definitions, locales, themes/layouts, tests, generated docs/schema, and an **exportable** resource-pack payload. Import is preview-first and supports overlay/replace/namespace-remap. Enforce zip-slip protection, path/size/count limits, checksums, dependency checks, script warnings, and signature metadata. Player/world state is never mixed into a content bundle.

Importing a `.pspack` does not magically deploy client assets. The manifest records required/optional resource-pack id, content digest, minimum asset contract, and fallback theme. Publish requires the operator to point at an installed local pack or configure a server resource-pack URL + verified hash through Minecraft's normal mechanism. Studio shows `available / not configured / declined / failed / digest mismatch` preview states. If a client lacks or declines optional assets, it uses `progressiveskills:vanilla`; gameplay definitions/costs never change with asset acceptance.

---

## 33. Stable Identity, Reuse, Inheritance & Localization Primitives

### 33.1 Identity rules

- **Exact derivation:** for `<pack>/skills/combat/physique.toml`, remove the type directory (`skills/`) and extension, normalize `/`, and prefix the manifest namespace → `mypack:combat/physique`. Likewise `trees/warrior_tree.toml` → `mypack:warrior_tree`. The type folder is not part of the `ResourceLocation`; the canonical key is `(DefinitionKind, ResourceLocation)`, and reference fields are typed. An explicit top-level `id` must equal this derived id or staging fails.
- A nested stateful id is a full `ResourceLocation`, conventionally owner path + local path: node `cleave` inside `mypack:warrior_tree` is `mypack:warrior_tree/cleave`; its damage grant is `mypack:warrior_tree/cleave/damage`. The compiler can suggest this value but never persists an array index.
- Two kinds may technically share a `ResourceLocation` because their canonical keys include kind, but validation warns since it harms readability/provenance.
- Every nested stateful object requires an `id`: XP source, grant, condition branch if it holds memory, scaling entry, level milestone, node rank, synergy, trigger, cost, conversion edge, prestige reward, challenge objective, notification.
- Renames use a pack-level `replacements.toml` mapping old full id → new full id for **all** kinds. Chained/cyclic/ambiguous replacements fail validation.
- Display strings, file names, list indexes, row/column, and icons are never persistence identity.
- Generated ids are allowed only for stateless decoration; validation warns if a stateful entry lacks an explicit id.

### 33.2 Reusable primitive definitions

Packs can define and reference:

- `variables` / balance constants
- `curves`
- `predicates`
- `requirements`
- `cost_bundles`
- `grant_bundles`
- `notification_profiles`
- `component_specs` (localized text)
- `icon_specs`
- `item_stack_specs`
- `targeting_profiles`
- `anti_exploit_profiles`
- `theme` and `layout` fragments

This prevents 100 copied blocks from drifting.

### 33.3 Templates, inheritance, mixins, parameters

```toml
schema_version = 2

[template]
id = "mypack:base_skill"
abstract = true
target_kind = "skill"

[[template.parameters]]
id = "mypack:base_skill/skill_display"
name = "skill_display"
type = "component"
required = true

[[template.parameters]]
id = "mypack:base_skill/base_xp"
name = "base_xp"
type = "integer"
default = 100
min = 1
max = 1000000000

[prototype.skill]
display = { param = "skill_display" }
max_level = 100

[prototype.curve]
type = "polynomial"
base = { param = "base_xp" }
coefficient = 15
power = 2
```

An instance supplies typed arguments without string interpolation:

```toml
schema_version = 2

[skill]
id = "mypack:mining"
extends = "mypack:base_skill"
arguments = { skill_display = { key = "skill.mypack.mining", fallback = "Mining" }, base_xp = 125 }
```

Definitions support one same-kind `extends` parent plus ordered `mixins`. Diamond conflicts must be resolved explicitly. Parameter references are typed nodes (`{ param = "..." }`), so an integer cannot become a quoted string and arbitrary helpers such as `title()` do not exist. Presentation casing/style belongs in locale/`ComponentSpec`. Template expansion has depth/count limits and cannot access files/environment/network. `for_each` generation is permitted for simple tiers/level ranges but every expanded stateful object gets a predictable stable id visible in `info`/docs.

### 33.4 Shared presentation types

```toml
# Fragment (schema v2): shared presentation-field shapes.
display = { key = "skill.mypack.physique", fallback = "Physique" }
description = { key = "skill.mypack.physique.desc", fallback = "Raw physical conditioning." }
icon = { type = "item", value = "minecraft:iron_chestplate", fallback = "minecraft:barrier", alt = "Iron chestplate" }
```

`ComponentSpec` supports pack-locale key + fallback, simple `&` shorthand, typed placeholders, and a bounded safe JSON Component subset. Synced JSON forbids selector/NBT interpretation and command/file/URL click actions by default; only allowlisted styling, hover text, and trusted server-generated interactions survive sanitization. `IconSpec` supports item stack, block, texture/atlas sprite, player head/profile, entity preview (explicit opt-in), cycling tag, or composite badge—with narration/alt text and fallback required. `SoundSpec`, `ParticleSpec`, `VisibilitySpec`, and `StyleSpec` are likewise shared.

Pack locale files support fallback chains, search aliases, ICU-inspired **bounded** plural/select cases (no arbitrary MessageFormat execution), placeholder type checking, localized numbers, narration, and right-to-left metadata. `PsLocaleResolver` chooses exact locale → configured parent → pack default → `en_us` → `ComponentSpec.fallback`; client UI resolves locally, while recipient-specific server messages resolve on the server. Validation catches missing/unused keys, placeholder mismatch, unsupported glyphs, unsafe component events, and likely overflow.

### 33.5 Generated schema as the single source of truth

`SchemaRegistry` metadata generates:

- parsers/codecs and validation constraints
- reference documentation tables
- native encyclopedia/editor help
- Studio fields/widgets
- command suggestions
- TOML/JSON editor schemas and snippets
- diff semantics/default elision
- client projection/redaction rules
- test parameter catalogs

This eliminates stale claims such as “all 11 outputs” after a twelfth type is added.

---

## 34. Requirements, Conditions & Safe Formula Language

### 34.1 One boolean requirement/predicate AST

All skill unlocks, node prerequisites, class selection, ability targeting, contextual effects, XP rules, item use, challenges, prestige, and visibility use the same tree:

```toml
schema_version = 2

[predicate]
id = "mypack:nether_ready"
all = [
  { type = "skill_level", subject = "actor", missing = false, skill = "mypack:physique", op = ">=", value = 10 },
  { any = [
      { type = "dimension", subject = "actor", missing = false, value = "minecraft:the_nether" },
      { type = "stage", subject = "actor", missing = false, value = "mypack:nether_attuned" }
  ]},
  { not = { type = "effect", subject = "actor", missing = false, value = "minecraft:weakness" } }
]
```

Every leaf declares a subject (`actor`, `target`, `owner`, `tool`, `item`, `event_position`, `party`, `world`) and missing-context policy. Built-ins include:

- progression: skill/class/node/rank/ability/prestige/currency/resource/loadout/discovery
- vanilla: advancement, recipe, scoreboard, team, permission, gamerule, difficulty, game mode
- world: dimension/biome/structure tags, Y range, light, sky, weather, time, moon, season
- entity: id/tag, health/resource %, effect, equipment/item component, enchantment, entity data exposed by safe providers
- event: damage type/amount, critical, spell/school/level, block/item/recipe, spawn reason, distance, result success
- social: party size/role, contribution, level gap, guild/team state, privacy/consent
- integration/provider predicates.

Reusable named predicates are inlined/compiled with cycle detection. Context-sensitive secrecy allows `hidden`, `silhouette`, `hint`, or full failed-requirement disclosure.

### 34.2 Safe expression language

Formulas are not JavaScript and cannot reflect, allocate unbounded collections, loop, recurse, read files, call commands, or access arbitrary NBT. The grammar supports numeric literals, single/double-quoted literal ids, typed variables, arithmetic/comparison/boolean/ternary operators, and a small whitelist: `min`, `max`, `clamp`, `abs`, `floor`, `ceil`, `round`, `sqrt`, `pow` with bounded exponent, `lerp`, and named curve lookup. Identifier-taking accessors require a compile-time literal, never a computed string.

Useful variables include `base`, `level`, `highest_level`, `lifetime_xp`, local `prestige`, `node_rank`, `class_rank`, `difficulty`, `party_size`, `target_max_health`, `actual_damage`, `spell_level`, `block_hardness`, `recipe_value`, `resource_percent`, and pack constants. Bounded typed accessors such as `skill_level("mypack:physique")`, `currency_value("mypack:favor")`, `resource_percent("mypack:rage")`, and `prestige_level("mypack:rebirth")` take literal ids, expose explicit missing-target fallback, and register dependencies at compile time. Providers may contribute similarly typed variables/functions with a cost classification.

```toml
# Fragment (schema v2): formula-bearing numeric field group.
amount_formula = "clamp(base * (1 + 0.08 * level) * difficulty, 0, 5000)"
rounding = "floor"               # floor | ceil | nearest | bankers
fractional_carry = true
```

The compiler performs type checks, constant folding, range/overflow analysis, dependency extraction, operation-budget estimation, and fixed-point lowering. Divide-by-zero, NaN-equivalent, overflow, missing variables, and out-of-budget formulas fail staging or use an explicit fallback.

### 34.3 Efficient reevaluation

Predicate dependencies determine their watcher:

- state changes (skill/node/class/resource) invalidate immediately;
- equipment/effect/dimension/biome/combat events invalidate on matching NeoForge events;
- time/weather/light/health thresholds use configurable bucketed checks;
- expensive structure/provider queries are cached with TTL and strict budgets.

Only players with an active predicate depending on a signal are watched. Thresholds support hysteresis/debounce (`enter_below=0.25`, `exit_above=0.30`) so buffs do not flap every tick.

### 34.4 Randomness

Weighted choices use the server RNG inside a transaction and record chosen branch + seed token in the audit/receipt. Previews show probabilities/ranges. Persistent recompute cannot contain random selection unless the selection was previously materialized as state.

---

## 35. Progression Transactions, Output Lifecycles & Ownership

### 35.1 Transaction pipeline

Every mutation—XP award, node purchase, class change, ability use, conversion, decay, prestige, admin command, pending offline operation—uses:

1. **Capture** actor/target/cause/origin, state revision, definition digest, event context, idempotency key.
2. **Plan** resolve matchers/conditions/formulas, costs, dependency cascades, outputs, delivery, expected before/after values.
3. **Validate** permission, revision, caps, affordability, anti-exploit, cooldown, target, inventory/delivery, cascade/loop budget.
4. **Commit state** atomically on the server thread using checked arithmetic.
5. **Project persistent effects** and source-aware entitlements.
6. **Execute transition actions** in deterministic order; isolate/record failures according to declared atomicity.
7. **Persist/audit/sync** revisioned deltas, receipts, and player/admin feedback.

No nested mutation applies immediately. A native value-bearing child must already belong to the fully expanded/reserved `CascadePlan`; an unexpected opaque value child is rejected before its own commit. Non-value children queue with the origin chain and drain after the parent within coalescing feedback budgets (§23).

### 35.2 Lifecycle matrix

| Lifecycle | Meaning | Typical outputs | Recompute? | Receipt? |
|---|---|---|---|---|
| `while_eligible` | Active exactly while all owning source/conditions hold | attribute, owned stage/spell/ability/flag/access, PS virtual recipe gate | Yes | No |
| `on_gain` | Fires when a source becomes owned/eligible | message, sound, item, effect, function | No | Usually |
| `on_loss` | Fires when a source stops being owned | message, cleanup action | No | Optional |
| `on_enter` / `on_exit` | Context condition edge | effect/action/notification | No | By edge epoch |
| `on_first_reach` | First time player reaches milestone | kit, title, stage reward, currency | No | Required |
| `on_each_reach` | Each legitimate upward crossing | feedback/repeat reward | No | Crossing epoch + anti-farm policy |
| `on_purchase` / `on_refund` | Node/class transaction edge | rewards/cost/refund hooks | No | Transaction id |
| `on_character_creation` | First successful character/profile initialization only | starting kit, initial class/loadout, welcome reward | No | Required creation transaction + grant receipts |
| `on_activate` / `on_trigger` | Ability/rule execution | typed action graph | No | Cooldown/idempotency dependent |
| `on_use` / `on_prestige` / `on_season_end` | Explicit carrier/progression transaction edge | typed actions/rewards | No | Required for value delivery |
| `timed` | Materialized effect/entitlement until expiry | effect, temporary grant, resource modifier | Scheduled state | Expiry record |

Validation rejects nonsensical combinations (for example `item + while_eligible`).

Edge-producing sources persist the prior predicate/source state and an edge epoch. Initial login/load, definition publish, migration, reconcile, and resync default to **initialize without firing**. Each transition action may allow causes from `gameplay`, `character_creation`, `admin`, `migration`, `reload`, `reconcile`, or `season`; value-bearing edge rewards default to eligible gameplay only, while creation outputs default solely to the creation transaction, always with the required receipt.

Vanilla recipe-book knowledge has no grantor provenance. A native vanilla `recipe` unlock is therefore sticky `on_gain` by default and is never automatically revoked; an explicitly destructive revoke requires a warning/confirmation. Packs needing reversible access use a PS-owned recipe gate/condition provider without deleting independently learned vanilla knowledge. The same capability rule applies to stages, advancements, permission nodes, or any foreign boolean entitlement: `while_eligible` requires a tested ownership/revoke capability. A sticky/`grant_only` fallback is an irreversible, receipt-protected transition—not a persistent projection—and validation rejects it inside a normally refundable owner unless the pack explicitly accepts irreversible-on-respec behavior and presents that fact before purchase.

Timed materialization uses `TimedInstanceKey(EntitlementKey, GrantSourceId, stackSlotOrActivationId)`. Each definition chooses `refresh`, `extend` (bounded), `replace_if_stronger`, or `stack(max_stacks, resolver)` plus online/offline clock and pause rules. Repeated activations can neither overwrite an unrelated source nor create unbounded timer entries; expiry removes only that instance and recomputes the resolved entitlement.

**Canonical authoring-to-IR compilation:** the immutable IR always has an explicit `lifecycle`; the following named containers are the only lifecycle sugar. `levels.effects`, `scaling`, class/node/synergy `grants`, and ability `persistent_effects` compile to `while_eligible`. `levels.rewards` must state its lifecycle (normally `on_first_reach`/`on_each_reach`). A starting profile's `character_creation.outputs` compiles only to `on_character_creation` and requires creation-scoped permanent receipts. Ability `actions`, rule `outputs`, carrier `use_actions`, and prestige `transition_rewards` compile to `on_activate`, `on_trigger`, `on_use`, and `on_prestige` respectively. Containers reject incompatible output types. `repeat_policy` is the sole v2 spelling; v1 `repeat` is a migration alias and never appears in generated examples.

### 35.3 Repeat and receipt policy

Transition outputs declare one of `always`, `once_per_character`, `once_per_prestige`, `once_per_season`, `once_per_source_epoch`, `once_per_target`, `once_per_transaction`, or a named reset policy. `ReceiptKey(GrantSourceId, policyScopeId)` therefore distinguishes owner kind/id/grant and stores many bounded target/season/prestige epochs. `GrantReceipt` records definition digest/version, timestamp, transaction, delivery/outbox result, and chosen random branch.

Receipt ids survive file reorder/reload. Renamed grants require an alias; deleting/recreating an id does not silently reset claims unless an operator explicitly clears receipts. `once_per_character` and other still-live permanent tombstones are **never evicted** by age/count retention; they may only be losslessly compacted into a membership structure with collision-free proof or archived in a still-consulted ledger. Expiring target/season epochs may be pruned only after their reset domain can never recur. `/pskills prune receipts --dry-run` explains every candidate and cannot reopen a claim silently.

Before a value transaction that needs a new permanent tombstone, reserve exact-ledger capacity. If the configured exact ledger/archive is full, reject the transaction before cost/reward rather than grant without durable claim truth. Bloom/probabilistic filters are never the sole authority for eligibility.

`once_per_target` is not permission to retain arbitrary identities forever. Permanent target receipts are allowed only for a bounded catalog domain (for example, a finite boss-definition id set) or an explicitly capacity-managed exact external ledger. Arbitrary entity UUIDs, block positions, stack fingerprints, and player-generated ids must use a named TTL/reset/season window with a bounded map; when that window or its permanent-ledger reservation is full, the triggering transaction fails closed before charging or rewarding. The validator estimates cardinality and rejects an unbounded permanent policy.

### 35.4 Source-aware ownership

Persistent entitlement model:

```text
EntitlementKey(target type + target id) -> Map<GrantSourceId, EntitlementValue>
```

`GrantSourceId` includes owner definition and stable grant id. Manual/admin sources are separate. Effective value uses a registered resolver: boolean union, highest, lowest, additive, multiplicative, priority, or custom provider. Revoking one class/node never removes an entitlement still provided elsewhere.

### 35.5 Costs, refunds, and dependency invalidation

- Every purchase records **historical paid cost**; refunds never use today's edited cost unless policy explicitly says so.
- Level currencies/rewards declare scope (`character`, `prestige_run`, `season`). The safe Creator default is `run_highest_level` with prestige-scoped receipts; lifetime rewards use `lifetime_highest_level`. This prevents both relevel farming and accidentally suppressing the next prestige run.
- One typed economy graph covers native conversions, salvage, recipes/stations, trades, carrier use, kits, refunds, rules, quests, and challenges. Command/KubeJS/provider edges are marked opaque, so validation reports “economy unproven” rather than claiming loop freedom.
- When level/cap/class/pack changes invalidate a dependent purchase, each scope selects: `suspend`, `cascade_refund`, `grandfather`, or `block_causing_change`.
- Destructive operations show a full transitive preview: disabled/removed nodes, classes, abilities, stages, stats, exact refunds, item rewards not clawed back, and orphaned state.

Every progress mutation also carries a typed `ProgressCause` and a separately computed `RewardEligibility`; changing a level and earning a milestone are never treated as the same operation. The safe defaults are:

| Cause/origin | Change persistent progress? | Highest-basis/milestone transition eligibility |
|---|---:|---|
| ordinary gameplay XP | Yes | Normal declared policy and receipts |
| `character_creation` | Yes | Only explicit creation/start-kit grants |
| explicit admin grant | Yes | Off unless the command/provider opts in and previews it |
| transfer/conversion/sacrifice refund/level token | Yes | **No** by default; an edge must opt in and enter loop simulation |
| migration/reconcile/reload/profile or curve change/cap release | Yes | Never implicitly; optional separate receipt-protected remediation grant |
| decay/death/mandatory season reset | Yes | Downward effects and explicit compatible `on_loss` only |

This matrix governs transition rewards and `run_highest_level`/`lifetime_highest_level` awards. It does **not** exempt a `current_level` live budget from rebalancing: that budget follows `effectiveLevel` on every cause, including admin/token/transfer/migration/profile/cap suppression or release. Transfer moves accounted XP and simultaneously removes/adds any live source budgets; it does not convert the same conserved value into fresh earned crossings in the destination. `on_each_reach` consumes a monotonic crossing epoch scoped to eligible gameplay, so A→B→A cannot mint rewards. The economy solver includes stateful milestone, point-award, refund, current-level entitlement, cap-release, and receipt-reset edges—not only exchange rates—and refuses a claimed loop-free status when an opaque edge can re-enter progression.

Every level transition grant has `LevelRewardProgress` keyed by typed `GrantSourceId(ownerKind, ownerId, grantId)` + character/prestige/season scope epoch (plus target dimension for a target-scoped crossing policy). An upward change processes **each crossed edge even when rewards are ineligible**. Highest/first policies advance a monotonic `processedThrough`; delivered edges also advance `rewardedThrough`, while an ineligible jump inserts its exact edge interval into a losslessly coalesced, hard-cap-bounded `suppressedEdges` set and stores cause/transaction. `on_each_reach` additionally consumes a monotonic crossing epoch, but an edge in `suppressedEdges` cannot become eligible merely by deleveling and re-crossing it. Thus an admin/token/transfer jump 0→10 followed by gameplay 10→11 can pay only edge 11, never ten deferred rewards; A→B→A and jump→delevel→relevel remain closed. Suppressed rewards are payable only through an explicit previewed, receipt-protected remediation command that updates this ledger. Generic numeric peaks alone are never used to infer “missing rewards.”

`award_basis = "current_level"` is a source-tracked live budget whose exact entitlement is recomputed from `effectiveLevel` on **every** change. It is not a one-shot reward and cannot use “preserve earned.” Each source stores its prior entitlement; reconciliation posts only the checked delta, so suppress→release restores the same budget rather than minting a new crossing. It requires `delevel_policy = "debt" | "cascade_refund" | "block_voluntary"` plus a mandatory fallback. If awarded currency was spent, `debt` reserves future income and `cascade_refund` unwinds reversible purchases in deterministic order. `block_voluntary` may reject only an elective respec/transfer/profile switch; it never immunizes against death, decay, moderation, season rollover, migration, or another mandatory change, which uses the declared fallback. A preview shows entitlement deltas, debt, suspended purchases, cascade order, and unclawable outputs. Highest-level award bases and milestone history instead read eligible earned crossings, not cap suppression/release.

### 35.6 Transition failure and delivery

State-changing native outputs are planned before commit. Value delivery uses a durable outbox/claim record keyed by transaction id; delivered stacks/claims carry that id where possible, and monotonic claim tombstones are preserved across ordinary snapshots. Because player inventories, containers, dropped entities, SavedData, mail providers, commands, and scripts do not share one atomic disk commit, the plan does **not** promise universal exactly-once behavior across a process crash. Native pending-claim delivery targets effectively-once retry semantics; commands/external scripts are explicitly `at_least_once` or `best_effort` and must be idempotent if retries are enabled. Nonrollbackable transactions cannot be undone. Items support `refuse`, `drop_at_player`, `pending_claim`, or provider mail when inventory is full.

### 35.7 Definition-generation publish barrier

Definitions have two revision domains. `definitionGeneration` + `semanticDigest` cover every gameplay-bearing field; `presentationRevision` + `clientPresentationDigest` cover only schema fields proven presentation-only (localized display/description/guide text, icons, theme/layout descriptors, and accessibility metadata). A semantic diff may take the lightweight presentation path only when **every** changed field is marked presentation-only and no provider codec declares gameplay behavior. That path atomically swaps the sanitized presentation projection, invalidates the per-server client cache, and resyncs the new presentation revision without reconciling player state, canceling casts, or pausing routes. Any unknown/ambiguous change escalates to the full gameplay barrier.

Every compiled gameplay registry has a monotonic `definitionGeneration`. A transaction plan, queued child, deferred native action, and client request is pinned to one generation. Publish occurs only at a server-thread safe point after the old generation's end-of-tick action queue drains:

1. stop accepting progression intents/routes and snapshot online state revisions;
2. precompute new desired projections and validate player migrations against the staged registry;
3. at the barrier, recheck revisions, atomically swap the registry, apply every online player's new persistent diff, mark `stateGeneration`, and enqueue the new client projection;
4. reopen routes only after all online players are on the new generation.

If the online-player diff cannot fit the written publish budget, the command refuses live publish and requires a declared maintenance window/freeze; it never leaves new rules running against old attributes/stages. Stale client requests are rejected with an idempotent result/resync. A deferred plan cannot silently execute under another generation: it finishes before the barrier or is canceled/replanned.

On startup/login, a stored `stateDefinitionDigest`/generation mismatch marks that attachment stale; the server migrates and reconciles it before enabling its progression routes, abilities, or client mutation UI.

### 35.8 Snapshot, restore, and undo boundary

- A **definition snapshot** stores pack/overlay sources, semantic compiled digest, manifest/lockfile, and migration metadata. Restoring it changes definitions only, then runs the generation barrier; it never rewinds player rewards.
- An **online-player progression snapshot** requires `/pskills freeze`, captures only loaded attachments plus referenced pending offline-operation metadata, has a target list/digest/retention quota, and restores reversible progression fields. It does not rewind inventories, drops, containers, mail, commands, scripts, or provider databases. Monotonic receipts/claim tombstones and delivered outbox entries are union-preserved, not rolled back; transactions with later nonrollbackable effects block ordinary undo.
- A **maintenance/world backup** is an operator/hosting-level coordinated save or stopped-server filesystem snapshot. PS can request freeze, flush/verify its stores, emit a manifest, and later verify the backup, but does not label a partial attachment export a world backup.

Restore is dry-run first, checks current/target generations and pending operations, refuses future-schema/incomplete sets, records a **new** audit transaction instead of erasing history, and reconciles before unfreeze. It never restores an old `stateRevision` or `stateGeneration`; the result uses the current definition generation and `nextRevision > max(currentRevision, snapshotRevision)` so client/CAS ordering remains monotonic. Default quotas bound count/bytes/age; deleting an old snapshot never deletes permanent receipt truth.

### 35.9 Cross-owner transactions and crash recovery

A transaction touching more than one independently saved authority (two players, player + team, player + world, or a selector batch advertised as atomic) uses a server-local, append-only `CrossOwnerJournal`; ordinary single-owner mutations do not pay this cost. Its durable states are `PREPARED → COMMITTED → FINALIZED/checkpointed`. The coordinator first writes and forces `PREPARED` with transaction id, generation, participants, expected revisions, exact debit/credit legs, expiry, and recovery policy. Journal I/O runs on the bounded storage executor while the operation remains in a non-spendable pending state; after the force completes, the server thread reacquires participants in typed-id order, revalidates every revision, and installs bounded reservations. It then forces the **COMMIT decision before any leg becomes authoritative/spendable**. A timeout/I/O failure leaves a recoverable PREPARED record or fails closed—it never blocks the tick waiting for disk.

After durable COMMIT, the decision is irrevocable: idempotently apply every participant leg and persist a `CommittedLegReceipt(transactionId, legId, resultingRevision)` with that owner. Nonrollbackable transition outputs enter the outbox only from this phase. Credits remain provisional and debits reserved until their committed leg is finalized; participants cannot log in/use the affected scope past recovery with an unresolved committed leg. Only after every leg receipt has been forced/saved does the coordinator append `FINALIZED`, then a retained checkpoint may permit journal compaction.

Startup and participant login scan **all non-checkpointed PREPARED and COMMITTED records**. PREPARED without a commit decision aborts/releases reservations without value mutation. COMMITTED always completes every missing leg from the journal, even if some owners show old state and others already show the result; duplicate leg receipts no-op. If revision/identity invariants cannot be proven, quarantine and freeze the affected scopes for operator recovery—never mint an inverse compensation or silently strand one side. Offline/unresolved participants are rejected before prepare or routed through the explicit offline queue. External economy/team services require their own idempotency/prepare contract (§45.7); the local journal cannot make a remote API atomic.

### 35.10 Character creation and one-time initialization

The first successfully decoded player profile runs one `character_creation` transaction guarded by `CharacterInitializationState(profileId, creationEpoch, transactionId, status)` in the attachment. It selects the configured starting profile/class/loadout, materializes initial currencies/resources, and delivers only outputs explicitly marked `on_character_creation`. The transaction and its receipts are written together; login, clone, retry, reconcile, reload, and profile display are not creation events. An interrupted creation resumes by transaction id before gameplay routes open, so starter kits cannot duplicate and a partially created character cannot play.

Currency/resource `initial` values are once-per-typed-definition-and-scope materialization entries keyed by definition lineage plus character/prestige/season epoch. Editing `initial`, reloading, or deleting/re-adding a definition never tops up existing state. A replacement mapping carries the initialization marker; a genuinely new definition remains uninitialized until the declared policy runs. Retroactive initialization for existing characters is a separate dry-run, impact-previewed admin grant with receipts—not reconciliation. Reset/new-character systems must create a new declared scope epoch rather than erase tombstones.

---

## 36. Generic Rule / XP Engine and Anti-Exploit Surface

An XP source is sugar over the generic rule pipeline:

```text
trigger → subject/credit resolution → matcher → predicate → dedupe/anti-exploit
→ base/formula → multiplier stack → caps/rounding → ProgressionTransaction → outputs
```

The same engine can power XP, resources, challenges, procs, discoveries, and notifications without duplicating event plumbing.

### 36.1 Trigger catalog

Core/high-value triggers are added only when a reliable server-side event or carefully gated mixin exists:

| Domain | Trigger examples |
|---|---|
| Blocks/world | `block_break`, `block_place`, `block_interact`, `crop_harvest`, `fluid_collect`, `structure_enter`, `biome_discover`, `dimension_enter`, `light/change_context` |
| Combat | `damage_dealt_success`, `damage_taken_success`, `critical_hit`, `kill`, `assist_kill`, `projectile_hit`, `block/parry/dodge` (provider), `heal_done`, `heal_received`, `combat_start/end` |
| Items/crafting | `item_use_complete`, `consume`, `craft_extract`, `stonecut_extract`, `smith`, `anvil`, `enchant`, `grindstone`, `brew_extract`, `smelt_extract`, `loot_generate/claim` |
| Creatures/economy | `breed`, `tame`, `shear`, `milk`, `fish_catch`, `villager_trade`, `pet_kill`, `boss_first_kill` |
| Movement/survival | `travel_validated`, `fall_landed`, `sleep_complete`, `food_exhaustion`, `death`, `respawn` |
| Progression | `advancement`, `recipe_unlock`, `quest_complete`, `skill_level`, `node_purchase`, `class_change`, `ability_use`, `challenge_complete`, `prestige` |
| Magic/modded | `spell_precast`, `spell_cast_success`, `spell_impact`, provider-defined combat/technology/magic triggers |
| Custom | command, Java API, KubeJS typed trigger, scoreboard/function bridge |

Each binding documents whether the underlying event represents **attempt**, **success**, **result extraction**, or **final post-mitigation value**. XP defaults to successful, economically meaningful results.

### 36.2 Rich rule schema

```toml
schema_version = 2

[rule]
id = "mypack:mining/natural_ores"
trigger = "progressiveskills:block_break"
priority = 100
stack_group = "mypack:ore_mining"
stack_rule = "highest"          # sum | highest | first | exclusive | diminishing
credit = "actor"                # actor | owner | assists | party | team | world
match = ["tag:c:ores"]
predicate = "mypack:valid_survival_mining"
base = 8
amount_formula = "base * max(1, block_hardness / 3)"

[rule.anti_exploit]
profile = "mypack:natural_resource"
fake_players = "deny"
allowed_block_origins = ["natural", "creative_placed"]
per_tick_cap = 40
per_minute_cap = 500
per_day_cap = 10000
repeat_window_ticks = 200
repeat_decay = 0.75
minimum_multiplier = 0.10

[[rule.outputs]]
id = "mypack:mining/natural_ores/xp"
type = "xp"
skill = "mypack:mining"
amount_formula = "rule_amount"
```

`rule_amount` is a typed, transaction-local numeric variable containing the post-formula/stack/cap value; it is available only to that rule's output expressions. There is no string interpolation into numeric fields.

### 36.3 Multiplier order and stacking

The pipeline is fixed and visible in `/pskills explain xp`:

`base/event value → target value scaling → rule formula → context → equipment → party/team → rested/catch-up → prestige/season → global difficulty → anti-exploit decay → per-event cap → rate-cap remainder → rounding/fraction carry`

Every modifier belongs to a stack group with `add`, `multiply`, `highest`, `lowest`, or `replace(priority)` semantics. Validation warns when the same event will match overlapping `id:`/`tag:`/`mod:` rules in an ungrouped additive way.

### 36.4 Credit and attribution

Resolve direct actor, projectile owner, pet/tame owner, damage contributors, assists, party/team, automation/fake-player identity, and original transaction origin. Contribution windows have bounded maps and expiry. Packs define minimum contribution, last-hit bonus, pet policy, summoned-entity policy, and whether offline/dead/different-dimension members share.

### 36.5 Built-in anti-exploit policies

| Vector | Controls |
|---|---|
| Place/break loops | recent placed-block ledger or chunk provenance provider, natural-only policy, event dedupe token, cyclic-action decay |
| Spawner/summon farms | spawn-reason matcher, per-type/chunk/source decay, boss/unique scopes |
| Heal/damage loops | actual final damage/heal, eligible target, self/friendly filters, per-target/combat caps, repeated-source decay |
| Armor stands/dummies | entity eligibility tags/provider; default deny nonliving/training targets unless explicitly allowed |
| Movement loops | teleport/dimension delta exclusion, vehicle/mode policy, AFK/path repetition detection, unique-chunk exploration mode |
| Craft/uncraft | reward on actual player extraction, recipe fingerprint/value, cycle diagnostics, fake-player/automation policy |
| Enchant/grind | item fingerprint/first-operation memory, cost-weighting, cycle cooldown |
| Spell spam | completed successful cast, mana/cooldown/value weighting, per-spell group cap, canceled-cast rejection |
| PvP boosting | attacker-victim pair cooldown, contribution, level/IP/provider signals where lawful, daily cap, diminishing repeat; never silently collect external identity data |
| Conversion rounding | fixed-point math, cycle-product/arbitrage validation, min transaction size |
| Respec rewards | stable receipts, acquisition rewards non-refundable by default, paid-cost history |

Placed-position and per-target memories have explicit TTL/count caps and compact persistence choices; no unbounded “remember every block forever.” Claims/region mods can provide natural/provenance signals.

`deny_recent` is only a delay heuristic: a player can wait out its TTL. High-value “natural resource” rewards must use persistent tracked-position provenance, worldgen/chunk metadata with proven semantics, or a trusted claim/region/provider signal. If none is available, validation labels the rule exploitable/unproven instead of calling it natural-only.

Provenance follows a block through every movement transform the engine can identify: piston source→destination, falling-block entity spawn→landing, structure/template relocation, and registered mod-mover callbacks transfer the provenance token without duplicating it. Destruction, chunk unload, failed/canceled movement, and multi-block transforms are covered by idempotent move ids and tests. An unrecognized mover or ambiguous one-to-many transform invalidates the affected token (fail closed for natural-only rewards); it never turns a player-placed or unknown block into natural provenance at the destination.

### 36.6 Pacing and catch-up

Per-skill/source/tick/minute/day caps; level-difference scaling; server-median catch-up; new-season boost; rested pool; first-character versus alt policy; PvE/PvP scalars; dimension/difficulty profiles; and TPS-aware scheduling are configurable. TPS must never multiply rewards merely because the server lags.

### 36.7 Debugging

`/pskills explain xp last` shows every stage: event values, candidate route count, rejected matcher/predicate, attribution, stacking winner, every multiplier, cap/decay, rounding remainder, transaction id, and final award. `/pskills trace` samples with a hard duration/output cap.

---

## 37. Pack-Defined Currencies, Resources, Global Level & Progression Profiles

### 37.1 Currencies

Replace global/per-skill point special cases with `currencies/<id>.toml`:

- integer/fixed-point storage, min/max, initial value
- visibility/secret policy, icon/localization/theme
- earn/spend/refund/transfer policies
- scope: character, prestige run, season, team/guild, world
- debt allowed or denied; exact historical-cost refund behavior
- death/prestige/season reset/keep matrix
- leaderboard/privacy eligibility

Examples: talent points, mastery points, class tokens, prestige shards, reputation/favor. XP remains a specialized skill progression quantity but can be transacted through the same checked numeric layer.

### 37.2 Resources/meters

```toml
schema_version = 2

[resource]
id = "mypack:stamina"
display = { key = "resource.mypack.stamina", fallback = "Stamina" }
initial = 100
max_formula = "100 + 5 * skill_level('mypack:physique')"
clamp = [0, 100000]

[regen]
amount_per_second = 5
delay_after_spend_ticks = 20
predicate = "mypack:not_sprinting"

[persistence]
on_death = "refill"             # keep | refill | zero | percentage
on_relog = "keep"
offline_regen = false
```

Resources support regen, decay, drains, generators, reservation during cast, refunds on interruption, combat-only behavior, thresholds/hysteresis, overcap/temporary shield, and HUD widgets. Examples: stamina, rage, focus, energy, souls, combo points, heat, corruption.

### 37.3 Global level is a definition, not an assumption

Packs choose a named aggregate formula:

- sum of skill levels
- weighted sum by skill/category
- average or highest-N
- total **run-accounted** XP mapped through its own curve
- lifetime-earned history (informational/nonrepeatable gates only)
- milestone/rank table
- custom safe formula.

Adding/removing a skill can otherwise change every player's global level, so the lockfile impact report previews aggregate changes. The repeatable-run/prestige-safe default is **total conserved `runAccountedXp` through a global curve**. For a run, `Σ(skill.activeAccounted + skill.bankAccounted) = totalRunAccountedAllocation`; gameplay/accounted token awards are explicit sources, transfers preserve the sum, fees/sacrifice/declared decay are explicit sinks, and no other transaction may change it. Bank release and cap/profile/curve migration have accounted delta zero. A conversion cannot credit more accounted allocation than its source legs debit, and fixed-point split remainders stay with the source/transaction rather than rounding into new value. `/pskills audit` and property tests reconcile this equation after every mutation and crash recovery. Lifetime-earned history is never a repeatable prestige gate. A prestige reset closes the old run ledger and starts a new run-accounting scope before eligibility can become true again.

### 37.4 Progression profiles and difficulty

Named profiles (`casual`, `normal`, `expert`, `hardcore`, `season_3`) parameterize curves, multipliers, caps, respec rules, death loss, disclosure, and anti-farm without duplicating every definition. A world selects one active profile; migration between profiles is dry-run and impact-reported. Per-dimension/PvP profiles may layer only fields marked profile-overridable.

### 37.5 Scope model

State can be scoped `character`, `account/provider`, `team`, `guild`, `world`, or `season`. Core guarantees character/world. Other scopes are provider capabilities and never silently fall back to a different identity domain. Cross-server storage is an adapter (§45), not hidden network I/O in gameplay code.

---

## 38. Advanced Skill, Mastery, Discovery & Challenge Design

Skills gain these optional axes:

- **Categories/tags/sort/search aliases** and per-category theme/layout.
- **Visibility:** visible, locked, silhouette, discovered, secret; reveal via predicate/action.
- **Unlock prerequisites** separate from XP sources; locked XP can discard, bank, redirect, or count secretly.
- **Level cap formula** and cap unlocks from stages, classes, quests, dimensions, prestige, or challenge completion.
- **Segmented curves:** different curve/table/formula ranges with continuity validation.
- **Overflow:** discard, bank, convert, mastery, prestige meter, or world contribution.
- **Highest/lifetime/current values:** clear negative-XP and delevel semantics.
- **Mastery tiers/paragon levels** after cap, with a separate currency/curve/reward table.
- **Capstone trials:** reaching XP cap can unlock a challenge rather than auto-level.
- **Rank names/badges/titles** and cosmetic-only rewards.
- **Skill relations:** explicit synergy/penalty, shared XP, parent/child specialization, soft cap budgets.
- **Practice/upkeep:** decay, rested XP, offline training, and maintenance costs remain opt-in.
- **Discovery/collection log:** first biome, mob, recipe, ore, structure, spell, boss, or pack-defined fact.

### 38.1 Level-state invariant and exact Core curves

The authoritative per-skill progress coordinate is checked integer `currentTotalXp`; `earnedLevel` and `xpIntoLevel` are cached/validated derivatives. Default `min_level = 0`, `max_level ≥ min_level`, and a new character begins at `min_level` with zero XP. Let `C(L)` be XP required to advance from level `L` to `L+1`, `n = L - min_level`, and `T(min_level)=0`, `T(L)=Σ(C(k), k=min_level…L-1)`. Then `earnedLevel` is the greatest `L ≤ max_level` for which `T(L) ≤ currentTotalXp`; below the hard cap, `xpIntoLevel = currentTotalXp - T(L)`.

`effectiveLevel = min(earnedLevel, resolvedDynamicCap)` drives gates, persistent projections, and any `current_level` live budget when a class/stage/profile/context imposes a softer cap. The stored XP coordinate and earned peaks do not move when that cap toggles. Lowering a dynamic cap uses cause `cap_suppress`; raising it uses `cap_release`: both recompute persistent effects and post the idempotent live-budget delta, but neither emits highest/milestone rewards nor `on_each_reach`. A pack that banks XP while capped must declare whether earning continues and when the bank is released; release is a separate transaction and is never reclassified as fresh gameplay XP.

Core curve definitions are normative:

| Type | Required fields | Exact `C(L)` before final rounding |
|---|---|---|
| `flat` | `base` | `base` |
| `linear` | `base`, `step` | `base + step × n` |
| `polynomial` | `base`, `coefficient`, integer `power ≥ 0` | `base + coefficient × n^power` |
| `exponential` | `base`, fixed-point `factor > 0` | `base × factor^n` |
| `custom_table` | `custom_table` | entry `n`; array length must equal `max_level - min_level` |

Decimals are parsed as exact fixed-point/rational values, exponentiation is bounded/deterministic, and the complete expression is rounded once using `curve.rounding` (`ceil` default; `floor`, `nearest`, `bankers` optional). Every final `C(L)` must be at least 1, be nondecreasing by default, and every checked cumulative `T(L)` must fit signed `long`; otherwise staging fails with the first bad level. Negative linear steps/coefficients, an exponential factor below 1, or a decreasing custom table therefore fail normally. Creator exposes `allow_decreasing_costs = true` only as an unsafe expert opt-in with a conspicuous graph/validation warning, full-boundary tests, and no claim that the curve is monotonic. `max_level` has no outgoing cost. At cap, excess follows the explicit overflow policy (`discard`, `bank` default, `currency`, `mastery`, or provider); banked XP is not silently included in `currentTotalXp`. Its accounted delta is assigned once by the earning transaction and stored in `bankAccounted`; release moves it to `activeAccounted` with zero new accounted value. `discard` accepts no accounted allocation, while `currency`/`mastery`/provider overflow is a typed economy edge that debits or destroys source accounted units before crediting its target and can never retain both forms.

Positive awards commit immediately, calculate the final `earnedLevel`, and emit legitimate reward-eligible earned edges in ascending order. Negative XP defaults to `deny`; `drain_in_level` stops at `T(earnedLevel)`, while `allow_delevel` crosses downward in descending order, recomputes `effectiveLevel` and persistent ownership, and runs only explicitly compatible `on_loss` actions. Ordinary gate/scaling variable `level` and `current_level` awards resolve to `effectiveLevel`; peaks and milestone history resolve to eligible earned crossings. UI, sync, `/pskills info`, and `/pskills explain` show both whenever cap suppression makes them differ.

On curve changes, the shipped migration default is `preserve_level_fraction`: preserve `earnedLevel` and the exact rational fraction `xpIntoLevel / old C(earnedLevel)`, map it into the new cost with deterministic floor, clamp at the new hard cap, and keep accounted/earned history unchanged. At the old hard cap, where `C(max_level)` is undefined, the fraction is defined as zero and the separate overflow bank is preserved; if the hard cap rises, the player remains at the former cap with zero progress into the newly available level unless an explicit bank-release policy applies. Alternatives `preserve_total_xp`, `preserve_level_raw_into`, and an explicit rederive policy require dry-run impact. Cap lowering banks overflow; raising a cap does not emit gameplay transition rewards during reconcile. Changing `min_level` is structural and requires a named migration with dry-run rather than ordinary hot reload. All migrations use cause `migration`, then validate `T(earnedLevel) + xpIntoLevel = currentTotalXp` below cap (and the capped coordinate plus separate bank at cap) and suppress value-bearing crossings unless an operator issues a separate receipt-protected grant transaction.

### 38.2 Challenges/contracts

`challenges/<id>.toml` uses rule counters + predicates + lifecycle rewards:

- one-time achievements/capstones
- daily/weekly contracts using explicit server timezone/reset epoch
- streaks and multi-objective `all/any/ordered`
- bounties and rotating pools
- exploration/collection sets
- class trials and prestige trials
- party/team/community goals
- secret objectives and hints

Progress/counter scope, reset/archive, reroll, catch-up, failure, anti-exploit, and reward receipts are explicit. The scheduler uses timestamps, not one ticking task per objective.

### 38.3 Cosmetic progression

Titles, badges, nameplate/icon providers, toast frames, optional particles/aura descriptors, and guide trophies are first-class entitlements with client preference/visibility controls. Cosmetics cannot grant hidden gameplay power unless also represented by an ordinary audited output.

---

## 39. Peak Skill-Tree Customization

### 39.1 Node capabilities

- multiple ranks with per-rank cost, requirements, persistent effects, transition rewards
- `requires` boolean AST, cross-tree/class/skill/team/challenge requirements
- exclusive choice groups, group pick limits, and weighted capacity
- keystones/capstones/gates/socket/rune nodes
- repeatable/infinite nodes with escalating cost and diminishing grant formulas
- hidden/secret nodes, discovery reveals, decoy/lore nodes
- auto-granted nodes and non-refundable story nodes
- timed/seasonal/dormant nodes
- tree completion and branch completion bonuses
- named cross-tree synergy definitions
- suspension/refund/grandfather behavior if requirements disappear
- tags/search/category/rarity/narration/alt text

```toml
# Fragment (schema v2): append to a tree definition.
[[nodes]]
id = "mypack:warrior/cleave"
max_rank = 5
cost_formula = "rank * rank"        # or explicit cost_by_rank
exclusive_group = "mypack:weapon_path"
group_pick_limit = 1
dependency_policy = "cascade_refund"
layout = { x = 2.5, y = 1.0, group = "offense" }
```

Only registered safe expression functions/operators are valid; unknown function calls fail validation rather than falling through to arbitrary execution.

### 39.2 Layout model

Support logical graph identity separately from presentation:

- freeform `x/y`, grid `row/col`, or generated layout
- groups/pages/subtrees/layers and portal edges
- directed/undirected/decorative edge styles
- auto-layout strategies with deterministic seed
- zoom/pan bounds, minimap, legend, snap grid
- node overlap/edge crossing/viewport accessibility diagnostics
- alternate compact/list layout for keyboard/narrator/small screens.

Theme/layout changes never change node ids or purchases.

### 39.3 Purchase and respec UX

- hypothetical planner and compare current → planned stats
- multi-rank slider
- “buy shortest path” or selected path with total cost and unmet requirements
- preview exact dependent suspension/cascade/refund
- individual rank/node/branch/tree/all respec
- safe-zone/trainer/item/cooldown/escalating-cost policies
- optional undo grace window implemented as a reversible transaction snapshot, not client trust
- build share code containing ids/ranks/classes/slots only—never XP or authority.

### 39.4 Balance validation

Detect unreachable nodes, cycles, contradictory exclusive groups, free/infinite purchase loops, currency-positive respec loops, rank formula overflow, dominated choices, impossible group limits, hidden dead ends, and theoretical attribute/resource extremes. Export graph as DOT/Mermaid-compatible data and balance tables as CSV/JSON.

---

## 40. Class Slots, Ranks, Evolution, Roles & Loadouts

Replace a single ambiguous `stackable` boolean with a capacity model. These are separate canonical files, preserving the one-definition-per-file rule:

`class_slots/origin.toml`:

```toml
schema_version = 2

[class_slot]
id = "mypack:origin"
capacity = 1
```

`class_slots/combat.toml`:

```toml
schema_version = 2

[class_slot]
id = "mypack:combat"
capacity = 2
```

`classes/blade_dancer.toml`:

```toml
schema_version = 2

[class]
id = "mypack:blade_dancer"
display = { key = "class.mypack.blade_dancer", fallback = "Blade Dancer" }
slot = "mypack:combat"
slot_cost = 1
exclusive_tags = ["mypack:pure_martial"]
primary_allowed = true
max_rank = 20
```

Features:

- pack-defined slots: origin, background, profession, combat, subclass, prestige, curse, etc.
- weighted capacity and classes that occupy several slots
- primary/secondary/dormant states with different grant scaling
- explicit exclusivity tags/families and required/coexisting tags
- class XP/ranks/mastery and rank-specific tree/currency/resource
- evolution/promotion/subclass choices with reversible/irreversible policies
- class trials, trainers, locations, items, stages, quests, time/season requirements
- starter/re-rank kits protected by receipts and inventory delivery policy
- named standalone synergies with conditions, priority, and source-aware grants
- party roles/auras whose recipients and stacking are explicit
- swap/respec cooldown, combat lock, safe-zone/trainer requirement, costs, dependency cascade
- NPC/dialogue integration via provider rather than a hard dependency.

### 40.1 Loadouts

Loadouts may capture selected classes, node ranks, toggle state, ability slot arrangement, tracked HUD goals, and optional carrier equipment references. They never snapshot/copy items or progression amounts. Server controls slot count, switch cost/cooldown/location/combat policy and chooses exactly one accounting model:

- `shared_purchases`: all purchases exist once in character state; loadouts only select/arrange an allowed active subset. Switching neither spends/refunds currency nor fires acquisition rewards.
- `reserved_allocations`: each loadout owns a historical-cost allocation ledger; inactive loadouts continue to reserve their currency budget, so one balance cannot fund several builds. Editing/deleting a loadout is an explicit purchase/refund transaction, while activation only changes derived ownership and never replays kits, `on_purchase`, or `on_gain` value rewards.

Mixing models within one currency/scope is invalid. The UI shows `available`, `spent active`, and `reserved inactive` totals before every edit/switch.

Players can plan/save/share unavailable builds, but activation is a server transaction that revalidates every id, cost, entitlement, and dependency against the current digest.

### 40.2 Class change safety

Preview final stats/resources/spells/abilities, source ownership changes, suspended nodes, lost recipes/stages, exact refunds, non-returned kit rewards, and health/flight safety. Lowering max health clamps current health according to configured policy. Creative/spectator flight is never touched; foreign flight ownership is preserved only through a proven provider. Without one, PS restores a captured baseline only if the current value still equals the value PS applied, otherwise leaves it unchanged and reports ambiguous ownership.

---

## 41. Reactive Ability, Targeting & Typed Action-Graph Engine

Abilities become composable without requiring KubeJS for common gameplay.

### 41.1 Ability kinds

- `passive`: persistent entitlements/effects while owned
- `toggle`: persistent effects with optional resource drain and forced-off rules
- `active`: explicit slot/wheel activation
- `proc`: rule-triggered reaction with chance/internal cooldown
- `aura`: applies source-owned effects to a dynamic target set
- `channel`: active with cast/channel duration and interruption
- `stance`: mutually exclusive toggle family
- `combo`: ordered activation/trigger chain with expiry.

### 41.2 Activation schema

```toml
schema_version = 2

[ability]
id = "mypack:blood_fury"
kind = "active"
cooldown_ticks = 200
cooldown_group = "mypack:major_offensive"
global_cooldown_ticks = 10
charges = 2
recharge_ticks = 400
cast_time_ticks = 20
channel_time_ticks = 0
interrupt_on_damage = true
combat_policy = "allowed"

[[costs]]
id = "mypack:blood_fury/rage_cost"
type = "resource"
resource = "mypack:rage"
amount = 40
reserve_on_start = true
refund_on_interrupt = 0.5

[targeting]
mode = "self"                  # self | entity | block | ground | ray | cone | line | radius | chain
range = 0
line_of_sight = false
friendly_fire = false

[[actions]]
id = "mypack:blood_fury/strength"
type = "effect"
effect = "minecraft:strength"
duration_ticks = 200
amplifier_formula = "min(3, floor(skill_level('mypack:physique') / 20))"
```

### 41.3 Targeting

Profiles define range, ray width, block/entity predicate, line of sight, ally/enemy rules, self inclusion, min/max targets, sorting (nearest/lowest health/random with recorded choice), cone angle, radius/falloff, chain count/range, ground validity, cross-dimension denial, and PvP/safe-zone policy. The client may render a prediction/preview, but the server raycasts and chooses authoritative targets.

### 41.4 Native actions

Typed actions include:

- damage/heal/absorb/knockback/velocity
- potion/mod effect
- resource/currency/XP change
- teleport/dash with collision and claim checks
- sound/particle/shake/animation hook/message/title/toast
- projectile using pre-registered/provider type
- loot/item delivery
- cooldown/charge adjustment
- persistent entitlement for a duration
- target mark/link/taunt provider
- spell cast via version-pinned adapter
- server function/command/KubeJS escape hatch.

World modification, entity summoning, teleport, projectile, and command actions are separately permissioned and capped.

### 41.5 Reactive triggers/procs

```toml
# Fragment (schema v2): append to the ability above.
[[triggers]]
id = "mypack:blood_fury/rage_on_hit"
event = "progressiveskills:damage_dealt_success"
chance = 0.25
internal_cooldown_ticks = 20
per_target_cooldown_ticks = 20
ignore_origin_tags = ["ability:mypack:blood_fury"]
```

Proc events expose actor/target/tool/damage/spell/context variables. Every generated action carries an origin chain; rules can ignore ancestors/tags so lifesteal cannot recursively trigger itself. Cap cascade depth, breadth, actions/player/tick, and total server action time.

### 41.6 Cooldowns/charges/time

Cooldowns use monotonic server game ticks for online play; offline recharge is explicit and timestamp-based if enabled. Groups support `start`, `max`, `add`, `reduce`, and lockout policies. Client displays are predictions reconciled to server state. Death/dimension/relog/season/prestige/reset policies are individually defined.

Every cast/channel with a reservation or delayed completion persists an `ActiveCastState` keyed by activation transaction id: ability/version/generation, phase, authoritative target fingerprint, start/deadline, reserved cost legs, charge/cooldown start policy, completed action index, cancel reason, and settlement receipt. Reserving a cost and creating this state are one attachment mutation; completing, canceling, refunding, or consuming it is an idempotent settlement of that same record. Delayed value actions use the durable outbox rather than an untracked timer.

The definition must select policies for logout, death, dimension change, target invalidation, server restart, season/prestige, and definition publish. Safe default: cancel, consume the declared nonrefundable portion, return the exact recorded refundable portion once, and apply the configured `on_start`/`on_commit`/`on_interrupt` cooldown rule. Restart/login settles incomplete casts before abilities or costs become usable. Resume is allowed only for a provider/action graph proven deterministic and restart-safe; it revalidates target, generation, elapsed-clock policy, and reservations. The publish barrier cancels/settles every old-generation cast before swap unless a migration explicitly proves equivalence. Reconnect receives the authoritative cast/reservation snapshot, never a client-invented continuation.

### 41.7 Generic gameplay modifiers

Beyond attributes/boolean flags, provider-backed typed modifiers cover block-break speed/harvest permission, durability cost, loot quantity/quality, crafting/smelting yield, fishing, healing done/received, crit chance/damage, dodge/parry/block, lifesteal, projectile speed/count/spread/penetration, food/exhaustion, vanilla XP, mob aggro/detection, and recipe/advancement availability. Each modifier has an event contract, stack group, source owners, cap, diagnostics, and compat test; unsupported event families never silently fake behavior.

---

## 42. Carrier Items, Acquisition, Loot, Rewards & Economy

### 42.1 Full `ItemStackSpec`

Configured rewards can describe an existing item or a ProgressiveSkills carrier stack:

- item/carrier id and definition component
- count/formula, data components, enchantments, damage, custom model data
- translatable name/lore, rarity, glint, tooltip sections
- owner/team binding, tradability, pickup/use predicates
- charges, durability, cooldown group, expiration, repair/recharge rules
- definition digest/version and migration policy
- unique reward/receipt key
- fallback item if an optional mod is absent.

Arbitrary raw NBT/SNBT is disabled by default; typed data components and provider codecs are safer, validateable, and syncable.

### 42.2 Acquisition channels

Packs may place progression carriers through:

- shaped/shapeless/smithing/stonecutting/brewing recipes
- block/entity/chest/fishing/archaeology loot via datapack/global loot modifier helpers
- boss/first-kill/advancement/challenge rewards
- villager/wandering-trader/custom trader offers
- quest rewards and shop/economy providers
- class/node/prestige transition rewards
- commands/functions/KubeJS/Java API
- generic training station/altar recipes
- seasonal track and team/community claim screen.

Every channel is either native data-pack content or a version-pinned provider. Runtime TOML may generate an **exportable** datapack/resource pack, but changes that Minecraft only discovers on resource reload say so and run `/reload`/restart only through an operator-confirmed workflow.

### 42.3 Generic station/altar

The jar may register one generic menu/block entity at startup. A station definition configures appearance descriptor, accepted stack predicates, costs, progress time, skill/class/stage requirements, output transaction, per-player/world cooldown, recipe discovery, automation/fake-player behavior, and UI theme. It never creates a new block id at reload. Packs that do not want a new block can bind station recipes to provider-owned blocks or commands.

### 42.4 Delivery and claims

Inventory-full policy is explicit: `refuse_transaction`, `pending_claim`, `drop_if_safe`, or provider mail. Pending claims persist the bounded materialized `ItemStackSpec`/`BehaviorSnapshot`, digest, transaction, and receipt—not only a pointer that a later reload could change. They merge only under an identical spec/digest, obey count/byte ceilings, and appear in a claim UI. A unique reward is marked complete only after successful delivery or durable pending-claim creation.

### 42.5 Binding/trading/security

Bound stacks always revalidate owner/team and pinned behavior on **use/activation**; pickup, drop, death, and supported equip hooks enforce where NeoForge/provider events prove coverage. Arbitrary modded item handlers and container transfers are not universally interceptable, so “strict anti-transfer” is best-effort unless a pinned provider/capability test proves the entire path. Binding is never marketed as cheat-proof against operators/world editors. Current definitions may update safe presentation, but economic behavior uses the creation-time pinned/materialized spec unless an explicit migration or unsafe `accept_live` policy says otherwise (§9.5). Client lore is cosmetic. Unknown/invalidated stacks are inert, non-destructive, and diagnosable.

### 42.6 Upgrading, salvaging, and crafting quality

Optional systems:

- tier upgrades with preserved owner/charges and explicit receipt semantics
- recharge/repair using resource/currency/item costs
- salvage to configured outputs with anti-loop validation
- skill-scaled crafting quality/bonus output, represented by typed components
- bind-on-craft/use/pickup
- randomized affix/reward tables with recorded branch and previewable ranges
- research/recipe discovery requirements.

Conversion and salvage graphs are analyzed together to detect positive-value loops.

---

## 43. Multiplayer, Parties, Teams, Guilds, Mentors, Seasons & Privacy

### 43.1 Provider-neutral party contract

Core exposes a `PartyProvider` interface; FTB Teams is one adapter. A minimal internal party implementation is optional and not required for Core 1.0.

```toml
# Fragment (schema v2): multiplayer rule block embedded in a rule/profile definition.
[sharing]
scope = "party"
mode = "proportional"           # duplicate | split_evenly | proportional | last_hit | weighted_role
radius = 32
same_dimension = true
minimum_contribution = 0.05
include_pet_owner = true
include_dead = false
include_offline = false

[sharing.level_gap]
free_gap = 10
penalty_per_level = 0.05
minimum_multiplier = 0.10

[sharing.anti_boost]
same_victim_cooldown_ticks = 1200
pair_daily_cap = 5000
```

Define attribution, radius snapshot time, disconnect/death behavior, rounding remainders, rested/catch-up interaction, max party multiplier, fake-player policy, and whether duplication changes total generated XP.

### 43.2 Team/guild progression

Optional shared scopes support:

- team/guild skills, currencies, resources, trees, classes/roles
- member contribution ledger and permissions
- cooperative unlocks, research, structures/boss first-kills, community goals
- team auras and class-composition synergies
- season guild ladder
- join/leave/kick/disband ownership, cooldown, inheritance, and anti-hop policy
- snapshot/export and provider migration.

A provider must guarantee stable team ids and lifecycle events; otherwise shared progression is unavailable, never keyed by mutable display name.

### 43.3 Mentoring and catch-up

Mentor/apprentice pairs or party roles can grant bounded catch-up XP, shared challenges, teaching milestones, or temporary caps. Require consent, level-gap bands, play-together contribution, cooldown, daily cap, and anti-alt policy. Server-median catch-up can use privacy-preserving aggregate snapshots.

### 43.4 Player-to-player transfers

Consent-based XP/currency/item transfer supports tax, min/max, daily cap, cooldown, same-team requirement, distance/trade-screen confirmation, irrevocable receipt, and offline denial/queue policy. Conversion/transfer transactions never trust amounts calculated by the client.

### 43.5 PvP and competitive integrity

Separate PvE/PvP effect scalars, banned abilities/outputs, safe-zone/claim provider, duel override, friendly-fire rules, combat tagging, class/loadout switch lockout, victim-pair decay, level-gap scaling, and season rules. Explain every PvP adjustment in the player UI; no invisible stat lies.

### 43.6 Seasons

A `SeasonDef` declares id, start/end/reset epoch, active pack overlay/profile, eligible scopes, reset/keep/archive matrix, rewards, catch-up, leaderboard tie-breaks, grace/freeze windows, and rollover transaction. Time comes from authoritative server UTC/epoch plus explicit timezone only for display; restarts cannot double-roll.

At season end: freeze mutations, snapshot/archive, validate reward recipients, deliver receipt-protected rewards, apply reset/keep, switch overlay/profile, reconcile, and unfreeze. Every step is resumable/idempotent.

### 43.7 Leaderboards and privacy

Boards support skill/global/mastery/prestige/challenge/season/team, pagination, stable tie-break, offline last-known snapshot, staff hiding, opt-out, anonymous alias mode, and retention/archive. Players control build inspection, class/skill visibility, announcements, and leaderboard participation within competitive server rules disclosed on join. No sensitive identity/network data is included in diagnostic exports by default.

### 43.8 Announcements and social feedback

Milestone announcements scope to self/party/team/server/off, with rate/batch limits and client opt-down. Build inspect/share uses consent and share codes; codes contain selection ids/ranks/slots only.

---

## 44. Maximum Player UI, Theme, HUD, Planner & Authoring Studio

### 44.1 Authority model

- Server controls gameplay truth, visibility/secrets, default theme/layout, and required structural pages.
- Client controls HUD placement/scale/opacity, notification density, accessibility, theme preference if allowed, favorites, tracked goals, and ability arrangement.
- Accessibility settings are never server-lockable.
- The client renders only the current generation's sanitized presentation model; stale mutation buttons disable until resync.

Hidden predicates, anti-exploit thresholds, command bodies, secret objectives, and provider internals never enter that projection. Definitions may provide author-controlled `public_hint`/silhouette text. “Why no XP?” and shortest-path requests call a bounded server explanation endpoint; it applies self/op permissions and returns localized structured reason codes, source-safe values, or a deliberate `redacted` reason—not the hidden rule tree.

### 44.2 Theme definitions

`themes/<id>.toml` can inherit and customize:

- palette, typography/font resource, shadows, opacity, spacing, padding, corner/nine-slice metrics
- backgrounds, panels, tabs, buttons, slots, bars, connectors, node/class frames, state badges
- state styles (owned/available/locked/suspended/secret/maxed)
- sounds, particles, toast/notification styles
- animation durations/curves with reduced-motion alternatives
- per-skill/category/tree/class overrides
- asset fallbacks to `progressiveskills:vanilla`.

Themes reference resource locations that the client already has. An absent asset falls back at the smallest component boundary and logs one deduplicated diagnostic.

Every non-vanilla theme declares `asset_pack_id`, expected digest/version, whether assets are required or cosmetic, and a complete vanilla fallback. The join/resource-pack handshake tracks only the capability/acceptance state needed to choose a renderer. A declined/failed pack never blocks progression or changes stats; at most, a server whose separate policy requires its resource pack may use Minecraft's normal join policy. Studio refuses to claim a pixel-perfect publish until all referenced sprites/fonts/sounds validate against the chosen asset pack and previews the declined-pack fallback.

### 44.3 Layout definitions

Layouts control tab order/name/visibility, optional dashboards, list/card/grid density, detail placement, responsive breakpoints, tree viewport/minimap/legend, search/filter/sort/favorites/recent changes, ability panel, guide links, and custom read-only metric widgets. A layout cannot remove mandatory confirmation/accessibility semantics.

### 44.4 HUD widget system

Built-in widgets:

- one/many tracked skill bars
- global level/mastery/prestige
- currency and resource meters
- ability bar/wheel, charges, cooldown groups, cast/channel bar
- rested XP, combo/streak, decay/upkeep warning
- contextual-grant indicators
- next milestone/tracked objective/challenge
- compact recent-XP feed
- class/stance/toggle state
- party/team progress where allowed.

Every widget supports anchor/safe-area offset, scale, opacity, orientation, spacing, max entries, visibility predicate, fade, compact/verbose, background, text mode, snapping, and per-server profile. The drag/drop HUD editor has grid/alignment guides, presets, reset, undo, preview contexts, and import/export of **client-only** layouts.

### 44.5 Character screen and build planner

- dashboard summary with selectable cards/widgets
- Skills: search/category/filter/sort, source breakdown, time-to-next estimate, pin/favorite
- Trees: zoom/pan/minimap, logical keyboard view, shortest path, rank planner, current-vs-planned stat comparison
- Classes: slots/capacity, roles, ranks/evolution, synergies, swap preview
- Abilities: owned/search, drag to slots, target/cost/cooldown/charge preview, stance groups
- Challenges/Collections/Seasons/Team pages when enabled
- Claims page for pending item rewards
- “Why locked?”, “why active?”, “why this value?”, “why no XP?” throughout
- save/share hypothetical build; activation remains server-authoritative.

### 44.6 Native encyclopedia/onboarding

Searchable guide pages are generated from the client projection and show actual current-player values/formulas, XP methods, next milestones, requirements, reverse references, shortest known unlock path, optional-mod fallbacks, hidden-content hints, and troubleshooting links. First login may run a skippable onboarding sequence: open-key prompt, choose HUD preset, pin first skill, explain points/classes/abilities, and show server privacy/season rules.

### 44.7 Accessibility requirements

- large-text/reflow independent from GUI scale
- high-contrast and multiple color-vision presets
- state icon/pattern/text, never color alone
- reduced motion/no shake/no flashing, adjustable toast time, notification batching
- tooltip delay/scroll speed/double-click/hold-vs-toggle preferences
- logical keyboard traversal; graph parent/child/sibling commands
- narrator summaries for current/next value, costs, failed requirements, planned changes
- icon alt/narration text and sound captions/visual equivalents
- focus restoration, text expansion, RTL, long-locale, small-window tests
- optional controller adapter tested explicitly.

### 44.8 Op-only Authoring Studio

`/pskills studio` edits a **draft overlay**, never the live registry directly.

- schema-generated forms with docs/defaults/examples
- wizards for pack, skill, tree, class, ability, resource, item, conversion, challenge, theme/layout
- registry/tag/mod/attribute/spell browser with server-side autocomplete
- visual tree canvas, drag/edges/ranks/exclusivity/groups, auto-layout, overlap/cycle diagnostics
- curve/formula graph, cumulative XP table, actions/time-to-level estimate, stat/economy preview
- raw TOML expert view with source spans and formatting/comment preservation where parser/editor supports it
- clone, rename-with-alias, extract-template, find references, provenance
- live validation/suggested fixes
- simulated player/event/world contexts; no real player mutation
- player-view preview at locale/GUI scale/theme/accessibility profiles
- draft/live semantic diff and impacted-player/orphan/cap/stat/stage report
- atomic publish, last-known-good fallback, undo/redo, autosaved drafts, named versions, rollback
- optimistic revision conflict or file locks for multiple editors
- permission nodes and immutable audit records
- strict path/size/import sanitization.

Ordinary players get an optional read-only planner using the same widgets but never server file access.

The optional remote editor protocol is a separate threat-modeled spike, disabled by default. If shipped, it binds loopback only unless the operator explicitly supplies a TLS/mTLS reverse-proxy trust configuration; uses short-lived, narrowly scoped, revocable tokens; checks Origin/CSRF and operator permission; rate/size/concurrency limits every route; and writes only into a canonicalized staged-overlay sandbox. It exposes no arbitrary path, shell/command, Java object, secret, live-registry, or player-state endpoint. Every read/write/publish attempt is audited, publish still passes normal validation/generation barriers, and network-listener security tests are a Studio release gate.

---

## 45. Integration Architecture, Public SPI & Optional Storage

### 45.1 Provider SPI

External mods/addons can register:

- trigger/event types
- matchers and credit resolvers
- predicate/requirement leaves
- output/action and persistent-effect types
- safe formula variables/functions
- currency/resource/team/storage providers
- icon/targeting/editor widgets
- guide renderers
- diagnostic/validation checks
- claim/region/combat/party/quest adapters.

Providers declare id, version, capabilities, thread/event contract, codec/stream codec, client projection/redaction, preview support, rollback classification, cost/performance class, and diagnostic info. Registration occurs at the appropriate startup lifecycle; content references provider ids and `missing_policy`.

### 45.2 Stable API boundary

`ProgressiveSkillsApi` exposes read services, transactional mutation builders, definition queries, and provider registration—not mutable internal maps.

Events include cancellable/modifiable **pre** events and immutable **post** events with transaction id, definition generation, provenance, cause/origin chain, before/after, and result. Ordering is normative: construct an initial typed plan → fire pre → if modified, discard derived calculations and rerun every permission, target/range, revision, cap, affordability, anti-exploit, cascade, delivery, and security check → commit once → fire one immutable post result. Cancellation creates no cost, state write, reservation, receipt, or outbox entry. An addon cannot mark its mutation “already validated.” All mutation entry points (packet, command, item, event, KubeJS, addon) route through `ProgressionService` on the server thread.

### 45.3 KubeJS

KubeJS can:

- register typed builders/providers during the correct startup/reload phase
- fire custom triggers with validated context
- listen to pre/post progression events
- build transactions through the service
- query immutable state/definitions
- add predicates/formula variables/actions through validated adapter registration.

KubeJS/provider scripts are trusted arbitrary server code, not part of the expression sandbox. PS can validate inputs, isolate its own pre-commit state plan, record exceptions, and disable **future** calls after a handler returns/throws; it cannot preempt an infinite handler, roll back external mutations, or guarantee a script cannot corrupt other state. Script-backed outputs therefore declare `best_effort`/`at_least_once`, run only when the operator enables them, and carry prominent import/diagnostic warnings. Raw client script execution is never involved.

### 45.4 Vanilla/data bridges

First-class bridges for server functions, advancements, recipes, loot tables/global loot modifiers, scoreboards, vanilla teams, entity selectors, command result storage, predicates/tags, and data components. Native integrations use Minecraft's own data systems where they fit rather than recreating them.

### 45.5 Optional integration families

- progression/gating: ProgressiveStages, advancements, quest systems, Origins-style providers
- magic/combat: Iron's Spells, combat/parry providers, spell systems
- equipment: Curios/Accessories-style provider abstraction
- teams/social: FTB Teams and other stable party providers
- claims/regions: safe-zone/natural-block/protection predicates
- UI/reference: JEI/EMI subtype/info, Jade/WTHIT, Patchouli/Modonomicon guide renderer
- permissions: vanilla permission level + optional granular provider
- scripting/automation: KubeJS, functions, metrics exports
- attributes: any registered id is a candidate, but application requires a compatible `AttributeInstance`; named diagnose adapters cover provider-specific behavior.

Do not promise every bridge in Core. Each shipped adapter has a version matrix, absent-mod boot test, isolated integration test, all-integrations smoke test, and circuit breaker.

### 45.6 Compat classloading pattern

Common code knows only an internal interface and a string class name. After mod/version checks, the factory classloads the isolated module. No field, annotation, generic signature, lambda, codec, or event subscriber in common code references optional types. Client-only compat lives under client distribution guards. Compile-only API calls are preferred when pinned/stable; reflection is for gaps and is tested like any other code.

### 45.7 Multi-server storage (optional adapter, never Core magic)

An advanced provider may persist selected scopes to SQLite/PostgreSQL/network service, with idempotent transaction ids, write-ahead/outbox, reconnect policy, local cache, schema migration, encryption/TLS, and explicit operator configuration. It must choose one authority model per scope:

- a single-writer character/session lease with fencing token;
- remote atomic compare-and-swap/serializable transaction **before** authoritative local effects; or
- explicitly provisional/eventual mutations that allow compensation and forbid nonrollbackable outputs until confirmed.

Optimistic asynchronous writes alone are insufficient because two servers could spend/grant the same value before a conflict arrives. The gameplay thread may await an asynchronous future without blocking the tick thread, but the player action remains pending until the authority contract resolves or times out. On partition, policy is fail-closed/read-only/queued provisional—never silent local double-spend. Character attachment remains the Core default.

---

## 46. Security, Safety, Threading & Performance Budgets

### 46.1 Threat model

| Threat | Required mitigation |
|---|---|
| Malicious/stale client intent | server recomputation, generation/revision/request id, rate limit, bounded codec, target/permission/cost/cooldown validation |
| Packet allocation/compression bomb | count/string/byte caps before allocation, bounded decompression, chunk/total/time limits, digest, disconnect with localized reason |
| Command/template injection | typed placeholders, safe UUID/name Component handling, fixed command source/permission, allow/deny list, no client-provided command fragments |
| Formula abuse | sandboxed expression VM, operation/depth/count limits, no reflection/I/O/looping |
| Trusted script/provider abuse | explicit operator trust boundary; validated entry; pre-call cascade budget; exception audit; disable future calls after return/throw; no claim that PS can preempt a hang or roll back external side effects |
| Pack import traversal/bomb | canonical path check, zip-slip prevention, extension allowlist, file/count/ratio/total limits, preview, trusted-script warning |
| Infinite cascades | origin chain, idempotency, depth + breadth + per-player/global action/time budget, child queue, circuit breaker |
| State overflow/corruption | checked/saturating `long`, fixed-point bounds, codec limits, raw migration quarantine/shadow, last-known-good definitions |
| Reward duplication | stable grant ids, receipts, atomic delivery/pending claim, duplicate request handling |
| Permission escalation | single ProgressionService, granular nodes, op Studio, audit, no client-defined targets/amounts |
| Information leak | dedicated redacted ClientDefinitionView; no command strings, hidden predicates, scripts, admin metadata, or anti-exploit internals |

### 46.2 Threading model

- All authoritative state mutations and vanilla world/entity access occur on the logical server thread.
- File parse/schema compilation may occur off-thread only on immutable byte/text snapshots and never touches live registries/world objects.
- Staging publish and tag/registry resolution finalize on the server thread at a safe point.
- Network decode is bounded; handlers enqueue state work to the correct thread and surface async exceptions.
- Remote storage/exports are asynchronous with immutable snapshots and explicit completion/result.

### 46.3 Hard safety ceilings

Configurable downward but not removable above build hard limits:

- packs/definitions/nodes/grants/rules per server and per pack
- nesting/template expansion/formula operations/reference depth
- string/locale/tooltip/list/map lengths
- maximum level/threshold table/XP award/currency/resource
- client projection total and chunk sizes
- sync deltas per tick and pending queue length
- receipts/orphans/audit/pending claims/offline ops/behavior-archive retention
- actions/cascade/player/tick and global/tick
- structure/context queries and Studio simulations
- imported bundle sizes/compression ratio.

Capacity behavior is type-aware: expired audit/epoch data may follow ordinary retention, but permanent receipts, unresolved claims, paid-cost truth, and orphan raw state cannot be silently evicted. At the ceiling, the system losslessly archives into a still-consulted index or fails the new mutation/import closed with an operator diagnostic and maintenance command.

### 46.4 Hot-path indexes

Compile exact registry ids into direct route arrays; tags into membership indexes rebuilt after tag reload; namespaces into grouped arrays; provider matchers into cost-sorted chains. Event listeners early-return on empty tables. No string formatting/logging/allocation on a disabled or nonmatching hot path. Damage/movement fractional awards keep primitive fixed-point remainders.

### 46.5 Condition/effect diffing

Use dirty masks/dependency indexes and compare desired persistent snapshot to last applied snapshot. Do not clear/re-add every modifier after every minor context tick. Transient attribute modifiers remain derived; persisted attachment is the authority. Verify an entity actually has an `AttributeInstance` before applying a registered attribute.

### 46.6 Typed safety handlers

Flight, no-fall, fire immunity, potion effects, reach, step height, saturation, spell costs, health, and scale use separate handlers. PS can prove only its own sources. For vanilla booleans, capture the pre-PS baseline and expected applied value; restore only when the current value still matches that expectation, otherwise leave it and diagnose ambiguous foreign mutation. Prefer source-aware provider hooks where available. For vanilla potion effects, use short refreshed instances or attribute/provider alternatives and stop refreshing on loss; do not blindly remove/replace a stronger/equal foreign `MobEffectInstance`. When ownership cannot be proved, policy is conservative `no_remove`/grant-only. Creative/spectator is never overwritten; max-health reduction, dimension, respawn, and relog each have tested safety paths.

### 46.7 Performance gates

Phase 1 creates and commits `PERF-001`, fixing the reference hardware/JVM, pack fixture, player scripts, warm-up/capture duration, profiler commands, and numeric pass/fail thresholds before gameplay optimization begins. The initial release-candidate baseline is: Linux x86-64, 8 physical cores at ≥3.5 GHz, Java 21/G1 with an 8-GiB heap, release jar, view distance 10; 15-minute warm-up + 30-minute capture; a 10,000-definition/50,000-routed-rule synthetic pack; and reproducible 40- and 100-player mining/combat/movement mixes. CI can use a smaller deterministic smoke fixture, but a tagged release must run the reference soak. Changing a threshold requires an ADR with before/after profiles—never a quiet goalpost move.

| Initial measurable gate | Core release threshold |
|---|---:|
| PS-attributed tick time, 40-player mix | p95 ≤ 0.75 ms; p99 ≤ 1.5 ms; no sustained 5-ms spikes |
| PS-attributed tick time, 100-player stress | p95 ≤ 2.0 ms; p99 ≤ 4.0 ms |
| Disabled or no-route event after warm-up | 0 B allocation/event and no string/log construction |
| Simple matched XP route after warm-up | mean ≤ 128 B allocation/event; no collection growth |
| 10k-definition parse/compile on reference host | p95 ≤ 10 s off-thread; live server-thread publish slice ≤ 50 ms or maintenance publish is required |
| Cache-hit join at 50-ms RTT | progression-ready p95 ≤ 1 s; ≤ 256 KiB PS transfer/player |
| Cache-miss upper-bound projection at 50-ms RTT | p95 ≤ 10 s; ≤ 16 MiB assembled; clientbound chunks ≤ 512 KiB |
| Client intents | PS hard limit ≤ 16 KiB each and platform hard ceiling < 32 KiB |
| Steady-state state sync in the 40-player mix | mean ≤ 128 KiB/player/minute |

- pure matcher/formula JMH or equivalent allocation benchmark (informative, not flaky CI wall-clock)
- dedicated-server synthetic 40/100-player workload with mining/combat/movement/conditions
- JFR/allocation profile and tick percentiles
- reload of a large upper-bound pack while players have screens open and events fire
- sync/chunk compression/decompression benchmarks
- receipt/audit/leaderboard compaction soak
- optional integration matrix soak.

Targets are written as hardware/test-scenario-specific budgets before optimization; “negligible” alone is not acceptance criteria.

---

## 47. Validation, Simulation, Diagnostics, Operations & Test Matrix

### 47.1 Diagnostic categories and stable codes

- syntax/schema/source span (`PS-SCHEMA-*`)
- identity/alias/merge/provenance (`PS-ID-*`, `PS-MERGE-*`)
- references/dependency/reachability (`PS-REF-*`, `PS-GRAPH-*`)
- lifecycle/idempotency/ownership (`PS-LIFE-*`)
- numeric/economy/balance/arbitrage (`PS-BAL-*`)
- performance/size/cascade risk (`PS-PERF-*`)
- networking/client projection/secrecy (`PS-NET-*`)
- migration/orphan/impact (`PS-MIG-*`)
- integration/version/capability (`PS-COMPAT-*`)
- localization/presentation/accessibility (`PS-I18N-*`, `PS-A11Y-*`)
- security/permission/import (`PS-SEC-*`).

Diagnostics include severity, code, pack/file/key/source span, message, why it matters, suggested fix, related ids, and docs link. Suppression requires code + scope + human justification and cannot suppress hard security/schema errors. Outputs: chat, log, Studio, JSON, SARIF.

### 47.2 Explain/trace/simulate

- `explain lock`: every **disclosable** requirement result and shortest known unlock path; hidden branches return author hints/redacted nodes
- `explain xp`: permission-aware candidate rules, matcher/predicate/anti-farm/multiplier/cap/rounding; players receive safe reason codes while ops may request full server detail
- `explain output`: source owners, lifecycle, receipt, condition, resolver
- `explain stat`: all vanilla/foreign/PS modifiers and worked math
- `trace`: bounded sampled live events with redaction
- `simulate action`: dry-run event against synthetic/real snapshot
- `simulate build`: selected levels/nodes/classes/loadout → stats/resources/abilities
- `simulate pacing`: actions/time per level, total XP, milestone table
- `simulate economy`: sources/sinks, max budgets, conversion/respec/salvage cycles
- `simulate migration/reload`: changed players/orphans/caps/grants/stages/stats/packets.

Export CSV/JSON graphs/tables and sanitized diagnostic bundle with pack lockfile, diagnostics, versions, capabilities, timings, and selected logs—no player UUID/name by default.

### 47.3 Admin operations

- staged reload/diff/publish/rollback
- freeze/unfreeze progression
- create/list/restore snapshots
- reconcile derived effects/state
- migration dry-run/execute/status
- prune preview/confirm
- audit query and reversible transaction undo
- bulk selectors with atomicity mode and reason
- pending offline operation/claim inspection
- client digest/resync and compat circuit reset
- season dry-run/rollover/resume
- safe-mode last-known-good startup.

Audit record: transaction id, actor, target, cause/reason, timestamps, generation/revisions, before/after digest, costs/refunds, outputs, delivery, child origins, result. Retention and privacy are configurable within hard caps.

### 47.4 Headless authoring pipeline

Provide a supported validator/export entry point usable by Gradle/CI/Packwiz-style pipelines: resolve content roots + installed registry catalog fixture, validate, compile digest, run pack tests, emit docs/schema/SARIF/balance tables. If full registry/mod loading is required, ship a documented NeoForge data-run/server validation task rather than pretending the mod jar is a trivial standalone CLI.

### 47.5 Expanded tests

**Unit/property/fuzz:**

- codecs/source spans/unknown fields/bounds/malformed/future data
- formulas fixed-point/rounding/overflow/divide-by-zero/budget
- curve thresholds nondecreasing by default; explicit decreasing-cost opt-in remains positive/bounded; negative/cap/overflow and old-cap→new-cap invariants
- stable ids/hash collisions/aliases/replacements/merge/template determinism
- transaction atomicity/idempotency/receipts/child cascade budgets
- native cascade overflow/cycle rejects before any prefix; opaque re-entry cannot commit value by default
- ownership with two sources and manual source
- delevel/relevel/decay/prestige point farming
- reward watermarks: ineligible jump→eligible gain, jump→delevel→relevel, and A→B→A never back-pay suppressed edges
- `current_level` live-budget deltas under gameplay, token, admin, transfer, profile/migration, cap suppress/release, debt, and cascade
- raw/accounted active+bank conservation through earn, cap, bank release, transfer, conversion fee, sacrifice, curve migration, and fixed-point remainder
- exact historical refunds and conversion/salvage arbitrage
- random generated graphs for reachability/cycles/currency loops
- client redaction cannot leak secret/server-only fields
- packet collection/compression/chunk/revision/rate fuzzing.

**GameTests/integration:**

- attachment death copy, keep inventory, End return, relog/dimension
- reload/relog/reconcile never repeats one-shot rewards
- pending offline operations mark SavedData dirty/apply once
- cross-owner crash at every PREPARED/COMMITTED/individual-leg/FINALIZED checkpoint, with both participant login orders
- tag reload rebuilds matcher indexes
- screen open across generation publish; stale action rejection/resync
- attributes missing on entity, max-health/flight/scale safety
- carrier stack orphan/migrate/inventory-full/unique delivery
- every XP trigger success semantics and anti-farm controls
- ranked/exclusive tree, invalidation policy, class slots/loadouts
- ability targets, costs, charges, cooldown groups, proc recursion guard
- dedicated server boot, real client login, absent optional mods
- each compat alone and all supported compats together
- ISS learned-required/independently learned/selection/client-server/mana/cooldowns/instant/channelled casts
- ProgressiveStages external co-owner/revoke fallback.

**Golden/UX/accessibility:** generated docs/schema snapshots, semantic UI state snapshots, long/RTL locale layouts, keyboard/narrator focus order, themes missing assets, reduced motion, HUD profile migration.

**Migration:** raw-tag fixtures for every version, corrupt/future/quarantine, unknown preservation, alias/refactor, old carrier digests, old receipts, interrupted migration resume. Compare semantic canonical state, not byte ordering.

---

## 48. Verified Platform Notes, Compatibility Matrix & Technical Spikes

### 48.1 Locked baseline

- Minecraft `1.21.1`
- NeoForge `21.1.236` (latest published 21.1 build shown in the official Maven repository index at this revision; keep the exact tested pin in the build lock)
- Java 21
- current optional integration versions are **not** inferred from old docs; pin the actual target pack versions in `gradle.properties`/CI profiles.

### 48.2 Primary-source constraints used by this revision

- Official NeoForge Maven index for the exact published 21.1 baseline: <https://maven.neoforged.net/releases/net/neoforged/neoforge/>
- NeoForge attachments require explicit persistence/sync and do not copy on death unless `copyOnDeath()`/Clone handling is used: <https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/>
- NeoForge custom payloads use registered `CustomPacketPayload`/`StreamCodec` handlers and documented size ceilings: <https://docs.neoforged.net/docs/1.21.1/networking/payload/>
- Key mappings register through the client `RegisterKeyMappingsEvent`: <https://docs.neoforged.net/docs/1.21.1/misc/keymappings/>
- Registry objects register during registry lifecycle; config cannot hot-create arbitrary items: <https://docs.neoforged.net/docs/1.21.1/concepts/registries>
- ItemStack data components are the correct persistent/networked carrier metadata primitive: <https://docs.neoforged.net/docs/1.21.1/items/datacomponents>
- NeoForge client/common/server config authorities and world serverconfig overrides: <https://docs.neoforged.net/docs/1.21.1/misc/config/>
- Ordinary `Screen` versus menu-backed container screens: <https://docs.neoforged.net/docs/1.21.1/gui/screens/> and <https://docs.neoforged.net/docs/1.21.1/gui/menus/>
- Current ISS 1.21 source has `LearnedSpellData` and `SpellSelectionManager`, so selection and learning must be separate capabilities: <https://github.com/iron431/irons-spells-n-spellbooks/blob/1.21/src/main/java/io/redspace/ironsspellbooks/api/magic/LearnedSpellData.java> and <https://github.com/iron431/irons-spells-n-spellbooks/blob/1.21/src/main/java/io/redspace/ironsspellbooks/api/magic/SpellSelectionManager.java>

### 48.3 Required technical spikes before feature implementation

1. **ISS spike:** exact target version; selection + learned-required spell; client/server; respec with independent learned overlap; active instant/channelled casts.
2. **ProgressiveStages spike:** prove source-aware stage grant/revoke. Choose `managed`, `sticky`, or `grant_only` fallback.
3. **Carrier spike:** generic tome component survives save/network/recipe/JEI subtype/reload/orphan and renders fallback/custom model.
4. **Protocol spike:** 10–50k presentation definitions, chunk/digest/cache/reconnect/reload/stale screen/malformed bounds.
5. **Lifecycle spike:** reconcile/relog/reload/delevel cannot replay a reward; crash tests cover every outbox/delivery checkpoint, and command/external semantics are labeled honestly.
6. **Attachment spike:** death, Clone/End return, relog, dimension, mutable dirty/save behavior, migration raw shadow.
7. **Predicate spike:** event dependency index + health/time/light/structure buckets under 100-player load.
8. **Guide-renderer spike:** establish exact Patchouli and/or Modonomicon runtime renderer/export/reload capability for each adapter; native guide remains fallback.
9. **Compat classloading spike:** dedicated server with no optional mods and a CI scan/test for forbidden optional/client references.

No corresponding feature phase begins until its spike has an executable test and decision record.

### 48.4 Living compatibility table

| Integration | Tested versions | Required capabilities | Missing/unsupported behavior | CI profile |
|---|---|---|---|---|
| NeoForge | `21.1.236` | attachments, payloads, events, registries, screens | hard requirement | core |
| ProgressiveStages | TBD pinned | source-aware grant/revoke or declared fallback | pack policy | compat-ps |
| Iron's Spells | target pack exact + tested range | cast success, selection, learning policy, attributes | declared branch policy | compat-iss |
| KubeJS | TBD pinned | startup registration + events/transactions | bindings unavailable | compat-kubejs |
| Curios/equipment | TBD pinned | component-aware equip change and subtype | carrier works without slot | compat-curios |
| FTB Quests/Teams | TBD pinned | reward/task/party ids/events | feature hidden/disabled | compat-ftb |
| JEI/EMI/Jade/WTHIT/Patchouli/Modonomicon | each adapter TBD pinned | lifecycle/plugin APIs | native UI/guide fallback | compat-ui |

“TBD pinned” is a release blocker for that adapter, not for Core.

---

## 49. Release Trains, Gates & Definitions of Done

### 49.1 Core 1.0 — shippable vertical platform

**Includes:** TOML pack manifest/IR/staging; stable ids/aliases; transaction/lifecycle/receipts; attachment/migration/offline queue; handshake/projection/state sync; core skill/curve/XP/custom + selected event rules; highest-level point currency; attributes; linear grants; basic tree; class slot/capacity; persistent entitlements; fixed ability slots + basic passive/toggle/active; carrier items; vanilla UI; native guide; validation/explain; commands/audit basics; ProgressiveStages and one pinned ISS adapter only if spikes pass.

**Gate:**

- no P0 diagnostic/test failures
- dedicated server and client join green
- one-shot idempotency, co-owner revocation, point-farm, death-copy, offline queue, reload atomicity, stale packet tests green
- upper-bound payload and hot-path profile inside written budgets
- schema-generated docs/native guide complete
- vanilla-lite and hybrid example packs require no absent optional mod
- optional magic example is separate and manifest-gated
- migration rehearsal + last-known-good recovery proven.

### 49.2 Creator 1.1

**Includes:** templates/mixins/variables/primitives; formula/predicate DSL; rich triggers/anti-exploit; currencies/resources; ranked/exclusive trees; class ranks/evolution/loadouts; reactive abilities/action graph; prestige/conversion/decay/mastery; contextual effects; challenges; carrier acquisition/station; theme/layout/HUD editor; build planner/share codes; simulate/trace/export/import; native docs expansion.

**Gate:** formula/template fuzzing, economy/arbitrage property tests, theme/accessibility suite, simulation parity with runtime, `.pspack` import security, large-pack authoring/reload profile.

### 49.3 Multiplayer 1.2

**Includes:** provider-neutral parties, assist/contribution XP, team/guild state, mentor/catch-up, consent transfers, PvP policies, seasons, competitive boards/privacy, community challenges, season rollover.

**Gate:** team lifecycle/provider migration, anti-boost simulations, privacy/redaction review, idempotent interrupted rollover, 100-player party/boss/board soak.

### 49.4 Studio 2.0

**Includes:** schema-generated op Studio, visual graph, curve/economy preview, drafts/version history, impact diff, atomic publish/rollback, datapack JSON adapter, visual `.pspack` composition/signing, remote editor protocol if still justified.

**Gate:** multi-editor conflict, path/permission/import security, comment/provenance fidelity, crash/interrupted publish recovery, TOML/JSON/Studio IR equivalence, complete accessibility/keyboard authoring path.

### 49.5 Phase 20 interface rehaul

Phase 20 replaces the Phase 14 baseline screens with one cohesive vanilla advancement inspired interface. It is a client presentation and input milestone. Server authority, transaction rules, player state, pack semantics, and the Protocol 7 gameplay contract remain unchanged unless a separately documented compatibility fix requires an additive field.

**Visual language and shared shell:**

- Every ProgressiveSkills menu uses one shared screen foundation with a single correctly ordered blur pass, a centered advancement style window, stone and parchment toned panels, vanilla font, item rendering, tooltips, button sounds, focus states, and narration.
- Progression, trees, classes, abilities, claims, guide, comparison, tests, synchronization, HUD editing, command discovery, and Studio use the same frame, spacing, tabs, selected states, headings, and footer controls.
- Layout responds to GUI scale, window resize, long localized text, text scaling, high contrast, compact layout, and reduced motion. Small windows retain keyboard reachable content instead of clipping required actions.
- The redesign uses resource pack addressable ProgressiveSkills sprites or stable vanilla resources with code fallbacks. Missing cosmetic assets cannot hide information or disable progression.
- Menu Background Blur affects only the world or previous screen behind the interface. Panels, text, icons, widgets, and tooltips render after the blur and remain sharp.

**Progression screen:**

- The main Progression screen follows the Minecraft advancements window model. Major progression pages appear as icon tabs around the window rather than a full screen text table.
- Each page owns an advancement style canvas or list inside the window, a title strip, current player summary, hovered element tooltip, selected element detail, and contextual actions.
- Skills and the other progression pages use compact native selection cards, item tab icons, hover detail, selected detail, and page specific actions inside the advancement window. Classes, abilities, claims, guide entries, comparison, tests, synchronization, and Studio preserve their appropriate list, card, form, or diagnostic behavior.
- Search, paging, safe retry, accessibility preferences, and direct shortcuts remain available without covering the active content.
- Buttons are created only when the authoritative visible state allows their action. An unowned ability can be inspected but cannot expose assignment, toggle, or activation actions.

**Tree screen:**

- The Tree screen behaves like Minecraft's advancements screen. Every live progression tree is a root tab with its own remembered pan position, selected node, node graph, connectors, background, and tooltip state.
- Tree tabs move between the top, bottom, left, and right edges as needed using advancement style tab shapes. Mouse click, keyboard traversal, and accessible next and previous tree actions select roots without leaving the screen.
- The canvas supports bounded click drag panning, centered initial layout, owned, available, locked, suspended, and selected node frames, connection lines, item icons, rank text, hover tooltips, and a selected node detail panel.
- Purchase and refund controls show only for valid current player states. Every mutation uses the current definition generation, player revision, and preview digest. Stale or invalid actions disable safely and request resynchronization rather than throwing from an input callback.

**Ability wheel:**

- The ability wheel is a nonpausing HUD overlay rather than a `Screen`. Holding the configured Ability Wheel key displays it. Releasing the key closes it. A click does not latch it open.
- Because no screen is installed, movement, sprinting, jumping, sneaking, and normal world ticking continue while the wheel is visible.
- Assigned abilities occupy equally sized radial wedges. Mouse direction selects a wedge, the center is a dead zone, the current selection is highlighted, and empty slots remain visibly distinct.
- Ability slots render their configured item directly without an advancement frame or token background. The hovered slot uses a clean circular highlight behind the item, while the previously selected slot uses a quieter circle. Empty slots retain a neutral placeholder item and slot number without restoring the token background.
- Releasing the wheel key commits only the selected slot. The existing Use Selected Ability key performs activation. Closing with no radial selection preserves the previous slot.
- The overlay shows the selected ability name, slot number, type, charges, cooldown, readiness, and a clear unavailable reason without exposing server private data.
- Opening and closing the wheel cannot send duplicate selection or activation intents. Disconnect, loss of synchronization, screen opening, focus loss, and key remapping cancel the overlay safely.

**Crash hardening:**

- UI construction derives action availability from owner visible state and never treats definition presence as ownership.
- Client intent preparation may reject stale, unowned, malformed, or unavailable actions, but no exception from that boundary may escape a button, key, mouse, or overlay callback into Minecraft's render thread.
- Safe Retry stores only actions that were valid when built and rebuilds them against the latest synchronized state. It never replays an invalid ability id, slot, revision, generation, or digest.
- A regression test reproduces the Phase 19 crash where the Progression Ability page attempted to assign an unowned ability. Expected behavior is a disabled or absent action and a nonfatal player message.

**Phase 20 Option B, expanded progression hub and tree workspace:**

- Option B replaces the small fixed advancement window with a responsive advancement inspired workbench. The frame grows to the available GUI area while preserving room for tabs and footer controls at every supported GUI scale.
- Skills, classes, abilities, trees, claims, guide entries, and diagnostic pages use original ProgressiveSkills card layouts informed by the dense overview pattern of RPG character menus. No third party source, texture, sprite, or other bundled asset is copied into ProgressiveSkills.
- Definition cards show their configured item icon, display name, concise state summary, selected state, and a separate wrapped detail panel. Large windows show multiple card columns. Narrow windows reduce the column count before reducing readable content.
- Skills use a dedicated character dashboard instead of the generic definition browser. The dashboard places the local player preview between aggregate progression totals and the selected skill summary, then presents every skill as an icon selector with its current level badge in a separate lower grid.
- Classes use a dedicated slot selector instead of the generic definition browser. Pack defined class slots form their own selector rail with used capacity, the middle grid shows only classes belonging to the selected slot, and the detail rail shows selection state, weight, requirements, costs, and authoritative select or respec actions.
- Hovering a skill or class selector previews it without changing the authoritative selection. Clicking changes only the inspected entry. Class mutation controls remain derived from the current synchronized definition and player state and still route through the existing server intent boundary.
- Dedicated selectors page safely when a pack contains more entries or slots than the current GUI scale can display. The player preview, selector grids, slot rail, details, actions, tabs, and footer retain separate layout regions so none can overlap another.
- Every Progression root tab icon is resource pack configurable through `assets/progressiveskills/ui/progression.json`. The default file covers skills, trees, classes, abilities, claims, guide, compare, tests, sync, and Studio. A missing or invalid override falls back to the built in vanilla item for that page.
- Definition and tree root icons remain definition driven. Resource packs may also replace the shared background and other declared ProgressiveSkills presentation resources without altering authoritative gameplay data.
- The tree becomes a larger workspace with a graph canvas and a separate selected node panel. The selected icon, status, cost, and description never occupy graph space, so nodes cannot overlap the selected node text.
- Tree movement uses click drag panning, mouse wheel zoom centered on the pointer, per tree remembered pan and zoom, and Space to recenter. Connections render below nodes and the canvas clips nodes and lines before the selected detail panel.
- Tree node tooltips use a resource pack configurable ordered line template. The default template is `&l&c{name}`, `&c{state}`, `Cost {cost} {currency}`, and `{description}`. Templates support line breaks, placeholders, and legacy `&` color and style codes.
- Tooltip currency and other stable ids use the synchronized definition display when available. Otherwise namespaces are removed, separators become spaces, and words are title cased, so `progressiveskills:global_points` displays as `Global Points`.
- Every tooltip is wrapped to a configurable maximum width, positioned beside the pointer when possible, and clamped inside the current screen. Its opaque backing panel separates it from the graph without allowing text to extend beyond the screen.
- The exact resource format, fallback behavior, examples, accessibility requirements, and player verification steps are maintained in `docs/guides/UI-CUSTOMIZATION.md` and `docs/verification/PHASE-20.md`.

**Phase 20 gate:**

- all ProgressiveSkills screens compile on the physical client boundary and open without a second blur pass;
- progression and tree screens pass mouse, keyboard, narrator, resize, GUI scale, high contrast, compact layout, and reduced motion checks;
- every live tree can be selected and panned without closing the screen;
- every live tree can be zoomed, recentered, and revisited with its prior pan and zoom restored;
- long localized and legacy formatted tooltips wrap and remain fully visible at every tested GUI scale;
- all Progression tab icons can be replaced by a resource pack without changing the JAR or authoritative definitions;
- the radial wheel appears only while held, movement remains active, release selects at most once, and the wheel never activates an ability implicitly;
- wheel slots display only their item or neutral empty placeholder, no token frame remains, and hover and selected circles remain readable without covering cooldown, charge, or slot labels;
- owned, unowned, disabled, stale, cooling down, empty slot, and disconnected ability cases are nonfatal;
- the supplied unowned ability click crash has an automated regression test;
- unit, property, architecture, schema, GameTest, dedicated server, headless client, and release JAR verification gates pass;
- `docs/verification/PHASE-20.md`, an exact release JAR, and its SHA 256 are committed to the Phase 20 branch for the player checklist.

### 49.6 Feature completion checklist

A feature is not done until it has:

- stable schema id + migration/alias story
- lifecycle/ownership/rollback/receipt semantics
- server authority and client redaction rules
- bounds/performance classification
- validation codes + `explain` output
- docs/schema/Studio/native-guide metadata
- localization/narration/accessibility fields
- unit/property/GameTest + optional-mod/absent-mod tests where relevant
- reload/relog/death/dimension/orphan behavior
- audit/transaction provenance
- example that works with declared dependencies
- compatibility matrix entry and version pin if external.

---

## 50. North-Star Feature Inventory and Explicit Non-Goals

### 50.1 Peak customization inventory

By the end of the roadmap, pack developers can customize:

- content packs, namespaces, dependency/merge/override policy and world profiles
- localized Components, icons, themes, layouts, HUD widgets, sounds/particles/notifications
- pack-defined skills/categories/curves/caps/mastery/prestige/discovery/decay/rested/catch-up, up to documented hard safety ceilings
- typed currencies/resources and their scope/reset/regen/transfer rules
- rich trigger/matcher/predicate/formula/anti-exploit/stack/cap pipelines
- linear grants and transition rewards with exact lifecycle/receipts
- ranked/repeatable/exclusive/secret/socketed/multipage trees and layouts
- typed class slots/capacity/ranks/evolution/roles/synergies/loadouts
- passive/toggle/active/proc/aura/channel/stance/combo abilities
- targeting, resources, charges, cooldown groups, action sequences, PvP policies
- carrier items, acquisition, loot/trades/recipes/stations, binding/charges/upgrades/salvage
- requirements and outputs across vanilla/modded registries/providers
- challenges, collections, dailies/weeklies, seasons, community goals
- parties/teams/guilds/mentors/leaderboards/privacy/announcements
- native encyclopedia, onboarding, build planner/share codes, claims, admin dashboard
- authoring Studio, simulation, validation, trace, diff, snapshots, rollback, import/export
- KubeJS/Java/provider extensions and optional multi-server persistence.

### 50.2 Explicit non-goals unless separately approved

- Loading ProgressiveSkills on an unmodded client: carrier items, custom screens, and payloads make it a required client+server mod.
- Runtime creation of arbitrary Minecraft registry ids or unlimited Controls-menu keys.
- Trusting clients for XP, target selection, costs, effects, formulas, cooldowns, or files.
- Silently mutating other mods' private persistent state when ownership cannot be proven.
- Guaranteeing compatibility with every version of every optional mod through reflection.
- Shipping a general-purpose unsafe scripting language, arbitrary Java reflection, or network/file access in formulas.
- Enforcing a universal final attribute cap without acknowledging invasive cross-mod conflict.
- Treating Patchouli, KubeJS, FTB, Curios, or ISS as required for the native core experience.
- Hiding destructive migrations or season resets from operators/players.
- Building all north-star features before a stable Core 1.0 release.

### 50.3 Product principle

**Maximum customization means maximum composition and inspectability, not maximum hardcoded special cases.** The winning core is: stable ids + content packs + typed predicates/formulas + transactions/lifecycles + source-owned effects + provider APIs + excellent authoring/diagnostics. Every flashy feature above should be expressible by those primitives or justify a new primitive with the full §49.6 completion contract.
