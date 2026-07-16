# ProgressiveSkills

ProgressiveSkills is a data-driven progression engine for Minecraft 1.21.1 on NeoForge. Phases 1–3 of the master plan now provide a reproducible, side-safe foundation, immutable schema/IR, and a real staged TOML content-pack loader. Gameplay progression is intentionally not implemented yet.

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

See [DOCUMENTATION.md](DOCUMENTATION.md) for setup and testing, [the content-pack architecture](docs/architecture/CONTENT_PACKS.md), [the schema/IR architecture](docs/architecture/SCHEMA_AND_IR.md), [the generated schema reference](docs/reference/SCHEMA-V2.md), [the package boundary policy](docs/architecture/PACKAGE_BOUNDARIES.md), [the compatibility matrix](docs/compatibility/COMPATIBILITY_MATRIX.md), [PERF-001](docs/performance/PERF-001.md), and the [Phase 1](docs/verification/PHASE-1.md), [Phase 2](docs/verification/PHASE-2.md), and [Phase 3](docs/verification/PHASE-3.md) evidence records.

Phase 3 discovers global/world packs, validates manifests/dependencies, resolves explicit layers, stages semantic diffs, publishes reviewed generations, and retains a checksummed last-known-good source journal. Use `/ps help` in a cheats-enabled development world. Gameplay, player persistence, and networking remain deferred to their planned phases.
