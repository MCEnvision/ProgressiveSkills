package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        var result = new TreeMap<DefinitionKey, Entry>();
        ir.definitions().forEach((key, definition) -> result.put(key,
                definition.header().presentation()
                        .map(presentation -> new Entry(
                                Optional.of(Text.from(presentation.display())),
                                presentation.description().map(Text::from),
                                Optional.of(Icon.from(presentation.icon())),
                                List.copyOf(presentation.searchAliases())
                        ))
                        .orElseGet(Entry::withoutPresentation)));
        return new DefinitionProjection(result);
    }

    public record Entry(
            Optional<Text> display,
            Optional<Text> description,
            Optional<Icon> icon,
            List<String> searchAliases
    ) {
        public Entry {
            display = Objects.requireNonNull(display, "display");
            description = Objects.requireNonNull(description, "description");
            icon = Objects.requireNonNull(icon, "icon");
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
