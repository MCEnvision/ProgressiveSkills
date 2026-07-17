# Phase 6 Verification Record

Status: accepted after automated verification and the real client networking checkpoint.

This record covers “Networking handshake + definition projection.” It does not claim skill XP, rules, formulas, trees, classes, abilities, baseline UI, or later gameplay intent types.

## Implemented scope

- NeoForge registrar-version negotiation plus application protocol/features handshake
- persistent world/server identity and ephemeral per-connection sessions
- exact local-connection/server/protocol/semantic/presentation definition-cache key
- bounded 24-hour/eight-entry sanitized definition LRU with explicit clear control
- presentation-only definition DTO with no gameplay fields or provenance
- common compressed/chunked definitions and full-state envelope with atomic digest ACK
- hard compressed/uncompressed/chunk/count/concurrency/timeout ceilings
- owner-visible full state and continuity/hash-checked semantic deltas
- join, respawn, dimension, transaction, logout, and published-reload lifecycle wiring
- monotonic bounded intent replay cache, stale rejection/resync, future/old rejection, and token bucket
- strict NeoForge `StreamCodec` registrations and malformed-buffer/property tests
- `/ps network status` and `/ps network resync`
- six generated networking schemas and five stable `PS-NET-*` diagnostics

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
| Network unit/property suite | local pass | Sanitization, deterministic full/delta codecs, cache miss/hit handshake, out-of-order chunks, duplicates/conflicts, compression cap, digest mismatch, timeout, replay identity, stale resync, old/future/rate rejection, payload round trips, and 1,000 malformed-buffer property cases pass. |
| Architecture boundaries | local pass | The shared protocol has no client/server implementation dependency; server orchestration has no client type; all eight architecture rules pass. |
| Clean compile/check | local pass | 142 tests across 38 suites passed with 0 failed/errored/skipped, all warnings treated as errors, 4,000 jqwik tries, and byte-for-byte schema verification. The reconnect regression proves a changed temporary transport id cannot defeat an unchanged selected-destination cache key. |
| Generated metadata | local pass | 25 schemas, 32 definition kinds, and 47 diagnostics, including six Phase 6 network contracts and five `PS-NET-*` diagnostics. |
| NeoForge GameTest | local pass | All 1 required tests passed with payload registration and lifecycle wiring loaded; the synthetic non-negotiated test connection is safely ignored while the full real-server persistence workflow remains green. |
| Dedicated server/client smoke | local pass | Production-only server reached ready state and production-only client reached the title screen; both error-marker gates passed. |
| Release JAR | local pass | 643,855 bytes; archive verification passed; SHA-256 `a30e765d0111bb97cfd6513d2cef776cd4b579a0f497afc995bfac169ae5ffba`. |
| Manual networking workflow | user pass | A real NeoForge 21.1.238 client proved an active reconnect cache hit across changed temporary transport ids, a changed definition publish to generation 2, a fresh active resync with matching revisions, and a clean restore publish to generation 3. |

## Acceptance checklist

- [x] NeoForge rejects incompatible non-optional registrar versions; application protocol/features are also checked.
- [x] Only sanitized identity/presentation fields can enter definition projection bytes.
- [x] Definition caching is scoped to local connection identity, persistent server identity, protocol, and both digests.
- [x] Definitions and oversized full state use bounded compressed chunks with exact digest/length ACK.
- [x] Counts, strings, enums, chunks, decompression, concurrent transfers, and timeouts fail closed.
- [x] Full state is sent after login/respawn/dimension session setup and excludes durable server ledgers.
- [x] Deltas require exact generation/presentation/base continuity and resulting-state digest.
- [x] Duplicate identical deltas ACK without reapplying; gaps and mismatches request full resync.
- [x] Serverbound payloads remain under both the NeoForge 32-KiB and smaller 16-KiB project ceilings.
- [x] Monotonic intent IDs, bounded exact replay results, stale/future/old rejection, and token bucket are enforced.
- [x] Published reloads invalidate sessions and require a fresh digest-bound handshake.
- [x] Disconnect clears all authority; only bounded sanitized definitions retain TTL/LRU cache state.
- [x] Final clean build, schema verification, GameTest, smokes, and release archive pass.
- [x] User completed the real-client join/delta/reconnect/reload/resync checkpoint.
