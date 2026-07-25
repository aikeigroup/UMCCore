# Placeholders

## UMCCore expansion (`%umccore_*%`)

Registered automatically when **PlaceholderAPI** is installed. Values come from the performance
module's live snapshot.

| Placeholder | Meaning |
|---|---|
| `%umccore_tps%` | Ticks per second (0–20) |
| `%umccore_mspt%` | Mean milliseconds per tick |
| `%umccore_online%` | Online player count |
| `%umccore_max_players%` | Server max players |
| `%umccore_ram_used%` | Used heap (MB) |
| `%umccore_ram_max%` | Max heap (MB) |
| `%umccore_uptime%` | Human uptime (e.g. `3d 4h 12m`) |
| `%umccore_entities%` | Total entities across worlds |
| `%umccore_chunks%` | Total loaded chunks |

## Using other plugins' placeholders

UMCCore resolves **any** PlaceholderAPI placeholder inside its own text — menus, action bar
segments, and Discord embeds — per player. Examples used in the default menus:

- `%player_name%`, `%statistic_time_played%` (PAPI base expansions)
- `%luckperms_primary_group_name%` (LuckPerms expansion)
- `%vault_eco_balance_formatted%` (Vault expansion)

Install the matching PAPI expansion (`/papi ecloud download <name>`) for these to resolve.

## Discord embed tokens

Inside `discord.yml`, in addition to PAPI you can use the brace tokens documented in
[discord.md](discord.md): `{tps}`, `{mspt}`, `{online}`, `{max}`, `{ram_used}`, `{ram_max}`,
`{uptime}`, `{world_entities}`, `{interval}`.
