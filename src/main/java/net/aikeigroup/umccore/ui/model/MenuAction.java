package net.aikeigroup.umccore.ui.model;

import java.util.Locale;

/**
 * A single action a menu button performs, parsed from a config string of the
 * form {@code "TYPE:argument"} (argument optional).
 *
 * <p>Supported types:</p>
 * <ul>
 *   <li>{@code RUN_COMMAND} — run as the player (no elevated perms).</li>
 *   <li>{@code CONSOLE_COMMAND} — run from console (admin-authored only).</li>
 *   <li>{@code OPEN_MENU} — open another menu by id.</li>
 *   <li>{@code TELEPORT} — teleport to {@code world,x,y,z[,yaw,pitch]}.</li>
 *   <li>{@code MESSAGE} — send a MiniMessage line to the player.</li>
 *   <li>{@code SOUND} — play a sound key.</li>
 *   <li>{@code CLOSE} — close the current menu.</li>
 * </ul>
 */
public record MenuAction(Type type, String argument) {

    public enum Type {
        RUN_COMMAND,
        CONSOLE_COMMAND,
        OPEN_MENU,
        TELEPORT,
        MESSAGE,
        SOUND,
        CLOSE
    }

    /**
     * Parses a config action string. Unknown types throw
     * {@link IllegalArgumentException} so bad menus are caught at load time.
     *
     * @param raw e.g. {@code "OPEN_MENU:stats"} or {@code "CLOSE"}
     * @return the parsed action
     */
    public static MenuAction parse(String raw) {
        String trimmed = raw.trim();
        int colon = trimmed.indexOf(':');
        String typePart = (colon >= 0 ? trimmed.substring(0, colon) : trimmed)
                .trim().toUpperCase(Locale.ROOT);
        String arg = colon >= 0 ? trimmed.substring(colon + 1).trim() : "";
        Type type = Type.valueOf(typePart);
        return new MenuAction(type, arg);
    }
}
