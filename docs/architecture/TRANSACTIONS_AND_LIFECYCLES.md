# Transactions and Output Lifecycles

Status: Phase 4 implemented as a side-neutral transaction core plus a session-only server demonstration. Player attachment persistence begins in Phase 5.

## Scope and boundary

Phase 4 implements the mutation and lifecycle machinery required before skills, XP, trees, classes, or abilities may safely change player progression:

- state- and definition-pinned transaction plans;
- checked long balance mutations with explicit floors and ceilings;
- bounded, fully expanded root/child cascade plans;
- typed source ownership and deterministic persistent-value resolvers;
- idempotency results and exact transition receipts;
- persistent recompute that cannot execute transition actions;
- prevalidated physical projection followed by one state commit;
- deterministic post-commit transition execution and honest failure status;
- bounded audit retention; and
- rollback only for a retained action-free transaction with no later state mutation.

`common.transaction` contains only side-neutral plans, identities, immutable results, and the bounded coordinator. Minecraft attributes, inventory delivery, player lookup, events, and commands remain under `server.transaction` and `server.command`.

Phase 4 does not claim player-save persistence, death copying, offline mutation, networking, gameplay schemas, or skill/XP behavior. Its server state deliberately lasts only for the current server session. Phase 5 replaces that temporary authority with the versioned player attachment and migration boundary.

## Transaction pipeline

Every `CascadePlan` is already fully expanded when submitted. The coordinator:

1. Derives a stable transaction UUID from the target and idempotency key.
2. Returns the cached terminal result immediately for an exact replay.
3. Checks exact-ledger capacity, live definition generation/digest, and target state revision.
4. Applies every checked balance and ownership mutation to copied state in root/child order.
5. Resolves all persistent owners and computes one effective projection diff.
6. Prevalidates the complete physical projection and every still-unreceipted transition action.
7. Reserves exact receipt capacity.
8. Installs copied state once, increments the state revision once, and applies the prevalidated atomic physical projection; an atomic projection exception restores the copied state boundary before any transition action.
9. Executes transition actions in declared order, recording success receipts and isolating failures according to `continue` or `stop`.
10. Stores the stable idempotency result and bounded audit record.

No child mutation executes recursively. Oversized steps/cascades are rejected during construction rather than truncating a committed prefix.

## Revision and idempotency rules

`TransactionPlan` carries an expected state revision and `DefinitionRevision(generation, semanticDigest)`. Either mismatch rejects the complete plan before projection or mutation.

An `IdempotencyKey` is a bounded caller identity. The service stores every terminal result, including ordinary rejections, and returns the same transaction UUID and outcome for a replay. Exact idempotency entries are not evicted; a full ledger rejects new work before mutation.

The session runtime is capped at 256 accounts, 512 exact receipts and 512 idempotency results per account, and 256 retained audit records per account. These are temporary Phase 4 safety ceilings, not the Phase 5 persistence configuration contract.

The Phase 4 demo uses a stable primary key. Running `/ps lifecycle demo` twice therefore cannot add another point, item, health owner, revision, or audit mutation.

## Checked balances and cascades

Balances are signed `long` values. Every `BalanceMutation` declares its allowed post-mutation minimum and maximum and uses checked addition. A failure leaves every balance, ownership map, projection, receipt, and revision unchanged.

One transaction step permits at most 256 balance/ownership mutations and 128 actions. A cascade permits at most 64 total steps, 512 total mutations, and 256 total actions. All limits are hard rejection points.

## Persistent ownership and recompute

Persistent values use:

```text
EntitlementKey(target type + target id)
  -> GrantSourceId(owner kind + owner id + nested grant id)
  -> EntitlementContribution(value + resolver)
```

Phase 4 supplies `additive`, `highest`, `lowest`, and `boolean_union`. Every source for one entitlement must use the same resolver. Removing one source removes only that contribution; the physical value changes only when the resolved result changes.

`recompute` reads existing ownership, resolves the desired values, and projects only the diff. It has no transition-action input and cannot create points, items, commands, receipts, or audit mutations. Tests explicitly submit point, item, and command-shaped outputs, then replay and recompute them without duplication.

The server demonstration projects only `progressiveskills:attribute[minecraft:generic.max_health]` through the exact modifier ID `progressiveskills:phase4_demo_health`. The projector removes or updates only that owned modifier and clamps current health after a reduction. It cannot remove another mod's modifier.

## Transition actions and receipts

A `TransitionAction` declares:

- registered action type;
- typed `GrantSourceId`;
- bounded payload and positive amount;
- `always`, `once_per_transaction`, or `once_per_character` repeat policy;
- `effectively_once`, `at_least_once`, or `best_effort` delivery contract; and
- `continue` or `stop` failure behavior.

Receipt-required actions reserve exact-ledger capacity before commit. A successful action records its definition revision, transaction ID, delivery time, contract, and adapter detail. Existing receipts skip delivery deterministically. Permanent receipts are not age/count evicted; full capacity rejects the transaction.

Physical actions execute after state commit. A late adapter failure therefore produces `COMMITTED_WITH_ACTION_FAILURES`; it never pretends the external action was rolled back. Phase 4 has no durable outbox or automatic crash retry. Those persistence semantics begin in Phase 5.

The in-game fixture supports only a prevalidated one-stack item delivery. Unknown types/items, excessive amounts, offline targets, and full inventories reject before state commit.

## Audit and rollback boundary

Each retained audit record contains transaction, actor/target, cause/reason, definition revision, completion time, before/after state revisions, status, balance mutations, persistent projection changes, transition results, and reversibility.

Only an action-free transaction gets a retained rollback snapshot. Rollback is rejected when:

- the original transaction had a transition action;
- the snapshot aged out with bounded audit retention;
- another state mutation occurred afterward; or
- physical re-projection cannot validate/apply atomically.

A successful rollback is a new transaction with a new, greater state revision. It never removes receipts, erases audit history, or rewinds revision identity.

## Operator commands

| Command | Purpose |
|---|---|
| `/ps lifecycle status` | Show session revision, demo points, health bonus/owner count, receipts, and audit count. |
| `/ps lifecycle demo` | Commit or replay the primary point + health-owner + gold-ingot transaction. |
| `/ps lifecycle coowner` | Add a second equal owner without stacking the `highest` value. |
| `/ps lifecycle recompute` | Re-resolve persistent owners and prove zero transition execution. |
| `/ps lifecycle revoke primary` | Remove only the primary health source. |
| `/ps lifecycle revoke secondary` | Remove only the secondary health source. |
| `/ps lifecycle audit` | Show the latest bounded transaction records. |
| `/ps lifecycle selftest` | Run a player-independent executable invariant proof; usable from GameTest/console. |

All player-mutating lifecycle commands require an in-game operator and execute on that player. The fixture is a development checkpoint, not pack content or a supported player progression system.

## Manual in-game checkpoint

Use a cheats-enabled development world with at least one free inventory slot:

1. `/ps lifecycle status` — expect revision 0, 0 demo points, 0 health bonus, and 0 owners.
2. `/ps lifecycle demo` — expect revision 1, 1 point, a +4 max-health bonus (two hearts), one owner, one receipt, and exactly one gold ingot.
3. Run `/ps lifecycle demo` again — expect `Idempotent replay`, the same transaction ID/revision, and no additional point, item, owner, or receipt.
4. Run `/ps lifecycle recompute` twice — expect 0 persistent changes and 0 transition actions both times.
5. `/ps lifecycle coowner` — expect revision 2 and two owners, but the `highest` resolver keeps the bonus at +4 rather than +8.
6. `/ps lifecycle revoke primary` — expect revision 3, one owner, and the +4 bonus still active.
7. `/ps lifecycle revoke secondary` — expect revision 4, zero owners, and the bonus removed.
8. `/ps lifecycle audit` — expect four committed mutation records; replay and recompute do not add mutation records.

Restarting the server clears this Phase 4 session fixture. That is intentional evidence that Phase 5 persistence has not been silently claimed.
