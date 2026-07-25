package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;

import java.util.List;

/**
 * {@code /umccore stack info} — reports current stacker state: how many mob and
 * item entities exist across worlds. Useful to see the effect of stacking.
 */
public final class StackCommand implements SubCommand {

    @Override
    public String name() {
        return "stack";
    }

    @Override
    public String permission() {
        return "umccore.command.stack";
    }

    @Override
    public String usage() {
        return "/umccore stack info";
    }

    @Override
    public String description() {
        return "Inspect mob/item stacker state.";
    }

    @Override
    public List<String> complete(UMCCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("info");
        }
        return List.of();
    }

    @Override
    public void execute(UMCCore plugin, CommandSender sender, String[] args) {
        int mobs = 0;
        int items = 0;
        for (World world : Bukkit.getWorlds()) {
            mobs += world.getEntitiesByClass(Mob.class).size();
            items += world.getEntitiesByClass(Item.class).size();
        }
        boolean mobOn = plugin.modules().isActive("mobstacker");
        boolean itemOn = plugin.modules().isActive("itemstacker");

        sender.sendMessage(Text.mm("<gradient:#00c6ff:#0072ff><b>Stacker</b></gradient>"));
        sender.sendMessage(Text.mm("<gray>Mob stacker:</gray> " + onOff(mobOn)
                + "  <gray>live mobs:</gray> <white>" + mobs + "</white>"));
        sender.sendMessage(Text.mm("<gray>Item stacker:</gray> " + onOff(itemOn)
                + "  <gray>live item drops:</gray> <white>" + items + "</white>"));
    }

    private String onOff(boolean on) {
        return on ? "<green>on</green>" : "<red>off</red>";
    }
}
