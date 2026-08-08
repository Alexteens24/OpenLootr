package me.alexisbinh.openlootr.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.time.Duration;

public interface SchedulerFacade {
    void executeGlobal(Runnable task);

    void executeAt(Location location, Runnable task);

    void executeFor(Entity entity, Runnable task, Runnable retired);

    void executeAsync(Runnable task);

    void executeAsyncLater(Runnable task, Duration delay);

    void cancelPluginTasks();

    boolean isFolia();
}
