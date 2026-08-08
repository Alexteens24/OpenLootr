package me.alexisbinh.openlootr;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.alexisbinh.openlootr.command.InfoCommand;
import me.alexisbinh.openlootr.command.InspectCommand;
import me.alexisbinh.openlootr.command.DebugCommand;
import me.alexisbinh.openlootr.command.RepairCommand;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class OpenLootrBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    Commands.literal("openlootr")
                            .then(Commands.literal("info")
                                    .requires(source -> source.getSender()
                                            .hasPermission("openlootr.command.info"))
                                    .executes(command -> InfoCommand.execute(command.getSource())))
                            .then(Commands.literal("inspect")
                                    .requires(source -> source.getSender()
                                            .hasPermission("openlootr.command.inspect"))
                                    .executes(command -> InspectCommand.execute(command.getSource())))
                            .then(Commands.literal("debug")
                                    .requires(source -> source.getSender()
                                            .hasPermission("openlootr.command.debug"))
                                    .executes(command -> DebugCommand.execute(command.getSource())))
                            .then(Commands.literal("repair")
                                    .requires(source -> source.getSender()
                                            .hasPermission("openlootr.command.repair"))
                                    .executes(command -> RepairCommand.plan(command.getSource()))
                                    .then(Commands.literal("confirm")
                                            .then(Commands.argument("nonce", StringArgumentType.word())
                                                    .executes(command -> RepairCommand.confirm(command.getSource(),
                                                            StringArgumentType.getString(command, "nonce"))))))
                            .build(),
                    "OpenLootr administration",
                    List.of("ol")
            );
        });
    }
}
