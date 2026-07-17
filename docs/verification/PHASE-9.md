# Phase 9 Verification Record

Status: implementation and automated verification complete. Player checkpoint test pending.

This record covers the bounded typed requirement evaluator, deterministic dependency indexing, exact fixed-point numeric AST, one final rounding policy, Core direct requirement TOML, and shared preview and explanation hooks. It does not claim Creator formula parsing, reusable named predicates or requirement files, contextual world leaves, or nested predicate authoring.

## Scope under verification

- immutable typed `all`, `any`, `not`, skill-level, and named-currency requirement nodes
- flat Core `[[rule.requirements]]` authoring with AND semantics
- actor-only `skill_level` and `currency` requirement leaves
- typed subject, missing policy, comparison, identity, and value validation
- deterministic sorted dependency collection
- bounded node count, depth, fanout, dependencies, operations, and trace retention
- pure preview evaluation with no transaction or source-memory mutation
- bounded requirement results in `/ps explain xp last`
- exact checked fixed-point numeric AST
- one final rule-level `rounding` operation
- fail-closed overflow, divide-by-zero, domain, missing-value, and budget behavior
- canonical encoding, semantic digest, last-known-good recovery, and hot-reload compatibility
- generated schema and diagnostic metadata for the Phase 9 fields
- rejection of general formulas, nested TOML predicates, and standalone predicate or requirement definitions in Core

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
| Requirement AST unit and property suite | passed | Four evaluator and dependency tests plus two properties passed. The properties retained 1000 tries and cover every comparison and both missing policies. |
| Dependency index suite | passed | Typed deduplication, stable ordering, exact selection, short circuit behavior, and hard limits passed. |
| Numeric AST unit and property suite | passed | Four evaluator tests and a 500 try exact reference property passed for exact arithmetic, all rounding modes, one final rounding, invalid domains, and budgets. |
| Rule TOML and canonical codec suite | passed | Direct parsing, unsupported shape rejection, dangling reference rejection, canonical reordering, Phase 8 defaults, and rounding passed. |
| Rule runtime and explanation suite | passed | Shared preview, failed gate behavior, live award behavior, pre anti amount, rounding evidence, and transaction evidence passed in the NeoForge GameTest. |
| Full unit and property suite | passed | `./gradlew clean build --stacktrace --no-daemon` passed with 180 tests and 5500 jqwik tries across the complete suite. The retained property gate requires at least 2500 tries from its selected core files. |
| Generated metadata | passed | The 30 schema reference entries and editor JSON regenerated and matched byte for byte. |
| NeoForge GameTest | passed | `./gradlew runGameTestServer --stacktrace --no-daemon` completed one required real server test with the current starter fixture. |
| Dedicated server and client smoke | passed | The production dedicated server reached ready state and the headless client reached the title screen with no forbidden error marker or GameTest output. |
| Release JAR | passed | `releases/phase-9/progressiveskills-phase-9.jar` is 888633 bytes. SHA 256 is `d48518e85de58e61e5b5678619b17985caf466cd929abf43e496da61fe58b7df`. Archive verification passed. |

## Implementation acceptance checklist

- [x] Internal requirements compose through bounded typed `all`, `any`, and `not` nodes.
- [x] Core TOML accepts only a flat AND list of direct actor skill-level and named-currency leaves.
- [x] Every direct leaf validates `type`, `subject`, `missing`, target identity, `op`, and `value`.
- [x] Unknown skill or currency targets fail staged publication with a stable diagnostic.
- [x] Standalone predicate and requirement files, nested predicate syntax, and formula strings remain unavailable in Core.
- [x] Dependency keys are typed, deduplicated, and sorted independently of map or set iteration order.
- [x] Compile-time limits reject oversized trees before publication.
- [x] Runtime operation exhaustion fails closed without XP, currency, cooldown, first-time, or rate-memory mutation.
- [x] Preview and live eligibility call the same evaluator against immutable snapshots.
- [x] Preview cannot authorize a later commit and cannot mutate state.
- [x] Numeric operations use checked exact fixed-point values without binary floating point.
- [x] The complete amount expression applies its selected rounding policy exactly once.
- [x] Overflow, divide-by-zero, invalid domains, and unavailable required values fail closed.
- [x] `/ps explain xp last` includes bounded requirement and final-rounding evidence.
- [x] Canonical encode and decode preserve Phase 9 values while old Phase 8 definitions receive safe defaults.
- [x] Semantic digest and staged diff change when a requirement or rounding value changes.
- [x] Generated schemas and diagnostics describe only the supported Core authoring surface.
- [x] Clean build, GameTest, server smoke, client smoke, and release archive checks pass.
- [x] Architecture documents state the internal capability and the narrower Core authoring boundary separately.

## Manual fallback checklist

Use the exact Phase 9 checkpoint JAR in a cheats-enabled test world. Back up the world and the pack directory first. Existing starter files are not overwritten, so perform the test in a temporary copy of the starter pack or remove the temporary route when finished.

1. Run `/ps validate`, `/ps status`, `/ps rule status`, and `/ps skill get progressiveskills:physique`. Record the starting Physique level and XP. Use a fresh test player if possible so the starting level and `progressiveskills:global_points` balance are both zero.
2. Copy the Phase 8 stone route to a temporary, correctly named rule file. Give it a unique rule ID and stack group, disable the original stone route for this test, allow newly placed test blocks, disable cooldown, decay, and rate caps, and keep its output pointed at Physique. Set its base to 0.000001 and its one literal multiplier to 1.5 so its exact pre-rounded amount falls halfway between two fixed-point units.
3. Add `rounding = "floor"`. Add two flat `[[rule.requirements]]` objects using the documented Phase 9 shape. Require actor Physique level at least 1 and actor global points at least 0. Run `/ps reload --dry-run`, inspect `/ps diff`, then run `/ps reload --publish`.
4. At level zero, place and break a test stone through an allowed origin. Expect no XP. Run `/ps explain xp last`; expect the skill-level requirement to fail and no transaction ID to be committed.
5. Run `/ps xp @s progressiveskills:physique 100`. Confirm Physique reaches level 1 and the first highest-level point entitlement is granted. Change the global points threshold to 2, review, and publish. Break another allowed test stone. Expect no route XP because the skill requirement now passes while the two-point currency requirement fails. Confirm both results in `/ps explain xp last`.
6. Award only enough additional Physique XP to reach level 2 and its second point entitlement. Break another allowed test stone. Expect both requirements to pass and a committed 0.000001 XP route award. Confirm the explanation shows both typed dependencies, the selected floor policy, rounded amount, and transaction ID.
7. Change only `rounding` to `ceil`, keeping the same nonintegral exact amount. Stage, inspect, and publish. Break another allowed test stone. Expect a committed 0.000002 XP route award and the explanation to identify the ceil policy. Do not use repeat decay or a cap in this comparison.
8. Change the currency threshold above the current balance and publish. Break another allowed test stone. Expect no XP, no cooldown or first-time consumption, and a failed currency comparison in the explanation. Restore the passing threshold, publish, and confirm the next break can award normally.
9. In separate dry runs, attempt a nested `all` or `any` object, a general formula string, an unknown skill ID, and an unknown currency ID. Each unsupported or dangling form must fail validation without changing the live generation. Restore the valid file after every case.
10. Restart the game and rejoin the same world. Confirm the valid direct requirements and rounding policy still govern the route, proving canonical and last-known-good compatibility. Run `/ps validate` and `/ps rule status` once more.
11. Restore the original starter route, remove the temporary test file, and publish the cleanup through the reviewed reload flow.

The internal `any` and `not` nodes, deterministic dependency order, operation budgets, pure preview behavior, one-final-round invariant, and malformed canonical-value defenses have no complete player command surface in Phase 9. Treat their automated unit, property, and GameTest rows as required evidence rather than claiming the manual route proves them.
