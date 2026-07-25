package net.aikeigroup.umccore.command.subcommands;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.command.SubCommand;
import net.aikeigroup.umccore.integrations.IntegrationManager;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.command.CommandSender;

/**
 * {@code /umccore version} — prints the plugin version and which optional
 * integrations were detected at runtime.
 */
public final class VersionCommand implements SubCommand {

    @Override
    public String name() {
        return "version";
    }

    @Override
    public String permission() {
        return "umccore.command.version";
    }

    @Override
    public String usage() {
        return "/umccore version";
    }

    @Override
    public String description() {
        return "Show version and detected integrations.";
    }

    @Override
    public void execute(UMCCore plugin, CommandSender sender, String[] args) {
        sender.sendMessage(Text.mm("<gradient:#00c6ff:#0072ff><b>UMCCore</b></gradient> <gray>v"
                + plugin.getPluginMeta().getVersion() + "</gray>"));
        sender.sendMessage(Text.mm("<gray>by aikeigroup.net</gray>"));

        IntegrationManager i = plugin.integrations();
        sender.sendMessage(Text.mm("<gray>Integrations:</gray>"));
        sender.sendMessage(flag("PlaceholderAPI", i.hasPlaceholderApi()));
        sender.sendMessage(flag("Vault", i.hasVault()));
        sender.sendMessage(flag("LuckPerms", i.hasLuckPerms()));
        sender.sendMessage(flag("DiscordSRV", i.hasDiscordSrv()));
        sender.sendMessage(flag("Floodgate", i.hasFloodgate()));
    }

    private net.kyori.adventure.text.Component flag(String label, boolean present) {
        String mark = present ? "<green>[+]</green>" : "<red>[-]</red>";
        return Text.mm("  " + mark + " <gray>" + label + "</gray>");
    }
}
