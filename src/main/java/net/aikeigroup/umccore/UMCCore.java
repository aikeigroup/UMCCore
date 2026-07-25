package net.aikeigroup.umccore;

import net.aikeigroup.umccore.command.UMCCommand;
import net.aikeigroup.umccore.core.ConfigManager;
import net.aikeigroup.umccore.core.MessageManager;
import net.aikeigroup.umccore.core.ModuleManager;
import net.aikeigroup.umccore.core.ReloadService;
import net.aikeigroup.umccore.integrations.IntegrationManager;
import net.aikeigroup.umccore.modules.clearlag.ClearLagModule;
import net.aikeigroup.umccore.modules.itemstacker.ItemStackerModule;
import net.aikeigroup.umccore.modules.limiter.MobLimiterModule;
import net.aikeigroup.umccore.modules.mobstacker.MobStackerModule;
import net.aikeigroup.umccore.modules.mobxp.MobXpModule;
import net.aikeigroup.umccore.modules.performance.PerformanceModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * UMCCore — all-in-one performance, optimization, and cross-platform UI core.
 *
 * <p>The main class stays thin: it owns the shared services (config, messages,
 * integrations, modules, reload) and wires the root command. All real features
 * live in {@code modules.*} as {@link net.aikeigroup.umccore.core.Module}s so
 * they can be reloaded as a group.</p>
 */
public final class UMCCore extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private IntegrationManager integrationManager;
    private ModuleManager moduleManager;
    private ReloadService reloadService;

    @Override
    public void onEnable() {
        // 1. Config first — everything else reads from it.
        this.configManager = new ConfigManager(this);
        this.configManager.loadAll();

        // 2. Messages (needs config loaded for the prefix).
        this.messageManager = new MessageManager(this);
        this.messageManager.reload();

        // 3. Detect optional integrations.
        this.integrationManager = new IntegrationManager(this);
        this.integrationManager.refresh();

        // 4. Module registry + reload orchestrator.
        this.moduleManager = new ModuleManager(this);
        this.reloadService = new ReloadService(this);
        registerModules();

        // 5. Commands.
        registerCommands();

        // 6. Bring modules up.
        int enabled = moduleManager.enableAll();
        getLogger().info("UMCCore enabled (" + enabled + " module(s) active).");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        getLogger().info("UMCCore disabled.");
    }

    /**
     * Registers all feature modules. M1 ships the core skeleton with no feature
     * modules yet; later milestones add stacker, clearlag, discord, ui, etc.
     */
    private void registerModules() {
        // M2 — Performance & optimization. Order matters for enable/disable:
        // performance first (others may reference it), stackers before mobxp so
        // death handling composes predictably.
        modules().register(new PerformanceModule());
        modules().register(new MobStackerModule());
        modules().register(new ItemStackerModule());
        modules().register(new MobLimiterModule());
        modules().register(new MobXpModule());
        modules().register(new ClearLagModule());
        // UI, action bar, and Discord modules are added in M3–M5.
    }

    private void registerCommands() {
        UMCCommand root = new UMCCommand(this);
        PluginCommand command = getCommand("umccore");
        if (command != null) {
            command.setExecutor(root);
            command.setTabCompleter(root);
        } else {
            getLogger().severe("Command 'umccore' is not defined in plugin.yml!");
        }
    }

    // --- Service accessors -------------------------------------------------

    public ConfigManager configs() {
        return configManager;
    }

    public MessageManager messages() {
        return messageManager;
    }

    public IntegrationManager integrations() {
        return integrationManager;
    }

    public ModuleManager modules() {
        return moduleManager;
    }

    public ReloadService reloadService() {
        return reloadService;
    }
}
