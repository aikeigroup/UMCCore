package net.aikeigroup.umccore.command;

import net.aikeigroup.umccore.UMCCore;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * One sub-command of {@code /umccore} (e.g. {@code reload}, {@code version}).
 *
 * <p>Each sub-command declares its own name, permission, and usage, and
 * contributes its own tab completions. The root command dispatches to it and
 * enforces the permission before {@link #execute} runs.</p>
 */
public interface SubCommand {

    /** @return the primary name used to invoke this sub-command. */
    String name();

    /** @return alternative names, or an empty list. */
    default List<String> aliases() {
        return Collections.emptyList();
    }

    /**
     * @return the permission node required to see and run this sub-command,
     *         or {@code null} if everyone may use it.
     */
    String permission();

    /** @return short usage hint, e.g. {@code "/umccore menu <name> [player]"}. */
    String usage();

    /** @return one-line description for the help listing. */
    String description();

    /**
     * @return {@code true} if this sub-command may only be run by a player
     *         (not console). The root command enforces this.
     */
    default boolean playerOnly() {
        return false;
    }

    /**
     * Executes the sub-command. Permission and player-only checks have already
     * passed by the time this is called.
     *
     * @param plugin the plugin instance
     * @param sender the command sender
     * @param args   arguments <em>after</em> the sub-command name
     */
    void execute(UMCCore plugin, CommandSender sender, String[] args);

    /**
     * Contributes tab completions for the argument currently being typed.
     *
     * @param plugin the plugin instance
     * @param sender the command sender (for permission-aware suggestions)
     * @param args   arguments after the sub-command name; the last element is
     *               the partial token being completed
     * @return suggestions (unfiltered; the root command filters by prefix)
     */
    default List<String> complete(UMCCore plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
