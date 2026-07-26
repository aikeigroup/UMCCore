package net.aikeigroup.umccore.ui.model;

import java.util.List;

/**
 * One button/entry in a menu.
 *
 * <p>Icon fields serve the different renderers: {@code icon}/{@code headTexture}/
 * {@code customModelData} draw the item in the Java chest-GUI, while {@code image}
 * is the picture shown next to a <em>Bedrock</em> form button (a PNG URL, or
 * {@code head:<name>} / {@code url:<link>} — see {@link #bedrockImage()}).</p>
 *
 * @param id          stable identifier within the menu
 * @param label       MiniMessage label (button text / item name)
 * @param description MiniMessage tooltip / lore lines (may be empty)
 * @param icon        material name for chest-GUI rendering (nullable)
 * @param headTexture player name or base64 skin for a textured head icon
 *                    (nullable) — an avatar/logo "image" for the chest GUI
 * @param customModelData resource-pack custom model data for the icon (-1 none)
 * @param image       Bedrock form button image: a full {@code https://…png} URL,
 *                    {@code url:<link>}, or {@code head:<player>} (rendered via a
 *                    head-avatar service). Nullable = no picture on the button.
 * @param slot        chest-GUI slot; -1 means auto-place
 * @param width       Dialog button pixel width (<=0 = client default)
 * @param permission  permission required to see/use the button (nullable = all)
 * @param actions     actions run (in order) when the button is clicked
 */
public record MenuButton(
        String id,
        String label,
        List<String> description,
        String icon,
        String headTexture,
        int customModelData,
        String image,
        int slot,
        int width,
        String permission,
        List<MenuAction> actions
) {
    /** @return true if the given permission holder may see this button. */
    public boolean visibleTo(org.bukkit.permissions.Permissible who) {
        return permission == null || permission.isBlank() || who.hasPermission(permission);
    }

    /**
     * Resolves the Bedrock button picture, if any, into a {@code (type, data)}
     * pair the Cumulus renderer can use.
     *
     * <p>Accepted forms for {@code image}:</p>
     * <ul>
     *   <li>{@code https://…} or {@code url:https://…} → a URL image.</li>
     *   <li>{@code head:<name>} → the player's face via a head-avatar service
     *       (works for any name, Java or Bedrock).</li>
     *   <li>a bare player name is also treated as {@code head:<name>} when no
     *       {@code image} is set but {@code headTexture} is a plain name.</li>
     * </ul>
     *
     * @return {@code [urlType?"url":"path", data]} or {@code null} for none
     */
    public String[] bedrockImage() {
        String v = image;
        if ((v == null || v.isBlank()) && headTexture != null && !headTexture.isBlank()
                && headTexture.length() <= 20 && !headTexture.contains(" ")) {
            // A plain player-name head doubles as a Bedrock avatar.
            v = "head:" + headTexture;
        }
        if (v == null || v.isBlank()) {
            return null;
        }
        v = v.trim();
        if (v.startsWith("head:")) {
            String name = v.substring(5).trim();
            return new String[]{"url", "https://mc-heads.net/avatar/" + name + "/64"};
        }
        if (v.startsWith("url:")) {
            return new String[]{"url", v.substring(4).trim()};
        }
        if (v.startsWith("path:")) {
            return new String[]{"path", v.substring(5).trim()};
        }
        // Bare value: URL if it looks like one, else a resource-pack texture path.
        if (v.startsWith("http://") || v.startsWith("https://")) {
            return new String[]{"url", v};
        }
        return new String[]{"path", v};
    }

    /**
     * Compact factory for the synthetic buttons the renderers create internally
     * (paging arrows, confirm defaults) — keeps their call sites readable.
     */
    public static MenuButton simple(String id, String label, String icon, List<MenuAction> actions) {
        return new MenuButton(id, label, List.of(), icon, null, -1, null, -1, -1, "", actions);
    }
}
