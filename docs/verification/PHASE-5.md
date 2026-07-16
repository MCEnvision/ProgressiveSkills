# Phase 5 Verification Record

Status: accepted after automated verification and manual restart/death persistence review.

This record covers “Persistence and migrations.” It does not claim networking, gameplay skills/XP, bulk world backup, snapshot restore, direct unloaded-player NBT mutation, or universal cross-file exactly-once delivery.

## Implemented scope

- versioned `progressiveskills:player_data` attachment with deep `copyOnDeath`
- exact Phase 4 transaction state persistence and cache restore/unload
- raw-first bounded decode, pure v1-to-v2 migration, embedded migration shadow, and unknown-field retention
- once-per-schema-bump world backup reminder without claiming a bulk attachment backup
- fail-closed corrupt/future/oversized/foreign-owner quarantine with raw digest evidence
- typed lineage reconciliation, explicit aliases/replacements, inert orphan retention, and compatible restore
- death marker plus same-attachment operation receipt; non-death clone distinction
- bounded overworld pending-operation `SavedData` and two-save login recovery
- checked expiry/cap/definition revalidation and quarantine for unsafe pending operations
- atomically written digest/identity-verified snapshots and readable exports
- persistence commands, generated internal schema metadata, and stable diagnostics
- unit, property/fuzz, and real NeoForge GameTest coverage

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
| Clean compile/check | local pass | `clean build` completed with warnings treated as errors and byte-for-byte schema artifact verification. |
| Unit/property/architecture suite | local pass | 129 tests across 31 suites; 0 failed/ignored. The 3,000 jqwik tries include 1,000 malformed player-data codec cases. |
| Generated metadata | local pass | 19 schemas, 32 definition kinds, and 42 diagnostics. The registry includes five Phase 5 persistence contracts and seven `PS-DATA-*` diagnostics; checked-in Markdown/JSON match regeneration. |
| NeoForge GameTest | local pass | A real server attachment is serialized/restored, replayed without redelivery, receives and later consumes a pending offline operation without duplication, writes snapshot/export files, and passes actual death/non-death attachment copies; all 1 required tests passed. |
| Dedicated server | local pass | Production-only server reached ready state without optional mods or GameTest output. |
| Client | local pass | Production-only client reached the real title screen without GameTest output. |
| Release JAR | local pass | 530,136 bytes; archive verification passed; SHA-256 `de5c8dafcd4cae5db8b97b0974710ec7e946072f8ce8a43b414597d4edf941d5`. |
| Runtime logs | local pass | GameTest, dedicated-server, and client logs passed their error/exception/crash-marker gates. The first-world schema backup reminder appeared as the expected warning. |
| Manual persistence workflow | user pass | On 2026-07-16, a real Windows 11 client using Microsoft Java 21.0.7 and NeoForge 21.1.238 created active v2 state, wrote matching snapshot/export digests, restored revision 1 after a full server restart, replayed without another action, and preserved the ledger through death while creating exactly one death receipt. |

## Local verification environment

Verification ran 2026-07-16 on Linux `6.12.63+deb13-amd64` x86-64 with the locked Java 21 toolchain, Minecraft 1.21.1, and NeoForge 21.1.236.

## Acceptance checklist

- [x] Persisted transaction balances, ownership, receipts, idempotency results, and audit records round-trip exactly.
- [x] Restart/cache restore cannot redeliver a receipt-protected transition action.
- [x] Quarantined data cannot restore or project gameplay state.
- [x] Future/corrupt/oversized input retains bounded raw evidence or a digest-only fallback that can itself save.
- [x] Migration runs on raw tags before typed decode and keeps a bounded one-login shadow.
- [x] Missing/incompatible definition state remains inert and recoverable; explicit compatible replacement carries it.
- [x] Death copy creates one same-attachment receipt; non-death replacement creates none.
- [x] Offline operations never edit unloaded player NBT and cannot apply twice across the two-save protocol.
- [x] Snapshot write is bounded, atomic where supported, reread, and digest/identity verified.
- [x] Readable export is bounded and clearly distinguished from a world backup.
- [x] User completes the fresh-world restart, idempotent replay, and death checkpoint; post-death receipt serialization is also covered by the exact codec regression.

## Accepted manual checkpoint

The supplied client log records a fresh revision `0 -> 1` commit, one gold-ingot transition, +4 max health, one receipt/replay/audit record, an active v2 attachment, and verified NBT/SNBT exports sharing digest `3580b793d8547bca68c873caed4451ed51e9790331fa735e5299a7b00ad8e2d8`. After the integrated server fully stopped and reopened, those exact transaction counts remained at revision 1. Replaying returned transaction `8d183bbb-858e-3fae-9020-6d92240e63a4` with zero actions. A real `/kill` then retained the same transaction revision and produced exactly one same-attachment death operation receipt. The automated codec regression additionally serializes and restores that completed death receipt with no remaining marker.
