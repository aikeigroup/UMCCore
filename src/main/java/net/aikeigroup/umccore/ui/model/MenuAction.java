package net.aikeigroup.umccore.ui.model;

import java.util.Locale;

/**
 * A single action a menu button performs, parsed from a config string of the
 * form {@code "TYPE:argument"} (argument optional).
 *
 * <p>An action may be deferred with a trailing {@code <delay=TICKS>} marker,
 * e.g. {@code "RUN_COMMAND:tag set fipp<delay=100>"} runs 100 ticks (5s) later —
 * matching the DeluxeMenus convention so those menus port cleanly.</p>
 *
 * <p>Supported types:</p>
 * <ul>
 *   <li>{@code RUN_COMMAND} — run as the player (no elevated perms).</li>
 *   <li>{@code CONSOLE_COMMAND} — run from console (admin-authored only).</li>
 *   <li>{@code OPEN_MENU} — open another menu by id (optionally {@code id:page}).</li>
 *   <li>{@code PAGE} — go to a page of the current menu ({@code next}/{@code prev}/{@code <n>}).</li>
 *   <li>{@code BACK} — return to the previously opened menu.</li>
 *   <li>{@code TELEPORT} — teleport to {@code world,x,y,z[,yaw,pitch]}.</li>
 *   <li>{@code MESSAGE} — send a MiniMessage line to the player.</li>
 *   <li>{@code BROADCAST} — send a MiniMessage line to everyone.</li>
 *   <li>{@code TITLE} — show a title/subtitle: {@code title;subtitle[;fadeIn;stay;fadeOut]}.</li>
 *   <li>{@code OPEN_URL} — open a web link on the player's client.</li>
 *   <li>{@code SOUND} — play a sound key.</li>
 *   <li>{@code CLOSE} — close the current menu.</li>
 * </ul>
 *
 * @param type     the action type
 * @param argument the (placeholder-bearing) argument, delay marker stripped
 * @param delayTicks ticks to wait before running (0 = immediate)
 */
public record MenuAction(Type type, String argument, long delayTicks) {

    public enum Type {
        RUN_COMMAND,
        CONSOLE_COMMAND,
        OPEN_MENU,
        PAGE,
        BACK,
        TELEPORT,
        MESSAGE,
        BROADCAST,
        TITLE,
        OPEN_URL,
        SOUND,
        CLOSE
    }

    /**
     * Parses a config action string. Unknown types throw
     * {@link IllegalArgumentException} so bad menus are caught at load time.
     *
     * @param raw e.g. {@code "OPEN_MENU:stats"}, {@code "CLOSE"} or
     *            {@code "RUN_COMMAND:tag set fipp<delay=100>"}
     * @return the parsed action
     */
    public static MenuAction parse(String raw) {
        String trimmed = raw.trim();

        // Extract an optional trailing <delay=TICKS> marker (anywhere in the arg).
        long delay = 0;
        int dOpen = trimmed.lastIndexOf("<delay=");
        if (dOpen >= 0) {
            int dClose = trimmed.indexOf('>', dOpen);
            if (dClose > dOpen) {
                try {
                    delay = Long.parseLong(trimmed.substring(dOpen + 7, dClose).trim());
                } catch (NumberFormatException ignored) {
                    delay = 0;
                }
                // Remove the marker from the string before splitting type/arg.
                trimmed = (trimmed.substring(0, dOpen) + trimmed.substring(dClose + 1)).trim();
            }
        }

        int colon = trimmed.indexOf(':');
        String typePart = (colon >= 0 ? trimmed.substring(0, colon) : trimmed)
                .trim().toUpperCase(Locale.ROOT);
        String arg = colon >= 0 ? trimmed.substring(colon + 1).trim() : "";
        Type type = Type.valueOf(typePart);
        return new MenuAction(type, arg, Math.max(0, delay));
    }
}
