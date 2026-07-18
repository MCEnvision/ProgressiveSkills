package com.envisione.progressiveskills.server.pack;

import com.envisione.progressiveskills.common.carrier.CarrierCatalog;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.pack.PackSnapshot;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.server.carrier.BehaviorArchiveSavedData;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;

public final class CarrierPublicationGuard {
    private CarrierPublicationGuard() {
    }

    public static BehaviorArchiveSavedData.Reservation preview(
            MinecraftServer server,
            PackSnapshot snapshot
    ) {
        return BehaviorArchiveSavedData.get(Objects.requireNonNull(server, "server"))
                .previewReserve(enabledBehaviors(snapshot));
    }

    public static BehaviorArchiveSavedData.Reservation reserve(
            MinecraftServer server,
            PackSnapshot snapshot
    ) {
        return BehaviorArchiveSavedData.get(Objects.requireNonNull(server, "server"))
                .reserve(enabledBehaviors(snapshot));
    }

    private static java.util.List<com.envisione.progressiveskills.common.carrier.CarrierBehaviorSnapshot>
    enabledBehaviors(PackSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        SkillCatalog skills = SkillCatalog.from(snapshot.canonicalIr());
        TreeCatalog trees = TreeCatalog.from(snapshot.canonicalIr(), skills);
        CarrierCatalog carriers = CarrierCatalog.from(snapshot.canonicalIr(), skills, trees);
        return carriers.carriers().values().stream()
                .filter(com.envisione.progressiveskills.common.carrier.CarrierDefinition::enabled)
                .map(com.envisione.progressiveskills.common.carrier.CarrierDefinition::behaviorSnapshot)
                .toList();
    }
}
