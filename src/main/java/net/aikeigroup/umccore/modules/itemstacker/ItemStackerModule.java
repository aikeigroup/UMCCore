package net.aikeigroup.umccore.modules.itemstacker;

import net.aikeigroup.umccore.core.AbstractModule;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * Merges same-type ground item drops into fewer entities to reduce item-entity
 * load. Optionally shows a floating amount label.
 *
 * <p>Vanilla already merges dropped items up to a stack, but only within a tight
 * radius and up to the material's max stack size. This module widens the merge
 * radius and (when configured) lets a single item entity represent more than a
 * vanilla stack, drastically cutting entity counts on busy farms.</p>
 */
public final class ItemStackerModule extends AbstractModule {

    private int mergeRadius;
    private int scanInterval;
    private int maxStackSize;
    private boolean showLabel;
    private boolean respectMetadata;

    public ItemStackerModule() {
        super("itemstacker");
    }

    @Override
    protected String configName() {
        return "stacker";
    }

    @Override
    protected void enable() {
        if (!config().getBoolean("item-stacker.enabled", true)) {
            plugin.getLogger().info("Item stacker sub-toggle is off; module idle.");
            return;
        }
        mergeRadius = config().getInt("item-stacker.merge-radius", 4);
        scanInterval = Math.max(20, config().getInt("item-stacker.scan-interval-ticks", 40));
        maxStackSize = config().getInt("item-stacker.max-stack-size", 0);
        showLabel = config().getBoolean("item-stacker.show-amount-label", true);
        respectMetadata = config().getBoolean("item-stacker.respect-metadata", true);

        track(scheduler.runTimer(this::scanAll, scanInterval, scanInterval));
    }

    private void scanAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntitiesByClass(Item.class)) {
                if (e instanceof Item item && item.isValid()) {
                    mergeInto(item);
                }
            }
        }
    }

    private void mergeInto(Item stack) {
        ItemStack stackItem = stack.getItemStack();
        int cap = maxStackSize > 0 ? maxStackSize : stackItem.getMaxStackSize();
        double r = mergeRadius;

        for (Entity near : stack.getWorld().getNearbyEntities(stack.getLocation(), r, r, r)) {
            if (near == stack || !(near instanceof Item other) || !other.isValid()) {
                continue;
            }
            ItemStack otherItem = other.getItemStack();
            if (!matches(stackItem, otherItem)) {
                continue;
            }
            int current = stackItem.getAmount();
            if (current >= cap) {
                break;
            }
            int room = cap - current;
            int move = Math.min(room, otherItem.getAmount());
            stackItem.setAmount(current + move);
            if (move >= otherItem.getAmount()) {
                other.remove();
            } else {
                otherItem.setAmount(otherItem.getAmount() - move);
                other.setItemStack(otherItem);
                updateLabel(other);
            }
        }
        stack.setItemStack(stackItem);
        updateLabel(stack);
    }

    private boolean matches(ItemStack a, ItemStack b) {
        if (a.getType() != b.getType()) {
            return false;
        }
        if (!respectMetadata) {
            return true;
        }
        return a.isSimilar(b);
    }

    private void updateLabel(Item item) {
        if (!showLabel) {
            return;
        }
        int amount = item.getItemStack().getAmount();
        if (amount > 1) {
            item.customName(Text.mm("<gray>x<yellow>" + amount));
            item.setCustomNameVisible(true);
        } else {
            item.setCustomNameVisible(false);
        }
    }
}
