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
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private boolean perMobPermission;
    private double maxHealth;
    private List<String> blacklist;

    // Throw-on-hold-sneak with an oscillating charge meter.
    private boolean throwEnabled;
    private int tapMaxMs;         // release under this = drop, not throw
    private double throwMinPower;
    private double throwMaxPower;
    private int chargeCycleTicks;  // ticks for one full->min->full oscillation

    /** Active charge sessions (player holding sneak while carrying a mob). */
    private final Map<UUID, ChargeSession> charging = new ConcurrentHashMap<>();

    /** Per-player charge state while holding sneak. */
    private static final class ChargeSession {
        final long startMs;
        int ticks;
        double lastFraction; // 0..1 charge at the latest tick
        ChargeSession(long startMs) {
            this.startMs = startMs;
        }
    }

    public MobPickupModule() {
        super("pickup");
    }

    @Override
    protected String configName() {
        return "pickup";
    }

    @Override
    protected void enable() {
        // Default false: sneak is reserved for the drop/throw control, so
        // pickup is a plain right-click.
        requireSneak = config().getBoolean("require-sneak", false);
        allowHostile = config().getBoolean("allow-hostile", true);
        allowPassive = config().getBoolean("allow-passive", true);
        requireEmptyHand = config().getBoolean("require-empty-hand", true);
        perMobPermission = config().getBoolean("per-mob-permission", false);
        maxHealth = config().getDouble("max-health", 0); // 0 = no limit
        blacklist = config().getStringList("blacklist-types").stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).toList();

        throwEnabled = config().getBoolean("throw.enabled", true);
        tapMaxMs = Math.max(0, config().getInt("throw.tap-max-ms", 200));
        throwMinPower = config().getDouble("throw.min-power", 0.6);
        throwMaxPower = config().getDouble("throw.max-power", 2.4);
        chargeCycleTicks = Math.max(10, config().getInt("throw.charge-cycle-ticks", 30));

        listen(new PickupListener());
        // Per-tick charge meter animation for players holding sneak to throw.
        track(scheduler.runTimer(this::chargeTick, 1L, 1L));
    }

    @Override
    protected void disable() {
        charging.clear();
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

        // Sneak controls drop/throw while carrying a mob:
        //   - quick tap sneak (< tap-max-ms)  -> drop the mob in front
        //   - hold sneak                       -> a power meter oscillates
        //     (full -> min -> full ...) on the action bar; release to throw the
        //     mob with the power shown at that instant.
        @EventHandler
        public void onToggleSneak(PlayerToggleSneakEvent event) {
            Player player = event.getPlayer();
            if (!isCarrying(player)) {
                charging.remove(player.getUniqueId());
                return;
            }
            if (event.isSneaking()) {
                if (!throwEnabled) {
                    dropCarried(player);
                    return;
                }
                // Begin charging (the per-tick task animates the meter).
                charging.put(player.getUniqueId(), new ChargeSession(System.currentTimeMillis()));
            } else {
                ChargeSession session = charging.remove(player.getUniqueId());
                if (session == null) {
                    // Already sneaking at pickup time — ignore this release.
                    return;
                }
                long held = System.currentTimeMillis() - session.startMs;
                if (!throwEnabled || held < tapMaxMs) {
                    dropCarried(player);
                } else {
                    throwCarried(player, session.lastFraction);
                }
            }
        }

        @EventHandler
        public void onDismount(EntityDismountEvent event) {
            if (event.getDismounted() instanceof Player player && event.getEntity() instanceof Mob) {
                charging.remove(player.getUniqueId());
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            // Drop anything carried so it isn't lost when the player leaves.
            charging.remove(event.getPlayer().getUniqueId());
            dropCarried(event.getPlayer());
        }
    }

    /**
     * Per-tick charge animation: for each player holding sneak while carrying a
     * mob, advance an oscillating power fraction and render it as a bar on the
     * action bar so they can time a throw.
     */
    private void chargeTick() {
        if (charging.isEmpty()) {
            return;
        }
        for (var entry : charging.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            ChargeSession session = entry.getValue();
            if (player == null || !isCarrying(player)) {
                charging.remove(entry.getKey());
                continue;
            }
            session.ticks++;
            // Triangle wave 0..1..0 over chargeCycleTicks: full -> min -> full.
            double phase = (session.ticks % chargeCycleTicks) / (double) chargeCycleTicks;
            double fraction = 1.0 - Math.abs(1.0 - 2.0 * phase); // 0..1..0
            session.lastFraction = fraction;
            player.sendActionBar(renderChargeBar(fraction));
        }
    }

    /** Builds an eye-catching MiniMessage power bar for the given 0..1 charge. */
    private net.kyori.adventure.text.Component renderChargeBar(double fraction) {
        int total = 20;
        int filled = (int) Math.round(fraction * total);
        String tmpl = plugin.configs().messages().getString("pickup.charge",
                "<gray>Throw power</gray> <white>[</white>{bar}<white>]</white> <yellow>{percent}%</yellow>");
        // Colour shifts green -> yellow -> red as power rises, for quick reading.
        String color = fraction < 0.4 ? "#57f287" : fraction < 0.75 ? "#faa61a" : "#ff4d4d";
        StringBuilder bar = new StringBuilder();
        bar.append("<").append(color).append(">");
        bar.append("|".repeat(Math.max(0, filled)));
        bar.append("<dark_gray>");
        bar.append("|".repeat(Math.max(0, total - filled)));
        String out = tmpl
                .replace("{bar}", bar.toString())
                .replace("{percent}", String.valueOf((int) Math.round(fraction * 100)));
        return Text.mm(out);
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
     * Drops the carried mob gently in front of the player (no launch).
     */
    public void dropCarried(Player player) {
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof Mob mob) {
                player.removePassenger(mob);
                Location drop = player.getEyeLocation().add(player.getLocation().getDirection());
                mob.teleport(drop);
                feedback(player, "pickup.dropped");
            }
        }
    }

    /**
     * Throws the carried mob forward with power scaled by {@code fraction}
     * (0..1) — the value the charge meter showed when the player released sneak.
     */
    public void throwCarried(Player player, double fraction) {
        double power = throwMinPower + (throwMaxPower - throwMinPower) * clamp01(fraction);
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof Mob mob) {
                player.removePassenger(mob);
                Location from = player.getEyeLocation().add(player.getLocation().getDirection());
                mob.teleport(from);
                // Launch along the look direction, with a slight upward arc.
                Vector velocity = player.getLocation().getDirection().normalize()
                        .multiply(power).add(new Vector(0, 0.35, 0));
                mob.setVelocity(velocity);
                player.getWorld().playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_SNOWBALL_THROW, 1f, 1f);
                feedback(player, "pickup.thrown");
            }
        }
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
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
        // Optional per-mob permission: umccore.pickup.mob.<type> (e.g.
        // umccore.pickup.mob.cow). The wildcard umccore.pickup.mob.* grants all.
        if (perMobPermission && !player.hasPermission("umccore.pickup.mob." + type)) {
            feedback(player, "pickup.no-mob-perm");
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
