package net.aikeigroup.umccore.modules.performance;

import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Live performance monitor: samples TPS/MSPT/RAM/entities on a fixed interval
 * and (optionally) fires auto-optimization actions when the server is lagging.
 *
 * <p>The latest snapshot is published to a lock-free {@link AtomicReference} so
 * other modules (action bar, Discord, PAPI) can read it cheaply from any
 * thread. Sampling itself runs on the main thread because the entity/chunk
 * counts and TPS reads touch the server internals.</p>
 */
public final class PerformanceModule extends AbstractModule {

    private final AtomicReference<ServerStats> latest = new AtomicReference<>(ServerStats.empty());
    private long startMillis;

    // Auto-optimize state.
    private boolean autoEnabled;
    private double msptThreshold;
    private int sustainedSeconds;
    private int sampleIntervalTicks;
    private int lagStreakSamples; // consecutive lagging samples

    public PerformanceModule() {
        super("performance");
    }

    @Override
    protected String configName() {
        return "performance";
    }

    @Override
    protected void enable() {
        startMillis = System.currentTimeMillis();
        sampleIntervalTicks = Math.max(20, config().getInt("sample-interval-ticks", 100));

        autoEnabled = config().getBoolean("auto-optimize.enabled", false);
        msptThreshold = config().getDouble("auto-optimize.mspt-threshold", 45.0);
        sustainedSeconds = config().getInt("auto-optimize.sustained-seconds", 10);
        lagStreakSamples = 0;

        // Sample on the main thread every interval.
        track(scheduler.runTimer(this::sample, sampleIntervalTicks, sampleIntervalTicks));
    }

    private void sample() {
        double tps = clampTps(Bukkit.getTPS()[0]);
        double mspt = Bukkit.getAverageTickTime();

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);

        int chunks = 0;
        int entities = 0;
        for (World world : Bukkit.getWorlds()) {
            chunks += world.getLoadedChunks().length;
            entities += world.getEntities().size();
        }

        long uptime = (System.currentTimeMillis() - startMillis) / 1000L;

        ServerStats stats = new ServerStats(tps, mspt, usedMb, maxMb, chunks, entities,
                Bukkit.getOnlinePlayers().size(), uptime);
        latest.set(stats);

        if (autoEnabled) {
            evaluateAutoOptimize(stats);
        }
    }

    private void evaluateAutoOptimize(ServerStats stats) {
        double secondsPerSample = sampleIntervalTicks / 20.0;
        int needed = (int) Math.ceil(sustainedSeconds / secondsPerSample);

        if (stats.mspt() > msptThreshold) {
            lagStreakSamples++;
            if (lagStreakSamples >= needed) {
                runAutoActions(stats);
                lagStreakSamples = 0; // reset so we don't spam every sample
            }
        } else {
            lagStreakSamples = 0;
        }
    }

    private void runAutoActions(ServerStats stats) {
        for (String action : config().getStringList("auto-optimize.actions")) {
            switch (action.toLowerCase()) {
                case "notify-staff" -> notifyStaff(stats);
                case "clearlag-light" -> triggerLightClearlag();
                default -> plugin.getLogger().warning("Unknown auto-optimize action: " + action);
            }
        }
    }

    private void notifyStaff(ServerStats stats) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("umccore.command.perf")) {
                p.sendMessage(Text.mm("<gold>[UMC]</gold> <red>Server lagging:</red> <gray>MSPT "
                        + stats.msptString() + ", TPS " + stats.tpsString() + "</gray>"));
            }
        }
        plugin.getLogger().warning("Auto-optimize triggered: MSPT=" + stats.msptString()
                + " TPS=" + stats.tpsString());
    }

    private void triggerLightClearlag() {
        // Delegates to the clearlag module if it is active; otherwise no-op.
        plugin.modules().get("clearlag").ifPresent(m -> {
            if (m instanceof net.aikeigroup.umccore.modules.clearlag.ClearLagModule clm) {
                int removed = clm.runCleanup(true);
                plugin.getLogger().info("Auto light-clearlag removed " + removed + " entities.");
            }
        });
    }

    private static double clampTps(double tps) {
        return Math.min(20.0, Math.max(0.0, tps));
    }

    /** @return the most recent performance snapshot (thread-safe). */
    public ServerStats stats() {
        return latest.get();
    }
}
