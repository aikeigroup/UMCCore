package net.aikeigroup.umccore.modules.actionbar;

import java.util.Locale;

/**
 * Animation played on every change from one segment to the next, so text never
 * hard-cuts between messages.
 */
public enum Transition {
    /** Erase old text char-by-char, then type the new text in. */
    TYPEWRITER,
    /** Old text slides out to the left as new text slides in. */
    SLIDE,
    /** Cross-fade by brightness. */
    FADE,
    /** Left-to-right wipe swapping old chars for new. */
    WAVE;

    public static Transition from(String raw) {
        if (raw == null) return TYPEWRITER;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TYPEWRITER;
        }
    }
}
