package net.aikeigroup.umccore.modules.staffchat;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import net.aikeigroup.umccore.UMCCore;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

/**
 * Bridges Minecraft staff chat with DiscordSRV.
 *
 * <p>Separated into its own class so DiscordSRV/JDA classes are only loaded
 * when DiscordSRV is confirmed present.</p>
 */
final class DiscordStaffChatBridge {

    private final UMCCore plugin;
    private final StaffChatModule module;

    DiscordStaffChatBridge(UMCCore plugin, StaffChatModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    void register() {
        DiscordSRV.api.subscribe(this);
    }

    void unregister() {
        try {
            DiscordSRV.api.unsubscribe(this);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Receives Discord messages and relays them to Minecraft if sent in the configured staff channel.
     */
    @Subscribe
    public void onDiscordMessage(DiscordGuildMessageReceivedEvent event) {
        if (event == null || event.getMessage() == null) {
            return;
        }

        FileConfiguration config = plugin.configs().get("staffchat");
        if (!config.getBoolean("discord.enabled", true) || !config.getBoolean("discord.discord-to-mc", true)) {
            return;
        }

        // Ignore bots and webhooks to avoid echoing our own messages
        if (event.getAuthor().isBot() || event.getMessage().isWebhookMessage()) {
            return;
        }

        String targetChannel = config.getString("discord.channel", "staff-chat");
        if (!isStaffChannel(event.getChannel(), targetChannel)) {
            return;
        }

        String content = event.getMessage().getContentDisplay();
        List<Message.Attachment> attachments = event.getMessage().getAttachments();
        if (attachments != null && !attachments.isEmpty()) {
            StringBuilder sb = new StringBuilder(content);
            for (Message.Attachment att : attachments) {
                if (!sb.isEmpty()) {
                    sb.append(" ");
                }
                sb.append("[").append(att.getFileName()).append("]");
            }
            content = sb.toString();
        }

        if (content.isBlank()) {
            return;
        }

        Member member = event.getMember();
        String authorName = member != null ? member.getEffectiveName() : event.getAuthor().getName();
        String roleName = "";
        if (member != null && !member.getRoles().isEmpty()) {
            roleName = member.getRoles().get(0).getName();
        }

        module.sendFromDiscord(authorName, roleName, content, event.getChannel().getName());
    }

    /**
     * Sends a Minecraft staff chat message to Discord.
     */
    void sendToDiscord(CommandSender sender, String message) {
        DiscordSRV srv;
        try {
            srv = DiscordSRV.getPlugin();
        } catch (Throwable t) {
            return;
        }
        if (srv == null || srv.getJda() == null) {
            return;
        }

        FileConfiguration config = plugin.configs().get("staffchat");
        String targetChannel = config.getString("discord.channel", "staff-chat");
        TextChannel channel = resolveChannel(srv, targetChannel);
        if (channel == null) {
            plugin.getLogger().warning("staffchat: Discord channel not found: " + targetChannel);
            return;
        }

        boolean sendAsEmbed = config.getBoolean("discord.send-as-embed", false);
        if (sendAsEmbed) {
            MessageEmbed embed = buildEmbed(config, sender, message);
            channel.sendMessageEmbeds(embed).queue(
                    ok -> {},
                    err -> plugin.getLogger().warning("staffchat: Failed to send embed to Discord: " + err.getMessage())
            );
        } else {
            String textFormat = config.getString("discord.text-format", "🛡️ **[Staff] {player}**: {message}");
            String text = textFormat
                    .replace("{player}", sender.getName())
                    .replace("{message}", message)
                    .replace("{server}", plugin.getServer().getName());
            channel.sendMessage(text).queue(
                    ok -> {},
                    err -> plugin.getLogger().warning("staffchat: Failed to send message to Discord: " + err.getMessage())
            );
        }
    }

    private MessageEmbed buildEmbed(FileConfiguration config, CommandSender sender, String message) {
        EmbedBuilder b = new EmbedBuilder();
        b.setColor(new Color(hex(config.getString("discord.embed.color", "#ff416c"))));

        String authorName = config.getString("discord.embed.author-name", "{player}")
                .replace("{player}", sender.getName())
                .replace("{server}", plugin.getServer().getName());
        String iconUrl = config.getString("discord.embed.author-icon-url", "https://mc-heads.net/avatar/{player}")
                .replace("{player}", sender.getName());
        b.setAuthor(authorName, null, iconUrl);

        String desc = config.getString("discord.embed.description", "{message}")
                .replace("{player}", sender.getName())
                .replace("{message}", message)
                .replace("{server}", plugin.getServer().getName());
        b.setDescription(desc);

        String footer = config.getString("discord.embed.footer", "UMCCore StaffChat")
                .replace("{player}", sender.getName())
                .replace("{server}", plugin.getServer().getName());
        b.setFooter(footer);

        if (config.getBoolean("discord.embed.show-timestamp", true)) {
            b.setTimestamp(Instant.now());
        }

        return b.build();
    }

    private boolean isStaffChannel(TextChannel channel, String configured) {
        if (channel == null || configured == null || configured.isBlank()) {
            return false;
        }
        if (channel.getId().equals(configured) || channel.getName().equalsIgnoreCase(configured)) {
            return true;
        }
        try {
            TextChannel resolved = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(configured);
            if (resolved != null && resolved.getId().equals(channel.getId())) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
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

    private static int hex(String value) {
        if (value == null) {
            return 0xFF416C;
        }
        String h = value.startsWith("#") ? value.substring(1) : value;
        try {
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFF416C;
        }
    }
}
