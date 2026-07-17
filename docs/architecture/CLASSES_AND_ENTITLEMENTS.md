# Classes and Source Owned Entitlements

Status: automated Phase 11 beta checkpoint complete. The integrated class panel remains Phase 14 work and practical player checks remain in the final mass test.

## Core boundary

Phase 11 adds the smallest complete class lifecycle that can select weighted classes, enforce coexistence and prerequisites, activate synergies, charge exact named currency costs, and revoke only the entitlement sources owned by the removed class.

The Core contract includes:

- pack defined class slots with capacities from one through sixty four;
- weighted class slot costs, including explicit zero cost background classes;
- enabled and access required selection gates;
- bounded minimum skill levels, required tree nodes, and required classes;
- stable exclusive tags for deterministic coexistence conflicts;
- optional named currency selection and respec costs;
- explicit allowed or disabled swap policy per slot;
- source owned attributes, abilities, virtual spells, stages, tree access, and class access;
- named synergies that activate only while every required class is active;
- once per character starter kit delivery through a stable receipt;
- atomic select, respec, and swap transactions;
- active or suspended retained class state after a safe reload reconciliation;
- sanitized class, slot, synergy, starter kit, grant, and visible selection projection; and
- keyboard and narrator friendly chat commands while the integrated class panel remains deferred.

Ranks, evolution, roles, loadouts, class trees, formula costs, partial respec, percentage refunds, permanent spell learning, general conditions, and automatic capacity migration remain Creator features. Unsupported fields fail staging.

## Stable identity and catalogs

`class_slots/<id>.toml` defines one stable capacity bucket. `classes/<id>.toml` defines a stable selected class identity. Nested synergy IDs and every class or synergy grant ID are globally unique inside the live class catalog.

Staging resolves every slot, skill, node, class, currency, and grant target before publication. It rejects:

- a missing slot or currency;
- a class whose slot cost exceeds slot capacity;
- dangling skill, node, class, or access references;
- class prerequisite cycles;
- self requirements;
- duplicate or conflicting stable IDs;
- a synergy with fewer than two required classes;
- an impossible full reconciliation or swap transaction bound; and
- Creator only fields mixed into a Core definition.

Definition order and array order do not become persistence identity. Canonical output is sorted by stable ID. Presentation changes do not change selection identity. A structural change that cannot preserve the selected class lineage retains recoverable state but suspends its live grants until an explicit safe migration or respec.

## Weighted capacity and coexistence

For each slot, active occupancy is the checked sum of every selected class `slot_cost`. A zero cost class is still selected, persisted, visible, and subject to prerequisites and coexistence rules, but consumes no capacity.

A selection is allowed only when:

1. the class exists and is enabled;
2. any required class access entitlement is active;
3. every minimum skill, required node, and required class is satisfied;
4. no active selected class shares an exclusive tag;
5. the resulting slot occupancy does not exceed current capacity;
6. the named selection cost is affordable inside its configured balance bounds; and
7. the complete transaction, receipt, audit, and attachment preflight fits every hard limit.

The client can display disclosed requirements and capacity, but the server recomputes every decision against its current immutable snapshot. The client never supplies affordability, capacity, requirement, conflict, cost, grant, or resulting ownership answers.

## Selection costs, respec, and swap

Selection cost is an explicit sink. It is not a refundable historical payment. Removing a class does not return its earlier selection cost. A respec may charge the current configured respec cost and then removes that class only if `respec_allowed` is true.

A swap is one atomic replacement, not a respec followed later by a select. The authoritative preview resolves:

- the removed and replacement class IDs;
- affected class and synergy activation changes;
- current respec and selection currency charges;
- resulting weighted slot occupancy;
- requirement and coexistence blockers;
- the current definition and state revisions; and
- a digest over the complete deterministic plan.

Confirmation carries only stable class IDs and the preview digest. The server rebuilds the plan. Any intervening progression mutation or definition publication makes the preview stale. If any debit, grant projection, starter delivery reservation, attachment preflight, or compare and swap check fails, no part commits.

## Source owned entitlements

Each persistent grant contributes through a stable source identity containing the owner kind, owner ID, and grant ID. The effective target retains every source independently. Removing one class or synergy source cannot revoke a value still owned by a skill, tree node, another class, another synergy, or an administrator source.

Core class grants use these deterministic forms:

| Type | Effective meaning |
| --- | --- |
| `attribute` | Fixed point value through the authored attribute operation and additive resolver. |
| `ability` | Boolean source owned ability access. Phase 12 supplies assignment and execution. |
| `spell` | Highest source owned virtual spell level with `require_existing` or `satisfy_while_owned`. Phase 15 supplies the pinned physical adapter. |
| `stage` | Boolean source owned stage entitlement. Phase 15 supplies the pinned physical adapter. |
| `tree_access` | Boolean access to a named tree. |
| `class_access` | Boolean access to another named class. |

Provider backed grants remain logical and diagnosable when their provider is absent. They do not pretend that an external spell or stage was physically granted. Permanent spell learning is irreversible and is not a Core persistent class grant.

Synergy grants use the same source model. A synergy activates only when every required class is selected and active. Suspending or removing any requirement revokes only that synergy source.

## Starter kit receipt

The inline starter kit compiles to one stable receipt identity at `<class_id>/starter_kit`. It is delivered at most once per character, even after respec, swap, relog, restart, death copy, replay, or definition reorder.

Repeated item IDs represent repeated item stacks and are disclosed to the owner as bounded item counts. The receipt identity and delivery bookkeeping remain server only. A starter kit that cannot be reserved within the transaction and attachment bounds blocks selection before currency or ownership changes.

## Reload and suspension

Reload reconciliation derives active grants from selected state and the new live catalog without replaying acquisition actions. A selected class remains visible but becomes suspended when its definition or slot is unavailable, its lineage is incompatible, its prerequisites are no longer true, or the slot becomes over capacity.

Suspension means:

- selected identity remains durable and visible;
- class and dependent synergy grants become inactive;
- no selection cost is refunded;
- no starter kit is replayed;
- new selections cannot use the suspended class as an active prerequisite; and
- commands explain the bounded public reason while server only details remain redacted.

An author lowering capacity never silently chooses a class to delete. Restoration occurs automatically when the same compatible lineage becomes valid again. A forced migration requires a separate reviewed preview.

## Network contract

Protocol version 3 adds the required Core classes feature. The sanitized definition projection exposes slot capacity and swap policy, class slot use, disclosed prerequisites, costs, starter item counts, typed grant target, operation, resolver and value, plus named synergy requirements and grant summaries. It excludes source maps, provenance, raw ownership sets, paid records, receipts, audit bodies, hidden conditions, and provider secrets.

Visible owner state maps selected class IDs to slot ID, slot cost, and the explicit text equivalent `active` or `suspended`. Full snapshots and semantic deltas carry this map under existing continuity, digest, size, and resync checks.

Class intents are session bound, monotonically numbered, rate limited, definition bound, and state revision bound. Strict payload shapes exist for select, respec preview, respec confirmation, swap preview, and swap confirmation. Retained duplicate request IDs return the exact cached result and preview. Old, future jump, stale, malformed, quarantined, or inactive requests never reach the progression executor.

## Commands and chat accessibility

Phase 11 does not add a class screen. The command surface must remain sufficient for a keyboard only or chat only player:

| Command | Result |
| --- | --- |
| `/ps class list` | Lists slots, occupancy, selected state, and available class IDs in stable order. |
| `/ps class info <class>` | Describes slot use, requirements, costs, starter kit, grants, synergies, and current status. |
| `/ps class preview select <class>` | Shows all public blockers and exact selection charges without mutation. |
| `/ps class select <class>` | Selects through the authoritative transaction planner. |
| `/ps class preview respec <class>` | Shows affected classes, synergies, grants, and charges plus a confirmation digest. |
| `/ps class respec <class> <digest>` | Rebuilds and commits the exact current respec plan. |
| `/ps class preview swap <old> <new>` | Shows the complete atomic replacement plan and digest. |
| `/ps class swap <old> <new> <digest>` | Rebuilds and commits the exact current swap plan. |
| `/ps class entitlements` | Lists effective class and synergy grant summaries without raw source internals. |

Output uses stable line order, explicit Active, Suspended, Available, and Blocked words, short readable messages, and no color only meaning. Commands must be usable without a mouse. The Phase 14 class panel will call the same server intent and projection contracts rather than creating another authority path.

## Hard limits

| Area | Core bound |
| --- | --- |
| Slots, classes, and synergies | Bounded live catalog totals. |
| Capacity | Checked weighted occupancy with zero cost support and a maximum of sixty four. |
| Prerequisites | Bounded skills, nodes, classes, exclusive tags, and cycle work. |
| Grants | At most thirty two per class or synergy and one bounded full reconciliation mutation budget. |
| Starter kit | At most thirty two item entries with receipt protected once per character delivery. |
| Preview | Bounded affected classes, currency legs, blockers, digest, and text. |
| Visible state | Bounded selected class map with no raw source owners. |
| Intents | Existing payload, replay cache, future jump, rate, and session ceilings. |

Every limit is enforced during staging and again at the runtime authority boundary. Capacity or persistence exhaustion fails closed before a debit, receipt, or grant can commit.

## Deferred features

Phase 11 does not claim a class panel, advanced class navigation, loadouts, ranks, evolution, roles, class specific trees, class XP, formula costs, flexible refund percentages, permanent spell learning, general conditional grants, external provider compatibility, or physical ability execution. Those features remain assigned to Phases 12, 14, 15, or 17.
