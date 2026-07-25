package net.aikeigroup.umccore.integrations;

import net.aikeigroup.umccore.UMCCore;
import org.bukkit.Bukkit;

/**
 * Detects and tracks optional third-party integrations at runtime.
 *
 * <p>Every hook here is <em>soft</em>: UMCCore runs fine if any of these are
 * absent. Presence is re-checked on {@link #refresh()} (called during reload)
 * so admins can add a dependency and reload without restarting.</p>
 *
 * <p>This M1 version only records availability flags. Concrete hook objects
 * (PlaceholderAPI expansion, Vault economy, LuckPerms API, DiscordSRV, Floodgate)
 * are wired up by their respective modules in later milestones.</p>
 */
public final class IntegrationManager {

    private final UMCCore plugin;

    private boolean placeholderApi;
    private boolean vault;
    private boolean luckPerms;
    private boolean discordSrv;
    private boolean floodgate;

    public IntegrationManager(UMCCore plugin) {
        this.plugin = plugin;
    }

    /** Re-detects which optional plugins are present and enabled. */
    public void refresh() {
        placeholderApi = isPresent("PlaceholderAPI");
        vault = isPresent("Vault");
        luckPerms = isPresent("LuckPerms");
        discordSrv = isPresent("DiscordSRV");
        floodgate = isPresent("floodgate");

        plugin.getLogger().info("Integrations detected: "
                + "PlaceholderAPI=" + placeholderApi
                + ", Vault=" + vault
                + ", LuckPerms=" + luckPerms
                + ", DiscordSRV=" + discordSrv
                + ", Floodgate=" + floodgate);
    }

    private boolean isPresent(String name) {
        return Bukkit.getPluginManager().getPlugin(name) != null
                && Bukkit.getPluginManager().isPluginEnabled(name);
    }

    public boolean hasPlaceholderApi() {
        return placeholderApi;
    }

    public boolean hasVault() {
        return vault;
    }

    public boolean hasLuckPerms() {
        return luckPerms;
    }

    public boolean hasDiscordSrv() {
        return discordSrv;
    }

    public boolean hasFloodgate() {
        return floodgate;
    }
}
