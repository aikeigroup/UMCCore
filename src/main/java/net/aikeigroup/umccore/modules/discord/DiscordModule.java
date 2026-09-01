package net.aikeigroup.umccore.modules.discord;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.modules.performance.PerformanceModule;
import net.aikeigroup.umccore.modules.performance.ServerStats;
import org.bukkit.configuration.file.YamlConfiguration;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Posts and continuously edits Discord status embeds via DiscordSRV.
 *
 * <p>Rather than spamming new messages, the module keeps one message per
 * configured embed and edits it on an interval. Message IDs are persisted to
 * {@code data/discord-state.yml} so the same message keeps being edited across
 * reloads and restarts.</p>
 *
 * <p>All DiscordSRV/JDA access happens off the main thread (async timer) since
 * it performs network I/O. Stats are read from the performance module snapshot,
 * which is thread-safe. The whole module degrades gracefully: if DiscordSRV is
 * absent or a channel is invalid, it logs a warning and stays idle.</p>
 */
public final class DiscordModule extends AbstractModule {

    private YamlConfiguration state;
    private File stateFile;
    private final List<EmbedSpec> embeds = new ArrayList<>();

    public DiscordModule() {
        super("discord");
    }

    @Override
    protected void enable() {
        if (!plugin.integrations().hasDiscordSrv()) {
            plugin.getLogger().warning("DiscordSRV not found; discord module idle.");
            return;
        }

        loadState();
        loadEmbeds();
        if (embeds.isEmpty()) {
            plugin.getLogger().info("No Discord embeds configured; discord module idle.");
            return;
        }

        int intervalSeconds = Math.max(15, config().getInt("update-interval-seconds", 30));
        long periodTicks = intervalSeconds * 20L;

        // Run async: network I/O must never touch the main thread.
        track(scheduler.runTimerAsync(this::updateAll, 100L, periodTicks));
    }

    private void loadState() {
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        stateFile = new File(dataDir, "discord-state.yml");
        state = YamlConfiguration.loadConfiguration(stateFile);
    }

    private void loadEmbeds() {
        embeds.clear();
        int interval = config().getInt("update-interval-seconds", 30);
        for (java.util.Map<?, ?> map : config().getMapList("embeds")) {
            EmbedSpec spec = EmbedSpec.fromMap(map, interval);
            if (spec != null && spec.enabled()) {
                embeds.add(spec);
            }
        }
    }

    /** Called on the async timer: builds and pushes/edits every embed. */
    private void updateAll() {
        DiscordSRV srv;
        try {
            srv = DiscordSRV.getPlugin();
        } catch (Throwable t) {
            return; // DiscordSRV not ready
        }
        if (srv == null || srv.getJda() == null) {
            return;
        }

        ServerStats stats = currentStats();
        for (EmbedSpec spec : embeds) {
            try {
                updateOne(srv, spec, stats);
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to update Discord embed '" + spec.id()
                        + "': " + t.getMessage());
            }
        }
    }

    private void updateOne(DiscordSRV srv, EmbedSpec spec, ServerStats stats) {
        TextChannel channel = resolveChannel(srv, spec.channel());
        if (channel == null) {
            plugin.getLogger().warning("Discord channel not found for embed '" + spec.id()
                    + "': " + spec.channel());
            return;
        }

        MessageEmbed embed = buildEmbed(spec, stats);
        String key = "messages." + spec.id();
        String messageId = state.getString(key);

        if (messageId != null) {
            // Edit the existing message; if it was deleted, fall back to sending.
            channel.editMessageEmbedsById(messageId, embed).queue(
                    success -> { /* edited in place */ },
                    failure -> sendNew(channel, spec, embed, key));
        } else {
            sendNew(channel, spec, embed, key);
        }
    }

    private void sendNew(TextChannel channel, EmbedSpec spec, MessageEmbed embed, String key) {
        channel.sendMessageEmbeds(embed).queue(message -> {
            state.set(key, message.getId());
            saveState();
        }, failure -> plugin.getLogger().warning("Could not send Discord embed '" + spec.id()
                + "': " + failure.getMessage()));
    }

    private TextChannel resolveChannel(DiscordSRV srv, String channel) {
        // Try DiscordSRV's named game-channel mapping first, then raw ID.
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

    private MessageEmbed buildEmbed(EmbedSpec spec, ServerStats stats) {
        EmbedBuilder b = new EmbedBuilder();
        b.setColor(new Color(spec.resolveColor(stats)));
        if (!spec.title().isBlank()) {
            b.setTitle(tokens(spec.title(), stats, spec));
        }
        if (!spec.description().isBlank()) {
            b.setDescription(tokens(spec.description(), stats, spec));
        }
        if (spec.thumbnailUrl() != null && !spec.thumbnailUrl().isBlank()) {
            b.setThumbnail(spec.thumbnailUrl());
        }
        for (EmbedSpec.Field field : spec.fields()) {
            b.addField(tokens(field.name(), stats, spec), tokens(field.value(), stats, spec), field.inline());
        }
        if (!spec.footer().isBlank()) {
            b.setFooter(tokens(spec.footer(), stats, spec));
        }
        if (spec.showTimestamp()) {
            b.setTimestamp(Instant.now());
        }
        return b.build();
    }

    /** Replaces both UMCCore tokens and PlaceholderAPI (server-scope) tokens. */
    private String tokens(String input, ServerStats stats, EmbedSpec spec) {
        String out = input
                .replace("{tps}", stats.tpsString())
                .replace("{mspt}", stats.msptString())
                .replace("{online}", String.valueOf(stats.onlinePlayers()))
                .replace("{max}", String.valueOf(plugin.getServer().getMaxPlayers()))
                .replace("{ram_used}", String.valueOf(stats.ramUsedMb()))
                .replace("{ram_max}", String.valueOf(stats.ramMaxMb()))
                .replace("{uptime}", stats.uptimeString())
                .replace("{world_entities}", String.valueOf(stats.entities()))
                .replace("{interval}", String.valueOf(spec.intervalSeconds()));
        // Server-scope PAPI (null player) for %server_*% style placeholders.
        return plugin.text().resolve(null, out);
    }

    private ServerStats currentStats() {
        return plugin.modules().get("performance")
                .filter(m -> m instanceof PerformanceModule)
                .map(m -> ((PerformanceModule) m).stats())
                .orElse(ServerStats.empty());
    }

    // Synchronized: JDA delivers .queue() callbacks on its own threads, so
    // multiple embeds can finish sending at once and race on the state file.
    private synchronized void saveState() {
        try {
            state.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save Discord state: " + e.getMessage());
        }
    }

    /**
     * Forces an immediate update (used by {@code /umccore discord update}).
     * Runs the update on an async task to keep network I/O off the main thread.
     */
    public void forceUpdate() {
        scheduler.runAsync(this::updateAll);
    }
}
