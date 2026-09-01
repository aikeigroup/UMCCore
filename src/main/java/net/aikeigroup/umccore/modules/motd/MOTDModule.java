package net.aikeigroup.umccore.modules.motd;

import net.aikeigroup.umccore.core.AbstractModule;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Live, rotating server MOTD shown on the multiplayer server list.
 *
 * <p>Listens to {@link ServerListPingEvent}, renders the current MOTD frame and
 * rotates between configured frames on an interval. Text uses MiniMessage so
 * colours, gradients and bold are fully supported, and is resolved per-ping
 * through {@link net.aikeigroup.umccore.util.TextService} so PlaceholderAPI
 * tokens ({@code %server_online%}, {@code %umccore_*%}, ...) stay live.</p>
 *
 * <p>The server list MOTD is capped by Minecraft at {@link #MAX_MOTD_LENGTH}
 * characters — longer text is silently truncated by the client, so the config
 * warns if a frame is too long.</p>
 */
public final class MOTDModule extends AbstractModule {

    /** Maximum characters the Minecraft client shows in the server list. */
    private static final int MAX_MOTD_LENGTH = 59;

    private final List<MotdFrame> frames = new ArrayList<>();

    private int intervalSeconds;
    private int currentIndex;

    public MOTDModule() {
        super("motd");
    }

    @Override
    protected void enable() {
        if (!config().getBoolean("enabled", true)) {
            plugin.getLogger().info("MOTD sub-toggle is off; module idle.");
            return;
        }

        intervalSeconds = Math.max(1, config().getInt("rotation.interval-seconds", 8));
        loadFrames();
        if (frames.isEmpty()) {
            plugin.getLogger().warning("MOTD has no frames configured; module idle.");
            return;
        }

        currentIndex = 0;
        listen(new PingListener());

        // Rotation timer: advances the frame index on an interval. The actual
        // render still happens per-ping so values are always fresh.
        long period = intervalSeconds * 20L;
        track(scheduler.runTimer(this::advance, period, period));
    }

    @Override
    protected void disable() {
        frames.clear();
    }

    private void loadFrames() {
        frames.clear();
        List<String> lines = config().getStringList("frames");
        if (lines.isEmpty()) {
            lines = List.of(defaultFrame());
        }
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            // Allow multi-line frames with a literal "\n" inside a YAML string.
            String text = raw.replace("\\n", "\n");
            if (text.isBlank()) {
                continue;
            }
            // The client caps EACH line of the server list at 59 visible
            // characters, so check per line (tags stripped).
            for (String line : text.split("\n", -1)) {
                int visible = TextLength.visible(line);
                if (visible > MAX_MOTD_LENGTH) {
                    plugin.getLogger().warning("MOTD line is " + visible + " visible characters "
                            + "(max " + MAX_MOTD_LENGTH + "); the client will truncate it: " + line);
                }
            }
            frames.add(new MotdFrame(text));
        }
    }

    /** Rotates to the next frame (called on the interval timer). */
    private void advance() {
        if (frames.size() > 1) {
            currentIndex = (currentIndex + 1) % frames.size();
        }
    }

    /** Fallback frame used only when {@code frames} is empty in the config. */
    private static String defaultFrame() {
        return "<gradient:#00c6ff:#0072ff><bold>UNNESMC</bold></gradient> <dark_gray>—</dark_gray> "
                + "<#ffd700><bold>Survival</bold></#ffd700> <dark_gray>•</dark_gray> "
                + "<#33d17a><bold>Semi-RPG</bold></#33d17a> <dark_gray>•</dark_gray> "
                + "<#ffd54f><bold>Economy</bold></#ffd54f> <dark_gray>•</dark_gray> "
                + "<#7c9cff><bold>Lands</bold></#7c9cff>\n"
                + "<#90a4ae><bold>Main bareng mahasiswa UNNES</bold></#90a4ae> "
                + "<dark_gray>|</dark_gray> <#4dd0e1><bold>dc.unnesmc.my.id</bold></#4dd0e1>";
    }

    private final class PingListener implements Listener {

        @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
        public void onPing(ServerListPingEvent event) {
            if (frames.isEmpty()) {
                return;
            }
            // ServerListPingEvent has no per-player context; resolve server-scope
            // placeholders (null player) so %server_online% / %umccore_*% work.
            String text = plugin.text().resolve(null, frames.get(currentIndex).text());
            // setMotd expects a legacy §-coded string; render MiniMessage then
            // convert so colours/gradients/bold show up on the server list.
            event.setMotd(net.aikeigroup.umccore.util.Text.legacy(plugin.text().render(null, text)));
        }
    }

    /** One configured MOTD line. */
    private record MotdFrame(String text) {
    }
}
