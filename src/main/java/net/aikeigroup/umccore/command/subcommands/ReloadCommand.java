package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import net.aikeigroup.umccore.core.ReloadService;
import org.bukkit.command.CommandSender;

/**
 * {@code /umccore reload} — performs a full reload of the plugin (all modules
 * and config files) without restarting the server.
 */
public final class ReloadCommand implements SubCommand {

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return "umccore.command.reload";
    }

    @Override
    public String usage() {
        return "/umccore reload";
    }

    @Override
    public String description() {
        return "Fully reload all modules and configuration.";
    }

    @Override
    public void execute(UMCCore plugin, CommandSender sender, String[] args) {
        plugin.messages().send(sender, "reload.start");
        ReloadService.Result result = plugin.reloadService().reload();
        if (result.success()) {
            plugin.messages().send(sender, "reload.success",
                    "modules", String.valueOf(result.modulesEnabled()),
                    "ms", String.valueOf(result.millis()));
        } else {
            plugin.messages().send(sender, "reload.failure",
                    "error", result.error() == null ? "unknown" : result.error());
        }
    }
}
