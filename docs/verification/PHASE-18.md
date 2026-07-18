# Phase 18 cumulative implementation record

Status: implemented and verified inside the cumulative Phase 19 beta. Phase 18 was not released as a separate JAR, commit, tag, or approved checkpoint.

## Delivered scope

- provider backed native parties and shared progression teams;
- invitation, ownership, kick, leave, and disband lifecycles;
- readiness with role, build, resource, and cooldown privacy;
- deterministic shared award allocation and contribution receipts;
- combat assist credit and configurable mentoring catch up;
- consent checked currency transfer offers and acceptance;
- shared and community challenges;
- season score, privacy aware leaderboard, and replay safe rollover;
- persistent PvP pair cooldown, daily cap, repeat decay, level gap penalty, and enable policy;
- chat only accessibility for every social operation.

## Detailed example

See [Phase 18 in the complete guide](../guides/PHASES-1-19.md#phase-18-multiplayer-progression) and the [multiplayer profile example](../guides/PACK-AUTHORING.md#multiplayer-profile).

```text
/ps party create Dungeon Team
/ps party invite <player>
/ps party ready true
/ps contribution share mypack:boss_defeat 1000
/ps contribution receipts
/ps season leaderboard mypack:physique_season 10
```

Provider lifecycle, revision checks, privacy, PvP, persistence, and cumulative evidence are recorded in [PHASE-19.md](PHASE-19.md).
