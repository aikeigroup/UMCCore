package net.aikeigroup.umccore.modules.motd;

/**
 * Length helpers for MiniMessage text.
 *
 * <p>The Minecraft client caps the server-list MOTD at 59 visible characters and
 * silently truncates anything longer, so we measure the <em>visible</em> length
 * (with all {@code <tag>}s removed) to warn admins about oversized frames.</p>
 */
final class TextLength {

    private TextLength() {
    }

    /**
     * @return the number of visible characters in a MiniMessage string, i.e. the
     *         text with every {@code <...>} tag removed. A trailing {@code \\} is
     *         treated as an escape for a literal {@code <}.
     */
    static int visible(String input) {
        int len = 0;
        boolean inTag = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inTag) {
                if (c == '>') {
                    inTag = false;
                }
                continue;
            }
            if (c == '\\' && i + 1 < input.length() && input.charAt(i + 1) == '<') {
                // Escaped literal '<'; count the '<' only.
                len++;
                i++;
                continue;
            }
            if (c == '<') {
                inTag = true;
                continue;
            }
            len++;
        }
        return len;
    }
}
