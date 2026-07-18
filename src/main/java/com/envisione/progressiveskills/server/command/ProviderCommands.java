package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.provider.CapabilityProfile;
import com.envisione.progressiveskills.common.provider.ProviderCapability;
import com.envisione.progressiveskills.server.provider.ProviderRuntime;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Set;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class ProviderCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private ProviderCommands() {
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("compatibility")
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("profile")
                                .then(Commands.literal("list").executes(context -> profiles(
                                        context.getSource())))
                                .then(Commands.literal("active").executes(context -> activeProfile(
                                        context.getSource())))
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(context -> profile(context.getSource(),
                                                ResourceLocationArgument.getId(context, "id"))))
                                .then(Commands.literal("strict").executes(context -> profile(
                                        context.getSource(), CapabilityProfile.Mode.STRICT)))
                                .then(Commands.literal("preferred").executes(context -> profile(
                                        context.getSource(), CapabilityProfile.Mode.PREFERRED)))
                                .then(Commands.literal("fallback").executes(context -> profile(
                                        context.getSource(), CapabilityProfile.Mode.FALLBACK)))))
                .then(Commands.literal("diagnose")
                        .executes(context -> status(context.getSource()))));
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        var registry = ProviderRuntime.registry();
        if (registry.isEmpty()) {
            source.sendFailure(Component.literal(PREFIX + "Provider registry is unavailable."));
            return 0;
        }
        registry.orElseThrow().probeAll().forEach((id, value) -> source.sendSuccess(() -> Component.literal(
                PREFIX + id + ". Version " + value.version() + ". Health " + value.health().status()
                        + ". Circuit " + value.circuit() + ". " + value.health().message() + "."), false));
        ProviderRuntime.adapterErrors().forEach((id, message) -> source.sendFailure(Component.literal(
                PREFIX + "Adapter " + id + ". " + message + ".")));
        ProviderRuntime.activePlan().ifPresent(plan -> source.sendSuccess(() -> Component.literal(
                PREFIX + "Active pack profile. Usable " + plan.usable() + ". Selected "
                        + plan.selectedProviders() + ". Missing " + plan.unavailable() + "."), false));
        return 1;
    }

    private static int profiles(net.minecraft.commands.CommandSourceStack source) {
        var profiles = ProviderRuntime.profiles().profiles();
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal(PREFIX + "No pack compatibility profiles are loaded."));
            return 0;
        }
        profiles.forEach((id, entry) -> source.sendSuccess(() -> Component.literal(
                PREFIX + id + ". Mode " + entry.profile().mode() + ". Required "
                        + entry.profile().required() + ". Preferred " + entry.profile().preferred()
                        + ". Active " + entry.active() + "."), false));
        return profiles.size();
    }

    private static int activeProfile(net.minecraft.commands.CommandSourceStack source) {
        var active = ProviderRuntime.profiles().active();
        if (active.isEmpty()) {
            source.sendFailure(Component.literal(PREFIX + "No pack compatibility profile is active."));
            return 0;
        }
        return profile(source, active.orElseThrow().profile().id());
    }

    private static int profile(
            net.minecraft.commands.CommandSourceStack source,
            ResourceLocation id
    ) {
        var registry = ProviderRuntime.registry();
        var profile = ProviderRuntime.profiles().find(id);
        if (registry.isEmpty() || profile.isEmpty()) {
            source.sendFailure(Component.literal(PREFIX + "Compatibility profile is unavailable."));
            return 0;
        }
        var plan = registry.orElseThrow().resolve(profile.orElseThrow().profile());
        source.sendSuccess(() -> Component.literal(PREFIX + "Profile " + id + ". Mode "
                + profile.orElseThrow().profile().mode() + ". Usable " + plan.usable()
                + ". Selected " + plan.selectedProviders() + ". Missing " + plan.unavailable()
                + ". Fallback " + plan.usingFallback() + "."), false);
        return plan.usable() ? 1 : 0;
    }

    private static int profile(
            net.minecraft.commands.CommandSourceStack source,
            CapabilityProfile.Mode mode
    ) {
        var registry = ProviderRuntime.registry();
        if (registry.isEmpty()) {
            source.sendFailure(Component.literal(PREFIX + "Provider registry is unavailable."));
            return 0;
        }
        CapabilityProfile profile = new CapabilityProfile(
                ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "preview"),
                mode,
                Set.of(ProviderCapability.VANILLA_ATTRIBUTES),
                Set.of(
                        ProviderCapability.STAGE_OWNERSHIP,
                        ProviderCapability.SPELL_OWNERSHIP,
                        ProviderCapability.PARTY_MEMBERSHIP,
                        ProviderCapability.CARRIER_SLOTS
                )
        );
        var plan = registry.orElseThrow().resolve(profile);
        source.sendSuccess(() -> Component.literal(PREFIX + "Profile " + mode + ". Usable " + plan.usable()
                + ". Selected " + plan.selectedProviders() + ". Missing " + plan.unavailable()
                + ". Fallback " + plan.usingFallback() + "."), false);
        return plan.usable() ? 1 : 0;
    }
}
