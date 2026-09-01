package net.aikeigroup.umccore.ui;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.ui.model.MenuAction;
import net.aikeigroup.umccore.ui.model.MenuBody;
import net.aikeigroup.umccore.ui.model.MenuButton;
import net.aikeigroup.umccore.ui.model.MenuDefinition;
import net.aikeigroup.umccore.ui.model.MenuInput;
import net.aikeigroup.umccore.ui.model.Platform;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads menu definitions from the platform-split {@code menus/} folder.
 *
 * <p>Menus live under two sub-folders so Java and Bedrock can have completely
 * different layouts for the same menu id:</p>
 * <pre>
 *   menus/
 *     java/     ← shown to Java Edition players (Dialog / chest-GUI)
 *     bedrock/  ← shown to Bedrock players via Geyser (native Cumulus forms)
 * </pre>
 *
 * <p>A menu id only needs to exist in one folder: if a player's platform has no
 * definition for that id, the loader falls back to the other platform's copy, so
 * admins never have to duplicate a menu they're happy to share. On first run the
 * bundled defaults are copied out for both platforms; any legacy flat
 * {@code menus/*.yml} from an older version are migrated into {@code java/}.</p>
 *
 * <p>The parser is intentionally forgiving: every field has a default, both the
 * map-style and list-style {@code buttons:} syntaxes are accepted, and unknown
 * pieces are skipped with a warning rather than failing the whole menu — so a
 * typo in one guide never blanks the others.</p>
 */
public final class MenuLoader {

    /** Default menu ids shipped in the jar (under {@code menus/java|bedrock/}). */
    private static final String[] DEFAULT_MENUS = {
            "main", "stats", "shortcut", "data", "warps", "guide", "tagfakultas"
    };

    private final UMCCore plugin;

    public MenuLoader(UMCCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads every menu for both platforms from disk, saving packaged defaults on
     * first run and migrating any legacy flat menus into {@code java/}.
     *
     * @return per-platform maps of id → definition, in file order
     */
    public Map<Platform, Map<String, MenuDefinition>> loadAll() {
        File root = new File(plugin.getDataFolder(), "menus");
        boolean firstRun = !root.exists();
        root.mkdirs();

        File javaDir = new File(root, "java");
        File bedrockDir = new File(root, "bedrock");

        // First run (or an upgrade from the old flat layout): create the split
        // folders and copy the bundled defaults into each.
        if (firstRun || (!javaDir.exists() && !bedrockDir.exists())) {
            javaDir.mkdirs();
            bedrockDir.mkdirs();
            migrateLegacyFlatMenus(root, javaDir);
            for (String name : DEFAULT_MENUS) {
                saveDefault("menus/java/" + name + ".yml");
                saveDefault("menus/bedrock/" + name + ".yml");
            }
        } else {
            javaDir.mkdirs();
            bedrockDir.mkdirs();
        }

        Map<Platform, Map<String, MenuDefinition>> out = new EnumMap<>(Platform.class);
        out.put(Platform.JAVA, loadFolder(javaDir, Platform.JAVA));
        out.put(Platform.BEDROCK, loadFolder(bedrockDir, Platform.BEDROCK));
        plugin.getLogger().info("Loaded " + out.get(Platform.JAVA).size() + " Java menu(s) and "
                + out.get(Platform.BEDROCK).size() + " Bedrock menu(s).");
        return out;
    }

    /** Copies a bundled resource only if it isn't already on disk. */
    private void saveDefault(String resource) {
        File target = new File(plugin.getDataFolder(), resource);
        if (target.exists()) {
            return;
        }
        try {
            plugin.saveResource(resource, false);
        } catch (IllegalArgumentException e) {
            // Resource missing from the jar (e.g. a menu without a bedrock variant
            // bundled) — that's fine, cross-platform fallback covers it.
        }
    }

    /**
     * Moves any pre-split {@code menus/*.yml} files (from an older UMCCore) into
     * {@code menus/java/} so upgrading servers keep their customised menus.
     */
    private void migrateLegacyFlatMenus(File root, File javaDir) {
        File[] legacy = root.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (legacy == null) {
            return;
        }
        for (File file : legacy) {
            File dest = new File(javaDir, file.getName());
            if (!dest.exists() && file.renameTo(dest)) {
                plugin.getLogger().info("Migrated legacy menu '" + file.getName() + "' into menus/java/.");
            }
        }
    }

    /** Loads every {@code *.yml} in a platform folder into id → definition. */
    private Map<String, MenuDefinition> loadFolder(File dir, Platform platform) {
        Map<String, MenuDefinition> result = new LinkedHashMap<>();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                result.put(id, parse(id, platform, YamlConfiguration.loadConfiguration(file)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to load " + platform + " menu '" + id + "'", e);
            }
        }
        return result;
    }

    private MenuDefinition parse(String id, Platform platform, YamlConfiguration yml) {
        String title = yml.getString("title", "<white>" + id + "</white>");
        MenuDefinition.Kind kind = MenuDefinition.Kind.from(yml.getString("kind", "MENU"));
        MenuDefinition.Renderer renderer = MenuDefinition.Renderer.from(yml.getString("type", "AUTO"));
        int rows = yml.getInt("rows", 3);
        String image = yml.getString("image", yml.getString("header-image"));

        List<MenuBody> body = parseBody(yml.get("body"));
        List<MenuInput> inputs = parseInputs(yml.getConfigurationSection("inputs"), yml.get("inputs"));
        List<MenuButton> buttons = parseButtons(yml);
        List<MenuDefinition.Page> pages = parsePages(yml.getList("pages"));

        // Optional decorative filler for the chest-GUI fallback.
        String fillerIcon = null;
        List<Integer> fillerSlots = List.of();
        ConfigurationSection fillerSec = yml.getConfigurationSection("filler");
        if (fillerSec != null) {
            fillerIcon = fillerSec.getString("icon", fillerSec.getString("material"));
            fillerSlots = parseSlotList(fillerSec.get("slots"));
        }

        return new MenuDefinition(id, platform, title, kind, renderer, rows, body, inputs, buttons,
                pages, fillerIcon, fillerSlots, image);
    }

    /**
     * Parses a slot list that may contain single numbers and {@code a-b} ranges,
     * e.g. {@code ["0-8", 13, "18-26"]}. Used for chest-GUI filler slots.
     */
    private List<Integer> parseSlotList(Object raw) {
        List<Integer> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                String s = String.valueOf(o).trim();
                int dash = s.indexOf('-');
                if (dash > 0) {
                    try {
                        int a = Integer.parseInt(s.substring(0, dash).trim());
                        int b = Integer.parseInt(s.substring(dash + 1).trim());
                        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) out.add(i);
                    } catch (NumberFormatException ignored) {
                        // skip malformed range
                    }
                } else {
                    try {
                        out.add(Integer.parseInt(s));
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                }
            }
        }
        return out;
    }

    // --- Buttons -----------------------------------------------------------

    private List<MenuButton> parseButtons(ConfigurationSection sec) {
        List<MenuButton> buttons = new ArrayList<>();
        ConfigurationSection buttonsSec = sec.getConfigurationSection("buttons");
        if (buttonsSec != null) {
            for (String key : buttonsSec.getKeys(false)) {
                ConfigurationSection b = buttonsSec.getConfigurationSection(key);
                if (b != null) {
                    buttons.add(parseButton(key, b));
                }
            }
        } else if (sec.isList("buttons")) {
            for (Map<?, ?> map : sec.getMapList("buttons")) {
                buttons.add(parseButtonMap(map));
            }
        }
        return buttons;
    }

    private MenuButton parseButton(String id, ConfigurationSection b) {
        return new MenuButton(
                id,
                b.getString("label", id),
                b.getStringList("description"),
                b.getString("icon", "STONE"),
                b.getString("head", b.getString("head-texture")),
                b.getInt("custom-model-data", -1),
                b.getString("image", null),
                b.getInt("slot", -1),
                b.getInt("width", -1),
                b.getString("permission", ""),
                parseActions(b.getStringList("actions")));
    }

    private MenuButton parseButtonMap(Map<?, ?> map) {
        String id = str(map.get("id"), "button");
        List<String> desc = map.get("description") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        List<String> rawActions = map.get("actions") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        return new MenuButton(
                id,
                str(map.get("label"), id),
                desc,
                str(map.get("icon"), "STONE"),
                str(map.get("head"), str(map.get("head-texture"), null)),
                intOf(map.get("custom-model-data"), -1),
                str(map.get("image"), null),
                intOf(map.get("slot"), -1),
                intOf(map.get("width"), -1),
                str(map.get("permission"), ""),
                parseActions(rawActions));
    }

    // --- Body --------------------------------------------------------------

    /**
     * Parses the {@code body:} node. Accepts three shapes for flexibility:
     * a single string, a list of strings (each a text paragraph), or a list of
     * maps ({@code {text: ...}} or {@code {icon: ..., text: ...}}).
     */
    private List<MenuBody> parseBody(Object raw) {
        List<MenuBody> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        if (raw instanceof String s) {
            out.add(MenuBody.text(s, -1));
            return out;
        }
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    out.add(MenuBody.text(s, -1));
                } else if (o instanceof Map<?, ?> m) {
                    MenuBody b = parseBodyMap(m);
                    if (b != null && !b.isEmpty()) {
                        out.add(b);
                    }
                }
            }
        }
        return out;
    }

    private MenuBody parseBodyMap(Map<?, ?> m) {
        String icon = str(m.get("icon"), null);
        String head = str(m.get("head"), str(m.get("head-texture"), null));
        String text = str(m.get("text"), "");
        int width = intOf(m.get("width"), -1);
        int cmd = intOf(m.get("custom-model-data"), -1);
        MenuBody.Kind kind = (icon != null || head != null) ? MenuBody.Kind.ITEM : MenuBody.Kind.TEXT;
        return new MenuBody(kind, text, width, icon, head, cmd);
    }

    // --- Inputs ------------------------------------------------------------

    private List<MenuInput> parseInputs(ConfigurationSection sec, Object listForm) {
        List<MenuInput> out = new ArrayList<>();
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection in = sec.getConfigurationSection(key);
                if (in != null) {
                    out.add(parseInput(key, in));
                }
            }
        } else if (listForm instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add(parseInputMap(m));
                }
            }
        }
        return out;
    }

    private MenuInput parseInput(String key, ConfigurationSection in) {
        MenuInput.Kind kind = MenuInput.Kind.from(in.getString("type", "TEXT"));
        List<MenuInput.Option> opts = new ArrayList<>();
        for (Map<?, ?> om : in.getMapList("options")) {
            opts.add(new MenuInput.Option(str(om.get("id"), str(om.get("label"), "")),
                    str(om.get("label"), str(om.get("id"), ""))));
        }
        return new MenuInput(
                key, kind,
                in.getString("label", key),
                in.getString("initial", ""),
                in.getInt("width", -1),
                in.getInt("max-length", -1),
                in.getBoolean("multiline", false),
                (float) in.getDouble("min", 0),
                (float) in.getDouble("max", 100),
                (float) in.getDouble("step", 0),
                opts);
    }

    private MenuInput parseInputMap(Map<?, ?> m) {
        String key = str(m.get("key"), "input");
        MenuInput.Kind kind = MenuInput.Kind.from(str(m.get("type"), "TEXT"));
        List<MenuInput.Option> opts = new ArrayList<>();
        if (m.get("options") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> om) {
                    opts.add(new MenuInput.Option(str(om.get("id"), str(om.get("label"), "")),
                            str(om.get("label"), str(om.get("id"), ""))));
                }
            }
        }
        return new MenuInput(
                key, kind,
                str(m.get("label"), key),
                str(m.get("initial"), ""),
                intOf(m.get("width"), -1),
                intOf(m.get("max-length"), -1),
                Boolean.parseBoolean(str(m.get("multiline"), "false")),
                floatOf(m.get("min"), 0),
                floatOf(m.get("max"), 100),
                floatOf(m.get("step"), 0),
                opts);
    }

    // --- Pages -------------------------------------------------------------

    private List<MenuDefinition.Page> parsePages(List<?> raw) {
        List<MenuDefinition.Page> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            String title = str(m.get("title"), null);
            List<MenuBody> body = parseBody(m.get("body"));
            List<MenuButton> buttons = new ArrayList<>();
            if (m.get("buttons") instanceof List<?> l) {
                for (Object bo : l) {
                    if (bo instanceof Map<?, ?> bm) {
                        buttons.add(parseButtonMap(bm));
                    }
                }
            }
            out.add(new MenuDefinition.Page(title, body, buttons));
        }
        return out;
    }

    // --- Actions -----------------------------------------------------------

    private List<MenuAction> parseActions(List<String> raw) {
        List<MenuAction> actions = new ArrayList<>();
        for (String s : raw) {
            try {
                actions.add(MenuAction.parse(s));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid menu action '" + s + "': " + e.getMessage());
            }
        }
        return actions;
    }

    // --- Helpers -----------------------------------------------------------

    private String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private int intOf(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return def;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private float floatOf(Object o, float def) {
        if (o instanceof Number n) return n.floatValue();
        if (o == null) return def;
        try {
            return Float.parseFloat(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
