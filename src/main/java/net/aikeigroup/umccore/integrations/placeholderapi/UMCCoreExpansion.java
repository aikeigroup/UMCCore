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
 *
 * <p>Presentation placeholders (return MiniMessage, meant to be embedded in
 * other MiniMessage text such as menu bodies):</p>
 * <ul>
 *   <li>{@code %umccore_tps_colored%}, {@code %umccore_mspt_colored%} — the value
 *       tinted green/amber/red by how healthy it is.</li>
 *   <li>{@code %umccore_ram_percent%} — used heap as a whole-number percentage.</li>
 *   <li>{@code %umccore_tps_bar%}, {@code %umccore_ram_bar%} — a 10-segment
 *       progress bar coloured by health.</li>
 *   <li>{@code %umccore_health%} — a one-word health verdict (localised) coloured
 *       by the current TPS/MSPT.</li>
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

            // --- Presentation helpers (MiniMessage output) --------------------
            case "tps_colored" -> tint(tpsColor(stats.tps()), stats.tpsString());
            case "mspt_colored" -> tint(msptColor(stats.mspt()), stats.msptString());
            case "ram_percent" -> String.valueOf(ramPercent(stats));
            case "tps_bar" -> bar(stats.tps() / 20.0, tpsColor(stats.tps()));
            case "ram_bar" -> bar(ramPercent(stats) / 100.0, ramColor(ramPercent(stats)));
            case "health" -> tint(healthColor(stats), healthWord(stats));

            default -> null; // unknown placeholder -> PAPI shows it unchanged
        };
    }

    private ServerStats currentStats() {
        return plugin.modules().get("performance")
                .filter(m -> m instanceof PerformanceModule)
                .map(m -> ((PerformanceModule) m).stats())
                .orElse(ServerStats.empty());
    }

    // --- Health helpers -------------------------------------------------------
    // Bedrock forms render on a dark panel, so the tints are the bright variants
    // used throughout the bedrock/ menus (kept in sync with them intentionally).

    private static final String GOOD = "#69f0ae";  // healthy (green)
    private static final String WARN = "#ffd740";  // caution (amber)
    private static final String BAD  = "#ff5252";  // unhealthy (red)

    private String tpsColor(double tps) {
        if (tps >= 18.5) return GOOD;
        if (tps >= 15.0) return WARN;
        return BAD;
    }

    private String msptColor(double mspt) {
        if (mspt <= 0) return GOOD;          // no sample yet
        if (mspt <= 35.0) return GOOD;
        if (mspt <= 50.0) return WARN;
        return BAD;
    }

    private String ramColor(int percent) {
        if (percent < 75) return GOOD;
        if (percent < 90) return WARN;
        return BAD;
    }

    /** Overall verdict: the worse of the TPS and MSPT ratings wins. */
    private String healthColor(ServerStats stats) {
        String tps = tpsColor(stats.tps());
        String mspt = msptColor(stats.mspt());
        if (tps.equals(BAD) || mspt.equals(BAD)) return BAD;
        if (tps.equals(WARN) || mspt.equals(WARN)) return WARN;
        return GOOD;
    }

    private String healthWord(ServerStats stats) {
        String c = healthColor(stats);
        if (c.equals(GOOD)) return "Sehat";
        if (c.equals(WARN)) return "Waspada";
        return "Berat";
    }

    private int ramPercent(ServerStats stats) {
        if (stats.ramMaxMb() <= 0) return 0;
        long pct = Math.round(100.0 * stats.ramUsedMb() / stats.ramMaxMb());
        return (int) Math.max(0, Math.min(100, pct));
    }

    /** Wraps a value in a MiniMessage colour tag. */
    private String tint(String hexColor, String value) {
        return "<" + hexColor + ">" + value + "</" + hexColor + ">";
    }

    /**
     * A 10-segment progress bar as MiniMessage: filled part in {@code color},
     * the remainder dark grey. {@code ratio} is clamped to 0..1.
     */
    private String bar(double ratio, String color) {
        int total = 10;
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * total);
        StringBuilder sb = new StringBuilder("<" + color + ">");
        for (int i = 0; i < filled; i++) sb.append('|');
        sb.append("</").append(color).append("><#37474f>");
        for (int i = filled; i < total; i++) sb.append('|');
        sb.append("</#37474f>");
        return sb.toString();
    }
}
