# Phase 4 Verification Record

Status: accepted after automated verification and manual in-game lifecycle review.

This record covers “Transaction and lifecycle core.” It does not claim player-save persistence, skills, XP, networking, offline mutation, or other later milestones.

## Implemented scope

- stable idempotency and transaction identities
- state-revision and definition-generation/digest compare-and-swap checks
- checked bounded balance mutations
- fully expanded deterministic cascade plans with hard budgets
- source-owned persistent contributions with four deterministic resolvers
- atomic physical projection contract and exact owned health demonstration
- edge-only transition actions with repeat/delivery/failure policies
- exact bounded receipts and fail-closed ledger capacity
- recompute path structurally unable to execute transitions
- bounded audit records and action-free/latest-only rollback boundary
- session-only operator fixture, executable console self-test, and real-player command GameTest

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

Verification ran 2026-07-16 on Linux `6.12.63+deb13-amd64` x86-64 with the locked Java 21 toolchain, Minecraft 1.21.1, and NeoForge 21.1.236.

## Evidence table

| Check | Status | Evidence |
|---|---|---|
| Clean compile/check | local pass | `clean build verifySchemaArtifacts` completed with warnings treated as errors and byte-for-byte generated-reference verification. |
| Unit/property/architecture suite | local pass | 114 tests across 25 suites; 0 failed/skipped/errored; includes 2,000 enforced jqwik tries and 8 architecture rules. |
| Generated metadata | local pass | 14 schemas, 32 definition kinds, and 35 diagnostics; checked-in Markdown/JSON match the regenerated output. |
| NeoForge GameTest | local pass | The real server loaded the starter generation, exercised the Phase 3 pack commands and `/ps lifecycle selftest`, registered a test player, then ran lifecycle status/demo/replay/recompute/co-owner/revoke/audit. It verified exactly one gold ingot, no replay/recompute duplication, +4 max health across shared ownership, and removal after the final revocation; 1 required GameTest passed. |
| Dedicated server | local pass | Production-only server reached ready state without optional mods or GameTest output. |
| Client | local pass | Production-only client reached the real title screen without GameTest output. |
| Release JAR | local pass | 437,072 bytes; archive verification passed; SHA-256 `3fddd4e7bf0423acf7ec6b2baf1643f5a186cb0c7f72daccba02e96c4ff00c56`. |
| Runtime logs | local pass | Current GameTest, dedicated-server, and client logs passed their error/exception/crash-marker gates. |
| Manual lifecycle workflow | user pass | On 2026-07-16, the complete workflow passed in a real Windows 11 client using Microsoft Java 21.0.7 and NeoForge 21.1.238. The replay preserved its transaction/revision without another action, both recomputes reported zero actions, shared ownership remained +4, final revocation removed the bonus, and audit showed four committed mutations. |

## Acceptance checklist

- [x] An exact idempotency replay returns the same transaction identity and cannot mutate twice.
- [x] Stale state or definition revisions reject before any mutation.
- [x] Balance overflow/floor/cap failure leaves the entire cascade unchanged.
- [x] Cascade limits reject rather than truncate a committed prefix.
- [x] Co-owner removal cannot revoke another source's persistent value.
- [x] Recompute cannot run an item, command, point mutation, or other transition.
- [x] Receipt-required actions reserve exact capacity and skip an existing receipt.
- [x] Late action failure is reported as committed-with-failures and never mislabeled rollback.
- [x] Only a retained action-free transaction with no later mutation can roll back.
- [x] Rollback creates a new monotonic revision and preserves receipt/audit truth.
- [x] The real GameTest server executes both the Phase 4 self-test and visible player lifecycle workflow.
- [x] Final clean build, smoke, archive, and evidence counts are recorded.
- [x] User completes the manual in-game lifecycle workflow.

## Accepted manual checkpoint

The user completed the exact command sequence and expected visible results in [TRANSACTIONS_AND_LIFECYCLES.md](../architecture/TRANSACTIONS_AND_LIFECYCLES.md#manual-in-game-checkpoint). The supplied client log records revisions 0 through 4, one permanent receipt, one non-duplicated item delivery, and the expected four-entry audit trail.
