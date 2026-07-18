# Phase 7 Verification Record

Status: accepted. Automated verification and the real client Physique checkpoint passed.

This record covers the skill XP vertical slice. It does not claim the Phase 8 general rule engine, ordinary gameplay event bindings, requirements/formulas, trees, classes, abilities, or baseline progression UI.

## Implemented scope

- exact six-decimal fixed-point XP with checked signed-long arithmetic
- normative flat, linear, polynomial, exponential, and custom-table curves
- one final selectable rounding operation and complete threshold validation
- active XP, cap bank, current level, and lifetime-highest level state
- character-scoped named currencies with checked bounds
- lifetime-highest level currency entitlements without repeated awards
- manual XP and one stable repeatable custom XP route
- discrete `[[levels]]` and uniform `[[scaling]]` attribute grants
- source-owned additive attribute projection for all three vanilla operations
- login, respawn, publish, removal, persistence, and physical re-projection reconciliation
- bounded action-bar, chat, sound, bank, and currency feedback
- `/pskills xp`, `/pskills xp source`, and `/pskills skill get`
- generated skill/currency schema metadata and six stable Phase 7 diagnostics
- bundled `progressiveskills:physique` and `progressiveskills:global_points`

## Required commands

```text
./gradlew clean build --stacktrace
./gradlew verifySchemaArtifacts --stacktrace
./gradlew runGameTestServer --stacktrace
bash .ci/smoke-server.sh
bash .ci/smoke-client.sh
.ci/verify-release-jar.sh
```

## Automated evidence

| Check | Status | Evidence |
|---|---|---|
| Skill and curve unit suite | local pass | All five curves, all four rounding modes, exact boundaries, decreasing/invalid tables, cumulative overflow, fixed-point decimal drift, highest-level points, cap banking, attribute ownership, and persistence restore pass. |
| Full unit/property suite | local pass | 152 tests passed with 0 failed, errored, or skipped. All eight architecture rules pass and the retained jqwik suite completes 4,000 tries. |
| Generated metadata | local pass | 27 schemas, 32 definition kinds, and 53 diagnostics, including skill/currency authoring schemas and `PS-SKILL-*` plus `PS-CURRENCY-001`. Checked-in Markdown and editor JSON match byte for byte. |
| NeoForge GameTest | local pass | The one required real-server test loads three starter definitions, awards manual and custom XP, reaches exact level 6, verifies +10 max health and multiplied-base jump strength, proves six points, denies negative XP, serializes state, and reprojects identical attributes. |
| Dedicated server/client smoke | local pass | Production-only server reached ready state and production-only client reached the title screen; both error-marker gates passed. |
| Release JAR | local pass | 715,585 bytes; archive verification passed; SHA-256 `e1ff197fc252755afd8e0a2ffe6c747325198314282907950b89559987a2c051`. |
| Real client checkpoint | user pass | NeoForge 21.1.238 on Java 21.0.7 reached exact 975 XP, level and highest 6, six points, max health 30 from a 20 baseline, and jump strength 0.483 from a 0.42 baseline. Negative XP was rejected. Full relog retained identical state and effects. Reloading scaling from 2 to 1 changed health to 26 without progression drift, and restoring it returned health to 30. |

## Acceptance checklist

- [x] XP input is exact to six decimal places and negative XP fails closed.
- [x] Every curve type follows the normative expression and rounds only once.
- [x] Costs are positive and nondecreasing and cumulative fixed-point thresholds cannot overflow.
- [x] Active XP derives level exactly; cap overflow enters a separate bank.
- [x] Currency rewards use only newly crossed lifetime-highest levels.
- [x] XP, level, highest, currency, and attribute ownership commit atomically.
- [x] Discrete and scaling grants share source-aware ownership and remove cleanly.
- [x] All three attribute operations project through deterministic owned modifiers.
- [x] Persistence restore and force projection reproduce identical semantic state and effects.
- [x] Published definitions reconcile online players before gameplay resumes.
- [x] Generated schemas, diagnostics, unit/property tests, GameTest, smokes, and release archive pass.
- [x] User completed the real-client Physique progression, relog, and reload-reconciliation checkpoint.

## Manual in-game checkpoint

Use a cheats-enabled development world. The installer adds the missing starter currency and skill files on launch without replacing existing files.

1. Run `/pskills validate` and `/pskills status`. Expect a valid live pack with at least the three Core starter definitions.
2. Run `/pskills skill get progressiveskills:physique`. On a player who has not used Phase 7, expect level 0, highest 0, active XP 0, bank 0, and global points 0.
3. Record max health and jump strength with `/attribute @s minecraft:generic.max_health get` and `/attribute @s minecraft:generic.jump_strength get`.
4. Run `/pskills xp @s progressiveskills:physique 100`. Expect level 1, one global point, one level-up sound/message, and max health exactly 2 above the recorded baseline.
5. Run `/pskills xp source @s progressiveskills:physique_training`, then `/pskills skill get progressiveskills:physique`. Expect active XP 125, level 1, 25 XP into the 125-XP level, and still one point.
6. Run `/pskills xp @s progressiveskills:physique 850`. Expect active XP 975, level/highest 6, six global points, max health exactly 10 above baseline, and jump strength 15 percent above its vanilla base.
7. Run `/pskills xp @s progressiveskills:physique -1`. Expect rejection with no XP, level, point, health, or jump change.
8. Save and quit completely, reopen the same world, and repeat `/pskills skill get` plus both `/attribute` commands. Expect the exact 975 XP, level 6, six points, +10 max health, and level-6 jump value with no duplicate rewards.
9. In the generated `skills/physique.toml`, change the scaling `per_level` from `2.0` to `1.0`. Run `/pskills reload --dry-run`, review `/pskills diff`, then `/pskills reload --publish`. Expect XP/level/points unchanged and max health to become exactly 6 above the original baseline.
10. Restore `per_level = 2.0` through the same reviewed publish flow. Expect max health to return to exactly 10 above baseline with XP and points still unchanged.

The manual checkpoint is complete when the command feedback, hearts, jump, relog, and both hot-reload projections match without duplicate XP or points.
