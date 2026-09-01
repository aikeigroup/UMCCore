package net.aikeigroup.umccore.util;

import net.aikeigroup.umccore.UMCCore;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Per-player text resolution pipeline.
 *
 * <p>Resolves PlaceholderAPI tokens (when PAPI is present and enabled) then
 * parses the result as MiniMessage. All user-facing text that can contain
 * placeholders — menus, action bar, Discord embeds — flows through here so the
 * behaviour is consistent and PAPI stays an optional soft dependency.</p>
 */
public final class TextService {

    private final UMCCore plugin;

    public TextService(UMCCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolves PlaceholderAPI tokens in {@code input} for a player, returning a
     * plain (still-MiniMessage) string. Safe to call when PAPI is absent — it
     * returns the input unchanged.
     *
     * @param player the context player (nullable for server-wide placeholders)
     * @param input  raw text possibly containing {@code %papi%} tokens
     * @return the placeholder-resolved string
     */
    public String resolve(OfflinePlayer player, String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        if (plugin.integrations() != null
                && plugin.integrations().hasPlaceholderApi()
                && plugin.configs().main().getBoolean("integrations.placeholderapi.resolve-in-text", true)) {
            try {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, input);
            } catch (Throwable t) {
                // PAPI not actually loadable at runtime; degrade gracefully.
                return input;
            }
        }
        return input;
    }

    /**
     * Resolves placeholders then parses as MiniMessage into a component.
     *
     * @param player context player (nullable)
     * @param input  raw MiniMessage text with optional PAPI tokens
     * @return the parsed component
     */
    public Component render(OfflinePlayer player, String input) {
        return Text.mm(resolve(player, input));
    }

    /** Convenience overload for online players. */
    public Component render(Player player, String input) {
        return render((OfflinePlayer) player, input);
    }
}
