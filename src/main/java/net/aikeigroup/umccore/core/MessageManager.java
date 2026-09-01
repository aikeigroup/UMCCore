package net.aikeigroup.umccore.core;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves and sends user-facing messages from {@code messages.yml}.
 *
 * <p>Messages are MiniMessage strings keyed by a dotted path. A global prefix is
 * prepended to most messages. Simple {@code {placeholder}} tokens are replaced
 * before MiniMessage parsing; PlaceholderAPI (if present) is applied by the
 * integration layer for per-player text.</p>
 */
public final class MessageManager {

    private final UMCCore plugin;
    private String prefix = "";

    public MessageManager(UMCCore plugin) {
        this.plugin = plugin;
    }

    /** Re-reads the prefix from the freshly-loaded messages config. */
    public void reload() {
        this.prefix = plugin.configs().messages().getString("prefix", "<gray>[<aqua>UMC</aqua>]</gray> ");
    }

    /**
     * Looks up a message by key and returns it as a parsed component with the
     * prefix applied and {@code {token}} placeholders substituted.
     *
     * @param key          dotted message key, e.g. {@code "reload.success"}
     * @param replacements alternating token/value pairs (token without braces)
     * @return the parsed component
     */
    public Component get(String key, String... replacements) {
        String raw = plugin.configs().messages().getString(key, key);
        raw = applyTokens(raw, buildMap(replacements));
        return Text.mm(prefix + raw);
    }

    /**
     * Like {@link #get} but without the prefix (for multi-line bodies, menus).
     */
    public Component getRaw(String key, String... replacements) {
        String raw = plugin.configs().messages().getString(key, key);
        raw = applyTokens(raw, buildMap(replacements));
        return Text.mm(raw);
    }

    /**
     * Sends a keyed message to a recipient.
     *
     * @param sender       who receives it
     * @param key          message key
     * @param replacements alternating token/value pairs
     */
    public void send(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(get(key, replacements));
    }

    private Map<String, String> buildMap(String... replacements) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            map.put(replacements[i], replacements[i + 1]);
        }
        return map;
    }

    private String applyTokens(String input, Map<String, String> tokens) {
        if (tokens.isEmpty() || input == null) {
            return input;
        }
        String out = input;
        for (Map.Entry<String, String> e : tokens.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    public String prefix() {
        return prefix;
    }
}
