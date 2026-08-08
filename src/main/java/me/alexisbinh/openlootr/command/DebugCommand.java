package me.alexisbinh.openlootr.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.alexisbinh.openlootr.OpenLootrPlugin;
import me.alexisbinh.openlootr.instance.PersistenceHealth;
import net.kyori.adventure.text.Component;

public final class DebugCommand {
    private DebugCommand() { }

    public static int execute(CommandSourceStack source) {
        OpenLootrPlugin plugin = OpenLootrPlugin.instance();
        if (plugin == null || !plugin.isEnabled()) {
            source.getSender().sendMessage(Component.text("[OpenLootr] Plugin is unavailable."));
            return 0;
        }
        var cache = plugin.instanceCache();
        var storage = plugin.storageHealth();
        source.getSender().sendMessage(Component.text("[OpenLootr] Runtime diagnostics"));
        source.getSender().sendMessage(Component.text("cache=" + cache.size()
                + ", sessions=" + plugin.sessions().size()
                + ", inflight=" + plugin.lootService().inFlightCount()
                + ", attempts=" + plugin.lootService().pendingAttemptCount()));
        source.getSender().sendMessage(Component.text("dirty=" + plugin.saves().pendingCount()
                + ", writing=" + plugin.saves().writingCount()
                + ", acceptingMutations=" + plugin.saves().acceptingMutations()));
        source.getSender().sendMessage(Component.text("degraded=" + cache.count(PersistenceHealth.DEGRADED)
                + ", quarantined=" + cache.count(PersistenceHealth.QUARANTINED)
                + ", corrupt=" + cache.count(PersistenceHealth.CORRUPT)));
        source.getSender().sendMessage(Component.text("dbQueue=" + plugin.dbExecutor().queuedTasks()
                + ", schema=" + storage.schemaVersion() + ", WAL=" + storage.journalMode()
                + ", sync=" + storage.synchronousMode()));
        source.getSender().sendMessage(Component.text("scheduler="
                + (plugin.scheduler().isFolia() ? "Folia" : "Paper")
                + ", gate=" + (plugin.scheduler().accepting() ? "open" : "closed")
                + ", nms=" + plugin.parityBridge().status()));
        return 1;
    }
}
