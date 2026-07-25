package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import net.aikeigroup.umccore.modules.actionbar.ActionBarModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /umccore actionbar toggle} — show/hide your own animated action bar.
 */
public final class ActionBarCommand implements SubCommand {

    @Override
    public String name() {
        return "actionbar";
    }

    @Override
    public String permission() {
        return "umccore.command.actionbar";
    }

    @Override
    public String usage() {
        return "/umccore actionbar toggle";
    }

    @Override
    public String description() {
        return "Toggle your own action bar.";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public List<String> complete(UMCCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("toggle");
        }
        return List.of();
    }

    @Override
    public void execute(UMCCore plugin, CommandSender sender, String[] args) {
        Player player = (Player) sender;
        var module = plugin.modules().get("actionbar").orElse(null);
        if (!(module instanceof ActionBarModule bar) || !plugin.modules().isActive("actionbar")) {
            plugin.messages().send(sender, "actionbar.disabled");
            return;
        }
        if (!bar.isToggleAllowed()) {
            plugin.messages().send(sender, "actionbar.toggle-disabled");
            return;
        }
        boolean visible = bar.toggle(player);
        plugin.messages().send(sender, visible ? "actionbar.shown" : "actionbar.hidden");
    }
}
