package com.envisione.progressiveskills.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ProgressionUiTheme {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath(
            "progressiveskills", "ui/progression.json");
    private static final int DEFAULT_TOOLTIP_WIDTH = 220;
    private static final List<String> DEFAULT_TOOLTIP_LINES = List.of(
            "&l&c{name}",
            "&c{state}",
            "Cost {cost} {currency}",
            "{description}"
    );
    private static final Map<String, ResourceLocation> DEFAULT_ICONS = Map.ofEntries(
            Map.entry("skills", ResourceLocation.withDefaultNamespace("experience_bottle")),
            Map.entry("trees", ResourceLocation.withDefaultNamespace("oak_sapling")),
            Map.entry("classes", ResourceLocation.withDefaultNamespace("armor_stand")),
            Map.entry("abilities", ResourceLocation.withDefaultNamespace("blaze_powder")),
            Map.entry("claims", ResourceLocation.withDefaultNamespace("chest")),
            Map.entry("guide", ResourceLocation.withDefaultNamespace("knowledge_book")),
            Map.entry("compare", ResourceLocation.withDefaultNamespace("compass")),
            Map.entry("tests", ResourceLocation.withDefaultNamespace("writable_book")),
            Map.entry("sync", ResourceLocation.withDefaultNamespace("redstone")),
            Map.entry("studio", ResourceLocation.withDefaultNamespace("crafting_table"))
    );

    private final Map<String, ResourceLocation> tabIcons;
    private final List<String> treeTooltipLines;
    private final int treeTooltipWidth;

    private ProgressionUiTheme(
            Map<String, ResourceLocation> tabIcons,
            List<String> treeTooltipLines,
            int treeTooltipWidth
    ) {
        this.tabIcons = Map.copyOf(tabIcons);
        this.treeTooltipLines = List.copyOf(treeTooltipLines);
        this.treeTooltipWidth = Math.clamp(treeTooltipWidth, 80, 320);
    }

    static ProgressionUiTheme load() {
        Map<String, ResourceLocation> icons = new LinkedHashMap<>(DEFAULT_ICONS);
        List<String> lines = new ArrayList<>(DEFAULT_TOOLTIP_LINES);
        int width = DEFAULT_TOOLTIP_WIDTH;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(RESOURCE);
            if (resource.isEmpty()) {
                return defaults();
            }
            try (Reader reader = resource.orElseThrow().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject iconObject = root.has("tab_icons")
                        ? root.getAsJsonObject("tab_icons") : new JsonObject();
                for (Map.Entry<String, JsonElement> entry : iconObject.entrySet()) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.getValue().getAsString());
                    if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                        icons.put(entry.getKey().toLowerCase(Locale.ROOT), id);
                    }
                }
                if (root.has("tree_tooltip")) {
                    JsonObject tooltip = root.getAsJsonObject("tree_tooltip");
                    if (tooltip.has("max_width")) {
                        width = tooltip.get("max_width").getAsInt();
                    }
                    if (tooltip.has("lines")) {
                        JsonArray array = tooltip.getAsJsonArray("lines");
                        if (array.size() == 0 || array.size() > 32) {
                            throw new IllegalArgumentException("Tree tooltip line count is outside the safe range");
                        }
                        var parsed = new ArrayList<String>();
                        for (JsonElement value : array) {
                            String line = value.getAsString();
                            if (line.length() > 2048) {
                                throw new IllegalArgumentException("Tree tooltip line is too long");
                            }
                            parsed.add(line);
                        }
                        lines = parsed;
                    }
                }
            }
        } catch (RuntimeException | java.io.IOException exception) {
            LOGGER.warn("ProgressiveSkills UI theme could not be loaded. Defaults remain active.", exception);
        }
        return new ProgressionUiTheme(icons, lines, width);
    }

    private static ProgressionUiTheme defaults() {
        return new ProgressionUiTheme(DEFAULT_ICONS, DEFAULT_TOOLTIP_LINES, DEFAULT_TOOLTIP_WIDTH);
    }

    ItemStack tabIcon(String name, Item fallback) {
        ResourceLocation id = tabIcons.get(name.toLowerCase(Locale.ROOT));
        return id == null ? new ItemStack(fallback) : BuiltInRegistries.ITEM.getOptional(id)
                .map(ItemStack::new).orElseGet(() -> new ItemStack(fallback));
    }

    List<String> treeTooltipLines() {
        return treeTooltipLines;
    }

    int treeTooltipWidth() {
        return treeTooltipWidth;
    }
}
