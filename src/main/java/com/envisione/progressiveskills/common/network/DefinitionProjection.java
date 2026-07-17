package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.ability.AbilityAction;
import com.envisione.progressiveskills.common.ability.AbilityAttributeEffect;
import com.envisione.progressiveskills.common.ability.AbilityCatalog;
import com.envisione.progressiveskills.common.ability.AbilityCost;
import com.envisione.progressiveskills.common.ability.AbilityCurrencyCost;
import com.envisione.progressiveskills.common.ability.AbilityDefinition;
import com.envisione.progressiveskills.common.ability.AbilityFlagEffect;
import com.envisione.progressiveskills.common.ability.AbilityHealAction;
import com.envisione.progressiveskills.common.ability.AbilityMessageAction;
import com.envisione.progressiveskills.common.ability.AbilityPersistentEffect;
import com.envisione.progressiveskills.common.ability.AbilityVanillaEffectAction;
import com.envisione.progressiveskills.common.classdef.ClassAttributeGrant;
import com.envisione.progressiveskills.common.classdef.ClassCatalog;
import com.envisione.progressiveskills.common.classdef.ClassCurrencyCost;
import com.envisione.progressiveskills.common.classdef.ClassDefinition;
import com.envisione.progressiveskills.common.classdef.ClassGrant;
import com.envisione.progressiveskills.common.classdef.ClassSlotDefinition;
import com.envisione.progressiveskills.common.classdef.ClassSpellGrant;
import com.envisione.progressiveskills.common.classdef.ClassSynergyDefinition;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.common.tree.TreeDefinition;
import com.envisione.progressiveskills.common.tree.TreeNodeDefinition;
import com.envisione.progressiveskills.common.tree.TreeScope;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/** Client safe definition identity and presentation. Gameplay fields and provenance stay on the server. */
public record DefinitionProjection(
        Map<DefinitionKey, Entry> definitions,
        Map<ResourceLocation, SynergyView> classSynergies
) {
    public DefinitionProjection(Map<DefinitionKey, Entry> definitions) {
        this(definitions, Map.of());
    }

    public DefinitionProjection {
        Objects.requireNonNull(definitions, "definitions");
        if (definitions.size() > NetworkLimits.MAX_DEFINITIONS) {
            throw new IllegalArgumentException("Definition projection exceeds capacity");
        }
        var sorted = new TreeMap<DefinitionKey, Entry>();
        definitions.forEach((key, value) -> {
            DefinitionKey checkedKey = Objects.requireNonNull(key, "definition key");
            Entry checkedValue = Objects.requireNonNull(value, "definition projection");
            validateViewKind(checkedKey, checkedValue);
            sorted.put(checkedKey, checkedValue);
        });
        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        Objects.requireNonNull(classSynergies, "classSynergies");
        if (classSynergies.size() > NetworkLimits.MAX_CLASS_SYNERGY_VIEWS) {
            throw new IllegalArgumentException("Projected class synergy count exceeds capacity");
        }
        var sortedSynergies = new TreeMap<ResourceLocation, SynergyView>(
                ResourceLocation::compareNamespaced);
        classSynergies.forEach((id, value) -> sortedSynergies.put(
                StableId.requireValid(id), Objects.requireNonNull(value, "class synergy projection")));
        classSynergies = Collections.unmodifiableMap(new LinkedHashMap<>(sortedSynergies));
    }

    private static void validateViewKind(DefinitionKey key, Entry entry) {
        int viewCount = (entry.tree().isPresent() ? 1 : 0)
                + (entry.classSlot().isPresent() ? 1 : 0)
                + (entry.classDefinition().isPresent() ? 1 : 0)
                + (entry.ability().isPresent() ? 1 : 0);
        if (viewCount > 1
                || (entry.tree().isPresent()
                && !key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.TREE))
                || (entry.classSlot().isPresent()
                && !key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.CLASS_SLOT))
                || (entry.classDefinition().isPresent()
                && !key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.CLASS))
                || (entry.ability().isPresent()
                && !key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.ABILITY))) {
            throw new IllegalArgumentException("Projected gameplay view does not match its definition kind");
        }
    }

    public static DefinitionProjection from(CanonicalIr ir) {
        Objects.requireNonNull(ir, "ir");
        boolean typedGameplay = ir.definitions().keySet().stream().anyMatch(key ->
                key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.TREE)
                        || key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.CLASS)
                        || key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.CLASS_SLOT)
                        || key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.ABILITY));
        if (!typedGameplay) {
            var result = new TreeMap<DefinitionKey, Entry>();
            ir.definitions().forEach((key, definition) -> result.put(key, entry(
                    definition.header().presentation(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty())));
            return new DefinitionProjection(result);
        }
        SkillCatalog skills = SkillCatalog.from(ir);
        TreeCatalog trees = TreeCatalog.from(ir, skills);
        ClassCatalog classes = ClassCatalog.from(ir, skills, trees);
        return from(ir, trees, classes);
    }

    public static DefinitionProjection from(CanonicalIr ir, TreeCatalog trees) {
        Objects.requireNonNull(ir, "ir");
        Objects.requireNonNull(trees, "trees");
        SkillCatalog skills = SkillCatalog.from(ir);
        return from(ir, trees, ClassCatalog.from(ir, skills, trees));
    }

    public static DefinitionProjection from(
            CanonicalIr ir,
            TreeCatalog trees,
            ClassCatalog classes
    ) {
        SkillCatalog skills = SkillCatalog.from(ir);
        return from(ir, trees, classes, AbilityCatalog.from(ir, skills, classes));
    }

    public static DefinitionProjection from(
            CanonicalIr ir,
            TreeCatalog trees,
            ClassCatalog classes,
            AbilityCatalog abilities
    ) {
        Objects.requireNonNull(ir, "ir");
        Objects.requireNonNull(trees, "trees");
        Objects.requireNonNull(classes, "classes");
        Objects.requireNonNull(abilities, "abilities");
        SkillCatalog skills = SkillCatalog.from(ir);
        var result = new TreeMap<DefinitionKey, Entry>();
        ir.definitions().forEach((key, definition) -> {
            Optional<TreeView> tree = key.kind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.TREE)
                    ? Optional.of(TreeView.from(trees.tree(key.id()).orElseThrow(
                    () -> new IllegalArgumentException("Tree catalog is missing " + key.id())
            ), skills)) : Optional.empty();
            Optional<ClassSlotView> classSlot = key.kind().equals(
                    com.envisione.progressiveskills.common.id.DefinitionKinds.CLASS_SLOT)
                    ? Optional.of(ClassSlotView.from(classes.slot(key.id()).orElseThrow(
                    () -> new IllegalArgumentException("Class slot catalog is missing " + key.id())
            ))) : Optional.empty();
            Optional<ClassView> classDefinition = key.kind().equals(
                    com.envisione.progressiveskills.common.id.DefinitionKinds.CLASS)
                    ? Optional.of(ClassView.from(classes.classDefinition(key.id()).orElseThrow(
                    () -> new IllegalArgumentException("Class catalog is missing " + key.id())
            ))) : Optional.empty();
            Optional<AbilityView> ability = key.kind().equals(
                    com.envisione.progressiveskills.common.id.DefinitionKinds.ABILITY)
                    ? Optional.of(AbilityView.from(abilities.ability(key.id()).orElseThrow(
                    () -> new IllegalArgumentException("Ability catalog is missing " + key.id())
            ))) : Optional.empty();
            result.put(key, entry(
                    definition.header().presentation(), tree, classSlot, classDefinition, ability));
        });
        var synergies = new TreeMap<ResourceLocation, SynergyView>(ResourceLocation::compareNamespaced);
        classes.synergies().forEach((id, synergy) -> synergies.put(id, SynergyView.from(synergy)));
        return new DefinitionProjection(result, synergies);
    }

    private static Entry entry(
            Optional<com.envisione.progressiveskills.common.ir.DefinitionPresentation> presentation,
            Optional<TreeView> tree,
            Optional<ClassSlotView> classSlot,
            Optional<ClassView> classDefinition,
            Optional<AbilityView> ability
    ) {
        return presentation.map(value -> new Entry(
                Optional.of(Text.from(value.display())),
                value.description().map(Text::from),
                Optional.of(Icon.from(value.icon())),
                List.copyOf(value.searchAliases()),
                tree,
                classSlot,
                classDefinition,
                ability
        )).orElseGet(() -> new Entry(
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(),
                tree, classSlot, classDefinition, ability));
    }

    public record Entry(
            Optional<Text> display,
            Optional<Text> description,
            Optional<Icon> icon,
            List<String> searchAliases,
            Optional<TreeView> tree,
            Optional<ClassSlotView> classSlot,
            Optional<ClassView> classDefinition,
            Optional<AbilityView> ability
    ) {
        public Entry(
                Optional<Text> display,
                Optional<Text> description,
                Optional<Icon> icon,
                List<String> searchAliases
        ) {
            this(display, description, icon, searchAliases,
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        public Entry(
                Optional<Text> display,
                Optional<Text> description,
                Optional<Icon> icon,
                List<String> searchAliases,
                Optional<TreeView> tree
        ) {
            this(display, description, icon, searchAliases,
                    tree, Optional.empty(), Optional.empty(), Optional.empty());
        }

        public Entry(
                Optional<Text> display,
                Optional<Text> description,
                Optional<Icon> icon,
                List<String> searchAliases,
                Optional<TreeView> tree,
                Optional<ClassSlotView> classSlot,
                Optional<ClassView> classDefinition
        ) {
            this(display, description, icon, searchAliases,
                    tree, classSlot, classDefinition, Optional.empty());
        }

        public Entry {
            display = Objects.requireNonNull(display, "display");
            description = Objects.requireNonNull(description, "description");
            icon = Objects.requireNonNull(icon, "icon");
            tree = Objects.requireNonNull(tree, "tree");
            classSlot = Objects.requireNonNull(classSlot, "classSlot");
            classDefinition = Objects.requireNonNull(classDefinition, "classDefinition");
            ability = Objects.requireNonNull(ability, "ability");
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
            return new Entry(Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        }
    }

    public record AbilityView(
            boolean enabled,
            String kind,
            boolean slotAllowed,
            boolean defaultOn,
            List<AbilityEffectView> persistentEffects,
            List<AbilityCostView> costs,
            AbilityTargetView targeting,
            ResourceLocation cooldownGroup,
            int cooldownTicks,
            int maximumCharges,
            int rechargeTicks,
            List<AbilityActionView> actions
    ) {
        public AbilityView {
            kind = NetworkLimits.requireBoundedText(kind, 32, "projected ability kind");
            if (!List.of("passive", "toggle", "active").contains(kind)) {
                throw new IllegalArgumentException("Projected ability kind is invalid");
            }
            persistentEffects = boundedAbilityValues(
                    persistentEffects, NetworkLimits.MAX_ABILITY_ACTION_SUMMARIES,
                    "persistent ability effect", AbilityEffectView::id, true);
            costs = boundedAbilityValues(
                    costs, NetworkLimits.MAX_ABILITY_COST_SUMMARIES, "ability cost",
                    AbilityCostView::id, true);
            targeting = Objects.requireNonNull(targeting, "targeting");
            cooldownGroup = StableId.requireValid(cooldownGroup);
            if (cooldownTicks < 0 || cooldownTicks > AbilityDefinition.MAX_COOLDOWN_TICKS
                    || rechargeTicks < 0 || rechargeTicks > AbilityDefinition.MAX_RECHARGE_TICKS
                    || maximumCharges < 1 || maximumCharges > AbilityDefinition.MAX_CHARGES
                    || maximumCharges > 1 && rechargeTicks == 0) {
                throw new IllegalArgumentException("Projected ability timing is invalid");
            }
            actions = boundedAbilityValues(
                    actions, NetworkLimits.MAX_ABILITY_ACTION_SUMMARIES, "ability action",
                    AbilityActionView::id, false);
            boolean active = kind.equals("active");
            if (active && (!slotAllowed || defaultOn || !persistentEffects.isEmpty() || actions.isEmpty())
                    || !active && (!costs.isEmpty() || !actions.isEmpty()
                    || !targeting.mode().equals("self") || cooldownTicks != 0
                    || maximumCharges != 1 || rechargeTicks != 0)
                    || kind.equals("passive") && (slotAllowed || defaultOn)
                    || !active && persistentEffects.isEmpty()) {
                throw new IllegalArgumentException("Projected ability lifecycle is invalid");
            }
        }

        static AbilityView from(AbilityDefinition definition) {
            return new AbilityView(
                    definition.enabled(),
                    definition.kind().serializedName(),
                    definition.slotAllowed(),
                    definition.defaultOn(),
                    definition.persistentEffects().stream().map(AbilityEffectView::from).toList(),
                    definition.costs().stream().map(AbilityCostView::from).toList(),
                    AbilityTargetView.from(definition.targeting()),
                    definition.cooldownGroup(),
                    definition.cooldownTicks(),
                    definition.maxCharges(),
                    definition.rechargeTicks(),
                    definition.actions().stream().map(AbilityActionView::from).toList()
            );
        }
    }

    public record AbilityEffectView(
            ResourceLocation id,
            String type,
            ResourceLocation target,
            String operation,
            String resolver,
            long value
    ) implements Comparable<AbilityEffectView> {
        public AbilityEffectView {
            id = StableId.requireValid(id);
            type = NetworkLimits.requireBoundedText(type, 32, "projected ability effect type");
            target = StableId.requireValid(target);
            operation = NetworkLimits.requireBoundedText(
                    operation, 32, "projected ability effect operation");
            resolver = NetworkLimits.requireBoundedText(
                    resolver, 32, "projected ability effect resolver");
            if (type.isBlank() || operation.isBlank() || resolver.isBlank()) {
                throw new IllegalArgumentException("Projected ability effect summary is blank");
            }
            if (type.equals("attribute")
                    && (!List.of("add_value", "add_multiplied_base", "add_multiplied_total")
                    .contains(operation) || !resolver.equals("additive") || value == 0)
                    || type.equals("flag")
                    && (!operation.equals("owned") || !resolver.equals("highest")
                    || value < 0 || value > 1)
                    || !List.of("attribute", "flag").contains(type)) {
                throw new IllegalArgumentException("Projected ability effect summary is invalid");
            }
        }

        static AbilityEffectView from(AbilityPersistentEffect effect) {
            String operation = effect instanceof AbilityAttributeEffect attribute
                    ? attribute.operation().serializedName() : "owned";
            return new AbilityEffectView(
                    effect.id(), effect.type().serializedName(), effect.targetId(), operation,
                    effect.resolver().name().toLowerCase(Locale.ROOT), effect.value());
        }

        @Override
        public int compareTo(AbilityEffectView other) {
            return id.compareNamespaced(other.id);
        }
    }

    public record AbilityCostView(
            ResourceLocation id,
            String type,
            Optional<ResourceLocation> currency,
            long amount
    ) implements Comparable<AbilityCostView> {
        public AbilityCostView {
            id = StableId.requireValid(id);
            type = NetworkLimits.requireBoundedText(type, 32, "projected ability cost type");
            currency = Objects.requireNonNull(currency, "currency").map(StableId::requireValid);
            long maximum = type.equals("currency") ? AbilityCurrencyCost.MAX_AMOUNT
                    : type.equals("hunger")
                    ? com.envisione.progressiveskills.common.ability.AbilityVanillaCost.MAX_HUNGER
                    : com.envisione.progressiveskills.common.ability.AbilityVanillaCost.MAX_EXPERIENCE;
            if (amount < 1 || amount > maximum || currency.isPresent() != type.equals("currency")
                    || !List.of("currency", "hunger", "experience").contains(type)) {
                throw new IllegalArgumentException("Projected ability cost is invalid");
            }
        }

        static AbilityCostView from(AbilityCost cost) {
            return new AbilityCostView(
                    cost.id(), cost.type().serializedName(),
                    cost instanceof AbilityCurrencyCost currency
                            ? Optional.of(currency.currency()) : Optional.empty(),
                    cost.amount());
        }

        @Override
        public int compareTo(AbilityCostView other) {
            return id.compareNamespaced(other.id);
        }
    }

    public record AbilityTargetView(String mode, int range, boolean lineOfSight) {
        public AbilityTargetView {
            mode = NetworkLimits.requireBoundedText(mode, 32, "projected ability target mode");
            boolean self = mode.equals("self");
            if (!List.of("self", "entity", "block").contains(mode)
                    || self && (range != 0 || lineOfSight)
                    || !self && (range < 1 || range > 64)) {
                throw new IllegalArgumentException("Projected ability targeting is invalid");
            }
        }

        static AbilityTargetView from(com.envisione.progressiveskills.common.ability.AbilityTargeting targeting) {
            return new AbilityTargetView(
                    targeting.mode().serializedName(), targeting.range(), targeting.lineOfSight());
        }
    }

    public record AbilityActionView(
            ResourceLocation id,
            String type,
            Optional<Text> message,
            Optional<ResourceLocation> target,
            long value,
            int durationTicks,
            boolean ambient,
            boolean showParticles,
            boolean showIcon
    ) implements Comparable<AbilityActionView> {
        public AbilityActionView {
            id = StableId.requireValid(id);
            type = NetworkLimits.requireBoundedText(type, 32, "projected ability action type");
            message = Objects.requireNonNull(message, "message");
            target = Objects.requireNonNull(target, "target").map(StableId::requireValid);
            boolean valid = switch (type) {
                case "message" -> message.isPresent() && target.isEmpty() && value == 0
                        && durationTicks == 0 && !ambient && !showParticles && !showIcon;
                case "heal" -> message.isEmpty() && target.isEmpty() && value > 0
                        && value <= AbilityHealAction.MAX_AMOUNT_UNITS
                        && durationTicks == 0 && !ambient && !showParticles && !showIcon;
                case "vanilla_effect" -> message.isEmpty() && target.isPresent()
                        && value >= 0 && value <= AbilityVanillaEffectAction.MAX_AMPLIFIER
                        && durationTicks > 0
                        && durationTicks <= AbilityVanillaEffectAction.MAX_DURATION_TICKS;
                default -> false;
            };
            if (!valid) {
                throw new IllegalArgumentException("Projected ability action is invalid");
            }
        }

        static AbilityActionView from(AbilityAction action) {
            if (action instanceof AbilityMessageAction message) {
                return new AbilityActionView(
                        action.id(), action.type().serializedName(),
                        Optional.of(Text.from(message.message())), Optional.empty(),
                        0, 0, false, false, false);
            }
            if (action instanceof AbilityHealAction heal) {
                return new AbilityActionView(
                        action.id(), action.type().serializedName(), Optional.empty(), Optional.empty(),
                        heal.amountUnits(), 0, false, false, false);
            }
            AbilityVanillaEffectAction effect = (AbilityVanillaEffectAction) action;
            return new AbilityActionView(
                    action.id(), action.type().serializedName(), Optional.empty(),
                    Optional.of(effect.effect()), effect.amplifier(), effect.durationTicks(),
                    effect.ambient(), effect.showParticles(), effect.showIcon());
        }

        @Override
        public int compareTo(AbilityActionView other) {
            return id.compareNamespaced(other.id);
        }
    }

    private static <T extends Comparable<? super T>> List<T> boundedAbilityValues(
            List<T> values,
            int maximum,
            String name,
            Function<T, ResourceLocation> id,
            boolean sorted
    ) {
        Objects.requireNonNull(values, name + "s");
        if (values.size() > maximum) {
            throw new IllegalArgumentException("Projected " + name + " count exceeds capacity");
        }
        List<T> checked = values.stream().map(value -> Objects.requireNonNull(value, name)).toList();
        if (new HashSet<>(checked.stream().map(id).toList()).size() != checked.size()) {
            throw new IllegalArgumentException("Projected " + name + " contains duplicates");
        }
        return sorted ? checked.stream().sorted().toList() : checked;
    }

    public record ClassSlotView(int capacity, String swapPolicy) {
        public ClassSlotView {
            if (capacity < 1 || capacity > NetworkLimits.MAX_CLASS_SLOT_CAPACITY) {
                throw new IllegalArgumentException("Projected class slot capacity is invalid");
            }
            swapPolicy = NetworkLimits.requireBoundedText(
                    swapPolicy, 32, "projected class swap policy");
            if (swapPolicy.isBlank()) {
                throw new IllegalArgumentException("Projected class swap policy must not be blank");
            }
        }

        static ClassSlotView from(ClassSlotDefinition slot) {
            return new ClassSlotView(slot.capacity(), slot.swapPolicy().serializedName());
        }
    }

    public record ClassView(
            boolean enabled,
            ResourceLocation slotId,
            int slotCost,
            boolean accessRequired,
            List<ResourceLocation> exclusiveTags,
            Map<ResourceLocation, Integer> minimumSkillLevels,
            List<ResourceLocation> requiredNodes,
            List<ResourceLocation> requiredClasses,
            Optional<CurrencyCostView> selectionCost,
            boolean respecAllowed,
            Optional<CurrencyCostView> respecCost,
            List<StarterItemView> starterKit,
            List<GrantSummary> grants
    ) {
        public ClassView {
            slotId = StableId.requireValid(slotId);
            if (slotCost < 0 || slotCost > NetworkLimits.MAX_CLASS_SLOT_CAPACITY) {
                throw new IllegalArgumentException("Projected class slot cost is invalid");
            }
            exclusiveTags = boundedClassIds(
                    exclusiveTags, NetworkLimits.MAX_CLASS_EXCLUSIVE_TAGS, "class exclusive tag");
            minimumSkillLevels = boundedClassLevels(minimumSkillLevels);
            requiredNodes = boundedClassIds(
                    requiredNodes, NetworkLimits.MAX_CLASS_PREREQUISITES, "required class node");
            requiredClasses = boundedClassIds(
                    requiredClasses, NetworkLimits.MAX_CLASS_PREREQUISITES, "required class");
            if (requiredNodes.size() + requiredClasses.size() > NetworkLimits.MAX_CLASS_PREREQUISITES) {
                throw new IllegalArgumentException("Projected class prerequisite count exceeds capacity");
            }
            selectionCost = Objects.requireNonNull(selectionCost, "selectionCost");
            respecCost = Objects.requireNonNull(respecCost, "respecCost");
            if (!respecAllowed && respecCost.isPresent()) {
                throw new IllegalArgumentException("Projected class cannot price a disabled respec");
            }
            Objects.requireNonNull(starterKit, "starterKit");
            if (starterKit.size() > NetworkLimits.MAX_CLASS_STARTER_ITEMS) {
                throw new IllegalArgumentException("Projected starter kit item count exceeds capacity");
            }
            starterKit = starterKit.stream()
                    .map(value -> Objects.requireNonNull(value, "starter kit item"))
                    .sorted().toList();
            if (new HashSet<>(starterKit.stream().map(StarterItemView::item).toList()).size()
                    != starterKit.size()) {
                throw new IllegalArgumentException("Projected starter kit contains duplicate items");
            }
            grants = boundedGrantSummaries(grants);
        }

        static ClassView from(ClassDefinition definition) {
            return new ClassView(
                    definition.enabled(),
                    definition.slot(),
                    definition.slotCost(),
                    definition.accessRequired(),
                    definition.exclusiveTags().stream().toList(),
                    definition.minimumSkillLevels(),
                    definition.requiredNodes(),
                    definition.requiredClasses(),
                    definition.selectionCost().map(CurrencyCostView::from),
                    definition.respecAllowed(),
                    definition.respecCost().map(CurrencyCostView::from),
                    starterItems(definition),
                    definition.grants().stream().map(GrantSummary::from).toList()
            );
        }
    }

    public record SynergyView(
            boolean enabled,
            Optional<Text> display,
            Optional<Text> description,
            Optional<Icon> icon,
            List<String> searchAliases,
            List<ResourceLocation> requiredClasses,
            List<GrantSummary> grants
    ) {
        public SynergyView {
            display = Objects.requireNonNull(display, "display");
            description = Objects.requireNonNull(description, "description");
            icon = Objects.requireNonNull(icon, "icon");
            if (display.isEmpty() != icon.isEmpty()) {
                throw new IllegalArgumentException("Projected synergy display and icon must be present together");
            }
            Objects.requireNonNull(searchAliases, "searchAliases");
            if (searchAliases.size() > NetworkLimits.MAX_ALIASES_PER_DEFINITION) {
                throw new IllegalArgumentException("Projected synergy aliases exceed capacity");
            }
            searchAliases = searchAliases.stream().map(value -> NetworkLimits.requireBoundedText(
                    value, NetworkLimits.MAX_TEXT_BYTES, "projected synergy alias")).toList();
            requiredClasses = boundedClassIds(
                    requiredClasses, NetworkLimits.MAX_CLASS_SYNERGY_CLASSES, "synergy required class");
            if (requiredClasses.size() < 2) {
                throw new IllegalArgumentException("Projected class synergy requires at least two classes");
            }
            grants = boundedGrantSummaries(grants);
        }

        static SynergyView from(ClassSynergyDefinition synergy) {
            var presentation = synergy.presentation();
            return new SynergyView(
                    synergy.enabled(),
                    Optional.of(Text.from(presentation.display())),
                    presentation.description().map(Text::from),
                    Optional.of(Icon.from(presentation.icon())),
                    List.copyOf(presentation.searchAliases()),
                    synergy.requiredClasses(),
                    synergy.grants().stream().map(GrantSummary::from).toList()
            );
        }
    }

    public record CurrencyCostView(ResourceLocation currency, long amount) {
        public CurrencyCostView {
            currency = StableId.requireValid(currency);
            if (amount < 1) {
                throw new IllegalArgumentException("Projected class currency cost must be positive");
            }
        }

        static CurrencyCostView from(ClassCurrencyCost cost) {
            return new CurrencyCostView(cost.currency(), cost.amount());
        }
    }

    public record StarterItemView(ResourceLocation item, int count) implements Comparable<StarterItemView> {
        public StarterItemView {
            item = StableId.requireValid(item);
            if (count < 1 || count > NetworkLimits.MAX_CLASS_STARTER_ITEMS) {
                throw new IllegalArgumentException("Projected starter item count is invalid");
            }
        }

        @Override
        public int compareTo(StarterItemView other) {
            return item.compareNamespaced(other.item);
        }
    }

    public record GrantSummary(
            String type,
            ResourceLocation target,
            String operation,
            String resolver,
            long value
    ) implements Comparable<GrantSummary> {
        public GrantSummary {
            type = NetworkLimits.requireBoundedText(type, 64, "projected class grant type");
            if (type.isBlank()) {
                throw new IllegalArgumentException("Projected class grant type must not be blank");
            }
            target = StableId.requireValid(target);
            operation = NetworkLimits.requireBoundedText(
                    operation, 64, "projected class grant operation");
            if (operation.isBlank()) {
                throw new IllegalArgumentException("Projected class grant operation must not be blank");
            }
            resolver = NetworkLimits.requireBoundedText(
                    resolver, 32, "projected class grant resolver");
            if (resolver.isBlank()) {
                throw new IllegalArgumentException("Projected class grant resolver must not be blank");
            }
        }

        @Override
        public int compareTo(GrantSummary other) {
            int typeComparison = type.compareTo(other.type);
            if (typeComparison != 0) {
                return typeComparison;
            }
            int targetComparison = target.compareNamespaced(other.target);
            if (targetComparison != 0) {
                return targetComparison;
            }
            int operationComparison = operation.compareTo(other.operation);
            if (operationComparison != 0) {
                return operationComparison;
            }
            int resolverComparison = resolver.compareTo(other.resolver);
            return resolverComparison != 0 ? resolverComparison : Long.compare(value, other.value);
        }

        static GrantSummary from(ClassGrant grant) {
            String operation;
            if (grant instanceof ClassAttributeGrant attribute) {
                operation = attribute.operation().serializedName();
            } else if (grant instanceof ClassSpellGrant spell) {
                operation = spell.learningPolicy().serializedName();
            } else {
                operation = "owned";
            }
            return new GrantSummary(
                    grant.type().serializedName(),
                    grant.targetId(),
                    operation,
                    grant.resolver().name().toLowerCase(Locale.ROOT),
                    grant.value()
            );
        }
    }

    private static List<StarterItemView> starterItems(ClassDefinition definition) {
        var counts = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        definition.starterKit().ifPresent(kit -> kit.items().forEach(item ->
                counts.merge(item, 1, Math::addExact)));
        return counts.entrySet().stream()
                .map(entry -> new StarterItemView(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<ResourceLocation> boundedClassIds(
            List<ResourceLocation> values,
            int maximum,
            String name
    ) {
        Objects.requireNonNull(values, name);
        if (values.size() > maximum) {
            throw new IllegalArgumentException("Projected " + name + " count exceeds capacity");
        }
        var sorted = values.stream().map(StableId::requireValid)
                .sorted(ResourceLocation::compareNamespaced).toList();
        if (new HashSet<>(sorted).size() != sorted.size()) {
            throw new IllegalArgumentException("Projected " + name + " contains duplicates");
        }
        return sorted;
    }

    private static Map<ResourceLocation, Integer> boundedClassLevels(
            Map<ResourceLocation, Integer> values
    ) {
        Objects.requireNonNull(values, "minimumSkillLevels");
        if (values.size() > NetworkLimits.MAX_CLASS_MINIMUM_SKILLS) {
            throw new IllegalArgumentException("Projected class minimum skill count exceeds capacity");
        }
        var sorted = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        values.forEach((skill, level) -> {
            if (level == null || level < 0) {
                throw new IllegalArgumentException("Projected class minimum skill level is invalid");
            }
            sorted.put(StableId.requireValid(skill), level);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static List<GrantSummary> boundedGrantSummaries(List<GrantSummary> values) {
        Objects.requireNonNull(values, "grant summaries");
        if (values.size() > NetworkLimits.MAX_CLASS_GRANT_SUMMARIES) {
            throw new IllegalArgumentException("Projected class grant summary count exceeds capacity");
        }
        var sorted = values.stream().map(value -> Objects.requireNonNull(value, "grant summary"))
                .sorted().toList();
        if (new HashSet<>(sorted).size() != sorted.size()) {
            throw new IllegalArgumentException("Projected class grant summaries contain duplicates");
        }
        return sorted;
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
