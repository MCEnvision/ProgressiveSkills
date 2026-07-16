package com.envisione.progressiveskills.common.id;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Stable kind identities reserved by the north-star definition vocabulary. */
public final class DefinitionKinds {
    private static final List<DefinitionKind> REGISTRATIONS = new ArrayList<>();

    public static final DefinitionKind ABILITY = kind("ability", "abilities");
    public static final DefinitionKind ANTI_EXPLOIT_PROFILE = kind("anti_exploit_profile", "anti_exploit_profiles");
    public static final DefinitionKind CATEGORY = kind("category", "categories");
    public static final DefinitionKind CHALLENGE = kind("challenge", "challenges");
    public static final DefinitionKind CLASS = kind("class", "classes");
    public static final DefinitionKind CLASS_SLOT = kind("class_slot", "class_slots");
    public static final DefinitionKind COMPONENT_SPEC = kind("component_spec", "component_specs");
    public static final DefinitionKind CONVERSION = kind("conversion", "conversions");
    public static final DefinitionKind COST_BUNDLE = kind("cost_bundle", "cost_bundles");
    public static final DefinitionKind CURRENCY = kind("currency", "currencies");
    public static final DefinitionKind CURVE = kind("curve", "curves");
    public static final DefinitionKind GLOBAL_LEVEL = kind("global_level", "global_levels");
    public static final DefinitionKind GRANT_BUNDLE = kind("grant_bundle", "grant_bundles");
    public static final DefinitionKind ICON_SPEC = kind("icon_spec", "icon_specs");
    public static final DefinitionKind ITEM = kind("item", "items");
    public static final DefinitionKind ITEM_STACK_SPEC = kind("item_stack_spec", "item_stack_specs");
    public static final DefinitionKind LAYOUT = kind("layout", "layouts");
    public static final DefinitionKind NOTIFICATION_PROFILE = kind("notification_profile", "notification_profiles");
    public static final DefinitionKind PREDICATE = kind("predicate", "predicates");
    public static final DefinitionKind PRESTIGE = kind("prestige", "prestige");
    public static final DefinitionKind PROFILE = kind("profile", "profiles");
    public static final DefinitionKind REQUIREMENT = kind("requirement", "requirements");
    public static final DefinitionKind RESOURCE = kind("resource", "resources");
    public static final DefinitionKind RULE = kind("rule", "rules");
    public static final DefinitionKind SEASON = kind("season", "seasons");
    public static final DefinitionKind SKILL = kind("skill", "skills");
    public static final DefinitionKind STATION = kind("station", "stations");
    public static final DefinitionKind TARGETING_PROFILE = kind("targeting_profile", "targeting_profiles");
    public static final DefinitionKind TEMPLATE = kind("template", "templates");
    public static final DefinitionKind THEME = kind("theme", "themes");
    public static final DefinitionKind TREE = kind("tree", "trees");
    public static final DefinitionKind VARIABLE = kind("variable", "variables");

    private static final List<DefinitionKind> ALL = REGISTRATIONS.stream().sorted().toList();
    private static final Map<ResourceLocation, DefinitionKind> BY_ID = indexById();

    private DefinitionKinds() {
    }

    public static List<DefinitionKind> all() {
        return ALL;
    }

    public static DefinitionKind require(ResourceLocation id) {
        var kind = BY_ID.get(Objects.requireNonNull(id, "id"));
        if (kind == null) {
            throw new IllegalArgumentException("Unknown built-in definition kind: " + id);
        }
        return kind;
    }

    private static DefinitionKind kind(String path, String directory) {
        var kind = new DefinitionKind(
                ResourceLocation.fromNamespaceAndPath("progressiveskills", path),
                directory
        );
        REGISTRATIONS.add(kind);
        return kind;
    }

    private static Map<ResourceLocation, DefinitionKind> indexById() {
        var sorted = new TreeMap<ResourceLocation, DefinitionKind>(ResourceLocation::compareNamespaced);
        for (var kind : ALL) {
            var previous = sorted.putIfAbsent(kind.id(), kind);
            if (previous != null) {
                throw new IllegalStateException("Duplicate built-in definition kind: " + kind.id());
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
