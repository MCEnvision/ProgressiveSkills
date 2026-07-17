# Schema Registry and Canonical IR

Status: Phase 2 foundation implemented, consumed by the Phase 3 TOML compiler, and extended with Phase 4 transaction, Phase 5 persistence, and Phase 6 networking metadata. No gameplay definition schemas are included.

## Boundary

Phase 2 establishes the vocabulary that later authoring adapters compile into:

```text
authoring source (Phase 3 TOML; later adapters follow)
  -> strict typed identity + field source map
  -> schema-guided compilation
  -> immutable CanonicalDefinition
  -> semanticProjection() without provenance
```

The historical Phase 2 boundary excluded TOML discovery/parsing, pack manifests, merge/patch, staging/live publication, and `/ps` commands. Phase 3 supplies those consumers in `common.pack` and `server.pack` without changing the immutable IR contract. Phase 4 registers internal metadata for transaction plans, transition actions, entitlement contributions, and audit records while keeping execution in `common.transaction`. Phase 5 adds internal metadata for the player attachment, stored definition state, durable operation receipts, pending offline operations, and snapshot envelopes. Phase 6 adds handshake, sanitized projection, transfer, full/delta state, and intent contracts; executable codecs/state machines remain in `common.network` and orchestration in `server.network`. Gameplay definitions remain outside this document; concrete `Skill`, `Tree`, `Class`, and other records arrive with their feature phases.

## Delivery boundary against plan §§33.4–33.5

Phase 2 supplies a data-only schema/IR foundation and two deterministic reference artifacts. It does not claim that the complete shared-presentation and generated-schema systems described in the master plan are already delivered.

| Plan capability | Phase 2 candidate | Deferred work |
|---|---|---|
| `ComponentSpec`, `StyleSpec`, and `IconSpec` | Immutable bounded records, stable schema metadata, safe placeholder/style vocabulary, and tagged/arity-checked unresolved icon references | Authoring codecs, format-specific icon payload compilation, runtime locale/registry resolution, and conversion to vanilla presentation objects |
| Bounded safe JSON Component subset | The IR record can represent only the foundation's non-interpreting component structure | A bounded JSON decoder/sanitizer, hover-text policy, and any allowlisted trusted server-generated interactions |
| `SoundSpec`, `ParticleSpec`, and `VisibilitySpec` | Not implemented | Add their typed records, schemas, validation, projections, and consumers with the feature phases that first need them |
| Locale fallback, plural/select, localized numbers, RTL metadata, and glyph/overflow checks | Locale key, required fallback, typed placeholder declarations, definition-level search aliases, and safe text bounds only | `PsLocaleResolver`, pack locale loading, bounded plural/select evaluation, locale fallback chains, missing/unused key checks, RTL handling, glyph checks, and layout overflow validation |
| Generated parsers/codecs and validation constraints | Foundation records enforce local invariants; Phase 3 adds a strict hand-written TOML adapter for manifests/layers and the two shared definition kinds | Generate broader executable codecs/constraints alongside later typed definitions; other adapters remain deferred |
| Reference documentation | Deterministic Markdown reference tables and diagnostic help are generated | Expand generated reference coverage alongside each concrete definition type |
| Native encyclopedia and editor help | Machine-readable field descriptions, ordering, examples, and widget hints are generated | Native encyclopedia UI/search/reverse references and an editor/Studio consumer |
| Studio fields/widgets | Widget metadata exists | Studio forms, validation UX, and editing workflows |
| Command suggestions | Phase 3 suggestions use the live kind/pack/definition registries | Generate richer field/value suggestions as concrete typed schemas arrive |
| TOML/JSON editor schemas and snippets | Not implemented; the editor catalog is not a TOML schema, JSON Schema, or snippet bundle | Generate format-specific schemas/snippets from the registry after adapter grammar is fixed |
| Diff/default behavior | Diff policy and default-elision metadata exist | Executable semantic diff/default-elision engine |
| Client projection/redaction | Per-field projection policy metadata plus Phase 6 sanitized definition/full/delta projection, bounded networking, and secrecy tests | Gameplay-specific visible DTO fields expand only with their implementation phases |
| Test parameter catalogs | Not implemented | Generate reusable conformance inputs once codecs and executable constraints exist |

The checked-in `schema-v2-editor.json` is therefore a catalog for future tooling, not evidence that every renderer listed in §33.5 exists.

## Stable identity

`DefinitionKey` is the pair `(DefinitionKind, ResourceLocation)`. `DefinitionKind` is an extensible namespaced value rather than an ordinal enum, so providers can add future kinds without changing persisted numeric identities.

`StableId` requires an explicit nonblank `namespace:path`, applies tighter length/segment limits than vanilla, and rejects traversal or empty path segments. Minecraft's implicit `minecraft` namespace behavior is never used for definition identity. Ordering is kind-first and namespace-first; vanilla's path-first natural `ResourceLocation` ordering is not used.

Path derivation is exact. For kind directory `skills`, pack namespace `mypack`, and `skills/combat/physique.toml`, the result is `mypack:combat/physique`. An explicit authored ID must equal that result. Display text, icons, row order, filenames outside the normalized path, and list indexes are not persistence identity.

The kind registry rejects two providers assigning different directories to one kind ID and also rejects two kind IDs claiming the same discovery directory. The plan's readability warning for two different kinds sharing the same `ResourceLocation` belongs to Phase 3 whole-pack validation, when a complete staged definition set exists.

Replacement aliases are same-kind, direct old-to-terminal mappings. Self aliases, cross-kind aliases, conflicting targets, chains, cycles, and overlong graphs are rejected before an `AliasMap` can be created. Many retired IDs may map directly to the same terminal ID.

## Source data versus meaning

`Provenance`, `SourceReference`, and `SourceMap` retain portable pack-relative POSIX paths and one-based half-open spans. Absolute host paths are forbidden.

`CanonicalDefinition` keeps its `Provenance` and field-level `SourceMap`, while `semanticProjection()` returns only the header and typed fields. Ordinary record equality remains honest: definitions from different sources are different sourced records. Adapter-parity comparison and future semantic digests use the explicit projection, so source locations cannot accidentally change gameplay meaning.

## Phase 3 compiler normalization contract

Phase 2 validates and stores the target data shapes. Phase 3 now parses TOML for manifests, layers, components, and icons and owns the single normalization step before a supported definition enters canonical IR. Future JSON/builder adapters must follow the same contract:

- normalize syntax-specific component maps, defaults, escaping, and allowed `&` shorthand into one canonical `ComponentSpec` representation before semantic comparison; conversion to vanilla text/style objects remains a later trusted-boundary operation;
- translate icon authoring fields such as `type`, `value`, `values`, `fallback`, `alt`, `narration`, and entity-preview opt-in into the tagged `IconSpec` reference model, including the documented alt/narration default;
- derive stable identity from the source path and compare an optional authored `id` with that result; and
- emit the same typed canonical fields for equivalent TOML, future JSON, and builder fixtures, while retaining adapter-specific provenance and source spans separately.

The TOML fixtures prove that source comments and field order do not alter semantic digests while source digests still detect review-time changes. Cross-adapter parity remains deferred until a second adapter exists; it will compare semantic projections/digests after provenance and source maps are excluded.

## Immutable canonical values

The generic envelope accepts only the closed `CanonicalValue` vocabulary: booleans, checked integers, normalized decimals, bounded text, strict IDs, typed references, safe components/icons, bounded lists, and deterministically ordered objects. Collections are defensively copied and exposed through unmodifiable views. Definition-level depth, node, and aggregate text-cost limits prevent repeated bounded values from creating an effectively unbounded canonical payload. Phase 3 adds source/envelope byte and pack-wide quotas before decoding; see [CONTENT_PACKS.md](CONTENT_PACKS.md).

This envelope is the shared foundation, not a substitute for concrete typed gameplay records. A feature phase registers its schema and compiler, then emits its typed definition and/or canonical values through the same immutable header/provenance contract.

## Safe presentation descriptors

The IR never stores vanilla `Component`, `MutableComponent`, `ItemStack`, registry objects, entities, or client renderer types.

`ComponentSpec` contains a bounded optional locale key, required fallback, typed placeholder declarations, allowlisted style, and bounded children. It cannot represent selectors, scores, NBT, keybind interpretation, commands, URLs, files, click events, or registry-bearing hover events. Its constructor validates the accepted `&` subset, but authoring-syntax normalization and rendering are later compiler/presentation-boundary responsibilities.

`IconSpec` contains tagged unresolved `ResourceLocation` references, a strict fallback, accessible alt/narration semantics, and explicit entity-preview opt-in. Missing registry entries are resolved later through injected lookups; no defaulted registry lookup can silently turn an unknown ID into air during schema compilation.

Definitions may omit presentation entirely. If presentation is authored, declarative schema constraints require display and icon together; description and non-default search aliases also require that pair. The canonical `DefinitionPresentation` therefore never represents a half-present player-facing identity.

## One metadata source

`CoreSchemas` registers field paths, value types, typed defaults, descriptions, examples, diagnostic codes, declarative cross-field relationships, editor widgets/help/order, client projection policy, diff behavior, and default-elision policy. `SchemaRegistry.Builder` rejects duplicate IDs, ambiguous kind directories, invalid field references, and unknown diagnostic codes, then freezes into deterministic order.

Two artifacts are generated from that snapshot:

- `docs/reference/SCHEMA-V2.md` — human reference tables and diagnostic help.
- `docs/reference/schema-v2-editor.json` — machine-readable editor/catalog metadata.

The editor artifact records a separately versioned catalog format plus the current schema version derived from `SchemaVersion.CURRENT`; these version axes are intentionally distinct.

These renderers do not yet generate parsers, codecs, executable constraints, a native encyclopedia, command suggestions, TOML/JSON Schema or snippets, projection code, or test parameter catalogs.

Regenerate intentionally:

```text
./gradlew generateSchemaArtifacts
```

Verify without changing checked-in files:

```text
./gradlew verifySchemaArtifacts
```

The verification task generates into `build/` and compares file sets and bytes. It is part of `check`, so stale documentation fails local builds and CI.

## Extension checklist

When a later phase adds a schema or field, the same change must add its stable identity, value shape and bounds, diagnostic code/help, editor metadata, projection/redaction policy, diff/default behavior, example, generated artifacts, and focused unit/property tests. Registry-aware resolution, lifecycle behavior, and gameplay tests belong to the implementing feature phase rather than this foundation.
