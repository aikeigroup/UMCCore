# Configuration

All files live in `plugins/UMCCore/`. Every option is documented inline in the file itself;
this page is the map. Run `/umccore reload` after editing.

## Files

| File | Purpose |
|---|---|
| `config.yml` | Master module toggles, general settings, integrations, UI, auto-update |
| `messages.yml` | All user-facing messages (MiniMessage) |
| `performance.yml` | Monitor interval + auto-optimization triggers |
| `stacker.yml` | Mob stacker and item stacker |
| `clearlag.yml` | Scheduled entity cleanup |
| `limiter.yml` | Per-chunk / per-radius mob limits |
| `mobxp.yml` | Mob XP & drop control |
| `actionbar.yml` | Animated action bar segments & transitions |
| `discord.yml` | Discord status embeds |
| `menus/*.yml` | Menu definitions (see [menus.md](menus.md)) |
| `data/` | Persisted state (Discord message IDs) — do not edit |

## Enabling / disabling features

Each module has a toggle under `modules:` in `config.yml`:

```yaml
modules:
  performance: true
  mobstacker: true
  itemstacker: true
  clearlag: true
  moblimiter: true
  mobxp: true
  actionbar: true
  discord: true
  ui: true
```

You can also toggle at runtime (until next reload) with
`/umccore module <enable|disable> <name>`.

## Config version & migration

`config.yml` carries a `config-version`. When you update the plugin, new keys from the packaged
defaults are merged into your existing files automatically, so you keep your values and gain
new options with their documentation.

## Per-world overrides

The stacker, limiter, and mobxp modules support `per-world:` sections to override values for
specific worlds. See the comments in each file for the exact shape.
