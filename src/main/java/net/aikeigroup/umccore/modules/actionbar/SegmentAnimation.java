package net.aikeigroup.umccore.modules.actionbar;

import java.util.Locale;

/**
 * Continuous animation applied to a segment while it is displayed.
 * Every type produces a new frame each tick — none are static.
 */
public enum SegmentAnimation {
    /** No colour motion, but still re-sent each frame and still transitions. */
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

    public static SegmentAnimation from(String raw) {
        if (raw == null) return GRADIENT_SHIFT;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GRADIENT_SHIFT;
        }
    }
}
