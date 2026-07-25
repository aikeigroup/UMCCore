package net.aikeigroup.umccore.modules.actionbar;

import java.util.Locale;

/**
 * Continuous animation applied to a segment while it is displayed.
 */
public enum SegmentAnimation {
    /**
     * No colour motion — the segment's own MiniMessage is shown as-is, so any
     * hex/RGB colours or gradients you write (e.g. {@code <#ff8800>} or
     * {@code <gradient:#ff0000:#00ff00>}) are preserved exactly. Still fully
     * animated between segments via the configured transition.
     */
    NONE,
    /** Full-spectrum hue cycle across characters. */
    RAINBOW,
    /** A gradient that scrolls across the text. */
    GRADIENT_SHIFT,
    /** Brightness pulse. */
    PULSE,
    /** Text scrolls horizontally within a fixed window. */
    SCROLL,
    /** A bright highlight travels across the characters. */
    WAVE;

    /**
     * Parses an animation name. {@code STATIC} is accepted as an alias for
     * {@link #NONE}. Unknown values fall back to {@code NONE} so the segment's
     * own colours (including hex) are kept rather than being overridden.
     */
    public static SegmentAnimation from(String raw) {
        if (raw == null) return NONE;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (v.equals("STATIC")) return NONE;
        try {
            return valueOf(v);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
