# ProgressiveSkills

ProgressiveSkills is a data-driven progression engine for Minecraft 1.21.1 on NeoForge. The cumulative Phase 19 beta implements the full planned train from schema and pack loading through server authoritative skills, rules, trees, classes, abilities, carriers, UI, compatibility providers, Creator systems, multiplayer progression, and Studio authoring. The beta stays off `main` until its final in game mass check is approved.

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
bash .ci/verify-release-jar.sh releases/phase-19/progressiveskills-phase-19.jar
```

The client smoke requires `xvfb-run` on headless Linux. Test reports are written below `build/reports/`; runtime logs are isolated below `run/<configuration>/logs/`.

See [DOCUMENTATION.md](DOCUMENTATION.md) for setup and navigation, the [complete Phase 1 through Phase 19 guide](docs/guides/PHASES-1-19.md), the [detailed pack examples](docs/guides/PACK-AUTHORING.md), the [command reference](docs/guides/COMMANDS.md), the [Phase 19 verification and mass check](docs/verification/PHASE-19.md), the [generated schema reference](docs/reference/SCHEMA-V2.md), the [compatibility matrix](docs/compatibility/COMPATIBILITY_MATRIX.md), and [PERF-001](docs/performance/PERF-001.md).

In a cheats enabled test world, use `/pskills doctor`, `/pskills why latest`, and `/pskills check start`. The Progression screen opens with `P`, its Tests tab contains the visual Test Center, and the command palette opens with the grave accent key.
