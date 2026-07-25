package net.aikeigroup.umccore.core;

import net.aikeigroup.umccore.UMCCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads and reloads every YAML config file used by UMCCore.
 *
 * <p>Each logical config is referenced by a short name (e.g. {@code "config"},
 * {@code "stacker"}). On first load the packaged default is copied out to the
 * data folder if missing, so a fresh install ships with fully-commented,
 * ready-to-run configs. Reloading re-reads every file from disk.</p>
 *
 * <p>Menu definitions in the {@code menus/} folder are handled separately by the
 * UI module; this manager owns the flat top-level {@code *.yml} files.</p>
 */
public final class ConfigManager {

    /** Config file names (without the .yml extension) that ship with the jar. */
    private static final String[] FILES = {
            "config",
            "messages",
            "performance",
            "stacker",
            "clearlag",
            "limiter",
            "mobxp",
            "actionbar",
            "discord",
            "pickup"
    };

    private final UMCCore plugin;
    private final Map<String, FileConfiguration> loaded = new LinkedHashMap<>();

    public ConfigManager(UMCCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or reloads) all known config files from disk, saving packaged
     * defaults first if a file is missing.
     */
    public void loadAll() {
        loaded.clear();
        for (String name : FILES) {
            load(name);
        }
    }

    private void load(String name) {
        File file = new File(plugin.getDataFolder(), name + ".yml");
        if (!file.exists()) {
            // Copy the packaged default (with comments) to disk.
            plugin.saveResource(name + ".yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Merge in any keys added in newer plugin versions from the jar default,
        // so upgrades don't silently miss new options.
        try (InputStream in = plugin.getResource(name + ".yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not read packaged default for '" + name + ".yml'", e);
        }

        loaded.put(name, config);
    }

    /**
     * Returns a loaded config by name, or an empty config if unknown.
     *
     * @param name file name without extension
     * @return the configuration (never {@code null})
     */
    public FileConfiguration get(String name) {
        FileConfiguration config = loaded.get(name);
        return config != null ? config : new YamlConfiguration();
    }

    /** @return the main {@code config.yml}. */
    public FileConfiguration main() {
        return get("config");
    }

    /** @return the raw {@code messages.yml}. */
    public FileConfiguration messages() {
        return get("messages");
    }
}
