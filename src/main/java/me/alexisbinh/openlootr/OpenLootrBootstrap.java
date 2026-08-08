package me.alexisbinh.openlootr;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.alexisbinh.openlootr.command.InfoCommand;
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
                            .build(),
                    "OpenLootr administration",
                    List.of("ol")
            );
        });
    }
}
