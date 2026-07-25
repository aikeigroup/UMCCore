package net.aikeigroup.umccore.api;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.modules.performance.PerformanceModule;
import net.aikeigroup.umccore.modules.performance.ServerStats;
import org.bukkit.entity.Player;

/**
 * Stable public entry point for other plugins that want to integrate with
 * UMCCore.
 *
 * <p>Obtain an instance via {@link #get()} once UMCCore is enabled. Everything
 * here is safe to call from the main thread; {@link ServerStats} is also
 * safe to read from async contexts.</p>
 *
 * <pre>{@code
 * if (Bukkit.getPluginManager().isPluginEnabled("UMCCore")) {
 *     UMCCoreAPI api = UMCCoreAPI.get();
 *     double tps = api.stats().tps();
 *     api.openMenu(player, "main");
 * }
 * }</pre>
 */
public final class UMCCoreAPI {

    private static UMCCoreAPI instance;

    private final UMCCore plugin;

    private UMCCoreAPI(UMCCore plugin) {
        this.plugin = plugin;
    }

    /** Called internally by the plugin on enable. */
    public static void init(UMCCore plugin) {
        instance = new UMCCoreAPI(plugin);
    }

    /**
     * @return the API singleton
     * @throws IllegalStateException if UMCCore is not enabled yet
     */
    public static UMCCoreAPI get() {
        if (instance == null) {
            throw new IllegalStateException("UMCCore is not enabled yet.");
        }
        return instance;
    }

    /**
     * @return the latest server performance snapshot, or an empty snapshot if
     *         the performance module is disabled
     */
    public ServerStats stats() {
        return plugin.modules().get("performance")
                .filter(m -> m instanceof PerformanceModule)
                .map(m -> ((PerformanceModule) m).stats())
                .orElse(ServerStats.empty());
    }

    /**
     * Opens a menu for a player (respects the menu's permission).
     *
     * @param player the player
     * @param menuId the menu id (built-in or custom)
     * @return true if the menu opened
     */
    public boolean openMenu(Player player, String menuId) {
        return plugin.menuService().open(player, menuId);
    }

    /**
     * @param id module id
     * @return true if the named module is currently active
     */
    public boolean isModuleActive(String id) {
        return plugin.modules().isActive(id);
    }

    /**
     * Resolves PlaceholderAPI + MiniMessage text for a player into a plain,
     * placeholder-substituted string.
     *
     * @param player context player (nullable)
     * @param text   raw text
     * @return resolved string
     */
    public String resolve(Player player, String text) {
        return plugin.text().resolve(player, text);
    }

    /** @return the underlying plugin instance (advanced use). */
    public UMCCore plugin() {
        return plugin;
    }
}
