package net.aikeigroup.umccore.api.events;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a stacked mob is killed.
 *
 * <p>Reports how many of the stack died ({@code killedAmount}) and how many
 * remain ({@code remainingAmount}). Not cancellable — it is informational, so
 * loot/economy plugins can react to bulk kills.</p>
 */
public final class StackKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity entity;
    private final int killedAmount;
    private final int remainingAmount;

    public StackKillEvent(LivingEntity entity, int killedAmount, int remainingAmount) {
        this.entity = entity;
        this.killedAmount = killedAmount;
        this.remainingAmount = remainingAmount;
    }

    /** @return the stack entity that was killed (may be removed if none remain). */
    public LivingEntity getEntity() {
        return entity;
    }

    /** @return how many mobs of the stack died in this event. */
    public int getKilledAmount() {
        return killedAmount;
    }

    /** @return how many remain in the stack afterwards (0 if fully killed). */
    public int getRemainingAmount() {
        return remainingAmount;
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
