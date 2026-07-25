package net.aikeigroup.umccore.core;

import net.aikeigroup.umccore.UMCCore;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Registry and lifecycle owner for every {@link Module}.
 *
 * <p>Modules are registered once at startup (insertion order preserved) and
 * enabled/disabled as a group. A module whose toggle in {@code config.yml} is
 * {@code false} is registered but never enabled. Enable failures are isolated:
 * one broken module does not stop the others.</p>
 */
public final class ModuleManager {

    private final UMCCore plugin;

    /** Insertion-ordered so enable/disable happen in a predictable sequence. */
    private final Map<String, Module> modules = new LinkedHashMap<>();

    /** Tracks which modules are currently enabled (id -> enabled). */
    private final Map<String, Boolean> active = new LinkedHashMap<>();

    public ModuleManager(UMCCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a module. Does not enable it. Later registrations of the same
     * id replace earlier ones (should not happen in practice).
     *
     * @param module the module to register
     * @return the same module, for chaining
     */
    public Module register(Module module) {
        modules.put(module.id().toLowerCase(), module);
        active.putIfAbsent(module.id().toLowerCase(), false);
        return module;
    }

    /**
     * Enables every registered module whose config toggle is on. Modules
     * already enabled are skipped. Failures are logged and isolated.
     *
     * @return the number of modules successfully enabled this call
     */
    public int enableAll() {
        int enabled = 0;
        for (Module module : modules.values()) {
            if (isActive(module.id())) {
                continue;
            }
            if (!isToggledOn(module)) {
                plugin.getLogger().info("Module '" + module.id() + "' is disabled in config; skipping.");
                continue;
            }
            if (enable(module)) {
                enabled++;
            }
        }
        return enabled;
    }

    /**
     * Disables every currently-active module in reverse registration order.
     * Always safe; exceptions during disable are swallowed and logged.
     */
    public void disableAll() {
        // Reverse order: tear down dependents before their dependencies.
        var ids = modules.keySet().toArray(new String[0]);
        for (int i = ids.length - 1; i >= 0; i--) {
            Module module = modules.get(ids[i]);
            if (module != null && isActive(module.id())) {
                disable(module);
            }
        }
    }

    private boolean enable(Module module) {
        try {
            module.onEnable(plugin);
            active.put(module.id().toLowerCase(), true);
            plugin.getLogger().info("Enabled module: " + module.id());
            return true;
        } catch (Throwable t) {
            active.put(module.id().toLowerCase(), false);
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to enable module '" + module.id() + "'. It will be skipped.", t);
            // Best-effort cleanup of a half-initialized module.
            try {
                module.onDisable(plugin);
            } catch (Throwable ignored) {
                // Nothing more we can do; already logged the primary failure.
            }
            return false;
        }
    }

    private void disable(Module module) {
        try {
            module.onDisable(plugin);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "Error while disabling module '" + module.id() + "'.", t);
        } finally {
            active.put(module.id().toLowerCase(), false);
            plugin.getLogger().info("Disabled module: " + module.id());
        }
    }

    /**
     * Enables a single module by id if not already active and toggled on.
     *
     * @param id module id
     * @return true if it became active as a result of this call
     */
    public boolean enableById(String id) {
        Module module = modules.get(id.toLowerCase());
        if (module == null || isActive(id)) {
            return false;
        }
        return enable(module);
    }

    /**
     * Disables a single module by id if currently active.
     *
     * @param id module id
     * @return true if it was active and is now disabled
     */
    public boolean disableById(String id) {
        Module module = modules.get(id.toLowerCase());
        if (module == null || !isActive(id)) {
            return false;
        }
        disable(module);
        return true;
    }

    private boolean isToggledOn(Module module) {
        String path = module.enabledPath();
        if (path == null) {
            return true;
        }
        return plugin.configs().main().getBoolean(path, true);
    }

    public boolean isActive(String id) {
        return active.getOrDefault(id.toLowerCase(), false);
    }

    public Optional<Module> get(String id) {
        return Optional.ofNullable(modules.get(id.toLowerCase()));
    }

    public Collection<Module> all() {
        return modules.values();
    }

    /**
     * @return module ids in registration order (for tab completion / listing)
     */
    public Collection<String> ids() {
        return modules.keySet();
    }
}
