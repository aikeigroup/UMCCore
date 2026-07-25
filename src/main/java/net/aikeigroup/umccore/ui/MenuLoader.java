package net.aikeigroup.umccore.ui;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.ui.model.MenuAction;
import net.aikeigroup.umccore.ui.model.MenuButton;
import net.aikeigroup.umccore.ui.model.MenuDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads menu definitions from the {@code menus/} folder.
 *
 * <p>On first run, the bundled default menus (main, stats, shortcut, data,
 * warps) are copied out so admins have working examples to edit. Every
 * {@code *.yml} in the folder becomes a menu whose id is the file name.</p>
 */
public final class MenuLoader {

    /** Default menu files shipped in the jar under {@code menus/}. */
    private static final String[] DEFAULT_MENUS = {
            "main", "stats", "shortcut", "data", "warps"
    };

    private final UMCCore plugin;

    public MenuLoader(UMCCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all menus from disk, saving packaged defaults on first run.
     *
     * @return id → definition, in file order
     */
    public Map<String, MenuDefinition> loadAll() {
        File dir = new File(plugin.getDataFolder(), "menus");
        if (!dir.exists()) {
            dir.mkdirs();
            for (String name : DEFAULT_MENUS) {
                plugin.saveResource("menus/" + name + ".yml", false);
            }
        }

        Map<String, MenuDefinition> result = new LinkedHashMap<>();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                result.put(id, parse(id, YamlConfiguration.loadConfiguration(file)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load menu '" + id + "'", e);
            }
        }
        plugin.getLogger().info("Loaded " + result.size() + " menu(s).");
        return result;
    }

    private MenuDefinition parse(String id, YamlConfiguration yml) {
        String title = yml.getString("title", "<white>" + id + "</white>");
        MenuDefinition.Renderer renderer = MenuDefinition.Renderer.from(yml.getString("type", "AUTO"));
        int rows = yml.getInt("rows", 3);

        List<MenuButton> buttons = new ArrayList<>();
        ConfigurationSection buttonsSec = yml.getConfigurationSection("buttons");
        if (buttonsSec != null) {
            for (String key : buttonsSec.getKeys(false)) {
                ConfigurationSection b = buttonsSec.getConfigurationSection(key);
                if (b == null) {
                    continue;
                }
                buttons.add(parseButton(key, b));
            }
        } else if (yml.isList("buttons")) {
            // Support list-of-maps style too.
            for (Map<?, ?> map : yml.getMapList("buttons")) {
                buttons.add(parseButtonMap(map));
            }
        }

        return new MenuDefinition(id, title, renderer, rows, buttons);
    }

    private MenuButton parseButton(String id, ConfigurationSection b) {
        String label = b.getString("label", id);
        List<String> desc = b.getStringList("description");
        String icon = b.getString("icon", "STONE");
        int slot = b.getInt("slot", -1);
        String perm = b.getString("permission", "");
        List<MenuAction> actions = parseActions(b.getStringList("actions"));
        return new MenuButton(id, label, desc, icon, slot, perm, actions);
    }

    @SuppressWarnings("unchecked")
    private MenuButton parseButtonMap(Map<?, ?> map) {
        String id = str(map.get("id"), "button");
        String label = str(map.get("label"), id);
        List<String> desc = map.get("description") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        String icon = str(map.get("icon"), "STONE");
        int slot = map.get("slot") instanceof Number n ? n.intValue() : -1;
        String perm = str(map.get("permission"), "");
        List<String> rawActions = map.get("actions") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        return new MenuButton(id, label, desc, icon, slot, perm, parseActions(rawActions));
    }

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

    private String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }
}
