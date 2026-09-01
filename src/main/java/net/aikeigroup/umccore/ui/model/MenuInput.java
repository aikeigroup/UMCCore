package net.aikeigroup.umccore.ui.model;

import java.util.List;
import java.util.Locale;

/**
 * An interactive input field on a menu (Dialog API only). The value a player
 * enters is exposed to actions as the token {@code {input_<key>}}.
 *
 * @param key     unique key within the menu; also the placeholder token name
 * @param kind    the input widget type
 * @param label   MiniMessage label shown next to the field
 * @param initial initial text value (TEXT), or the initially-selected option id
 * @param width   preferred pixel width (<=0 = client default)
 * @param maxLength TEXT: max characters (<=0 = client default)
 * @param multiline TEXT: allow multiple lines
 * @param min     NUMBER: minimum value
 * @param max     NUMBER: maximum value
 * @param step    NUMBER: step size (<=0 = continuous)
 * @param options SINGLE_OPTION: selectable entries (id + label)
 */
public record MenuInput(
        String key,
        Kind kind,
        String label,
        String initial,
        int width,
        int maxLength,
        boolean multiline,
        float min,
        float max,
        float step,
        List<Option> options
) {
    public enum Kind {
        TEXT, BOOLEAN, NUMBER, SINGLE_OPTION;

        public static Kind from(String raw) {
            if (raw == null) return TEXT;
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return TEXT;
            }
        }
    }

    /** One selectable option for a SINGLE_OPTION input. */
    public record Option(String id, String label) {
    }
}
