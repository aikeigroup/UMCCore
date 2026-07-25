# Commands & Permissions

Root command: `/umccore` (alias `/umc`). Tab completion only shows sub-commands and
arguments you have permission to use.

| Command | Description | Permission | Default |
|---|---|---|---|
| `/umccore help` | List available commands | `umccore.command.help` | everyone |
| `/umccore reload` | Full reload (modules + config) | `umccore.command.reload` | op |
| `/umccore version` | Version & detected integrations | `umccore.command.version` | op |
| `/umccore perf` | Live performance summary | `umccore.command.perf` | op |
| `/umccore clearlag` | Manual entity cleanup | `umccore.command.clearlag` | op |
| `/umccore stack info` | Mob/item stacker state | `umccore.command.stack` | op |
| `/umccore menu <name> [player]` | Open a menu | `umccore.command.menu` (+ `umccore.menu.<name>`) | everyone |
| `/umccore actionbar toggle` | Toggle your action bar | `umccore.command.actionbar` | everyone |
| `/umccore discord update` | Force Discord embed update | `umccore.command.discord` | op |
| `/umccore module <list\|enable\|disable> [name]` | Manage modules at runtime | `umccore.command.module` | op |
| `/umccore update [check\|download]` | Self-update check/download | `umccore.command.update` | op |

## Permission notes

- Wildcards: `umccore.*` (full admin), `umccore.command.*`, `umccore.menu.*`.
- Opening a menu for **another** player requires `umccore.command.menu.others`.
- Each built-in menu also checks `umccore.menu.<id>` (e.g. `umccore.menu.stats`).
- Per-warp permissions in the warp menu use `umccore.warp.<name>` (author-defined).
- `CONSOLE_COMMAND` menu actions can only be written in server-side menu files by admins;
  players cannot inject them at runtime.
