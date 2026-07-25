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

    private int intervalSeconds;
    private List<Integer> warnAt;
    private int secondsUntilClean;

    public ClearLagModule() {
        super("clearlag");
    }

    @Override
    protected void enable() {
        intervalSeconds = Math.max(10, config().getInt("interval-seconds", 300));
        warnAt = config().getIntegerList("warn-at-seconds");
        secondsUntilClean = intervalSeconds;

        // One-second heartbeat drives both the countdown and the cleanup.
        track(scheduler.runTimer(this::tick, 20L, 20L));
    }

    private void tick() {
        secondsUntilClean--;
        if (warnAt.contains(secondsUntilClean) && secondsUntilClean > 0) {
            broadcastWarning(secondsUntilClean);
        }
        if (secondsUntilClean <= 0) {
            int removed = runCleanup(false);
            announceDone(removed);
            secondsUntilClean = intervalSeconds;
        }
    }

    private void broadcastWarning(int seconds) {
        var msg = Text.mm(config().getString("messages.warn",
                        "<gray>Clearing ground entities in <yellow>{seconds}s</yellow>...</gray>")
                .replace("{seconds}", String.valueOf(seconds)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
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
        if (config().getBoolean("protect.custom-named", true)
                && e.customName() != null) return true;
        if (config().getBoolean("protect.tamed", true)
                && e instanceof Tameable t && t.isTamed()) return true;
        if (config().getBoolean("protect.leashed", true)
                && e instanceof LivingEntity le && le.isLeashed()) return true;
        if (config().getBoolean("protect.in-vehicle", true)
                && e.isInsideVehicle()) return true;
        if (config().getBoolean("protect.armor-stands", true)
                && e.getType() == EntityType.ARMOR_STAND) return true;
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
