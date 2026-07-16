package com.envisione.progressiveskills.common.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtDataLimitsTest {
    @Test
    void excessiveDepthAndStringsAreRejectedBeforeTypedDecode() {
        var deep = new CompoundTag();
        CompoundTag cursor = deep;
        for (int depth = 0; depth <= NbtDataLimits.MAX_DEPTH; depth++) {
            var child = new CompoundTag();
            cursor.put("child", child);
            cursor = child;
        }
        assertTrue(NbtDataLimits.rejection(deep).isPresent());

        var text = new CompoundTag();
        text.putString("value", "x".repeat(NbtDataLimits.MAX_STRING_CHARACTERS + 1));
        assertTrue(NbtDataLimits.rejection(text).isPresent());
    }
}
