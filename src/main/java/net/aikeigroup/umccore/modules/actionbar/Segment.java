package net.aikeigroup.umccore.modules.actionbar;

/**
 * One rotation segment: base text, its continuous animation, and an optional
 * permission gating who sees it.
 *
 * @param id         segment id (for logging/config)
 * @param text       MiniMessage base text (may contain PAPI tokens)
 * @param animation  continuous animation while displayed
 * @param permission permission required to see it (empty = everyone)
 */
public record Segment(String id, String text, SegmentAnimation animation, String permission) {

    /** @return true if the segment is visible to the given permissible. */
    public boolean visibleTo(org.bukkit.permissions.Permissible who) {
        return permission == null || permission.isBlank() || who.hasPermission(permission);
    }
}
