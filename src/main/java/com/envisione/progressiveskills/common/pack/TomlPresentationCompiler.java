package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.presentation.PlaceholderType;
import com.envisione.progressiveskills.common.presentation.StyleSpec;
import com.envisione.progressiveskills.common.presentation.TextColorSpec;
import com.envisione.progressiveskills.common.presentation.TextDecoration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** One adapter-neutral normalization path for TOML component and icon authoring shapes. */
final class TomlPresentationCompiler {
    private static final Set<String> COMPONENT_FIELDS = Set.of("key", "fallback", "placeholders", "style", "children");
    private static final Set<String> STYLE_FIELDS = Set.of(
            "color", "font", "bold", "italic", "underlined", "strikethrough", "obfuscated"
    );
    private static final Set<String> ICON_FIELDS = Set.of(
            "type", "value", "values", "fallback", "alt", "narration", "entity_preview_opt_in"
    );

    private TomlPresentationCompiler() {}

    static ComponentSpec component(Object authored, String context) {
        if (authored instanceof String literal) {
            return ComponentSpec.literal(literal);
        }
        if (!(authored instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException(context + " must be a string or component table");
        }
        @SuppressWarnings("unchecked") Map<String, Object> values = (Map<String, Object>) raw;
        TomlValues.rejectUnknown(values, COMPONENT_FIELDS, context);
        Optional<String> key = TomlValues.optionalString(values, "key");
        String fallback = TomlValues.string(values, "fallback");
        var placeholders = new TreeMap<String, PlaceholderType>();
        for (var entry : TomlValues.object(values, "placeholders", false).entrySet()) {
            if (!(entry.getValue() instanceof String type)) {
                throw new IllegalArgumentException(context + ".placeholders values must be strings");
            }
            try {
                placeholders.put(entry.getKey(), PlaceholderType.valueOf(type.toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown placeholder type: " + type, exception);
            }
        }
        StyleSpec style = style(TomlValues.object(values, "style", false), context + ".style");
        var children = new ArrayList<ComponentSpec>();
        Object childrenValue = values.get("children");
        if (childrenValue != null) {
            if (!(childrenValue instanceof List<?> list)) {
                throw new IllegalArgumentException(context + ".children must be a list");
            }
            for (int index = 0; index < list.size(); index++) {
                children.add(component(list.get(index), context + ".children[" + index + "]"));
            }
        }
        return new ComponentSpec(key, fallback, placeholders, style, children);
    }

    static IconSpec icon(Object authored, String context) {
        if (!(authored instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException(context + " must be an icon table");
        }
        @SuppressWarnings("unchecked") Map<String, Object> values = (Map<String, Object>) raw;
        TomlValues.rejectUnknown(values, ICON_FIELDS, context);
        String type = TomlValues.string(values, "type");
        IconKind kind = java.util.Arrays.stream(IconKind.values())
                .filter(candidate -> candidate.serializedName().equals(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown icon type: " + type));
        boolean hasSingle = values.containsKey("value");
        boolean hasMultiple = values.containsKey("values");
        if (hasSingle == hasMultiple) {
            throw new IllegalArgumentException(context + " must declare exactly one of value or values");
        }
        List<net.minecraft.resources.ResourceLocation> references;
        if (hasSingle) {
            references = List.of(StableId.parse(TomlValues.string(values, "value")));
        } else {
            references = TomlValues.stringList(values, "values").stream().map(StableId::parse).toList();
        }
        var fallback = StableId.parse(TomlValues.string(values, "fallback"));
        ComponentSpec alt = component(values.get("alt"), context + ".alt");
        Optional<ComponentSpec> narration = values.containsKey("narration")
                ? Optional.of(component(values.get("narration"), context + ".narration"))
                : Optional.empty();
        boolean entityPreview = TomlValues.optionalBoolean(values, "entity_preview_opt_in", false);
        return new IconSpec(kind, references, fallback, alt, narration, entityPreview);
    }

    private static StyleSpec style(Map<String, Object> values, String context) {
        TomlValues.rejectUnknown(values, STYLE_FIELDS, context);
        Optional<TextColorSpec> color = TomlValues.optionalString(values, "color").map(TextColorSpec::new);
        Optional<net.minecraft.resources.ResourceLocation> font = TomlValues.optionalString(values, "font")
                .map(StableId::parse);
        var decorations = EnumSet.noneOf(TextDecoration.class);
        addDecoration(values, "bold", TextDecoration.BOLD, decorations);
        addDecoration(values, "italic", TextDecoration.ITALIC, decorations);
        addDecoration(values, "underlined", TextDecoration.UNDERLINED, decorations);
        addDecoration(values, "strikethrough", TextDecoration.STRIKETHROUGH, decorations);
        addDecoration(values, "obfuscated", TextDecoration.OBFUSCATED, decorations);
        return new StyleSpec(color, decorations, font);
    }

    private static void addDecoration(
            Map<String, Object> values,
            String field,
            TextDecoration decoration,
            EnumSet<TextDecoration> decorations
    ) {
        if (TomlValues.optionalBoolean(values, field, false)) {
            decorations.add(decoration);
        }
    }
}
