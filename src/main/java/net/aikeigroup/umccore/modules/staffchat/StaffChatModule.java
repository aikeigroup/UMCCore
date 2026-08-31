package net.aikeigroup.umccore.modules.staffchat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-platform StaffChat module with two-way Discord synchronization.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Toggle mode: regular chat messages are redirected exclusively to staff chat.</li>
 *   <li>Direct mode: {@code /sc <message>} broadcasts instantly without toggling.</li>
 *   <li>Discord synchronization: Minecraft to Discord and Discord to Minecraft.</li>
 *   <li>Leak-proof isolation: prevents staff chat from leaking to public Minecraft chat or Discord global channel.</li>
 *   <li>Permission-gated: {@code umccore.staffchat.use} and {@code umccore.staffchat.see}.</li>
 *   <li>Customizable formats, sounds, and embed/plain text Discord options.</li>
 * </ul>
 */
public final class StaffChatModule extends AbstractModule {

    public static final String PERM_USE = "umccore.staffchat.use";
    public static final String PERM_SEE = "umccore.staffchat.see";

    private final Set<UUID> toggledStaff = ConcurrentHashMap.newKeySet();
    private DiscordStaffChatBridge discordBridge;

    public StaffChatModule() {
        super("staffchat");
    }

    @Override
    protected void enable() {
        listen(new StaffChatListener());

        if (plugin.integrations().hasDiscordSrv() && config().getBoolean("discord.enabled", true)) {
            try {
                discordBridge = new DiscordStaffChatBridge(plugin, this);
                discordBridge.register();
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to initialize Discord staff chat bridge: " + t.getMessage());
                discordBridge = null;
            }
        }
    }

    @Override
    protected void disable() {
        toggledStaff.clear();
        if (discordBridge != null) {
            try {
                discordBridge.unregister();
            } catch (Throwable ignored) {
            }
            discordBridge = null;
        }
    }

    /**
     * Toggles staff chat mode for a player.
     *
     * @param player the player
     * @return true if staff chat mode is now ON, false if OFF
     */
    public boolean toggle(Player player) {
        if (toggledStaff.remove(player.getUniqueId())) {
            return false;
        }
        toggledStaff.add(player.getUniqueId());
        return true;
    }

    /**
     * Checks if staff chat mode is currently toggled on for a player.
     *
     * @param player the player
     * @return true if toggled on
     */
    public boolean isToggled(Player player) {
        return toggledStaff.contains(player.getUniqueId());
    }

    /**
     * Broadcasts a staff chat message originating from Minecraft (player or console).
     *
     * @param sender     the sender
     * @param rawMessage the raw message text
     */
    public void sendFromMinecraft(CommandSender sender, String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        String template;
        String senderName = sender.getName();
        if (sender instanceof Player player) {
            template = config().getString("format.minecraft",
                    "<dark_gray>[<gradient:#ff416c:#ff4b2b><bold>STAFF</bold></gradient>]</dark_gray> <yellow>{player}</yellow><gray>:</gray> <white>{message}</white>");
        } else {
            template = config().getString("format.console",
                    "<dark_gray>[<gradient:#ff416c:#ff4b2b><bold>STAFF</bold></gradient>]</dark_gray> <red>[Console]</red><gray>:</gray> <white>{message}</white>");
        }

        String formatted = template
                .replace("{player}", senderName)
                .replace("{message}", rawMessage)
                .replace("{server}", plugin.getServer().getName());

        if (sender instanceof Player player) {
            formatted = plugin.text().resolve(player, formatted);
        } else {
            formatted = plugin.text().resolve(null, formatted);
        }

        Component component = Text.mm(formatted);
        broadcastToStaff(component);
        playStaffSound();

        // Forward to Discord staff channel async if enabled
        if (discordBridge != null && config().getBoolean("discord.mc-to-discord", true)) {
            scheduler.runAsync(() -> discordBridge.sendToDiscord(sender, rawMessage));
        }
    }

    /**
     * Broadcasts a staff chat message originating from Discord to in-game staff.
     *
     * @param authorName  the Discord author display name
     * @param roleName    the Discord author primary role name
     * @param rawMessage  the message text
     * @param channelName the Discord channel name
     */
    public void sendFromDiscord(String authorName, String roleName, String rawMessage, String channelName) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        String template = config().getString("format.discord",
                "<dark_gray>[<gradient:#ff416c:#ff4b2b><bold>STAFF</bold></gradient>]</dark_gray> <gradient:#7289da:#5865f2>[Discord]</gradient> <aqua>{user}</aqua><gray>:</gray> <white>{message}</white>");

        String formatted = template
                .replace("{user}", authorName != null ? authorName : "Discord")
                .replace("{role}", roleName != null ? roleName : "")
                .replace("{message}", rawMessage)
                .replace("{channel}", channelName != null ? channelName : "staff-chat")
                .replace("{server}", plugin.getServer().getName());

        formatted = plugin.text().resolve(null, formatted);
        Component component = Text.mm(formatted);
        broadcastToStaff(component);
        playStaffSound();
    }

    /**
     * Sends a Component exclusively to online staff players and the server console.
     */
    private void broadcastToStaff(Component component) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PERM_SEE) || player.hasPermission(PERM_USE) || player.isOp()) {
                player.sendMessage(component);
            }
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }

    /**
     * Plays the configured notification sound to online staff members.
     */
    private void playStaffSound() {
        if (!config().getBoolean("sound.enabled", true)) {
            return;
        }
        String soundName = config().getString("sound.name", "block.note_block.pling");
        float volume = (float) config().getDouble("sound.volume", 1.0);
        float pitch = (float) config().getDouble("sound.pitch", 1.5);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PERM_SEE) || player.hasPermission(PERM_USE) || player.isOp()) {
                try {
                    player.playSound(player.getLocation(), soundName, SoundCategory.PLAYERS, volume, pitch);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Inner Bukkit listener for player chat and quit events with multi-layer leak prevention.
     *
     * <p>On Paper, chat is processed in two stages (see
     * {@code io.papermc.paper.adventure.ChatProcessor}): the legacy
     * {@link AsyncPlayerChatEvent} is fired first (when any listener exists for
     * it), and its resulting message/recipients/cancelled state is then forwarded
     * into the modern {@link AsyncChatEvent}. Both events therefore fire for a
     * single player chat.</p>
     *
     * <p>Broadcasting is deliberately done ONLY from the modern handler. The
     * legacy handler only cancels and clears recipients — it must NEVER blank the
     * message, because the legacy message is what Paper forwards into the modern
     * event. Blanking it would make {@code AsyncChatEvent.message()} empty and the
     * staff broadcast would silently no-op, while the original chat is already
     * cancelled — the message would vanish entirely.</p>
     */
    private final class StaffChatListener implements Listener {

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
        public void onAsyncChatLowest(AsyncChatEvent event) {
            Player player = event.getPlayer();
            if (isToggled(player)) {
                event.setCancelled(true);
                clearViewers(event);

                // Some other plugin may have cleared the message before us; fall
                // back to the original component so the staff payload survives.
                Component message = event.message();
                String raw = PlainTextComponentSerializer.plainText().serialize(message);
                if (raw == null || raw.isBlank()) {
                    raw = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
                }
                sendFromMinecraft(player, raw);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
        public void onAsyncChatMonitor(AsyncChatEvent event) {
            Player player = event.getPlayer();
            if (isToggled(player)) {
                event.setCancelled(true);
                clearViewers(event);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
        public void onLegacyChatLowest(AsyncPlayerChatEvent event) {
            Player player = event.getPlayer();
            if (isToggled(player)) {
                event.setCancelled(true);
                clearRecipients(event);
                // NOTE: do not call event.setMessage("") here. Paper forwards the
                // legacy message into the modern AsyncChatEvent; blanking it would
                // destroy the payload the modern handler broadcasts to staff.
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
        public void onLegacyChatMonitor(AsyncPlayerChatEvent event) {
            Player player = event.getPlayer();
            if (isToggled(player)) {
                event.setCancelled(true);
                clearRecipients(event);
            }
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            toggledStaff.remove(event.getPlayer().getUniqueId());
        }

        private void clearViewers(AsyncChatEvent event) {
            try {
                event.viewers().clear();
            } catch (Throwable ignored) {
            }
        }

        private void clearRecipients(AsyncPlayerChatEvent event) {
            try {
                event.getRecipients().clear();
            } catch (Throwable ignored) {
            }
        }
    }
}
