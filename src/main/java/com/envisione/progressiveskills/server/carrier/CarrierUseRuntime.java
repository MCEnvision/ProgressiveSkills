package com.envisione.progressiveskills.server.carrier;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.carrier.CarrierBehaviorSnapshot;
import com.envisione.progressiveskills.common.carrier.CarrierCatalog;
import com.envisione.progressiveskills.common.carrier.CarrierIdentity;
import com.envisione.progressiveskills.common.carrier.CarrierStackState;
import com.envisione.progressiveskills.common.carrier.CarrierUseReservationStatus;
import com.envisione.progressiveskills.common.carrier.PsCarrierComponents;
import com.envisione.progressiveskills.common.carrier.PsCarrierItems;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Objects;
import java.util.Optional;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class CarrierUseRuntime {
    private CarrierUseRuntime() {
    }

    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || PsCarrierItems.kindOf(event.getItemStack()).isEmpty()) {
            return;
        }
        event.setCanceled(true);
        UseOutcome outcome = use(player, event.getHand());
        if (outcome.committed()) {
            outcome.warning().ifPresent(message -> player.sendSystemMessage(Component.literal(message)));
            player.displayClientMessage(Component.literal(outcome.message()), true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        } else {
            player.sendSystemMessage(Component.literal(outcome.message()));
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    public static UseOutcome use(ServerPlayer player, InteractionHand hand) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(hand, "hand");
        ItemStack original = player.getItemInHand(hand);
        var kind = PsCarrierItems.kindOf(original);
        if (kind.isEmpty()) {
            return rejected(Code.NOT_A_CARRIER, "Held item is not a progression carrier");
        }
        CarrierIdentity identity = original.get(PsCarrierComponents.IDENTITY.get());
        CarrierStackState state = original.get(PsCarrierComponents.STATE.get());
        if (identity == null || state == null) {
            return rejected(Code.INCOMPLETE_STACK, "Carrier identity or state is missing");
        }
        if (player.isCreative()) {
            return rejected(Code.CREATIVE_DENIED, "Progression carriers cannot be used in creative mode");
        }
        if (player.getCooldowns().isOnCooldown(original.getItem())) {
            return rejected(Code.COOLDOWN, "Carrier is still on cooldown");
        }
        CarrierBehaviorResolver.Resolution resolution;
        try {
            BehaviorArchiveSavedData archive = BehaviorArchiveSavedData.get(player.getServer());
            resolution = CarrierBehaviorResolver.resolve(
                    identity,
                    state,
                    kind.orElseThrow(),
                    player.getUUID(),
                    archive,
                    CarrierStackService.currentBehavior(identity.definitionId())
            );
        } catch (RuntimeException exception) {
            return rejected(Code.BEHAVIOR_REJECTED, message(exception));
        }
        if (!resolution.allowed()) {
            return rejected(Code.BEHAVIOR_REJECTED, resolution.message());
        }
        CarrierBehaviorSnapshot behavior = resolution.behavior().orElseThrow();
        if (original.getCount() > behavior.stackSize()) {
            return rejected(Code.STACK_TAMPER, "Carrier count exceeds its archived stack bound");
        }
        ProgressiveSkillsData data = player.getData(PsDataAttachments.PLAYER_DATA);
        if (!data.active()) {
            return rejected(Code.PLAYER_DATA_UNAVAILABLE, "Player progression data is quarantined");
        }
        CarrierUseReservationStatus reservation;
        try {
            reservation = data.reserveCarrierUse(state.instanceId(), state.useCounter());
        } catch (RuntimeException exception) {
            return rejected(Code.USE_COUNTER_REJECTED, message(exception));
        }
        if (reservation != CarrierUseReservationStatus.RESERVED) {
            return rejected(Code.USE_COUNTER_REJECTED, switch (reservation) {
                case REPLAYED -> "Carrier use counter was already consumed";
                case SKIPPED -> "Carrier use counter skipped an expected value";
                case CAPACITY_FULL -> "Carrier use ledger capacity is full";
                case ALREADY_RESERVED -> "Carrier use is already being processed";
                case RESERVED -> throw new IllegalStateException("Reserved use was rejected");
            });
        }
        TransactionRuntime.Context transactions;
        DefinitionRevision definition;
        CarrierActionPlanCompiler.CompiledUse compiled;
        ItemStack afterStack;
        try {
            transactions = TransactionRuntime.context(player.getServer())
                    .orElseThrow(() -> new IllegalStateException("Transaction runtime is unavailable"));
            if (!transactions.ready(player)) {
                throw new IllegalStateException("Player transaction state is unavailable");
            }
            definition = TransactionRuntime.currentDefinition()
                    .orElseThrow(() -> new IllegalStateException("Live definitions are unavailable"));
            Catalogs catalogs = catalogs();
            compiled = CarrierActionPlanCompiler.compile(
                    player.getUUID(),
                    behavior,
                    state,
                    catalogs.skills(),
                    catalogs.trees(),
                    transactions.service(),
                    definition
            );
            afterStack = CarrierStackService.afterCommittedUse(
                    original,
                    behavior,
                    resolution.afterState().orElseThrow()
            );
        } catch (RuntimeException exception) {
            data.cancelCarrierUse(state.instanceId(), state.useCounter());
            return rejected(Code.PLANNING_REJECTED, message(exception));
        }
        TransactionResult transaction;
        try {
            transaction = transactions.executeAndPersist(
                    player, compiled.cascade(), definition
            );
        } catch (RuntimeException exception) {
            data.cancelCarrierUse(state.instanceId(), state.useCounter());
            return rejected(Code.TRANSACTION_REJECTED, message(exception));
        }
        if (!transaction.status().committed()) {
            data.cancelCarrierUse(state.instanceId(), state.useCounter());
            return rejected(
                    Code.TRANSACTION_REJECTED,
                    "Carrier use was rejected. " + transaction.message()
            );
        }
        data.commitCarrierUse(state.instanceId(), state.useCounter());
        player.setItemInHand(hand, afterStack);
        if (behavior.cooldownTicks() > 0) {
            player.getCooldowns().addCooldown(original.getItem(), behavior.cooldownTicks());
        }
        int remaining = resolution.afterState().orElseThrow().charges();
        return new UseOutcome(
                Code.COMMITTED,
                true,
                transaction.replayed(),
                "Carrier used. Charges remaining " + remaining + ".",
                resolution.warning()
        );
    }

    private static Catalogs catalogs() {
        var service = PackRuntime.service().filter(value -> value.live().generation() > 0)
                .orElseThrow(() -> new IllegalStateException("Pack runtime is unavailable"));
        var canonical = service.live().snapshot().canonicalIr();
        SkillCatalog skills = SkillCatalog.from(canonical);
        TreeCatalog trees = TreeCatalog.from(canonical, skills);
        CarrierCatalog.from(canonical, skills, trees);
        return new Catalogs(skills, trees);
    }

    private static UseOutcome rejected(Code code, String message) {
        return new UseOutcome(code, false, false, message, Optional.empty());
    }

    private static String message(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Carrier use failed safely";
        }
        return message.substring(0, Math.min(message.length(), 256));
    }

    public enum Code {
        COMMITTED,
        NOT_A_CARRIER,
        INCOMPLETE_STACK,
        CREATIVE_DENIED,
        COOLDOWN,
        BEHAVIOR_REJECTED,
        STACK_TAMPER,
        PLAYER_DATA_UNAVAILABLE,
        USE_COUNTER_REJECTED,
        PLANNING_REJECTED,
        TRANSACTION_REJECTED
    }

    public record UseOutcome(
            Code code,
            boolean committed,
            boolean replayedTransaction,
            String message,
            Optional<String> warning
    ) {
        public UseOutcome {
            Objects.requireNonNull(code, "code");
            message = Objects.requireNonNull(message, "message");
            warning = Objects.requireNonNull(warning, "warning");
        }
    }

    private record Catalogs(SkillCatalog skills, TreeCatalog trees) {
    }
}
