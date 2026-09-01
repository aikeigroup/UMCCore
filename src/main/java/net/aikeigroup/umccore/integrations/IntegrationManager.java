package net.aikeigroup.umccore.integrations;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.integrations.placeholderapi.PlaceholderIntegration;
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
    private boolean votifier;

    private final PlaceholderIntegration papi;

    public IntegrationManager(UMCCore plugin) {
        this.plugin = plugin;
        this.papi = new PlaceholderIntegration(plugin);
    }

    /** Re-detects which optional plugins are present and enabled. */
    public void refresh() {
        placeholderApi = isPresent("PlaceholderAPI");
        vault = isPresent("Vault");
        luckPerms = isPresent("LuckPerms");
        discordSrv = isPresent("DiscordSRV");
        floodgate = isPresent("floodgate");
        // NuVotifier registers as "Votifier"; plain Votifier does too.
        votifier = isPresent("Votifier") || isPresent("NuVotifier");

        // (Re)register the PAPI expansion to match current availability.
        if (placeholderApi) {
            papi.register();
        } else {
            papi.unregister();
        }

        plugin.getLogger().info("Integrations detected: "
                + "PlaceholderAPI=" + placeholderApi
                + ", Vault=" + vault
                + ", LuckPerms=" + luckPerms
                + ", DiscordSRV=" + discordSrv
                + ", Floodgate=" + floodgate
                + ", Votifier=" + votifier);
    }

    /** Cleans up integration registrations on plugin shutdown. */
    public void shutdown() {
        papi.unregister();
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

    /** @return true if NuVotifier (or classic Votifier) is present. */
    public boolean hasVotifier() {
        return votifier;
    }

    /**
     * Detects whether a given player is a Bedrock Edition client (connected via
     * Geyser/Floodgate). Used to route the UI to the native Bedrock form path.
     *
     * <p>Soft: if Floodgate isn't installed this always returns {@code false},
     * so the plugin treats everyone as Java and nothing breaks.</p>
     *
     * @param uuid the player's UUID
     * @return true if the player is on Bedrock via Floodgate
     */
    public boolean isBedrock(java.util.UUID uuid) {
        if (!floodgate || uuid == null) {
            return false;
        }
        try {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        } catch (Throwable t) {
            // Floodgate classes not actually loadable at runtime — treat as Java.
            return false;
        }
    }
}
