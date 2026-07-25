package net.aikeigroup.umccore.ui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.aikeigroup.umccore.UMCCore;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Locale;
import java.util.UUID;

/**
 * Builds icon {@link ItemStack}s for menus from a material name, an optional
 * textured player head, and optional custom-model-data.
 *
 * <p>Heads are the closest thing Minecraft has to an inline image: a head can
 * show any player's skin (an avatar) or an arbitrary texture (a logo). The
 * {@code head} field accepts either a plain player name (resolved to that
 * player's skin) or a base64 texture value (a fixed image, e.g. a UNNESMC logo
 * head copied from a head database).</p>
 */
public final class IconFactory {

    /** A fixed namespace so the same base64 texture always yields the same UUID. */
    private static final UUID TEXTURE_UUID = new UUID(0x00000000_0000_0000L, 0x0000_000000000001L);

    private final UMCCore plugin;

    public IconFactory(UMCCore plugin) {
        this.plugin = plugin;
    }

    /**
     * @param materialName fallback material name (nullable → STONE, or a head
     *                     when {@code head} is set)
     * @param head         player name or base64 texture (nullable)
     * @param customModelData resource-pack model data (-1 = none)
     * @return a built icon; never null
     */
    public ItemStack build(String materialName, String head, int customModelData) {
        ItemStack item;
        if (head != null && !head.isBlank()) {
            item = new ItemStack(Material.PLAYER_HEAD);
            applyHead(item, head.trim());
        } else {
            Material material = Material.matchMaterial(
                    materialName == null ? "STONE" : materialName.toUpperCase(Locale.ROOT));
            item = new ItemStack(material == null ? Material.STONE : material);
        }
        if (customModelData >= 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(customModelData);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private void applyHead(ItemStack head, String value) {
        if (!(head.getItemMeta() instanceof SkullMeta meta)) {
            return;
        }
        // Heuristic: a long token with no spaces that isn't a 3-16 char name is
        // treated as a base64 texture value; otherwise it's a player name.
        boolean looksBase64 = value.length() > 20 && !value.contains(" ");
        if (looksBase64) {
            PlayerProfile profile = plugin.getServer().createProfile(TEXTURE_UUID, "umccore");
            profile.setProperty(new ProfileProperty("textures", value));
            meta.setPlayerProfile(profile);
        } else {
            // Player name → offline profile; Paper resolves the skin lazily.
            PlayerProfile profile = plugin.getServer().createProfile(value);
            meta.setPlayerProfile(profile);
        }
        head.setItemMeta(meta);
    }
}
