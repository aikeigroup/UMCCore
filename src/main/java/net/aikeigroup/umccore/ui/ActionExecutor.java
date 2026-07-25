package net.aikeigroup.umccore.ui;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.ui.model.MenuAction;
import net.aikeigroup.umccore.ui.model.MenuButton;
import net.aikeigroup.umccore.ui.model.MenuDefinition;
import net.aikeigroup.umccore.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Executes a button's list of {@link MenuAction}s for a player.
 *
 * <p>Security model: {@code RUN_COMMAND} runs with the player's own
 * permissions; {@code CONSOLE_COMMAND} runs from console but can only ever be
 * authored in server-side menu files by admins — players cannot inject them at
 * runtime. Placeholders in arguments are resolved per-player, including the
 * {@code {input_<key>}} tokens for any form inputs the player filled in.</p>
 */
public final class ActionExecutor {

    private final UMCCore plugin;

    public ActionExecutor(UMCCore plugin) {
        this.plugin = plugin;
    }

    /** Simple entry point (no menu/input context) — used by legacy callers. */
    public void run(Player player, MenuButton button) {
        run(player, button.actions(), null, 0, Map.of());
    }

    /** Full entry point carrying the source menu, page, and input values. */
    public void run(Player player, MenuButton button, MenuDefinition menu, int page,
                    Map<String, String> inputs) {
        run(player, button.actions(), menu, page, inputs);
    }

    /** Runs a list of actions in order with full context. */
    public void run(Player player, List<MenuAction> actions, MenuDefinition menu, int page,
                    Map<String, String> inputs) {
        for (MenuAction action : actions) {
            if (action.delayTicks() > 0) {
                // Deferred action (e.g. "tag set fipp<delay=100>"): run later so
                // an earlier CLOSE takes effect first, mirroring DeluxeMenus.
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> safeDispatch(player, action, menu, page, inputs), action.delayTicks());
            } else {
                safeDispatch(player, action, menu, page, inputs);
            }
        }
    }

    private void safeDispatch(Player player, MenuAction action, MenuDefinition menu, int page,
                              Map<String, String> inputs) {
        try {
            dispatch(player, action, menu, page, inputs);
        } catch (Exception e) {
            plugin.getLogger().warning("Menu action failed (" + action.type()
                    + ":" + action.argument() + "): " + e.getMessage());
        }
    }

    private void dispatch(Player player, MenuAction action, MenuDefinition menu, int page,
                          Map<String, String> inputs) {
        // Resolve input tokens first, then PlaceholderAPI (per-player).
        String arg = applyInputs(action.argument(), inputs);
        arg = plugin.text().resolve(player, arg);

        switch (action.type()) {
            case RUN_COMMAND -> player.performCommand(stripSlash(arg));
            case CONSOLE_COMMAND ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(arg));
            case OPEN_MENU -> openMenu(player, arg);
            case PAGE -> gotoPage(player, menu, page, arg);
            case BACK -> plugin.menuService().back(player);
            case MESSAGE -> player.sendMessage(Text.mm(arg));
            case BROADCAST -> Bukkit.getServer().sendMessage(Text.mm(arg));
            case TITLE -> showTitle(player, arg);
            case OPEN_URL -> player.sendMessage(
                    Text.mm("<gray>Open: </gray>").append(
                            Component.text(arg).clickEvent(ClickEvent.openUrl(arg))));
            case SOUND -> playSound(player, arg);
            case TELEPORT -> teleport(player, arg);
            case CLOSE -> {
                player.closeInventory();
                player.closeDialog();
            }
        }
    }

    /** Replaces {@code {input_<key>}} tokens with the player's entered values. */
    private String applyInputs(String raw, Map<String, String> inputs) {
        if (raw == null || inputs == null || inputs.isEmpty() || raw.indexOf('{') < 0) {
            return raw == null ? "" : raw;
        }
        String out = raw;
        for (Map.Entry<String, String> e : inputs.entrySet()) {
            out = out.replace("{input_" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    /** OPEN_MENU argument may be {@code id} or {@code id:page}. */
    private void openMenu(Player player, String arg) {
        int colon = arg.lastIndexOf(':');
        if (colon > 0) {
            String id = arg.substring(0, colon);
            try {
                int page = Integer.parseInt(arg.substring(colon + 1).trim());
                plugin.menuService().open(player, id, page, true);
                return;
            } catch (NumberFormatException ignored) {
                // ':' wasn't a page number — treat whole arg as a menu id.
            }
        }
        plugin.menuService().open(player, arg);
    }

    /** PAGE argument: {@code next}, {@code prev}/{@code back}, or an absolute index. */
    private void gotoPage(Player player, MenuDefinition menu, int page, String arg) {
        if (menu == null) {
            return;
        }
        String a = arg.trim().toLowerCase(Locale.ROOT);
        int target = switch (a) {
            case "next", "+1", "" -> page + 1;
            case "prev", "previous", "-1" -> page - 1;
            case "first" -> 0;
            case "last" -> menu.pageCount() - 1;
            default -> {
                try {
                    yield Integer.parseInt(a);
                } catch (NumberFormatException e) {
                    yield page;
                }
            }
        };
        plugin.menuService().openPage(player, menu.id(), target);
    }

    /**
     * TITLE argument: {@code title;subtitle[;fadeIn;stay;fadeOut]} where the
     * times are in ticks (DeluxeMenus convention). title/subtitle are MiniMessage.
     */
    private void showTitle(Player player, String spec) {
        String[] parts = spec.split(";", -1);
        Component title = Text.mm(parts.length > 0 ? parts[0] : "");
        Component subtitle = Text.mm(parts.length > 1 ? parts[1] : "");
        long fadeIn = parts.length > 2 ? parseTicks(parts[2], 10) : 10;
        long stay = parts.length > 3 ? parseTicks(parts[3], 60) : 60;
        long fadeOut = parts.length > 4 ? parseTicks(parts[4], 10) : 10;
        Title.Times times = Title.Times.times(
                java.time.Duration.ofMillis(fadeIn * 50),
                java.time.Duration.ofMillis(stay * 50),
                java.time.Duration.ofMillis(fadeOut * 50));
        player.showTitle(Title.title(title, subtitle, times));
    }

    private long parseTicks(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private String stripSlash(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }

    private void playSound(Player player, String key) {
        try {
            Sound sound = Sound.valueOf(key.toUpperCase(Locale.ROOT));
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
