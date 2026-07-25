package net.aikeigroup.umccore.core;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.util.Scheduler;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

/**
 * Convenience base for feature modules that removes reload-leak boilerplate.
 *
 * <p>Subclasses schedule tasks via {@link #track(Scheduler.Task)} and register
 * listeners via {@link #listen(Listener)}. On disable, this base cancels every
 * tracked task and unregisters every tracked listener automatically, so a full
 * reload leaves nothing running. Subclasses implement {@link #enable()} and
 * {@link #disable()} for their own setup/teardown beyond that.</p>
 */
public abstract class AbstractModule implements Module {

    private final String id;

    protected UMCCore plugin;
    protected Scheduler scheduler;

    private final List<Scheduler.Task> tasks = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    protected AbstractModule(String id) {
        this.id = id;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final void onEnable(UMCCore plugin) throws Exception {
        this.plugin = plugin;
        this.scheduler = new Scheduler(plugin);
        enable();
    }

    @Override
    public final void onDisable(UMCCore plugin) {
        // Subclass teardown first (in case it needs live tasks/listeners)...
        try {
            disable();
        } finally {
            // ...then guarantee cleanup regardless of what the subclass did.
            for (Scheduler.Task task : tasks) {
                task.cancel();
            }
            tasks.clear();
            for (Listener listener : listeners) {
                HandlerList.unregisterAll(listener);
            }
            listeners.clear();
        }
    }

    /** Subclass startup: read config, schedule tasks, register listeners. */
    protected abstract void enable() throws Exception;

    /**
     * Subclass shutdown. Optional — tracked tasks/listeners are cleaned up for
     * you. Override only for extra state (e.g. flushing data, restoring mobs).
     */
    protected void disable() {
    }

    /** Records a scheduled task so it is cancelled on disable. */
    protected Scheduler.Task track(Scheduler.Task task) {
        tasks.add(task);
        return task;
    }

    /**
     * Runs a one-shot delayed action that is tracked for cancellation on
     * disable but removes itself from the tracking list once it fires — so
     * frequently-scheduled short tasks (e.g. per-death effects) don't
     * accumulate and leak memory.
     *
     * @param run        the action to run
     * @param delayTicks delay in ticks
     */
    protected void runLaterTracked(Runnable run, long delayTicks) {
        // Holder lets the lambda reference its own task to self-remove.
        final Scheduler.Task[] holder = new Scheduler.Task[1];
        holder[0] = scheduler.runLater(() -> {
            try {
                run.run();
            } finally {
                tasks.remove(holder[0]);
            }
        }, delayTicks);
        tasks.add(holder[0]);
    }

    /** Registers and records a listener so it is unregistered on disable. */
    protected <T extends Listener> T listen(T listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
        return listener;
    }

    /** Shortcut to this module's config file (defaults to {@code <id>.yml}). */
    protected FileConfiguration config() {
        return plugin.configs().get(configName());
    }

    /**
     * @return the config file name (without extension) backing this module.
     *         Defaults to the module id; override if they differ (e.g. both
     *         stacker modules share {@code stacker.yml}).
     */
    protected String configName() {
        return id;
    }
}
