# Performance Tuning

UMCCore's optimization modules lower entity count and MSPT. This guide explains how to tune
them for your server. Watch the effect live with `/umccore perf`.

## Diagnose first (important)

High MSPT is not always entities. Install **spark** (`/spark profiler`) to see the real cause.
UMCCore helps most when the top consumers are mob AI (`Mob`, `PathNavigation`, `Brain`,
`GoalSelector`, `PathFinder`, `WalkNodeEvaluator`) — i.e. too many mobs. If the top consumers are
hoppers, redstone, chunk generation, or a specific plugin (e.g. Slimefun cargo networks), those
need fixing separately — UMCCore's stackers/limiter won't move the needle there.

Do **not** run two clearlag plugins at once. If you use UMCCore's, remove the other (or set
`modules.clearlag: false` to use the other).

## Aggressive preset for entity-heavy servers

The shipped defaults are already tuned for busy servers (merge-radius 8, larger stacks, tighter
mob limits). Combine with these Paper settings (paper-world-defaults.yml) — they don't hurt the
play experience because they only affect mobs far from players:

```yaml
entity-activation-range:
  animals: 8        # was 16 — distant animals stop ticking AI
  monsters: 24      # keep monsters near players active
  villagers: 16
  misc: 8
nerf-spawner-mobs: true      # spawner mobs use reduced AI
tick-inactive-villagers: false
```

Then in UMCCore, if you want it even more aggressive, lower `limits.*` in limiter.yml and raise
`merge-radius` in stacker.yml. Verify with `/umccore perf` before/after and `/umccore clearlag`:
if MSPT drops sharply right after a clearlag, entities were indeed the cause.

## The golden rule

Fewer entities = lower MSPT. The stackers and limiter attack that directly; clearlag is the
safety net; mobxp shapes farm output.

## Mob Stacker (`stacker.yml`)

- `merge-radius` — bigger merges more aggressively but scans heavier. 4–8 is a good range.
- `scan-interval-ticks` — how often the merge scan runs. Raise it (e.g. 60–100) on large
  servers to reduce scan cost; lower it for snappier merging.
- `kill-one-at-a-time` — `true` feels vanilla (one kill = one drop). `false` kills the whole
  stack and multiplies drops/XP.
- **Protected mobs** — a built-in default blacklist keeps villagers, traders, golems, bosses,
  mounts (horses/llamas/camels/etc.), and other special mobs from stacking. Named, tamed,
  leashed, ridden, passenger-carrying, and equipped mobs are also skipped. Set
  `use-default-blacklist: false` to manage it yourself; `protect-persistent` is `false` by
  default so bred farm animals still stack. Babies never merge with adults (`match-age`).

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
