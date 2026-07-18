package com.envisione.progressiveskills.server.carrier;

import com.envisione.progressiveskills.common.carrier.CarrierBehaviorSnapshot;
import com.envisione.progressiveskills.common.carrier.CarrierBindPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierCatalog;
import com.envisione.progressiveskills.common.carrier.CarrierDefinition;
import com.envisione.progressiveskills.common.carrier.CarrierIdentity;
import com.envisione.progressiveskills.common.carrier.CarrierMigrationPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierStackState;
import com.envisione.progressiveskills.common.carrier.PendingCarrierClaim;
import com.envisione.progressiveskills.common.carrier.PsCarrierComponents;
import com.envisione.progressiveskills.common.carrier.PsCarrierItems;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CarrierStackService {
    private CarrierStackService() {
    }

    public static ItemStack create(
            MinecraftServer server,
            CarrierBehaviorSnapshot behavior,
            String packDigest,
            Optional<UUID> owner,
            int count
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(behavior, "behavior");
        owner = Objects.requireNonNull(owner, "owner");
        if (count < 1 || count > behavior.stackSize()) {
            throw new IllegalArgumentException("Carrier stack count is outside its behavior bound");
        }
        CarrierStackState state = CarrierStackState.fresh(
                behavior.behaviorVersion(),
                behavior.charges(),
                packDigest,
                UUID.randomUUID()
        );
        if (behavior.bindPolicy() == CarrierBindPolicy.ON_PICKUP
                || behavior.bindPolicy() == CarrierBindPolicy.ON_CRAFT) {
            state = state.bind(owner.orElseThrow(
                    () -> new IllegalArgumentException("Carrier behavior requires an owner at creation")
            ));
        }
        BehaviorArchiveSavedData.get(server).reserve(java.util.List.of(behavior));
        return stack(behavior, state, count);
    }

    public static ItemStack materialize(MinecraftServer server, PendingCarrierClaim claim) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(claim, "claim");
        CarrierBehaviorSnapshot behavior = claim.behavior();
        if (!behavior.digest().equals(claim.identity().behaviorDigest())
                || !behavior.definitionId().equals(claim.identity().definitionId())
                || behavior.carrier() != claim.kind()
                || behavior.behaviorVersion() != claim.state().behaviorVersion()
                || claim.state().charges() < 1
                || claim.state().charges() > behavior.charges()) {
            throw new IllegalArgumentException("Pending claim cannot materialize an inconsistent carrier");
        }
        if ((behavior.bindPolicy() == CarrierBindPolicy.ON_PICKUP
                || behavior.bindPolicy() == CarrierBindPolicy.ON_CRAFT)
                && claim.state().boundOwner().isEmpty()) {
            throw new IllegalArgumentException("Pending claim is missing its required owner binding");
        }
        BehaviorArchiveSavedData.get(server).reserve(java.util.List.of(behavior));
        return stack(behavior, claim.state(), 1);
    }

    public static ItemStack materialize(ServerPlayer player, PendingCarrierClaim claim) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(claim, "claim");
        if (claim.state().boundOwner().isPresent()
                && !claim.state().boundOwner().orElseThrow().equals(player.getUUID())) {
            throw new IllegalArgumentException("Pending claim is bound to another player");
        }
        return materialize(player.getServer(), claim);
    }

    public static Inspection inspect(ServerPlayer player, ItemStack stack) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(stack, "stack");
        var kind = PsCarrierItems.kindOf(stack);
        CarrierIdentity identity = stack.get(PsCarrierComponents.IDENTITY.get());
        CarrierStackState state = stack.get(PsCarrierComponents.STATE.get());
        if (kind.isEmpty() || identity == null || state == null) {
            return new Inspection(
                    Optional.ofNullable(identity),
                    Optional.ofNullable(state),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    "Held item is not a complete progression carrier"
            );
        }
        BehaviorArchiveSavedData archive = BehaviorArchiveSavedData.get(player.getServer());
        Optional<CarrierBehaviorSnapshot> archived = archive.resolve(identity.behaviorDigest());
        Optional<CarrierBehaviorSnapshot> current = currentBehavior(identity.definitionId());
        CarrierBehaviorResolver.Resolution resolution = CarrierBehaviorResolver.resolve(
                identity, state, kind.orElseThrow(), player.getUUID(), archive, current
        );
        return new Inspection(
                Optional.of(identity),
                Optional.of(state),
                archived,
                current,
                Optional.of(resolution),
                resolution.message()
        );
    }

    public static MigrationPreview migratePreview(ServerPlayer player, ItemStack stack) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(stack, "stack");
        var kind = PsCarrierItems.kindOf(stack);
        CarrierIdentity identity = stack.get(PsCarrierComponents.IDENTITY.get());
        CarrierStackState state = stack.get(PsCarrierComponents.STATE.get());
        if (kind.isEmpty() || identity == null || state == null) {
            return MigrationPreview.rejected("Held item is not a complete progression carrier");
        }
        BehaviorArchiveSavedData archive = BehaviorArchiveSavedData.get(player.getServer());
        Optional<CarrierBehaviorSnapshot> archivedValue = archive.resolve(identity.behaviorDigest());
        if (archivedValue.isEmpty()) {
            return MigrationPreview.rejected("Carrier behavior is not present in the archive");
        }
        CarrierBehaviorSnapshot archived = archivedValue.orElseThrow();
        if (!archived.definitionId().equals(identity.definitionId())
                || archived.carrier() != kind.orElseThrow()
                || archived.behaviorVersion() != state.behaviorVersion()
                || state.charges() < 1
                || state.charges() > archived.charges()) {
            return MigrationPreview.rejected("Carrier identity and archived behavior do not match");
        }
        if (state.boundOwner().isPresent()
                && !state.boundOwner().orElseThrow().equals(player.getUUID())) {
            return MigrationPreview.rejected("Carrier is bound to another player");
        }
        Optional<CarrierBehaviorSnapshot> currentValue = currentBehavior(identity.definitionId());
        if (currentValue.isEmpty()) {
            return MigrationPreview.rejected("Live carrier behavior is unavailable");
        }
        CarrierBehaviorSnapshot current = currentValue.orElseThrow();
        if (current.carrier() != archived.carrier()) {
            return MigrationPreview.rejected("Live carrier kind differs from its archived kind");
        }
        if (stack.getCount() > current.stackSize()) {
            return MigrationPreview.rejected("Carrier count exceeds the live stack bound");
        }
        if (current.migrationPolicy() != CarrierMigrationPolicy.MIGRATE) {
            return MigrationPreview.rejected("Live carrier behavior does not permit explicit migration");
        }
        if (current.digest().equals(identity.behaviorDigest())) {
            return MigrationPreview.rejected("Carrier already uses the live behavior");
        }
        int nextCharges = Math.min(state.charges(), current.charges());
        if (nextCharges < 1) {
            return MigrationPreview.rejected("Carrier has no charges to migrate");
        }
        CarrierStackState nextState = state.migrated(
                current.behaviorVersion(),
                nextCharges,
                "v" + archived.behaviorVersion() + "_to_v" + current.behaviorVersion()
        );
        if ((current.bindPolicy() == CarrierBindPolicy.ON_PICKUP
                || current.bindPolicy() == CarrierBindPolicy.ON_CRAFT
                || current.bindPolicy() == CarrierBindPolicy.ON_USE)
                && nextState.boundOwner().isEmpty()) {
            nextState = nextState.bind(player.getUUID());
        }
        CarrierIdentity nextIdentity = new CarrierIdentity(current.definitionId(), current.digest());
        String previewDigest = migrationDigest(identity, state, nextIdentity, nextState, stack.getCount());
        return new MigrationPreview(
                true,
                previewDigest,
                "Carrier migration is ready",
                Optional.of(nextIdentity),
                Optional.of(nextState),
                Optional.of(current)
        );
    }

    public static MigrationResult migrate(
            ServerPlayer player,
            ItemStack stack,
            String expectedPreviewDigest
    ) {
        Objects.requireNonNull(expectedPreviewDigest, "expectedPreviewDigest");
        MigrationPreview preview = migratePreview(player, stack);
        if (!preview.allowed()) {
            return new MigrationResult(false, preview.message(), Optional.empty());
        }
        if (!preview.previewDigest().equals(expectedPreviewDigest)) {
            return new MigrationResult(false, "Carrier migration preview is stale", Optional.empty());
        }
        BehaviorArchiveSavedData.get(player.getServer()).reserve(
                java.util.List.of(preview.behavior().orElseThrow())
        );
        stack.set(PsCarrierComponents.IDENTITY.get(), preview.identity().orElseThrow());
        stack.set(PsCarrierComponents.STATE.get(), preview.state().orElseThrow());
        applyCosmetics(stack, preview.behavior().orElseThrow());
        return new MigrationResult(true, "Carrier migrated", Optional.of(stack.copy()));
    }

    static ItemStack afterCommittedUse(
            ItemStack original,
            CarrierBehaviorSnapshot behavior,
            CarrierStackState afterState
    ) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(behavior, "behavior");
        Objects.requireNonNull(afterState, "afterState");
        if (afterState.charges() > 0) {
            ItemStack result = original.copy();
            result.set(PsCarrierComponents.STATE.get(), afterState);
            return result;
        }
        if (original.getCount() <= 1) {
            return ItemStack.EMPTY;
        }
        ItemStack result = original.copyWithCount(original.getCount() - 1);
        CarrierStackState next = CarrierStackState.fresh(
                behavior.behaviorVersion(),
                behavior.charges(),
                afterState.creationPackDigest(),
                UUID.randomUUID()
        );
        if (afterState.boundOwner().isPresent()) {
            next = next.bind(afterState.boundOwner().orElseThrow());
        }
        result.set(PsCarrierComponents.STATE.get(), next);
        return result;
    }

    static Optional<CarrierBehaviorSnapshot> currentBehavior(net.minecraft.resources.ResourceLocation id) {
        return currentDefinition(id).map(CarrierDefinition::behaviorSnapshot);
    }

    static Optional<CarrierDefinition> currentDefinition(net.minecraft.resources.ResourceLocation id) {
        return PackRuntime.service().filter(service -> service.live().generation() > 0).flatMap(service -> {
            var canonical = service.live().snapshot().canonicalIr();
            SkillCatalog skills = SkillCatalog.from(canonical);
            TreeCatalog trees = TreeCatalog.from(canonical, skills);
            return CarrierCatalog.from(canonical, skills, trees).carrier(id);
        }).filter(CarrierDefinition::enabled);
    }

    private static ItemStack stack(
            CarrierBehaviorSnapshot behavior,
            CarrierStackState state,
            int count
    ) {
        var stack = new ItemStack(PsCarrierItems.item(behavior.carrier()), count);
        stack.set(
                PsCarrierComponents.IDENTITY.get(),
                new CarrierIdentity(behavior.definitionId(), behavior.digest())
        );
        stack.set(PsCarrierComponents.STATE.get(), state);
        applyCosmetics(stack, behavior);
        return stack;
    }

    private static void applyCosmetics(ItemStack stack, CarrierBehaviorSnapshot behavior) {
        stack.set(DataComponents.MAX_STACK_SIZE, behavior.stackSize());
        currentDefinition(behavior.definitionId())
                .filter(definition -> definition.carrier() == behavior.carrier())
                .ifPresent(definition -> {
                    stack.set(
                            DataComponents.CUSTOM_NAME,
                            Component.literal(definition.presentation().display().fallback())
                    );
                    if (definition.presentation().description().isPresent()) {
                        stack.set(
                                DataComponents.LORE,
                                new ItemLore(java.util.List.of(Component.literal(
                                        definition.presentation().description().orElseThrow().fallback()
                                )))
                        );
                    } else {
                        stack.remove(DataComponents.LORE);
                    }
                    stack.set(DataComponents.RARITY, switch (definition.rarity()) {
                        case COMMON -> Rarity.COMMON;
                        case UNCOMMON -> Rarity.UNCOMMON;
                        case RARE -> Rarity.RARE;
                        case EPIC -> Rarity.EPIC;
                    });
                    stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, definition.glint());
                });
    }

    private static String migrationDigest(
            CarrierIdentity identity,
            CarrierStackState state,
            CarrierIdentity nextIdentity,
            CarrierStackState nextState,
            int stackCount
    ) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                write(output, "progressiveskills-carrier-migration-v1");
                write(output, identity.definitionId().toString());
                write(output, identity.behaviorDigest());
                output.writeInt(state.dataVersion());
                write(output, state.creationPackDigest());
                write(output, state.instanceId().toString());
                output.writeLong(state.useCounter());
                output.writeInt(state.behaviorVersion());
                output.writeInt(state.charges());
                write(output, state.boundOwner().map(UUID::toString).orElse(""));
                write(output, state.migrationMarker().orElse(""));
                write(output, nextIdentity.definitionId().toString());
                write(output, nextIdentity.behaviorDigest());
                output.writeInt(nextState.dataVersion());
                write(output, nextState.creationPackDigest());
                write(output, nextState.instanceId().toString());
                output.writeLong(nextState.useCounter());
                output.writeInt(nextState.behaviorVersion());
                output.writeInt(nextState.charges());
                write(output, nextState.boundOwner().map(UUID::toString).orElse(""));
                write(output, nextState.migrationMarker().orElse(""));
                output.writeInt(stackCount);
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in memory migration digest failure", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    public record Inspection(
            Optional<CarrierIdentity> identity,
            Optional<CarrierStackState> state,
            Optional<CarrierBehaviorSnapshot> archivedBehavior,
            Optional<CarrierBehaviorSnapshot> currentBehavior,
            Optional<CarrierBehaviorResolver.Resolution> resolution,
            String message
    ) {
        public Inspection {
            identity = Objects.requireNonNull(identity, "identity");
            state = Objects.requireNonNull(state, "state");
            archivedBehavior = Objects.requireNonNull(archivedBehavior, "archivedBehavior");
            currentBehavior = Objects.requireNonNull(currentBehavior, "currentBehavior");
            resolution = Objects.requireNonNull(resolution, "resolution");
            message = Objects.requireNonNull(message, "message");
        }
    }

    public record MigrationPreview(
            boolean allowed,
            String previewDigest,
            String message,
            Optional<CarrierIdentity> identity,
            Optional<CarrierStackState> state,
            Optional<CarrierBehaviorSnapshot> behavior
    ) {
        public MigrationPreview {
            previewDigest = Objects.requireNonNull(previewDigest, "previewDigest");
            message = Objects.requireNonNull(message, "message");
            identity = Objects.requireNonNull(identity, "identity");
            state = Objects.requireNonNull(state, "state");
            behavior = Objects.requireNonNull(behavior, "behavior");
        }

        private static MigrationPreview rejected(String message) {
            return new MigrationPreview(
                    false, "0".repeat(64), message,
                    Optional.empty(), Optional.empty(), Optional.empty()
            );
        }
    }

    public record MigrationResult(boolean migrated, String message, Optional<ItemStack> stack) {
        public MigrationResult {
            message = Objects.requireNonNull(message, "message");
            stack = Objects.requireNonNull(stack, "stack");
        }
    }
}
