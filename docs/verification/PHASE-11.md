# Phase 11 Verification Record

Status: automated Phase 11 beta checkpoint complete. Player rendering, narration, real input, restart, and multiplayer checks remain folded into the final Phase 9 through Phase 19 mass test.

This record covers typed class slots, weighted capacity, class selection, respec, atomic swap, source owned entitlements, named synergies, receipt protected starter kits, sanitized projection, strict protocol version 3 intents, chat accessibility, persistence, and reload suspension. It does not claim the Phase 14 class panel, Phase 12 ability execution, Phase 15 physical provider adapters, or Creator class features.

## Scope to verify

- stable typed class slot, class, synergy, grant, cost, and starter kit definitions
- positive slot capacity and zero cost background class support
- checked weighted occupancy per slot
- enabled, access, skill, node, class, and coexistence gates
- deterministic class prerequisite cycle rejection
- selection cost as a nonrefundable sink
- current configured respec and atomic swap costs
- preview digest, definition revision, state revision, and request replay protection
- source owned attribute, ability, spell, stage, tree access, and class access grants
- two sources owning one target without premature revoke
- synergy activation and revoke from the complete active class set
- once per character starter kit receipt across respec, replay, relog, and restart
- active or suspended retained selections after safe reload reconciliation
- sanitized slot, class, synergy, starter item, grant, and visible selection projection
- no raw source owners, receipt bodies, audit bodies, or provider secrets on the client
- strict class select, respec preview, respec confirm, swap preview, and swap confirm payloads
- protocol version 3 negotiation and required Core class feature
- keyboard and narrator friendly command output without a Phase 11 screen
- hard catalog, graph, transaction, persistence, projection, preview, and packet bounds
- explicit rejection of Creator class fields

## Required commands

```text
./gradlew cleanTest test --no-daemon
./gradlew verifySchemaArtifacts --no-daemon
./gradlew clean build --stacktrace --no-daemon
./gradlew runGameTestServer --stacktrace --no-daemon
bash .ci/smoke-server.sh
bash .ci/smoke-client.sh
bash .ci/verify-release-jar.sh releases/phase-11/progressiveskills-phase-11.jar
```

## Automated evidence

| Check | Status | Evidence |
| --- | --- | --- |
| Class TOML and canonical codec suite | pass | Slot, class, grant, spell policy, synergy, cost, starter kit, canonical ordering, unknown fields, and deferred fields passed. |
| Catalog and graph validation suite | pass | Missing references, cycles, access targets, coexistence, capacity, unique grants, lineage, and mutation bounds passed. |
| Selection transaction suite | pass | Access, requirements, affordability, zero and positive costs, weighted capacity, source grants, replay, and atomic rejection passed. |
| Respec and swap suite | pass | Nonrefunding selection costs, current respec charges, preview digests, atomic replacement, disabled policy, and stale confirmation passed. |
| Entitlement coownership regression | pass | Removing class and synergy sources preserved independent owners of the same target. |
| Synergy suite | pass | Complete active class sets activate one stable source, while suspension and respec revoke only that source. |
| Starter kit receipt suite | pass | One canonical batch action uses one permanent class receipt and remains stable across duplicates, reorder, replay, respec, and reselection. |
| Persistence and reconciliation suite | pass | Restore, forged marker cleanup, active and suspended state, capacity recovery, compatible grant updates, and no reward replay passed. |
| Definition projection suite | pass | Bounded class, slot, starter item, grant semantics, synergy round trip, and forbidden authority field checks passed. |
| Visible state and delta suite | pass | Active, suspended, missing definition, semantic delta, and zero slot cost behavior passed. |
| Intent codec and replay suite | pass | Strict shapes, followups, stale session and state, malformed input, replay, future jump, rate, and quarantine guards passed. |
| Network runtime dispatcher suite | pass | Select, respec preview and confirm, swap preview and confirm, inactive gate, deterministic keys, and result mapping passed. |
| Command and chat accessibility suite | pass | GameTest exercised list, info, preview, select, entitlement, and respec routes. Output uses stable explicit text states and blockers. |
| NeoForge GameTest | pass | One required end to end test passed selection, weighted capacity, grants, synergy, coownership, one receipt kits, respec, and reselection. |
| Full unit and property suite | pass | 266 tests and 6000 property tries. Zero failed, errored, or skipped. |
| Generated metadata | pass | Schema Markdown, diagnostics, and editor JSON regenerated and verified byte for byte. |
| Dedicated server and client smoke | pass | Dedicated server readiness and client title screen readiness passed their forbidden error scans. |
| Release JAR | pass | `releases/phase-11/progressiveskills-phase-11.jar`, 1200416 bytes, SHA 256 `9c65bcde0fb39581ccd80e2148c51544b48bef5abfc65359c5d9803a9c01558b`. Archive verification passed. |
| Player fallback checkpoint | final mass test | Run only if the final cumulative build fails or Phase 11 behavior needs isolation. |

## Implementation acceptance checklist

- [x] Core accepts only bounded explicit class slots, classes, grants, and synergies.
- [x] Every class uses one known pack defined slot.
- [x] Slot capacity is positive and checked weighted occupancy includes zero cost background classes safely.
- [x] Every prerequisite and target resolves inside the same staged registry.
- [x] Class prerequisite cycles and self requirements fail staging.
- [x] Exclusive tags reject deterministic coexistence conflicts.
- [x] Selection cost is sunk once and never implicitly refunded.
- [x] Respec and swap use current server configured charges.
- [x] Swap commits the complete replacement once or not at all.
- [x] A stale preview cannot authorize a respec or swap.
- [x] Replayed command or packet identities cannot charge, grant, revoke, or deliver twice.
- [x] Every persistent grant has one stable source identity.
- [x] Removing one source preserves other owners of the same target.
- [x] Synergies activate only from the complete active required class set.
- [x] Starter kit delivery uses one stable once per character receipt.
- [x] Respec, swap, relog, restart, and reorder do not replay a starter kit.
- [x] Reload reconciliation never replays acquisition actions.
- [x] Invalid retained selections become visible and suspended instead of being silently deleted.
- [x] Visible sync exposes selected class slot use and active or suspended state only.
- [x] Definition projection discloses truthful costs, starter item counts, and grant semantics.
- [x] Projection excludes raw source owners, receipts, audits, provenance, and provider secrets.
- [x] Protocol version 3 class intents are strict, bounded, session bound, and replay guarded.
- [x] Commands and packet intents enter one authoritative server runtime.
- [x] Command output is keyboard usable, narrator friendly, stable, and explicit without color only meaning.
- [x] The Phase 14 class panel remains deferred and no placeholder screen is claimed.
- [x] Every catalog, graph, transaction, ledger, preview, payload, and visible collection has an enforced hard bound.
- [x] Unsupported Creator class fields fail staging.
- [x] Clean build, tests, GameTest, smokes, generated artifacts, and release archive checks pass.
- [ ] Complete the practical class, command, restart, and multiplayer checks in the final mass test.

## Focused fallback checklist

Use only the exact committed Phase 11 checkpoint JAR after its checksum and automated rows are recorded. Back up the world and pack directory. Use a cheats enabled temporary world and a copy of the Phase 11 starter pack.

1. Run `/ps validate`, `/ps status`, `/ps network status`, `/ps class list`, and `/ps class entitlements`. Record generation, state revision, every slot capacity and occupancy, selected class status, global points, and effective class grant summaries.
2. Run `/ps class info` for every starter class. Confirm the output names slot, slot cost, access rule, skill, node and class requirements, exclusive tags, selection and respec costs, starter kit item counts, grant type, target, operation, resolver and value, plus synergies. Confirm the order remains the same after reconnect.
3. Use only the keyboard and chat history to navigate every class command. Enable narrator chat output. Confirm Active, Suspended, Available, and Blocked are written explicitly and no meaning depends only on color, hover, or a mouse.
4. With insufficient currency or requirements, preview and attempt a class selection. Confirm the public blockers agree, no currency changes, no class is selected, no entitlement changes, no starter item appears, and no committed state revision is produced.
5. Satisfy the requirements and earn the exact selection cost through normal progression. Preview then select the class. Confirm the exact cost is removed once, weighted occupancy increases by the declared amount, state becomes Active, each grant appears once, and each starter item count is delivered once.
6. Repeat the same visible selection action and any documented replay test. Confirm currency, ownership, entitlements, starter items, receipts, and state do not duplicate.
7. Select a zero cost background class. Confirm it is visible and active, consumes zero capacity, still enforces requirements and conflicts, persists across reconnect, and activates its grants normally.
8. Give a skill or tree source and a class source ownership of the same attribute or access target. Respec the class through a fresh preview and digest. Confirm only the class contribution disappears and the other source remains across reconnect.
9. Select all required classes for a starter synergy. Confirm the synergy appears only after the last required class is active, its grants appear once, and removing one required class revokes only the synergy source.
10. Preview a respec. Confirm it lists the affected class and synergies, exact current respec charge, grant changes, and a digest without mutation. Change XP or currency before confirmation. Confirm the old digest is stale and nothing is removed or charged.
11. With a full slot, preview an allowed swap. Confirm the preview accounts for removed and replacement weights atomically and lists both respec and selection charges. Confirm it and verify there is no intermediate over capacity or grant gap, and one failed debit cannot leave either half applied.
12. Attempt the same swap in a slot whose swap policy is disabled, with a nonrespeccable class, with overlapping exclusive tags, and with a missing replacement prerequisite. Each must reject without a partial debit, removal, grant, kit, or state change.
13. Respec then reselect a class with a starter kit. Confirm the selection cost applies again if configured, but the once per character starter kit never delivers again. Restart the game and repeat the check.
14. In a temporary pack copy, lower slot capacity below current occupancy and publish through dry run, diff, and reviewed publish. Confirm the affected class remains selected and visible as Suspended, its grants and dependent synergies are inactive, no class is silently deleted, no selection cost is refunded, and no starter kit replays.
15. Restore compatible capacity and publish again. Confirm the retained class returns to Active and its persistent grants reproject exactly once without replaying the starter kit.
16. In separate dry runs, attempt a missing slot, over capacity class weight, missing currency, class prerequisite cycle, self requirement, dangling node, duplicate grant ID, one class synergy, unsupported permanent spell learning, legacy global cap, rank, evolution, role, and loadout field. Each must fail staging without changing the live generation.
17. Inspect `/ps network status`, reconnect, and repeat one class preview. Confirm protocol 3, Active session, matching state revisions, and no stale preview survives a state change or reconnect.
18. Restore the starter pack, publish cleanup through the reviewed reload flow, run `/ps validate`, and attach the complete log plus before and after currency, occupancy, entitlement, starter kit, suspension, narrator, stale preview, and restart evidence.

Automated tests remain required for exact compare and swap races, source ownership, malformed codecs, deterministic ordering, transaction rollback, persistence ceilings, and property based accounting. The fallback checklist does not replace those rows.
