# ProgressiveSkills

ProgressiveSkills is a data-driven progression engine for Minecraft 1.21.1 on NeoForge. Phases 1–6 of the master plan now provide a reproducible side-safe foundation, immutable schema/IR, a staged TOML content-pack loader, bounded transaction/lifecycle runtime, versioned player persistence/migrations, and a server-authoritative bounded networking handshake with sanitized client projection. Skills and XP are intentionally not implemented yet.

## Locked baseline

| Component | Version / identity |
|---|---|
| Mod ID | `progressiveskills` |
| Root package | `com.envisione.progressiveskills` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.236` |
| Parchment | `1.21.1:2024.11.17` |
| Java | `21` |
| Gradle | `8.8` |
| ModDevGradle | `2.0.141` |

## Verify locally

```text
./gradlew clean build
./gradlew verifySchemaArtifacts
./gradlew runGameTestServer
bash .ci/smoke-server.sh
bash .ci/smoke-client.sh
.ci/verify-release-jar.sh
```

The client smoke requires `xvfb-run` on headless Linux. Test reports are written below `build/reports/`; runtime logs are isolated below `run/<configuration>/logs/`.

See [DOCUMENTATION.md](DOCUMENTATION.md) for setup and testing, [the networking architecture](docs/architecture/NETWORKING.md), [the persistence/migration architecture](docs/architecture/PERSISTENCE_AND_MIGRATIONS.md), [the transaction/lifecycle architecture](docs/architecture/TRANSACTIONS_AND_LIFECYCLES.md), [the content-pack architecture](docs/architecture/CONTENT_PACKS.md), [the schema/IR architecture](docs/architecture/SCHEMA_AND_IR.md), [the generated schema reference](docs/reference/SCHEMA-V2.md), [the package boundary policy](docs/architecture/PACKAGE_BOUNDARIES.md), [the compatibility matrix](docs/compatibility/COMPATIBILITY_MATRIX.md), [PERF-001](docs/performance/PERF-001.md), and the [Phase 1](docs/verification/PHASE-1.md), [Phase 2](docs/verification/PHASE-2.md), [Phase 3](docs/verification/PHASE-3.md), [Phase 4](docs/verification/PHASE-4.md), [Phase 5](docs/verification/PHASE-5.md), and [Phase 6](docs/verification/PHASE-6.md) evidence records.

Phase 5 backs that transaction state with a versioned death-copying player attachment, raw migrations/quarantine, definition orphan/restore rules, a pending offline-operation store, and verified snapshot/export primitives. Phase 6 negotiates and ACKs sanitized definitions/full state, then sends continuity-checked deltas and rejects stale bounded intents. Use `/ps help`, `/ps lifecycle`, `/ps persistence`, and `/ps network status` in a cheats-enabled development world.
