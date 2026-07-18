package com.envisione.progressiveskills.client;

import com.envisione.progressiveskills.ProjectIdentity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID, value = Dist.CLIENT)
public final class ClientKeyMappings {
    public static final KeyMapping OPEN_PROGRESS = new KeyMapping(
            "key.progressiveskills.open_progress",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "key.categories.progressiveskills"
    );
    public static final KeyMapping OPEN_TREE = new KeyMapping(
            "key.progressiveskills.open_tree",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.progressiveskills"
    );
    public static final KeyMapping ABILITY_WHEEL = mapping(
            "key.progressiveskills.ability_wheel", GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping PREVIOUS_ABILITY = mapping(
            "key.progressiveskills.previous_ability", GLFW.GLFW_KEY_LEFT_BRACKET);
    public static final KeyMapping NEXT_ABILITY = mapping(
            "key.progressiveskills.next_ability", GLFW.GLFW_KEY_RIGHT_BRACKET);
    public static final KeyMapping USE_SELECTED_ABILITY = mapping(
            "key.progressiveskills.use_selected_ability", GLFW.GLFW_KEY_R);
    public static final KeyMapping COMMAND_PALETTE = mapping(
            "key.progressiveskills.command_palette", GLFW.GLFW_KEY_GRAVE_ACCENT);
    public static final List<KeyMapping> DIRECT_ABILITY_SLOTS = List.of(
            mapping("key.progressiveskills.ability_slot_1", GLFW.GLFW_KEY_UNKNOWN),
            mapping("key.progressiveskills.ability_slot_2", GLFW.GLFW_KEY_UNKNOWN),
            mapping("key.progressiveskills.ability_slot_3", GLFW.GLFW_KEY_UNKNOWN),
            mapping("key.progressiveskills.ability_slot_4", GLFW.GLFW_KEY_UNKNOWN),
            mapping("key.progressiveskills.ability_slot_5", GLFW.GLFW_KEY_UNKNOWN),
            mapping("key.progressiveskills.ability_slot_6", GLFW.GLFW_KEY_UNKNOWN),
            mapping("key.progressiveskills.ability_slot_7", GLFW.GLFW_KEY_UNKNOWN),
            mapping("key.progressiveskills.ability_slot_8", GLFW.GLFW_KEY_UNKNOWN)
    );

    private ClientKeyMappings() {
    }

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PROGRESS);
        event.register(OPEN_TREE);
        event.register(ABILITY_WHEEL);
        event.register(PREVIOUS_ABILITY);
        event.register(NEXT_ABILITY);
        event.register(USE_SELECTED_ABILITY);
        event.register(COMMAND_PALETTE);
        DIRECT_ABILITY_SLOTS.forEach(event::register);
    }

    private static KeyMapping mapping(String name, int key) {
        return new KeyMapping(
                name,
                InputConstants.Type.KEYSYM,
                key,
                "key.categories.progressiveskills"
        );
    }
}
