package net.aikeigroup.umccore.ui.model;

import java.util.List;
import java.util.Locale;

/**
 * A complete menu definition loaded from a {@code menus/<id>.yml} file.
 *
 * <p>A menu is flexible enough to act as a plain button hub, a rich guide /
 * tutorial page (body text + icon "images"), an interactive form (inputs), a
 * single-button notice, or a yes/no confirmation. Long guides can be split into
 * {@link #pages()} that the reader flips through.</p>
 *
 * @param id       menu id (file name without extension)
 * @param title    MiniMessage title
 * @param kind     dialog kind (MENU / NOTICE / CONFIRM)
 * @param renderer preferred render strategy
 * @param rows     chest-GUI row count (1-6), used by the chest renderer
 * @param body     body elements shown above the buttons (text/icon rows)
 * @param inputs   interactive input fields (Dialog API only)
 * @param buttons  the buttons in the menu
 * @param pages    optional extra pages; each page overrides body/buttons for
 *                 multi-step guides. Empty = single page (this definition).
 * @param fillerIcon material name for the chest-GUI filler (nullable = none)
 * @param fillerSlots chest-GUI slots to fill with the filler icon (decorative,
 *                 chest fallback only — dialogs have no slot grid)
 */
public record MenuDefinition(
        String id,
        String title,
        Kind kind,
        Renderer renderer,
        int rows,
        List<MenuBody> body,
        List<MenuInput> inputs,
        List<MenuButton> buttons,
        List<Page> pages,
        String fillerIcon,
        List<Integer> fillerSlots
) {
    /**
     * One page of a multi-step menu. A page can override the title, body and
     * buttons; anything left null/empty inherits from the parent menu.
     *
     * @param title   page title (nullable → inherits menu title)
     * @param body    page body elements
     * @param buttons page buttons (nullable/empty → inherits menu buttons)
     */
    public record Page(String title, List<MenuBody> body, List<MenuButton> buttons) {
    }

    /**
     * The dialog shape.
     * <ul>
     *   <li>{@code MENU} — the standard multi-action menu (many buttons).</li>
     *   <li>{@code NOTICE} — an info page with a single confirm/close button.</li>
     *   <li>{@code CONFIRM} — a yes/no confirmation (first two buttons used).</li>
     * </ul>
     */
    public enum Kind {
        MENU, NOTICE, CONFIRM;

        public static Kind from(String raw) {
            if (raw == null) return MENU;
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return MENU;
            }
        }
    }

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

    /** @return number of pages, counting this definition as page 0. */
    public int pageCount() {
        return 1 + (pages == null ? 0 : pages.size());
    }

    /** @return the title for the given page index (falls back to menu title). */
    public String titleFor(int page) {
        if (page > 0 && pages != null && page - 1 < pages.size()) {
            String t = pages.get(page - 1).title();
            if (t != null && !t.isBlank()) return t;
        }
        return title;
    }

    /** @return the body elements for the given page index. */
    public List<MenuBody> bodyFor(int page) {
        if (page > 0 && pages != null && page - 1 < pages.size()) {
            List<MenuBody> b = pages.get(page - 1).body();
            if (b != null) return b;
        }
        return body == null ? List.of() : body;
    }

    /** @return the buttons for the given page index (falls back to menu buttons). */
    public List<MenuButton> buttonsFor(int page) {
        if (page > 0 && pages != null && page - 1 < pages.size()) {
            List<MenuButton> b = pages.get(page - 1).buttons();
            if (b != null && !b.isEmpty()) return b;
        }
        return buttons == null ? List.of() : buttons;
    }
}
