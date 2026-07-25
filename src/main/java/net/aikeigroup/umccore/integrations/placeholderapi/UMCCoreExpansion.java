package net.aikeigroup.umccore.integrations.placeholderapi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.modules.performance.PerformanceModule;
import net.aikeigroup.umccore.modules.performance.ServerStats;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion exposing UMCCore metrics as {@code %umccore_*%}.
 *
 * <p>Registered by {@link PlaceholderIntegration} only when PlaceholderAPI is
 * present. Values are read from the performance module's latest snapshot, so
 * they are cheap and thread-safe.</p>
 *
 * <p>Available placeholders:</p>
 * <ul>
 *   <li>{@code %umccore_tps%}, {@code %umccore_mspt%}</li>
 *   <li>{@code %umccore_online%}, {@code %umccore_max_players%}</li>
 *   <li>{@code %umccore_ram_used%}, {@code %umccore_ram_max%}</li>
 *   <li>{@code %umccore_uptime%}, {@code %umccore_entities%}, {@code %umccore_chunks%}</li>
 * </ul>
 */
public final class UMCCoreExpansion extends PlaceholderExpansion {

    private final UMCCore plugin;

    public UMCCoreExpansion(UMCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "umccore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "aikeigroup";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** Keep the expansion registered across PAPI reloads. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        ServerStats stats = currentStats();
        return switch (params.toLowerCase()) {
            case "tps" -> stats.tpsString();
            case "mspt" -> stats.msptString();
            case "online" -> String.valueOf(stats.onlinePlayers());
            case "max_players" -> String.valueOf(plugin.getServer().getMaxPlayers());
            case "ram_used" -> String.valueOf(stats.ramUsedMb());
            case "ram_max" -> String.valueOf(stats.ramMaxMb());
            case "uptime" -> stats.uptimeString();
            case "entities" -> String.valueOf(stats.entities());
            case "chunks" -> String.valueOf(stats.loadedChunks());
            default -> null; // unknown placeholder -> PAPI shows it unchanged
        };
    }

    private ServerStats currentStats() {
        return plugin.modules().get("performance")
                .filter(m -> m instanceof PerformanceModule)
                .map(m -> ((PerformanceModule) m).stats())
                .orElse(ServerStats.empty());
    }
}
