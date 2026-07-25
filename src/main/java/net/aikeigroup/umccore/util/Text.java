package net.aikeigroup.umccore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.regex.Pattern;

/**
 * Central text utility for UMCCore.
 *
 * <p>All user-facing text in the plugin flows through MiniMessage so colors,
 * gradients, and formatting stay consistent across chat, action bars, menus,
 * and Discord embeds.</p>
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** Legacy '&' codes (colours, formats, and '&#rrggbb' hex) for compatibility. */
    private static final LegacyComponentSerializer LEGACY_AMP =
            LegacyComponentSerializer.builder().character('&').hexColors().hexCharacter('#').build();

    /** Matches a legacy code: {@code &0-9a-fk-or} or a {@code &#rrggbb} hex code. */
    private static final Pattern LEGACY_CODE =
            Pattern.compile("&(?:#[0-9A-Fa-f]{6}|[0-9A-Fa-fk-oK-OrRxX])");

    private Text() {
    }

    /**
     * Parses a text string into an Adventure {@link Component}.
     *
     * <p>Primary format is MiniMessage. For convenience — and so menus/messages
     * copied from legacy plugins (DeluxeMenus, Essentials, …) work unchanged —
     * any legacy {@code &} colour/format codes present are converted to
     * MiniMessage first. Text with no {@code &} codes is treated as pure
     * MiniMessage, so existing configs are unaffected.</p>
     *
     * @param input the raw string (never {@code null})
     * @return the parsed component
     */
    public static Component mm(String input) {
        if (input == null) {
            return Component.empty();
        }
        return MINI.deserialize(preprocessLegacy(input));
    }

    /**
     * If the input contains legacy {@code &} codes, convert just those codes into
     * their MiniMessage tag equivalents while leaving any existing MiniMessage
     * tags intact. No-op when no {@code &} codes are present.
     */
    private static String preprocessLegacy(String input) {
        if (input.indexOf('&') < 0 || !LEGACY_CODE.matcher(input).find()) {
            return input;
        }
        // Deserialize the legacy codes to a component, then re-serialize as
        // MiniMessage tags. Any pre-existing MiniMessage tags are plain text to
        // the legacy serializer, so they survive and are re-parsed by mm().
        Component legacy = LEGACY_AMP.deserialize(input);
        return MINI.serialize(legacy);
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
