# Phase 12 Verification Record

Status: automated Phase 12 beta checkpoint complete. Rendering, narration, real input, restart, and multiplayer checks remain folded into the final Phase 9 through Phase 19 mass test.

This record covers ability ownership, fixed assignments, selection, toggles, source owned persistent effects, named currency and vanilla costs, cooldown groups, charges, recharge, server targeting, the Core native action subset, sanitized protocol version 4 state, fixed key mappings, and chat accessibility. It does not claim the Phase 14 ability screen, rendered wheel, HUD, provider actions, or Creator reactive graphs.

## Required commands

```text
./gradlew cleanTest test --no-daemon
./gradlew verifySchemaArtifacts --no-daemon
./gradlew clean build --stacktrace --no-daemon
./gradlew runGameTestServer --stacktrace --no-daemon
bash .ci/smoke-server.sh
bash .ci/smoke-client.sh
bash .ci/verify-release-jar.sh releases/phase-12/progressiveskills-phase-12.jar
```

## Automated gate

| Check | Status | Evidence |
| --- | --- | --- |
| Ability compiler and canonical codec | pass | Passive, toggle, and active definitions compile deterministically. Invalid lifecycle combinations, missing registry targets, oversized attribute projections, and unusable active definitions fail staging. |
| Ownership and reconciliation | pass | Multiple grant owners preserve one ability. Missing and disabled definitions retain inert identity. Class changes and resulting ability projections share one atomic cascade. |
| Aggregate atomicity | pass | Two individually legal passive modifiers that exceed the physical aggregate bound reject the second class selection without committing ownership, costs, effects, or a revision. |
| Assignment and toggle transactions | pass | Assign, move, unassign, select, and toggle are replay safe, bounded, revision pinned, and source owned. |
| Activation transactions | pass | Ownership, assignment, cooldown, charge, named currency, hunger, experience, target, and actions validate before one commit. Rejection spends nothing. |
| Cooldown and recharge | pass | Shared groups, persisted ready ticks, charge consumption, and bounded online recharge are deterministic. Reserved internal balance ids cannot be authored as currencies. |
| Physical action bridge | pass | Hunger, experience, message, heal, and vanilla effect paths preflight and execute through the transaction action boundary. |
| Projection and visible state | pass | Protocol version 4 definitions, ownership, toggles, charges, cooldowns, assignments, and selection round trip without raw owners, provenance, receipts, executors, or internal balances. |
| Intent guards and dispatcher | pass | Every assign, unassign, select, toggle, and activate intent uses canonical payloads behind session, replay, revision, rate, and quarantine guards. |
| Client controls and commands | pass | Wheel, previous, next, use selected, eight direct mappings, and all `/ps ability` chat fallbacks route synchronized authoritative state. |
| Full unit and property suite | pass | 300 tests and 6300 property tries completed with zero failures, errors, or skips. |
| Schema artifacts | pass | The 50 schema editor entries and generated Markdown match the committed Core registry byte for byte. |
| NeoForge GameTest | pass | One cumulative required test passed with 12 live starter definitions, two server players, chat commands, ownership, toggles, active costs and effects, cooldown rejection, plus entity and block target validation. |
| Dedicated server and client smoke | pass | Dedicated server readiness and headless client title screen readiness passed their forbidden error scans. |
| Release JAR | pass | `releases/phase-12/progressiveskills-phase-12.jar`, 1376624 bytes, SHA 256 `e182381baf7f12d209884b2d22f5b5eb0ba11966c6830c4c0b5092149afa8932`. Archive verification passed. |

## Focused fallback checklist

Use the exact committed Phase 12 checkpoint JAR only if the final cumulative build fails or ability behavior needs isolation. Back up the world and pack directory first.

1. Start a cheats enabled temporary world with the Phase 12 starter pack.
2. Confirm `/ps ability list` shows the starter passive, toggle, and active abilities with readable kind and ownership state.
3. Select the Warrior class and confirm Warrior Guard and Second Wind become owned. Select Scholar and confirm the Warrior plus Scholar synergy adds Combat Insight without losing either direct grant.
4. Run `/ps ability info progressiveskills:second_wind`. Verify target, cooldown, charges, costs, and ordered actions match the starter TOML.
5. Assign Second Wind to slot one, select slot one, and confirm `/ps ability status` reports both assignment and selection.
6. Activate slot one. Verify its message, heal, speed effect, hunger cost, one consumed charge, and cooldown.
7. Activate again during cooldown and verify rejection does not spend another cost or charge.
8. Wait for recharge and verify the charge and cooldown status recover on the server.
9. Assign and toggle Warrior Guard. Verify its armor and guarding flag appear only while on and survive relog.
10. Rebind Previous Ability, Next Ability, Use Selected Ability, and one direct slot in Controls. Verify keyboard input reaches the expected assigned ability.
11. Verify an empty direct slot reports a readable message and performs no mutation.
12. Remove one of two owners of a shared ability and verify the ability remains owned. Remove the last owner and verify assignment and effects reconcile safely.
13. Reload after disabling an owned ability. Verify ownership and retained assignment remain diagnosable while activation stops and spends nothing.
14. Fill an entity and block target scenario. Verify out of range and line of sight failures spend nothing.
15. Join with two clients. Verify one player cannot activate, assign, or observe private ability state for the other player.
16. Use narration with `/ps ability status` and verify every slot, toggle, charge, and cooldown result is understandable without color.

Record the exact command, expected result, observed result, relevant log excerpt, and whether a relog or restart changes the result for every failure.
