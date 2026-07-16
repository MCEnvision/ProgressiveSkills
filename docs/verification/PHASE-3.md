# Phase 3 Verification Record

Status: accepted after automated local verification and a successful external in-game operator review.

This record covers “Content packs + staged loader.” It does not claim skill, XP, player-state, networking, or other gameplay behavior.

## Implemented scope

- strict schema-v2 `pack.toml` parsing with namespace ownership, SemVer engine/content compatibility, dependencies, priorities, policies, and optional metadata
- deterministic bounded global/world pack discovery with locked low-to-high root precedence and no symbolic links
- dependency-first ordering, cycle/collision/missing-version checks, and explicit precedence-conflict rejection
- bounded UTF-8 TOML normalization into Phase 2 component/icon canonical IR
- path-derived IDs, strict explicit-ID matching, direct replacements, and exact field provenance
- explicit add/replace/merge/patch/disable resolution before typed validation
- immutable staging/live snapshots, semantic digests/diffs, source digests, and generation publication barrier
- source-aware time-of-check/time-of-use protection between dry-run and publish
- checksummed two-generation source journal and environment-locked last-known-good recovery
- first-launch dependency-free Core starter pack that preserves operator edits
- `/ps status`, `validate`, safe reload/diff/publish, pack info, and definition/provenance commands
- fail-closed rejection for every planned definition kind whose typed feature compiler is not implemented yet

## Required commands

```text
./gradlew clean build --stacktrace
./gradlew verifySchemaArtifacts --stacktrace
./gradlew runGameTestServer --stacktrace
bash .ci/smoke-server.sh
bash .ci/smoke-client.sh
.ci/verify-release-jar.sh
```

## Local verification environment

Verification is running 2026-07-16 on Linux `6.12.63+deb13-amd64` x86-64 with the locked Java 21 toolchain.

## Evidence table

| Check | Status | Evidence |
|---|---|---|
| Clean compile/check | local pass | `clean build verifySchemaArtifacts` completed with warnings treated as errors, property discovery enforcement, and byte-for-byte schema verification. |
| Unit/property/architecture suite | local pass | 99 tests across 22 suites; 0 failed/skipped/errored; includes 1,000 enforced jqwik tries and 8 architecture rules. |
| Generated artifacts | local pass | 10 schemas, 32 definition kinds, and 27 diagnostics; checked-in Markdown/JSON matched regenerated output byte-for-byte. |
| NeoForge GameTest | local pass | The real server loaded a live starter-pack generation and executed status, validation, dry-run, diff, and definition/provenance inspection commands; 1 required test passed. |
| Dedicated server | local pass | Production-only server reached ready state without optional mods or GameTest output. |
| Client | local pass | Production-only client reached the real title screen without GameTest output. |
| Release JAR | local pass | 348,025 bytes; verification passed; SHA-256 `b9044f20c3d616637f5ef34bae451d946f0dd0edd4f57a999b9f7d483829beda`. |
| Runtime logs | local pass | Current GameTest, dedicated-server, and client smoke logs passed their error/exception/crash-marker gates. |
| Manual in-game workflow | user pass | A Windows 11 / PrismLauncher / NeoForge 21.1.238 client loaded generation 1, staged exactly one edited component, published generation 2, and reported the edited fallback with exact file/line provenance. |

## Acceptance checklist

- [x] Invalid packs never expose a partial publishable snapshot.
- [x] Root tier, priority, dependency, file, and ID ordering is deterministic.
- [x] Every collision requires an explicit operation with checked preconditions.
- [x] TOML source syntax/provenance does not contaminate semantic equality.
- [x] Source-only edits are nevertheless pinned and protected across review/publication.
- [x] Validation alone cannot arm a publish.
- [x] Publication re-stages sources and swaps live state only after persistence succeeds.
- [x] Current and previous recovery pairs are bounded, checksummed, and recompiled before use.
- [x] Recovery requires matching schema/engine/mod/pack/source lock facts.
- [x] Unsupported gameplay schemas fail with a stable actionable diagnostic.
- [x] The real GameTest server loads and exercises Phase 3 commands.
- [x] User completes the manual in-game operator workflow.
- [x] Final server/client/archive smoke results are recorded above.

## Manual in-game checkpoint

1. Launch a development client and enter a cheats-enabled single-player world (or use an operator account on a development server).
2. Run `/ps status`, `/ps validate`, and `/ps reload --dry-run`. Expect one `progressiveskills:core` pack, one definition, and no errors.
3. While the world stays open, edit `config/progressiveskills/packs/progressiveskills-core/component_specs/engine_name.toml` in that run directory. Change `fallback = "ProgressiveSkills"` to `fallback = "ProgressiveSkills Phase 3 Test"`.
4. Run `/ps validate`. It should pass but must not create a publishable candidate by itself.
5. Run `/ps reload --dry-run`, then `/ps diff`. Expect one modified definition and an unchanged live generation.
6. Run `/ps reload --publish`. Expect the generation to increment exactly once.
7. Run `/ps info progressiveskills:component_spec progressiveskills:engine_name --provenance`. Expect the new fallback text and its pack/file/line provenance.
8. Optionally make a second edit after dry-run but before publish. Publication must reject it and ask for a fresh review; after restoring or reviewing the new edit, publish normally.

The visible feature at this checkpoint is the safe authoring/operator workflow. Skills and XP remain intentionally absent until their typed implementation phases.
