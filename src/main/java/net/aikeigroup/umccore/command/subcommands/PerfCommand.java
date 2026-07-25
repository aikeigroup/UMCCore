package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import net.aikeigroup.umccore.modules.performance.PerformanceModule;
import net.aikeigroup.umccore.modules.performance.ServerStats;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.command.CommandSender;

/**
 * {@code /umccore perf} — prints a live performance summary (TPS/MSPT/RAM/
 * entities). Reads the latest snapshot from the performance module.
 */
public final class PerfCommand implements SubCommand {

    @Override
    public String name() {
        return "perf";
    }

    @Override
    public String permission() {
        return "umccore.command.perf";
    }

    @Override
    public String usage() {
        return "/umccore perf";
    }

    @Override
    public String description() {
        return "Show a live performance summary.";
    }

    @Override
    public void execute(UMCCore plugin, CommandSender sender, String[] args) {
        var module = plugin.modules().get("performance").orElse(null);
        if (!(module instanceof PerformanceModule perf) || !plugin.modules().isActive("performance")) {
            plugin.messages().send(sender, "perf.disabled");
            return;
        }
        ServerStats s = perf.stats();
        String tpsColor = s.tps() >= 18 ? "green" : s.tps() >= 15 ? "yellow" : "red";
        String msptColor = s.mspt() <= 40 ? "green" : s.mspt() <= 50 ? "yellow" : "red";

        sender.sendMessage(Text.mm("<gradient:#00c6ff:#0072ff><b>Performance</b></gradient>"));
        sender.sendMessage(Text.mm("<gray>TPS:</gray> <" + tpsColor + ">" + s.tpsString() + "</" + tpsColor + ">"
                + "  <gray>MSPT:</gray> <" + msptColor + ">" + s.msptString() + " ms</" + msptColor + ">"));
        sender.sendMessage(Text.mm("<gray>RAM:</gray> <white>" + s.ramUsedMb() + " / " + s.ramMaxMb() + " MB</white>"));
        sender.sendMessage(Text.mm("<gray>Chunks:</gray> <white>" + s.loadedChunks() + "</white>"
                + "  <gray>Entities:</gray> <white>" + s.entities() + "</white>"
                + "  <gray>Players:</gray> <white>" + s.onlinePlayers() + "</white>"));
        sender.sendMessage(Text.mm("<gray>Uptime:</gray> <white>" + s.uptimeString() + "</white>"));
    }
}
