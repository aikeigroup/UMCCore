# Discord Status Embed

UMCCore posts a live server-status embed to Discord and **edits it in place** on an interval —
no message spam. Requires **DiscordSRV**.

## Prerequisites

1. Install and configure **DiscordSRV** (bot token, linked channels).
2. Make sure UMCCore's `modules.discord` is `true`.

## Configuration (`discord.yml`)

```yaml
update-interval-seconds: 30      # >= 15 (Discord rate limits)

embeds:
  - id: server-stats             # unique id; also the state key
    channel: "global"            # DiscordSRV channel name OR raw channel ID
    enabled: true
    dynamic-color: true          # color changes with TPS
    color-thresholds:
      good: 18.0                 # TPS >= good  -> good-color
      warn: 15.0                 # TPS >= warn  -> warn-color, else bad-color
    good-color: "#43b581"
    warn-color: "#faa61a"
    bad-color: "#f04747"
    color: "#5865F2"             # used when dynamic-color is false
    title: "Server Status"
    description: "Updated every {interval}s."
    thumbnail-url: ""
    fields:
      - name: "Players"
        value: "{online} / {max}"
        inline: true
      - name: "TPS"
        value: "{tps}"
        inline: true
    footer: "UMCCore"
    show-timestamp: true
```

## Tokens

Inside any text field you can use:

`{tps}` `{mspt}` `{online}` `{max}` `{ram_used}` `{ram_max}` `{uptime}` `{world_entities}`
`{interval}`

PlaceholderAPI server-scope tokens (`%server_*%`, etc.) also resolve.

## Multiple embeds

Add more entries under `embeds:` — each can target a different channel (e.g. one "Stats"
embed and one "Player Count" embed).

## Persistence

Message IDs are saved to `data/discord-state.yml` so the **same** message keeps being edited
across reloads and restarts. Don't edit that file by hand; delete an entry only if you want a
fresh message posted.

## Commands

- `/umccore discord update` — force an immediate refresh.

## Troubleshooting

- *"DiscordSRV not found; discord module idle"* — install DiscordSRV or set `modules.discord: false`.
- *"Discord channel not found"* — the `channel` value doesn't match a DiscordSRV channel name or
  a valid channel ID.
