package net.aikeigroup.umccore.ui.chest;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.ui.model.MenuBody;
import net.aikeigroup.umccore.ui.model.MenuButton;
import net.aikeigroup.umccore.ui.model.MenuDefinition;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders menus as a chest inventory GUI — the fallback path for clients that
 * cannot use the native Dialog API.
 *
 * <p>Each open inventory uses a custom {@link InventoryHolder} that carries the
 * menu definition and slot→button map, so clicks are routed without any global
 * per-player state. The single click listener is registered by the UI module
 * and cancels all clicks (menus are read-only).</p>
 */
public final class ChestMenuRenderer {

    private final UMCCore plugin;

    public ChestMenuRenderer(UMCCore plugin) {
        this.plugin = plugin;
    }

    /** Custom holder that tags an inventory as a UMCCore menu. */
    public static final class MenuHolder implements InventoryHolder {
        private final MenuDefinition menu;
        private final int page;
        private final Map<Integer, MenuButton> slotButtons = new HashMap<>();
        private Inventory inventory;

        MenuHolder(MenuDefinition menu, int page) {
            this.menu = menu;
            this.page = page;
        }

        public MenuDefinition menu() {
            return menu;
        }

        public int page() {
            return page;
        }

        public MenuButton buttonAt(int slot) {
            return slotButtons.get(slot);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Builds and opens the chest GUI for a page of a menu. */
    public void open(Player player, MenuDefinition menu, int page) {
        int rows = Math.min(6, Math.max(1, menu.rows()));
        MenuHolder holder = new MenuHolder(menu, page);
        Component title = plugin.text().render(player, menu.titleFor(page));
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory = inv;

        // Decorative filler first (so real items overwrite it where they overlap).
        if (menu.fillerIcon() != null && !menu.fillerSlots().isEmpty()) {
            ItemStack filler = buildFiller(player, menu.fillerIcon());
            for (int slot : menu.fillerSlots()) {
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, filler);
                }
            }
        }

        // Body elements become read-only info items across the top row so guides
        // are still readable in the chest fallback (no click actions).
        int bodySlot = 0;
        for (MenuBody el : menu.bodyFor(page)) {
            if (el.isEmpty() || bodySlot >= 9) {
                continue;
            }
            inv.setItem(bodySlot++, buildBodyItem(player, el));
        }

        int auto = bodySlot > 0 ? 9 : 0; // start buttons on row 2 if a body exists
        for (MenuButton button : menu.buttonsFor(page)) {
            if (!button.visibleTo(player)) {
                continue;
            }
            int slot = button.slot() >= 0 ? button.slot() : auto++;
            if (slot < 0 || slot >= inv.getSize()) {
                continue;
            }
            inv.setItem(slot, buildItem(player, button));
            holder.slotButtons.put(slot, button);
        }

        player.openInventory(inv);
    }

    private ItemStack buildItem(Player player, MenuButton button) {
        ItemStack item = plugin.icons().build(button.icon(), button.headTexture(), button.customModelData());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(noItalic(plugin.text().render(player, button.label())));
            if (button.description() != null && !button.description().isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : button.description()) {
                    lore.add(noItalic(plugin.text().render(player, line)));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Body element → a non-clickable info item (icon + wrapped caption lore). */
    private ItemStack buildBodyItem(Player player, MenuBody el) {
        ItemStack item = plugin.icons().build(el.iconOr("PAPER"), el.headTexture(), el.customModelData());
        ItemMeta meta = item.getItemMeta();
        if (meta != null && el.text() != null && !el.text().isBlank()) {
            // First line as name; keep it readable in the fallback GUI.
            meta.displayName(noItalic(plugin.text().render(player, el.text())));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** A blank-named decorative filler item (e.g. a stained-glass pane). */
    private ItemStack buildFiller(Player player, String icon) {
        ItemStack item = plugin.icons().build(icon, null, -1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    private Component noItalic(Component c) {
        return c.decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    /** Click listener; registered once by the UI module. */
    public final class ClickListener implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
                return;
            }
            event.setCancelled(true); // menus are read-only
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null
                    || !(event.getClickedInventory().getHolder() instanceof MenuHolder)) {
                return; // clicked their own inventory
            }
            MenuButton button = holder.buttonAt(event.getSlot());
            if (button != null) {
                plugin.actionExecutor().run(player, button, holder.menu(), holder.page(), Map.of());
            }
        }
    }

    public ClickListener newListener() {
        return new ClickListener();
    }
}
