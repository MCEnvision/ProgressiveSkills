# PERF-001 — Core Reference Performance Contract

Status: locked before gameplay implementation. Threshold changes require an ADR containing before/after profiles and the reason for the change.

The normative inputs are machine-readable in [`fixtures/perf-001-v1.toml`](fixtures/perf-001-v1.toml). The fixture generator and synthetic-client driver do not exist in the Phase 1 scaffold because their schemas and event routes begin in later phases; when implemented, they must consume this contract without changing its counts, seeds, schedules, or timing. Any such change requires the same ADR as a threshold change.

## Reference environment

- Linux x86-64.
- Eight physical CPU cores at 3.5 GHz or faster.
- Java 21, G1 garbage collector, fixed 8 GiB heap.
- JVM flags: `-Xms8G -Xmx8G -XX:+UseG1GC -XX:+AlwaysPreTouch -XX:MaxGCPauseMillis=100 -Dfile.encoding=UTF-8`.
- Release ProgressiveSkills JAR, not development classes.
- Minecraft view distance 10.
- Simulation distance 10; flat plains world with structures, daylight/weather cycling, random ticks, and natural spawning disabled.
- Fifteen-minute warm-up followed by a thirty-minute captured interval.

The evidence record must include CPU model, kernel, JDK vendor/build, JVM flags, NeoForge and mod hashes, pack digest, world seed, view/simulation distance, and profiler versions.

## Reference fixture and workloads

- A deterministic synthetic pack with 10,000 definitions, exactly five routes per definition, and 50,000 compiled routed rules. IDs and selector families are fixed by the manifest.
- The 40-player mix is 16 mining / 12 combat / 8 movement / 4 condition clients. The 100-player mix is 40 / 30 / 20 / 10.
- Mining fires every 4 ticks, combat every 10, movement samples every tick with a jump every 10, and condition transitions every 20. Per-player offsets, block cycle, target type/count, health/equipment cycle, and arena length are fixed by the manifest.
- Event ordinal modulo 20 fixes traffic at 70% no-match, 25% single-match, and 5% four-match events.
- The seed, reload at captured tick 18,000, screen-open window, changed-definition counts, and cache-hit/cache-miss trials are fixed by the manifest.
- The large-pack reload occurs while 25% of clients have a progression screen open and all qualifying action streams continue.
- Receipt, audit, leaderboard, compression, decompression, and optional-integration soaks where those systems exist.

CI may run a smaller deterministic smoke fixture. A tagged Core release must run the full reference soak.

## Numeric gates

| Initial measurable gate | Core release threshold |
|---|---:|
| PS-attributed tick time, 40-player mix | p95 ≤ 0.75 ms; p99 ≤ 1.5 ms; no sustained 5 ms spikes |
| PS-attributed tick time, 100-player stress | p95 ≤ 2.0 ms; p99 ≤ 4.0 ms |
| Disabled or no-route event after warm-up | 0 B allocation/event and no string/log construction |
| Simple matched XP route after warm-up | mean ≤ 128 B allocation/event; no collection growth |
| 10k-definition parse/compile on reference host | p95 ≤ 10 s off-thread; live server-thread publish slice ≤ 50 ms or maintenance publish is required |
| Cache-hit join at 50 ms RTT | progression-ready p95 ≤ 1 s; ≤ 256 KiB PS transfer/player |
| Cache-miss upper-bound projection at 50 ms RTT | p95 ≤ 10 s; ≤ 16 MiB assembled; clientbound chunks ≤ 512 KiB |
| Client intents | PS hard limit ≤ 16 KiB each and platform hard ceiling < 32 KiB |
| Steady-state state sync in the 40-player mix | mean ≤ 128 KiB/player/minute |

## Capture procedure

1. Build the release artifact with `./gradlew clean build` and record its SHA-256.
2. Start the dedicated server with the fixed heap and G1 flags.
3. Load the versioned fixture, seed, and scripted clients.
4. Warm for 15 minutes without retaining samples.
5. Start JFR with allocation, CPU, monitor, socket, and GC events; capture for 30 minutes.
6. Export tick percentiles, PS-attributed stacks, allocations per routed event, transfer totals, and GC pauses.
7. Run the pure matcher/formula JMH or equivalent allocation benchmark separately. Wall-clock microbenchmarks remain informative and are not flaky CI gates.
8. Archive the JFR, workload manifest, logs, summary, build hash, and raw metrics together.

Reference JFR commands, run immediately after the separately timed 15-minute warm-up, with the real server PID substituted:

```text
jcmd <pid> JFR.start name=progressiveskills settings=profile duration=30m filename=progressiveskills.jfr
jcmd <pid> JFR.check
jcmd <pid> JFR.stop name=progressiveskills
```

## Change control

No threshold, fixture size, warm-up, capture duration, or reference-hardware requirement may change silently. The proposing ADR must include the old and new values, profiles from both procedures, gameplay/reliability impact, migration impact if applicable, and approval. Optimizing the implementation is preferred to moving the goalpost.
