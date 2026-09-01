package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import net.aikeigroup.umccore.modules.discord.DiscordModule;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /umccore discord update} — forces an immediate refresh of all Discord
 * status embeds.
 */
public final class DiscordCommand implements SubCommand {

    @Override
    public String name() {
        return "discord";
    }

    @Override
    public String permission() {
        return "umccore.command.discord";
    }

    @Override
    public String usage() {
        return "/umccore discord update";
    }

    @Override
    public String description() {
        return "Force a Discord embed update.";
    }

    @Override
    public List<String> complete(UMCCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("update");
        }
        return List.of();
    }

    @Override
    public void execute(UMCCore plugin, CommandSender sender, String[] args) {
        var module = plugin.modules().get("discord").orElse(null);
        if (!(module instanceof DiscordModule discord) || !plugin.modules().isActive("discord")) {
            plugin.messages().send(sender, "discord.disabled");
            return;
        }
        discord.forceUpdate();
        plugin.messages().send(sender, "discord.updating");
    }
}
