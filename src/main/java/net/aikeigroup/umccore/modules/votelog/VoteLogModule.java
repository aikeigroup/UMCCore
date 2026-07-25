package net.aikeigroup.umccore.modules.votelog;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import net.aikeigroup.umccore.core.AbstractModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Posts a Discord embed (via DiscordSRV) each time a player votes, using the
 * {@link VotifierEvent} fired by NuVotifier/Votifier.
 *
 * <h2>Duplicate protection</h2>
 * <p>In a proxy → backend NuVotifier setup a single real vote can arrive as more
 * than one {@code VotifierEvent} on the same backend (forward broadcast or
 * retry). Since a legitimate vote for a given site happens at most once per day,
 * any second event carrying the same {@code username|serviceName} within a short
 * window is a duplicate and is dropped. The window is configurable
 * ({@code dedupe-window-seconds}; {@code 0} disables it).</p>
 *
 * <p>The dedupe cache is in-memory and per-server. If UMCCore's votelog runs on
 * several backends that all receive the same broadcast vote, enable this module
 * on ONE server only (or point each at a different channel) — see votelog.yml.</p>
 *
 * <p>All DiscordSRV/JDA work is done off the main thread; the vote event itself
 * is handled on whatever thread Votifier fires it, and we hop to an async task
 * before touching the network.</p>
 */
public final class VoteLogModule extends AbstractModule {

    /**
     * Recently-seen vote keys → epoch-millis of when they were seen, used to
     * drop duplicate votes. Guarded by its own monitor; entries are pruned lazily
     * on each vote. Kept small (one entry per distinct player+site per window).
     */
    private final Map<String, Long> recentVotes = new LinkedHashMap<>();

    private long dedupeWindowMs;

    public VoteLogModule() {
        super("votelog");
    }

    @Override
    protected void enable() {
        if (!plugin.integrations().hasVotifier()) {
            plugin.getLogger().warning("Votifier/NuVotifier not found; votelog module idle.");
            return;
        }
        if (!plugin.integrations().hasDiscordSrv()) {
            plugin.getLogger().warning("DiscordSRV not found; votelog module idle.");
            return;
        }

        dedupeWindowMs = Math.max(0, config().getInt("dedupe-window-seconds", 60)) * 1000L;

        // Guarded by hasVotifier() above so VotifierEvent is loadable here.
        listen(new VoteListener());
    }

    /** Listener kept as a nested type so the class only loads when registered. */
    private final class VoteListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onVote(VotifierEvent event) {
            Vote vote = event.getVote();
            if (vote == null) {
                return;
            }
            String player = safe(vote.getUsername());
            String service = safe(vote.getServiceName());

            if (isDuplicate(player, service)) {
                if (config().getBoolean("log-duplicates", true)) {
                    plugin.getLogger().info("Ignored duplicate vote: " + player
                            + " / " + service);
                }
                return;
            }

            String address = safe(vote.getAddress());
            // Network I/O must never touch the calling thread.
            scheduler.runAsync(() -> sendVoteEmbed(player, service, address));
        }
    }

    /**
     * @return true if a vote with the same player+service was already seen inside
     *         the dedupe window (and records this one otherwise). Always false
     *         when the window is disabled ({@code dedupe-window-seconds: 0}).
     */
    private boolean isDuplicate(String player, String service) {
        if (dedupeWindowMs <= 0) {
            return false;
        }
        // We deliberately avoid System.currentTimeMillis via nanoTime-free path:
        // Bukkit's scheduler thread is fine to read wall-clock here.
        long now = System.currentTimeMillis();
        String key = player.toLowerCase(Locale.ROOT) + "|" + service.toLowerCase(Locale.ROOT);
        synchronized (recentVotes) {
            // Prune expired entries so the map stays bounded.
            recentVotes.entrySet().removeIf(e -> now - e.getValue() > dedupeWindowMs);
            Long seen = recentVotes.get(key);
            if (seen != null && now - seen <= dedupeWindowMs) {
                return true;
            }
            recentVotes.put(key, now);
            return false;
        }
    }

    private void sendVoteEmbed(String player, String service, String address) {
        DiscordSRV srv;
        try {
            srv = DiscordSRV.getPlugin();
        } catch (Throwable t) {
            return; // DiscordSRV not ready
        }
        if (srv == null || srv.getJda() == null) {
            return;
        }

        String channelName = config().getString("channel", "global");
        TextChannel channel = resolveChannel(srv, channelName);
        if (channel == null) {
            plugin.getLogger().warning("votelog: Discord channel not found: " + channelName);
            return;
        }

        MessageEmbed embed = buildEmbed(player, service, address);
        channel.sendMessageEmbeds(embed).queue(
                ok -> { /* posted */ },
                failure -> plugin.getLogger().warning("votelog: could not send embed: "
                        + failure.getMessage()));
    }

    private MessageEmbed buildEmbed(String player, String service, String address) {
        ConfigurationSection embedCfg = config().getConfigurationSection("embed");
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("{player}", player);
        tokens.put("{service}", service);
        tokens.put("{address}", address);

        EmbedBuilder b = new EmbedBuilder();
        b.setColor(new Color(hex(embedCfg == null ? "#43b581"
                : embedCfg.getString("color", "#43b581"))));

        String title = embedCfg == null ? "New Vote!" : embedCfg.getString("title", "New Vote!");
        String description = embedCfg == null ? "" : embedCfg.getString("description", "");
        String thumbnail = embedCfg == null ? "" : embedCfg.getString("thumbnail-url", "");
        String footer = embedCfg == null ? "UMCCore" : embedCfg.getString("footer", "UMCCore");
        boolean showAvatar = embedCfg == null || embedCfg.getBoolean("show-player-head", true);

        if (title != null && !title.isBlank()) {
            b.setTitle(apply(title, tokens, player));
        }
        if (description != null && !description.isBlank()) {
            b.setDescription(apply(description, tokens, player));
        }
        if (showAvatar) {
            // Crossheads renders a Bedrock-friendly avatar by name; works for
            // both Java and Floodgate-prefixed names without a UUID lookup.
            b.setThumbnail("https://mc-heads.net/avatar/" + player);
        } else if (thumbnail != null && !thumbnail.isBlank()) {
            b.setThumbnail(apply(thumbnail, tokens, player));
        }

        for (FieldSpec f : fields(embedCfg)) {
            b.addField(apply(f.name, tokens, player), apply(f.value, tokens, player), f.inline);
        }

        if (footer != null && !footer.isBlank()) {
            b.setFooter(apply(footer, tokens, player));
        }
        if (embedCfg == null || embedCfg.getBoolean("show-timestamp", true)) {
            b.setTimestamp(Instant.now());
        }
        return b.build();
    }

    /** One configured embed field. */
    private record FieldSpec(String name, String value, boolean inline) {
    }

    private List<FieldSpec> fields(ConfigurationSection embedCfg) {
        List<FieldSpec> out = new ArrayList<>();
        if (embedCfg == null) {
            return out;
        }
        for (Map<?, ?> fm : embedCfg.getMapList("fields")) {
            Object name = fm.get("name");
            Object value = fm.get("value");
            Object inline = fm.get("inline");
            out.add(new FieldSpec(
                    name == null ? "" : String.valueOf(name),
                    value == null ? "" : String.valueOf(value),
                    Boolean.parseBoolean(String.valueOf(inline))));
        }
        return out;
    }

    /** Replaces vote tokens, then server/player-scope PlaceholderAPI tokens. */
    private String apply(String input, Map<String, String> tokens, String player) {
        String out = input;
        for (Map.Entry<String, String> e : tokens.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(player);
        return plugin.text().resolve(offline, out);
    }

    private TextChannel resolveChannel(DiscordSRV srv, String channel) {
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

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Parses a {@code #rrggbb} hex colour into an RGB int (0xRRGGBB). */
    private static int hex(String value) {
        if (value == null) {
            return 0x43B581;
        }
        String h = value.startsWith("#") ? value.substring(1) : value;
        try {
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0x43B581;
        }
    }
}
