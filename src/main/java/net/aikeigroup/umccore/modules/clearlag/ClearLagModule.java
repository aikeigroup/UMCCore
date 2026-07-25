package net.aikeigroup.umccore.modules.clearlag;

import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;

import java.util.List;

/**
 * Scheduled entity cleanup ("clearlag") with animated countdown warnings.
 *
 * <p>Every {@code interval-seconds} it removes configured entity categories,
 * warning players at the configured second-marks via chat and action bar.
 * A rich protection list keeps named/tamed/leashed/vehicle entities safe.</p>
 */
public final class ClearLagModule extends AbstractModule {

    /**
     * Entity types that should never be cleared by default — the important,
     * hard-to-replace mobs (traders/NPCs, golems, bosses, mounts, pets, etc.).
     * Admins extend this via {@code protect.blacklist-types} or turn off the
     * built-in list with {@code protect.use-default-blacklist}.
     */
    private static final java.util.Set<String> DEFAULT_BLACKLIST = java.util.Set.of(
            // Trading / utility NPCs
            "villager", "zombie_villager", "wandering_trader",
            // Golems / helpers
            "iron_golem", "snow_golem", "allay",
            // Bosses & unique
            "ender_dragon", "wither", "elder_guardian", "warden",
            // Mounts / pack animals
            "horse", "donkey", "mule", "llama", "trader_llama",
            "skeleton_horse", "zombie_horse", "camel", "strider", "happy_ghast",
            // Special / valuable
            "sniffer", "creaking", "shulker",
            // Player-attached projectiles — a fishing bobber is a Projectile, so
            // it falls in the PROJECTILE category and would be cleared while a
            // player is actively fishing. Never clear it.
            "fishing_bobber"
    );

    private int intervalSeconds;
    private List<Integer> warnAt;
    private int secondsUntilClean;

    private boolean useDefaultBlacklist;
    private List<String> blacklist;

    /**
     * The mob stacker's PDC key ({@code umccore:stack_size}). A mob carrying it
     * only has a custom name because it is a stack label — not because a player
     * name-tagged it — so the custom-name protection must NOT apply to it.
     */
    private org.bukkit.NamespacedKey stackKey;

    public ClearLagModule() {
        super("clearlag");
    }

    @Override
    protected void enable() {
        intervalSeconds = Math.max(10, config().getInt("interval-seconds", 300));
        warnAt = config().getIntegerList("warn-at-seconds");
        secondsUntilClean = intervalSeconds;
        stackKey = new org.bukkit.NamespacedKey(plugin, "stack_size");
        useDefaultBlacklist = config().getBoolean("protect.use-default-blacklist", true);
        blacklist = config().getStringList("protect.blacklist-types").stream()
                .map(s -> s.toLowerCase(java.util.Locale.ROOT)).toList();

        // One-second heartbeat drives the schedule + chat warnings + cleanup.
        track(scheduler.runTimer(this::tick, 20L, 20L));
        // Per-tick repaint of the action-bar countdown so it stays visible even
        // alongside the action-bar module.
        track(scheduler.runTimer(this::countdownTick, 1L, 1L));
    }

    private void tick() {
        secondsUntilClean--;

        // Chat warnings only at the configured marks, and only under a minute so
        // long intervals don't spam players early.
        if (secondsUntilClean > 0 && secondsUntilClean < 60 && warnAt.contains(secondsUntilClean)) {
            broadcastWarning(secondsUntilClean);
        }

        if (secondsUntilClean <= 0) {
            int removed = runCleanup(false);
            announceDone(removed);
            secondsUntilClean = intervalSeconds;
        }
    }

    /**
     * Runs every tick. During the final countdown window it repaints the
     * action-bar countdown each tick so it stays visible even if the action-bar
     * module is also writing there (last writer per tick wins; repainting every
     * tick lets the countdown dominate).
     */
    private void countdownTick() {
        int cd = config().getInt("actionbar-countdown-seconds", 10);
        if (cd > 0 && secondsUntilClean > 0 && secondsUntilClean <= cd) {
            broadcastActionBarCountdown(secondsUntilClean);
        }
    }

    private void broadcastWarning(int seconds) {
        var msg = Text.mm(config().getString("messages.warn",
                        "<gray>Clearing ground entities in <yellow>{seconds}s</yellow>...</gray>")
                .replace("{seconds}", String.valueOf(seconds)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
    }

    /** Live per-second action-bar countdown with MiniMessage formatting. */
    private void broadcastActionBarCountdown(int seconds) {
        String template = config().getString("messages.actionbar",
                "<bold><gradient:#ff512f:#dd2476>CLEAR LAG</gradient></bold> "
                        + "<gray>in</gray> <bold><yellow>{seconds}s</yellow></bold>");
        var msg = Text.mm(template.replace("{seconds}", String.valueOf(seconds)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(msg);
        }
    }

    private void announceDone(int removed) {
        var msg = Text.mm(config().getString("messages.done",
                        "<green>Removed <yellow>{count}</yellow> entities.</green>")
                .replace("{count}", String.valueOf(removed)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
    }

    /**
     * Removes all configured entity categories immediately.
     *
     * @param lightMode if true, only remove dropped items + hostile mobs in
     *                  playerless chunks (used by auto-optimize). Ignores the
     *                  configured category toggles for a lighter sweep.
     * @return number of entities removed
     */
    public int runCleanup(boolean lightMode) {
        boolean rmItems = lightMode || config().getBoolean("remove.dropped-items", true);
        boolean rmHostile = lightMode || config().getBoolean("remove.hostile-mobs", false);
        boolean rmPassive = !lightMode && config().getBoolean("remove.passive-mobs", false);
        boolean rmProjectiles = !lightMode && config().getBoolean("remove.projectiles", true);
        boolean rmXp = !lightMode && config().getBoolean("remove.experience-orbs", false);
        boolean playerlessOnly = lightMode || config().getBoolean("protect.playerless-chunks-only", false);

        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (isProtected(e)) {
                    continue;
                }
                if (playerlessOnly && hasNearbyPlayer(e)) {
                    continue;
                }
                boolean remove = switch (categoryOf(e)) {
                    case ITEM -> rmItems;
                    case HOSTILE -> rmHostile;
                    case PASSIVE -> rmPassive;
                    case PROJECTILE -> rmProjectiles;
                    case XP -> rmXp;
                    case OTHER -> false;
                };
                if (remove) {
                    e.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    private enum Category { ITEM, HOSTILE, PASSIVE, PROJECTILE, XP, OTHER }

    private Category categoryOf(Entity e) {
        if (e instanceof Item) return Category.ITEM;
        if (e.getType() == EntityType.EXPERIENCE_ORB) return Category.XP;
        if (e instanceof Projectile) return Category.PROJECTILE;
        if (e instanceof Mob mob) {
            // Monster/hostile detection: enemies target players.
            return isHostile(mob) ? Category.HOSTILE : Category.PASSIVE;
        }
        return Category.OTHER;
    }

    private boolean isHostile(Mob mob) {
        return mob instanceof org.bukkit.entity.Monster
                || mob instanceof org.bukkit.entity.Flying    // ghast, phantom
                || mob instanceof org.bukkit.entity.Slime;
    }

    private boolean isProtected(Entity e) {
        if (e instanceof Player) return true;

        // Type blacklist: never clear these mob types (villagers, bosses,
        // mounts, pets, etc.). Admin list + built-in default unless opted out.
        String type = e.getType().name().toLowerCase(java.util.Locale.ROOT);
        if (blacklist.contains(type)) return true;
        if (useDefaultBlacklist && DEFAULT_BLACKLIST.contains(type)) return true;

        // Custom-name protection is meant for named MOBS/entities the owner wants
        // to keep. It must NOT protect:
        //   - dropped items (the item stacker gives them an "x{amount}" label)
        //   - stacked mobs (the mob stacker gives them a "{type} x{amount}" label)
        // Both carry the stacker's PDC key, so their name is a stack label, not a
        // player-given name tag. Excluding them lets clearlag clear stacks.
        if (config().getBoolean("protect.custom-named", true)
                && !(e instanceof Item)
                && !isStackLabel(e)
                && e.customName() != null) return true;

        if (config().getBoolean("protect.tamed", true)
                && e instanceof Tameable t && t.isTamed()) return true;

        if (config().getBoolean("protect.leashed", true)
                && e instanceof LivingEntity le && le.isLeashed()) return true;

        // A mob riding a boat/minecart/another entity — never clear it.
        if (config().getBoolean("protect.in-vehicle", true)
                && e.isInsideVehicle()) return true;

        // A mob that is carrying passengers (e.g. a jockey, or a horse a player
        // set up) — clearing it would eject/kill the riders.
        if (config().getBoolean("protect.has-passengers", true)
                && !e.getPassengers().isEmpty()) return true;

        if (config().getBoolean("protect.armor-stands", true)
                && e.getType() == EntityType.ARMOR_STAND) return true;

        // Persistent mobs (name-tagged, spawn-egg, or flagged not to despawn) are
        // usually placed intentionally by players; keep them by default.
        if (config().getBoolean("protect.persistent", true)
                && e instanceof Mob mob && mob.isPersistent()) return true;

        // Mobs wearing/holding equipment (armed piglins, kitted mobs, mobs given
        // items) are typically special — don't clear them by default.
        if (config().getBoolean("protect.equipped", true)
                && e instanceof LivingEntity living && hasEquipment(living)) return true;

        return false;
    }

    /**
     * @return true if the entity's custom name is a stacker label (it carries
     *         the mob stacker's {@code stack_size} PDC key), i.e. its name is
     *         not a player-given name tag.
     */
    private boolean isStackLabel(Entity e) {
        return e.getPersistentDataContainer()
                .has(stackKey, org.bukkit.persistence.PersistentDataType.INTEGER);
    }

    /** @return true if the living entity has any armour or hand item equipped. */
    private boolean hasEquipment(LivingEntity living) {
        var eq = living.getEquipment();
        if (eq == null) {
            return false;
        }
        for (var slot : org.bukkit.inventory.EquipmentSlot.values()) {
            var item = eq.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNearbyPlayer(Entity e) {
        // "Nearby" = same chunk or an immediately adjacent chunk.
        int cx = e.getLocation().getBlockX() >> 4;
        int cz = e.getLocation().getBlockZ() >> 4;
        for (Player p : e.getWorld().getPlayers()) {
            int px = p.getLocation().getBlockX() >> 4;
            int pz = p.getLocation().getBlockZ() >> 4;
            if (Math.abs(px - cx) <= 1 && Math.abs(pz - cz) <= 1) {
                return true;
            }
        }
        return false;
    }

    /** @return seconds remaining until the next scheduled cleanup. */
    public int secondsUntilClean() {
        return secondsUntilClean;
    }
}
