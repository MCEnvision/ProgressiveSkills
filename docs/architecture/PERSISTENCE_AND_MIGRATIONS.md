# Persistence and Migrations

Status: Phase 5 accepted. Automated attachment, migration, clone, offline-operation, snapshot, and export checks pass, and the real-world restart/death checkpoint passed on 2026-07-16.

## Authority and storage split

`ProgressiveSkillsData` is the authoritative per-player progression attachment. It has an explicit data version, is registered as `progressiveskills:player_data`, and uses NeoForge death copying with a deep-copy handler. The attachment contains:

- the exact Phase 4 transaction revision, balances, ownership, transition receipts, idempotency results, and audit records;
- stateful-definition payloads with their typed key and semantic lineage;
- retained orphan records;
- same-attachment death/offline-operation receipts;
- a bounded migration shadow, quarantine evidence, and unknown extension fields.

The online transaction service is only a cache. Login restores it from the attachment before projection, every command mutation writes the new exact state back, logout captures it before unloading the cache, and server shutdown captures every online account.

Overworld `SavedData` stores pending operations only. It never opens or rewrites an unloaded player's NBT. The store is capped at 1,024 operations total and 64 per player; malformed stores fail closed and retain quarantine evidence.

## Raw-first decode and hard ceilings

The attachment serializer accepts a raw `CompoundTag` first. Before any typed decode or gameplay projection it enforces:

| Limit | Ceiling |
|---|---:|
| Encoded attachment | 1 MiB |
| NBT depth | 32 |
| Total tags | 16,384 |
| Compound/list entries | 4,096 |
| String characters | 2,048 |
| Compound-key characters | 128 |
| Primitive-array elements | 131,072 |

Missing, malformed, oversized, foreign-player, corrupt, or future-version data becomes `QUARANTINED`. Quarantined state is never restored into the transaction cache and never projected. Raw evidence is embedded when the complete quarantine envelope remains within the ceiling; otherwise its SHA-256 digest and reason are retained without creating an attachment that can no longer save.

Current data version 2 is written deterministically. The implemented pure `v1 -> v2` raw-tag migration runs before typed decode. Its pre-migration tag is retained as a shadow through the first successful login/save and removed on the following save. Unknown bounded root fields round-trip in an extension bucket.

World persistence metadata records the last observed player-data version. The first startup after a version increase emits an explicit world-backup-required warning once, before any joining player can lazily migrate. This is a reminder and version marker, not a claim that the mod copied unloaded attachment files.

## Definition rename, orphan, and restore rules

Each generic state payload records its typed definition key, current semantic lineage, original lineage, payload version, and bounded NBT. Reconciliation runs before the player's projection is enabled:

1. A matching live key and lineage stays active.
2. A missing key becomes an inert orphan; its payload is retained.
3. Reuse of the same key with a different lineage remains orphaned.
4. An explicit validated same-kind alias may carry the payload to its replacement while preserving origin lineage.
5. A compatible definition returning later restores its exact payload.

Phase 5 does not expose destructive prune/import/restore commands. Those require their later confirmation, audit, and monotonic-revision contracts.

## Death and non-death clone behavior

A real player death prepares a unique marker on the old attachment. NeoForge copies the attachment to the replacement player; the clone callback completes the marker by adding a receipt in that same attachment and then removes the marker. A duplicate callback sees no marker and cannot repeat the operation. The Phase 5 default has no progression death penalty, so the receipt proves lifecycle completion without inventing a loss policy.

Non-death replacement has no marker and creates no death receipt. Both paths preserve the exact transaction state. The automated GameTest exercises NeoForge's actual attachment-copy method for death and non-death copies.

## Pending offline operations

A `PendingProgressionOperation` is a bounded, expiring, definition-pinned checked balance mutation. Application occurs only after the target player logs in and their active attachment is restored. The coordinator revalidates the operation, expiry, receipt capacity, definition generation/digest, and any explicit same-kind currency replacement.

Application uses a two-save recovery protocol:

1. The balance mutation and same-id `OperationReceipt` are written into the player attachment.
2. The `SavedData` entry remains pending with its attempt identity.
3. A later login that observes the durable receipt consumes the `SavedData` entry.

A retry cannot apply the balance twice because both the transaction idempotency result and the operation receipt are durable. Expired, incompatible, capacity-blocked, or failed operations move to store quarantine instead of disappearing. This is crash-recoverable/effectively-once across two stores; it is not described as a universal atomic filesystem transaction.

## Snapshot and export primitives

The following in-game operator commands act on the executing player:

| Command | Permission | Result |
|---|---:|---|
| `/pskills persistence status` | 2 | Attachment version/status, storage and transaction revisions, ledger counts, orphans, operation receipts, pending operations, and migration/quarantine state. |
| `/pskills persistence snapshot` | 3 | Atomically writes a compressed NBT envelope, rereads it, and verifies player identity plus SHA-256 digest. |
| `/pskills persistence export` | 3 | Atomically writes a bounded readable SNBT maintenance export. |

Files are kept beneath the current world at `progressiveskills/snapshots/<player-uuid>/` and `progressiveskills/exports/<player-uuid>/`. The command reports the exact path, byte count, and digest. A player export is not mislabeled as a complete world backup, and Phase 5 intentionally does not provide snapshot restore yet.

## Manual in-game checkpoint

Use a fresh cheats-enabled world so the stable Phase 4 demo idempotency key has no earlier history:

1. Run `/gamerule keepInventory true`.
2. Run `/pskills lifecycle status`; expect revision 0, zero demo points, zero health bonus, and no receipt/audit entries.
3. Run `/pskills lifecycle demo`; expect revision 1, one demo point, +4 max health (two hearts), one transaction receipt, one audit record, and one gold ingot.
4. Run `/pskills persistence status`; expect player data v2 `ACTIVE`, transaction revision 1, one transaction receipt/replay result/audit record, zero orphans, and no quarantine.
5. Run `/pskills persistence snapshot` and `/pskills persistence export`; both must report a path, nonzero byte count, and digest with no error.
6. Save and quit all the way to the title screen, reopen the same world, and run both status commands. Revision 1, the point, +4 health, ledger counts, and the one gold ingot must still be present.
7. Run `/pskills lifecycle demo` again. It must report `Idempotent replay`; revision and inventory must not change.
8. Run `/kill @s`, respawn, then run both status commands. Revision 1, the point, +4 health, and transaction ledger must remain; persistence status must now show one death operation receipt.
9. Save and quit to title again, reopen, and repeat both status commands. The death receipt and all progression state must still be present.

The migration, corrupt/future input, size-ceiling, definition orphan/restore, offline two-save, and non-death clone cases are intentionally automated because manufacturing them by hand would require unsafe save editing.
