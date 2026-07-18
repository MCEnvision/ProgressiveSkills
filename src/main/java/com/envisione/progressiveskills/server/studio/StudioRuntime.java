package com.envisione.progressiveskills.server.studio;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.network.NetworkPayloads;
import com.envisione.progressiveskills.common.network.PsNetworking;
import com.envisione.progressiveskills.common.studio.StudioRecorder;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class StudioRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, StudioRecorder> RECORDERS = new LinkedHashMap<>();

    private StudioRuntime() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void started(ServerStartedEvent event) {
        PsNetworking.configureServerStudioFileExecutor(StudioRuntime::writeFile);
        try {
            StudioService.recover(event.getServer());
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("ProgressiveSkills Studio recovery failed", exception);
            throw new IllegalStateException("ProgressiveSkills Studio recovery failed", exception);
        }
    }

    @SubscribeEvent
    static void stopping(ServerStoppingEvent event) {
        PsNetworking.configureServerStudioFileExecutor(PsNetworking.StudioFileExecutor.REJECT);
        RECORDERS.clear();
    }

    private static NetworkPayloads.StudioFileResult writeFile(
            MinecraftServer server,
            UUID actor,
            NetworkPayloads.StudioFilePut payload
    ) throws IOException {
        var updated = StudioService.write(
                server, payload.draftId(), actor, payload.revision(), payload.path(), payload.contents());
        return new NetworkPayloads.StudioFileResult(
                payload.draftId(), true, updated.revision(), "Studio file saved.");
    }

    public static synchronized void startRecording(UUID operator) {
        if (RECORDERS.putIfAbsent(operator, new StudioRecorder()) != null) {
            throw new IllegalStateException("Studio recording is already active");
        }
    }

    public static synchronized void record(UUID operator, String type, String subject, long value) {
        StudioRecorder recorder = RECORDERS.get(operator);
        if (recorder == null) {
            throw new IllegalStateException("Studio recording is not active");
        }
        recorder.record(type, subject, value);
    }

    public static synchronized StudioRecorder.Recording finishRecording(UUID operator) {
        StudioRecorder recorder = RECORDERS.remove(operator);
        if (recorder == null) {
            throw new IllegalStateException("Studio recording is not active");
        }
        return recorder.finish();
    }

    public static synchronized boolean recording(UUID operator) {
        return RECORDERS.containsKey(operator);
    }

    public static String fixture(StudioRecorder.Recording recording, ResourceLocation fixtureId) {
        var text = new StringBuilder();
        text.append("schema_version = 2\n\n[simulation]\n");
        text.append("id = \"").append(fixtureId).append("\"\n");
        text.append("recording_id = \"").append(recording.id()).append("\"\n");
        text.append("seed = ").append(recording.id().getMostSignificantBits()).append("\n");
        for (StudioRecorder.Event event : recording.events()) {
            text.append("\n[[simulation.steps]]\n");
            text.append("sequence = ").append(event.sequence()).append("\n");
            text.append("type = \"").append(escape(event.type())).append("\"\n");
            text.append("subject = \"").append(escape(event.subject())).append("\"\n");
            text.append("value = ").append(event.value()).append("\n");
        }
        return text.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
