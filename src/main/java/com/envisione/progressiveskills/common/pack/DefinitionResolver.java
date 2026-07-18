package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.source.SourceReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;

/** Applies explicit layer intent before compiling one immutable whole-snapshot IR. */
public final class DefinitionResolver {
    private final TypedDefinitionCompiler compiler = new TypedDefinitionCompiler();

    public DefinitionResolution resolve(
            Collection<ParsedDefinitionLayer> layers,
            Collection<ParsedAlias> parsedAliases
    ) {
        List<PackLayer> packOrder = layers.stream().map(ParsedDefinitionLayer::pack).distinct().sorted().toList();
        return resolve(layers, parsedAliases, packOrder);
    }

    public DefinitionResolution resolve(
            Collection<ParsedDefinitionLayer> layers,
            Collection<ParsedAlias> parsedAliases,
            List<PackLayer> packOrder
    ) {
        Objects.requireNonNull(layers, "layers");
        Objects.requireNonNull(parsedAliases, "parsedAliases");
        Objects.requireNonNull(packOrder, "packOrder");
        var states = new TreeMap<DefinitionKey, RawState>();
        var disabled = new TreeSet<DefinitionKey>();
        var problems = new ArrayList<PackProblem>();

        var rank = new HashMap<PackLayer, Integer>();
        for (int index = 0; index < packOrder.size(); index++) {
            if (rank.put(packOrder.get(index), index) != null) {
                throw new IllegalArgumentException("Pack order contains a duplicate layer: " + packOrder.get(index));
            }
        }
        var orderedLayers = layers.stream().sorted((left, right) -> {
            Integer leftRank = rank.get(left.pack());
            Integer rightRank = rank.get(right.pack());
            if (leftRank == null || rightRank == null) {
                throw new IllegalArgumentException("Definition layer references a pack absent from resolved order");
            }
            int comparison = leftRank.compareTo(rightRank);
            if (comparison == 0) {
                comparison = left.key().compareTo(right.key());
            }
            return comparison != 0 ? comparison : left.provenance().compareTo(right.provenance());
        }).toList();
        for (ParsedDefinitionLayer layer : orderedLayers) {
            try {
                applyLayer(states, disabled, layer);
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.MERGE_CONFLICT,
                        exception.getMessage(),
                        layer.provenance(),
                        layer.key()
                ));
            }
        }

        var definitions = new ArrayList<CanonicalDefinition>();
        for (var entry : states.entrySet()) {
            RawState state = entry.getValue();
            try {
                definitions.add(compiler.compile(
                        entry.getKey(),
                        immutableObject(materializedFields(entry.getKey(), state, states)),
                        state.provenance,
                        sourceMap(state.sources)
                ));
            } catch (TypedDefinitionCompiler.UnsupportedDefinitionSchemaException exception) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.UNSUPPORTED_DEFINITION_SCHEMA,
                        exception.getMessage(),
                        state.provenance,
                        entry.getKey()
                ));
            } catch (IllegalArgumentException | ArithmeticException exception) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.INVALID_TOML,
                        "Definition " + entry.getKey() + " is invalid: " + exception.getMessage(),
                        state.provenance,
                        entry.getKey()
                ));
            }
        }

        AliasMap aliases = compileAliases(parsedAliases, problems);
        Set<DefinitionKey> compiledKeys = definitions.stream()
                .map(definition -> definition.header().key())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (ParsedAlias parsedAlias : parsedAliases) {
            var alias = parsedAlias.alias();
            if (!compiledKeys.contains(alias.target())) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.INVALID_ALIAS,
                        "Replacement target does not exist in the staged registry: " + alias.target(),
                        parsedAlias.provenance(),
                        alias.target()
                ));
            }
            if (compiledKeys.contains(alias.source())) {
                problems.add(PackProblem.error(
                        CoreDiagnostics.INVALID_ALIAS,
                        "Replacement source is still a live definition: " + alias.source(),
                        parsedAlias.provenance(),
                        alias.source()
                ));
            }
        }
        CanonicalIr ir;
        try {
            ir = CanonicalIr.of(definitions, aliases);
        } catch (IllegalArgumentException exception) {
            if (!layers.isEmpty()) {
                ParsedDefinitionLayer first = layers.iterator().next();
                problems.add(PackProblem.error(
                        CoreDiagnostics.MERGE_CONFLICT,
                        exception.getMessage(),
                        first.provenance(),
                        first.key()
                ));
            }
            ir = CanonicalIr.of(List.of(), AliasMap.empty());
        }
        return new DefinitionResolution(ir, disabled, problems);
    }

    private void applyLayer(
            Map<DefinitionKey, RawState> states,
            Set<DefinitionKey> disabled,
            ParsedDefinitionLayer layer
    ) {
        RawState current = states.get(layer.key());
        switch (layer.mergeIntent()) {
            case ADD -> {
                if (current != null) {
                    throw new IllegalArgumentException("add requires a new definition but " + layer.key() + " already exists");
                }
                states.put(layer.key(), RawState.from(layer));
                disabled.remove(layer.key());
            }
            case REPLACE -> {
                if (current == null) {
                    throw new IllegalArgumentException("replace requires an existing definition: " + layer.key());
                }
                verifyExpectedDigest(layer, current, states);
                states.put(layer.key(), RawState.from(layer));
                disabled.remove(layer.key());
            }
            case MERGE -> {
                if (current == null) {
                    throw new IllegalArgumentException("merge requires an existing definition: " + layer.key());
                }
                deepMerge(current.fields, layer.fields(), "", current.sources, layer.sourceMap());
                current.provenance = layer.provenance();
                disabled.remove(layer.key());
            }
            case PATCH -> {
                if (current == null) {
                    throw new IllegalArgumentException("patch requires an existing definition: " + layer.key());
                }
                for (int index = 0; index < layer.patches().size(); index++) {
                    DefinitionPatch patch = layer.patches().get(index);
                    applyPatch(current.fields, patch);
                    current.sources.put(patch.path(), layer.patchSources().get(index));
                }
                current.provenance = layer.provenance();
                disabled.remove(layer.key());
            }
            case DISABLE -> {
                if (current == null) {
                    throw new IllegalArgumentException("disable requires an existing definition: " + layer.key());
                }
                disabled.add(layer.key());
            }
        }
    }

    private void verifyExpectedDigest(
            ParsedDefinitionLayer layer,
            RawState current,
            Map<DefinitionKey, RawState> states
    ) {
        if (layer.expectedOldDigest().isEmpty()) {
            return;
        }
        CanonicalDefinition compiled = compiler.compile(
                layer.key(),
                immutableObject(materializedFields(layer.key(), current, states)),
                current.provenance,
                sourceMap(current.sources)
        );
        String actual = CanonicalSemanticDigest.definition(compiled);
        if (!actual.equals(layer.expectedOldDigest().orElseThrow())) {
            throw new IllegalArgumentException("replace expected old digest "
                    + layer.expectedOldDigest().orElseThrow() + " but found " + actual + " for " + layer.key());
        }
    }

    private static Map<String, Object> materializedFields(
            DefinitionKey key,
            RawState state,
            Map<DefinitionKey, RawState> states
    ) {
        if (key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.TEMPLATE)) {
            return state.fields;
        }
        List<net.minecraft.resources.ResourceLocation> templates = new ArrayList<>();
        templates.addAll(templateIds(state.fields.get("templates"), "templates"));
        templates.addAll(templateIds(state.fields.get("mixins"), "mixins"));
        if (templates.isEmpty()) {
            return state.fields;
        }
        if (templates.size() > 16 || new java.util.HashSet<>(templates).size() != templates.size()) {
            throw new IllegalArgumentException(key + " template list is invalid");
        }
        var result = new LinkedHashMap<String, Object>();
        for (net.minecraft.resources.ResourceLocation template : templates) {
            mergeTemplateFields(result, resolveTemplate(
                    key.kind(), template, states, new java.util.HashSet<>(), 0));
        }
        var local = new LinkedHashMap<String, Object>(state.fields);
        local.remove("templates");
        local.remove("mixins");
        mergeTemplateFields(result, local);
        requireStructureBudget(result, 0, new int[]{0});
        return result;
    }

    private static Map<String, Object> resolveTemplate(
            com.envisione.progressiveskills.common.id.DefinitionKind targetKind,
            net.minecraft.resources.ResourceLocation templateId,
            Map<DefinitionKey, RawState> states,
            Set<net.minecraft.resources.ResourceLocation> visiting,
            int depth
    ) {
        if (depth > 32 || !visiting.add(templateId)) {
            throw new IllegalArgumentException("Template inheritance is cyclic or exceeds its depth bound at "
                    + templateId);
        }
        RawState template = states.get(new DefinitionKey(
                com.envisione.progressiveskills.common.id.DefinitionKinds.TEMPLATE, templateId));
        if (template == null) {
            throw new IllegalArgumentException("Template is unavailable " + templateId);
        }
        Object declared = template.fields.get("target_kind");
        if (!(declared instanceof String text)) {
            throw new IllegalArgumentException("Template target kind is unavailable " + templateId);
        }
        net.minecraft.resources.ResourceLocation targetId = text.indexOf(':') >= 0
                ? com.envisione.progressiveskills.common.id.StableId.parse(text)
                : net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("progressiveskills", text);
        if (!targetKind.id().equals(targetId)) {
            throw new IllegalArgumentException("Template " + templateId + " targets " + targetId
                    + " and cannot apply to " + targetKind.id());
        }
        var result = new LinkedHashMap<String, Object>();
        for (net.minecraft.resources.ResourceLocation parent : templateIds(
                template.fields.get("extends"), "extends")) {
            mergeTemplateFields(result, resolveTemplate(targetKind, parent, states, visiting, depth + 1));
        }
        Object fields = template.fields.get("fields");
        if (!(fields instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Template fields are unavailable " + templateId);
        }
        var checked = new LinkedHashMap<String, Object>();
        raw.forEach((field, value) -> {
            if (!(field instanceof String name)) {
                throw new IllegalArgumentException("Template field name is invalid " + templateId);
            }
            checked.put(name, mutableCopy(value));
        });
        mergeTemplateFields(result, checked);
        visiting.remove(templateId);
        return result;
    }

    private static List<net.minecraft.resources.ResourceLocation> templateIds(Object value, String field) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.size() > 16) {
            throw new IllegalArgumentException("Template " + field + " must be a bounded list");
        }
        var result = new ArrayList<net.minecraft.resources.ResourceLocation>();
        for (Object entry : list) {
            if (!(entry instanceof String text)) {
                throw new IllegalArgumentException("Template " + field + " contains a nontext identity");
            }
            result.add(com.envisione.progressiveskills.common.id.StableId.parse(text));
        }
        return List.copyOf(result);
    }

    private static void mergeTemplateFields(Map<String, Object> target, Map<String, Object> overlay) {
        overlay.forEach((field, value) -> {
            Object previous = target.get(field);
            if (previous instanceof Map<?, ?> previousRaw && value instanceof Map<?, ?> incomingRaw) {
                @SuppressWarnings("unchecked") Map<String, Object> previousMap =
                        (Map<String, Object>) previousRaw;
                var incoming = new LinkedHashMap<String, Object>();
                incomingRaw.forEach((key, child) -> incoming.put(Objects.requireNonNull(key).toString(), child));
                mergeTemplateFields(previousMap, incoming);
            } else {
                target.put(field, mutableCopy(value));
            }
        });
    }

    private static void requireStructureBudget(Object value, int depth, int[] nodes) {
        if (depth > TomlDocument.MAX_DEPTH || ++nodes[0] > TomlDocument.MAX_NODES) {
            throw new IllegalArgumentException("Expanded template exceeds its structure budget");
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(child -> requireStructureBudget(child, depth + 1, nodes));
        } else if (value instanceof List<?> list) {
            list.forEach(child -> requireStructureBudget(child, depth + 1, nodes));
        }
    }

    private static AliasMap compileAliases(Collection<ParsedAlias> parsed, List<PackProblem> problems) {
        var aliases = parsed.stream().sorted().toList();
        var result = AliasMap.validate(aliases.stream().map(ParsedAlias::alias).toList());
        if (result.isValid()) {
            return result.orThrow();
        }
        Provenance fallback = aliases.isEmpty() ? null : aliases.getFirst().provenance();
        for (var issue : result.issues()) {
            if (fallback != null) {
                problems.add(PackProblem.error(CoreDiagnostics.INVALID_ALIAS, issue.message(), fallback));
            }
        }
        return AliasMap.empty();
    }

    private static void deepMerge(
            Map<String, Object> target,
            Map<String, Object> overlay,
            String prefix,
            Map<String, SourceReference> targetSources,
            SourceMap overlaySources
    ) {
        for (var entry : overlay.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object previous = target.get(entry.getKey());
            Object incoming = mutableCopy(entry.getValue());
            if (previous instanceof Map<?, ?> previousMap && incoming instanceof Map<?, ?> incomingMap) {
                @SuppressWarnings("unchecked") Map<String, Object> checkedPrevious = (Map<String, Object>) previousMap;
                @SuppressWarnings("unchecked") Map<String, Object> checkedIncoming = (Map<String, Object>) incomingMap;
                deepMerge(checkedPrevious, checkedIncoming, path, targetSources, overlaySources);
            } else if (previous instanceof List<?> && incoming instanceof List<?> && !previous.equals(incoming)) {
                throw new IllegalArgumentException("merge cannot implicitly combine list field " + path
                        + "; use an explicit patch operation");
            } else {
                target.put(entry.getKey(), incoming);
                closestSource(overlaySources, path).ifPresent(reference -> targetSources.put(path, reference));
            }
        }
    }

    private static Optional<SourceReference> closestSource(SourceMap sourceMap, String path) {
        String candidate = path;
        while (true) {
            Optional<SourceReference> found = sourceMap.find(candidate);
            if (found.isPresent()) {
                return found;
            }
            int separator = candidate.lastIndexOf('.');
            if (separator < 0) {
                return Optional.empty();
            }
            candidate = candidate.substring(0, separator);
        }
    }

    private static void applyPatch(Map<String, Object> root, DefinitionPatch patch) {
        String[] segments = patch.path().split("\\.");
        Map<String, Object> parent = root;
        for (int index = 0; index < segments.length - 1; index++) {
            Object child = parent.get(segments[index]);
            if (!(child instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Patch parent does not exist as an object: " + patch.path());
            }
            @SuppressWarnings("unchecked") Map<String, Object> checked = (Map<String, Object>) raw;
            parent = checked;
        }
        String field = segments[segments.length - 1];
        switch (patch.operation()) {
            case SET -> parent.put(field, mutableCopy(patch.value().orElseThrow()));
            case REMOVE -> {
                if (parent.remove(field) == null) {
                    throw new IllegalArgumentException("Patch remove target does not exist: " + patch.path());
                }
            }
            case APPEND, PREPEND -> {
                Object current = parent.get(field);
                if (!(current instanceof List<?> list)) {
                    throw new IllegalArgumentException("Patch list target does not exist: " + patch.path());
                }
                var mutable = new ArrayList<Object>();
                Object incoming = mutableCopy(patch.value().orElseThrow());
                List<?> additions = incoming instanceof List<?> additionsList ? additionsList : List.of(incoming);
                if (patch.operation() == PatchOperation.PREPEND) {
                    mutable.addAll(additions);
                    mutable.addAll(list);
                } else {
                    mutable.addAll(list);
                    mutable.addAll(additions);
                }
                parent.put(field, mutable);
            }
            case REPLACE_BY_ID -> {
                Object current = parent.get(field);
                if (!(current instanceof List<?> list)) {
                    throw new IllegalArgumentException("replace_by_id target must be a list: " + patch.path());
                }
                var mutable = new ArrayList<Object>(list);
                String targetId = patch.targetId().orElseThrow();
                int match = -1;
                for (int index = 0; index < mutable.size(); index++) {
                    Object candidate = mutable.get(index);
                    if (candidate instanceof Map<?, ?> object && targetId.equals(object.get("id"))) {
                        if (match >= 0) {
                            throw new IllegalArgumentException("replace_by_id target is ambiguous: " + targetId);
                        }
                        match = index;
                    }
                }
                if (match < 0) {
                    throw new IllegalArgumentException("replace_by_id target does not exist: " + targetId);
                }
                mutable.set(match, mutableCopy(patch.value().orElseThrow()));
                parent.put(field, mutable);
            }
        }
    }

    private static SourceMap sourceMap(Map<String, SourceReference> sources) {
        var builder = SourceMap.builder();
        sources.forEach(builder::put);
        return builder.build();
    }

    private static Map<String, Object> immutableObject(Map<String, Object> source) {
        var sorted = new TreeMap<String, Object>();
        source.forEach((key, value) -> sorted.put(key, immutableCopy(value)));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Object immutableCopy(Object value) {
        if (value instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) raw;
            return immutableObject(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(DefinitionResolver::immutableCopy).toList();
        }
        return value;
    }

    private static Object mutableCopy(Object value) {
        if (value instanceof Map<?, ?> raw) {
            var copy = new LinkedHashMap<String, Object>();
            raw.forEach((key, child) -> copy.put(Objects.requireNonNull(key).toString(), mutableCopy(child)));
            return copy;
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list.stream().map(DefinitionResolver::mutableCopy).toList());
        }
        return value;
    }

    private static final class RawState {
        private final Map<String, Object> fields;
        private final Map<String, SourceReference> sources;
        private Provenance provenance;

        private RawState(Map<String, Object> fields, Map<String, SourceReference> sources, Provenance provenance) {
            this.fields = fields;
            this.sources = sources;
            this.provenance = provenance;
        }

        private static RawState from(ParsedDefinitionLayer layer) {
            @SuppressWarnings("unchecked") Map<String, Object> fields =
                    (Map<String, Object>) mutableCopy(layer.fields());
            return new RawState(fields, new TreeMap<>(layer.sourceMap().fields()), layer.provenance());
        }
    }
}
