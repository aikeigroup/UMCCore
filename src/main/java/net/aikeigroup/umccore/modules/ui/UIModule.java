package net.aikeigroup.umccore.modules.ui;

import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.ui.MenuLoader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The UI feature module. Loads menu definitions and registers the chest-GUI
 * click listener. The {@link net.aikeigroup.umccore.ui.MenuService} itself is a
 * long-lived service on the plugin; this module (re)populates it with menus on
 * enable and wires the listener that the chest renderer needs.
 */
public final class UIModule extends AbstractModule {

    public UIModule() {
        super("ui");
    }

    @Override
    protected void enable() {
        // (Re)load menu definitions into the shared MenuService.
        MenuLoader loader = new MenuLoader(plugin);
        plugin.menuService().setMenus(loader.loadAll());

        // Register the chest-GUI click listener (unregistered automatically on
        // disable via AbstractModule tracking).
        listen(plugin.menuService().chestRenderer().newListener());

        // Clear a player's menu navigation history when they leave.
        listen(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                plugin.menuService().clearHistory(event.getPlayer().getUniqueId());
            }
        });
    }
}
