package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import net.aikeigroup.umccore.modules.clearlag.ClearLagModule;
import org.bukkit.command.CommandSender;

/**
 * {@code /umccore clearlag} — triggers a manual entity-cleanup pass now.
 */
public final class ClearLagCommand implements SubCommand {

    @Override
    public String name() {
        return "clearlag";
    }

    @Override
    public String permission() {
        return "umccore.command.clearlag";
    }

    @Override
    public String usage() {
        return "/umccore clearlag";
    }

    @Override
    public String description() {
        return "Run a manual entity cleanup now.";
    }

    @Override
    public void execute(UMCCore plugin, CommandSender sender, String[] args) {
        var module = plugin.modules().get("clearlag").orElse(null);
        if (!(module instanceof ClearLagModule clm) || !plugin.modules().isActive("clearlag")) {
            plugin.messages().send(sender, "clearlag.disabled");
            return;
        }
        int removed = clm.runCleanup(false);
        plugin.messages().send(sender, "clearlag.manual", "count", String.valueOf(removed));
    }
}
