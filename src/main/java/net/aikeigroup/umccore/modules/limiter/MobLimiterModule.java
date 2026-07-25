package net.aikeigroup.umccore.modules.limiter;

import net.aikeigroup.umccore.core.AbstractModule;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Locale;

/**
 * Caps the number of mobs per chunk (or per radius) to prevent entity buildup
 * that inflates MSPT.
 *
 * <p>On {@link CreatureSpawnEvent} it counts existing mobs of the relevant
 * category near the spawn location and cancels (hard) or probabilistically
 * cancels (soft) spawns that would exceed the configured limits. Only the
 * configured spawn sources are affected; player-built farms can be exempted by
 * disabling the {@code spawner} source, etc.</p>
 */
public final class MobLimiterModule extends AbstractModule {

    private enum Mode { CHUNK, RADIUS }
    private enum Category { HOSTILE, PASSIVE, AMBIENT }

    public MobLimiterModule() {
        super("moblimiter");
    }

    @Override
    protected String configName() {
        return "limiter";
    }

    @Override
    protected void enable() {
        listen(new SpawnListener());
    }

    private final class SpawnListener implements Listener {

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onSpawn(CreatureSpawnEvent event) {
            if (!appliesToSource(event.getSpawnReason())) {
                return;
            }
            if (!(event.getEntity() instanceof Mob mob)) {
                return;
            }

            ConfigurationSection perWorld = worldSection(event.getLocation());
            Category category = categorize(mob);
            int limit = limitFor(perWorld, category);
            int totalLimit = perWorld != null
                    ? perWorld.getInt("limits.total", config().getInt("limits.total", 0))
                    : config().getInt("limits.total", 0);

            int categoryCount = countNearby(event.getLocation(), category);
            int totalCount = countNearby(event.getLocation(), null);

            boolean overCategory = limit > 0 && categoryCount >= limit;
            boolean overTotal = totalLimit > 0 && totalCount >= totalLimit;

            if ((overCategory || overTotal) && shouldCancel()) {
                event.setCancelled(true);
            }
        }
    }

    private boolean appliesToSource(CreatureSpawnEvent.SpawnReason reason) {
        return switch (reason) {
            case NATURAL, JOCKEY, PATROL, RAID, REINFORCEMENTS, VILLAGE_DEFENSE, VILLAGE_INVASION ->
                    config().getBoolean("apply-to.natural", true);
            case SPAWNER, TRIAL_SPAWNER, SPAWNER_EGG ->
                    config().getBoolean("apply-to.spawner", true);
            case BREEDING, EGG ->
                    config().getBoolean("apply-to.breeding", false);
            default ->
                    config().getBoolean("apply-to.other", false);
        };
    }

    private boolean shouldCancel() {
        String type = config().getString("enforcement.type", "HARD").toUpperCase(Locale.ROOT);
        if (type.equals("SOFT")) {
            double chance = config().getDouble("enforcement.soft-cancel-chance", 0.5);
            // Deterministic-ish: use nano time bit as pseudo-random source to
            // avoid Math.random() (kept simple; exact distribution not critical).
            double roll = (System.nanoTime() & 0xFFFF) / 65535.0;
            return roll < chance;
        }
        return true; // HARD
    }

    private ConfigurationSection worldSection(Location loc) {
        if (loc.getWorld() == null) return null;
        return config().getConfigurationSection("per-world." + loc.getWorld().getName());
    }

    private int limitFor(ConfigurationSection perWorld, Category category) {
        String key = "limits." + category.name().toLowerCase(Locale.ROOT);
        int base = config().getInt(key, defaultLimit(category));
        if (perWorld != null && perWorld.contains(key)) {
            return perWorld.getInt(key, base);
        }
        return base;
    }

    private int defaultLimit(Category category) {
        return switch (category) {
            case HOSTILE -> 30;
            case PASSIVE -> 20;
            case AMBIENT -> 10;
        };
    }

    private Category categorize(Mob mob) {
        if (mob instanceof org.bukkit.entity.Ambient) return Category.AMBIENT;
        if (mob instanceof org.bukkit.entity.Monster
                || mob instanceof org.bukkit.entity.Flying
                || mob instanceof org.bukkit.entity.Slime) {
            return Category.HOSTILE;
        }
        return Category.PASSIVE;
    }

    /**
     * Counts mobs near a location matching a category (or all mobs if null).
     */
    private int countNearby(Location loc, Category category) {
        Mode mode = Mode.valueOf(config().getString("mode", "CHUNK").toUpperCase(Locale.ROOT));
        int count = 0;
        Iterable<Entity> candidates;
        if (mode == Mode.CHUNK) {
            if (loc.getWorld() == null || !loc.getChunk().isLoaded()) return 0;
            candidates = java.util.Arrays.asList(loc.getChunk().getEntities());
        } else {
            double r = config().getInt("radius", 8);
            candidates = loc.getWorld() == null
                    ? java.util.List.of()
                    : loc.getWorld().getNearbyEntities(loc, r, r, r);
        }
        for (Entity e : candidates) {
            if (e instanceof Mob mob && !(e instanceof Player)) {
                if (category == null || categorize(mob) == category) {
                    count++;
                }
            }
        }
        return count;
    }
}
