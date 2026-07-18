# Carrier items and durable claims

Phase 13 adds a frozen registry safe carrier vertical slice. Pack files define configured stacks and server owned behavior. They do not create registry entries during reload.

## Registered carriers

ProgressiveSkills registers five generic items during startup.

- `progressiveskills:tome`
- `progressiveskills:token`
- `progressiveskills:charm`
- `progressiveskills:consumable`
- `progressiveskills:artifact`

Each item has a vanilla safe fallback model. A configured stack may set its visible name, lore, rarity, glint, and maximum stack size through validated vanilla data components. An unconfigured generic carrier has no identity component and is inert.

## Stack identity

Two persistent and network synchronized components identify a configured stack.

`carrier_identity` contains only the stable item definition id and a lowercase SHA 256 behavior digest.

`carrier_state` contains the component schema version, behavior version, remaining charges, creation pack digest, issuance UUID, monotonic use counter, optional bound owner, and optional migration marker.

Actions, targets, reward amounts, server receipts, archive payloads, and claim internals are never accepted from the component set. The server resolves economic behavior by digest from its world archive on every use.

The issuance UUID and use counter are checked against an exact non evicting player ledger. Old counters and skipped counters fail closed. When that bounded ledger is full, a new issuance cannot be spent until an operator resolves capacity. This prevents an old split or copied stack from becoming valid again merely because ordinary request replay history aged out. It does not claim protection against operators, world editors, or another mod that can rewrite server state.

## Item definitions

`items/<id>.toml` compiles into a typed carrier definition. Core accepts these fields.

- one of the five fixed carrier ids
- positive behavior version
- `keep_pinned`, `warn`, `invalidate`, or explicit `migrate` policy
- `none`, `on_pickup`, `on_use`, or `on_craft` binding policy
- `refuse_transaction`, `pending_claim`, or `drop_if_safe` delivery policy
- bounded stack size, charges, cooldown, rarity, and glint
- up to sixteen ordered Core use actions

Core use actions grant skill XP, grant enough XP to cross a bounded number of skill levels, credit a named currency, or respec a named tree using its exact historical paid costs. Every referenced skill, currency, and tree must exist and be enabled in the same staged snapshot. Action ids are stable and unique.

The compiler rejects unsafe `accept_live`. Live economic adoption always requires a reviewed migration. Presentation may follow the current definition while existing economic behavior remains pinned.

## Behavior archive

The overworld `progressiveskills_carrier_behaviors` SavedData stores canonical behavior snapshots by semantic digest. Every entry has a payload checksum, canonical decode check, entry size bound, total byte bound, and schema version. Corrupt or future data quarantines the archive and makes carrier use inert.

Before startup publication or a reviewed reload can expose a carrier definition, all new snapshots must fit the archive. Reservation is performed before the live generation changes. A full archive blocks publication instead of publishing definitions that cannot safely issue stacks.

Entries are never removed because of age. An old digest may still exist in an unloaded container. Phase 13 exposes archive status and verification, but does not claim a complete world container scan or automatic garbage collection.

## Authoritative use

Right click handling runs only for a server player holding one of the five registered carriers. It validates all of the following before mutation.

- the identity and state components exist and pass their bounds
- the physical carrier kind matches the archived behavior
- definition id, behavior digest, behavior version, and creation digest agree
- the digest is present and verifies in the server archive
- the item is not invalidated by the current migration policy
- owner binding permits this player
- creative use is denied
- cooldown has expired
- charges and the issuance ledger can be advanced
- every referenced live skill, currency, and tree is valid
- the complete ordered action plan fits transaction and persistence limits

All actions are expanded against a virtual snapshot and committed as one progression cascade. A failure leaves balances, trees, the issuance ledger, charges, and the held stack unchanged. On success the ledger and stack advance once and the client receives fresh state.

Tree respec actions use the same refund planner as `/pskills tree respec`, including original currency and paid amount records. Skill level actions grant only the XP needed to reach the requested bounded target level. They do not overwrite a level balance directly.

## Acquisition and claims

`/pskills give <player> <item_definition> [count]` is the safe operator acquisition path. It resolves the current typed definition, reserves its archived snapshot, creates server owned components, and attempts inventory insertion. The command never accepts a behavior digest or reward amount from the caller.

When configured delivery is `pending_claim`, inventory overflow becomes a durable player claim. A claim materializes its validated carrier identity, state, physical kind, full bounded behavior snapshot, origin delivery id, reason, and creation time. Construction and decode prove that the snapshot digest and fields match the stack identity. This lets a recovery path reconstruct the pinned item even if the archive needs repair. Client projection contains only a bounded summary and never includes actions or amounts.

Claim ids are stable UUIDs. Claims have exact count and encoded size ceilings, deterministic ordering, and no age eviction. Capacity is reserved before a value bearing acquisition completes. Taking a claim removes it only after successful inventory insertion. `take all` processes a deterministic prefix and leaves every undelivered claim intact.

The cumulative Phase 14 client adds the visual claim panel. The complete chat fallback remains `/pskills claim list`, `/pskills claim take <id>`, and `/pskills claim take all`.

## Migration and diagnostics

Held item inspection reports identity, physical kind, behavior version, remaining charges, binding state, archive status, and whether the current definition digest differs.

`keep_pinned` continues to use the archived snapshot. `warn` continues to use it and reports drift. `invalidate` makes the old stack inert. `migrate` requires a preview digest followed by explicit confirmation. Migration preserves issuance identity, use counter, binding, and a bounded migration marker while changing only reviewed behavior fields.

Unknown ids, missing digests, malformed components, quarantined archives, and stale previews are inert and diagnosable. They are never consumed on failure.

## Network boundary

Protocol version 5 adds sanitized held carrier and pending claim summaries plus strict inspect, claim, and migration intents. Existing session identity, request replay, definition revision, state revision, rate, permission, and quarantine checks remain mandatory. A client cannot submit an action list, behavior snapshot, amount, owner, archive entry, or claim body.

## Explicit Phase 13 limits

The cumulative Phase 15 provider contracts and honest fallbacks are implemented. Curios, JEI, EMI, quest, mail, trade, station, and recipe integrations remain unavailable without an exact pinned provider artifact and a passing present mod test. Generic carriers, safe command acquisition, and durable native claims work without those mods.

Phase 13 also defers HMAC world secrets, signed unique carriers, automatic archive pruning, upgrades, salvage, arbitrary action graphs, generated datapacks, and station blocks. The exact issuance ledger is a Core replay guard, not a claim that ordinary item components are cheat proof.
