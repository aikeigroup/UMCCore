# Developer API

UMCCore exposes a small, stable API plus custom events.

## Entry point

```java
import net.aikeigroup.umccore.api.UMCCoreAPI;

if (Bukkit.getPluginManager().isPluginEnabled("UMCCore")) {
    UMCCoreAPI api = UMCCoreAPI.get();

    // Live performance snapshot (safe from async too)
    double tps = api.stats().tps();
    double mspt = api.stats().mspt();

    // Open a menu (respects the menu's permission)
    api.openMenu(player, "main");

    // Check if a module is active
    boolean stacking = api.isModuleActive("mobstacker");

    // Resolve PAPI + MiniMessage text for a player
    String resolved = api.resolve(player, "TPS %umccore_tps%");
}
```

Add UMCCore as a `provided`/`softdepend` dependency; never bundle it.

## Custom events

Listen with a normal Bukkit `@EventHandler`.

### `StackMergeEvent` (cancellable)

Fired before two mobs merge into a stack.

```java
@EventHandler
public void onMerge(net.aikeigroup.umccore.api.events.StackMergeEvent e) {
    if (isSpecial(e.getMerged())) {
        e.setCancelled(true); // keep it separate
    }
}
```

Methods: `getStack()`, `getMerged()`, `getAmountBefore()`, `getAmountAdded()`.

### `StackKillEvent`

Fired when a stacked mob is killed (informational).

```java
@EventHandler
public void onStackKill(net.aikeigroup.umccore.api.events.StackKillEvent e) {
    int killed = e.getKilledAmount();
    int remaining = e.getRemainingAmount();
}
```

## Notes

- `ServerStats` is an immutable record; read it from any thread.
- Menu/action execution must happen on the main thread — the API handles that for `openMenu`.
- The plugin publishes the API in `onEnable`; call `UMCCoreAPI.get()` only after UMCCore is
  enabled (use `softdepend`/`loadbefore` ordering, or check `isPluginEnabled`).
