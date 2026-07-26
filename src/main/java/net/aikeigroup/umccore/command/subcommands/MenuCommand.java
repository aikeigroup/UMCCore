package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /umccore menu <name> [player]} — opens a menu for yourself, or (with
 * {@code umccore.command.menu.others}) for another player.
 *
 * <p>Tab completion only suggests menus the sender has permission to open, so
 * players never see menus they cannot use.</p>
 */
public final class MenuCommand implements SubCommand {

    @Override
    public String name() {
        return "menu";
    }

    @Override
    public String permission() {
        return "umccore.command.menu";
    }

    @Override
    public String usage() {
        return "/umccore menu <name> [player]";
    }

    @Override
    public String description() {
        return "Open a menu.";
    }

    @Override
    public List<String> complete(UMCCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (String id : plugin.menuService().ids()) {
                if (sender.hasPermission("umccore.menu." + id)) {
                    names.add(id);
                }
            }
            return names;
        }
        if (args.length == 2 && sender.hasPermission("umccore.command.menu.others")) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p.getName());
            }
            return players;
        }
        return List.of();
    }

    @Override
    public void execute(UMCCore plugin, CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "menu.usage");
            return;
        }
        String menuId = args[0];

        // Target another player (requires extra permission).
        if (args.length >= 2) {
            if (!sender.hasPermission("umccore.command.menu.others")) {
                plugin.messages().send(sender, "command.no-permission");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "menu.player-offline", "player", args[1]);
                return;
            }
            plugin.menuService().open(target, menuId);
            plugin.messages().send(sender, "menu.opened-for",
                    "name", menuId, "player", target.getName());
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player-only");
            return;
        }
        plugin.menuService().open(player, menuId);
    }
}
