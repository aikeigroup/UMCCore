package net.aikeigroup.umccore.modules.actionbar;

import net.aikeigroup.umccore.util.Text;
import net.kyori.adventure.text.Component;

/**
 * Produces animated action-bar frames.
 *
 * <p>Two families of animation, both fully frame-driven (a new string every
 * frame — never a static hold):</p>
 * <ul>
 *   <li><b>Segment animations</b> — run continuously while a segment is shown
 *       (rainbow, gradient shift, scroll, wave, pulse).</li>
 *   <li><b>Transitions</b> — play on <em>every</em> change from one segment to
 *       the next (typewriter, slide, fade, wave) so text never hard-cuts.</li>
 * </ul>
 *
 * <p>Colour-based effects colour each character via HSV computed in Java, giving
 * smooth motion independent of MiniMessage's built-in tags. Character-based
 * effects (scroll/wave/transitions) operate on the plain text of the segment.</p>
 */
public final class AnimationEngine {

    private AnimationEngine() {
    }

    // --- Public API --------------------------------------------------------

    /**
     * Renders a single segment at a given animation frame.
     *
     * @param miniMessageText the segment's (already PAPI-resolved) MiniMessage
     * @param animation       the animation type
     * @param frame           monotonically increasing frame counter
     * @return a component ready to send as an action bar
     */
    public static Component renderSegment(String miniMessageText, SegmentAnimation animation, long frame) {
        String plain = Text.plain(miniMessageText);
        if (plain.isEmpty()) {
            return Component.empty();
        }
        // "Eased" frame: advances during a MOVE window then holds still during a
        // PAUSE window, so the motion has calm pauses (jeda) instead of running
        // non-stop. Colour animations use this; NONE keeps the user's own text
        // (hex/gradients included) untouched.
        double eased = easedFrame(frame, MOVE_FRAMES, PAUSE_FRAMES);
        return switch (animation) {
            case NONE -> Text.mm(miniMessageText);
            // Speeds are hue-degrees per eased-frame; kept low for a gentle drift.
            case RAINBOW -> Text.mm(perCharHue(plain, eased, 0, 360, 1.4));
            case GRADIENT_SHIFT -> Text.mm(perCharHue(plain, eased, 190, 70, 1.2));
            case PULSE -> Text.mm(pulse(plain, eased));
            case SCROLL -> Text.mm(scroll(plain, frame, 40));
            case WAVE -> Text.mm(wave(plain, eased));
        };
    }

    // Motion cadence: move for MOVE_FRAMES, then hold for PAUSE_FRAMES. At 20 fps
    // that is ~2s of gentle motion followed by ~1.5s of stillness.
    private static final int MOVE_FRAMES = 40;
    private static final int PAUSE_FRAMES = 30;

    /**
     * Maps the raw frame counter to a monotonically increasing value that
     * advances during the move window and stays flat during the pause window —
     * giving animations a natural "move, then rest" rhythm.
     */
    private static double easedFrame(long frame, int movePeriod, int pausePeriod) {
        long cycle = movePeriod + pausePeriod;
        long cycles = frame / cycle;
        long pos = frame % cycle;
        return cycles * (double) movePeriod + Math.min(pos, movePeriod);
    }

    /**
     * Renders a transition frame between two segments.
     *
     * @param fromText   outgoing segment MiniMessage (PAPI-resolved)
     * @param toText     incoming segment MiniMessage (PAPI-resolved)
     * @param transition the transition type
     * @param progress   0.0 (fully "from") .. 1.0 (fully "to")
     * @param frame      global frame counter (for any colour motion)
     * @return a component for this transition frame
     */
    public static Component renderTransition(String fromText, String toText,
                                             Transition transition, double progress, long frame) {
        String from = Text.plain(fromText);
        String to = Text.plain(toText);
        // Each transition returns COMPLETE MiniMessage. typewriter/slide return
        // plain text and colour it themselves with a calm static gradient;
        // fade/wave already embed their own colour tags. We must NOT run the
        // result through perCharHue again — doing so escaped those tags and
        // produced broken colour output.
        String out = switch (transition) {
            case TYPEWRITER -> tint(typewriter(from, to, progress));
            case SLIDE -> tint(slide(from, to, progress));
            case FADE -> fade(from, to, progress);
            case WAVE -> waveWipe(from, to, progress);
        };
        return Text.mm(out);
    }

    /** Wraps already-escaped plain text in a calm, static two-colour gradient. */
    private static String tint(String escaped) {
        if (escaped.isEmpty()) {
            return escaped;
        }
        return "<gradient:#7fd8ff:#4aa8ff>" + escaped + "</gradient>";
    }

    // --- Segment animations ------------------------------------------------

    private static String pulse(String plain, double frame) {
        // Brightness oscillates between ~40% and 100% of white. Slower divisor
        // = gentler pulse.
        double t = (Math.sin(frame / 10.0) + 1) / 2; // 0..1
        int v = (int) (110 + t * 145);               // 110..255
        String hex = hex(v, v, v);
        return "<" + hex + ">" + escape(plain);
    }

    private static String scroll(String plain, long frame, int window) {
        String padded = plain + "   •   ";
        int len = padded.length();
        int width = Math.min(window, len);
        // Advance one character every 3 frames so scrolling reads slowly and
        // calmly instead of racing past.
        int offset = (int) ((frame / 3) % len);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width; i++) {
            sb.append(padded.charAt((offset + i) % len));
        }
        return perCharHue(sb.toString(), frame, 190, 60, 1.2);
    }

    private static String wave(String plain, double frame) {
        // A bright highlight travels across the text (bold) while the rest is dim.
        int len = plain.length();
        int pos = (int) (((long) (frame / 2)) % Math.max(1, len));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            char c = plain.charAt(i);
            int dist = Math.abs(i - pos);
            if (dist == 0) {
                sb.append("<white><bold>").append(escapeChar(c)).append("</bold>");
            } else if (dist <= 2) {
                sb.append("<gray>").append(escapeChar(c));
            } else {
                sb.append("<dark_gray>").append(escapeChar(c));
            }
        }
        return sb.toString();
    }

    // --- Transitions -------------------------------------------------------

    private static String typewriter(String from, String to, double p) {
        if (p < 0.5) {
            // Erase the outgoing text right-to-left.
            int keep = (int) Math.round(from.length() * (1 - p * 2));
            return escape(from.substring(0, Math.max(0, keep)));
        } else {
            // Type the incoming text left-to-right.
            int show = (int) Math.round(to.length() * ((p - 0.5) * 2));
            return escape(to.substring(0, Math.min(to.length(), show)));
        }
    }

    private static String slide(String from, String to, double p) {
        int width = Math.max(from.length(), to.length());
        // Build a strip: [from][gap][to] and slide the viewport left.
        String gap = " ".repeat(width);
        String strip = from + gap + to;
        int start = (int) Math.round((from.length() + width) * p);
        int end = Math.min(strip.length(), start + width);
        return escape(strip.substring(Math.min(start, strip.length()), end));
    }

    private static String fade(String from, String to, double p) {
        // Cross-fade by brightness: outgoing dims out, incoming brightens in.
        boolean first = p < 0.5;
        String text = first ? from : to;
        double local = first ? (1 - p * 2) : ((p - 0.5) * 2); // 1..0..1
        int v = (int) (40 + local * 215);
        return "<" + hex(v, v, v) + ">" + escape(text);
    }

    private static String waveWipe(String from, String to, double p) {
        // Left-to-right wipe: each position shows the new char once the wipe
        // front passes it, the old char otherwise.
        int width = Math.max(from.length(), to.length());
        int front = (int) Math.round(width * p);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width; i++) {
            char c = i < front ? charAt(to, i) : charAt(from, i);
            if (i == front - 1) {
                sb.append("<white><bold>").append(escapeChar(c)).append("</bold>");
            } else {
                sb.append(escapeChar(c));
            }
        }
        return sb.toString();
    }

    // --- Colour helpers ----------------------------------------------------

    /** Colours each character with a hue that moves with the frame counter. */
    private static String perCharHue(String plain, double frame, double baseHue,
                                     double span, double speed) {
        StringBuilder sb = new StringBuilder();
        int len = Math.max(1, plain.length());
        for (int i = 0; i < plain.length(); i++) {
            double t = (double) i / len;
            double hue = baseHue + t * span + frame * speed;
            int[] rgb = hsvToRgb(((hue % 360) + 360) % 360, 0.85, 1.0);
            sb.append('<').append(hex(rgb[0], rgb[1], rgb[2])).append('>')
                    .append(escapeChar(plain.charAt(i)));
        }
        return sb.toString();
    }

    private static int[] hsvToRgb(double h, double s, double v) {
        double c = v * s;
        double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
        double m = v - c;
        double r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return new int[]{
                (int) Math.round((r + m) * 255),
                (int) Math.round((g + m) * 255),
                (int) Math.round((b + m) * 255)
        };
    }

    private static String hex(int r, int g, int b) {
        return String.format("#%02x%02x%02x", clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static char charAt(String s, int i) {
        return i < s.length() ? s.charAt(i) : ' ';
    }

    /** Escapes a single char so it is safe inside MiniMessage. */
    private static String escapeChar(char c) {
        if (c == '<') return "\\<";
        if (c == '>') return "\\>";
        return String.valueOf(c);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append(escapeChar(s.charAt(i)));
        }
        return sb.toString();
    }
}
