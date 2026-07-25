package net.aikeigroup.umccore.modules.actionbar;

import net.aikeigroup.umccore.core.AbstractModule;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fully-animated action bar.
 *
 * <p>A single frame timer advances a global frame counter. On every frame the
 * currently-visible segment is re-rendered (so it is always in motion), and
 * whenever the rotation advances to the next segment a multi-frame
 * <em>transition</em> plays — meaning text never hard-cuts, satisfying the
 * "every change is animated" requirement.</p>
 *
 * <p>Segment visibility is per-player (permission-gated), so each player may see
 * a different rotation; frame/transition timing is shared for simplicity.
 * Players can hide their own bar with {@code /umccore actionbar toggle}.</p>
 */
public final class ActionBarModule extends AbstractModule {

    private final List<Segment> segments = new ArrayList<>();

    private int frameIntervalTicks;
    private int holdFrames;
    private int transitionFrames;
    private Transition transition;
    private boolean allowToggle;

    /** Global animation frame counter. */
    private long frame;

    /** Rotation phase: index of the currently "settled" segment. */
    private int currentIndex;
    /** Frames elapsed within the current phase (hold or transition). */
    private int phaseFrame;
    private boolean transitioning;

    /** Players who opted out (kept across frames; cleared on disable). */
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    public ActionBarModule() {
        super("actionbar");
    }

    @Override
    protected void enable() {
        if (!config().getBoolean("enabled", true)) {
            plugin.getLogger().info("Action bar sub-toggle is off; module idle.");
            return;
        }
        frameIntervalTicks = Math.max(1, config().getInt("frame-interval-ticks", 2));
        holdFrames = Math.max(1, config().getInt("rotation.hold-frames", 60));
        transitionFrames = Math.max(1, config().getInt("rotation.transition-frames", 12));
        transition = Transition.from(config().getString("rotation.transition", "TYPEWRITER"));
        allowToggle = config().getBoolean("allow-player-toggle", true);

        loadSegments();
        if (segments.isEmpty()) {
            plugin.getLogger().warning("Action bar has no segments configured; module idle.");
            return;
        }

        frame = 0;
        currentIndex = 0;
        phaseFrame = 0;
        transitioning = false;

        listen(new QuitListener());
        track(scheduler.runTimer(this::tick, frameIntervalTicks, frameIntervalTicks));
    }

    @Override
    protected void disable() {
        hidden.clear();
        segments.clear();
    }

    private void loadSegments() {
        segments.clear();
        var list = config().getMapList("segments");
        for (var raw : list) {
            String id = str(raw.get("id"), "segment");
            String text = str(raw.get("text"), "");
            SegmentAnimation anim = SegmentAnimation.from(str(raw.get("animation"), null));
            String perm = str(raw.get("permission"), "");
            segments.add(new Segment(id, text, anim, perm));
        }
    }

    private static String str(Object value, String def) {
        return value == null ? def : String.valueOf(value);
    }

    // --- Frame loop --------------------------------------------------------

    private void tick() {
        frame++;
        phaseFrame++;

        // Advance rotation phase.
        if (!transitioning && phaseFrame >= holdFrames && segments.size() > 1) {
            transitioning = true;
            phaseFrame = 0;
        } else if (transitioning && phaseFrame >= transitionFrames) {
            transitioning = false;
            phaseFrame = 0;
            currentIndex = (currentIndex + 1) % segments.size();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hidden.contains(player.getUniqueId())) {
                continue;
            }
            Component bar = renderFor(player);
            if (bar != null) {
                player.sendActionBar(bar);
            }
        }
    }

    private Component renderFor(Player player) {
        // Resolve the player's visible segments (permission-gated) so different
        // players can see different rotations.
        List<Segment> visible = new ArrayList<>();
        for (Segment s : segments) {
            if (s.visibleTo(player)) {
                visible.add(s);
            }
        }
        if (visible.isEmpty()) {
            return null;
        }

        int idx = currentIndex % visible.size();
        Segment current = visible.get(idx);
        String resolved = plugin.text().resolve(player, current.text());

        if (transitioning && visible.size() > 1) {
            Segment next = visible.get((idx + 1) % visible.size());
            String resolvedNext = plugin.text().resolve(player, next.text());
            double progress = Math.min(1.0, (double) phaseFrame / transitionFrames);
            return AnimationEngine.renderTransition(resolved, resolvedNext, transition, progress, frame);
        }
        return AnimationEngine.renderSegment(resolved, current.animation(), frame);
    }

    // --- Toggle ------------------------------------------------------------

    /**
     * Toggles the action bar for a player.
     *
     * @return true if now visible, false if now hidden
     */
    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (hidden.contains(id)) {
            hidden.remove(id);
            return true;
        } else {
            hidden.add(id);
            player.sendActionBar(Component.empty());
            return false;
        }
    }

    public boolean isToggleAllowed() {
        return allowToggle;
    }

    private final class QuitListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            // Keep memory tidy; a returning player defaults to visible.
            hidden.remove(event.getPlayer().getUniqueId());
        }
    }
}
