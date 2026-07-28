package net.aikeigroup.umccore.modules.lifecycle;

import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.modules.performance.PerformanceModule;
import net.aikeigroup.umccore.modules.performance.ServerStats;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server lifecycle recorder: writes a full JSON report just before the server
 * really stops, and reconstructs a crash report on the next boot when the
 * previous session died without a clean shutdown.
 *
 * <h2>How it knows what happened — accurately</h2>
 * There is no Bukkit event for "server is stopping/restarting/crashing", and no
 * code of any kind can run during a {@code kill -9}, JVM segfault, or power loss.
 * So this module does not try to "catch the crash as it happens". It combines
 * three independent signals that together cover every case:
 *
 * <ol>
 *   <li><b>Command listeners</b> ({@link ServerCommandEvent} +
 *       {@link PlayerCommandPreprocessEvent}) record <i>who</i> ran a stop/restart
 *       command and <i>which</i> command it was — the actor (console, or a
 *       player's name+UUID) and intent (STOP vs RESTART).</li>
 *   <li><b>{@link #onDisable} while {@link Bukkit#isStopping()}</b> is the primary
 *       write point: the world, players and stats are all still alive here, so the
 *       report is rich. A plain {@code /umccore reload} or module toggle also calls
 *       {@code onDisable} but with {@code isStopping() == false}, so it is correctly
 *       ignored — a reload is not a shutdown.</li>
 *   <li><b>A JVM shutdown hook</b> ({@link Runtime#addShutdownHook}) is the final
 *       safety net. It runs after the entire server + every plugin has been
 *       disabled — literally the last code in the JVM's life — so the report is
 *       still written even if UMCCore was disabled first. It writes only from a
 *       pre-captured in-memory snapshot (no Bukkit calls, which are illegal that
 *       late).</li>
 *   <li><b>A heartbeat file</b> is rewritten every few seconds and deleted only on
 *       a clean stop. If it is still present at the next boot, the previous session
 *       ended uncleanly → <b>CRASH / KILL / anomaly restart</b>. We then emit a
 *       crash report reconstructed from that last heartbeat (approx. time of death,
 *       last TPS, who was online).</li>
 * </ol>
 */
public final class LifecycleModule extends AbstractModule {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Guards against writing the shutdown report twice (onDisable + hook). */
    private final AtomicBoolean reportWritten = new AtomicBoolean(false);

    private File reportDir;
    private File heartbeatFile;
    private long startMillis;

    // Latest in-memory snapshot, refreshed by the heartbeat and readable by the
    // JVM shutdown hook (which cannot touch the Bukkit API).
    private volatile Snapshot lastSnapshot;

    // Who last invoked a stop/restart command, and when.
    private volatile String lastActor;
    private volatile String lastActorType;
    private volatile String lastCommand;
    private volatile String lastReasonKind; // "STOP" or "RESTART"
    private volatile long lastCommandMillis;
    // The plugin whose code dispatched the stop/restart command, if any (found by
    // walking the stack when the command fired). "console"/"player" typed commands
    // leave this null — a human, not a plugin, triggered it.
    private volatile String lastInitiatingPlugin;

    private Thread shutdownHook;
    private long attributionWindowMs;
    private ZoneId zone;

    public LifecycleModule() {
        super("lifecycle");
    }

    @Override
    protected void enable() throws Exception {
        this.startMillis = System.currentTimeMillis();
        this.reportWritten.set(false);
        this.zone = ZoneId.systemDefault();

        reportDir = new File(plugin.getDataFolder(), config().getString("report-folder", "lifecycle"));
        if (!reportDir.exists() && !reportDir.mkdirs()) {
            plugin.getLogger().warning("lifecycle: could not create report folder " + reportDir);
        }
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        heartbeatFile = new File(dataDir, "lifecycle-heartbeat.yml");

        attributionWindowMs = Math.max(1, config().getInt("attribution-window-seconds", 30)) * 1000L;

        // 1) Crash detection FIRST — a leftover heartbeat means last session never
        //    shut down cleanly (crash / kill -9 / OOM / power loss / panel restart).
        if (config().getBoolean("detect-crash", true)) {
            detectPreviousCrash();
        }

        // 2) Capture an initial snapshot and start the heartbeat.
        refreshSnapshot();
        writeHeartbeat();
        int intervalSeconds = Math.max(1, config().getInt("heartbeat-interval-seconds", 5));
        track(scheduler.runTimer(() -> {
            refreshSnapshot();
            writeHeartbeat();
        }, intervalSeconds * 20L, intervalSeconds * 20L));

        // 3) Record who runs stop/restart commands.
        listen(new CommandWatcher());

        // 4) Final safety net that runs after ALL plugins are disabled.
        if (config().getBoolean("use-shutdown-hook", true)) {
            installShutdownHook();
        }

        plugin.getLogger().info("lifecycle: recorder armed (reports -> " + reportDir.getName() + "/).");
    }

    @Override
    protected void disable() {
        // Distinguish a real server stop from a /umccore reload or module toggle.
        boolean stopping = safeIsStopping();
        if (stopping) {
            // Primary write point: players/world/stats are still alive here.
            writeShutdownReport(false);
            deleteHeartbeat(); // clean stop -> no crash flagged next boot
            // Leave the shutdown hook in place: it is the final safety net and it
            // is illegal to removeShutdownHook() once shutdown is in progress.
        } else {
            // Reload / toggle: NOT a shutdown. Do not write a report, and detach
            // the hook belonging to this (soon-to-be-replaced) instance.
            removeShutdownHook();
        }
        // AbstractModule cancels the heartbeat task + unregisters the listener.
    }

    // --- Crash detection --------------------------------------------------

    private void detectPreviousCrash() {
        if (heartbeatFile == null || !heartbeatFile.exists()) {
            return; // clean previous shutdown (or first ever boot)
        }
        YamlConfiguration hb = YamlConfiguration.loadConfiguration(heartbeatFile);
        Map<String, Object> report = new LinkedHashMap<>();
        long lastBeat = hb.getLong("last-beat-millis", 0L);

        report.put("event", "CRASH");
        report.put("classification", "UNCLEAN_SHUTDOWN");
        report.put("detail", "Previous session left a live heartbeat and never shut down cleanly. "
                + "Cause is one of: crash, kill -9 / OOM-killer, host/panel hard restart, or power loss.");
        report.put("detected-at", nowIso());
        report.put("approx-death-at", lastBeat > 0 ? isoOf(lastBeat) : "unknown");
        report.put("approx-uptime-seconds", hb.getLong("uptime-seconds", 0L));
        report.put("last-tps", hb.getString("tps", "unknown"));
        report.put("last-mspt", hb.getString("mspt", "unknown"));
        report.put("last-online-count", hb.getInt("online-count", 0));
        report.put("last-online-players", hb.getStringList("online-players"));
        report.put("last-ram-used-mb", hb.getLong("ram-used-mb", 0L));
        report.put("last-ram-max-mb", hb.getLong("ram-max-mb", 0L));
        report.put("last-heartbeat-file", heartbeatFile.getName());

        String file = writeReport("crash", report);
        plugin.getLogger().warning("lifecycle: previous session ended UNCLEANLY (crash/kill/anomaly). "
                + "Report: " + file);

        // Consume it so we don't re-report the same crash on the next boot.
        deleteHeartbeat();
    }

    // --- Shutdown report --------------------------------------------------

    /**
     * Writes the shutdown report. Idempotent: only the first caller (onDisable or
     * the shutdown hook) actually writes.
     *
     * @param fromHook true when called from the JVM shutdown hook, where the
     *                 Bukkit API is gone and only {@link #lastSnapshot} is usable
     */
    private void writeShutdownReport(boolean fromHook) {
        if (!reportWritten.compareAndSet(false, true)) {
            return; // already written by the other path
        }

        Snapshot snap = fromHook ? lastSnapshot : refreshSnapshot();
        if (snap == null) {
            snap = lastSnapshot; // best effort
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("event", "SHUTDOWN");

        // Classify intent + actor from the most recent stop/restart command.
        boolean attributed = lastCommand != null
                && (System.currentTimeMillis() - lastCommandMillis) <= attributionWindowMs;
        if (attributed) {
            report.put("classification", lastReasonKind); // STOP or RESTART
            Map<String, Object> actor = new LinkedHashMap<>();
            actor.put("type", lastActorType);
            actor.put("name", lastActor);
            actor.put("command", lastCommand);
            // Which plugin (if any) dispatched the command programmatically. Null
            // means a human typed it in console/chat, not a plugin.
            actor.put("initiating-plugin", lastInitiatingPlugin);
            actor.put("via", lastInitiatingPlugin != null
                    ? "command dispatched by plugin '" + lastInitiatingPlugin + "'"
                    : "command typed by " + lastActorType);
            report.put("triggered-by", actor);
        } else {
            report.put("classification", "EXTERNAL_OR_UNKNOWN");
            Map<String, Object> actor = new LinkedHashMap<>();
            actor.put("type", "process");
            actor.put("name", "external (SIGTERM from panel/systemd/watchdog, or API call)");
            actor.put("command", null);
            actor.put("via", "no stop/restart command seen within attribution window");
            report.put("triggered-by", actor);
        }

        report.put("source", fromHook ? "jvm-shutdown-hook (final safety net)" : "onDisable (server stopping)");
        report.put("stamped-at", nowIso());
        if (snap != null) {
            report.put("started-at", isoOf(snap.startMillis));
            report.put("uptime-seconds", snap.uptimeSeconds);
            report.put("tps", snap.tps);
            report.put("mspt", snap.mspt);
            report.put("ram-used-mb", snap.ramUsedMb);
            report.put("ram-max-mb", snap.ramMaxMb);
            report.put("loaded-chunks", snap.loadedChunks);
            report.put("entities", snap.entities);
            report.put("online-count", snap.onlineCount);
            report.put("online-players", snap.onlinePlayers);
        }

        String file = writeReport("shutdown", report);
        plugin.getLogger().info("lifecycle: shutdown report written -> " + file);

        // Optional Discord notification (best-effort during shutdown).
        if (!fromHook && config().getBoolean("discord.enabled", false)) {
            tryNotifyDiscord(report);
        }
    }

    // --- Heartbeat --------------------------------------------------------

    private Snapshot refreshSnapshot() {
        try {
            ServerStats stats = currentStats();
            List<String> players = new ArrayList<>();
            int online = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p.getName() + " (" + p.getUniqueId() + ")");
                online++;
            }
            Snapshot snap = new Snapshot(
                    startMillis,
                    (System.currentTimeMillis() - startMillis) / 1000L,
                    stats.tpsString(), stats.msptString(),
                    stats.ramUsedMb(), stats.ramMaxMb(),
                    stats.loadedChunks(), stats.entities(),
                    online, players);
            this.lastSnapshot = snap;
            return snap;
        } catch (Throwable t) {
            return lastSnapshot;
        }
    }

    private void writeHeartbeat() {
        Snapshot snap = lastSnapshot;
        if (snap == null || heartbeatFile == null) {
            return;
        }
        YamlConfiguration hb = new YamlConfiguration();
        hb.set("last-beat-millis", System.currentTimeMillis());
        hb.set("uptime-seconds", snap.uptimeSeconds);
        hb.set("tps", snap.tps);
        hb.set("mspt", snap.mspt);
        hb.set("online-count", snap.onlineCount);
        hb.set("online-players", snap.onlinePlayers);
        hb.set("ram-used-mb", snap.ramUsedMb);
        hb.set("ram-max-mb", snap.ramMaxMb);
        try {
            hb.save(heartbeatFile);
        } catch (IOException e) {
            plugin.getLogger().warning("lifecycle: could not write heartbeat: " + e.getMessage());
        }
    }

    private void deleteHeartbeat() {
        if (heartbeatFile != null && heartbeatFile.exists() && !heartbeatFile.delete()) {
            heartbeatFile.deleteOnExit();
        }
    }

    // --- Shutdown hook ----------------------------------------------------

    private void installShutdownHook() {
        shutdownHook = new Thread(() -> {
            // Runs after the server + every plugin is disabled. If onDisable
            // already wrote the report this is a no-op; otherwise it flushes the
            // last snapshot so a report is ALWAYS produced on a real JVM exit.
            try {
                if (!reportWritten.get()) {
                    writeShutdownReport(true);
                    deleteHeartbeat();
                }
            } catch (Throwable ignored) {
                // Nothing else can be done this late in the JVM's life.
            }
        }, "UMCCore-lifecycle-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException alreadyStopping) {
            shutdownHook = null; // JVM already shutting down; nothing to attach to
        }
    }

    private void removeShutdownHook() {
        if (shutdownHook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (Exception ignored) {
            // Shutdown already in progress (IllegalStateException) — leave the
            // hook to run; nothing else can be done.
        } finally {
            shutdownHook = null;
        }
    }

    // --- Command attribution ---------------------------------------------

    /** Observes stop/restart commands to attribute the shutdown to an actor. */
    private final class CommandWatcher implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onConsole(ServerCommandEvent event) {
            record(event.getSender(), event.getCommand());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlayer(PlayerCommandPreprocessEvent event) {
            // strip the leading slash
            String msg = event.getMessage();
            record(event.getPlayer(), msg.startsWith("/") ? msg.substring(1) : msg);
        }
    }

    private void record(CommandSender sender, String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return;
        }
        String cmd = rawCommand.trim();
        String word = cmd.split("\\s+")[0].toLowerCase(Locale.ROOT);
        // Normalise "plugin:command" -> "command" for matching, keep raw for report.
        String bare = word.contains(":") ? word.substring(word.indexOf(':') + 1) : word;

        String kind = classifyCommand(bare, word);
        if (kind == null) {
            return; // not a stop/restart command
        }

        lastReasonKind = kind;
        lastCommand = "/" + cmd;
        lastCommandMillis = System.currentTimeMillis();
        lastInitiatingPlugin = detectInitiatingPlugin();
        if (sender instanceof Player p) {
            lastActorType = "player";
            lastActor = p.getName() + " (" + p.getUniqueId() + ")";
        } else {
            // A plugin that dispatches a command uses the console sender, so a
            // non-player sender is either a real console or a plugin-driven one.
            lastActorType = lastInitiatingPlugin != null ? "plugin" : "console";
            lastActor = sender != null ? sender.getName() : "CONSOLE";
        }
    }

    /**
     * Walks the current call stack to find which plugin (if any) dispatched this
     * command programmatically — e.g. an auto-restart/watchdog plugin calling
     * {@code Bukkit.dispatchCommand(console, "restart")}. When a human types the
     * command in console or chat there is no plugin frame and this returns null.
     *
     * <p>UMCCore's own frames are skipped so we never blame ourselves.</p>
     */
    private String detectInitiatingPlugin() {
        try {
            for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
                Class<?> clazz;
                try {
                    clazz = Class.forName(el.getClassName(), false,
                            plugin.getClass().getClassLoader());
                } catch (Throwable notLoadable) {
                    continue;
                }
                org.bukkit.plugin.Plugin owner;
                try {
                    owner = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(clazz);
                } catch (Throwable notPluginClass) {
                    continue;
                }
                if (owner == null) {
                    continue;
                }
                // Skip ourselves and Bukkit/NMS frames; report the first real
                // third-party plugin that appears in the dispatch chain.
                if (owner == plugin) {
                    continue;
                }
                return owner.getName();
            }
        } catch (Throwable ignored) {
            // Attribution is best-effort; never let it break command handling.
        }
        return null;
    }

    /** @return "STOP", "RESTART", or null if the command is neither. */
    private String classifyCommand(String bare, String full) {
        for (String s : config().getStringList("stop-commands")) {
            if (matches(bare, full, s)) {
                return "STOP";
            }
        }
        for (String s : config().getStringList("restart-commands")) {
            if (matches(bare, full, s)) {
                return "RESTART";
            }
        }
        return null;
    }

    private boolean matches(String bare, String full, String configured) {
        if (configured == null) {
            return false;
        }
        String c = configured.toLowerCase(Locale.ROOT).trim();
        if (c.startsWith("/")) {
            c = c.substring(1);
        }
        return bare.equals(c) || full.equals(c);
    }

    // --- Discord (best-effort) -------------------------------------------

    private void tryNotifyDiscord(Map<String, Object> report) {
        if (!plugin.integrations().hasDiscordSrv()) {
            return;
        }
        try {
            DiscordNotifier.send(plugin, config(), report);
        } catch (Throwable t) {
            plugin.getLogger().warning("lifecycle: Discord notify failed: " + t.getMessage());
        }
    }

    // --- Helpers ----------------------------------------------------------

    private String writeReport(String prefix, Map<String, Object> report) {
        String name = prefix + "-" + ZonedDateTime.now(zone).format(STAMP) + ".json";
        File out = new File(reportDir, name);
        try {
            Files.writeString(out.toPath(), Json.write(report), StandardCharsets.UTF_8);
            pruneOldReports();
        } catch (IOException e) {
            plugin.getLogger().warning("lifecycle: could not write report " + name + ": " + e.getMessage());
        }
        return name;
    }

    /** Keeps only the newest {@code keep-reports} files to avoid unbounded growth. */
    private void pruneOldReports() {
        int keep = config().getInt("keep-reports", 50);
        if (keep <= 0) {
            return;
        }
        File[] files = reportDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null || files.length <= keep) {
            return;
        }
        // Oldest first by last-modified, delete the surplus.
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - keep; i++) {
            if (!files[i].delete()) {
                files[i].deleteOnExit();
            }
        }
    }

    private ServerStats currentStats() {
        return plugin.modules().get("performance")
                .filter(m -> m instanceof PerformanceModule)
                .map(m -> ((PerformanceModule) m).stats())
                .orElse(ServerStats.empty());
    }

    private boolean safeIsStopping() {
        try {
            return Bukkit.isStopping();
        } catch (Throwable t) {
            // Extremely old/edge servers without the API: assume stopping so we
            // never miss a report (a false positive only means an extra report).
            return true;
        }
    }

    private String nowIso() {
        return ZonedDateTime.now(zone).format(ISO);
    }

    private String isoOf(long millis) {
        return ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).format(ISO);
    }

    /** Immutable point-in-time capture usable from the shutdown-hook thread. */
    private record Snapshot(
            long startMillis,
            long uptimeSeconds,
            String tps,
            String mspt,
            long ramUsedMb,
            long ramMaxMb,
            int loadedChunks,
            int entities,
            int onlineCount,
            List<String> onlinePlayers) {
    }
}
