package me.alexisbinh.openlootr.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.alexisbinh.openlootr.OpenLootrPlugin;
import me.alexisbinh.openlootr.storage.StorageHealth;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class InfoCommand {
    private InfoCommand() {
    }

    public static int execute(CommandSourceStack source) {
        Plugin resolved = Bukkit.getPluginManager().getPlugin("OpenLootr");
        if (!(resolved instanceof OpenLootrPlugin plugin) || !plugin.isEnabled()) {
            source.getSender().sendMessage(Component.text("[OpenLootr] Plugin is unavailable."));
            return 0;
        }
        StorageHealth health = plugin.storageHealth();
        source.getSender().sendMessage(Component.text("[OpenLootr] " + plugin.getPluginMeta().getVersion()));
        source.getSender().sendMessage(Component.text("Runtime: "
                + (plugin.scheduler().isFolia() ? "Folia" : "Paper")
                + " / " + plugin.runtimeState()));
        source.getSender().sendMessage(Component.text("SQLite: "
                + (health.initialized() ? "healthy" : "unavailable")
                + ", schema=" + health.schemaVersion()
                + ", journal=" + health.journalMode()
                + ", synchronous=" + health.synchronousMode()
                + ", queued=" + plugin.dbExecutor().queuedTasks()));
        source.getSender().sendMessage(Component.text("Database: " + health.databasePath()));
        return health.initialized() ? 1 : 0;
    }
}
