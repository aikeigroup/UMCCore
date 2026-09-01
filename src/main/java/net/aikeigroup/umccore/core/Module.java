package net.aikeigroup.umccore.core;

import net.aikeigroup.umccore.UMCCore;

/**
 * A self-contained feature unit of UMCCore.
 *
 * <p>Every major feature (stacker, clearlag, discord, ui, ...) implements this
 * interface. The {@link ModuleManager} owns their lifecycle so that
 * {@code /umccore reload} can fully tear down and re-initialize every module
 * without a server restart and without leaking tasks or listeners.</p>
 *
 * <p>Contract:</p>
 * <ul>
 *   <li>{@link #onEnable()} — register listeners, start tasks, read config.</li>
 *   <li>{@link #onDisable()} — cancel <em>everything</em> started in enable.
 *       Must be idempotent and leak-free.</li>
 *   <li>A full reload is simply {@code onDisable()} then {@code onEnable()}.</li>
 * </ul>
 */
public interface Module {

    /**
     * @return the stable, lowercase identifier of this module
     *         (e.g. {@code "mobstacker"}). Used in config keys, commands,
     *         and tab completion.
     */
    String id();

    /**
     * @return the config path (in {@code config.yml}) that toggles this module,
     *         e.g. {@code "modules.mobstacker"}. Returning {@code null} means
     *         the module is always enabled.
     */
    default String enabledPath() {
        return "modules." + id();
    }

    /**
     * Called once when the module is being brought up. Register listeners and
     * schedule tasks here.
     *
     * @param plugin the owning plugin instance
     * @throws Exception if the module fails to start; the manager will log it
     *                   and mark the module as failed without crashing others
     */
    void onEnable(UMCCore plugin) throws Exception;

    /**
     * Called when the module is being torn down (shutdown or reload). Must
     * cancel all tasks and unregister all listeners it created. Safe to call
     * even if {@link #onEnable} failed partway.
     *
     * @param plugin the owning plugin instance
     */
    void onDisable(UMCCore plugin);
}
