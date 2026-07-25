package net.aikeigroup.umccore.modules.pickup;

import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Locale;

/**
 * Lets players pick up a mob and carry it on their head, then drop it.
 *
 * <p>Interaction: sneak + right-click a mob to lift it (it becomes a passenger
 * on the player's head); sneak + right-click again (or the mob dismounts) to set
 * it down in front of the player. Gated by permission and configurable rules
 * (which mob categories, blacklist, require sneak, hold item).</p>
 *
 * <p>Toggle with {@code modules.pickup} in config.yml.</p>
 */
public final class MobPickupModule extends AbstractModule {

    private boolean requireSneak;
    private boolean allowHostile;
    private boolean allowPassive;
    private boolean requireEmptyHand;
    private double maxHealth;
    private List<String> blacklist;

    public MobPickupModule() {
        super("pickup");
    }

    @Override
    protected String configName() {
        return "pickup";
    }

    @Override
    protected void enable() {
        requireSneak = config().getBoolean("require-sneak", true);
        allowHostile = config().getBoolean("allow-hostile", true);
        allowPassive = config().getBoolean("allow-passive", true);
        requireEmptyHand = config().getBoolean("require-empty-hand", true);
        maxHealth = config().getDouble("max-health", 0); // 0 = no limit
        blacklist = config().getStringList("blacklist-types").stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).toList();

        listen(new PickupListener());
    }

    private final class PickupListener implements Listener {

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onInteract(PlayerInteractEntityEvent event) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Player player = event.getPlayer();
            if (!player.hasPermission("umccore.pickup.use")) {
                return;
            }
            if (requireSneak && !player.isSneaking()) {
                return;
            }
            if (requireEmptyHand
                    && !player.getInventory().getItemInMainHand().getType().isAir()) {
                return;
            }
            if (!(event.getRightClicked() instanceof Mob mob)) {
                return;
            }

            // If already carrying something, drop it instead of picking a new one.
            if (isCarrying(player)) {
                event.setCancelled(true);
                dropCarried(player);
                return;
            }

            if (!canPickup(player, mob)) {
                return;
            }

            event.setCancelled(true);
            pickUp(player, mob);
        }

        // Sneak + right-click air/block while carrying a mob drops it.
        @EventHandler(priority = EventPriority.LOW)
        public void onInteractAir(PlayerInteractEvent event) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            if (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            Player player = event.getPlayer();
            if (requireSneak && !player.isSneaking()) {
                return;
            }
            if (isCarrying(player)) {
                dropCarried(player);
            }
        }

        // Right-clicking with an occupied head (not on a mob) drops the carried
        // mob. We also catch dismount to keep state clean.
        @EventHandler
        public void onDismount(EntityDismountEvent event) {
            if (event.getDismounted() instanceof Player player && event.getEntity() instanceof Mob) {
                // A carried mob left the player's head; nothing to persist.
                feedback(player, "pickup.dropped");
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            // Drop anything carried so it isn't lost when the player leaves.
            dropCarried(event.getPlayer());
        }
    }

    // --- Pickup / drop logic ----------------------------------------------

    private void pickUp(Player player, Mob mob) {
        // The mob rides the player's head.
        boolean ok = player.addPassenger(mob);
        if (ok) {
            feedback(player, "pickup.picked");
        }
    }

    /**
     * Drops the first mob the player is carrying, placing it where they look.
     * Called from a companion sneak-interact handler and on quit.
     */
    public void dropCarried(Player player) {
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof Mob mob) {
                player.removePassenger(mob);
                Location drop = player.getEyeLocation().add(player.getLocation().getDirection());
                mob.teleport(drop);
            }
        }
    }

    private boolean isCarrying(Player player) {
        return player.getPassengers().stream().anyMatch(p -> p instanceof Mob);
    }

    private boolean canPickup(Player player, Mob mob) {
        String type = mob.getType().name().toLowerCase(Locale.ROOT);
        if (blacklist.contains(type)) {
            return false;
        }
        boolean hostile = isHostile(mob);
        if (hostile && !allowHostile) {
            feedback(player, "pickup.not-allowed");
            return false;
        }
        if (!hostile && !allowPassive) {
            feedback(player, "pickup.not-allowed");
            return false;
        }
        if (hostile && !player.hasPermission("umccore.pickup.hostile")) {
            feedback(player, "pickup.no-hostile-perm");
            return false;
        }
        if (maxHealth > 0 && mob instanceof LivingEntity le
                && le.getHealth() > maxHealth) {
            return false;
        }
        // Never pick up something already ridden/carrying, or a boss.
        if (mob.isInsideVehicle() || !mob.getPassengers().isEmpty()) {
            return false;
        }
        return true;
    }

    private boolean isHostile(Mob mob) {
        return mob instanceof org.bukkit.entity.Monster
                || mob instanceof org.bukkit.entity.Flying
                || mob instanceof org.bukkit.entity.Slime;
    }

    private void feedback(Player player, String key) {
        player.sendActionBar(Text.mm(plugin.configs().messages().getString(key, key)));
    }
}
