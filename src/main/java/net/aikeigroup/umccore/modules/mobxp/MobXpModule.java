package net.aikeigroup.umccore.modules.mobxp;

import net.aikeigroup.umccore.core.AbstractModule;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Adjusts mob experience and item drops via multipliers and conditions.
 *
 * <p>Supports global and per-type multipliers, a "player kill required" gate,
 * and an anti-farm option that zeroes XP from spawner-spawned mobs. The spawn
 * reason is tagged onto each mob's PDC at spawn time so it is known at death.</p>
 */
public final class MobXpModule extends AbstractModule {

    private NamespacedKey spawnerKey;

    public MobXpModule() {
        super("mobxp");
    }

    @Override
    protected void enable() {
        spawnerKey = new NamespacedKey(plugin, "from_spawner");
        listen(new SpawnTagListener());
        listen(new DeathListener());
    }

    /** Tags spawner-spawned mobs so death handling can detect them. */
    private final class SpawnTagListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSpawn(CreatureSpawnEvent event) {
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER
                    || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) {
                event.getEntity().getPersistentDataContainer()
                        .set(spawnerKey, PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    private final class DeathListener implements Listener {
        @EventHandler(priority = EventPriority.HIGH)
        public void onDeath(EntityDeathEvent event) {
            LivingEntity entity = event.getEntity();
            boolean playerKill = entity.getKiller() != null;
            String type = entity.getType().name().toLowerCase(Locale.ROOT);

            // --- XP ---
            handleXp(event, entity, playerKill, type);

            // --- Drops ---
            handleDrops(event, playerKill, type);
        }
    }

    private void handleXp(EntityDeathEvent event, LivingEntity entity, boolean playerKill, String type) {
        if (config().getBoolean("xp.require-player-kill", true) && !playerKill) {
            event.setDroppedExp(0);
            return;
        }
        boolean fromSpawner = entity.getPersistentDataContainer()
                .has(spawnerKey, PersistentDataType.BYTE);
        if (fromSpawner && config().getBoolean("xp.no-xp-from-spawners", false)) {
            event.setDroppedExp(0);
            return;
        }
        double mult = config().getDouble("xp.global-multiplier", 1.0)
                * config().getDouble("xp.per-type." + type, 1.0);
        event.setDroppedExp((int) Math.round(event.getDroppedExp() * mult));
    }

    private void handleDrops(EntityDeathEvent event, boolean playerKill, String type) {
        if (config().getBoolean("drops.require-player-kill", false) && !playerKill) {
            event.getDrops().clear();
            return;
        }
        double mult = config().getDouble("drops.global-multiplier", 1.0)
                * config().getDouble("drops.per-type." + type, 1.0);
        if (mult == 1.0) {
            return;
        }
        if (mult <= 0.0) {
            event.getDrops().clear();
            return;
        }
        // Scale each drop's amount by the multiplier (rounded).
        for (ItemStack drop : event.getDrops()) {
            int newAmount = (int) Math.round(drop.getAmount() * mult);
            drop.setAmount(Math.max(1, newAmount));
        }
    }
}
