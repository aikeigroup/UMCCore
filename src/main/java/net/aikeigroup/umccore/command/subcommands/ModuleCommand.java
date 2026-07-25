package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /umccore module <list|enable|disable> [name]} — inspect and toggle
 * feature modules at runtime without a full reload.
 *
 * <p>Runtime toggles are not persisted to config.yml; they last until the next
 * reload/restart. To make a change permanent, edit {@code modules.<name>} in
 * config.yml.</p>
 */
public final class ModuleCommand implements SubCommand {

    @Override
    public String name() {
        return "module";
    }

    @Override
    public String permission() {
        return "umccore.command.module";
    }

    @Override
    public String usage() {
        return "/umccore module <list|enable|disable> [name]";
    }

    @Override
    public String description() {
        return "List or toggle modules at runtime.";
    }

    @Override
    public List<String> complete(UMCCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("list", "enable", "disable");
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            return new ArrayList<>(plugin.modules().ids());
        }
        return List.of();
    }

    @Override
    public void execute(UMCCore plugin, CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Text.mm("<gradient:#00c6ff:#0072ff><b>Modules</b></gradient>"));
            for (String id : plugin.modules().ids()) {
                boolean active = plugin.modules().isActive(id);
                sender.sendMessage(Text.mm("  <gray>" + id + ":</gray> "
                        + (active ? "<green>active</green>" : "<red>inactive</red>")));
            }
            return;
        }

        if (args.length < 2) {
            plugin.messages().send(sender, "module.usage");
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String id = args[1].toLowerCase(Locale.ROOT);
        if (plugin.modules().get(id).isEmpty()) {
            plugin.messages().send(sender, "module.unknown", "name", id);
            return;
        }

        switch (action) {
            case "enable" -> {
                if (plugin.modules().enableById(id)) {
                    plugin.messages().send(sender, "module.enabled", "name", id);
                } else {
                    plugin.messages().send(sender, "module.already", "name", id, "state", "active");
                }
            }
            case "disable" -> {
                if (plugin.modules().disableById(id)) {
                    plugin.messages().send(sender, "module.disabled", "name", id);
                } else {
                    plugin.messages().send(sender, "module.already", "name", id, "state", "inactive");
                }
            }
            default -> plugin.messages().send(sender, "module.usage");
        }
    }
}
