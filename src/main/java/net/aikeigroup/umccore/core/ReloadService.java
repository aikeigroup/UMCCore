package net.aikeigroup.umccore.core;

import net.aikeigroup.umccore.UMCCore;

import java.util.logging.Level;

/**
 * Orchestrates a <em>full</em> reload of UMCCore without a server restart.
 *
 * <p>The sequence is intentionally strict so no tasks or listeners leak:</p>
 * <ol>
 *   <li>Disable every active module (reverse order).</li>
 *   <li>Reload every config file from disk.</li>
 *   <li>Re-read derived state (message prefix, integrations).</li>
 *   <li>Re-enable modules whose toggles are on.</li>
 * </ol>
 *
 * <p>Safe to call repeatedly. Returns a {@link Result} summarising what
 * happened so the command layer can report it to the sender.</p>
 */
public final class ReloadService {

    private final UMCCore plugin;

    public ReloadService(UMCCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Performs the full reload.
     *
     * @return a summary of the reload outcome
     */
    public Result reload() {
        long start = System.nanoTime();
        try {
            // 1. Tear everything down first so nothing runs against stale config.
            plugin.modules().disableAll();

            // 2. Reload configs from disk.
            plugin.configs().loadAll();

            // 3. Refresh derived state that reads config directly.
            plugin.messages().reload();
            plugin.integrations().refresh();

            // 4. Bring modules back up according to (possibly changed) toggles.
            int enabled = plugin.modules().enableAll();

            long ms = (System.nanoTime() - start) / 1_000_000L;
            return new Result(true, enabled, ms, null);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Full reload failed.", t);
            long ms = (System.nanoTime() - start) / 1_000_000L;
            return new Result(false, 0, ms, t.getMessage());
        }
    }

    /**
     * Immutable summary of a reload attempt.
     *
     * @param success        whether the reload completed without a fatal error
     * @param modulesEnabled how many modules came back up
     * @param millis         wall-clock duration in milliseconds
     * @param error          error message if {@code success} is false, else null
     */
    public record Result(boolean success, int modulesEnabled, long millis, String error) {
    }
}
