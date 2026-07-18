# Phase 13 cumulative implementation record

Status: implemented and verified inside the approved Phase 19 release. Phase 13 was not released as a separate JAR, commit, tag, or approved checkpoint.

## Delivered scope

- pre registered artifact, charm, consumable, token, and tome carrier items;
- typed carrier definitions and canonical behavior digests;
- behavior version pinning and a checksummed non aging world archive;
- charges, binding, cooldown, issuance identity, and monotonic use counters;
- XP, skill level, currency, and tree respec actions;
- inventory preflight, durable pending claims, and claim recovery;
- held item inspection, archive diagnostics, and explicit digest confirmed migration;
- sanitized carrier and claim projection through Protocol 7;
- inert fail closed behavior for tampering, missing archives, duplicate use, stale previews, and invalid definitions.

## Detailed example

See [Phase 13 in the complete guide](../guides/PHASES-1-19.md#phase-13-carrier-items-archives-and-claims) and the [carrier pack example](../guides/PACK-AUTHORING.md#carrier-item).

Focused workflow:

```text
/pskills item list
/pskills item info progressiveskills:tome_of_physique
/pskills give @s progressiveskills:tome_of_physique 1
/pskills item held
/pskills item archive verify
/pskills claim list
/pskills claim take all
```

Automated evidence, release JAR identity, and the final player checklist are recorded in [PHASE-19.md](PHASE-19.md).
