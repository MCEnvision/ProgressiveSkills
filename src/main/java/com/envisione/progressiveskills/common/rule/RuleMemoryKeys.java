package com.envisione.progressiveskills.common.rule;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record RuleMemoryKeys(
        ResourceLocation firstTime,
        ResourceLocation lastAwardTick,
        ResourceLocation repeatTick,
        ResourceLocation repeatCount,
        Window tick,
        Window minute,
        Window day
) {
    public static RuleMemoryKeys forRule(ResourceLocation ruleId) {
        String root = "rule_memory/" + digest(ruleId.toString()).substring(0, 32) + "/";
        return new RuleMemoryKeys(
                id(root + "first"),
                id(root + "last"),
                id(root + "repeat_tick"),
                id(root + "repeat_count"),
                new Window(id(root + "tick_epoch"), id(root + "tick_used")),
                new Window(id(root + "minute_epoch"), id(root + "minute_used")),
                new Window(id(root + "day_epoch"), id(root + "day_used"))
        );
    }

    public static boolean isInternal(ResourceLocation id) {
        return id.getNamespace().equals("progressiveskills") && id.getPath().startsWith("rule_memory/");
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }

    public record Window(ResourceLocation epoch, ResourceLocation used) {
    }
}
