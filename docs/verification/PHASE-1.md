# Phase 1 Verification Record

This file records reproducible evidence for “Scaffold, CI, and evidence lock.” It is updated only from actual command results.

## Locked inputs

| Input | Pin |
|---|---|
| Minecraft | `1.21.1` |
| NeoForge | `21.1.236` |
| Parchment | `1.21.1:2024.11.17` |
| Java toolchain | `21` |
| Gradle wrapper | `8.8` |
| Gradle distribution SHA-256 | `a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612` |
| ModDevGradle | `2.0.141` |
| JUnit | `5.14.4` |
| jqwik | `1.10.1` |
| ArchUnit | `1.4.1` using Minecraft's strict SLF4J `2.0.9` |

## Required commands

```text
./gradlew clean build --stacktrace
./gradlew runGameTestServer --stacktrace
bash .ci/smoke-server.sh
bash .ci/smoke-client.sh
.ci/verify-release-jar.sh
```

## Local verification environment

Verified 2026-07-15 at 22:12 CDT on Linux `6.12.63+deb13-amd64` x86-64 with an Intel Core i9-13900HK. Gradle launched the Debian OpenJDK `21.0.11+10` Java 21 toolchain. This is scaffold verification, not the PERF-001 reference performance host.

## Evidence table

| Check | Status | Evidence |
|---|---|---|
| Compile with warnings as errors | local pass | Clean build succeeded in 5 s; final post-metadata build succeeded in 10 s with all 11 tests; no compiler or project deprecation warnings |
| JUnit/property/architecture suite | local pass | 11 tests: 3 identity/toolchain/metadata, 7 architecture boundaries, and 1 jqwik property with 250 generated cases; 0 failed/skipped/errors |
| Test-discovery regression gates | local pass | `verifyTestHarnessSources` prevents `NO-SOURCE`; the JUnit suite fails when zero tests are discovered |
| NeoForge GameTest | local pass | 1 required test passed; `verifyGameTestResults` confirmed the success marker; run completed in 13 s |
| Dedicated server without optional mods | local pass | Empty `run/server/mods`; production-only classpath; common bootstrap marker; `Done (0.878s)`; smoke completed in 12.2 s |
| Client title screen | local pass | Production-only classpath; common and client bootstrap markers; real title-screen marker; smoke completed in 11.9 s |
| Release JAR surface and digest | local pass | 3.9 KiB; test-output intersection empty; locked metadata present; SHA-256 `20d444bbf032a3424fde1b24b2a26cce7da849a8697157a8c2445261c2e23ec3` |
| Project/folder identity | local pass | Working project is `/home/envy/projects/ProgressiveSkills`; the retired `/home/envy/projects/UnrealSkills` directory is absent |
| PERF-001 evidence lock | local pass | Numeric gates plus machine-readable `docs/performance/fixtures/perf-001-v1.toml` lock seeds, counts, schedules, event ratios, reload timing, JVM flags, and capture outputs |
| GitHub Actions | remote pass | Run `29472464120` passed on commit `fb66ac99ae254d9787b29a537597767b8c424abd`: https://github.com/EnVisione/ProgressiveSkills/actions/runs/29472464120 |

Phase 1 acceptance is green locally and in the first remote GitHub Actions run. CI artifacts retain test reports, run logs, smoke consoles, and the release JAR even when a later step fails.

## Manual review checklist

- [x] Generated metadata and the runtime mod list display `ProgressiveSkills` with mod ID `progressiveskills`.
- [x] The release JAR contains no MDK example item/block/tab, GameTest output, NBT fixture, JUnit, jqwik, or ArchUnit class.
- [x] A dedicated server reaches ready state with an empty optional-mod directory and a production-only mod classpath.
- [x] The client reaches the title screen and reports common, client, and title-screen markers.
- [x] The project compiles with deprecations and other Java warnings treated as errors.
- [x] No retired ProgressiveSkills-owned package declaration, resource namespace, NeoForge `21.1.238`, or cross-version Parchment pin remains. Negative regression assertions and the external ProgressiveStages namespace are intentional.
- [x] Phase 2 schema/IR and later gameplay code have not leaked into Phase 1.
- [x] First remote GitHub Actions run passes.
