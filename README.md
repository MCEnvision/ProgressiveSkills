# ProgressiveSkills

ProgressiveSkills is a data-driven progression engine for Minecraft 1.21.1 on NeoForge. Phase 1 of the master plan is implemented and verified locally: a reproducible, side-safe, tested foundation with no hardcoded gameplay content. The first remote CI run remains the merge gate.

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
./gradlew runGameTestServer
bash .ci/smoke-server.sh
bash .ci/smoke-client.sh
.ci/verify-release-jar.sh
```

The client smoke requires `xvfb-run` on headless Linux. Test reports are written below `build/reports/`; runtime logs are isolated below `run/<configuration>/logs/`.

See [DOCUMENTATION.md](DOCUMENTATION.md) for setup and testing, [the package boundary policy](docs/architecture/PACKAGE_BOUNDARIES.md), [the compatibility matrix](docs/compatibility/COMPATIBILITY_MATRIX.md), [PERF-001](docs/performance/PERF-001.md), and [Phase 1 evidence](docs/verification/PHASE-1.md).

Phase 2 will introduce the schema registry and immutable canonical IR. Gameplay, packs, persistence, and networking intentionally do not begin in this scaffold milestone.
