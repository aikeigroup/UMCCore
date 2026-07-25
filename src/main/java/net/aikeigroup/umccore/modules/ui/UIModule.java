package net.aikeigroup.umccore.modules.ui;

import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.ui.MenuLoader;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The UI feature module. Loads menu definitions, registers the chest-GUI click
 * listener, wires config-driven menu-opening commands (like DeluxeMenus'
 * {@code open_command}), and optionally opens a menu on a player's first join.
 *
 * <p>All commands registered here are unregistered on {@link #disable()} so a
 * full reload leaves nothing dangling.</p>
 */
public final class UIModule extends AbstractModule {

    /** Commands we registered, so we can pull them back out on disable. */
    private final List<String> registeredCommands = new ArrayList<>();

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

        registerMenuCommands();
        registerFirstJoin();
    }

    /**
     * Registers commands from {@code ui.menu-commands} that open a menu directly.
     * Each entry is {@code "cmd[,alias...]": menuId}.
     */
    private void registerMenuCommands() {
        var section = plugin.configs().main().getConfigurationSection("ui.menu-commands");
        if (section == null) {
            return;
        }
        CommandMap map = plugin.getServer().getCommandMap();
        for (String key : section.getKeys(false)) {
            String menuId = section.getString(key);
            if (menuId == null || menuId.isBlank()) {
                continue;
            }
            List<String> names = Arrays.stream(key.split(","))
                    .map(s -> s.trim().toLowerCase(Locale.ROOT))
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (names.isEmpty()) {
                continue;
            }
            String primary = names.get(0);
            List<String> aliases = names.subList(1, names.size());
            Command command = new Command(primary, "Open the " + menuId + " menu",
                    "/" + primary, aliases) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    if (sender instanceof Player player) {
                        plugin.menuService().open(player, menuId);
                    } else {
                        sender.sendMessage("Only players can open menus.");
                    }
                    return true;
                }
            };
            map.register("umccore", command);
            registeredCommands.add(primary);
        }
    }

    /** Opens {@code ui.open-on-first-join} for players who have never joined. */
    private void registerFirstJoin() {
        String menuId = plugin.configs().main().getString("ui.open-on-first-join", "");
        if (menuId == null || menuId.isBlank()) {
            return;
        }
        String target = menuId;
        listen(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                Player player = event.getPlayer();
                if (player.hasPlayedBefore()) {
                    return;
                }
                // Slight delay so the client is fully in-world before the dialog.
                scheduler.runLater(() -> {
                    if (player.isOnline()) {
                        plugin.menuService().open(player, target);
                    }
                }, 40L);
            }
        });
    }

    @Override
    protected void disable() {
        // Pull our menu-opening commands back out of the command map on reload.
        if (registeredCommands.isEmpty()) {
            return;
        }
        CommandMap map = plugin.getServer().getCommandMap();
        var known = map.getKnownCommands();
        for (String name : registeredCommands) {
            Command c = map.getCommand(name);
            if (c != null) {
                c.unregister(map);
            }
            known.remove(name);
            known.remove("umccore:" + name);
        }
        registeredCommands.clear();
    }
}
