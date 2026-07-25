package net.aikeigroup.umccore.integrations.placeholderapi;

import net.aikeigroup.umccore.UMCCore;

/**
 * Manages registration/unregistration of the {@link UMCCoreExpansion}.
 *
 * <p>Kept separate from {@code IntegrationManager} so all direct references to
 * PlaceholderAPI classes live in this package and are only touched when PAPI is
 * actually present (avoiding {@link NoClassDefFoundError} on servers without
 * it).</p>
 */
public final class PlaceholderIntegration {

    private final UMCCore plugin;
    private UMCCoreExpansion expansion;

    public PlaceholderIntegration(UMCCore plugin) {
        this.plugin = plugin;
    }

    /** Registers the expansion (idempotent). */
    public void register() {
        if (expansion != null) {
            return;
        }
        try {
            expansion = new UMCCoreExpansion(plugin);
            expansion.register();
            plugin.getLogger().info("Registered PlaceholderAPI expansion 'umccore'.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            expansion = null;
        }
    }

    /** Unregisters the expansion if registered (called on disable/reload). */
    public void unregister() {
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {
                // PAPI may already be gone; nothing to do.
            }
            expansion = null;
        }
    }
}
