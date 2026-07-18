# Phase 8 Verification Record

Status: original Phase 8 gameplay checkpoint and all refined automated gates passed. The focused block-origin real-client checkpoint is pending.

This record covers the rule engine and anti exploit foundation, the first NeoForge block break binding, and persistent player-placement origin tracking. It does not claim Phase 9 predicates and formulas, arbitrary modded mover provenance, combat or crafting bindings, target or team attribution, or the full Creator rule surface.

## Implemented scope

- extensible frozen trigger, matcher, and provider registries
- stable rule, output, stack group, and multiplier identities
- canonical prefixed matchers, bare ID shorthand, negation, and custom-name opt in
- one registered `progressiveskills:block_break` NeoForge provider
- precompiled exact ID, namespace, translation-key, and tag routes
- fixed multiplier stage order and five multiplier group policies
- stable `sum`, `highest`, `first`, `exclusive`, and `diminishing` rule stacks
- tick, minute, and Minecraft-day caps
- first-time, cooldown, repeat-window, decay, and minimum multiplier memory
- fake-player denial by default
- configurable natural, creative-placed, survival-placed, automation-placed, and unknown origin policies
- safe natural and creative-placed default for omitted origin policies
- persistent bounded player-placement provenance with multi-place and piston transfer
- fail-closed unknown origin after capacity, decode, or piston-correlation failure
- bounded per-player event dedupe
- atomic XP and source-memory transactions
- persistent internal memory filtered from client state projection
- route pause and immutable table rebuild during definition publication
- `/pskills rule status` and `/pskills explain xp last`
- two non-overwriting starter routes for stone training and a first log reward
- generated rule schema metadata and six stable Phase 8 diagnostics

## Required commands

```text
./gradlew clean build --stacktrace
./gradlew runGameTestServer --stacktrace
bash .ci/smoke-server.sh
bash .ci/smoke-client.sh
.ci/verify-release-jar.sh
```

## Automated evidence

| Check | Status | Evidence |
|---|---|---|
| Rule engine unit suite | local pass | Registries, matchers, every multiplier and stack policy, cooldown at world tick zero, repeat decay, caps, first-time persistence, full-value disabled windows, safe origin defaults, customizable origin combinations, and atomic memory mutation pass. |
| Block provenance unit suite | local pass | Placement round trip, break consumption, piston transfer, destroyed-position removal, bounded capacity, and malformed-data fail-closed behavior pass. |
| Full unit and property suite | local pass | 165 tests passed with 0 failed, errored, or skipped. All eight architecture rules pass and the retained jqwik suite completes 4,000 tries. |
| Generated metadata | local pass | 28 schemas, 32 definition kinds, and 59 diagnostics. The rule schema exposes `allowed_block_origins`, and checked-in Markdown and editor JSON match byte for byte. |
| NeoForge GameTest | local pass | The one required real-server test proves placement-event classification, permitted creative placement, denied survival placement with origin explanation, natural routing and cooldown, and every prior Phase 8 invariant. |
| Client redaction | local pass | Internal hashed rule-memory balances are omitted from the visible network balance map. |
| Dedicated server and client smoke | local pass | The production-only server reached ready state and the production-only client reached the title screen; both error-marker gates passed. |
| Release JAR | local pass | 824,940 bytes; archive verification passed; SHA-256 `6107345b95b212e9260dec84f6dae9d51554ffc6bf072d56689804fcdd29c96e`. |
| Original real client checkpoint | pass | The supplied log loaded five definitions with no warnings, awarded 10 XP for stone and 5 XP for a repeated stone, awarded the first-log 20 XP once across a full relog, and published disable and restore generations successfully. |
| Block-origin client checkpoint | pending | Awaiting natural, creative-placed, survival-placed, customization, and full-value checks below. |

The exact checkpoint binary is committed at `releases/phase-8/progressiveskills-phase-8.jar` with its digest in the adjacent `checksums.txt`.

## Acceptance checklist

- [x] Rule, matcher, provider, output, stack, and multiplier identities are stable and validated.
- [x] Disabled and nonmatching routes return before transaction or diagnostic construction.
- [x] Literal fixed-point multiplier stages and stack groups resolve deterministically.
- [x] Invalid zero, negative, overflowing, and inconsistent rule values fail publication.
- [x] Cooldown, first-time, repeat, and cap memory are bounded and persistent.
- [x] XP and changed source memory commit in one ordinary progression transaction.
- [x] Fake players and duplicate event tokens fail closed.
- [x] Origin policy is configurable and defaults to natural plus creative-placed.
- [x] Survival placement persists across save and is consumed on break.
- [x] Vanilla piston movement transfers provenance without duplication.
- [x] Provenance corruption or capacity exhaustion makes untracked blocks unknown.
- [x] Internal anti exploit state cannot enter visible client balances.
- [x] Publication pauses old routes and rebuilds one immutable table from the new generation.
- [x] Regenerated schemas, full clean build, GameTest, smokes, and release archive pass after the refinement.
- [x] User completed the original real-client block route, persistence, and hot-reload checkpoint.
- [ ] User completed the focused block-origin and full-value checkpoint.

## Manual in-game checkpoint

Use a cheats-enabled development world. Existing installed rule files are not overwritten. An omitted `allowed_block_origins` still receives the safe natural and creative-placed default, but adding the line explicitly makes the pack intent clear.

1. Install the refined Phase 8 JAR and launch the same world. Run `/pskills validate`, `/pskills status`, and `/pskills rule status`. Expect five valid definitions, two enabled rules, and block provenance `Reliable true`.
2. Add `allowed_block_origins = ["natural", "creative_placed"]` under `[rule.anti_exploit]` in both starter rule files if it is absent. Publish through `/pskills reload --dry-run`, `/pskills diff`, and `/pskills reload --publish`.
3. Find stone in newly generated terrain, record Physique XP as `X`, and break it in survival. Expect `X + 10`. `/pskills explain xp last` must show origin `natural` and a committed 10 XP award.
4. Place stone in survival, wait at least six seconds, then break it. Expect no XP. The explanation must show origin `survival_placed` and outcome `block origin survival_placed is not allowed`.
5. Switch to creative, place stone, switch back to survival, wait at least six seconds, and break it. Expect 10 XP. The explanation must show origin `creative_placed` and a committed award.
6. Save and quit completely after placing another stone in survival. Reopen the world and break that stone. Expect no XP with origin `survival_placed`, proving the ledger persisted.
7. To prove customization, change the stone rule to `allowed_block_origins = ["natural", "creative_placed", "survival_placed"]`, publish it, place stone in survival, wait six seconds, and break it. Expect 10 XP with origin `survival_placed`.
8. To prove full XP without timeout or decay, keep survival placed enabled and set `cooldown_ticks = 0`, all three caps to `0`, `repeat_window_ticks = 0`, `repeat_decay = 1`, and `minimum_multiplier = 1`. Publish, then place and break two survival stones consecutively. Each must award the full 10 XP.
9. Restore the desired safe origins and pacing values through the reviewed publish flow. Run `/pskills rule status` once more and confirm `Reliable true`.

Blocks placed before this refinement have no historical marker and are inferred natural. Use newly placed blocks and newly generated terrain for an unambiguous checkpoint. Include the complete game log and all `/pskills explain xp last` lines when reporting the result.
