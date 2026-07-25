package net.aikeigroup.umccore.ui;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.ui.chest.ChestMenuRenderer;
import net.aikeigroup.umccore.ui.dialog.DialogMenuRenderer;
import net.aikeigroup.umccore.ui.model.MenuDefinition;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Central entry point for opening menus. Owns the loaded {@link MenuDefinition}s
 * and routes each open request to the right renderer.
 *
 * <p>Routing order for {@code AUTO} menus:</p>
 * <ol>
 *   <li>Dialog API — used when available (server supports it and config allows).</li>
 *   <li>Chest-GUI — fallback otherwise.</li>
 * </ol>
 *
 * <p>A menu may force one path via its {@code type:} (DIALOG/GUI). Bedrock
 * players are handled transparently: the Dialog API is translated to a native
 * Bedrock form by Geyser, so the same DIALOG path serves both platforms.</p>
 */
public final class MenuService {

    private final UMCCore plugin;
    private final DialogMenuRenderer dialogRenderer;
    private final ChestMenuRenderer chestRenderer;

    private final Map<String, MenuDefinition> menus = new LinkedHashMap<>();
    private final boolean dialogSupported;

    public MenuService(UMCCore plugin) {
        this.plugin = plugin;
        this.dialogRenderer = new DialogMenuRenderer(plugin);
        this.chestRenderer = new ChestMenuRenderer(plugin);
        this.dialogSupported = detectDialogSupport();
    }

    /** @return the chest renderer (the UI module registers its click listener). */
    public ChestMenuRenderer chestRenderer() {
        return chestRenderer;
    }

    /** Replaces the loaded menu set (called by the loader on enable/reload). */
    public void setMenus(Map<String, MenuDefinition> loaded) {
        menus.clear();
        for (Map.Entry<String, MenuDefinition> e : loaded.entrySet()) {
            menus.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
        }
    }

    public Collection<MenuDefinition> menus() {
        return Collections.unmodifiableCollection(menus.values());
    }

    public boolean has(String id) {
        return menus.containsKey(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Opens a menu for a player by id. Enforces the per-menu permission
     * ({@code umccore.menu.<id>}). No-ops with a message if the menu is unknown
     * or the player lacks permission.
     *
     * @return true if the menu was opened
     */
    public boolean open(Player player, String id) {
        MenuDefinition menu = menus.get(id.toLowerCase(Locale.ROOT));
        if (menu == null) {
            plugin.messages().send(player, "menu.unknown", "name", id);
            return false;
        }
        if (!player.hasPermission(menu.permission())) {
            plugin.messages().send(player, "menu.no-permission", "name", id);
            return false;
        }
        render(player, menu);
        return true;
    }

    private void render(Player player, MenuDefinition menu) {
        MenuDefinition.Renderer choice = menu.renderer();
        boolean useDialog = switch (choice) {
            case DIALOG -> dialogSupported;
            case GUI -> false;
            case AUTO -> dialogSupported
                    && plugin.configs().main().getBoolean("ui.prefer-dialog", true);
        };

        if (useDialog) {
            try {
                dialogRenderer.open(player, menu);
                return;
            } catch (Throwable t) {
                // Any Dialog failure (unexpected client/version) falls back to GUI.
                plugin.getLogger().warning("Dialog render failed for menu '" + menu.id()
                        + "', falling back to chest GUI: " + t.getMessage());
            }
        }
        chestRenderer.open(player, menu);
    }

    /**
     * Detects whether the Dialog API is usable on this server. The class is part
     * of Paper 26.2; if it is missing (older server) we stay on chest-GUI.
     */
    private boolean detectDialogSupport() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            return true;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("Dialog API not present; menus will use chest-GUI.");
            return false;
        }
    }
}
