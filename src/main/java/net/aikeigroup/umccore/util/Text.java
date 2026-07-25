package net.aikeigroup.umccore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Central text utility for UMCCore.
 *
 * <p>All user-facing text in the plugin flows through MiniMessage so colors,
 * gradients, and formatting stay consistent across chat, action bars, menus,
 * and Discord embeds.</p>
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    /**
     * Parses a MiniMessage string into an Adventure {@link Component}.
     *
     * @param input the raw MiniMessage string (never {@code null})
     * @return the parsed component
     */
    public static Component mm(String input) {
        if (input == null) {
            return Component.empty();
        }
        return MINI.deserialize(input);
    }

    /**
     * Strips all formatting and returns the plain visible text of a component.
     *
     * @param component the component to flatten
     * @return plain text without color/format codes
     */
    public static String plain(Component component) {
        if (component == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Convenience: parse a MiniMessage string then return its plain form.
     * Useful for legacy APIs that only accept {@code String}.
     *
     * @param input the raw MiniMessage string
     * @return plain text
     */
    public static String plain(String input) {
        return plain(mm(input));
    }

    /**
     * @return the shared MiniMessage instance
     */
    public static MiniMessage miniMessage() {
        return MINI;
    }
}
