# Self Auto-Update

UMCCore can check its own GitHub Releases for a newer version and (optionally) download the
new jar into the server's update folder, which the server applies on the next restart.

## How it works

1. On an interval, the `update` module queries
   `https://api.github.com/repos/<repository>/releases/latest`.
2. It compares the release tag (e.g. `v1.1.0`) to the running version.
3. If newer:
   - Admins (`umccore.command.update`) and console are notified.
   - If `auto-download` is on, the jar is downloaded to the update folder.
4. On restart, the server replaces the old jar with the downloaded one.

No JSON library is required — only the release tag and jar asset URL are read.

## Configuration (`config.yml`)

```yaml
modules:
  update: true            # enable/disable the whole feature

auto-update:
  repository: "aikeigroup/UMCCore"   # owner/name to check
  check-interval-hours: 6            # how often to check (min 1)
  auto-download: false               # true = fetch jar automatically
  notify-admins: true                # message online admins on availability
```

## Commands

- `/umccore update check` — check now and report.
- `/umccore update download` — download the latest jar to the update folder.

## Releasing new versions (maintainers)

The included GitHub Actions release workflow builds and publishes a release with the jar
attached whenever you push a tag:

```bash
# bump <version> in pom.xml, commit, then:
git tag v1.1.0
git push origin v1.1.0
```

The workflow (`.github/workflows/release.yml`) builds `UMCCore-<version>.jar` and attaches it to
the `v1.1.0` GitHub Release. The self-updater picks it up from there.

## Security notes

- Downloads come only from the configured repository's release assets.
- The jar is placed in the update folder; it is **not** hot-swapped at runtime — a restart is
  required, so you stay in control.
- Set `auto-download: false` if you prefer to review releases before fetching.
