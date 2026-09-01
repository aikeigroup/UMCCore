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
        String defaultLine = "<gradient:#00c6ff:#0072ff><bold>UNNESMC</bold></gradient>"
                + " <white>|</white> <gray>Survival</gray> <dark_gray>•</dark_gray>"
                + " <gray>Semi-RPG</gray> <dark_gray>•</dark_gray>"
                + " <gray>Economy</gray> <dark_gray>•</dark_gray> <gray>Lands</gray>";
        List<String> lines = config().getStringList("frames");
        if (lines.isEmpty()) {
            lines = List.of(defaultLine);
        }
        for (String raw : lines) {
            String text = raw == null ? "" : raw;
            if (text.isBlank()) {
                continue;
            }
            // Strip MiniMessage tags when measuring length; the client's 59-char
            // cap counts visible characters only.
            int visible = TextLength.visible(text);
            if (visible > MAX_MOTD_LENGTH) {
                plugin.getLogger().warning("MOTD frame is " + visible + " visible characters "
                        + "(max " + MAX_MOTD_LENGTH + "); the client will truncate it: " + text);
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
