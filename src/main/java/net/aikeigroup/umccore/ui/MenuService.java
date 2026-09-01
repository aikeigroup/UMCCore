package net.aikeigroup.umccore.ui;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.ui.bedrock.BedrockFormRenderer;
import net.aikeigroup.umccore.ui.chest.ChestMenuRenderer;
import net.aikeigroup.umccore.ui.dialog.DialogMenuRenderer;
import net.aikeigroup.umccore.ui.model.MenuDefinition;
import net.aikeigroup.umccore.ui.model.Platform;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Central entry point for opening menus. Owns the loaded {@link MenuDefinition}s
 * (kept separate per {@link Platform}) and routes each open request to the right
 * renderer for the player's platform.
 *
 * <p>Routing:</p>
 * <ul>
 *   <li><b>Bedrock</b> players (detected via Floodgate) get the native
 *       {@link BedrockFormRenderer} — a real Cumulus form, not a translated Java
 *       dialog. Their menu definition is looked up in the {@code bedrock} set
 *       first, falling back to the {@code java} set if that id only exists once.</li>
 *   <li><b>Java</b> players get the native Dialog API when available/allowed,
 *       else the chest-GUI. Their definition comes from the {@code java} set.</li>
 * </ul>
 *
 * <p>Keeping the two definition sets apart is what lets a guide be laid out
 * differently for each client without one platform's quirks bleeding into the
 * other.</p>
 */
public final class MenuService {

    private final UMCCore plugin;
    private final DialogMenuRenderer dialogRenderer;
    private final ChestMenuRenderer chestRenderer;
    private BedrockFormRenderer bedrockRenderer;

    /** Loaded menus, split by the platform they were authored for. */
    private final Map<Platform, Map<String, MenuDefinition>> menus = new EnumMap<>(Platform.class);
    private final boolean dialogSupported;

    /** Per-player navigation history ("menuId:page") for the BACK action. */
    private final Map<UUID, Deque<String>> history = new WeakHashMap<>();

    public MenuService(UMCCore plugin) {
        this.plugin = plugin;
        this.dialogRenderer = new DialogMenuRenderer(plugin);
        this.chestRenderer = new ChestMenuRenderer(plugin);
        // The Bedrock renderer is created lazily: its implementation class
        // references Floodgate/cumulus types, which are only present when Geyser
        // + Floodgate are installed. Instantiating it eagerly would crash the
        // whole plugin with NoClassDefFoundError on servers without them.
        this.bedrockRenderer = null;
        this.dialogSupported = detectDialogSupport();
        menus.put(Platform.JAVA, new LinkedHashMap<>());
        menus.put(Platform.BEDROCK, new LinkedHashMap<>());
    }

    /** @return the chest renderer (the UI module registers its click listener). */
    public ChestMenuRenderer chestRenderer() {
        return chestRenderer;
    }

    /**
     * Lazily creates the Bedrock renderer, but only when Floodgate is actually
     * present. Creating it unconditionally would load {@link BedrockFormRenderer}
     * (which references Floodgate/cumulus types) and crash the plugin on servers
     * without them.
     */
    private BedrockFormRenderer bedrockRenderer() {
        if (bedrockRenderer != null) {
            return bedrockRenderer;
        }
        if (!plugin.integrations().hasFloodgate()) {
            return null;
        }
        try {
            bedrockRenderer = new BedrockFormRenderer(plugin);
        } catch (Throwable t) {
            // Floodgate classes not loadable at runtime after all; stay on Java.
            plugin.getLogger().warning("Bedrock renderer unavailable (Floodgate missing?): "
                    + t.getMessage());
            bedrockRenderer = null;
        }
        return bedrockRenderer;
    }

    /** Replaces the loaded menu set (called by the loader on enable/reload). */
    public void setMenus(Map<Platform, Map<String, MenuDefinition>> loaded) {
        for (Platform p : Platform.values()) {
            Map<String, MenuDefinition> target = menus.get(p);
            target.clear();
            Map<String, MenuDefinition> src = loaded.get(p);
            if (src != null) {
                for (Map.Entry<String, MenuDefinition> e : src.entrySet()) {
                    target.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
                }
            }
        }
    }

    /** @return the platform UMCCore should render for this player. */
    public Platform platformOf(Player player) {
        return plugin.integrations().isBedrock(player.getUniqueId())
                ? Platform.BEDROCK : Platform.JAVA;
    }

    /**
     * Resolves a menu id for a platform, falling back to the other platform's
     * definition when this platform doesn't define that id — so admins only
     * duplicate a menu when they actually want it to differ.
     */
    private MenuDefinition resolve(String id, Platform platform) {
        String key = id.toLowerCase(Locale.ROOT);
        MenuDefinition def = menus.get(platform).get(key);
        if (def != null) {
            return def;
        }
        Platform other = platform == Platform.JAVA ? Platform.BEDROCK : Platform.JAVA;
        return menus.get(other).get(key);
    }

    /** @return every distinct menu id across both platforms (for tab-completion). */
    public Collection<String> ids() {
        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(menus.get(Platform.JAVA).keySet());
        ids.addAll(menus.get(Platform.BEDROCK).keySet());
        return Collections.unmodifiableSet(ids);
    }

    /** @return the Java-side menu definitions (used by the API/other callers). */
    public Collection<MenuDefinition> menus() {
        Set<MenuDefinition> all = new LinkedHashSet<>();
        all.addAll(menus.get(Platform.JAVA).values());
        all.addAll(menus.get(Platform.BEDROCK).values());
        return Collections.unmodifiableSet(all);
    }

    /** @return true if either platform defines this menu id. */
    public boolean has(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        return menus.get(Platform.JAVA).containsKey(key)
                || menus.get(Platform.BEDROCK).containsKey(key);
    }

    /**
     * Opens a menu for a player by id (page 0). Records navigation history so a
     * later BACK action can return here. Enforces the per-menu permission.
     *
     * @return true if the menu was opened
     */
    public boolean open(Player player, String id) {
        return open(player, id, 0, true);
    }

    /**
     * Opens a specific page of a menu without pushing history (used by paging
     * buttons — flipping pages shouldn't stack BACK entries).
     */
    public boolean openPage(Player player, String id, int page) {
        return open(player, id, page, false);
    }

    /**
     * Core open. {@code pushHistory} controls whether the previously shown
     * menu is remembered for BACK (true for cross-menu jumps, false for paging).
     */
    public boolean open(Player player, String id, int page, boolean pushHistory) {
        Platform platform = platformOf(player);
        MenuDefinition menu = resolve(id, platform);
        if (menu == null) {
            plugin.messages().send(player, "menu.unknown", "name", id);
            return false;
        }
        if (!player.hasPermission(menu.permission())) {
            plugin.messages().send(player, "menu.no-permission", "name", id);
            return false;
        }
        int clamped = Math.max(0, Math.min(page, menu.pageCount() - 1));
        if (pushHistory) {
            history.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>())
                    .push(menu.id() + ":" + clamped);
        }
        render(player, menu, clamped, platform);
        return true;
    }

    /**
     * Returns to the previously opened menu, if any. Pops the current entry then
     * opens the one beneath it. Falls back to closing when history is empty.
     */
    public void back(Player player) {
        Deque<String> stack = history.get(player.getUniqueId());
        if (stack == null || stack.size() < 2) {
            player.closeInventory();
            player.closeDialog();
            return;
        }
        stack.pop(); // discard current
        String prev = stack.peek();
        int colon = prev.lastIndexOf(':');
        String id = prev.substring(0, colon);
        int page = Integer.parseInt(prev.substring(colon + 1));
        open(player, id, page, false);
    }

    /** Clears a player's navigation history (called on quit). */
    public void clearHistory(UUID uuid) {
        history.remove(uuid);
    }

    private void render(Player player, MenuDefinition menu, int page, Platform platform) {
        // Bedrock players always get the native form path.
        if (platform == Platform.BEDROCK) {
            BedrockFormRenderer renderer = bedrockRenderer();
            if (renderer != null) {
                try {
                    renderer.open(player, menu, page);
                    return;
                } catch (Throwable t) {
                    // A Bedrock form failure (Floodgate missing at runtime, etc.)
                    // drops through to the Java path as a safety net.
                    plugin.getLogger().warning("Bedrock form failed for menu '" + menu.id()
                            + "', falling back to Java rendering: " + t.getMessage());
                }
            }
        }

        MenuDefinition.Renderer choice = menu.renderer();
        boolean useDialog = switch (choice) {
            case DIALOG -> dialogSupported;
            case GUI -> false;
            case AUTO -> dialogSupported
                    && plugin.configs().main().getBoolean("ui.prefer-dialog", true);
        };

        if (useDialog) {
            try {
                dialogRenderer.open(player, menu, page);
                return;
            } catch (Throwable t) {
                // Any Dialog failure (unexpected client/version) falls back to GUI.
                plugin.getLogger().warning("Dialog render failed for menu '" + menu.id()
                        + "', falling back to chest GUI: " + t.getMessage());
            }
        }
        chestRenderer.open(player, menu, page);
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
