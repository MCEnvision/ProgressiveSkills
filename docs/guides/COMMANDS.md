# Command reference

All ProgressiveSkills commands use `/ps`. Commands marked Player must be run by a player. Commands marked OP 2, OP 3, or OP 4 require that Minecraft permission level. Arguments in angle brackets are required. Text ending in `...` is a greedy argument and may contain spaces.

Mutation commands always call the same server planners used by the UI. Commands do not create a second authority path.

## Pack and definition commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps help` | Any | Print a compact command overview. |
| `/ps status` | OP 2 | Show the live generation, pack count, definition count, digest, recovery state, and staged candidate state. |
| `/ps validate` | OP 2 | Compile current pack sources without staging or publishing. |
| `/ps reload` | OP 2 | Alias for a dry run with diff output. |
| `/ps reload --dry-run` | OP 2 | Stage the exact reviewed candidate and show its semantic diff. |
| `/ps reload --publish` | OP 4 | Reread and atomically publish the unchanged staged candidate. |
| `/ps diff` | OP 2 | Show the current staged candidate against live definitions. |
| `/ps info pack <pack_id>` | OP 2 | Inspect one pack manifest and dependency state. |
| `/ps info <kind> <id>` | OP 2 | Inspect one canonical definition. |
| `/ps info <kind> <id> --provenance` | OP 2 | Include bounded field provenance. |

Kinds are namespaced, for example `progressiveskills:skill`, `progressiveskills:rule`, `progressiveskills:tree`, `progressiveskills:class`, `progressiveskills:ability`, and `progressiveskills:item`.

Example:

```text
/ps info progressiveskills:skill progressiveskills:physique --provenance
```

## Lifecycle and transaction diagnostics

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps lifecycle status` | OP 2 | Show the demonstration transaction state. |
| `/ps lifecycle demo` | OP 4 | Run the receipt and idempotency demonstration. |
| `/ps lifecycle coowner` | OP 4 | Add a second persistent owner. |
| `/ps lifecycle recompute` | OP 4 | Reproject persistent state without transition replay. |
| `/ps lifecycle revoke primary` | OP 4 | Remove the primary demonstration owner. |
| `/ps lifecycle revoke secondary` | OP 4 | Remove the secondary demonstration owner. |
| `/ps lifecycle audit` | OP 4 | Print bounded transaction audit evidence. |
| `/ps lifecycle selftest` | OP 4 | Execute lifecycle invariant checks in game. |

## Persistence commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps persistence status` | Player and OP 2 | Show attachment version, state, revisions, balances, ownership, receipts, orphans, pending operations, and quarantine. |
| `/ps persistence snapshot` | Player and OP 3 | Write a verified machine oriented snapshot under the world directory. |
| `/ps persistence export` | Player and OP 3 | Write a readable bounded export under the world directory. |

## Network commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps network status` | Player and OP 2 | Show Protocol 7 phase, generation, digests, revisions, cache, deltas, resyncs, and intent replay state. |
| `/ps network resync` | Player and OP 4 | Start a fresh digest bound handshake and full owner state synchronization. |

## Skill and XP commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps skill get <skill>` | Player | Show level, highest level, XP, current level progress, next cost, banked XP, and related currency. |
| `/ps xp <player> <skill> <amount>` | OP 2 | Award or remove a fixed point manual amount according to skill policy. |
| `/ps xp source <player> <key>` | OP 2 | Execute a named custom XP source from a skill definition. |

Examples:

```text
/ps xp @s progressiveskills:physique 25
/ps xp @s progressiveskills:physique 0.5
/ps xp source @s progressiveskills:physique_training
/ps skill get progressiveskills:physique
```

## Rule and explanation commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps rule status` | Player | Show rule count, enabled count, block provenance count, and provenance reliability. |
| `/ps rule preview <rule>` | Player | Evaluate disclosed requirements and amount without mutation or anti exploit memory consumption. |
| `/ps explain xp last` | Player | Explain the most recent XP route event and anti exploit outcome. |

## Tree commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps tree list` | Player | List trees and their currency and state. |
| `/ps tree info <tree>` | Player | Show tree and node details. |
| `/ps tree preview buy <tree> <node>` | Player | Preview purchase requirements and cost. |
| `/ps tree buy <tree> <node>` | Player | Buy a node through the authoritative transaction. |
| `/ps tree preview refund <tree> <node>` | Player | Preview exact refund and dependent closure and return a digest. |
| `/ps tree refund <tree> <node> <digest>` | Player | Confirm the exact refund preview. |
| `/ps tree preview respec <tree>` | Player | Preview full tree respec and return a digest. |
| `/ps tree respec <tree> <digest>` | Player | Confirm the exact tree respec preview. |

Example:

```text
/ps tree preview refund progressiveskills:physique_training progressiveskills:physique_training/conditioning
/ps tree refund progressiveskills:physique_training progressiveskills:physique_training/conditioning 4f2b...
```

Use the complete digest printed by preview. The shortened value above is illustrative only.

## Class commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps class list` | Player | List slots, classes, weights, and active, suspended, or available state. |
| `/ps class info <class>` | Player | Show requirements, slot, costs, grants, and state. |
| `/ps class entitlements` | Player | Show source owned class entitlements. |
| `/ps class preview select <class>` | Player | Preview selection requirements and cost. |
| `/ps class select <class>` | Player | Select a class. |
| `/ps class preview respec <class>` | Player | Preview respec and return a digest. |
| `/ps class respec <class> <digest>` | Player | Confirm the exact respec. |
| `/ps class preview swap <removed> <replacement>` | Player | Preview one atomic class swap and return a digest. |
| `/ps class swap <removed> <replacement> <digest>` | Player | Confirm the exact class swap. |

## Ability commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps ability list` | Player | List definitions, ownership, kind, and assignment. |
| `/ps ability info <ability>` | Player | Show target, cooldown, charges, costs, persistent effects, and actions. |
| `/ps ability status` | Player | Show selected slot and all eight fixed slots. |
| `/ps ability assign <ability> <slot>` | Player | Assign an owned slot eligible ability to slot 1 through 8. |
| `/ps ability unassign <slot>` | Player | Empty one slot. |
| `/ps ability select <slot>` | Player | Select an assigned slot. |
| `/ps ability toggle <ability>` | Player | Change an owned toggle ability. |
| `/ps ability activate <slot>` | Player | Activate the assigned active ability. |

## Carrier and claim commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps item list` | Any | List live carrier definitions. |
| `/ps item info <item_def>` | Any | Inspect one carrier definition and action summary. |
| `/ps item held` | Player | Inspect the held carrier identity, pinned digest, state, and validity. |
| `/ps item archive status` | OP 2 | Show archive entry and byte usage. |
| `/ps item archive verify` | OP 2 | Verify retained canonical behavior snapshots. |
| `/ps item migrate held preview` | Player | Preview explicit migration of the held carrier. |
| `/ps item migrate held confirm <digest>` | Player | Confirm the exact migration preview. |
| `/ps give <player> <item_def>` | OP 2 | Deliver one configured carrier. |
| `/ps give <player> <item_def> <count>` | OP 2 | Deliver a bounded carrier count. |
| `/ps claim list` | Player | List durable pending carrier claims. |
| `/ps claim take <claim_id>` | Player | Recover one claim. |
| `/ps claim take all` | Player | Recover as many claims as inventory capacity permits. |

## Compatibility and provider commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps compatibility status` | Any | Probe every provider and show version, health, circuit state, adapter errors, and active plan. |
| `/ps diagnose` | Any | Alias for compatibility status. |
| `/ps compatibility profile list` | Any | List pack defined profiles. |
| `/ps compatibility profile active` | Any | Inspect the active pack profile. |
| `/ps compatibility profile <id>` | Any | Resolve one loaded profile against current providers. |
| `/ps compatibility profile strict` | Any | Preview strict capability behavior. |
| `/ps compatibility profile preferred` | Any | Preview preferred capability behavior. |
| `/ps compatibility profile fallback` | Any | Preview fallback capability behavior. |

## Diagnostics, reproduction, and mass check

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps doctor` | Any | Run readable pack, data, network, archive, provider, transaction, and performance checks. |
| `/ps doctor json` | Any | Return the same check set as compact JSON. |
| `/ps why latest` | Player | Explain the latest recorded decision. |
| `/ps why verbose` | Player | Include time, revision, and bounded decision history. |
| `/ps reproduce export` | Player | Write a redacted deterministic decision bundle. |
| `/ps reproduce replay <file>` | Any | Replay an allowed reproduction filename against its captured state and current definition digest. |
| `/ps perf smoke` | OP 2 | Run the locked synthetic rule workload and report percentiles. |
| `/ps check start` | Player | Start or reset the persistent world mass checklist for the player. |
| `/ps check next` | Player | Move to the next row without changing the current result. |
| `/ps check pass [note...]` | Player | Mark the current row passed and advance. |
| `/ps check fail [note...]` | Player | Mark the current row failed and advance. |
| `/ps check skip [note...]` | Player | Mark the current row skipped and advance. |
| `/ps check status` | Player | Show totals and current row. |
| `/ps check finish` | Player | Finish and summarize the current session. |
| `/ps check export` | Player | Export every result and note under the world report directory. |

## Creator commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps creator catalog` | Player | List loaded Creator definition keys. |
| `/ps creator simulate <formula...>` | Player | Evaluate a safe formula against variables and player balances. |
| `/ps resource get <resource>` | Player | Show a custom resource value. |
| `/ps resource add <resource> <amount>` | OP 2 | Adjust a bounded custom resource. Negative amounts are allowed within bounds. |
| `/ps convert <conversion> <amount>` | Player | Execute one pack defined currency conversion. |
| `/ps prestige <track>` | Player | Execute an eligible prestige track. |
| `/ps milestone choose <milestone> <choice>` | Player | Select one milestone reward choice under its respec policy. |
| `/ps contract assign <contract>` | Player | Deterministically assign a training contract. |
| `/ps contract status` | Player | Show active contract and progress. |
| `/ps contract progress <contract> <skill> <amount>` | OP 2 | Add bounded contract progress for testing or an authorized bridge. |
| `/ps combo <combo> <award>` | Player | Submit one ordered combo award. |
| `/ps loadout save <id>` | Player | Save current classes, tree nodes, and ability assignments. |
| `/ps loadout code <id>` | Player | Print one saved loadout code. |
| `/ps loadout apply <id>` | Player | Apply one saved loadout atomically. |
| `/ps build code` | Player | Encode the current build. |
| `/ps build inspect <code...>` | Player | Decode and inspect a build without applying it. |
| `/ps build apply <code...>` | Player | Apply a digest compatible build atomically. |

## Advanced Creator commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps rank tree <rank>` | Player | Purchase the next configured tree rank. |
| `/ps rank class <rank>` | Player | Purchase the next configured class rank. |
| `/ps stance <stance>` | Player | Select a stance and replace another stance in the same group. |
| `/ps proc <proc> <trigger> <seed>` | OP 2 | Deterministically evaluate and execute a reactive proc. |
| `/ps predicate <predicate>` | Player | Evaluate one reusable predicate and show its trace. |
| `/ps challenge progress <challenge> <amount>` | OP 2 | Add bounded solo challenge progress. |
| `/ps context <effect>` | Player | Evaluate and apply one contextual effect. |

## Party and privacy commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps party create <name...>` | Player | Create a native provider backed party. |
| `/ps party invite <player>` | Player | Invite another player. |
| `/ps party accept` | Player | Accept the current invitation. |
| `/ps party leave` | Player | Leave the party. |
| `/ps party kick <player>` | Player and owner | Remove a member. |
| `/ps party owner <player>` | Player and owner | Transfer ownership. |
| `/ps party disband` | Player and owner | Delete the party. |
| `/ps party status` | Player | Show party identity and membership. |
| `/ps party ready <true or false>` | Player | Set readiness. |
| `/ps party readiness` | Player | Show privacy filtered readiness. |
| `/ps privacy status` | Player | Show all privacy choices. |
| `/ps privacy visibility <public or party or private>` | Player | Set overall visibility. |
| `/ps privacy role <true or false>` | Player | Allow or hide role readiness. |
| `/ps privacy build <true or false>` | Player | Allow or hide build readiness. |
| `/ps privacy resources <true or false>` | Player | Allow or hide resource readiness. |
| `/ps privacy cooldowns <true or false>` | Player | Allow or hide cooldown readiness. |
| `/ps privacy leaderboard <true or false>` | Player | Allow or hide leaderboard identity. |

## Shared progression, mentoring, transfers, and seasons

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps contribution share <source> <total>` | OP 2 | Allocate one party contribution and write receipts. |
| `/ps contribution receipts` | Player | Show privacy safe contribution receipts. |
| `/ps mentor offer <player>` | Player | Offer a mentor relationship. |
| `/ps mentor accept` | Player | Accept the current mentor offer. |
| `/ps mentor remove` | Player | End the relationship. |
| `/ps mentor status` | Player | Show mentor state. |
| `/ps transfer offer <player> <currency> <amount>` | Player | Offer a consent checked currency transfer. |
| `/ps transfer accept` | Player | Accept and revalidate the current transfer. |
| `/ps transfer wallet <currency>` | Player | Show the social wallet balance used by transfers. |
| `/ps transfer grant <player> <currency> <amount>` | OP 2 | Adjust a social wallet for testing or administration. |
| `/ps sharedchallenge <challenge> <amount> <goal>` | OP 2 | Add shared challenge progress. |
| `/ps season status` | Player | Show current season state. |
| `/ps season score <player> <board> <score>` | OP 2 | Set a bounded board score. |
| `/ps season leaderboard <board>` | Player | Show up to ten privacy filtered entries. |
| `/ps season leaderboard <board> <limit>` | Player | Show one through one hundred entries. |
| `/ps season rollover <season> <epoch>` | OP 2 | Perform a replay safe season rollover. |

## Team and community commands

Teams use the pluggable shared progression provider. The native provider is available without another mod.

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ps team create <team>` | Player | Create a namespaced shared progression scope. |
| `/ps team invite <player>` | Player and owner | Invite a player. |
| `/ps team accept` | Player | Accept an invitation. |
| `/ps team leave` | Player | Leave a team. |
| `/ps team owner <player>` | Player and owner | Transfer ownership. |
| `/ps team kick <player>` | Player and owner | Remove a member. |
| `/ps team disband` | Player and owner | Delete the scope. |
| `/ps team status` | Player | Show the provider snapshot. |
| `/ps team contribute <amount>` | Player and OP 2 | Add revision checked shared XP. |
| `/ps community <goal> <amount> <maximum>` | OP 2 | Add bounded community progress. |

## Studio commands

All Studio commands require OP 4. Draft ids, revisions, paths, file sizes, histories, archives, and imported entries are bounded.

| Command | Purpose |
| --- | --- |
| `/ps studio draft create <namespace> <name...>` | Create a draft from the current live base. |
| `/ps studio draft list` | List draft ids, revisions, status, and owners. |
| `/ps studio draft status <draft>` | Inspect one draft. |
| `/ps studio file put <draft> <revision> <path> <contents...>` | Add or replace one revision checked file. |
| `/ps studio file delete <draft> <revision> <path...>` | Delete one revision checked file. |
| `/ps studio lint <draft>` | Compile and validate the draft and return its confirmation digest. |
| `/ps studio diff <draft>` | Show draft impact against its live base. |
| `/ps studio history <draft>` | List bounded draft history. |
| `/ps studio history restore <draft> <revision> <history_revision>` | Restore a historical snapshot into a new current revision. |
| `/ps studio rebase <draft> <revision>` | Rebase a cleanly reconcilable draft onto current live content. |
| `/ps studio publish <draft> <revision> <digest>` | Atomically publish the exact linted revision. |
| `/ps studio rollback <draft> <revision>` | Roll back the draft publication safely. |
| `/ps studio export <draft>` | Export a signed pspack. |
| `/ps studio import <namespace> <file> <name...>` | Verify and import a bounded pspack as a new draft. |
| `/ps studio record start` | Start a redacted gameplay fixture recording. |
| `/ps studio record mark <type> <subject> <value>` | Add one bounded fixture event. |
| `/ps studio record stop <draft> <revision> <fixture>` | Write the recording into the draft without publishing. |
| `/ps studio palette` | Print Studio command discovery. |
| `/ps studio graph export <draft>` | Export the text graph representation. |

Revision workflow example:

```text
/ps studio draft create mypack Balance Pass
# Response gives a random mypack:studio/<uuid> draft id at revision 0.
/ps studio file put <draft_id> 0 variables/xp_multiplier.toml schema_version = 2 ...
# Response gives revision 1.
/ps studio lint <draft_id>
# Response gives a lint digest.
/ps studio diff <draft_id>
/ps studio publish <draft_id> 1 <lint_digest>
```

If another write advances the draft to revision 2, publishing revision 1 fails. Lint revision 2 and use its new digest.

## Recommended diagnostic sequence

When a player reports a failure, capture these commands before changing files:

```text
/ps doctor
/ps status
/ps network status
/ps persistence status
/ps why verbose
/ps explain xp last
/ps item held
/ps item archive verify
/ps compatibility status
```

Then record the exact action, expected result, observed result, definition id, player name, world time, and relevant client and server log excerpts.
