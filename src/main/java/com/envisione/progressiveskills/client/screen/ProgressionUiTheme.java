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
    private static final ResourceLocation DEFAULT_WORKBENCH_BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/end.png");
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
    private static final Map<String, Integer> DEFAULT_WORKBENCH_COLORS = Map.ofEntries(
            Map.entry("meter_fill", 0xFF78B84A),
            Map.entry("point_text", 0xFFFFD65C),
            Map.entry("owned_node", 0xFF5FA34A),
            Map.entry("available_node", 0xFFFFC55C),
            Map.entry("locked_node", 0xFF737373),
            Map.entry("hovered_node", 0xFFFFFFFF),
            Map.entry("selected_node", 0xFF80C8FF),
            Map.entry("required_path", 0xFF777777),
            Map.entry("alternative_path", 0xFF5D8FC7),
            Map.entry("owned_path", 0xFF63A84F)
    );
    private static final int DEFAULT_POINT_BALANCES = 3;

    private final Map<String, ResourceLocation> tabIcons;
    private final List<String> treeTooltipLines;
    private final int treeTooltipWidth;
    private final Map<String, Integer> workbenchColors;
    private final int pointBalanceLimit;
    private final ResourceLocation workbenchBackground;

    private ProgressionUiTheme(
            Map<String, ResourceLocation> tabIcons,
            List<String> treeTooltipLines,
            int treeTooltipWidth,
            Map<String, Integer> workbenchColors,
            int pointBalanceLimit,
            ResourceLocation workbenchBackground
    ) {
        this.tabIcons = Map.copyOf(tabIcons);
        this.treeTooltipLines = List.copyOf(treeTooltipLines);
        this.treeTooltipWidth = Math.clamp(treeTooltipWidth, 80, 320);
        this.workbenchColors = Map.copyOf(workbenchColors);
        this.pointBalanceLimit = Math.clamp(pointBalanceLimit, 1, 5);
        this.workbenchBackground = workbenchBackground;
    }

    static ProgressionUiTheme load() {
        Map<String, ResourceLocation> icons = new LinkedHashMap<>(DEFAULT_ICONS);
        List<String> lines = new ArrayList<>(DEFAULT_TOOLTIP_LINES);
        int width = DEFAULT_TOOLTIP_WIDTH;
        Map<String, Integer> colors = new LinkedHashMap<>(DEFAULT_WORKBENCH_COLORS);
        int pointBalances = DEFAULT_POINT_BALANCES;
        ResourceLocation background = DEFAULT_WORKBENCH_BACKGROUND;
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
                if (root.has("skill_workbench")) {
                    JsonObject workbench = root.getAsJsonObject("skill_workbench");
                    if (workbench.has("background")) {
                        ResourceLocation candidate = ResourceLocation.tryParse(
                                workbench.get("background").getAsString());
                        if (candidate != null
                                && Minecraft.getInstance().getResourceManager()
                                .getResource(candidate).isPresent()) {
                            background = candidate;
                        }
                    }
                    if (workbench.has("max_point_balances")) {
                        pointBalances = Math.clamp(
                                workbench.get("max_point_balances").getAsInt(), 1, 5);
                    }
                    if (workbench.has("colors")) {
                        JsonObject colorObject = workbench.getAsJsonObject("colors");
                        for (Map.Entry<String, Integer> fallback : DEFAULT_WORKBENCH_COLORS.entrySet()) {
                            if (colorObject.has(fallback.getKey())) {
                                colors.put(fallback.getKey(), parseColor(
                                        colorObject.get(fallback.getKey()), fallback.getValue()));
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException | java.io.IOException exception) {
            LOGGER.warn("ProgressiveSkills UI theme could not be loaded. Defaults remain active.", exception);
        }
        return new ProgressionUiTheme(icons, lines, width, colors, pointBalances, background);
    }

    private static ProgressionUiTheme defaults() {
        return new ProgressionUiTheme(
                DEFAULT_ICONS,
                DEFAULT_TOOLTIP_LINES,
                DEFAULT_TOOLTIP_WIDTH,
                DEFAULT_WORKBENCH_COLORS,
                DEFAULT_POINT_BALANCES,
                DEFAULT_WORKBENCH_BACKGROUND
        );
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

    int workbenchColor(String name) {
        return workbenchColors.getOrDefault(name, 0xFFFFFFFF);
    }

    int pointBalanceLimit() {
        return pointBalanceLimit;
    }

    ResourceLocation workbenchBackground() {
        return workbenchBackground;
    }

    static int parseColor(JsonElement value, int fallback) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return fallback;
        }
        String text = value.getAsString().strip();
        try {
            if (text.matches("#[0-9a-fA-F]{6}")) {
                return 0xFF000000 | Integer.parseUnsignedInt(text.substring(1), 16);
            }
            if (text.matches("#[0-9a-fA-F]{8}")) {
                return Integer.parseUnsignedInt(text.substring(1), 16);
            }
        } catch (NumberFormatException ignored) {
            return fallback;
        }
        return fallback;
    }
}
