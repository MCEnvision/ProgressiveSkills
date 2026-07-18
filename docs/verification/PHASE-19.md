# Phase 13 through Phase 19 verification record

Status: approved cumulative Phase 19 release. Automated verification is complete and the user approved promotion to `main`.

This checkpoint contains the cumulative carrier, baseline UI, compatibility provider, hardening, Creator, multiplayer, and Studio implementation from Phases 13 through 19. External optional mod adapters remain unavailable unless an exact supported artifact is present and tested. Native and absent provider behavior stays functional without those mods.

The command root is `/pskills`. ProgressiveSkills does not register `/ps`, so another installed mod can own that shorter command without a collision.

Every ProgressiveSkills screen performs the menu background blur once before rendering its panels, text, widgets, and tooltips. Enabling Menu Background Blur therefore keeps the world behind the screen blurred while the complete menu remains sharp.

## Automated gate

| Check | Status | Evidence |
| --- | --- | --- |
| Clean build | pass | `./gradlew clean build verifySchemaArtifacts --no-daemon --stacktrace` completed with no failure. |
| Unit and property tests | pass | 377 tests completed with zero failures and zero errors. |
| Architecture and schema | pass | Package boundaries passed and both checked schema artifacts match fresh generation byte for byte. |
| Performance contract | pass | The locked PERF fixture compiled 10000 real rules and 50000 matcher routes and passed its 40 and 100 player percentile budgets. |
| Reproduction and security | pass | Replay uses captured bundle state. Studio pack signatures use portable Ed25519 identities and unsafe imports fail closed. |
| Multiplayer | pass | Provider backed parties, revision checked shared progress, contribution receipts, assists, privacy, seasons, and PvP anti boosting passed. |
| NeoForge GameTest | pass | Both required cumulative GameTests passed. |
| Dedicated server | pass | Dedicated server startup and forbidden error scan passed. |
| Client | pass | Headless client reached the title screen and passed the forbidden error scan. |
| Release JAR | pass | `progressiveskills-phase-19.jar`, 2076680 bytes, SHA 256 `61422d5bf4bab04e9dc024bc632724736b0602a79ffe4b28756017131e089d93`. Archive verification passed. |

## Player mass check

Use only `releases/phase-19/progressiveskills-phase-19.jar` for this test. Back up existing worlds and remove every older ProgressiveSkills JAR before starting.

1. Create or open a cheats enabled test world and run `/pskills check start`.
2. Perform the check shown in chat. Use `/pskills check pass <note>` when it works or `/pskills check fail <note>` when it does not.
3. Use `/pskills check status` at any time to resume or confirm progress. The checklist persists with the world.
4. Open the Progression screen with `P`, choose Tests, and complete the shorter visual and input Test Center checklist too.
5. Test once with a second client for party privacy, shared credit, stale requests, and replay protection.
6. Run `/pskills check finish` after the final check, then `/pskills check export`.
7. Send the exported report and the latest client and server logs for every failed row.

This checkpoint is the current approved `main` release and is preserved by the lightweight `phase-19` tag.
