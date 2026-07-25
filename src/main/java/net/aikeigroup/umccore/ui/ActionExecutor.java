package net.aikeigroup.umccore.ui;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.ui.model.MenuAction;
import net.aikeigroup.umccore.ui.model.MenuButton;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Executes a button's list of {@link MenuAction}s for a player.
 *
 * <p>Security model: {@code RUN_COMMAND} runs with the player's own
 * permissions; {@code CONSOLE_COMMAND} runs from console but can only ever be
 * authored in server-side menu files by admins — players cannot inject them at
 * runtime. Placeholders in command/message arguments are resolved per-player.</p>
 */
public final class ActionExecutor {

    private final UMCCore plugin;

    public ActionExecutor(UMCCore plugin) {
        this.plugin = plugin;
    }

    /** Runs every action of a button in order for the given player. */
    public void run(Player player, MenuButton button) {
        run(player, button.actions());
    }

    /** Runs a list of actions in order. */
    public void run(Player player, List<MenuAction> actions) {
        for (MenuAction action : actions) {
            try {
                dispatch(player, action);
            } catch (Exception e) {
                plugin.getLogger().warning("Menu action failed (" + action.type()
                        + ":" + action.argument() + "): " + e.getMessage());
            }
        }
    }

    private void dispatch(Player player, MenuAction action) {
        String arg = plugin.integrations() != null
                ? plugin.text().resolve(player, action.argument())
                : action.argument();

        switch (action.type()) {
            case RUN_COMMAND -> player.performCommand(stripSlash(arg));
            case CONSOLE_COMMAND ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(arg));
            case OPEN_MENU -> plugin.menuService().open(player, arg);
            case MESSAGE -> player.sendMessage(Text.mm(arg));
            case SOUND -> playSound(player, arg);
            case TELEPORT -> teleport(player, arg);
            case CLOSE -> {
                player.closeInventory();
                player.closeDialog();
            }
        }
    }

    private String stripSlash(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }

    private void playSound(Player player, String key) {
        try {
            Sound sound = Sound.valueOf(key.toUpperCase(java.util.Locale.ROOT));
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown sound in menu action: " + key);
        }
    }

    private void teleport(Player player, String spec) {
        // Format: world,x,y,z[,yaw,pitch]
        String[] parts = spec.split(",");
        if (parts.length < 4) {
            plugin.getLogger().warning("Bad TELEPORT spec (need world,x,y,z): " + spec);
            return;
        }
        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            plugin.getLogger().warning("TELEPORT to unknown world: " + parts[0]);
            return;
        }
        double x = Double.parseDouble(parts[1].trim());
        double y = Double.parseDouble(parts[2].trim());
        double z = Double.parseDouble(parts[3].trim());
        float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : player.getLocation().getYaw();
        float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : player.getLocation().getPitch();
        player.teleport(new Location(world, x, y, z, yaw, pitch));
    }
}
