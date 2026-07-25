package net.aikeigroup.umccore.api.events;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when UMCCore is about to merge a mob into an existing stack.
 *
 * <p>Cancelling keeps the two mobs separate. Other plugins can use this to veto
 * stacking for special mobs they manage.</p>
 */
public final class StackMergeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity stack;   // the surviving stack entity
    private final LivingEntity merged;  // the entity being absorbed
    private final int amountBefore;
    private final int amountAdded;
    private boolean cancelled;

    public StackMergeEvent(LivingEntity stack, LivingEntity merged, int amountBefore, int amountAdded) {
        this.stack = stack;
        this.merged = merged;
        this.amountBefore = amountBefore;
        this.amountAdded = amountAdded;
    }

    /** @return the entity that survives and represents the stack. */
    public LivingEntity getStack() {
        return stack;
    }

    /** @return the entity being absorbed into the stack (will be removed). */
    public LivingEntity getMerged() {
        return merged;
    }

    /** @return the stack size before this merge. */
    public int getAmountBefore() {
        return amountBefore;
    }

    /** @return how many are being added by this merge. */
    public int getAmountAdded() {
        return amountAdded;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
