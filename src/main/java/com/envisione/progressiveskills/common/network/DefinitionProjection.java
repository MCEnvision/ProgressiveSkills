package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.tree.TreeCanonicalCodec;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.common.tree.TreeDefinition;
import com.envisione.progressiveskills.common.tree.TreeNodeDefinition;
import com.envisione.progressiveskills.common.tree.TreeScope;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Client safe definition identity and presentation. Gameplay fields and provenance stay on the server. */
public record DefinitionProjection(Map<DefinitionKey, Entry> definitions) {
    public DefinitionProjection {
        Objects.requireNonNull(definitions, "definitions");
        if (definitions.size() > NetworkLimits.MAX_DEFINITIONS) {
            throw new IllegalArgumentException("Definition projection exceeds capacity");
        }
        var sorted = new TreeMap<DefinitionKey, Entry>();
        definitions.forEach((key, value) -> sorted.put(
                Objects.requireNonNull(key, "definition key"),
                Objects.requireNonNull(value, "definition projection")
        ));
        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    public static DefinitionProjection from(CanonicalIr ir) {
        Objects.requireNonNull(ir, "ir");
        Optional<SkillCatalog> skills = containsTrees(ir)
                ? Optional.of(SkillCatalog.from(ir)) : Optional.empty();
        var result = new TreeMap<DefinitionKey, Entry>();
        ir.definitions().forEach((key, definition) -> {
            Optional<TreeView> tree = key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.TREE)
                    ? Optional.of(TreeView.from(
                    TreeCanonicalCodec.decode(definition), skills.orElseThrow())) : Optional.empty();
            result.put(key, entry(definition.header().presentation(), tree));
        });
        return new DefinitionProjection(result);
    }

    public static DefinitionProjection from(CanonicalIr ir, TreeCatalog trees) {
        Objects.requireNonNull(ir, "ir");
        Objects.requireNonNull(trees, "trees");
        Optional<SkillCatalog> skills = containsTrees(ir)
                ? Optional.of(SkillCatalog.from(ir)) : Optional.empty();
        var result = new TreeMap<DefinitionKey, Entry>();
        ir.definitions().forEach((key, definition) -> {
            Optional<TreeView> tree = key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.TREE)
                    ? Optional.of(TreeView.from(trees.tree(key.id()).orElseThrow(
                    () -> new IllegalArgumentException("Tree catalog is missing " + key.id())
            ), skills.orElseThrow())) : Optional.empty();
            result.put(key, entry(definition.header().presentation(), tree));
        });
        return new DefinitionProjection(result);
    }

    private static boolean containsTrees(CanonicalIr ir) {
        return ir.definitions().keySet().stream().anyMatch(key ->
                key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.TREE));
    }

    private static Entry entry(
            Optional<com.envisione.progressiveskills.common.ir.DefinitionPresentation> presentation,
            Optional<TreeView> tree
    ) {
        return presentation.map(value -> new Entry(
                Optional.of(Text.from(value.display())),
                value.description().map(Text::from),
                Optional.of(Icon.from(value.icon())),
                List.copyOf(value.searchAliases()),
                tree
        )).orElseGet(() -> new Entry(Optional.empty(), Optional.empty(), Optional.empty(), List.of(), tree));
    }

    public record Entry(
            Optional<Text> display,
            Optional<Text> description,
            Optional<Icon> icon,
            List<String> searchAliases,
            Optional<TreeView> tree
    ) {
        public Entry(
                Optional<Text> display,
                Optional<Text> description,
                Optional<Icon> icon,
                List<String> searchAliases
        ) {
            this(display, description, icon, searchAliases, Optional.empty());
        }

        public Entry {
            display = Objects.requireNonNull(display, "display");
            description = Objects.requireNonNull(description, "description");
            icon = Objects.requireNonNull(icon, "icon");
            tree = Objects.requireNonNull(tree, "tree");
            Objects.requireNonNull(searchAliases, "searchAliases");
            if (searchAliases.size() > NetworkLimits.MAX_ALIASES_PER_DEFINITION) {
                throw new IllegalArgumentException("Projected search aliases exceed capacity");
            }
            var checked = new ArrayList<String>(searchAliases.size());
            for (String alias : searchAliases) {
                checked.add(NetworkLimits.requireBoundedText(
                        alias, NetworkLimits.MAX_TEXT_BYTES, "projected search alias"));
            }
            searchAliases = List.copyOf(checked);
            if (display.isEmpty() != icon.isEmpty()) {
                throw new IllegalArgumentException("Projected display and icon must be present together");
            }
        }

        static Entry withoutPresentation() {
            return new Entry(Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.empty());
        }
    }

    public record TreeView(
            boolean enabled,
            String scope,
            Optional<ResourceLocation> boundSkill,
            ResourceLocation currency,
            long currencyMinimum,
            long currencyInitial,
            List<NodeView> nodes
    ) {
        public TreeView {
            scope = TreeScope.parse(NetworkLimits.requireBoundedText(
                    scope, 32, "tree scope")).serializedName();
            boundSkill = Objects.requireNonNull(boundSkill, "boundSkill").map(StableId::requireValid);
            currency = StableId.requireValid(currency);
            if (scope.equals(TreeScope.SKILL.serializedName()) != boundSkill.isPresent()) {
                throw new IllegalArgumentException("Projected tree scope and skill bind do not match");
            }
            if (currencyInitial < currencyMinimum) {
                throw new IllegalArgumentException("Projected tree currency initial value is below its minimum");
            }
            nodes = Objects.requireNonNull(nodes, "nodes").stream().sorted().toList();
            if (nodes.isEmpty() || nodes.size() > NetworkLimits.MAX_TREE_NODES_PER_VIEW) {
                throw new IllegalArgumentException("Projected tree node count is invalid");
            }
            var nodeIds = new HashSet<ResourceLocation>();
            var positions = new HashSet<String>();
            for (NodeView node : nodes) {
                if (!nodeIds.add(node.id()) || !positions.add(node.row() + "," + node.column())) {
                    throw new IllegalArgumentException("Projected tree contains duplicate nodes or positions");
                }
            }
            for (NodeView node : nodes) {
                if (!nodeIds.containsAll(node.requires()) || !nodeIds.containsAll(node.requiresAny())) {
                    throw new IllegalArgumentException("Projected tree prerequisite is unavailable");
                }
            }
        }

        static TreeView from(TreeDefinition tree, SkillCatalog skills) {
            var currency = skills.currency(tree.currency()).orElseThrow(
                    () -> new IllegalArgumentException("Tree currency is unavailable")
            );
            return new TreeView(
                    tree.enabled(),
                    tree.scope().serializedName(),
                    tree.boundSkill(),
                    tree.currency(),
                    currency.minimum(),
                    currency.initial(),
                    tree.nodes().stream().map(NodeView::from).toList()
            );
        }

        public long visibleCurrencyBalance(Map<String, Long> balances) {
            Objects.requireNonNull(balances, "balances");
            return balances.getOrDefault(currency.toString(), currencyInitial);
        }
    }

    public record NodeView(
            ResourceLocation id,
            Text display,
            Optional<Text> description,
            Icon icon,
            List<String> searchAliases,
            long cost,
            int row,
            int column,
            List<ResourceLocation> requires,
            List<ResourceLocation> requiresAny,
            Map<ResourceLocation, Integer> minimumSkillLevels
    ) implements Comparable<NodeView> {
        public NodeView {
            id = StableId.requireValid(id);
            Objects.requireNonNull(display, "display");
            description = Objects.requireNonNull(description, "description");
            Objects.requireNonNull(icon, "icon");
            searchAliases = boundedAliases(searchAliases);
            if (cost <= 0 || Math.abs((long) row) > 4_096 || Math.abs((long) column) > 4_096) {
                throw new IllegalArgumentException("Projected tree node cost or position is invalid");
            }
            requires = boundedIds(requires, "tree prerequisite");
            requiresAny = boundedIds(requiresAny, "tree alternative prerequisite");
            if (requires.size() + requiresAny.size() > NetworkLimits.MAX_TREE_PREREQUISITES) {
                throw new IllegalArgumentException("Projected tree prerequisite count exceeds capacity");
            }
            minimumSkillLevels = boundedLevels(minimumSkillLevels);
        }

        static NodeView from(TreeNodeDefinition node) {
            return new NodeView(
                    node.id(),
                    Text.from(node.presentation().display()),
                    node.presentation().description().map(Text::from),
                    Icon.from(node.presentation().icon()),
                    List.copyOf(node.presentation().searchAliases()),
                    node.cost(),
                    node.row(),
                    node.column(),
                    node.requires(),
                    node.requiresAny(),
                    node.minimumSkillLevels()
            );
        }

        @Override
        public int compareTo(NodeView other) {
            return id.compareNamespaced(other.id);
        }

        private static List<String> boundedAliases(List<String> values) {
            Objects.requireNonNull(values, "searchAliases");
            if (values.size() > NetworkLimits.MAX_ALIASES_PER_DEFINITION) {
                throw new IllegalArgumentException("Projected node aliases exceed capacity");
            }
            return values.stream().map(value -> NetworkLimits.requireBoundedText(
                    value, NetworkLimits.MAX_TEXT_BYTES, "projected node alias"
            )).toList();
        }

        private static List<ResourceLocation> boundedIds(List<ResourceLocation> values, String name) {
            Objects.requireNonNull(values, name);
            var sorted = values.stream().map(StableId::requireValid)
                    .sorted(ResourceLocation::compareNamespaced).toList();
            if (new java.util.HashSet<>(sorted).size() != sorted.size()) {
                throw new IllegalArgumentException("Projected tree prerequisite contains duplicates");
            }
            return sorted;
        }

        private static Map<ResourceLocation, Integer> boundedLevels(Map<ResourceLocation, Integer> values) {
            Objects.requireNonNull(values, "minimumSkillLevels");
            if (values.size() > NetworkLimits.MAX_TREE_MINIMUM_SKILLS) {
                throw new IllegalArgumentException("Projected tree minimum skill count exceeds capacity");
            }
            var sorted = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
            values.forEach((skill, level) -> {
                if (level == null || level < 0) {
                    throw new IllegalArgumentException("Projected tree minimum skill is invalid");
                }
                sorted.put(StableId.requireValid(skill), level);
            });
            return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        }
    }

    public record Text(Optional<String> localizationKey, String fallback) {
        public Text {
            localizationKey = Objects.requireNonNull(localizationKey, "localizationKey")
                    .map(value -> NetworkLimits.requireBoundedText(
                            value, NetworkLimits.MAX_KEY_BYTES, "localization key"));
            fallback = NetworkLimits.requireBoundedText(
                    fallback, NetworkLimits.MAX_TEXT_BYTES, "presentation fallback");
        }

        static Text from(ComponentSpec component) {
            return new Text(component.localizationKey(), component.fallback());
        }
    }

    public record Icon(
            String kind,
            List<ResourceLocation> references,
            ResourceLocation fallback,
            Text altText,
            Text narration
    ) {
        public Icon {
            kind = NetworkLimits.requireBoundedText(kind, 64, "icon kind");
            references = List.copyOf(Objects.requireNonNull(references, "references"));
            if (references.isEmpty() || references.size() > 16 || references.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Projected icon reference count is invalid");
            }
            Objects.requireNonNull(fallback, "fallback");
            Objects.requireNonNull(altText, "altText");
            Objects.requireNonNull(narration, "narration");
        }

        static Icon from(IconSpec icon) {
            return new Icon(
                    icon.kind().serializedName(),
                    icon.references(),
                    icon.fallback(),
                    Text.from(icon.altText()),
                    Text.from(icon.effectiveNarration())
            );
        }
    }
}
