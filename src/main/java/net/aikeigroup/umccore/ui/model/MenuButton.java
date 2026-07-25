package net.aikeigroup.umccore.ui.model;

import java.util.List;

/**
 * One button/entry in a menu.
 *
 * @param id          stable identifier within the menu
 * @param label       MiniMessage label (button text / item name)
 * @param description MiniMessage tooltip / lore lines (may be empty)
 * @param icon        material name for chest-GUI rendering (nullable)
 * @param slot        chest-GUI slot; -1 means auto-place
 * @param permission  permission required to see/use the button (nullable = all)
 * @param actions     actions run (in order) when the button is clicked
 */
public record MenuButton(
        String id,
        String label,
        List<String> description,
        String icon,
        int slot,
        String permission,
        List<MenuAction> actions
) {
    /** @return true if the given permission holder may see this button. */
    public boolean visibleTo(org.bukkit.permissions.Permissible who) {
        return permission == null || permission.isBlank() || who.hasPermission(permission);
    }
}
