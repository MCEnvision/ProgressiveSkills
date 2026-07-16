# Phase 2 Verification Record

Status: local and remote acceptance complete.

This file records reproducible evidence for “Schema registry and canonical IR.” It covers the data foundation only; no gameplay behavior is expected.

## Implemented scope

- strict namespaced stable IDs, typed definition keys, built-in kind identities, and exact TOML-path derivation
- immutable direct same-kind aliases with conflict, chain, cycle, self, cross-kind, and bound validation
- portable provenance, one-based half-open source spans, and deterministic field-level source maps
- bounded safe `ComponentSpec`, style, typed placeholders, and registry-neutral accessible `IconSpec`
- versioned immutable canonical definition/value/IR snapshots
- explicit semantic projections that exclude only provenance and source maps
- stable diagnostic descriptors/instances/reports
- frozen schema/field/typed-default/declarative-constraint/editor/projection/diff metadata
- deterministic generated Markdown and machine-readable editor catalog with byte-for-byte verification
- architecture rule preventing the canonical foundation from capturing runtime/client/mutable presentation types

## Explicitly deferred

The Phase 2 gate verifies the foundation above, not the full plan §§33.4–33.5 end state. The following are not acceptance claims for this phase:

- generated authoring parsers/codecs or executable schema constraints
- `SoundSpec`, `ParticleSpec`, or `VisibilitySpec`
- a bounded JSON Component decoder/sanitizer, hover-text policy, or trusted-interaction handling
- pack locale loading/resolution, bounded plural/select, localized numbers, RTL metadata, glyph support, or overflow checks
- a native encyclopedia, Studio/editor UI, or command suggestions
- TOML/JSON editor schemas and snippets
- executable diff/default-elision or client projection/redaction/networking code
- generated test parameter catalogs

The generated editor JSON contains metadata for future consumers; it is not any of the deferred renderers above. Phase 3 must normalize component shorthand and component/icon authoring fields into canonical IR before comparing adapters. Equivalent raw TOML, future JSON, and builder objects are not claimed to match in Phase 2; parity will be proven against semantic projections after adapter-specific provenance and source maps are removed.

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

Verified 2026-07-16 at 01:07 CDT on Linux `6.12.63+deb13-amd64` x86-64. Gradle and all Minecraft run configurations used the locked Java 21 toolchain; the shell's default Java is not part of the test contract.

## Evidence table

| Check | Status | Evidence |
|---|---|---|
| Clean compile/check | local pass | `clean build` completed in 6 s with warnings treated as errors, property-discovery enforcement, and generated-artifact verification included |
| Unit/property/architecture suite | local pass | 77 tests across 17 suites; 0 failed, skipped, or errored; includes 750 enforced jqwik tries and 8 architecture rules |
| Generated artifacts | local pass | Schema v2 catalog contains 7 schema descriptors, 32 definition kinds, and 13 diagnostic descriptors; Markdown and valid JSON matched build-directory output byte-for-byte |
| NeoForge GameTest | local pass | 1 required bootstrap test passed; success marker independently verified |
| Dedicated server | local pass | Production-only server reached ready state without optional mods |
| Client | local pass | Production-only client reached the title screen with common/client/title-screen markers |
| Release JAR | local pass | 161,607 bytes; test-output intersection empty; SHA-256 `98d68b3472ccdfbf7f6f77983d5a4ede3e9d14c06fb12f4b05d6ff1f86b96cf1` |
| Runtime logs | local pass | Current GameTest, dedicated-server, and client logs contain no error, fatal, exception, or crash entries |
| GitHub Actions | remote pass | Branch [run 29476549095](https://github.com/EnVisione/ProgressiveSkills/actions/runs/29476549095) and pull-request [run 29476550889](https://github.com/EnVisione/ProgressiveSkills/actions/runs/29476550889) passed the complete foundation workflow for commit `3fbbe2ea29dcd4363db5155066a3cbefcfa402ca` |

## Acceptance checklist

- [x] Phase 1 remote GitHub Actions gate passed before Phase 2 work began.
- [x] Phase 2 stays physical-side-neutral and contains no gameplay definitions or behavior.
- [x] IDs and aliases have stable typed identity and deterministic validation.
- [x] Source/provenance data cannot contaminate semantic projection equality.
- [x] Every nested collection is defensively copied and deterministically exposed.
- [x] Mutable/interpreting vanilla Components and resolved registry objects are absent from IR.
- [x] Schema metadata generates documentation, diagnostic help, and editor/projection/diff metadata.
- [x] Checked-in generated artifacts are verified byte-for-byte.
- [x] Final clean local build, GameTest, server/client smoke, and release-JAR checks pass.
- [x] Phase 2 branch GitHub Actions run passes.

## Expected in-game result

Phase 2 intentionally adds no screen, command, key mapping, config pack, skill, XP, class, tree, item, or other gameplay behavior. The correct runtime result remains: NeoForge loads `ProgressiveSkills`, a world/server starts, and the existing bootstrap markers appear without errors. Phase 3 begins pack loading and validation; gameplay starts later in the build order.
