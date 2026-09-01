package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import net.aikeigroup.umccore.command.UMCCommand;
import net.aikeigroup.umccore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * {@code /umccore help} — lists every sub-command the sender is allowed to use,
 * with its usage and description.
 */
public final class HelpCommand implements SubCommand {

    private final UMCCommand root;

    public HelpCommand(UMCCommand root) {
        this.root = root;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String permission() {
        return "umccore.command.help";
    }

    @Override
    public String usage() {
        return "/umccore help";
    }

    @Override
    public String description() {
        return "Show this help listing.";
    }

    @Override
    public void execute(UMCCore plugin, CommandSender sender, String[] args) {
        sender.sendMessage(Text.mm("<gradient:#00c6ff:#0072ff><b>UMCCore</b></gradient> <gray>— commands</gray>"));
        for (SubCommand sub : root.subCommands()) {
            if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
                continue;
            }
            Component line = Text.mm("<aqua>" + sub.usage() + "</aqua> <dark_gray>-</dark_gray> <gray>"
                    + sub.description() + "</gray>");
            sender.sendMessage(line);
        }
    }
}
