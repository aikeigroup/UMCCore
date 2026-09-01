package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import net.aikeigroup.umccore.modules.update.UpdateModule;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /umccore update [check|download]} — check for a newer release or
 * download it into the server update folder (applied on restart).
 */
public final class UpdateCommand implements SubCommand {

    @Override
    public String name() {
        return "update";
    }

    @Override
    public String permission() {
        return "umccore.command.update";
    }

    @Override
    public String usage() {
        return "/umccore update [check|download]";
    }

    @Override
    public String description() {
        return "Check for or download plugin updates.";
    }

    @Override
    public List<String> complete(UMCCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("check", "download");
        }
        return List.of();
    }

    @Override
    public void execute(UMCCore plugin, CommandSender sender, String[] args) {
        var module = plugin.modules().get("update").orElse(null);
        if (!(module instanceof UpdateModule updater) || !plugin.modules().isActive("update")) {
            plugin.messages().send(sender, "update.disabled");
            return;
        }

        String action = args.length > 0 ? args[0].toLowerCase() : "check";
        switch (action) {
            case "download" -> {
                String status = updater.download();
                plugin.messages().send(sender, "update.downloading", "status", status);
            }
            default -> {
                plugin.messages().send(sender, "update.checking");
                // Run the check async via the module's own client.
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    updater.check();
                    if (updater.updateAvailable()) {
                        plugin.messages().send(sender, "update.available",
                                "version", updater.latest().version());
                    } else {
                        plugin.messages().send(sender, "update.up-to-date");
                    }
                });
            }
        }
    }
}
