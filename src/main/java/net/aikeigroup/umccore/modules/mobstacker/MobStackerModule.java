package net.aikeigroup.umccore.modules.mobstacker;

import net.aikeigroup.umccore.api.events.StackKillEvent;
import net.aikeigroup.umccore.api.events.StackMergeEvent;
import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Merges nearby same-type mobs into a single entity that represents the whole
 * stack, cutting live entity count (and therefore MSPT).
 *
 * <p>Stack size is stored in the entity's {@link org.bukkit.persistence.PersistentDataContainer}
 * so it survives chunk unloads and reloads. A periodic scan merges eligible
 * neighbours; on death the stack either drops by one ({@code kill-one-at-a-time})
 * or dies entirely, multiplying loot/XP accordingly.</p>
 */
public final class MobStackerModule extends AbstractModule {

    /**
     * Mobs that must never be stacked by default: unique/boss mobs, trading and
     * utility NPCs, rideable mounts, pets, and other entities where stacking
     * would break gameplay (villager trades, golem protection, mounts, etc.).
     * Admins can override via {@code use-default-blacklist: false}.
     */
    private static final Set<String> DEFAULT_BLACKLIST = Set.of(
            // Trading / utility NPCs
            "villager", "zombie_villager", "wandering_trader",
            // Golems / helpers
            "iron_golem", "snow_golem", "allay",
            // Bosses & unique
            "ender_dragon", "wither", "elder_guardian", "warden",
            // Rideable mounts / pack animals (a stacked mount can't be ridden)
            "horse", "donkey", "mule", "llama", "trader_llama",
            "skeleton_horse", "zombie_horse", "camel", "strider", "pig",
            "happy_ghast",
            // Sensitive / special-behaviour mobs
            "sniffer", "creaking", "shulker", "ravager"
    );

    /** PDC key holding the stack size on a mob. Absence == size 1. */
    private NamespacedKey sizeKey;

    private int mergeRadius;
    private int maxStackSize;
    private int scanInterval;
    private String displayName;
    private boolean killOneAtATime;
    private List<String> blacklist;
    private List<String> whitelist;
    private boolean useDefaultBlacklist;
    private boolean protectEquipped;
    private boolean protectPersistent;
    private boolean matchAge;
    private boolean matchState;
    private boolean splitOnInteract;
    private int splitCooldownMs;
    private boolean unstackToolEnabled;
    private org.bukkit.Material unstackToolMaterial;
    private boolean unstackToolSneakOnly;
    private int unstackToolMax;

    /**
     * Mobs recently peeled off a stack by interaction, mapped to the time until
     * which they must NOT be re-merged. Without this, the merge scan would
     * instantly re-stack a sheep the player just split off to shear it.
     */
    private final java.util.Map<java.util.UUID, Long> noMergeUntil =
            new java.util.concurrent.ConcurrentHashMap<>();

    public MobStackerModule() {
        super("mobstacker");
    }

    @Override
    protected String configName() {
        return "stacker";
    }

    @Override
    protected void enable() {
        sizeKey = new NamespacedKey(plugin, "stack_size");

        mergeRadius = config().getInt("mob-stacker.merge-radius", 5);
        maxStackSize = config().getInt("mob-stacker.max-stack-size", 100);
        scanInterval = Math.max(20, config().getInt("mob-stacker.scan-interval-ticks", 40));
        displayName = config().getString("mob-stacker.display-name", "<yellow>{type} <gray>x{amount}");
        killOneAtATime = config().getBoolean("mob-stacker.kill-one-at-a-time", true);
        blacklist = lower(config().getStringList("mob-stacker.blacklist-types"));
        whitelist = lower(config().getStringList("mob-stacker.whitelist-types"));
        useDefaultBlacklist = config().getBoolean("mob-stacker.use-default-blacklist", true);
        protectEquipped = config().getBoolean("mob-stacker.protect-equipped", true);
        protectPersistent = config().getBoolean("mob-stacker.protect-persistent", false);
        matchAge = config().getBoolean("mob-stacker.match-age", true);
        matchState = config().getBoolean("mob-stacker.match-state", true);

        if (!config().getBoolean("mob-stacker.enabled", true)) {
            plugin.getLogger().info("Mob stacker sub-toggle is off; module idle.");
            return;
        }

        splitOnInteract = config().getBoolean("mob-stacker.split-on-interact", true);
        splitCooldownMs = Math.max(0, config().getInt("mob-stacker.split-cooldown-seconds", 5)) * 1000;

        unstackToolEnabled = config().getBoolean("mob-stacker.unstack-tool.enabled", true);
        unstackToolMaterial = parseMaterial(
                config().getString("mob-stacker.unstack-tool.item", "STICK"),
                org.bukkit.Material.STICK);
        unstackToolSneakOnly = config().getBoolean("mob-stacker.unstack-tool.require-sneak", false);
        unstackToolMax = config().getInt("mob-stacker.unstack-tool.max-per-use", 64);

        listen(new DeathListener());
        listen(new BreedListener());
        if (splitOnInteract || unstackToolEnabled) {
            listen(new InteractListener());
        }
        track(scheduler.runTimer(this::scanAll, scanInterval, scanInterval));
    }

    @Override
    protected void disable() {
        // Clear transient split cooldowns so a reload starts clean.
        noMergeUntil.clear();
    }

    // --- Merge scan --------------------------------------------------------

    private void scanAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntitiesByClass(Mob.class)) {
                // The list is a snapshot: an entity earlier in it may have been
                // removed (absorbed into another stack) by the time we reach it.
                // Skip anything no longer valid so we never merge INTO a dead
                // entity (which previously made both mobs disappear).
                if (e instanceof Mob mob && mob.isValid() && canStack(mob)) {
                    tryMergeInto(mob);
                }
            }
        }
    }

    private void tryMergeInto(Mob stack) {
        int size = sizeOf(stack);
        if (size >= maxStackSize && maxStackSize > 0) {
            return;
        }
        double r = mergeRadius;
        for (Entity near : stack.getWorld().getNearbyEntities(stack.getLocation(), r, r, r)) {
            if (near == stack) continue;
            if (!(near instanceof Mob other)) continue;
            // Guard: both the surviving stack and the candidate must still be
            // alive. If a prior iteration removed one of them, bail out.
            if (!stack.isValid()) return;
            if (!other.isValid()) continue;
            if (other.getType() != stack.getType()) continue;
            if (!canStack(other)) continue;
            // Don't mix babies and adults in one stack (keeps breeding/behaviour
            // sane and avoids a baby stack "growing up" all at once).
            if (matchAge && !sameAgeGroup(stack, other)) continue;
            // Don't mix mobs whose visible state differs — e.g. a sheared sheep
            // must NOT merge back into a woolly stack, a differently-dyed sheep
            // stays separate, etc. Otherwise shearing one mob would "spread" to
            // the whole stack when it re-merges.
            if (matchState && !sameState(stack, other)) continue;

            int otherSize = sizeOf(other);
            int room = maxStackSize > 0 ? (maxStackSize - size) : Integer.MAX_VALUE;
            if (room <= 0) break;
            int add = Math.min(otherSize, room);

            StackMergeEvent event = new StackMergeEvent(stack, other, size, add);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;

            size += add;
            setSize(stack, size);
            if (add >= otherSize) {
                other.remove();
            } else {
                setSize(other, otherSize - add);
                refreshName(other);
            }
            if (maxStackSize > 0 && size >= maxStackSize) break;
        }
        refreshName(stack);
    }

    // --- Breeding handling -------------------------------------------------

    /**
     * Keeps a freshly-bred baby individual for a short window so it doesn't get
     * instantly swallowed into a baby stack — the player sees the baby they just
     * made, and it ages on its own before (optionally) re-merging.
     */
    private final class BreedListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreed(org.bukkit.event.entity.EntityBreedEvent event) {
            if (event.getEntity() instanceof Mob baby) {
                markNoMerge(baby);
            }
        }
    }

    // --- Death handling ----------------------------------------------------

    private final class DeathListener implements Listener {
        @EventHandler(priority = EventPriority.NORMAL)
        public void onDeath(EntityDeathEvent event) {
            LivingEntity dead = event.getEntity();
            int size = sizeOf(dead);
            if (size <= 1) {
                return; // not a stack
            }

            if (killOneAtATime) {
                // One dies; respawn a replacement carrying the remaining size.
                int remaining = size - 1;
                Bukkit.getPluginManager().callEvent(new StackKillEvent(dead, 1, remaining));
                if (dead instanceof Mob mob) {
                    respawnRemainder(mob, remaining);
                }
                // Vanilla drops for the single kill are left as-is.
            } else {
                // Whole stack dies: multiply drops and XP.
                Bukkit.getPluginManager().callEvent(new StackKillEvent(dead, size, 0));
                event.setDroppedExp(event.getDroppedExp() * size);
                var drops = event.getDrops();
                var multiplied = new java.util.ArrayList<>(drops);
                for (int i = 1; i < size; i++) {
                    multiplied.addAll(drops.stream().map(item -> item.clone()).toList());
                }
                drops.clear();
                drops.addAll(multiplied);
            }
        }
    }

    // --- Interaction handling ---------------------------------------------

    /**
     * When a player interacts with a stacked mob (shear a sheep, milk a cow,
     * dye/name/breed/leash it, etc.), vanilla only ever affects the single
     * representative entity — so a stack of 64 sheep would yield wool once and
     * then get stuck. To keep interactions behaving per-mob, we peel ONE mob off
     * the stack the instant it is clicked: the clicked entity becomes a normal
     * size-1 mob (so vanilla runs on it as usual), and a new stack of the
     * remaining (size-1) is spawned alongside it. Interacting repeatedly thus
     * works through the stack one mob at a time, exactly like unstacked mobs.
     */
    private final class InteractListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onInteract(PlayerInteractEntityEvent event) {
            // Only handle one hand to avoid double-processing.
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            if (!(event.getRightClicked() instanceof Mob mob)) {
                return;
            }
            int size = sizeOf(mob);
            if (size <= 1) {
                return; // not a stack — vanilla behaviour is already correct
            }

            Player player = event.getPlayer();

            // Manual full-unstack tool: right-click a stack while holding the
            // configured item (e.g. a stick) to break the WHOLE stack into
            // individual mobs at once. Takes priority over the per-interact
            // peel, and cancels the event so no vanilla interaction fires.
            if (unstackToolEnabled && isUnstackTool(player)) {
                event.setCancelled(true);
                if (player.hasPermission("umccore.stacker.unstack")) {
                    unstackFully(mob, size);
                }
                return;
            }

            if (!splitOnInteract) {
                return;
            }
            // Peel one off: the clicked mob becomes a single mob, and the rest
            // (size-1) split into a fresh stack. Vanilla then processes the
            // click on the now-single mob normally. Mark the peeled mob so the
            // merge scan doesn't instantly re-stack it while the player is still
            // interacting with it.
            setSize(mob, 1);
            refreshName(mob);
            markNoMerge(mob);
            splitRemainder(mob, size - 1);
        }
    }

    /** @return true if the player is holding the configured unstack tool (respecting sneak). */
    private boolean isUnstackTool(Player player) {
        if (unstackToolSneakOnly && !player.isSneaking()) {
            return false;
        }
        var inHand = player.getInventory().getItemInMainHand();
        return inHand != null && inHand.getType() == unstackToolMaterial;
    }

    /**
     * Fully unstacks a mob into individual entities: the clicked mob becomes a
     * single mob, and (size-1) fresh single mobs are spawned around it — up to
     * {@code unstack-tool.max-per-use}. Every resulting mob gets a no-merge
     * cooldown so they stay separated temporarily before the scan re-merges
     * them, giving the "temporary unstack" behaviour requested.
     */
    private void unstackFully(Mob stack, int size) {
        int toSpawn = Math.min(size - 1, Math.max(0, unstackToolMax - 1));

        // The clicked mob becomes a normal single mob.
        setSize(stack, 1);
        refreshName(stack);
        markNoMerge(stack);

        World world = stack.getWorld();
        var loc = stack.getLocation();
        EntityType type = stack.getType();
        boolean baby = stack instanceof org.bukkit.entity.Ageable ag && !ag.isAdult();

        // Spawn the rest as individual mobs on the next tick.
        runLaterTracked(() -> {
            for (int i = 0; i < toSpawn; i++) {
                Entity spawned = world.spawnEntity(loc, type);
                if (spawned instanceof Mob single) {
                    if (baby && single instanceof org.bukkit.entity.Ageable ag) {
                        ag.setBaby();
                    }
                    setSize(single, 1);
                    refreshName(single);
                    markNoMerge(single);
                }
            }
            // If the stack was larger than the per-use cap, leave the overflow
            // as a smaller stack next to the unstacked mobs.
            int overflow = size - 1 - toSpawn;
            if (overflow > 0) {
                Entity rest = world.spawnEntity(loc, type);
                if (rest instanceof Mob restStack) {
                    if (baby && restStack instanceof org.bukkit.entity.Ageable ag) {
                        ag.setBaby();
                    }
                    setSize(restStack, overflow);
                    refreshName(restStack);
                }
            }
        }, 1L);
    }

    private org.bukkit.Material parseMaterial(String name, org.bukkit.Material def) {
        if (name == null) {
            return def;
        }
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return m != null ? m : def;
    }

    /**
     * Spawns a fresh stack of {@code remaining} mobs of the same type next to a
     * mob that was just peeled off. Copies age so a baby stack stays babies.
     */
    private void splitRemainder(Mob from, int remaining) {
        if (remaining <= 0) {
            return;
        }
        World world = from.getWorld();
        var loc = from.getLocation();
        EntityType type = from.getType();
        boolean baby = from instanceof org.bukkit.entity.Ageable ag && !ag.isAdult();
        // Next tick so we don't spawn mid-interaction processing.
        runLaterTracked(() -> {
            Entity spawned = world.spawnEntity(loc, type);
            if (spawned instanceof Mob newStack) {
                if (baby && newStack instanceof org.bukkit.entity.Ageable ag) {
                    ag.setBaby();
                }
                setSize(newStack, remaining);
                refreshName(newStack);
            }
        }, 1L);
    }

    private void respawnRemainder(Mob dead, int remaining) {
        World world = dead.getWorld();
        var loc = dead.getLocation();
        EntityType type = dead.getType();
        // Spawn on the next tick so it doesn't collide with death processing.
        // Uses the self-removing tracked scheduler so these per-death tasks
        // don't accumulate in the tracking list.
        runLaterTracked(() -> {
            Entity spawned = world.spawnEntity(loc, type);
            if (spawned instanceof Mob mob) {
                setSize(mob, remaining);
                refreshName(mob);
            }
        }, 1L);
    }

    // --- Eligibility & helpers --------------------------------------------

    private boolean canStack(Mob mob) {
        String type = mob.getType().name().toLowerCase(Locale.ROOT);

        // Recently peeled off a stack by interaction — leave it alone for a bit
        // so the player can shear/milk/breed it without it re-stacking.
        if (isOnSplitCooldown(mob)) return false;

        // Whitelist wins: if set, only listed types stack.
        if (!whitelist.isEmpty() && !whitelist.contains(type)) return false;

        // Blacklists: admin list + the built-in default (villagers, bosses,
        // mounts, pets, etc.) unless the admin opts out.
        if (blacklist.contains(type)) return false;
        if (useDefaultBlacklist && DEFAULT_BLACKLIST.contains(type)) return false;

        // Never stack a mob a player is riding or that rides something, or that
        // carries passengers (e.g. a chicken jockey) — merging would eject them.
        if (mob.isInsideVehicle() || !mob.getPassengers().isEmpty()) return false;

        // A mob currently in love mode (fed a breeding item, waiting for a mate)
        // must stay individual — merging it would drop its love status and break
        // the breeding the player just started.
        if (mob instanceof org.bukkit.entity.Animals animal && animal.isLoveMode()) return false;

        // Player-named mobs (name tag) stay individual. Our own stack label is
        // detected by the size PDC key and is allowed.
        if (config().getBoolean("mob-stacker.protect-named", true) && mob.customName() != null) {
            if (!mob.getPersistentDataContainer().has(sizeKey, PersistentDataType.INTEGER)) {
                return false;
            }
        }

        if (config().getBoolean("mob-stacker.protect-tamed", true)
                && mob instanceof Tameable t && t.isTamed()) return false;
        if (config().getBoolean("mob-stacker.protect-leashed", true) && mob.isLeashed()) return false;

        // Persistent mobs (spawn-egg/named/no-despawn) are usually placed
        // intentionally by players; don't stack them by default.
        if (protectPersistent && mob.isPersistent()) return false;

        // Mobs wearing/holding gear (armed piglins, equipped zombies, mobs given
        // items) are typically special — protect them from stacking.
        if (protectEquipped && hasEquipment(mob)) return false;

        return true;
    }

    /** Marks a mob so the merge scan won't re-stack it until the cooldown ends. */
    private void markNoMerge(Mob mob) {
        if (splitCooldownMs > 0) {
            noMergeUntil.put(mob.getUniqueId(), System.currentTimeMillis() + splitCooldownMs);
        }
    }

    /** @return true if the mob is still within its post-split no-merge window. */
    private boolean isOnSplitCooldown(Mob mob) {
        Long until = noMergeUntil.get(mob.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            noMergeUntil.remove(mob.getUniqueId()); // expired; clean up
            return false;
        }
        return true;
    }

    /** @return true if both mobs are the same age group (both adult or both baby). */
    private boolean sameAgeGroup(Mob a, Mob b) {
        boolean aBaby = a instanceof org.bukkit.entity.Ageable ag && !ag.isAdult();
        boolean bBaby = b instanceof org.bukkit.entity.Ageable bg && !bg.isAdult();
        return aBaby == bBaby;
    }

    /**
     * @return true if two same-type mobs share the visible state that stacking
     *         should not blur together. This stops a sheared sheep from merging
     *         back into a woolly stack (which would look like shearing spread to
     *         all of them), keeps different wool colours separate, etc.
     */
    private boolean sameState(Mob a, Mob b) {
        // Sheep: shear state + wool colour must match.
        if (a instanceof org.bukkit.entity.Sheep sa && b instanceof org.bukkit.entity.Sheep sb) {
            if (sa.isSheared() != sb.isSheared()) return false;
            if (sa.getColor() != sb.getColor()) return false;
        }
        // Tameable (wolf/cat/parrot): don't merge tamed with wild.
        if (a instanceof Tameable ta && b instanceof Tameable tb) {
            if (ta.isTamed() != tb.isTamed()) return false;
        }
        // Mooshroom variant (red vs brown) must match.
        if (a instanceof org.bukkit.entity.MushroomCow ma
                && b instanceof org.bukkit.entity.MushroomCow mb) {
            if (ma.getVariant() != mb.getVariant()) return false;
        }
        return true;
    }

    /** @return true if the mob has any armour or hand item equipped. */
    private boolean hasEquipment(Mob mob) {
        var eq = mob.getEquipment();
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

    private int sizeOf(LivingEntity e) {
        Integer v = e.getPersistentDataContainer().get(sizeKey, PersistentDataType.INTEGER);
        return v == null ? 1 : Math.max(1, v);
    }

    private void setSize(LivingEntity e, int size) {
        e.getPersistentDataContainer().set(sizeKey, PersistentDataType.INTEGER, size);
    }

    private void refreshName(Mob mob) {
        int size = sizeOf(mob);
        if (size <= 1) {
            mob.customName(null);
            mob.setCustomNameVisible(false);
            return;
        }
        String typeName = prettyType(mob.getType());
        String raw = displayName.replace("{type}", typeName).replace("{amount}", String.valueOf(size));
        mob.customName(Text.mm(raw));
        mob.setCustomNameVisible(true);
    }

    private String prettyType(EntityType type) {
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private List<String> lower(List<String> in) {
        return in.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }
}
