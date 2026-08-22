package net.aikeigroup.umccore.modules.staffchat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-platform StaffChat module with two-way Discord synchronization.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Toggle mode: regular chat messages are redirected to staff chat.</li>
 *   <li>Direct mode: {@code /sc <message>} broadcasts instantly without toggling.</li>
 *   <li>Discord synchronization: Minecraft to Discord and Discord to Minecraft.</li>
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

        // Forward to Discord async if enabled
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
     * Sends a Component to all online players with {@code umccore.staffchat.see} and the server console.
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
     * Inner Bukkit listener for player chat and quit events.
     */
    private final class StaffChatListener implements Listener {

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onAsyncChat(AsyncChatEvent event) {
            Player player = event.getPlayer();
            if (isToggled(player)) {
                event.setCancelled(true);
                String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
                sendFromMinecraft(player, raw);
            }
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            toggledStaff.remove(event.getPlayer().getUniqueId());
        }
    }
}
