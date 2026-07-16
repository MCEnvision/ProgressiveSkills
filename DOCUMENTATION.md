# ProgressiveSkills Documentation

## Current scope

This documentation covers the Phase 1 scaffold. ProgressiveSkills does not yet provide skills or other gameplay content. The base JAR intentionally contains only the loader bootstrap and physical-side boundary setup; the canonical schema and definition engine begin in Phase 2.

## Prerequisites

- A 64-bit Java 21 toolchain. Gradle may download one through the pinned Foojay resolver convention when necessary.
- Internet access for the first dependency and Minecraft-asset resolution.
- `xvfb-run` only for an automated client smoke on a headless Linux host.

The checked-in wrapper downloads Gradle 8.8 and verifies its official SHA-256 before execution.

## Three-minute setup

1. Open the repository as a Gradle project.
2. Run `./gradlew clean build`.
3. Run `./gradlew runGameTestServer`.
4. Run `bash .ci/smoke-server.sh` to prove dedicated-server startup without optional mods.
5. On a desktop, run `./gradlew runClient`; on headless Linux, run `bash .ci/smoke-client.sh`.
6. Run `.ci/verify-release-jar.sh` to compare the release archive against all compiled test outputs and verify its locked metadata.

The release JAR is created at `build/libs/progressiveskills-1.0-SNAPSHOT.jar`.

## Test layers

| Layer | Command | Purpose |
|---|---|---|
| JUnit | `./gradlew test` | Identity, generated metadata, Java toolchain, and architecture rules |
| Property | included in `test` | Proves jqwik discovery and exercises valid Minecraft resource paths |
| Compilation | `./gradlew build` | Compiles main, GameTest, and test sources with all warnings treated as errors |
| GameTest | `./gradlew runGameTestServer` | Boots NeoForge, executes the non-shipping in-world harness test, and fails without its success marker |
| Dedicated server | `bash .ci/smoke-server.sh` | Requires an empty mods folder and production-only classpath, boots to `Done`, then shuts down |
| Client | `bash .ci/smoke-client.sh` | Boots the production-only classpath under Xvfb and waits for the real title screen |
| Release archive | `.ci/verify-release-jar.sh` | Rejects compiled test output, test libraries, retired identities, or unlocked metadata in the shipping JAR |

Gradle fails if test sources disappear, if the JUnit Platform discovers zero tests, or if the GameTest log lacks a passing result. The GameTest source set and generated NBT fixture are bound only to the dedicated GameTest run and are excluded from ordinary client/server runs and the release JAR.

## Package policy

The canonical root is `com.envisione.progressiveskills`. Code is divided into `api`, `common`, `server`, `client`, `compat`, and `mixin` boundaries. The executable architecture tests enforce side direction and optional-mod isolation. See [PACKAGE_BOUNDARIES.md](docs/architecture/PACKAGE_BOUNDARIES.md).

## Optional integrations

No optional integration is currently compiled or loaded. Each adapter stays blocked until its exact target version, technical spike, absent-mod load test, and compatibility profile are green. See [COMPATIBILITY_MATRIX.md](docs/compatibility/COMPATIBILITY_MATRIX.md).

## Troubleshooting

| Problem | Likely cause | Fix |
|---|---|---|
| Gradle cannot find Java 21 | Toolchain download is unavailable or blocked | Install a 64-bit Java 21 JDK or restore network access to the configured resolver |
| `runGameTestServer` reports no tests | The `gameTest` source-set binding or generated fixture was changed | Run `./gradlew clean gameTestClasses` and inspect `build/generated/gameTestResources` |
| Server stops for EULA | A raw `runServer` invocation has no accepted development EULA | Use `.ci/smoke-server.sh` or place `eula=true` in that run configuration's directory |
| Client smoke cannot start | Xvfb is absent | Install `xvfb` or run `./gradlew runClient` in a graphical session |
| Architecture test fails | A shared class references client or optional-mod code | Move the implementation behind the appropriate `client` or `compat` boundary and expose only neutral contracts |

## Next milestone

Phase 2 adds only schema metadata and the immutable canonical IR: source spans, schema versions, stable typed IDs, aliases, presentation types, diagnostics, and generated documentation metadata. It will not add gameplay yet.
