package net.aikeigroup.umccore.modules.performance;

/**
 * Immutable snapshot of server performance at a moment in time.
 *
 * <p>Produced by {@link PerformanceModule} on each sampling tick and read by
 * anything that needs live numbers: the {@code perf} command, action bar,
 * Discord embed, and PlaceholderAPI expansion.</p>
 *
 * @param tps            ticks-per-second (rolling, clamped to 20.0)
 * @param mspt           mean milliseconds-per-tick
 * @param ramUsedMb      used heap in megabytes
 * @param ramMaxMb       max heap in megabytes
 * @param loadedChunks   total loaded chunks across all worlds
 * @param entities       total entities across all worlds
 * @param onlinePlayers  current online player count
 * @param uptimeSeconds  seconds since the plugin started
 */
public record ServerStats(
        double tps,
        double mspt,
        long ramUsedMb,
        long ramMaxMb,
        int loadedChunks,
        int entities,
        int onlinePlayers,
        long uptimeSeconds
) {

    /** A zeroed snapshot used before the first sample is taken. */
    public static ServerStats empty() {
        return new ServerStats(20.0, 0.0, 0, 0, 0, 0, 0, 0);
    }

    /** @return TPS formatted to two decimals, e.g. {@code "19.98"}. */
    public String tpsString() {
        return String.format("%.2f", tps);
    }

    /** @return MSPT formatted to two decimals. */
    public String msptString() {
        return String.format("%.2f", mspt);
    }

    /** @return a compact human uptime like {@code "3d 4h 12m"}. */
    public String uptimeString() {
        long s = uptimeSeconds;
        long days = s / 86400;
        long hours = (s % 86400) / 3600;
        long mins = (s % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        sb.append(mins).append("m");
        return sb.toString().trim();
    }
}
