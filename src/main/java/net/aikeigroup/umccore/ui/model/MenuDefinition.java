package net.aikeigroup.umccore.ui.model;

import java.util.List;
import java.util.Locale;

/**
 * A complete menu definition loaded from a {@code menus/<id>.yml} file.
 *
 * @param id       menu id (file name without extension)
 * @param title    MiniMessage title
 * @param renderer preferred render strategy
 * @param rows     chest-GUI row count (1-6), used by the chest renderer
 * @param buttons  the buttons in the menu
 */
public record MenuDefinition(
        String id,
        String title,
        Renderer renderer,
        int rows,
        List<MenuButton> buttons
) {
    /**
     * Which UI backend to prefer for this menu.
     * <ul>
     *   <li>{@code AUTO} — router decides by platform/version (default).</li>
     *   <li>{@code DIALOG} — force the native Dialog API.</li>
     *   <li>{@code GUI} — force the chest-GUI.</li>
     * </ul>
     */
    public enum Renderer {
        AUTO, DIALOG, GUI;

        public static Renderer from(String raw) {
            if (raw == null) return AUTO;
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return AUTO;
            }
        }
    }

    /** @return the permission node to open this menu: {@code umccore.menu.<id>}. */
    public String permission() {
        return "umccore.menu." + id;
    }
}
