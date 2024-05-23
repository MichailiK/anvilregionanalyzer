package dev.michaili.anvilregionanalyzer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnvilRegionCommand {
    public static final Logger LOGGER = LoggerFactory.getLogger("anvilregionanalyzer");

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("anvilregion").requires(source -> source.hasPermissionLevel(4)).then(CommandManager.literal("profile").executes(context -> {
                var world = context.getSource().getWorld();
                if (world == null) {
                    throw new SimpleCommandExceptionType(Text.literal("Must be run in a world")).create();
                }

                var result = AnvilRegionProfiler.toggleProfiling(world);
                if (result) {
                    context.getSource().sendFeedback(() -> Text.literal("Starting Anvil profiling in " + world.getRegistryKey().getValue().toString()), true);
                } else {
                    context.getSource().sendFeedback(() -> Text.literal("Stopping Anvil profiling in " + world.getRegistryKey().getValue().toString()), true);
                }

                return Command.SINGLE_SUCCESS;
            }).then(CommandManager.argument("dimension", DimensionArgumentType.dimension()).executes(context -> {
                var world = DimensionArgumentType.getDimensionArgument(context, "dimension");
                var result = AnvilRegionProfiler.toggleProfiling(world);
                if (result) {
                    context.getSource().sendFeedback(() -> Text.literal("Starting Anvil profiling in " + world.getRegistryKey().getValue().toString()), true);
                } else {
                    context.getSource().sendFeedback(() -> Text.literal("Stopping Anvil profiling in " + world.getRegistryKey().getValue().toString()), true);
                }

                return Command.SINGLE_SUCCESS;
            }))));
        });
    }
}
