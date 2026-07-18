# Command reference

All ProgressiveSkills commands use `/pskills`. The shorter command root is deliberately left unregistered so another mod can own it. Commands marked Player must be run by a player. Commands marked OP 2, OP 3, or OP 4 require that Minecraft permission level. Arguments in angle brackets are required. Text ending in `...` is a greedy argument and may contain spaces.

Mutation commands always call the same server planners used by the UI. Commands do not create a second authority path.

## Pack and definition commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills help` | Any | Print a compact command overview. |
| `/pskills status` | OP 2 | Show the live generation, pack count, definition count, digest, recovery state, and staged candidate state. |
| `/pskills validate` | OP 2 | Compile current pack sources without staging or publishing. |
| `/pskills reload` | OP 2 | Alias for a dry run with diff output. |
| `/pskills reload --dry-run` | OP 2 | Stage the exact reviewed candidate and show its semantic diff. |
| `/pskills reload --publish` | OP 4 | Reread and atomically publish the unchanged staged candidate. |
| `/pskills diff` | OP 2 | Show the current staged candidate against live definitions. |
| `/pskills info pack <pack_id>` | OP 2 | Inspect one pack manifest and dependency state. |
| `/pskills info <kind> <id>` | OP 2 | Inspect one canonical definition. |
| `/pskills info <kind> <id> --provenance` | OP 2 | Include bounded field provenance. |

Kinds are namespaced, for example `progressiveskills:skill`, `progressiveskills:rule`, `progressiveskills:tree`, `progressiveskills:class`, `progressiveskills:ability`, and `progressiveskills:item`.

Example:

```text
/pskills info progressiveskills:skill progressiveskills:physique --provenance
```

## Lifecycle and transaction diagnostics

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills lifecycle status` | OP 2 | Show the demonstration transaction state. |
| `/pskills lifecycle demo` | OP 4 | Run the receipt and idempotency demonstration. |
| `/pskills lifecycle coowner` | OP 4 | Add a second persistent owner. |
| `/pskills lifecycle recompute` | OP 4 | Reproject persistent state without transition replay. |
| `/pskills lifecycle revoke primary` | OP 4 | Remove the primary demonstration owner. |
| `/pskills lifecycle revoke secondary` | OP 4 | Remove the secondary demonstration owner. |
| `/pskills lifecycle audit` | OP 4 | Print bounded transaction audit evidence. |
| `/pskills lifecycle selftest` | OP 4 | Execute lifecycle invariant checks in game. |

## Persistence commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills persistence status` | Player and OP 2 | Show attachment version, state, revisions, balances, ownership, receipts, orphans, pending operations, and quarantine. |
| `/pskills persistence snapshot` | Player and OP 3 | Write a verified machine oriented snapshot under the world directory. |
| `/pskills persistence export` | Player and OP 3 | Write a readable bounded export under the world directory. |

## Network commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills network status` | Player and OP 2 | Show Protocol 7 phase, generation, digests, revisions, cache, deltas, resyncs, and intent replay state. |
| `/pskills network resync` | Player and OP 4 | Start a fresh digest bound handshake and full owner state synchronization. |

## Skill and XP commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills skill get <skill>` | Player | Show level, highest level, XP, current level progress, next cost, banked XP, and related currency. |
| `/pskills xp <player> <skill> <amount>` | OP 2 | Award or remove a fixed point manual amount according to skill policy. |
| `/pskills xp source <player> <key>` | OP 2 | Execute a named custom XP source from a skill definition. |

Examples:

```text
/pskills xp @s progressiveskills:physique 25
/pskills xp @s progressiveskills:physique 0.5
/pskills xp source @s progressiveskills:physique_training
/pskills skill get progressiveskills:physique
```

## Rule and explanation commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills rule status` | Player | Show rule count, enabled count, block provenance count, and provenance reliability. |
| `/pskills rule preview <rule>` | Player | Evaluate disclosed requirements and amount without mutation or anti exploit memory consumption. |
| `/pskills explain xp last` | Player | Explain the most recent XP route event and anti exploit outcome. |

## Tree commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills tree list` | Player | List trees and their currency and state. |
| `/pskills tree info <tree>` | Player | Show tree and node details. |
| `/pskills tree preview buy <tree> <node>` | Player | Preview purchase requirements and cost. |
| `/pskills tree buy <tree> <node>` | Player | Buy a node through the authoritative transaction. |
| `/pskills tree preview refund <tree> <node>` | Player | Preview exact refund and dependent closure and return a digest. |
| `/pskills tree refund <tree> <node> <digest>` | Player | Confirm the exact refund preview. |
| `/pskills tree preview respec <tree>` | Player | Preview full tree respec and return a digest. |
| `/pskills tree respec <tree> <digest>` | Player | Confirm the exact tree respec preview. |

Example:

```text
/pskills tree preview refund progressiveskills:physique_training progressiveskills:physique_training/conditioning
/pskills tree refund progressiveskills:physique_training progressiveskills:physique_training/conditioning 4f2b...
```

Use the complete digest printed by preview. The shortened value above is illustrative only.

## Class commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills class list` | Player | List slots, classes, weights, and active, suspended, or available state. |
| `/pskills class info <class>` | Player | Show requirements, slot, costs, grants, and state. |
| `/pskills class entitlements` | Player | Show source owned class entitlements. |
| `/pskills class preview select <class>` | Player | Preview selection requirements and cost. |
| `/pskills class select <class>` | Player | Select a class. |
| `/pskills class preview respec <class>` | Player | Preview respec and return a digest. |
| `/pskills class respec <class> <digest>` | Player | Confirm the exact respec. |
| `/pskills class preview swap <removed> <replacement>` | Player | Preview one atomic class swap and return a digest. |
| `/pskills class swap <removed> <replacement> <digest>` | Player | Confirm the exact class swap. |

## Ability commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills ability list` | Player | List definitions, ownership, kind, and assignment. |
| `/pskills ability info <ability>` | Player | Show target, cooldown, charges, costs, persistent effects, and actions. |
| `/pskills ability status` | Player | Show selected slot and all eight fixed slots. |
| `/pskills ability assign <ability> <slot>` | Player | Assign an owned slot eligible ability to slot 1 through 8. |
| `/pskills ability unassign <slot>` | Player | Empty one slot. |
| `/pskills ability select <slot>` | Player | Select an assigned slot. |
| `/pskills ability toggle <ability>` | Player | Change an owned toggle ability. |
| `/pskills ability activate <slot>` | Player | Activate the assigned active ability. |

## Carrier and claim commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills item list` | Any | List live carrier definitions. |
| `/pskills item info <item_def>` | Any | Inspect one carrier definition and action summary. |
| `/pskills item held` | Player | Inspect the held carrier identity, pinned digest, state, and validity. |
| `/pskills item archive status` | OP 2 | Show archive entry and byte usage. |
| `/pskills item archive verify` | OP 2 | Verify retained canonical behavior snapshots. |
| `/pskills item migrate held preview` | Player | Preview explicit migration of the held carrier. |
| `/pskills item migrate held confirm <digest>` | Player | Confirm the exact migration preview. |
| `/pskills give <player> <item_def>` | OP 2 | Deliver one configured carrier. |
| `/pskills give <player> <item_def> <count>` | OP 2 | Deliver a bounded carrier count. |
| `/pskills claim list` | Player | List durable pending carrier claims. |
| `/pskills claim take <claim_id>` | Player | Recover one claim. |
| `/pskills claim take all` | Player | Recover as many claims as inventory capacity permits. |

## Compatibility and provider commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills compatibility status` | Any | Probe every provider and show version, health, circuit state, adapter errors, and active plan. |
| `/pskills diagnose` | Any | Alias for compatibility status. |
| `/pskills compatibility profile list` | Any | List pack defined profiles. |
| `/pskills compatibility profile active` | Any | Inspect the active pack profile. |
| `/pskills compatibility profile <id>` | Any | Resolve one loaded profile against current providers. |
| `/pskills compatibility profile strict` | Any | Preview strict capability behavior. |
| `/pskills compatibility profile preferred` | Any | Preview preferred capability behavior. |
| `/pskills compatibility profile fallback` | Any | Preview fallback capability behavior. |

## Diagnostics, reproduction, and mass check

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills doctor` | Any | Run readable pack, data, network, archive, provider, transaction, and performance checks. |
| `/pskills doctor json` | Any | Return the same check set as compact JSON. |
| `/pskills why latest` | Player | Explain the latest recorded decision. |
| `/pskills why verbose` | Player | Include time, revision, and bounded decision history. |
| `/pskills reproduce export` | Player | Write a redacted deterministic decision bundle. |
| `/pskills reproduce replay <file>` | Any | Replay an allowed reproduction filename against its captured state and current definition digest. |
| `/pskills perf smoke` | OP 2 | Run the locked synthetic rule workload and report percentiles. |
| `/pskills check start` | Player | Start or reset the persistent world mass checklist for the player. |
| `/pskills check next` | Player | Move to the next row without changing the current result. |
| `/pskills check pass [note...]` | Player | Mark the current row passed and advance. |
| `/pskills check fail [note...]` | Player | Mark the current row failed and advance. |
| `/pskills check skip [note...]` | Player | Mark the current row skipped and advance. |
| `/pskills check status` | Player | Show totals and current row. |
| `/pskills check finish` | Player | Finish and summarize the current session. |
| `/pskills check export` | Player | Export every result and note under the world report directory. |

## Creator commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills creator catalog` | Player | List loaded Creator definition keys. |
| `/pskills creator simulate <formula...>` | Player | Evaluate a safe formula against variables and player balances. |
| `/pskills resource get <resource>` | Player | Show a custom resource value. |
| `/pskills resource add <resource> <amount>` | OP 2 | Adjust a bounded custom resource. Negative amounts are allowed within bounds. |
| `/pskills convert <conversion> <amount>` | Player | Execute one pack defined currency conversion. |
| `/pskills prestige <track>` | Player | Execute an eligible prestige track. |
| `/pskills milestone choose <milestone> <choice>` | Player | Select one milestone reward choice under its respec policy. |
| `/pskills contract assign <contract>` | Player | Deterministically assign a training contract. |
| `/pskills contract status` | Player | Show active contract and progress. |
| `/pskills contract progress <contract> <skill> <amount>` | OP 2 | Add bounded contract progress for testing or an authorized bridge. |
| `/pskills combo <combo> <award>` | Player | Submit one ordered combo award. |
| `/pskills loadout save <id>` | Player | Save current classes, tree nodes, and ability assignments. |
| `/pskills loadout code <id>` | Player | Print one saved loadout code. |
| `/pskills loadout apply <id>` | Player | Apply one saved loadout atomically. |
| `/pskills build code` | Player | Encode the current build. |
| `/pskills build inspect <code...>` | Player | Decode and inspect a build without applying it. |
| `/pskills build apply <code...>` | Player | Apply a digest compatible build atomically. |

## Advanced Creator commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills rank tree <rank>` | Player | Purchase the next configured tree rank. |
| `/pskills rank class <rank>` | Player | Purchase the next configured class rank. |
| `/pskills stance <stance>` | Player | Select a stance and replace another stance in the same group. |
| `/pskills proc <proc> <trigger> <seed>` | OP 2 | Deterministically evaluate and execute a reactive proc. |
| `/pskills predicate <predicate>` | Player | Evaluate one reusable predicate and show its trace. |
| `/pskills challenge progress <challenge> <amount>` | OP 2 | Add bounded solo challenge progress. |
| `/pskills context <effect>` | Player | Evaluate and apply one contextual effect. |

## Party and privacy commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills party create <name...>` | Player | Create a native provider backed party. |
| `/pskills party invite <player>` | Player | Invite another player. |
| `/pskills party accept` | Player | Accept the current invitation. |
| `/pskills party leave` | Player | Leave the party. |
| `/pskills party kick <player>` | Player and owner | Remove a member. |
| `/pskills party owner <player>` | Player and owner | Transfer ownership. |
| `/pskills party disband` | Player and owner | Delete the party. |
| `/pskills party status` | Player | Show party identity and membership. |
| `/pskills party ready <true or false>` | Player | Set readiness. |
| `/pskills party readiness` | Player | Show privacy filtered readiness. |
| `/pskills privacy status` | Player | Show all privacy choices. |
| `/pskills privacy visibility <public or party or private>` | Player | Set overall visibility. |
| `/pskills privacy role <true or false>` | Player | Allow or hide role readiness. |
| `/pskills privacy build <true or false>` | Player | Allow or hide build readiness. |
| `/pskills privacy resources <true or false>` | Player | Allow or hide resource readiness. |
| `/pskills privacy cooldowns <true or false>` | Player | Allow or hide cooldown readiness. |
| `/pskills privacy leaderboard <true or false>` | Player | Allow or hide leaderboard identity. |

## Shared progression, mentoring, transfers, and seasons

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills contribution share <source> <total>` | OP 2 | Allocate one party contribution and write receipts. |
| `/pskills contribution receipts` | Player | Show privacy safe contribution receipts. |
| `/pskills mentor offer <player>` | Player | Offer a mentor relationship. |
| `/pskills mentor accept` | Player | Accept the current mentor offer. |
| `/pskills mentor remove` | Player | End the relationship. |
| `/pskills mentor status` | Player | Show mentor state. |
| `/pskills transfer offer <player> <currency> <amount>` | Player | Offer a consent checked currency transfer. |
| `/pskills transfer accept` | Player | Accept and revalidate the current transfer. |
| `/pskills transfer wallet <currency>` | Player | Show the social wallet balance used by transfers. |
| `/pskills transfer grant <player> <currency> <amount>` | OP 2 | Adjust a social wallet for testing or administration. |
| `/pskills sharedchallenge <challenge> <amount> <goal>` | OP 2 | Add shared challenge progress. |
| `/pskills season status` | Player | Show current season state. |
| `/pskills season score <player> <board> <score>` | OP 2 | Set a bounded board score. |
| `/pskills season leaderboard <board>` | Player | Show up to ten privacy filtered entries. |
| `/pskills season leaderboard <board> <limit>` | Player | Show one through one hundred entries. |
| `/pskills season rollover <season> <epoch>` | OP 2 | Perform a replay safe season rollover. |

## Team and community commands

Teams use the pluggable shared progression provider. The native provider is available without another mod.

| Command | Permission | Purpose |
| --- | --- | --- |
| `/pskills team create <team>` | Player | Create a namespaced shared progression scope. |
| `/pskills team invite <player>` | Player and owner | Invite a player. |
| `/pskills team accept` | Player | Accept an invitation. |
| `/pskills team leave` | Player | Leave a team. |
| `/pskills team owner <player>` | Player and owner | Transfer ownership. |
| `/pskills team kick <player>` | Player and owner | Remove a member. |
| `/pskills team disband` | Player and owner | Delete the scope. |
| `/pskills team status` | Player | Show the provider snapshot. |
| `/pskills team contribute <amount>` | Player and OP 2 | Add revision checked shared XP. |
| `/pskills community <goal> <amount> <maximum>` | OP 2 | Add bounded community progress. |

## Studio commands

All Studio commands require OP 4. Draft ids, revisions, paths, file sizes, histories, archives, and imported entries are bounded.

| Command | Purpose |
| --- | --- |
| `/pskills studio draft create <namespace> <name...>` | Create a draft from the current live base. |
| `/pskills studio draft list` | List draft ids, revisions, status, and owners. |
| `/pskills studio draft status <draft>` | Inspect one draft. |
| `/pskills studio file put <draft> <revision> <path> <contents...>` | Add or replace one revision checked file. |
| `/pskills studio file delete <draft> <revision> <path...>` | Delete one revision checked file. |
| `/pskills studio lint <draft>` | Compile and validate the draft and return its confirmation digest. |
| `/pskills studio diff <draft>` | Show draft impact against its live base. |
| `/pskills studio history <draft>` | List bounded draft history. |
| `/pskills studio history restore <draft> <revision> <history_revision>` | Restore a historical snapshot into a new current revision. |
| `/pskills studio rebase <draft> <revision>` | Rebase a cleanly reconcilable draft onto current live content. |
| `/pskills studio publish <draft> <revision> <digest>` | Atomically publish the exact linted revision. |
| `/pskills studio rollback <draft> <revision>` | Roll back the draft publication safely. |
| `/pskills studio export <draft>` | Export a signed pspack. |
| `/pskills studio import <namespace> <file> <name...>` | Verify and import a bounded pspack as a new draft. |
| `/pskills studio record start` | Start a redacted gameplay fixture recording. |
| `/pskills studio record mark <type> <subject> <value>` | Add one bounded fixture event. |
| `/pskills studio record stop <draft> <revision> <fixture>` | Write the recording into the draft without publishing. |
| `/pskills studio palette` | Print Studio command discovery. |
| `/pskills studio graph export <draft>` | Export the text graph representation. |

Revision workflow example:

```text
/pskills studio draft create mypack Balance Pass
# Response gives a random mypack:studio/<uuid> draft id at revision 0.
/pskills studio file put <draft_id> 0 variables/xp_multiplier.toml schema_version = 2 ...
# Response gives revision 1.
/pskills studio lint <draft_id>
# Response gives a lint digest.
/pskills studio diff <draft_id>
/pskills studio publish <draft_id> 1 <lint_digest>
```

If another write advances the draft to revision 2, publishing revision 1 fails. Lint revision 2 and use its new digest.

## Recommended diagnostic sequence

When a player reports a failure, capture these commands before changing files:

```text
/pskills doctor
/pskills status
/pskills network status
/pskills persistence status
/pskills why verbose
/pskills explain xp last
/pskills item held
/pskills item archive verify
/pskills compatibility status
```

Then record the exact action, expected result, observed result, definition id, player name, world time, and relevant client and server log excerpts.
