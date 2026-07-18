# Trees and Exact Historical Refunds

Status: Phase 10 beta implementation complete with automated verification. Rendering, narration, real input, and multiplayer remain in the final mass test.

## Core boundary

Phase 10 is the smallest complete tree slice that can spend a named character currency, own persistent grants, and return the exact historical payment without creating currency.

The Phase 10 Core contract is limited to:

- optional global or skill bound trees;
- stable tree and node resource locations;
- one rank per node;
- literal positive costs paid in the tree's named currency;
- authored grid coordinates;
- acyclic prerequisites;
- `requires` with AND semantics;
- `requires_any` with OR semantics;
- bounded direct minimum actor skill levels;
- source owned attribute grants;
- transitive cascade refund;
- command and client intent entry points that share one server planner; and
- a keyboard and narrator usable first tree screen.

The tree engine is optional. A skill without a bound tree remains a linear skill, and a tree can coexist with linear level grants.

Core tree files do not expose ranked or repeatable nodes, cost formulas, exclusion groups, branch limits, sockets, runes, secret discovery, timed nodes, auto layout, generated layouts, shortest path purchasing, loadouts, cross tree synergy, or the general predicate and formula language. Phase 17 provides separate Creator rank, predicate, formula, and loadout systems. A Core tree file that uses a Creator field still fails staging instead of receiving placeholder behavior.

## Stable identity and compiled graph

A tree definition owns a sorted map of node IDs. Node array order is presentation only. Every prerequisite resolves to a node in the same live Core tree, every node ID is globally unique across the live tree catalog, and the directed prerequisite graph must be acyclic. Global identity prevents persistent grant source collisions between trees.

The compiler retains two distinct prerequisite lists:

- every owned node in `requires` must remain owned;
- when `requires_any` is nonempty, at least one listed node must remain owned.

An empty `requires` list passes. An empty `requires_any` list imposes no OR requirement. A nonempty `requires_any` list with no owned member fails. Direct minimum skill levels are an additional AND gate and do not replace graph prerequisites.

The Phase 10 compiled catalog must contain forward prerequisites, reverse dependents, a deterministic topological order, bounded minimum skill levels, and public presentation data. The server catalog must also contain costs and grants. The client must receive only the fields needed to render the disclosed graph and cannot receive hidden policy, raw ownership sources, paid cost records, or server only formula state.

## Historical paid cost ledger

Current configuration must never become refund truth. A completed implementation must write one immutable paid cost record in the same transaction that spends the currency and installs the node grants.

A Core paid cost record contains at least:

- the typed purchase instance ID;
- tree ID and node ID;
- fixed rank `1`;
- original currency ID;
- exact positive amount paid;
- purchase transaction ID;
- captured definition generation and semantic digest; and
- captured node lineage for audit and reconciliation.

The purchase instance ID is unique per player, node, and rank. Recording an existing instance fails closed. Removing a record requires the caller to supply the exact expected record. Refund planning reads the durable record, not the live node cost, and returns the original currency even when a later valid publication changes the tree's currency or displayed cost.

Paid cost records must be durable accounting state. Ordinary receipt retention cannot evict them. Capacity exhaustion must reject a new purchase before currency is spent. Records must not be projected to clients; the visible state contains only sanitized owned node ranks.

## One compare and swap transaction

Both purchase and refund planning must capture:

- the current definition generation and semantic digest;
- the player's current state revision;
- the exact immutable progression snapshot; and
- a unique idempotency key derived from the authoritative command operation or network session and request ID.

The Phase 10 planner must be pure and must commit through the existing transaction compare and swap checks. A changed definition or state revision must reject the complete plan. A replayed idempotency key must return the original result and cannot spend, grant, revoke, or refund twice.

The following state changes are one atomic transaction:

| Purchase | Refund |
|---|---|
| subtract the live literal cost | add each historical paid amount |
| record the immutable paid cost | remove each exact paid cost record |
| grant every persistent node source | revoke every removed node source |
| resolve and validate physical projection | resolve and validate physical projection |
| persist and synchronize after commit | persist and synchronize after commit |

If affordability, a bound, an entitlement resolver, physical projection, persistence, or compare and swap validation fails, none of those changes commits. Tree accounting must not be written later through a separate definition state because that would create a crash gap between the currency mutation and refund truth.

## Purchase algorithm

The authoritative server performs these steps in deterministic order:

1. Resolve the live node ID through the current tree catalog.
2. Reject disabled, unknown, already owned, or non Core nodes.
3. Read the authoritative progression snapshot.
4. Require every `requires` node to be owned.
5. If `requires_any` is nonempty, require at least one listed node to be owned.
6. Evaluate all minimum skill levels against the same immutable snapshot.
7. Read the live literal cost and currency from the server catalog.
8. Check the named currency definition and post spend bounds.
9. Build the paid cost record and source owned grant mutations.
10. Commit the spend, record, and grants with the captured definition and state revisions.
11. Persist the committed account and synchronize a sanitized new visible state.

Commands and packets provide only the requested node ID and an operation identity. They never provide an amount, currency, prerequisite result, grant, target player, or resulting ownership set.

## Transitive cascade refund

A refund can invalidate nodes that depend on the selected node. Phase 10 computes the complete closure before committing anything.

Let `owned` be the set represented by paid cost records and let `removed` initially contain the selected node. Repeatedly scan remaining owned nodes in stable ID order and add a node to `removed` when any dependency condition is no longer true:

- at least one member of its AND list is absent from `owned - removed`; or
- its OR list is nonempty and no member remains in `owned - removed`.
- a configured minimum skill level is no longer met.

The scan repeats until a complete pass adds nothing. This fixed point handles mixed AND and OR graphs and does not over refund an OR dependent while another owned alternative remains. Minimum skill levels that disappear follow the tree's Core dependency policy. The only Phase 10 policy is transitive cascade refund; suspension, grandfathering, and authorable policy combinations remain Creator features.

The final closure is ordered in reverse topological order, with stable node ID as the tie breaker, so dependents are revoked before their prerequisites. The planner then:

1. loads the exact paid record for every removed purchase;
2. groups refund legs by original currency ID with checked addition;
3. verifies every resulting currency balance against its definition bounds;
4. removes each exact paid record;
5. revokes each grant source owned by the removed nodes; and
6. commits the complete closure once.

No partial cascade is legal. If the closure, refund sum, mutation count, or projection exceeds a hard limit, the transaction fails without changing state and reports an operator actionable diagnostic.

## Preview and confirmation

Purchase and refund previews call the same pure planner inputs used by live execution. A refund preview returns a bounded structured result containing the selected node, ordered affected nodes, sorted historical refund legs, current state revision, current definition revision, and a digest of those values.

A preview is not authorization. Confirmation sends only the selected node and preview digest. The server rebuilds the closure from current authority and requires the digest, state revision, and definition revision to match. Any intervening purchase, refund, XP award, reload, migration, or reconciliation makes the confirmation stale. The client discards its confirmation state after a revision change.

## Lineage, reload, and reconciliation

Tree and node IDs are persistent identity. Changing display text, layout, cost, or grant values under the same node ID updates the live definition but does not rewrite its historical payment. Authors must use a new node ID when creating a different gameplay identity.

Publication must stage graph validation and impact calculation before swapping the live generation. Online reconciliation runs after the new generation is known and before the replacement network session becomes active.

For each paid record, reconciliation must:

- preserve the original paid currency and amount;
- retain an owned node that still exists and remains valid;
- rederive retained persistent grants from the new definition without firing purchase rewards;
- apply the Core cascade policy when a prerequisite or minimum skill level disappears;
- use the paid record and retained source ownership to refund and revoke a removed node; and
- reject publication or preserve recoverable orphan accounting when an exact safe result cannot be constructed.

A cost change alone never grants or removes currency. A numeric change to a compatible grant updates the desired source contribution through ordinary persistent projection. A change to grant identity, attribute, operation, or ownership shape is breaking and preserves recoverable orphan accounting when it cannot be applied exactly. Reconciliation never replays transition actions. Login, respawn, dimension change, relog, and server restart restore the ledger before projecting ownership.

Nested node aliases are not silently inferred from tree aliases or path similarity. Until an explicit node migration format exists, changing a purchased node ID is a breaking authoring action and must be rejected or handled through an explicit migration rather than guessed.

## Authoritative entry points

The implemented command surface is:

| Command | Authority |
|---|---|
| `/ps tree list` | Reads the live public catalog and caller ownership. |
| `/ps tree info <tree>` | Reads one live tree and caller state. |
| `/ps tree preview buy <tree> <node>` | Creates a bounded nonmutating purchase preview. |
| `/ps tree buy <tree> <node>` | Calls the same purchase planner as the client intent. |
| `/ps tree preview refund <tree> <node>` | Creates a bounded nonmutating cascade preview. |
| `/ps tree refund <tree> <node> <digest>` | Rebuilds and commits the exact current cascade. |
| `/ps tree preview respec <tree>` | Creates a bounded preview for every owned node in one tree. |
| `/ps tree respec <tree> <digest>` | Rebuilds and commits the exact current tree respec. |

The client intent envelope is session bound, monotonically numbered, rate limited, definition bound, and state revision bound. The intent body contains a stable node ID or a server issued confirmation token. Server code resolves the actor from the connection and always targets that player for the Core screen. The server recomputes affordability, requirements, closure, grants, and refunds.

Commands and client intents enter one `TreeRuntime` service on the Minecraft server thread and receive the same transaction result and audit provenance. No client supplied preview, label, price, rank, grant, or target is trusted.

## First accessible tree screen

Phase 10 introduces an ordinary client `Screen`, not a container screen. It reads the sanitized definition projection and visible owned ranks, while all mutations remain server intents.

The first screen must provide:

- a fixed rebindable client key;
- deterministic tree and node focus order;
- full keyboard selection and activation;
- narrator labels for tree, node, ownership state, cost, requirements, and action result;
- explicit text states such as Owned, Available, and Locked instead of color only;
- visible focus, hover, disabled, and pressed states;
- a list or menu fallback that exposes every graph node without pointer input;
- responsive bounds recomputed in `init` after resize or GUI scale changes;
- bounded pan or scroll behavior;
- confirmation text listing every cascade node and refund leg; and
- focus restoration after a state update or rejected stale intent.

The default uses vanilla font, buttons, tooltips, sounds, and `GuiGraphics`. A locked button can show a client safe hint, but the server remains the final authority and may return a redacted or more current reason.

## Hard limits

Phase 10 implementation and tests establish concrete constants for every row below. Every bound is enforced by authority rather than only by the UI.

| Area | Required bound |
|---|---|
| Trees and nodes | Per tree and total live catalog counts. |
| Graph | Prerequisites per node, total edges, depth, and cycle detection work. |
| Minimum skill levels | Per node entries, referenced skills, and evaluation work. |
| Grants | Grants per node and total transaction mutations. |
| Refund | Maximum transitive closure and exact worst case mutation weight. |
| Ledger | Paid records per account and serialized bytes. |
| Preview | Affected nodes, currency legs, text, payload bytes, and retained confirmations. |
| Intents | Payload bytes, request cache, future request jump, and token bucket. |
| UI | Visible widgets, text length, pan range, and virtualized list work. |

Graph staging must compute the worst possible Core cascade weight from paid record removals, grouped balance credits, and grant revocations. A graph whose legal cascade cannot fit the atomic transaction ceiling is invalid. Runtime must still recheck all ceilings and fail closed.

## Unsupported Creator features

Phase 10 does not claim:

- multiple ranks, infinite ranks, or escalating cost formulas;
- exclusive groups, branch limits, sockets, runes, or keystones;
- secret, hidden, seasonal, timed, dormant, or auto granted nodes;
- suspension, grandfathering, partial refunds, salvage, or configurable refund percentages;
- cross tree, class, party, team, challenge, season, or world requirements;
- named reusable predicates or author entered formulas;
- shortest path buying, bulk branch buying, loadouts, build codes, or simulations;
- generated layout, minimap, rich theme, or visual editor behavior; or
- client authority over costs, grants, requirements, or refunds.

The cumulative Phase 19 beta supplies selected advanced behavior through separate Creator definitions and loadout transactions. Any form that is still absent from those documented surfaces remains rejected rather than inferred.
