package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.ProjectIdentity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.UUID;

/** Registration boundary for the durable per-player authority. */
public final class PsDataAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(
            NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            ProjectIdentity.MOD_ID
    );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ProgressiveSkillsData>> PLAYER_DATA =
            ATTACHMENTS.register("player_data", () -> AttachmentType
                    .builder(holder -> ProgressiveSkillsData.empty(playerId(holder)))
                    .serialize(new ProgressiveSkillsDataSerializer())
                    .copyOnDeath()
                    .copyHandler((data, holder, provider) -> data.copyFor(playerId(holder)))
                    .build());

    private PsDataAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    private static UUID playerId(IAttachmentHolder holder) {
        if (holder instanceof Player player) {
            return player.getUUID();
        }
        throw new IllegalArgumentException("ProgressiveSkills player data can only attach to a player");
    }
}
