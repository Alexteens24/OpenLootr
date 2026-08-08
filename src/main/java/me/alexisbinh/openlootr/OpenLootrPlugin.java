package me.alexisbinh.openlootr;

import me.alexisbinh.openlootr.runtime.RuntimeState;
import me.alexisbinh.openlootr.codec.ContainerCodec;
import me.alexisbinh.openlootr.paper.container.ContainerResolver;
import me.alexisbinh.openlootr.instance.FirstOpenService;
import me.alexisbinh.openlootr.instance.InstanceCache;
import me.alexisbinh.openlootr.instance.SaveCoordinator;
import me.alexisbinh.openlootr.listener.ContainerProtectionListener;
import me.alexisbinh.openlootr.listener.PersonalLootInteractionListener;
import me.alexisbinh.openlootr.loot.GenerationEventTracker;
import me.alexisbinh.openlootr.loot.PaperLootGenerator;
import me.alexisbinh.openlootr.paper.container.PaperContainerResolver;
import me.alexisbinh.openlootr.paper.menu.PaperMenuFactory;
import me.alexisbinh.openlootr.scheduler.PaperSchedulerFacade;
import me.alexisbinh.openlootr.scheduler.SchedulerFacade;
import me.alexisbinh.openlootr.storage.DbExecutor;
import me.alexisbinh.openlootr.storage.SqliteLootStorage;
import me.alexisbinh.openlootr.storage.StorageHealth;
import me.alexisbinh.openlootr.paper.session.SessionManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class OpenLootrPlugin extends JavaPlugin {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SESSION_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private static volatile OpenLootrPlugin instance;

    private volatile RuntimeState runtimeState = RuntimeState.NEW;
    private SchedulerFacade scheduler;
    private DbExecutor dbExecutor;
    private SqliteLootStorage storage;
    private ContainerResolver containerResolver;
    private PersonalLootInteractionListener interactions;
    private SessionManager sessions;
    private SaveCoordinator saves;

    @Override
    public void onEnable() {
        instance = this;
        runtimeState = RuntimeState.STARTING;
        scheduler = new PaperSchedulerFacade(this);
        dbExecutor = new DbExecutor();
        storage = new SqliteLootStorage(getDataFolder().toPath().resolve("openlootr.db"));

        try {
            dbExecutor.run(storage::initialize).get(15, TimeUnit.SECONDS);
            wireGameplay();
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
        if (interactions != null) {
            interactions.stopAccepting();
        }
        if (sessions != null) {
            try {
                if (scheduler.isFolia()) {
                    sessions.snapshotAll().get(SESSION_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                } else {
                    sessions.snapshotAllNow();
                }
            } catch (Exception exception) {
                getSLF4JLogger().error("Timed out or failed while snapshotting active loot sessions", exception);
            }
        }
        if (saves != null) {
            long deadline = System.nanoTime() + SESSION_SHUTDOWN_TIMEOUT.toNanos();
            saves.flushAll();
            while (saves.pendingCount() > 0 && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
                saves.flushAll();
            }
            if (saves.pendingCount() > 0) {
                getSLF4JLogger().error("Shutdown deadline reached with {} dirty loot instances retained in memory",
                        saves.pendingCount());
            }
            saves.stopAccepting();
        }
        if (scheduler != null) {
            // Region/entity tasks cannot all be cancelled by plugin handle. Their guarded callbacks
            // must become no-ops before SQLite begins closing.
            scheduler.stopAccepting();
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
        if (scheduler != null) {
            scheduler.cancelPluginTasks();
        }
        runtimeState = RuntimeState.STOPPED;
        instance = null;
    }

    private void wireGameplay() {
        ContainerCodec codec = new ContainerCodec();
        InstanceCache cache = new InstanceCache();
        containerResolver = new PaperContainerResolver(this);
        GenerationEventTracker generationEvents = new GenerationEventTracker();
        sessions = new SessionManager(new PaperMenuFactory(this), codec, scheduler, cache);
        saves = new SaveCoordinator(storage, dbExecutor, scheduler, getSLF4JLogger(), sessions::degrade);
        sessions.attachSaveCoordinator(saves);
        interactions = new PersonalLootInteractionListener(containerResolver,
                new FirstOpenService(storage, dbExecutor), new PaperLootGenerator(codec, generationEvents),
                codec, cache, sessions, scheduler, getSLF4JLogger());

        var plugins = getServer().getPluginManager();
        plugins.registerEvents(generationEvents, this);
        plugins.registerEvents(new ContainerProtectionListener(containerResolver), this);
        plugins.registerEvents(sessions, this);
        plugins.registerEvents(interactions, this);
    }

    public static OpenLootrPlugin instance() { return instance; }

    public RuntimeState runtimeState() {
        return runtimeState;
    }

    public SchedulerFacade scheduler() {
        return scheduler;
    }

    public DbExecutor dbExecutor() {
        return dbExecutor;
    }

    public ContainerResolver containerResolver() { return containerResolver; }

    public SqliteLootStorage storage() { return storage; }

    public StorageHealth storageHealth() {
        return storage == null
                ? StorageHealth.unavailable(getDataFolder().toPath().resolve("openlootr.db").toString())
                : storage.health();
    }
}
