package me.alexisbinh.openlootr.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PaperSchedulerFacade implements SchedulerFacade {
    private final Plugin plugin;
    private final boolean folia;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public PaperSchedulerFacade(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.folia = detectFolia();
    }

    @Override
    public void executeGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, guarded(task));
    }

    @Override
    public void executeAt(Location location, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, location, guarded(task));
    }

    @Override
    public void executeFor(Entity entity, Runnable task, Runnable retired) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(retired, "retired");
        AtomicBoolean terminalRetired = new AtomicBoolean();
        Runnable retireOnce = () -> {
            if (terminalRetired.compareAndSet(false, true)) {
                retired.run();
            }
        };
        if (!accepting.get()) {
            retireOnce.run();
            return;
        }
        boolean scheduled = entity.getScheduler().execute(plugin, () -> {
            if (accepting.get()) {
                task.run();
            } else {
                retireOnce.run();
            }
        }, retireOnce, 1L);
        if (!scheduled) {
            retireOnce.run();
        }
    }

    @Override
    public void executeAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> guarded(task).run());
    }

    @Override
    public void executeAsyncLater(Runnable task, Duration delay) {
        long millis = Math.max(1L, delay.toMillis());
        Bukkit.getAsyncScheduler().runDelayed(plugin, ignored -> guarded(task).run(), millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void cancelPluginTasks() {
        stopAccepting();
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }

    @Override
    public void stopAccepting() {
        accepting.set(false);
    }

    @Override
    public boolean isFolia() {
        return folia;
    }

    @Override
    public boolean accepting() { return accepting.get(); }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private Runnable guarded(Runnable task) {
        Objects.requireNonNull(task, "task");
        return () -> {
            if (accepting.get()) {
                task.run();
            }
        };
    }
}
