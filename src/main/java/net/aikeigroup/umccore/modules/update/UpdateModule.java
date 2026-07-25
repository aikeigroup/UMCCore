package net.aikeigroup.umccore.modules.update;

import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-update module: checks the project's GitHub Releases for a newer version
 * and (optionally) downloads the new jar into the server's update folder, which
 * the server applies on the next restart.
 *
 * <p>All network work runs async. Nothing is downloaded automatically unless
 * {@code auto-download} is enabled; otherwise it only notifies staff and the
 * console, and admins pull it with {@code /umccore update download}.</p>
 *
 * <p>Deliberately dependency-free: the tiny slice of the GitHub JSON we need
 * (tag + asset URL) is extracted with a regex, so no JSON library is required.</p>
 */
public final class UpdateModule extends AbstractModule {

    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ASSET = Pattern.compile(
            "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.jar)\"");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private String repo;
    private boolean autoDownload;
    private boolean notifyAdmins;

    /** Cached result of the last check (null until first check completes). */
    private volatile ReleaseInfo latest;

    public UpdateModule() {
        super("update");
    }

    @Override
    protected String configName() {
        // Reads its options from the general config.yml under 'auto-update'.
        return "config";
    }

    @Override
    protected void enable() {
        repo = config().getString("auto-update.repository", "aikeigroup/UMCCore");
        autoDownload = config().getBoolean("auto-update.auto-download", false);
        notifyAdmins = config().getBoolean("auto-update.notify-admins", true);
        int intervalHours = Math.max(1, config().getInt("auto-update.check-interval-hours", 6));

        if (notifyAdmins) {
            listen(new JoinNotifier());
        }

        // Initial check shortly after start, then on the configured interval.
        track(scheduler.runTimerAsync(this::check, 200L, intervalHours * 60L * 60L * 20L));
    }

    // --- Version checking --------------------------------------------------

    /** Performs a release check (async-safe). Updates {@link #latest}. */
    public void check() {
        try {
            String url = "https://api.github.com/repos/" + repo + "/releases/latest";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "UMCCore-Updater")
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                plugin.getLogger().info("Update check: HTTP " + resp.statusCode() + " (no release?).");
                return;
            }
            ReleaseInfo info = parse(resp.body());
            if (info == null) {
                return;
            }
            this.latest = info;

            String current = plugin.getPluginMeta().getVersion();
            if (isNewer(info.version(), current)) {
                announce(info, current);
                if (autoDownload) {
                    download();
                }
            } else {
                plugin.getLogger().info("UMCCore is up to date (" + current + ").");
            }
        } catch (IOException | InterruptedException e) {
            plugin.getLogger().info("Update check failed: " + e.getMessage());
        }
    }

    private void announce(ReleaseInfo info, String current) {
        String msg = "A new UMCCore version is available: " + info.version()
                + " (current " + current + ").";
        plugin.getLogger().warning(msg);
        if (notifyAdmins) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("umccore.command.update")) {
                    p.sendMessage(Text.mm("<gold>[UMC]</gold> <yellow>" + msg
                            + " Run <white>/umccore update download</white>.</yellow>"));
                }
            }
        }
    }

    // --- Download ----------------------------------------------------------

    /**
     * Downloads the latest release jar into the server update folder. Safe to
     * call from any thread; performs the download async.
     *
     * @return a short status message describing what will happen
     */
    public String download() {
        ReleaseInfo info = latest;
        if (info == null || info.assetUrl() == null) {
            return "No release info yet — run a check first.";
        }
        scheduler.runAsync(() -> doDownload(info));
        return "Downloading " + info.version() + " to the update folder...";
    }

    private void doDownload(ReleaseInfo info) {
        try {
            Path updateDir = Bukkit.getUpdateFolderFile().toPath();
            Files.createDirectories(updateDir);
            // Paper replaces plugins/UMCCore-*.jar with the file of the SAME name
            // in the update folder, so name it to match our jar.
            Path target = updateDir.resolve(jarFileName());

            HttpRequest req = HttpRequest.newBuilder(URI.create(info.assetUrl()))
                    .header("User-Agent", "UMCCore-Updater")
                    .timeout(Duration.ofMinutes(2))
                    .GET().build();
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                plugin.getLogger().warning("Update download failed: HTTP " + resp.statusCode());
                return;
            }
            try (InputStream in = resp.body()) {
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getLogger().warning("Downloaded UMCCore " + info.version()
                    + " to update folder. Restart the server to apply.");
        } catch (IOException | InterruptedException e) {
            plugin.getLogger().warning("Update download error: " + e.getMessage());
        }
    }

    private String jarFileName() {
        // Best-effort: derive the on-disk jar name from our plugin file.
        try {
            return plugin.getUpdateJarName();
        } catch (Throwable t) {
            return "UMCCore.jar";
        }
    }

    // --- Helpers -----------------------------------------------------------

    private ReleaseInfo parse(String json) {
        Matcher tag = TAG.matcher(json);
        if (!tag.find()) {
            return null;
        }
        String version = stripV(tag.group(1));
        Matcher asset = ASSET.matcher(json);
        String url = asset.find() ? asset.group(1) : null;
        return new ReleaseInfo(version, url);
    }

    private static String stripV(String tag) {
        return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
    }

    /** @return true if {@code candidate} is a strictly higher version than {@code current}. */
    static boolean isNewer(String candidate, String current) {
        int[] a = versionParts(candidate);
        int[] b = versionParts(current);
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int[] versionParts(String v) {
        // Keep only the numeric dotted core (e.g. "1.2.3-beta" -> [1,2,3]).
        String core = v.split("[-+]")[0];
        String[] parts = core.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** @return the latest known release, or null if not checked yet. */
    public ReleaseInfo latest() {
        return latest;
    }

    /** @return true if a newer version than the running one is known. */
    public boolean updateAvailable() {
        return latest != null && isNewer(latest.version(), plugin.getPluginMeta().getVersion());
    }

    /** Minimal release info extracted from the GitHub API. */
    public record ReleaseInfo(String version, String assetUrl) {
    }

    private final class JoinNotifier implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            if (updateAvailable() && event.getPlayer().hasPermission("umccore.command.update")) {
                event.getPlayer().sendMessage(Text.mm("<gold>[UMC]</gold> <yellow>Update available: "
                        + latest.version() + ". <white>/umccore update download</white></yellow>"));
            }
        }
    }
}
