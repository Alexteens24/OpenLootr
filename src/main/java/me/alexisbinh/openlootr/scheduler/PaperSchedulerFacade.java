package me.alexisbinh.openlootr.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class PaperSchedulerFacade implements SchedulerFacade {
    private final Plugin plugin;
    private final boolean folia;

    public PaperSchedulerFacade(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.folia = detectFolia();
    }

    @Override
    public void executeGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void executeAt(Location location, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, location, task);
    }

    @Override
    public void executeFor(Entity entity, Runnable task, Runnable retired) {
        entity.getScheduler().execute(plugin, task, retired, 1L);
    }

    @Override
    public void executeAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    @Override
    public void executeAsyncLater(Runnable task, Duration delay) {
        long millis = Math.max(1L, delay.toMillis());
        Bukkit.getAsyncScheduler().runDelayed(plugin, ignored -> task.run(), millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void cancelPluginTasks() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }

    @Override
    public boolean isFolia() {
        return folia;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
