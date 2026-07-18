# Phase 10 Verification Record

Status: automated beta checkpoint complete. Rendering, narration, real input, and multiplayer remain in the final mass test.

This record covers single rank acyclic Core trees, exact historical paid costs, atomic purchase and transitive cascade refund, persistent attribute ownership, authoritative commands and intents, sanitized ownership sync, reconciliation, and the first accessible tree screen. It does not claim ranked nodes, formulas, exclusions, generated layouts, advanced dependency policies, loadouts, simulations, or Creator tree features.

## Scope to verify

- typed tree and node definitions with stable IDs and lineage
- global and skill bound Core scopes
- one rank and one literal positive named currency cost per node
- deterministic DAG validation and reverse dependency indexing
- AND prerequisites through `requires`
- OR prerequisites through `requires_any`
- bounded direct minimum actor skill levels
- immutable historical paid cost records
- atomic spend, record, grants, persist, and visible sync
- source owned attribute grants shared safely with other owners
- deterministic transitive cascade preview and refund
- exact refund from historical currency and amount after a live cost change
- stale definition, state, request, and preview rejection
- command and packet parity through one server runtime
- paid cost and ownership persistence across relog, death copy, and restart
- reload reconciliation without purchase reward replay
- sanitized tree projection and visible owned ranks
- keyboard, focus, narration, noncolor state, scale, and resize behavior
- hard graph, ledger, transaction, preview, payload, and UI bounds
- clear rejection of unsupported Creator fields

## Required commands

```text
./gradlew cleanTest test --no-daemon
./gradlew verifySchemaArtifacts --no-daemon
./gradlew clean build --stacktrace --no-daemon
./gradlew runGameTestServer --stacktrace --no-daemon
bash .ci/smoke-server.sh
bash .ci/smoke-client.sh
bash .ci/verify-release-jar.sh releases/phase-10/progressiveskills-phase-10.jar
```

## Automated evidence

| Check | Status | Evidence |
|---|---|---|
| Tree TOML and canonical codec suite | pass | `TreeDefinitionTest` and loader tests cover Core round trip, stable ordering, unsupported fields, and dangling IDs. |
| Graph validation suite | pass | `TreeDefinitionTest` covers AND, OR, mixed graphs, cycles, self edges, collisions, stable order, and hard limits. |
| Purchase transaction suite | pass | `TreeProgressionTest` covers affordability, minimum balances, duplicate ownership, source grants, stale state, replay, and atomic rejection. |
| Historical ledger codec suite | pass | `PaidCostLedgerTest` and `PaidCostNbtCodecTest` cover deterministic persistence, exact removal, capacity, malformed records, and migration. |
| Cascade refund unit and property suite | pass | `TreeProgressionTest` covers deterministic closure and historical refund legs. `TreeProgressionPropertyTest` proves currency and ledger conservation for 500 generated cases. |
| Cost change regression | pass | The refund suite purchases at one price, changes the live definition, and returns only the exact historical payment. |
| Coownership regression | pass | Tree and transaction tests prove revoking one node source preserves contributions owned by another source. |
| Command and intent parity | pass with final transport check | `NetworkRuntimeIntentDispatcherTest` covers purchase, refund preview, refund confirmation, inactive state rejection, deterministic request identities, and outcomes. GameTest exercises commands. Real multiplayer transport ordering remains in the final mass test. |
| Network codec and replay suite | pass | Network codec, client state, session, and runtime tests cover sanitized projections, owned ranks, bounded previews, request replay, stale state, malformed payloads, and resync. |
| Reconciliation suite | pass | `TreeProgressionReconciliationTest` covers compatible grant changes, dependency cascades, blocked refunds, orphan sources, stale definitions, and repeat no op reconciliation. |
| Screen accessibility implementation | pass | The tree screen provides keyboard traversal, deterministic ordering, explicit text states, bounded scrolling, contextual screen and action narration, result feedback, and focus restoration. Client startup smoke passed. |
| Screen accessibility experience | final mass test | Actual rendering, narrator behavior, resize, GUI scale, and physical keyboard input require the final player run. |
| NeoForge GameTest | pass | One required GameTest earns currency, buys a node and dependent, previews and confirms the cascade, and verifies exact resulting state. |
| Persistence capacity and restore safety | pass | Capacity tests prove full attachment preflight before mutation, deterministic bounded replay retention, durable delivery receipts, maximum default paid ledgers, and failed restore protection. |
| Full unit and property suite | pass | Clean isolated suite passed with 229 tests, 6,000 property tries, and zero failed, errored, or skipped tests. |
| Generated metadata | pass | `verifySchemaArtifacts` regenerated tree schemas, diagnostics, reference Markdown, and editor JSON byte for byte. |
| Dedicated server and client smoke | pass | Dedicated server reached ready state and the client reached its title or test world readiness marker without forbidden errors. |
| Release JAR | pass | `releases/phase-10/progressiveskills-phase-10.jar`, 1,041,886 bytes, SHA 256 `1079ebe16cf4b13561e5b31a52a211652160355bbc349adbf19c1ffff5e91ff6`. Archive verification passed. |
| Player fallback checkpoint | final mass test | The focused checklist remains available as a recovery path. Its manual checks are folded into the final combined Phase 9 through Phase 19 mass checklist. |

## Implementation acceptance checklist

- [x] Core accepts only single rank acyclic nodes with literal positive costs.
- [x] Tree and node IDs are stable and graph order does not define persistence identity.
- [x] Every prerequisite and bind resolves against the same staged registry.
- [x] `requires` uses AND semantics and `requires_any` uses OR semantics.
- [x] Mixed AND and OR cascade closure reaches a deterministic fixed point.
- [x] Minimum skill levels use the authoritative progression snapshot.
- [x] Every purchase records original currency and amount atomically with the spend.
- [x] Duplicate paid instance IDs and paid ledger capacity exhaustion fail before spending.
- [x] Purchase grants use source owned entitlement identities.
- [x] A failed projection, persistence check, or stale revision commits no partial state.
- [x] Refunds load immutable paid records and never use the current configured price.
- [x] Dependents are revoked before prerequisites in stable reverse topological order.
- [x] A complete cascade commits once or not at all.
- [x] Buy then cascade refund cannot increase any currency.
- [x] Replayed command or packet identities cannot buy or refund twice.
- [x] A preview is nonmutating and cannot authorize a stale confirmation.
- [x] Commands and network intents call one authoritative server service.
- [x] Client payloads cannot supply target, price, refund, grant, or requirement outcomes.
- [x] Visible sync exposes owned node ranks but not paid records or raw entitlement owners.
- [x] Reload and login reconciliation preserve historical costs and do not replay rewards.
- [x] A renamed or missing purchased node follows an explicit fail closed migration policy.
- [x] The first tree screen implements keyboard operation and narrator labels.
- [x] State is conveyed with text and focus state rather than color alone.
- [x] Every graph, ledger, closure, mutation, payload, preview, and UI collection has an enforced hard bound.
- [x] Unsupported Creator tree fields fail validation instead of receiving placeholder behavior.
- [x] Clean build, GameTest, smokes, generated artifacts, and release archive checks pass.
- [ ] Complete the real rendering, narration, input, and multiplayer checks in the final mass test.

## Focused fallback checklist

Use only the exact committed Phase 10 checkpoint JAR after its checksum and automated rows are recorded. Back up the world and pack directory. Use a cheats enabled temporary test world and a copy of the starter pack.

1. Run `/pskills validate`, `/pskills status`, `/pskills network status`, `/pskills tree list`, and `/pskills skill get progressiveskills:physique`. Record the live generation, state revision, Physique level, global points, owned nodes, and relevant attributes.
2. Open the tree screen with its registered key. Without using the mouse, select every tree and node, open the detail area, return to the first node, and close and reopen the screen. Confirm visible focus, logical order, readable text at the smallest supported window, and Owned, Available, or Locked text independent of color.
3. Enable the narrator. Focus a locked node, an available node, the purchase action, and the refund action. Confirm each narration includes the node name, state, cost, requirement summary, and action. Record any missing, repeated, or misleading narration.
4. With insufficient points, attempt the first node through the screen and its command. Both must reject without changing currency, ownership, attributes, state revision through a committed mutation, or paid cost count.
5. Earn the required Physique level and global points through the ordinary XP path. Buy the first node through the screen. Confirm exactly the displayed cost is removed, the node becomes Owned, its attribute grant appears once, and reconnecting preserves all three facts.
6. Repeat the same request if the UI permits a retry, then issue the same command operation only through the documented replay test surface. Confirm the node cannot be charged or granted twice.
7. Buy a second node that depends on the first. Preview refunding the first node. Confirm the preview lists the dependent before the first node and lists exact refund legs, while currency, ownership, attributes, and state revision remain unchanged.
8. Cancel the preview and reopen it. Then earn or spend XP or currency so the state revision changes before confirmation. Confirm the old token is rejected as stale, the client synchronizes, and no node is removed or refunded.
9. Create a temporary mixed graph with one node requiring all prerequisites and one node accepting either of two prerequisites. Buy both OR alternatives and the dependent. Preview removing one alternative. The OR dependent must remain. Preview removing the final remaining alternative. The dependent must enter the cascade.
10. Record the original cost and currency for a purchased test node. Change only its live configured cost to a different value through dry run, diff, and publish. Preview and confirm its refund. Confirm the returned amount and currency equal the original paid record, not the new configuration.
11. Give a skill grant and a tree node grant ownership of the same attribute and operation. Refund the tree node. Confirm only the tree contribution disappears and the skill contribution remains across relog.
12. In a temporary pack copy, remove or invalidate a prerequisite for an owned dependent and review the publish impact. The implementation must either reject publication before the live swap or reconcile through the documented cascade policy with exact historical refunds. It must not silently delete payment history, retain an invalid active grant, or replay a purchase reward.
13. Restart the full game and server process. Run the status and tree commands again, reopen the screen, and confirm owned nodes, historical refund behavior, attributes, focus, and narrator output remain correct.
14. In separate dry runs, attempt a cycle, duplicate node ID, dangling prerequisite, zero or negative cost, formula cost, multiple ranks, exclusion group, and oversized cascade. Each must fail staging without changing the live generation.
15. Refund all remaining temporary nodes through reviewed previews. Confirm the ending currency equals the expected starting balance plus legitimate earned points, all temporary node sources are gone, and no refund can be repeated.
16. Restore the starter pack, publish the cleanup through the reviewed reload flow, run `/pskills validate`, and attach the complete game log plus command, screen, narrator, stale preview, and before and after balance evidence.

Automated tests remain required for exact CAS races, malformed codecs, deterministic map ordering, transaction rollback, large closure bounds, and property based no creation invariants. The fallback checklist does not replace those rows.
