# Performance Tuning

UMCCore's optimization modules lower entity count and MSPT. This guide explains how to tune
them for your server. Watch the effect live with `/umccore perf`.

## The golden rule

Fewer entities = lower MSPT. The stackers and limiter attack that directly; clearlag is the
safety net; mobxp shapes farm output.

## Mob Stacker (`stacker.yml`)

- `merge-radius` — bigger merges more aggressively but scans heavier. 4–8 is a good range.
- `scan-interval-ticks` — how often the merge scan runs. Raise it (e.g. 60–100) on large
  servers to reduce scan cost; lower it for snappier merging.
- `kill-one-at-a-time` — `true` feels vanilla (one kill = one drop). `false` kills the whole
  stack and multiplies drops/XP.

## Item Stacker (`stacker.yml`)

- `merge-radius` / `scan-interval-ticks` — same trade-offs as the mob stacker.
- `max-stack-size: 0` keeps vanilla per-material caps; a positive value lets one item entity
  represent more, cutting entity counts hard on drop-heavy farms.

## Mob Limiter (`limiter.yml`)

- `mode: CHUNK` is cheapest (counts the spawn chunk). `RADIUS` is more precise but scans an area.
- Tune `limits.hostile/passive/ambient/total` to your server's headroom.
- `enforcement.type: HARD` cancels over-limit spawns; `SOFT` cancels a fraction
  (`soft-cancel-chance`).
- Disable `apply-to.spawner` if you want player grinders to bypass the limiter.

## ClearLagg (`clearlag.yml`)

- `interval-seconds` — how often the sweep runs.
- Enable only the categories you actually want removed; keep protections on
  (named/tamed/leashed/vehicle/armor-stands).
- `protect.playerless-chunks-only: true` avoids clearing entities right next to players.

## Mob XP & Drops (`mobxp.yml`)

- `xp.no-xp-from-spawners: true` kills spawner XP farms.
- `require-player-kill` gates XP/drops to genuine player kills.
- Per-type multipliers let you rebalance specific mobs.

## Auto-optimization (`performance.yml`)

- Turn on `auto-optimize.enabled` to react to sustained lag.
- `mspt-threshold` + `sustained-seconds` decide when it fires.
- Actions: `notify-staff` (message admins), `clearlag-light` (remove items + stray hostiles in
  playerless chunks).

## Sampling cost

`performance.yml → sample-interval-ticks` controls how often stats are gathered. 100 ticks
(5 s) is plenty; lowering it gives fresher numbers at a small cost.
