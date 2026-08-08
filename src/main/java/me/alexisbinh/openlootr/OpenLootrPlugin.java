package me.alexisbinh.openlootr;

import me.alexisbinh.openlootr.runtime.RuntimeState;
import me.alexisbinh.openlootr.scheduler.PaperSchedulerFacade;
import me.alexisbinh.openlootr.scheduler.SchedulerFacade;
import me.alexisbinh.openlootr.storage.DbExecutor;
import me.alexisbinh.openlootr.storage.SqliteLootStorage;
import me.alexisbinh.openlootr.storage.StorageHealth;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class OpenLootrPlugin extends JavaPlugin {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private volatile RuntimeState runtimeState = RuntimeState.NEW;
    private SchedulerFacade scheduler;
    private DbExecutor dbExecutor;
    private SqliteLootStorage storage;

    @Override
    public void onEnable() {
        runtimeState = RuntimeState.STARTING;
        scheduler = new PaperSchedulerFacade(this);
        dbExecutor = new DbExecutor();
        storage = new SqliteLootStorage(getDataFolder().toPath().resolve("openlootr.db"));

        try {
            dbExecutor.run(storage::initialize).get(15, TimeUnit.SECONDS);
            runtimeState = RuntimeState.RUNNING;
            getSLF4JLogger().info("OpenLootr {} enabled on {} with SQLite schema {}",
                    getPluginMeta().getVersion(), scheduler.isFolia() ? "Folia" : "Paper",
                    storage.health().schemaVersion());
        } catch (Exception exception) {
            runtimeState = RuntimeState.FAILED;
            getSLF4JLogger().error("Failed to initialize OpenLootr; disabling plugin", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (runtimeState == RuntimeState.STOPPED) {
            return;
        }
        runtimeState = RuntimeState.STOPPING;
        if (scheduler != null) {
            scheduler.cancelPluginTasks();
        }
        if (dbExecutor != null && storage != null) {
            try {
                dbExecutor.run(storage::close).get(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception exception) {
                getSLF4JLogger().error("Timed out or failed while closing SQLite", exception);
            }
            if (!dbExecutor.shutdown(SHUTDOWN_TIMEOUT)) {
                getSLF4JLogger().error("Database executor did not terminate cleanly");
            }
        }
        runtimeState = RuntimeState.STOPPED;
    }

    public RuntimeState runtimeState() {
        return runtimeState;
    }

    public SchedulerFacade scheduler() {
        return scheduler;
    }

    public DbExecutor dbExecutor() {
        return dbExecutor;
    }

    public StorageHealth storageHealth() {
        return storage == null
                ? StorageHealth.unavailable(getDataFolder().toPath().resolve("openlootr.db").toString())
                : storage.health();
    }
}
