package net.aikeigroup.umccore.ui.chest;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.ui.model.MenuButton;
import net.aikeigroup.umccore.ui.model.MenuDefinition;
import net.aikeigroup.umccore.util.Text;
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
import java.util.Locale;
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
        private final Map<Integer, MenuButton> slotButtons = new HashMap<>();
        private Inventory inventory;

        MenuHolder(MenuDefinition menu) {
            this.menu = menu;
        }

        public MenuDefinition menu() {
            return menu;
        }

        public MenuButton buttonAt(int slot) {
            return slotButtons.get(slot);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Builds and opens the chest GUI for a player. */
    public void open(Player player, MenuDefinition menu) {
        int rows = Math.min(6, Math.max(1, menu.rows()));
        MenuHolder holder = new MenuHolder(menu);
        Component title = plugin.text().render(player, menu.title());
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory = inv;

        int auto = 0;
        for (MenuButton button : menu.buttons()) {
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
        Material material = Material.matchMaterial(
                button.icon() == null ? "STONE" : button.icon().toUpperCase(Locale.ROOT));
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.text().render(player, button.label())
                    .decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                            net.kyori.adventure.text.format.TextDecoration.State.FALSE));
            if (button.description() != null && !button.description().isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : button.description()) {
                    lore.add(plugin.text().render(player, line)
                            .decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                                    net.kyori.adventure.text.format.TextDecoration.State.FALSE));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
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
                plugin.actionExecutor().run(player, button);
            }
        }
    }

    public ClickListener newListener() {
        return new ClickListener();
    }
}
