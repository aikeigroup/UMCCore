# UMCCore

All-in-one **performance, optimization, and cross-platform UI core** for Minecraft servers.

Targets **Paper 26.2** · **Java 25** · built with **Maven**.

UMCCore replaces a stack of separate plugins (clearlag + stackers + a Discord status bot +
a menu plugin) with one structured, documented, permission-safe core.

---

## Features

- **Performance monitor** — live TPS/MSPT/RAM/entity tracking with optional auto-optimization
  triggers when the server lags.
- **Optimization modules** — mob stacker, item-drop stacker, ClearLagg, per-chunk mob limiter,
  and mob XP/drop control. Each one lowers entity count and MSPT.
- **Cross-platform UI** — menus rendered with the native **Dialog API** (works on Java *and*
  Bedrock via Geyser) with an automatic **chest-GUI fallback**. Built-in menus plus fully
  custom menus authored in YAML.
- **Fully-animated action bar** — every frame is redrawn and *every* segment change is animated
  (typewriter / slide / fade / wave), never a hard cut.
- **Discord status embed** — real-time server stats posted to Discord via DiscordSRV, edited in
  place on an interval, with dynamic color by TPS.
- **Integrations** — PlaceholderAPI (expansion + global resolver), Vault, LuckPerms, DiscordSRV,
  Geyser/Floodgate. All **soft**: the plugin runs fine if any are missing.
- **Full reload** — `/umccore reload` tears down every module, reloads all config, and re-enables
  cleanly. No restart, no leaked tasks/listeners.
- **Self auto-update** — checks GitHub Releases and can download the new jar to the update folder
  (see [docs/auto-update.md](docs/auto-update.md)). Built & released automatically via GitHub Actions.

Every feature can be disabled in `config.yml` under `modules.<name>: false`.

---

## Installation

1. Download `UMCCore-<version>.jar` (or build it — see below).
2. Drop it into your server's `plugins/` folder.
3. Start the server once to generate the config files.
4. (Optional) Install any of: PlaceholderAPI, Vault, LuckPerms, DiscordSRV, Floodgate/Geyser.
5. Edit the configs in `plugins/UMCCore/`, then run `/umccore reload`.

### Requirements

- Paper (or a fork) for **Minecraft 26.2**.
- Java 25+ (required by Minecraft/Paper 26.2).

---

## Build

```bash
mvn package
# -> target/UMCCore-<version>.jar
```

---

## Quick start

- `/umccore help` — list commands you have access to.
- `/umccore perf` — live performance summary.
- `/umccore menu main` — open the built-in main menu.
- `/umccore reload` — full reload after editing configs.

---

## Documentation

| Doc | What's inside |
|---|---|
| [docs/configuration.md](docs/configuration.md) | Every config file & option |
| [docs/commands.md](docs/commands.md) | Commands & permissions |
| [docs/menus.md](docs/menus.md) | Building custom menus (Dialog & GUI) |
| [docs/discord.md](docs/discord.md) | Discord status embed setup |
| [docs/placeholders.md](docs/placeholders.md) | UMCCore & supported PAPI placeholders |
| [docs/performance-tuning.md](docs/performance-tuning.md) | Tuning the optimization modules |
| [docs/auto-update.md](docs/auto-update.md) | Self auto-update & releasing |
| [docs/api.md](docs/api.md) | Developer API |

---

## License

Copyright © aikeigroup. All rights reserved (adjust as needed).
