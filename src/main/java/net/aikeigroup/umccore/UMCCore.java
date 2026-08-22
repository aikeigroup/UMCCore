package net.aikeigroup.umccore;

import net.aikeigroup.umccore.api.UMCCoreAPI;
import net.aikeigroup.umccore.command.UMCCommand;
import net.aikeigroup.umccore.core.ConfigManager;
import net.aikeigroup.umccore.core.MessageManager;
import net.aikeigroup.umccore.core.ModuleManager;
import net.aikeigroup.umccore.core.ReloadService;
import net.aikeigroup.umccore.integrations.IntegrationManager;
import net.aikeigroup.umccore.modules.clearlag.ClearLagModule;
import net.aikeigroup.umccore.modules.itemstacker.ItemStackerModule;
import net.aikeigroup.umccore.modules.lifecycle.LifecycleModule;
import net.aikeigroup.umccore.modules.limiter.MobLimiterModule;
import net.aikeigroup.umccore.modules.mobstacker.MobStackerModule;
import net.aikeigroup.umccore.modules.actionbar.ActionBarModule;
import net.aikeigroup.umccore.modules.discord.DiscordModule;
import net.aikeigroup.umccore.modules.mobxp.MobXpModule;
import net.aikeigroup.umccore.modules.performance.PerformanceModule;
import net.aikeigroup.umccore.modules.pickup.MobPickupModule;
import net.aikeigroup.umccore.modules.staffchat.StaffChatCommand;
import net.aikeigroup.umccore.modules.staffchat.StaffChatModule;
import net.aikeigroup.umccore.modules.ui.UIModule;
import net.aikeigroup.umccore.modules.update.UpdateModule;
import net.aikeigroup.umccore.modules.votelog.VoteLogModule;
import net.aikeigroup.umccore.ui.ActionExecutor;
import net.aikeigroup.umccore.ui.MenuService;
import net.aikeigroup.umccore.util.TextService;
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
    private TextService textService;
    private MenuService menuService;
    private ActionExecutor actionExecutor;
    private net.aikeigroup.umccore.ui.IconFactory iconFactory;

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

        // 3b. Shared UI/text services (long-lived; modules populate them).
        this.textService = new TextService(this);
        this.iconFactory = new net.aikeigroup.umccore.ui.IconFactory(this);
        this.menuService = new MenuService(this);
        this.actionExecutor = new ActionExecutor(this);

        // 3c. Publish the public API for other plugins.
        UMCCoreAPI.init(this);

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
        if (integrationManager != null) {
            integrationManager.shutdown();
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
        modules().register(new MobPickupModule());
        // M3 — UI system.
        modules().register(new UIModule());
        // M4 — animated action bar.
        modules().register(new ActionBarModule());
        // M5 — Discord status embed.
        modules().register(new DiscordModule());
        // Vote log — Discord embed on each Votifier vote (soft: Votifier+DiscordSRV).
        modules().register(new VoteLogModule());
        // StaffChat — bidirectional Discord sync, toggle/direct modes, /sc shortcut.
        modules().register(new StaffChatModule());
        // Lifecycle recorder — detect stop/restart/crash + write a report before
        // the server really dies (heartbeat + JVM shutdown hook, reload-safe).
        modules().register(new LifecycleModule());
        // M6 — self updater.
        modules().register(new UpdateModule());
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

        PluginCommand scCommand = getCommand("staffchat");
        if (scCommand != null) {
            StaffChatCommand scExecutor = new StaffChatCommand(this);
            scCommand.setExecutor(scExecutor);
            scCommand.setTabCompleter(scExecutor);
        } else {
            getLogger().severe("Command 'staffchat' is not defined in plugin.yml!");
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

    public TextService text() {
        return textService;
    }

    public MenuService menuService() {
        return menuService;
    }

    public ActionExecutor actionExecutor() {
        return actionExecutor;
    }

    public net.aikeigroup.umccore.ui.IconFactory icons() {
        return iconFactory;
    }

    /**
     * @return the file name of this plugin's jar on disk (e.g.
     *         {@code UMCCore-1.0.0.jar}). Used by the updater so the file it
     *         places in the update folder matches and gets applied on restart.
     */
    public String getUpdateJarName() {
        return getFile().getName();
    }
}
