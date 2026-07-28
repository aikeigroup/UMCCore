package net.aikeigroup.umccore.modules.lifecycle;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import net.aikeigroup.umccore.UMCCore;
import org.bukkit.configuration.file.FileConfiguration;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sends a one-off Discord embed describing a shutdown or crash, via DiscordSRV.
 *
 * <p>Kept in its own class so DiscordSRV/JDA symbols only load when DiscordSRV is
 * actually present (the caller guards with {@code hasDiscordSrv()}). This runs
 * during shutdown, so the send is issued synchronously and best-effort: if JDA is
 * already torn down the {@code .complete()}/{@code queue()} simply fails and is
 * swallowed — we never block the server from stopping.</p>
 */
final class DiscordNotifier {

    private DiscordNotifier() {
    }

    static void send(UMCCore plugin, FileConfiguration config, Map<String, Object> report) {
        DiscordSRV srv;
        try {
            srv = DiscordSRV.getPlugin();
        } catch (Throwable t) {
            return;
        }
        if (srv == null || srv.getJda() == null) {
            return;
        }

        String channelName = config.getString("discord.channel", "global");
        TextChannel channel = resolveChannel(srv, channelName);
        if (channel == null) {
            plugin.getLogger().warning("lifecycle: Discord channel not found: " + channelName);
            return;
        }

        String event = String.valueOf(report.getOrDefault("event", "SHUTDOWN"));
        String classification = String.valueOf(report.getOrDefault("classification", "UNKNOWN"));
        boolean crash = "CRASH".equalsIgnoreCase(event);

        EmbedBuilder b = new EmbedBuilder();
        b.setColor(new Color(crash ? 0xE74C3C : 0xF1C40F));
        b.setTitle(crash ? "🔴 Server Crash Detected" : "🟡 Server " + classification);

        Object triggeredBy = report.get("triggered-by");
        if (triggeredBy instanceof Map<?, ?> actor) {
            String name = String.valueOf(actor.get("name"));
            Object plg = actor.get("initiating-plugin");
            String via = String.valueOf(actor.get("via"));
            b.addField("Triggered by", name, true);
            if (plg != null) {
                b.addField("Plugin", String.valueOf(plg), true);
            }
            b.addField("How", via, false);
        }

        addIfPresent(b, "Classification", classification, true);
        addIfPresent(b, "Uptime (s)", str(report.get("uptime-seconds")), true);
        addIfPresent(b, "Last TPS", str(report.get("tps")), true);
        addIfPresent(b, "Online", str(report.get("online-count")), true);
        if (crash) {
            addIfPresent(b, "Approx. death", str(report.get("approx-death-at")), true);
        }

        Object players = report.get("online-players");
        if (players instanceof List<?> list && !list.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Object o : list) {
                sb.append("• ").append(o).append('\n');
            }
            String value = sb.length() > 1000 ? sb.substring(0, 1000) + "…" : sb.toString();
            b.addField("Players online", value, false);
        }

        b.setFooter("UMCCore • lifecycle");
        b.setTimestamp(Instant.now());
        MessageEmbed embed = b.build();

        try {
            // Synchronous during shutdown so the message actually leaves before
            // JDA is closed; guarded so a failure never stalls the stop.
            channel.sendMessageEmbeds(embed).complete();
        } catch (Throwable t) {
            try {
                channel.sendMessageEmbeds(embed).queue();
            } catch (Throwable ignored) {
                // Best effort — DiscordSRV/JDA already shutting down.
            }
        }
    }

    private static void addIfPresent(EmbedBuilder b, String name, String value, boolean inline) {
        if (value != null && !value.isBlank() && !"null".equals(value)) {
            b.addField(name, value, inline);
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static TextChannel resolveChannel(DiscordSRV srv, String channel) {
        TextChannel byName = srv.getDestinationTextChannelForGameChannelName(channel);
        if (byName != null) {
            return byName;
        }
        try {
            return srv.getJda().getTextChannelById(channel);
        } catch (Throwable t) {
            return null;
        }
    }
}
