package com.envisione.progressiveskills.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

final class UiText {
    private UiText() {
    }

    static Component legacy(String value) {
        MutableComponent result = Component.empty();
        StringBuilder text = new StringBuilder();
        Style style = Style.EMPTY;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '&' || index + 1 >= value.length()) {
                text.append(current);
                continue;
            }
            char code = value.charAt(index + 1);
            if (code == '&') {
                text.append('&');
                index++;
                continue;
            }
            ChatFormatting format = ChatFormatting.getByCode(code);
            if (format == null) {
                text.append(current);
                continue;
            }
            if (!text.isEmpty()) {
                result.append(Component.literal(text.toString()).setStyle(style));
                text.setLength(0);
            }
            style = format == ChatFormatting.RESET ? Style.EMPTY : style.applyFormat(format);
            index++;
        }
        if (!text.isEmpty()) {
            result.append(Component.literal(text.toString()).setStyle(style));
        }
        return result;
    }

    static String prettyId(ResourceLocation id) {
        return prettyPath(id.getPath());
    }

    static String prettyPath(String value) {
        String normalized = value.replace('_', ' ').replace('-', ' ')
                .replace('/', ' ').replace('.', ' ').strip();
        if (normalized.isEmpty()) {
            return value;
        }
        StringBuilder result = new StringBuilder(normalized.length());
        for (String word : normalized.split("\\s+")) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            String lower = word.toLowerCase(Locale.ROOT);
            result.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
        }
        return result.toString();
    }
}
