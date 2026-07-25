package net.aikeigroup.umccore.modules.discord;

import net.aikeigroup.umccore.modules.performance.ServerStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parsed configuration for a single Discord status embed.
 *
 * <p>Built from a {@code discord.yml} {@code embeds:} list entry. Holds the
 * static template (title, description, fields, colours) and knows how to resolve
 * a dynamic colour based on live TPS.</p>
 */
public final class EmbedSpec {

    /** One embed field. */
    public record Field(String name, String value, boolean inline) {
    }

    private final String id;
    private final String channel;
    private final boolean enabled;
    private final boolean dynamicColor;
    private final double goodThreshold;
    private final double warnThreshold;
    private final int goodColor;
    private final int warnColor;
    private final int badColor;
    private final int staticColor;
    private final String title;
    private final String description;
    private final String thumbnailUrl;
    private final List<Field> fields;
    private final String footer;
    private final boolean showTimestamp;
    private final int intervalSeconds;

    private EmbedSpec(String id, String channel, boolean enabled, boolean dynamicColor,
                      double goodThreshold, double warnThreshold, int goodColor, int warnColor,
                      int badColor, int staticColor, String title, String description,
                      String thumbnailUrl, List<Field> fields, String footer, boolean showTimestamp,
                      int intervalSeconds) {
        this.id = id;
        this.channel = channel;
        this.enabled = enabled;
        this.dynamicColor = dynamicColor;
        this.goodThreshold = goodThreshold;
        this.warnThreshold = warnThreshold;
        this.goodColor = goodColor;
        this.warnColor = warnColor;
        this.badColor = badColor;
        this.staticColor = staticColor;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.fields = fields;
        this.footer = footer;
        this.showTimestamp = showTimestamp;
        this.intervalSeconds = intervalSeconds;
    }

    /**
     * Parses one embed entry from its config map. Returns {@code null} if the
     * entry is malformed (missing id/channel).
     */
    @SuppressWarnings("unchecked")
    public static EmbedSpec fromMap(Map<?, ?> map, int defaultInterval) {
        String id = str(map.get("id"), null);
        String channel = str(map.get("channel"), null);
        if (id == null || channel == null) {
            return null;
        }
        boolean enabled = bool(map.get("enabled"), true);
        boolean dynamic = bool(map.get("dynamic-color"), true);

        double good = 18.0, warn = 15.0;
        if (map.get("color-thresholds") instanceof Map<?, ?> ct) {
            good = dbl(ct.get("good"), 18.0);
            warn = dbl(ct.get("warn"), 15.0);
        }

        int goodColor = hex(str(map.get("good-color"), "#43b581"));
        int warnColor = hex(str(map.get("warn-color"), "#faa61a"));
        int badColor = hex(str(map.get("bad-color"), "#f04747"));
        int staticColor = hex(str(map.get("color"), "#5865F2"));

        String title = str(map.get("title"), "");
        String description = str(map.get("description"), "");
        String thumbnail = str(map.get("thumbnail-url"), "");
        String footer = str(map.get("footer"), "");
        boolean timestamp = bool(map.get("show-timestamp"), true);

        List<Field> fields = new ArrayList<>();
        if (map.get("fields") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> fm) {
                    fields.add(new Field(
                            str(fm.get("name"), ""),
                            str(fm.get("value"), ""),
                            bool(fm.get("inline"), false)));
                }
            }
        }

        return new EmbedSpec(id, channel, enabled, dynamic, good, warn,
                goodColor, warnColor, badColor, staticColor, title, description,
                thumbnail, fields, footer, timestamp, defaultInterval);
    }

    /** @return the RGB int colour for the current stats (dynamic or static). */
    public int resolveColor(ServerStats stats) {
        if (!dynamicColor) {
            return staticColor;
        }
        double tps = stats.tps();
        if (tps >= goodThreshold) return goodColor;
        if (tps >= warnThreshold) return warnColor;
        return badColor;
    }

    // --- Accessors ---------------------------------------------------------

    public String id() { return id; }
    public String channel() { return channel; }
    public boolean enabled() { return enabled; }
    public String title() { return title; }
    public String description() { return description; }
    public String thumbnailUrl() { return thumbnailUrl; }
    public List<Field> fields() { return fields; }
    public String footer() { return footer; }
    public boolean showTimestamp() { return showTimestamp; }
    public int intervalSeconds() { return intervalSeconds; }

    // --- Parsing helpers ---------------------------------------------------

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o == null) return def;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    private static double dbl(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return def;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Parses a {@code #rrggbb} hex colour into an RGB int (0xRRGGBB). */
    private static int hex(String value) {
        if (value == null) {
            return 0x5865F2;
        }
        String h = value.startsWith("#") ? value.substring(1) : value;
        try {
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0x5865F2;
        }
    }
}
