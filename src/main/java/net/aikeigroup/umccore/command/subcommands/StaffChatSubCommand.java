package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import net.aikeigroup.umccore.modules.staffchat.StaffChatModule;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /umccore staffchat [toggle|<message>]} — staff chat command under the
 * root {@code /umccore} router.
 */
public final class StaffChatSubCommand implements SubCommand {

    @Override
    public String name() {
        return "staffchat";
    }

    @Override
    public List<String> aliases() {
        return List.of("sc");
    }

    @Override
    public String permission() {
        return "umccore.staffchat.use";
    }

    @Override
    public String usage() {
        return "/umccore staffchat [toggle|<message>]";
    }

    @Override
    public String description() {
        return "Toggle staff chat mode or send a staff message.";
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
        var opt = plugin.modules().get("staffchat");
        if (opt.isEmpty() || !plugin.modules().isActive("staffchat") || !(opt.get() instanceof StaffChatModule module)) {
            sender.sendMessage(Text.mm("<red>The StaffChat module is currently disabled.</red>"));
            return;
        }

        var config = plugin.configs().get("staffchat");
        if (config == null) {
            sender.sendMessage(Text.mm("<red>The StaffChat module is currently disabled.</red>"));
            return;
        }

        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("toggle"))) {
            if (!(sender instanceof Player player)) {
                String consoleMsg = config.getString("messages.console-only-direct",
                        "<red>Console cannot toggle staff chat. Use:</red> <white>/sc <message></white>");
                sender.sendMessage(Text.mm(consoleMsg));
                return;
            }

            boolean enabled = module.toggle(player);
            if (enabled) {
                String onMsg = config.getString("messages.toggle-on",
                        "<green>Staff chat mode <bold>ENABLED</bold>. Your messages will now be sent to staff chat.</green>");
                player.sendMessage(Text.mm(onMsg));
            } else {
                String offMsg = config.getString("messages.toggle-off",
                        "<yellow>Staff chat mode <bold>DISABLED</bold>. Your messages will now be sent to public chat.</yellow>");
                player.sendMessage(Text.mm(offMsg));
            }
            return;
        }

        String message = String.join(" ", args);
        module.sendFromMinecraft(sender, message);
    }
}
