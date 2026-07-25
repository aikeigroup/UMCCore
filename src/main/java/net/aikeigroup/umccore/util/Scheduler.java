package net.aikeigroup.umccore.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

/**
 * Thin scheduler wrapper used by all modules.
 *
 * <p>Every scheduling call returns a {@link Task} handle that the caller (an
 * {@link net.aikeigroup.umccore.core.AbstractModule}) records so it can be
 * cancelled cleanly on disable/reload — this is what keeps full reload
 * leak-free.</p>
 *
 * <p>Currently backed by the Bukkit scheduler (Paper). The indirection means a
 * Folia region-aware backend can be swapped in later without touching module
 * code. Heavy scans should use {@link #runAsync} / {@link #runTimerAsync} and
 * hop back to the main thread for any world mutation.</p>
 */
public final class Scheduler {

    private final Plugin plugin;

    public Scheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    /** A cancellable scheduled task. */
    public interface Task {
        void cancel();

        boolean isCancelled();
    }

    private static Task wrap(BukkitTask task) {
        return new Task() {
            private boolean cancelled;

            @Override
            public void cancel() {
                if (!cancelled) {
                    task.cancel();
                    cancelled = true;
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled || task.isCancelled();
            }
        };
    }

    /** Runs once on the main thread after {@code delayTicks}. */
    public Task runLater(Runnable run, long delayTicks) {
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, run, Math.max(0, delayTicks)));
    }

    /** Runs on the main thread every {@code periodTicks} after {@code delayTicks}. */
    public Task runTimer(Runnable run, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, run,
                Math.max(0, delayTicks), Math.max(1, periodTicks)));
    }

    /** Runs once off the main thread. */
    public Task runAsync(Runnable run) {
        return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, run));
    }

    /** Runs off the main thread every {@code periodTicks} after {@code delayTicks}. */
    public Task runTimerAsync(Runnable run, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, run,
                Math.max(0, delayTicks), Math.max(1, periodTicks)));
    }

    /** Runs once on the main thread as soon as possible. */
    public Task run(Runnable run) {
        return wrap(Bukkit.getScheduler().runTask(plugin, run));
    }

    /** Convenience for hopping back to the main thread with a value consumer. */
    public <T> void sync(T value, Consumer<T> consumer) {
        Bukkit.getScheduler().runTask(plugin, () -> consumer.accept(value));
    }
}
