package net.aikeigroup.umccore.ui.model;

import java.util.Locale;

/**
 * The client platform a menu is being rendered for.
 *
 * <p>UMCCore keeps menu definitions <em>separate per platform</em> so a guide
 * can be authored differently for Java and Bedrock — the two clients have very
 * different UI primitives (Java: Dialog screens / chest grids; Bedrock: native
 * touch forms). Menus live under {@code menus/java/} and {@code menus/bedrock/};
 * a player is routed to the definition matching their platform, falling back to
 * the other platform's definition when only one exists.</p>
 */
public enum Platform {
    /** Java Edition clients (Dialog API primary, chest-GUI fallback). */
    JAVA,
    /** Bedrock Edition clients via Geyser/Floodgate (native Cumulus forms). */
    BEDROCK;

    /** The sub-folder under {@code menus/} that holds this platform's files. */
    public String folder() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Platform from(String raw) {
        if (raw == null) return JAVA;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return JAVA;
        }
    }
}
