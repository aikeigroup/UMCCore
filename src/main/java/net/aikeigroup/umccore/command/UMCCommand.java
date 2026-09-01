package net.aikeigroup.umccore.command;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.subcommands.ActionBarCommand;
import net.aikeigroup.umccore.command.subcommands.ClearLagCommand;
import net.aikeigroup.umccore.command.subcommands.DiscordCommand;
import net.aikeigroup.umccore.command.subcommands.HelpCommand;
import net.aikeigroup.umccore.command.subcommands.MenuCommand;
import net.aikeigroup.umccore.command.subcommands.ModuleCommand;
import net.aikeigroup.umccore.command.subcommands.PerfCommand;
import net.aikeigroup.umccore.command.subcommands.ReloadCommand;
import net.aikeigroup.umccore.command.subcommands.StaffChatSubCommand;
import net.aikeigroup.umccore.command.subcommands.StackCommand;
import net.aikeigroup.umccore.command.subcommands.UpdateCommand;
import net.aikeigroup.umccore.command.subcommands.VersionCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Root handler for {@code /umccore} (alias {@code /umc}).
 *
 * <p>Owns the sub-command registry, dispatches execution, enforces permission
 * and player-only rules, and provides permission-aware tab completion:</p>
 * <ul>
 *   <li>Level 1 completes sub-command names the sender may use.</li>
 *   <li>Level 2+ delegates to the matched sub-command's {@link SubCommand#complete}.</li>
 * </ul>
 */
public final class UMCCommand implements CommandExecutor, TabCompleter {

    private final UMCCore plugin;

    /** Insertion-ordered so help lists commands in a stable order. */
    private final Map<String, SubCommand> byName = new LinkedHashMap<>();

    public UMCCommand(UMCCore plugin) {
        this.plugin = plugin;
        // Register M1 sub-commands. Later milestones append their own.
        register(new HelpCommand(this));
        register(new ReloadCommand());
        register(new VersionCommand());
        // M2 — performance & optimization.
        register(new PerfCommand());
        register(new ClearLagCommand());
        register(new StackCommand());
        register(new ModuleCommand());
        // M3 — UI.
        register(new MenuCommand());
        // M4 — action bar.
        register(new ActionBarCommand());
        // M5 — Discord.
        register(new DiscordCommand());
        // StaffChat.
        register(new StaffChatSubCommand());
        // M6 — self updater.
        register(new UpdateCommand());
    }

    /** Registers a sub-command under its name and each alias. */
    public void register(SubCommand sub) {
        byName.put(sub.name().toLowerCase(Locale.ROOT), sub);
        for (String alias : sub.aliases()) {
            byName.put(alias.toLowerCase(Locale.ROOT), sub);
        }
    }

    /** @return the distinct, ordered set of sub-commands (no alias duplicates). */
    public List<SubCommand> subCommands() {
        List<SubCommand> out = new ArrayList<>();
        for (SubCommand sub : byName.values()) {
            if (!out.contains(sub)) {
                out.add(sub);
            }
        }
        return out;
    }

    private boolean canUse(CommandSender sender, SubCommand sub) {
        return sub.permission() == null || sender.hasPermission(sub.permission());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            byName.get("help").execute(plugin, sender, new String[0]);
            return true;
        }

        SubCommand sub = byName.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            plugin.messages().send(sender, "command.unknown", "input", args[0]);
            return true;
        }

        if (!canUse(sender, sub)) {
            plugin.messages().send(sender, "command.no-permission");
            return true;
        }

        if (sub.playerOnly() && !(sender instanceof Player)) {
            plugin.messages().send(sender, "command.player-only");
            return true;
        }

        // Strip the sub-command token; pass the rest along.
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        sub.execute(plugin, sender, rest);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (SubCommand sub : subCommands()) {
                if (canUse(sender, sub)) {
                    names.add(sub.name());
                }
            }
            return filterPrefix(names, args[0]);
        }

        SubCommand sub = byName.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null || !canUse(sender, sub)) {
            return List.of();
        }

        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        List<String> suggestions = sub.complete(plugin, sender, rest);
        return filterPrefix(suggestions, rest[rest.length - 1]);
    }

    /** Filters suggestions by the (case-insensitive) partial token prefix. */
    private List<String> filterPrefix(List<String> options, String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
