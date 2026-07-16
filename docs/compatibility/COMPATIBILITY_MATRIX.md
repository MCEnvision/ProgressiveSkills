# Living Compatibility Matrix

Status: Phase 1 evidence lock. `TBD pinned` blocks only that adapter; it does not block dependency-free Core work.

| Integration | Tested versions | Required capabilities | Missing / unsupported behavior | CI profile | Status |
|---|---|---|---|---|---|
| Minecraft | `1.21.1` | registries, data packs, dedicated/client runtime | hard requirement | `core` | locked |
| NeoForge | `21.1.236` | events, registries, attachments, payloads, screens, GameTest | hard requirement | `core` | locked and smoke-tested |
| Parchment | `1.21.1:2024.11.17` | same-version parameter names and Javadocs | official Mojang names remain the semantic authority | `core` | locked |
| ProgressiveStages | TBD pinned | source-aware grant/revoke or declared managed/sticky/grant-only fallback | owning pack policy | `compat-ps` | blocked pending spike |
| Iron's Spells 'n Spellbooks | target-pack exact + tested range | cast success, selection, learning policy, attributes | declared optional branch policy | `compat-iss` | blocked pending spike |
| KubeJS | TBD pinned | startup registration and transaction-safe events | bindings unavailable | `compat-kubejs` | blocked pending spike |
| Curios / equipment | TBD pinned | component-aware equip changes and subtype identity | carriers remain usable without slots | `compat-curios` | blocked pending spike |
| FTB Quests / Teams | TBD pinned | reward/task/party identifiers and lifecycle events | feature hidden or disabled | `compat-ftb` | blocked pending spike |
| JEI / EMI / Jade / WTHIT / Patchouli / Modonomicon | each adapter TBD pinned | plugin lifecycle and renderer/subtype APIs | native UI and guide remain the fallback | `compat-ui` | blocked pending spikes |

## Policy

The base build has no optional-mod dependency. An adapter cannot move out of `blocked pending spike` until all of the following are recorded:

1. Exact target artifact and supported version range.
2. The plan's capability-specific executable spike.
3. An absent-mod dedicated-server classloading check.
4. Present-mod client/server behavior tests.
5. Declared fallback for missing, unsupported, or circuit-broken capability.
6. A matching CI profile.

The matrix is updated in the same change as an adapter. A guessed package name, registry ID, or capability is not compatibility evidence.
