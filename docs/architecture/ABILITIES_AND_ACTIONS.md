# Abilities and actions

Phase 12 adds the first complete Core ability lifecycle. The cumulative Phase 19 beta also contains the Phase 14 screen, wheel and HUD, Phase 15 provider boundaries, and Phase 17 reactive Creator systems. Content remains reloadable while respecting Minecraft startup boundaries. Packs define abilities. The client registers a fixed set of controls at startup. The server owns every assignment, toggle, target, cost, cooldown, charge, and action decision.

## Core ability dialect

Core accepts three kinds.

| Kind | Persistent effects | Fixed slot | Activation actions |
| --- | --- | --- | --- |
| Passive | Always while owned and enabled | No | No |
| Toggle | Only while toggled on | Optional | No |
| Active | No | Yes | Yes |

Persistent Core effects are limited to source owned attributes and boolean flags. Core active costs are limited to named currencies, vanilla hunger, and vanilla experience. Targeting is limited to self, entity, and block targets. Native actions are limited to messages, healing, and vanilla effects. These limits keep Core behavior deterministic and auditable. Phase 17 implements reactive procs, combo mastery, and custom resources as separate Creator systems rather than silently widening Core action lists.

An ability id is owned through the shared source aware entitlement system. Removing one class, tree, or other owner cannot revoke an ability that still has another owner. Missing or disabled definitions stop active behavior without silently deleting the retained ownership identity.

Class selection, respec, swap, and reconciliation append resulting ability effects to the same bounded cascade. Class ownership and ability projection therefore commit together or reject together after one aggregate physical projection validation.

## Fixed assignments

The client registers exactly eight direct slot mappings plus Ability Wheel, Previous Ability, Next Ability, and Use Selected Ability during client startup. The direct slot mappings are unbound by default. Runtime packs never create new Controls entries.

Assignments use the stable logical slot ids `progressiveskills:ability_slot/1` through `progressiveskills:ability_slot/8`. The visible protocol presents them as zero based indexes. Commands and player facing text present slots as one through eight.

Only owned, enabled, slot allowed abilities can be newly assigned. An ability can occupy at most one slot. Moving it is atomic. Unassigning the selected slot also clears selection. Reload reconciliation removes assignments that lose ownership or violate a known slot lifecycle. Missing or disabled definitions retain their identity in inert diagnosable state so a safe reload can restore them.

## Toggle state and persistent effects

Toggle state is authoritative logical progression state. A toggle uses its configured default only until an explicit state exists. Toggling creates or replaces that explicit state and updates every persistent effect in the same transaction. Passive effects are reconciled whenever ownership or definitions change.

Every persistent contribution keeps the ability id and effect id as its source. The normal entitlement resolver therefore preserves overlapping owners and applies the established attribute and boolean union rules.

## Activation transaction

An active ability activation follows one server path.

1. Resolve the assigned ability from the requested fixed slot.
2. Verify ownership, enabled state, kind, assignment, cooldown, charges, and named currency affordability.
3. Validate vanilla hunger and experience costs against the player.
4. Resolve the authoritative self, entity, or block target on the server.
5. Build one revision pinned transaction with named currency, cooldown, charge, vanilla cost, and action effects.
6. Commit once through the progression transaction service and return one replay safe result.

Cooldown ready ticks, charge counts, and recharge ticks use internal authoritative balances. They are persisted but excluded from the generic visible balance map. The client receives only sanitized remaining cooldown and charge state.

The generated `progressiveskills:ability_state` cooldown, charge, and recharge balance families are reserved. Pack currencies that match an internal balance shape fail staging before publication.

Cooldown groups share one ready tick. Charges replenish on bounded server tick reconciliation. A failed target, unaffordable cost, stale revision, quarantine, or rejected physical action spends nothing.

## Networking

Protocol version 4 requires the Core abilities feature. The definition projection contains bounded presentation plus kind, slot policy, persistent effect summaries, cost summaries, target policy, cooldown group, timing, charges, and ordered native action summaries. It excludes raw ownership sources, internal balance ids, executors, receipts, audits, and provenance.

Visible state contains only owned ability ids with toggle, charge, and remaining cooldown state, fixed assignments, and selected slot. Ability support was introduced in protocol 4. The cumulative protocol 7 snapshots and semantic deltas retain the same collection ceilings, redaction rules, and continuity digests.

The closed client intent family is assign, unassign, select, toggle, and activate. Each request carries the negotiated session, monotonic request id, definition generation and digest, and state revision. Canonical payload shape, replay cache, future jump, stale state, rate, quarantine, and server target guards run before mutation.

## Keyboard and chat access

At the historical Phase 12 checkpoint, fixed controls provided the complete nonvisual fallback. The cumulative Phase 14 UI now adds the ability screen, wheel renderer, HUD, command palette, and accessibility controls. Previous, Next, and Ability Wheel cycle assigned slots. Use Selected activates the selected slot. A direct slot activates its assigned active ability or changes its assigned toggle.

The complete chat fallback is available under `/pskills ability`.

- `list` reports every definition and ownership state.
- `info` reports kind, target, costs, timing, effects, and ordered actions.
- `status` reports all eight assignments, selection, toggles, charges, and cooldowns.
- `assign`, `unassign`, `select`, `toggle`, and `activate` use the same server runtime as network input.

All output is ordinary readable chat text suitable for keyboard use and narration.

## Reload and failure behavior

Staging rejects unknown class granted abilities, invalid lifecycle combinations, invalid registry targets, excessive counts, duplicate ids, and definitions whose reconciliation exceeds transaction ceilings. Publication uses the existing atomic pack barrier. Login and definition reconciliation restore valid passive and toggle effects without replaying transition actions.

Unknown owned ability and assignment ids remain retained but inert progression identities for diagnosis, restoration, and migration. Known assignments that lose ownership or become impossible for their lifecycle reconcile safely. Corrupt or over capacity data remains quarantined. No client supplied target, cost, cooldown, charge, or action value is authoritative.

## Deferred scope

The cumulative beta includes the Phase 14 ability panel, rendered wheel, HUD, guide pages, and accessibility UI, Phase 15 provider capability contracts, and Phase 17 reactive procs, custom resources, formulas, combos, and digest pinned loadouts. Optional physical mod actions still require a tested adapter from the compatibility matrix.
