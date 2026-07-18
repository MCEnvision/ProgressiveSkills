# Package Boundary Policy

Status: locked and architecture tested through the approved Phase 19 release.

The canonical root package is `com.envisione.progressiveskills`.

| Package | Responsibility | Allowed inward dependencies |
|---|---|---|
| root bootstrap | NeoForge entry point and locked identity only | `common`, `api`, NeoForge bootstrap APIs |
| `api` | Stable public provider/event/service contracts | JDK, Minecraft/NeoForge public types chosen for the API |
| `common` | Physical-side-neutral engine code | `api`, JDK, side-neutral Minecraft/NeoForge types |
| `server` | Logical-server orchestration and world mutation | `common`, `api`, server-safe Minecraft/NeoForge types |
| `client` | Screens, rendering, key mappings, HUD, client caches | `common`, `api`, client Minecraft/NeoForge types |
| `compat` | Isolated optional-mod implementations and capability probes | `common`, `api`, exactly pinned optional APIs |
| `mixin` | Last-resort hooks when no supported event/API exists | The narrow target required by an approved technical decision |

## Enforced rules

- No class outside `client` may depend on the internal client package, `net.minecraft.client`, Blaze3D, or LWJGL.
- `common` may not depend on `client`, `server`, `compat`, or `mixin`.
- `api` may not expose internal implementation packages.
- Optional-mod packages may appear only inside `compat` implementations.
- `server` and `client` may not depend on one another.
- The root bootstrap must remain side-safe; a dedicated server must load it without resolving client classes.

The JUnit architecture suite checks bytecode dependencies without initializing the inspected classes. Dedicated-server and client smoke runs then verify actual classloading on both physical sides.

The canonical foundation rule locks `common.id`, `common.source`, `common.ir`, `common.presentation`, `common.schema`, `common.diagnostic`, Phase 3's `common.pack`, and Phase 4's `common.transaction` away from client/server classes, registries/holders, NBT, network protocol/chat types, NeoForge runtime types, vanilla Components, ItemStacks, entities, worlds, and levels. These packages store data-only descriptors, plans, results, and unresolved `ResourceLocation`s. Filesystem, Minecraft projection/action adapters, runtime lifecycle wiring, and commands live under `server`.

Phase 5's `common.data` is the narrow persistence boundary: it may use side-neutral NBT and NeoForge attachment contracts, but it may not depend on client code, logical-server orchestration, worlds, levels, inventories, or physical projection. Pending world `SavedData`, snapshot files, login/death orchestration, and commands remain under `server`.

Phase 6's `common.network` is the physical-side-neutral wire/state-machine boundary. It may use side-neutral Minecraft payload/buffer types and NeoForge payload registration/handling contracts, but has no client or server implementation dependency. Logical-server projection, persistent server identity, lifecycle wiring, packet distribution, and operator commands stay under `server.network`/`server.command`; the physical-client logout hook contains no server type.

When an optional adapter is introduced, its common-facing factory must check mod ID and supported version before the implementation class is loaded. Reflection does not excuse leaking foreign types into shared signatures.
