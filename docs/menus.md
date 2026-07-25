# Menus

UMCCore menus render natively via the **Dialog API** (a real screen on Java, a native form on
Bedrock through Geyser) and automatically fall back to a **chest-GUI** when Dialog isn't
available. You author them once; the router picks the backend.

## Where menus live

`plugins/UMCCore/menus/<id>.yml`. The file name is the menu id. Five documented defaults ship
out of the box: `main`, `stats`, `shortcut`, `data`, `warps`.

## Structure

```yaml
title: "<gradient:#00c6ff:#0072ff><bold>Main Menu</bold></gradient>"  # MiniMessage
type: AUTO        # AUTO | DIALOG | GUI
rows: 3           # chest-GUI rows (1-6), used only for the GUI backend
buttons:
  stats:                          # button id
    label: "<green>Server Stats</green>"
    description:                  # tooltip (Dialog) / lore (GUI)
      - "<gray>View performance</gray>"
    icon: CLOCK                   # material, GUI only
    slot: 11                      # GUI slot; -1 = auto
    permission: umccore.menu.stats  # optional; hides button if missing
    actions:
      - "OPEN_MENU:stats"
```

## Render types

| `type` | Behaviour |
|---|---|
| `AUTO` | Use Dialog if `ui.prefer-dialog` is true and the server supports it, else chest-GUI |
| `DIALOG` | Force the native Dialog |
| `GUI` | Force the chest-GUI |

## Actions

Buttons run a list of actions in order. Format: `TYPE:argument` (argument optional).

| Action | Argument | Notes |
|---|---|---|
| `RUN_COMMAND` | command | Runs as the player (player permissions) |
| `CONSOLE_COMMAND` | command | Runs from console — **admin-authored only** |
| `OPEN_MENU` | menu id | Opens another menu |
| `TELEPORT` | `world,x,y,z[,yaw,pitch]` | Teleports the player |
| `MESSAGE` | MiniMessage text | Sends a chat message |
| `SOUND` | sound key | Plays a sound (e.g. `UI_BUTTON_CLICK`) |
| `CLOSE` | — | Closes the menu |

Placeholders (PlaceholderAPI) work in `title`, `label`, `description`, and action arguments,
resolved per-player.

## Permissions

- Opening a menu requires `umccore.menu.<id>`.
- Hide individual buttons by setting a `permission:` on them.
- Warps can gate each destination with a custom node like `umccore.warp.spawn`.

## Creating your own menu

1. Copy `menus/main.yml` to `menus/mymenu.yml`.
2. Edit the title and buttons.
3. Add a permission node if you want to restrict it (default `umccore.menu.mymenu` is `op`
   unless you grant it).
4. `/umccore reload`, then `/umccore menu mymenu`.
