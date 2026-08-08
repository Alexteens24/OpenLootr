package me.alexisbinh.openlootr.loot;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.loot.LootTable;

/** Observes the synchronous event emitted by LootTable.fillInventory for the active generation. */
public final class GenerationEventTracker implements Listener {
    private final ThreadLocal<Attempt> active = new ThreadLocal<>();

    public Scope begin(LootTable table) {
        if (active.get() != null) {
            throw new IllegalStateException("nested OpenLootr loot generation");
        }
        Attempt attempt = new Attempt(table);
        active.set(attempt);
        return new Scope(attempt);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onLootGenerate(LootGenerateEvent event) {
        Attempt attempt = active.get();
        if (attempt != null && event.isPlugin() && event.getLootTable().equals(attempt.table)) {
            attempt.observed = true;
            attempt.cancelled = event.isCancelled();
        }
    }

    private static final class Attempt {
        private final LootTable table;
        private boolean observed;
        private boolean cancelled;

        private Attempt(LootTable table) { this.table = table; }
    }

    public final class Scope implements AutoCloseable {
        private final Attempt attempt;
        private boolean closed;

        private Scope(Attempt attempt) { this.attempt = attempt; }

        public boolean observed() { return attempt.observed; }
        public boolean cancelled() { return attempt.cancelled; }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                active.remove();
            }
        }
    }
}
