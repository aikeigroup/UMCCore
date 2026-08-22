package net.aikeigroup.umccore.modules.staffchat;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Command executor for {@code /staffchat} and its alias {@code /sc}.
 *
 * <ul>
 *   <li>{@code /sc} — toggles staff chat mode for the executing player.</li>
 *   <li>{@code /sc <message>} — sends a direct message to staff chat.</li>
 * </ul>
 */
public final class StaffChatCommand implements CommandExecutor, TabCompleter {

    private final UMCCore plugin;

    public StaffChatCommand(UMCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        FileConfiguration config = plugin.configs().get("staffchat");

        if (!sender.hasPermission(StaffChatModule.PERM_USE) && !sender.isOp()) {
            String noPerm = config.getString("messages.no-permission",
                    "<red>You don't have permission to use staff chat.</red>");
            sender.sendMessage(Text.mm(noPerm));
            return true;
        }

        StaffChatModule module = plugin.modules().get("staffchat")
                .filter(m -> m instanceof StaffChatModule)
                .map(m -> (StaffChatModule) m)
                .orElse(null);

        if (module == null) {
            sender.sendMessage(Text.mm("<red>The StaffChat module is currently disabled.</red>"));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                String consoleMsg = config.getString("messages.console-only-direct",
                        "<red>Console cannot toggle staff chat. Use:</red> <white>/sc <message></white>");
                sender.sendMessage(Text.mm(consoleMsg));
                return true;
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
            return true;
        }

        String message = String.join(" ", args);
        module.sendFromMinecraft(sender, message);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
