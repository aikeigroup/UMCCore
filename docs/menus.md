# Menus

UMCCore menus are **split per platform** so Java and Bedrock can look completely different — the
two clients have very different UI primitives, and a layout that reads well on a Java Dialog screen
feels cramped when auto-translated to a Bedrock touch form. Instead of translating, UMCCore renders
each platform natively:

| Platform | Backend | Notes |
|---|---|---|
| **Java** | native **Dialog API** (real screen), falls back to **chest-GUI** | primary path on Paper 26.2 |
| **Bedrock** (Geyser/Floodgate) | native **Cumulus form** (SimpleForm / ModalForm / CustomForm) | big tappable buttons, button pictures, sliders/toggles/dropdowns |

You author a menu for whichever platform(s) you want; the router picks the backend by detecting the
player's platform via Floodgate.

Menus are flexible enough to be a plain button hub **or** a rich guide / tutorial / help page:
long body text, icon & player-head "images", multi-page navigation, interactive input forms, and
`NOTICE` / `CONFIRM` shapes.

## Where menus live — the platform split

```
plugins/UMCCore/menus/
  java/      ← shown to Java players     (Dialog / chest-GUI)
  bedrock/   ← shown to Bedrock players  (native Cumulus forms)
```

The file name is the menu id. Documented defaults ship for **both** platforms out of the box:
`main`, `stats`, `shortcut`, `data`, `warps`, `guide`, and `tagfakultas`.

**Cross-platform fallback.** A menu id only needs to exist in **one** folder. If a player's
platform has no file for that id, UMCCore uses the other platform's copy — so you only duplicate a
menu when you actually want it to differ. Share the ones you're happy with; specialise the ones
(like guides) that benefit from a per-platform layout.

**Upgrading.** Servers coming from an older UMCCore had a flat `menus/*.yml`. On first start after
the update, those files are moved into `menus/java/` automatically and Bedrock defaults are written
alongside — nothing is lost.

> Same schema, both folders. Everything below (kind, body, inputs, pages, actions, buttons) works
> identically in `java/` and `bedrock/`; the renderer adapts each field to the platform.

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

`type:` only affects the **Java** backend. Bedrock players always get a native Cumulus form.

| `type` | Behaviour (Java players) |
|---|---|
| `AUTO` | Use Dialog if `ui.prefer-dialog` is true and the server supports it, else chest-GUI |
| `DIALOG` | Force the native Dialog |
| `GUI` | Force the chest-GUI |

## Bedrock forms (menus/bedrock/)

The Bedrock renderer maps your menu to the right Cumulus form automatically:

| Your menu | Bedrock form | Behaviour |
|---|---|---|
| `kind: MENU` | **SimpleForm** | `body` on top, one big tappable button per entry, auto Prev/Next paging |
| `kind: NOTICE` | **SimpleForm** | body + a single button |
| `kind: CONFIRM` | **ModalForm** | the first two buttons become the yes/no pair |
| any menu with `inputs:` | **CustomForm** | body becomes a label, inputs become fields; the **first button's** actions run on submit with `{input_*}` filled |

**Button pictures.** Bedrock buttons can show an image via the `image:` field:

```yaml
buttons:
  profile:
    label: "<green>My Profile</green>"
    image: "head:%player_name%"        # the player's face (works for any name)
  vote:
    label: "<gold>Vote</gold>"
    image: "url:https://example.com/vote.png"   # a PNG by URL
  shop:
    label: "<yellow>Shop</yellow>"
    image: "path:textures/items/emerald"        # a resource-pack texture path
```

`head:<name>` renders that player's avatar (via a head service), `url:<link>` uses any PNG, and
`path:<texture>` points at a Bedrock resource-pack texture. A plain player-name `head:` on a button
doubles as the avatar automatically.

**Inputs on Bedrock** map to real form widgets: `TEXT` → input field, `BOOLEAN` → toggle,
`NUMBER` → slider (uses `min`/`max`/`step`), `SINGLE_OPTION` → dropdown.

### Contrast — keep text readable

A Bedrock form draws on a **dark panel**, and a Java chest/Dialog draws on a light-ish inventory.
Dark colours that look fine on one are unreadable on the other. UMCCore converts your MiniMessage
(gradients and `<#rrggbb>` hex included) to the right colour format for each client, but **you**
pick colours that contrast with the background:

- In `menus/bedrock/`, avoid dark tones (`<dark_red>`, `<dark_green>`, `<black>`, `&4`, `&2`,
  `&0`). Prefer bright hex like `<#69f0ae>`, `<#40c4ff>`, `<#ffd740>`, `<#ff5252>`, and `<white>`.
- In `menus/java/`, the chest-GUI uses dark lore text on light slots, so mid/dark tones are fine.

The bundled `menus/bedrock/tagfakultas.yml` shows the rule in practice: the faculties that use
`&4`/`&2` (dark red/green) in the Java version are swapped for bright equivalents on Bedrock while
keeping the exact same tag-set logic.

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

See `menus/java/tagfakultas.yml` (and its Bedrock twin `menus/bedrock/tagfakultas.yml`) for a full
real-world conversion (UNNESMC faculty tag picker).

## Button fields

```yaml
buttons:
  example:
    label: "<green>Click me</green>"
    description: [ "<gray>tooltip / lore line</gray>" ]
    icon: DIAMOND               # Java chest-GUI: material icon
    head: "Notch"               # Java chest-GUI: player-head "image" (name or base64)
    custom-model-data: 1001     # Java chest-GUI: resource-pack model
    image: "head:Notch"         # Bedrock form: button picture (head:/url:/path:)
    slot: 13                    # chest-GUI slot; -1 = auto
    width: 150                  # Java Dialog button width in pixels (optional)
    permission: umccore.menu.example   # hides the button if the player lacks it
    actions:
      - "OPEN_MENU:stats"
```

Icon fields serve each renderer: `icon`/`head`/`custom-model-data` draw the Java chest-GUI item,
`image` draws the Bedrock form button picture, and `width` sizes a Java Dialog button. Set whichever
your platform folder needs; the others are simply ignored on the platform that doesn't use them.

## Opening menus with a command / on first join

Configured in `config.yml` under `ui:`:

```yaml
ui:
  menu-commands:            # register commands that open a menu (like DeluxeMenus open_command)
    guide: guide            # /guide opens the "guide" menu
    "tagfakultas,fakultas": tagfakultas   # /tagfakultas or /fakultas
  open-on-first-join: "guide"   # open this menu the first time a player joins (empty = off)
```

Both still honour the menu's `umccore.menu.<id>` permission. Commands are cleanly unregistered on
`/umccore reload`.

## Permissions

- Opening a menu requires `umccore.menu.<id>`.
- Hide individual buttons by setting a `permission:` on them.
- Warps can gate each destination with a custom node like `umccore.warp.spawn`.

## Creating your own menu

1. Copy `menus/java/main.yml` to `menus/java/mymenu.yml` (and/or `menus/bedrock/mymenu.yml`).
   If you only create one, the other platform reuses it via cross-platform fallback.
2. Edit the title and buttons. For a Bedrock file, prefer bright colours and add `image:` pictures.
3. Add a permission node if you want to restrict it (default `umccore.menu.mymenu` is `op`
   unless you grant it).
4. `/umccore reload`, then `/umccore menu mymenu`.

> Tip: to test the Bedrock layout without a Bedrock device, you can temporarily force it by
> reading `menus/bedrock/<id>.yml`. On a live server, join through Geyser to see the real form.
