# UMCCore — Progress Tracker

> Status pengerjaan per-milestone & per-fitur. **Selalu update file ini setiap ada
> perubahan.** Legend: ✅ selesai · 🚧 sedang dikerjakan · ⬜ belum · ⚠️ butuh perhatian.

**Last updated:** 2026-07-25
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

## Milestone M2 — Performance & Optimisasi ⬜

| Status | Item |
|---|---|
| ⬜ | Performance monitor (MSPT/TPS/RAM) + auto-optimize triggers |
| ⬜ | `Scheduler` util (Folia-aware wrapper) |
| ⬜ | Mob Stacker (`StackMergeEvent`, `StackKillEvent`) |
| ⬜ | Item Drop Stacker |
| ⬜ | ClearLagg (countdown, whitelist, kategori) |
| ⬜ | Mob Limiter per-chunk/radius |
| ⬜ | Mob XP & Drop control |
| ⬜ | Subcommand: `perf`, `clearlag`, `stack`, `module` |

## Milestone M3 — UI System ⬜

| Status | Item |
|---|---|
| ⬜ | `MenuService` router (Dialog → Floodgate form → chest-GUI) |
| ⬜ | Dialog API renderer (native, 26.2) |
| ⬜ | Chest-GUI fallback |
| ⬜ | Model: `MenuDefinition`, `Button`, `Action` |
| ⬜ | Menu bawaan: main, stats, shortcut, data, warps |
| ⬜ | Custom menu loader (`menus/*.yml`) |
| ⬜ | Subcommand: `menu` |

## Milestone M4 — Integrasi & Action Bar ⬜

| Status | Item |
|---|---|
| ⬜ | Action bar **full animasi** (frame-based, transisi tiap ganti segment) |
| ⬜ | PlaceholderAPI expansion (`%umccore_*%`) + resolver global |
| ⬜ | Vault (economy) |
| ⬜ | LuckPerms (rank/meta) |
| ⬜ | Subcommand: `actionbar` |

## Milestone M5 — Discord ⬜

| Status | Item |
|---|---|
| ⬜ | DiscordSRV hook (JDA edit message) |
| ⬜ | Status embed real-time, interval, multi-embed |
| ⬜ | Dynamic color by TPS |
| ⬜ | Persist message ID (`data/discord-state.yml`) |
| ⬜ | Subcommand: `discord update` |

## Milestone M6 — Docs & Polish ⬜

| Status | Item |
|---|---|
| ⬜ | `docs/` lengkap (configuration, commands, menus, discord, placeholders, api, performance-tuning) |
| ⬜ | README |
| ⬜ | API publik (`api/`) + Javadoc |
| ⬜ | Testing beban + optimisasi akhir |

---

## Catatan / Keputusan Teknis
- **Versi Minecraft 26.2** dikonfirmasi sebagai release stabil terbaru (skema year-based). Dialog API native.
- Build system **Maven** (Gradle tidak terpasang di environment).
- Java compile target **21** (server berjalan di Java 21+; environment build Java 26).
- Action bar: requirement khusus → **setiap pergantian/perubahan harus animatif**, tidak ada hard cut.
