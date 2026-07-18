# Requirements and Expression Foundation

Status: Phase 9 Core architecture contract implemented. Phase 17 adds a separate bounded Creator formula parser and reusable predicate catalog without widening the deliberately flat Core rule requirement syntax.

## Scope

Phase 9 supplies one bounded, typed evaluator for requirement checks and deterministic numeric work. The evaluator is an internal engine service shared by runtime awards, previews, and explanations. It is not a promise that every internal node can be written in a Core content pack.

Core exposes a deliberately small rule-authoring surface:

- direct `skill_level` requirements for the actor;
- direct named `currency` requirements for the actor;
- a flat `requirements` list with AND semantics;
- one rule-level `rounding` choice; and
- the literal base amounts and literal multiplier groups already supported by the rule compiler.

Reusable predicates and general formula text are available through the Phase 17 Creator definition and command surfaces. They do not become implicit Core rule syntax. A Core rule that attempts to use those fields is still rejected instead of receiving placeholder behavior.

## Typed requirement tree

The internal requirement model has four node families:

| Node | Result |
|---|---|
| `all` | Passes only when every child passes. |
| `any` | Passes when at least one child passes. |
| `not` | Inverts its one child. |
| typed leaf | Reads one declared dependency and applies its typed comparison. |

The Phase 9 leaf set contains skill level and named currency. Each Core TOML object declares its subject, and the compiler accepts only `actor` before constructing the internal leaf. The compiled leaf carries its missing-value policy, typed target identity, comparison operation, and comparison value. It never reads an untyped map by an arbitrary string key.

The tree is immutable after publication. Construction validates node count, nesting depth, child count, identifiers, comparison compatibility, and literal bounds against the implementation constants. An invalid tree rejects the candidate generation. Runtime exhaustion or an unavailable required value fails the requirement closed.

Runtime evaluation is pure. It cannot award XP, spend currency, change source memory, or run a command. Children retain canonical order so the same snapshot produces the same result and bounded explanation on every machine. Normal execution may short-circuit in that order; the explanation contains only the leaves actually evaluated before that decision.

## Core TOML boundary

Core rule TOML uses repeated direct requirement objects. Every object is compiled into one typed leaf and the list is wrapped in an internal `all` node.

```toml
[rule]
id = "mypack:trained_stone"
enabled = true
trigger = "progressiveskills:block_break"
priority = 100
stack_group = "mypack:mining_rewards"
stack_rule = "highest"
credit = "actor"
match = ["minecraft:stone"]
base = 5
rounding = "floor"

[[rule.requirements]]
type = "skill_level"
subject = "actor"
missing = false
skill = "progressiveskills:physique"
op = ">="
value = 5

[[rule.requirements]]
type = "currency"
subject = "actor"
missing = false
currency = "progressiveskills:global_points"
op = ">="
value = 2
```

Both leaves must pass. Core does not accept an `all`, `any`, or `not` object in this TOML list even though those nodes exist internally. That boundary lets later systems compile safe trees without prematurely publishing the Creator predicate language.

`missing` is the boolean result used when the declared dependency is unavailable and defaults to `false`. Comparison operators use their symbolic spellings, such as `>=`. A dangling definition reference still fails staged publication; the missing result is a runtime evaluation policy, not permission to publish an unknown ID.

Requirements are gates, not costs. A currency requirement reads the actor's balance but does not debit it. Phase 10 tree purchases introduce transactional costs and exact refunds separately.

## Dependency index

Every compiled tree publishes a deterministic set of typed dependency keys. Phase 9 keys distinguish skill-level state from named-currency state and retain the full resource ID. The index is deduplicated and sorted by stable type and identity rather than hash iteration order.

The runtime uses that index to prepare only the values the tree can read. Preview and explain use the same dependency set and evaluator as commit-time eligibility. Later conditional grants may subscribe to these keys and mark only affected owners dirty; Phase 9 does not introduce a polling watcher or world-context dependency leaves.

Dependency collection has its own hard bound. Compilation rejects a tree that exceeds it, so a published route cannot create an unbounded snapshot or trace.

## Exact numeric AST

The internal numeric AST represents literals and typed variables plus bounded arithmetic nodes such as addition, subtraction, multiplication, division, minimum, maximum, and clamp. Core rule TOML does not parse arbitrary formula text into those nodes in Phase 9. Existing literal rule fields compile directly into the safe internal form.

All progression decimals use the engine's fixed scale of 1,000,000. Arithmetic uses checked exact intermediates and never uses binary floating point for a gameplay or persistence result. Division by zero, a value outside the declared domain, budget exhaustion, or a result that cannot fit the target fixed-point range fails closed with a stable diagnostic.

An expression carries one explicit final rounding policy. Internal nodes do not round independently. The evaluator completes the bounded expression first, then applies `floor`, `ceil`, `nearest`, or `bankers` once when converting to the target integral fixed-point unit. The selected policy and rounded result are available to the explanation hook. Downstream anti-exploit caps still constrain the already evaluated award and do not turn a requirement into a mutation.

## Evaluation budgets

The Core implementation constants place these hard per-expression limits:

| Budget | Requirement tree | Numeric AST |
|---|---:|---:|
| nodes | 64 | 64 |
| nesting depth | 16 | 16 |
| runtime operations | 128 | 128 |
| distinct dependencies | 32 | 32 |
| retained explanation entries | 64 | one bounded summary |
| intermediate numerator or denominator | not applicable | 256 bits |

Child fanout is bounded by the total node limit. The cross-owner dependency index also receives an explicit edge limit from its publishing subsystem.

The compiler checks structural limits before publication. The runtime also decrements an operation budget, so a malformed canonical value or future internal compiler bug cannot bypass the guard. A budget failure denies the candidate, emits a bounded reason, and performs no progression transaction.

The disabled and nonmatching rule paths still return before requirement snapshots or explanation construction. Compiled dependency arrays and node order are immutable, so ordinary runtime evaluation does not discover keys or parse author text on the event thread.

## Preview and explanation hooks

The evaluator returns a typed result independently of whether the caller intends to commit. Together, the compiled evaluator and its result expose:

- pass or fail;
- the root result and bounded visited-node outcomes;
- actual and required typed values where disclosure is allowed;
- the deterministic dependency set and values read;
- the successful operation count or a bounded budget failure; and
- the rounding policy and rounded result for numeric evaluation.

Preview is read-only and uses an immutable actor snapshot. It cannot reserve, spend, award, update cooldowns, or consume first-time state. Commit-time code must evaluate against the current authoritative snapshot and still perform the award through the normal transaction; a preview result is never a transaction permit.

`/ps explain xp last` consumes the same bounded result produced by the live route. Player-facing output may redact server-only values while preserving a safe reason code. Future UI, Creator simulation, and Studio inspection may call the preview hook without creating a second evaluator.

## Phase boundary

Phase 9 proves the safe internal composition model, deterministic dependencies, exact numeric evaluation, and inspectable runtime results. The Phase 17 Creator catalog now exposes bounded formulas and reusable `all`, `any`, `not`, `flag`, and `value` predicates through separate definition kinds. Core rules remain limited to their flat disclosed actor requirements.
