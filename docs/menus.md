# Menus

UMCCore menus render natively via the **Dialog API** (a real screen on Java, a native form on
Bedrock through Geyser) and automatically fall back to a **chest-GUI** when Dialog isn't
available. You author them once; the router picks the backend.

Menus are flexible enough to be a plain button hub **or** a rich guide / tutorial / help page:
long body text, icon & player-head "images", multi-page navigation, interactive input forms, and
`NOTICE` / `CONFIRM` dialog shapes.

## Where menus live

`plugins/UMCCore/menus/<id>.yml`. The file name is the menu id. Documented defaults ship out of
the box: `main`, `stats`, `shortcut`, `data`, `warps`, and a full-featured `guide` you can copy.

## Dialog kind

```yaml
kind: MENU     # MENU (many buttons) | NOTICE (info + 1 button) | CONFIRM (yes/no)
```

## Body — text & "images" (guides/tutorials)

`body:` renders above the buttons. Minecraft dialogs can't show arbitrary images, so an "image"
is an **item icon** or a **textured player head** (an avatar, or a fixed logo head via a base64
texture). Each entry is one of:

```yaml
body:
  - "<gray>A plain text paragraph. PlaceholderAPI works: %player_name%.</gray>"
  - text: "<white>A paragraph with a set wrap width.</white>"
    width: 320
  - icon: KNOWLEDGE_BOOK              # item icon + caption ("image" row)
    text: "<aqua>Tip:</aqua> <gray>open with /guide</gray>"
  - head: "%player_name%"            # player name -> that player's avatar
    text: "<gray>...or paste a base64 texture for a fixed logo head.</gray>"
```

## Inputs — forms

`inputs:` adds interactive fields (Dialog backend only). A field's value is exposed to actions as
`{input_<key>}`.

```yaml
inputs:
  nickname:
    type: TEXT               # TEXT | BOOLEAN | NUMBER | SINGLE_OPTION
    label: "<white>Nickname</white>"
    initial: ""
    max-length: 16
  volume:
    type: NUMBER
    label: "Volume"
    min: 0
    max: 100
    step: 5
  team:
    type: SINGLE_OPTION
    label: "Team"
    options:
      - { id: red,  label: "<red>Red</red>" }
      - { id: blue, label: "<blue>Blue</blue>" }
buttons:
  submit:
    label: "Submit"
    actions:
      - "CONSOLE_COMMAND:nick {input_nickname}"   # tokens substituted before run
```

## Pages — multi-step guides

Add `pages:` for a flip-through guide. **Prev/Next buttons are added automatically.** Each page
overrides `title`/`body`/`buttons`; omit `buttons` to reuse the menu's.

```yaml
pages:
  - title: "Step 1"
    body: [ "<gray>First, do this...</gray>" ]
  - title: "Step 2"
    body: [ "<gray>Then, do that...</gray>" ]
```

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
| `OPEN_MENU` | `id` or `id:page` | Opens another menu (records history for `BACK`) |
| `PAGE` | `next` / `prev` / `first` / `last` / `<n>` | Flips a page of the current menu |
| `BACK` | — | Returns to the previously opened menu |
| `TELEPORT` | `world,x,y,z[,yaw,pitch]` | Teleports the player |
| `MESSAGE` | MiniMessage text | Sends a chat message |
| `BROADCAST` | MiniMessage text | Sends a message to everyone |
| `TITLE` | `title;subtitle[;fadeIn;stay;fadeOut]` | Shows a title (times in ticks) |
| `OPEN_URL` | url | Sends a clickable web link |
| `SOUND` | sound key | Plays a sound (e.g. `UI_BUTTON_CLICK`) |
| `CLOSE` | — | Closes the menu |

**Deferred actions.** Append `<delay=TICKS>` to any action to run it later (20 ticks = 1s), e.g.
`"RUN_COMMAND:tag set fipp<delay=100>"` runs 5s after the click — handy when an earlier `CLOSE`
must happen first. This matches the DeluxeMenus convention.

Placeholders (PlaceholderAPI) work in **every** text field — `title`, `body`, `label`,
`description`, input labels, and action arguments — resolved per-player. Input values are
substituted as `{input_<key>}` before PlaceholderAPI runs.

**Colour codes.** Text is MiniMessage (`<green>`, `<gradient:...>`, `<bold>`). Legacy `&` codes
(including `&#rrggbb` hex) are also accepted and auto-converted, so menus/messages copied from
DeluxeMenus/Essentials work unchanged.

## Filler (chest-GUI fallback)

Purely decorative panes for the chest fallback (dialogs have no slot grid):

```yaml
filler:
  icon: GRAY_STAINED_GLASS_PANE
  slots: [ "0-8", 13, "18-26" ]   # single slots and a-b ranges
```

## Porting a DeluxeMenus menu

| DeluxeMenus | UMCCore action |
|---|---|
| `[console] cmd` | `CONSOLE_COMMAND:cmd` |
| `[player] cmd` | `RUN_COMMAND:cmd` |
| `[message] text` | `MESSAGE:text` |
| `[title] a;b;in;stay;out` | `TITLE:a;b;in;stay;out` |
| `[sound] KEY` | `SOUND:KEY` |
| `[close]` | `CLOSE` |
| `<delay=100>` | `<delay=100>` (unchanged) |
| `left_click_commands` | `actions` |

See `menus/tagfakultas.yml` for a full real-world conversion (UNNESMC faculty tag picker).

## Button fields

```yaml
buttons:
  example:
    label: "<green>Click me</green>"
    description: [ "<gray>tooltip / lore line</gray>" ]
    icon: DIAMOND               # material (GUI + as fallback)
    head: "Notch"               # OR a player-head "image" (name or base64)
    custom-model-data: 1001     # OR a resource-pack model (turns the icon into any image)
    slot: 13                    # chest-GUI slot; -1 = auto
    width: 150                  # Dialog button width in pixels (optional)
    permission: umccore.menu.example   # hides the button if the player lacks it
    actions:
      - "OPEN_MENU:stats"
```

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
