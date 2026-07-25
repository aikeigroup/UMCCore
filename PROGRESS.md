# UMCCore — Progress Tracker

> Status pengerjaan per-milestone & per-fitur. **Selalu update file ini setiap ada
> perubahan.** Legend: ✅ selesai · 🚧 sedang dikerjakan · ⬜ belum · ⚠️ butuh perhatian.

**Last updated:** 2026-07-25 (M6 selesai — semua milestone inti selesai)
**Target:** Paper 26.2 · Java 21 · Maven · package `net.aikeigroup.umccore`

---

## Milestone M1 — Core Skeleton ✅ (build hijau)

| Status | Item | Catatan |
|---|---|---|
| ✅ | Setup Maven (`pom.xml`) | Paper 26.2 (`26.2.build.65-beta`), shade plugin, resource filtering versi. |
| ✅ | `Module` interface + `ModuleManager` | Lifecycle enable/disable/reload, isolasi error per-modul, toggle via config. |
| ✅ | `UMCCore` main class | Wiring service + command, register modul (kosong sampai M2). |
| ✅ | `ConfigManager` | Load/reload 9 file yml, copy default + merge key baru saat update. |
| ✅ | `MessageManager` | MiniMessage, prefix, token `{...}`. |
| ✅ | `ReloadService` | Full reload: disable → reload config → refresh → enable. Report hasil. |
| ✅ | `IntegrationManager` | Deteksi PAPI/Vault/LuckPerms/DiscordSRV/Floodgate (soft). |
| ✅ | Command `/umccore` + tab completion | Router, permission-aware, sub: help/reload/version. |
| ✅ | `plugin.yml` + permission tree | Wildcard + node granular, softdepend. |
| ✅ | Default config (semua ada keterangan) | config, messages, performance, stacker, clearlag, limiter, mobxp, actionbar, discord. |
| ✅ | Build verify | `mvn package` → `UMCCore-1.0.0.jar` OK. |

**Semua fitur bisa di-disable via `config.yml` → `modules.<nama>: false`.** ✔️

---

## Milestone M2 — Performance & Optimisasi ✅ (build hijau)

| Status | Item | Catatan |
|---|---|---|
| ✅ | `AbstractModule` + `Scheduler` | Base leak-free: track task/listener, auto-cleanup di onDisable, `runLaterTracked` self-removing. Scheduler defensif Folia. |
| ✅ | Performance monitor (MSPT/TPS/RAM) + auto-optimize | `ServerStats` snapshot atomic, sampling interval, trigger notify-staff / clearlag-light. |
| ✅ | Mob Stacker (`StackMergeEvent`, `StackKillEvent`) | Ukuran stack di PDC, merge scan, display name, kill-one/kill-all + multiplier loot/XP, proteksi named/tamed/leashed, black/whitelist. |
| ✅ | Item Drop Stacker | Merge item ground metadata-aware, label jumlah, interval scan. |
| ✅ | ClearLagg (countdown, whitelist, kategori) | Countdown broadcast+actionbar, kategori toggle, proteksi, playerless-chunks, `runCleanup(light)`. |
| ✅ | Mob Limiter per-chunk/radius | CreatureSpawnEvent cancel, kategori hostile/passive/ambient + total, hard/soft, per-world, filter spawn-source. |
| ✅ | Mob XP & Drop control | Multiplier global+per-type, require-player-kill, no-xp-from-spawners (tag PDC spawn-reason). |
| ✅ | Subcommand: `perf`, `clearlag`, `stack`, `module` | Tab completion + permission-aware; `module` bisa enable/disable runtime. |
| ✅ | Build verify | `mvn package` → jar 39 kelas. |

**Catatan uji:** kompilasi & packaging OK. Uji runtime di server Paper 26.2 asli belum dilakukan (butuh server) — direncanakan di M6 (testing beban).

## Milestone M3 — UI System ✅ (build hijau)

| Status | Item | Catatan |
|---|---|---|
| ✅ | Model: `MenuDefinition`, `MenuButton`, `MenuAction` | Action types: RUN_COMMAND, CONSOLE_COMMAND, OPEN_MENU, TELEPORT, MESSAGE, SOUND, CLOSE + chaining. |
| ✅ | `ActionExecutor` | Eksekusi aman: RUN_COMMAND as-player, CONSOLE_COMMAND admin-only (dari file), PAPI resolve arg. |
| ✅ | Dialog API renderer (native, 26.2) | `Dialog.create` + `multiAction` + `ActionButton.customClick` → callback jalan di main thread. Native Java & Bedrock (Geyser). |
| ✅ | Chest-GUI fallback | `MenuHolder` custom (slot→button map), listener cancel-click read-only. |
| ✅ | `MenuService` router | AUTO → Dialog (jika `ui.prefer-dialog` & didukung) else chest; per-menu override DIALOG/GUI; fallback aman bila Dialog gagal. |
| ✅ | `TextService` (PAPI resolver) | Resolve `%papi%` per-player lalu MiniMessage. Soft: aman tanpa PAPI. |
| ✅ | Custom menu loader (`menus/*.yml`) | Copy default saat pertama jalan; parse section & list style. |
| ✅ | Menu bawaan: main, stats, shortcut, data, warps | Semua ada keterangan; permission `umccore.menu.<id>`. |
| ✅ | Subcommand: `menu <name> [player]` | Tab completion sesuai izin; buka untuk orang lain butuh `umccore.command.menu.others`. |

**Catatan:** Floodgate form path (fallback Bedrock non-Dialog) belum diperlukan karena target 26.2 Dialog API sudah native lintas platform; disediakan sebagai opsi masa depan bila server < 1.21.6.

## Milestone M4 — Integrasi & Action Bar ✅ (build hijau)

| Status | Item | Catatan |
|---|---|---|
| ✅ | Action bar **full animasi** | `AnimationEngine`: per-char HSV motion. Segment anim: RAINBOW/GRADIENT_SHIFT/PULSE/SCROLL/WAVE. Transisi tiap ganti segment: TYPEWRITER/SLIDE/FADE/WAVE — **tidak ada hard cut**. Per-player permission-gated & toggle. |
| ✅ | PlaceholderAPI expansion (`%umccore_*%`) | tps, mspt, online, max_players, ram_used/max, uptime, entities, chunks. Register/unregister mengikuti keberadaan PAPI (reload-safe). |
| ✅ | Resolver global PAPI | `TextService` dipakai di menu, action bar, (nanti) Discord. Soft — aman tanpa PAPI. |
| ✅ | Vault / LuckPerms | Dipakai via placeholder di menu (`%vault_*%`, `%luckperms_*%`), flag deteksi soft di `IntegrationManager`. Wrapper khusus ditunda (belum diperlukan). |
| ✅ | Subcommand: `actionbar toggle` | Player-only, cek toggle-allowed. |

## Milestone M5 — Discord ✅ (build hijau)

| Status | Item | Catatan |
|---|---|---|
| ✅ | DiscordSRV hook (JDA edit message) | `DiscordSRV.getPlugin().getJda()`, edit `editMessageEmbedsById` (bukan kirim baru); fallback kirim bila message hilang. |
| ✅ | Status embed real-time, interval, multi-embed | Timer async (network off main-thread), interval min 15s, banyak embed per channel. |
| ✅ | Dynamic color by TPS | good/warn/bad threshold, hex configurable. |
| ✅ | Persist message ID (`data/discord-state.yml`) | Edit message yang sama lintas reload/restart. |
| ✅ | Token & PAPI | `{tps}{mspt}{online}{max}{ram_*}{uptime}{world_entities}{interval}` + `%server_*%`. |
| ✅ | Subcommand: `discord update` | Force refresh async. |
| ✅ | Soft-safe | Tanpa DiscordSRV → modul idle + warning, plugin tetap jalan. Dependency `provided` (tidak di-shade). |

## Milestone M6 — Docs & Polish ✅ (build hijau)

| Status | Item | Catatan |
|---|---|---|
| ✅ | `docs/` lengkap | configuration, commands, menus, discord, placeholders, api, performance-tuning. |
| ✅ | README | Fitur, instalasi, build, quick start, index docs. |
| ✅ | API publik (`api/UMCCoreAPI`) + events | `stats()`, `openMenu()`, `isModuleActive()`, `resolve()`; `StackMergeEvent`/`StackKillEvent` didokumentasikan. |
| ⬜ | Testing beban di server asli | Butuh server Paper 26.2 live — belum dijalankan. |

## Tambahan (di luar rencana awal) ✅

| Status | Item | Catatan |
|---|---|---|
| ✅ | GitHub Actions build | `.github/workflows/build.yml` — build tiap push/PR ke main, upload jar artifact. |
| ✅ | GitHub Actions release | `.github/workflows/release.yml` — tag `v*` → build + GitHub Release + attach jar. |
| ✅ | Self auto-update module | Cek GitHub Releases, notify admin/console, `auto-download` opsional ke update folder (apply saat restart), `/umccore update [check\|download]`. Dependency-free (regex parse). Bisa di-disable via `modules.update`. |

---

## Sisa / Catatan
- **Testing runtime di server Paper 26.2 asli** belum dilakukan (environment ini tak menjalankan server). Semua milestone lolos compile + package.
- Floodgate form path (fallback Bedrock non-Dialog) belum diimplementasi — tidak diperlukan di 26.2 karena Dialog API sudah native lintas platform. Disediakan sebagai opsi masa depan.

---

## Catatan / Keputusan Teknis
- **Versi Minecraft 26.2** dikonfirmasi sebagai release stabil terbaru (skema year-based). Dialog API native.
- Build system **Maven** (Gradle tidak terpasang di environment).
- Java compile target **21** (server berjalan di Java 21+; environment build Java 26).
- Action bar: requirement khusus → **setiap pergantian/perubahan harus animatif**, tidak ada hard cut.
