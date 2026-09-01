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

Releases are **automatic**. Just bump the version in `pom.xml`, commit, and push to `main`:

```bash
# edit <version> in pom.xml, e.g. 1.0.0 -> 1.0.1
git commit -am "Release 1.0.1"
git push origin main
```

The release workflow (`.github/workflows/release.yml`) reads the version from `pom.xml` on every
push to `main`. If no release exists for that version yet, it builds `UMCCore-<version>.jar`,
creates the tag `v<version>`, and publishes a GitHub Release with the jar attached. If the
version is unchanged, it does nothing (no duplicate releases). The self-updater picks it up from
there.

You can still trigger a manual release from the Actions tab (workflow_dispatch), optionally
passing an explicit tag.

## Security notes

- Downloads come only from the configured repository's release assets.
- The jar is placed in the update folder; it is **not** hot-swapped at runtime — a restart is
  required, so you stay in control.
- Set `auto-download: false` if you prefer to review releases before fetching.
