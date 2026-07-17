# Phase 8 Verification Record

Status: beta implementation complete. Automated verification passed and the real client gameplay checkpoint is pending.

This record covers the rule engine and anti exploit foundation plus the first NeoForge block break binding. It does not claim Phase 9 predicates and formulas, natural block provenance, combat or crafting bindings, target or team attribution, or the full Creator rule surface.

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
- bounded per-player event dedupe
- atomic XP and source-memory transactions
- persistent internal memory filtered from client state projection
- route pause and immutable table rebuild during definition publication
- `/ps rule status` and `/ps explain xp last`
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
| Rule engine unit suite | local pass | Registries, bare and prefixed matchers, negation, custom-name opt in, every multiplier mode and rule stack policy, final-only rounding, invalid factor rejection, cooldown at world tick zero, repeat decay, caps, first-time persistence, and atomic memory mutation pass. |
| Full unit and property suite | local pass | 159 tests passed with 0 failed, errored, or skipped. All eight architecture rules pass and the retained jqwik suite completes 4,000 tries. |
| Generated metadata | local pass | 28 schemas, 32 definition kinds, and 59 diagnostics, including `progressiveskills:rule_definition` and `PS-RULE-001` through `PS-RULE-006`. Checked-in Markdown and editor JSON match byte for byte. |
| NeoForge GameTest | local pass | The one required real-server test loads five starter definitions and proves exact stone and vanilla-log-tag routing, 10 and 20 XP awards, cooldown, event dedupe, fake-player rejection, first-time memory, command explanation, route pause, table rebuild, and retained source memory. |
| Client redaction | local pass | Internal hashed rule-memory balances are omitted from the visible network balance map. |
| Dedicated server and client smoke | local pass | The production-only server reached ready state and the production-only client reached the title screen; both error-marker gates passed. |
| Release JAR | local pass | 808,455 bytes; archive verification passed; SHA-256 `578beb622c4203d68aaf0d860dfab710a05add9d1417c079eeb767b929865740`. |
| Real client checkpoint | pending | Awaiting the stone cooldown and repeat sequence, first-log persistence, and live rule-table publish checks below. |

## Acceptance checklist

- [x] Rule, matcher, provider, output, stack, and multiplier identities are stable and validated.
- [x] Disabled and nonmatching routes return before transaction or diagnostic construction.
- [x] Literal fixed-point multiplier stages and stack groups resolve deterministically.
- [x] Invalid zero, negative, overflowing, and inconsistent rule values fail publication.
- [x] Cooldown, first-time, repeat, and cap memory are bounded and persistent.
- [x] XP and changed source memory commit in one ordinary progression transaction.
- [x] Fake players and duplicate event tokens fail closed.
- [x] Internal anti exploit state cannot enter visible client balances.
- [x] Publication pauses old routes and rebuilds one immutable table from the new generation.
- [x] Generated schemas, diagnostics, unit and property tests, GameTest, smokes, and release archive pass.
- [ ] User completed the real-client block route, persistence, and hot-reload checkpoint.

## Manual in-game checkpoint

Use a cheats-enabled development world. The installer adds the two missing starter rule files on launch without replacing existing pack files.

1. Install the Phase 8 beta JAR and launch the same world used for Phase 7.
2. Run `/ps validate` and `/ps status`. Expect a valid pack with at least five definitions and no errors.
3. Run `/ps rule status`. With the unmodified starter pack, expect `Rules 2. Enabled 2.`
4. Run `/ps skill get progressiveskills:physique` and record the current active XP as `X`.
5. In survival, break one normal `minecraft:stone` block. Run `/ps skill get progressiveskills:physique`; expect `X + 10` XP. Run `/ps explain xp last`; expect one candidate, one eligible route, one selected route, 10 XP, and a committed outcome.
6. Wait six seconds so the prior repeat window expires. For a repeatable timing check, place three adjacent stone blocks, use an unenchanted diamond pick, run `/tick rate 1`, and hold the break button across the row. The first accepted stone should add 10 XP, the second should add 0 while the ten-tick cooldown is active, and the third should add 5 XP from the 0.5 repeat multiplier. Use `/ps explain xp last` after each result if the timing differs. Restore `/tick rate 20` when done.
7. Break one normal oak log. Expect exactly 20 XP and a committed explanation for `progressiveskills:physique_first_log`. Break a second block in the `minecraft:logs` tag. Expect no XP and the outcome `first time reward already received`.
8. Save and quit completely, reopen the same world, and break another log. Expect no XP; the first-time source memory must survive the relog.
9. In `config/progressiveskills/packs/progressiveskills-core/rules/physique_stone_training.toml`, set `enabled = false`. Run `/ps reload --dry-run`, review `/ps diff`, then run `/ps reload --publish`. `/ps rule status` should report two rules with one enabled, and breaking stone must not change XP.
10. Restore `enabled = true` through the same reviewed publish flow. Wait six seconds so the repeat window expires, then break stone. Expect exactly 10 XP and two enabled rules again.

If the three-stone timing lands outside the intended windows, the explanation is authoritative: cooldown requires fewer than ten world ticks since the last committed stone award, and repeat decay requires the next accepted award within 100 world ticks. Include the complete game log and the explanation lines when reporting the checkpoint.
