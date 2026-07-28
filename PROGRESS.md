# UMCCore — Progress Tracker

> Status pengerjaan per-milestone & per-fitur. **Selalu update file ini setiap ada
> perubahan.** Legend: ✅ selesai · 🚧 sedang dikerjakan · ⬜ belum · ⚠️ butuh perhatian.

**Last updated:** 2026-07-28 (v1.3.0 — modul lifecycle: deteksi stop/restart/crash + laporan JSON sebelum server mati)
**Target:** Paper 26.2 · Java 25 · Maven · package `net.aikeigroup.umccore`

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

**Catatan:** ~~Floodgate form path belum diperlukan~~ → **sudah diimplementasi di v1.2.6**:
Bedrock kini pakai form native Cumulus (SimpleForm/ModalForm/CustomForm) via Floodgate, terpisah
dari path Dialog Java. Lihat bagian v1.2.6 di atas.

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
| ✅ | GitHub Actions release (auto) | `.github/workflows/release.yml` — **push ke `main`** → baca versi `pom.xml`; kalau belum ada release-nya → build + tag `vX.Y.Z` + GitHub Release + attach jar. Skip kalau versi sama. Bisa juga manual (workflow_dispatch). |
| ✅ | Self auto-update module | Cek GitHub Releases, notify admin/console, `auto-download` opsional ke update folder (apply saat restart), `/umccore update [check\|download]`. Dependency-free (regex parse). Bisa di-disable via `modules.update`. |

---

## Lifecycle recorder: deteksi stop/restart/crash + laporan (v1.3.0)
- **Modul baru `lifecycle`** — mencatat server STOP/RESTART dan mendeteksi CRASH,
  lalu menulis laporan JSON (`plugins/UMCCore/lifecycle/shutdown-*.json` /
  `crash-*.json`) tepat sebelum server benar-benar mati. Toggle `modules.lifecycle`,
  config `lifecycle.yml` (semua opsi berkomentar).
- **4 sinyal, jujur soal batas fisik:**
  1. Listener `ServerCommandEvent` + `PlayerCommandPreprocessEvent` → catat pelaku
     (console / nama+UUID player) + command persis + **plugin pemicu** (via walk
     stack + `JavaPlugin.getProvidingPlugin`) untuk restart yang di-dispatch plugin.
  2. `onDisable` + `Bukkit.isStopping()` → titik tulis utama (world/player/stats masih
     hidup). **`/umccore reload` & toggle TIDAK dianggap shutdown** (isStopping()=false).
  3. **JVM shutdown hook** → jaring pengaman terakhir; jalan setelah semua plugin
     di-disable, dari snapshot memori. Laporan tetap tertulis **walau UMCCore
     di-disable pertama**. Idempotent (AtomicBoolean) — tak dobel dengan onDisable.
  4. **Heartbeat** file tiap 5s (dihapus saat stop bersih). Masih ada saat boot →
     sesi lalu mati tidak bersih → **CRASH/kill/OOM/panel-restart/listrik**,
     ditulis sebagai `crash-*.json` + rekonstruksi (perkiraan waktu, TPS, player).
- **Klasifikasi:** STOP / RESTART / EXTERNAL_OR_UNKNOWN (SIGTERM panel tanpa command)
  / UNCLEAN_SHUTDOWN (crash). Notif Discord opsional (soft DiscordSRV).
- **JSON writer sendiri** (`Json.java`, tanpa dependency) — Gson tidak dijamin ada.
- **Bersih di reload**: heartbeat task & listener dilepas oleh AbstractModule; hook
  dilepas saat reload (bukan stop). Build hijau, `mvn package` → v1.3.0.

## Rombak total UI Java (Dialog) — selaras dengan Bedrock (v1.2.9)
- **Masalah**: menu Java masih tampilan lama (stats/profil per-tombol polos, tak pakai
  placeholder baru), emoji (🏝🚀💰) jadi kotak putus di font Java, gaya beda dari Bedrock.
- **Design system Java** (didokumentasi di header `menus/java/main.yml`, dipakai konsisten di 7
  menu): palet selaras Bedrock (brand gradient `#00c6ff→#0072ff`, hijau `#33d17a`, biru `#4dd0e1`,
  emas `#ffb300`, ungu `#b388ff`, merah `#ef5350`, abu `#90a4ae`), memanfaatkan keunggulan Dialog:
  **MiniMessage penuh** (gradient/hex langsung) + **baris body ber-ikon** (item & kepala pemain).
  **Tanpa emoji** (diganti ikon item + simbol « ✔ ⚔).
- **stats.yml Java** kini panel LIVE: verdict kondisi + bar TPS/RAM + angka berwarna dinamis
  (memakai placeholder presentasi `%umccore_health/tps_colored/mspt_colored/ram_percent/*_bar%`
  yang ditambahkan di v1.2.8 — berlaku lintas platform).
- **data.yml Java** jadi kartu profil kaya (kepala pemain + rank/saldo/waktu-main/level/ping).
  Hub `main.yml` ditambah tombol **Profil** & **Tag Fakultas** + baris body (sapaan, kepala
  pemain, IP server). `shortcut`/`warps` disamakan (ikon, suara teleport, warp `crate` baru).
- **guide.yml & tagfakultas.yml Java** dirombak presentasinya (emoji dibuang, palet baru, tombol
  kembali) — **konten UNNESMC & seluruh logika/command tag identik** (unset→set permission,
  title, sound, `/tags`, `tag set <f>` delay). Slot chest-fallback tetap valid.
- **Verifikasi menyeluruh**: `mvn package` hijau; 14 YAML (java+bedrock) lolos parse; **44 material
  ikon divalidasi terhadap Paper 26.2 API** (semua ada); tak ada emoji tersisa; slot chest dalam
  rentang (main rows:6, lainnya rows:3). ChestMenuRenderer aman (slot invalid dilewati, material
  invalid → STONE). Belum diuji di server live (butuh Paper 26.2 + client).

## Rombak total UI Bedrock + placeholder presentasi (v1.2.8)
- **Masalah**: UI Bedrock lama tak konsisten — ikon tombol pakai avatar `mc-heads.net`
  (URL, lambat/pixelated/butuh internet), emoji render tak konsisten di Bedrock, gaya tiap
  menu beda-beda, informasi cramped.
- **Design system baru** (didokumentasi di header `menus/bedrock/main.yml`, dipakai konsisten
  di 7 menu): palet warna terang tetap (hijau `#69f0ae`/biru `#40c4ff`/emas `#ffd740`/ungu
  `#b388ff`/merah `#ff5252`/abu `#90a4ae`), judul gradient brand, body ber-divider `▬▬▬`,
  tombol `<warna><bold>NAMA</bold>` + baris deskripsi konteks, tombol kembali/tutup pakai
  simbol `«`/`✖` (bukan emoji). **Tanpa emoji** agar konsisten lintas device Bedrock.
- **Ikon tombol NATIVE** — semua ikon diganti ke texture client Bedrock (`image: "path:textures/
  items/..."`, mis. `nether_star`, `ender_pearl`, `emerald`, `name_tag`, `book_writable`, dan
  `textures/ui/refresh|confirm`). Muncul **instan & tajam tanpa internet**. Renderer sudah
  mendukung `path:` → `FormImage.Type.PATH` (tak ada perubahan Java untuk ikon). Sengaja
  hindari texture blok (chest/grass) karena tak tersedia sebagai form-image datar di Bedrock.
- **Fitur baru — panel STATS live berwarna**: `stats.yml` kini menampilkan verdict kondisi
  (Sehat/Waspada/Berat), bar 10-segmen untuk TPS & RAM, dan angka yang berubah warna sesuai
  kesehatan server. Ditopang placeholder baru di `UMCCoreExpansion` (additive, backward-compat):
  `%umccore_tps_colored%` `%umccore_mspt_colored%` `%umccore_ram_percent%` `%umccore_tps_bar%`
  `%umccore_ram_bar%` `%umccore_health%`.
- **Fitur baru — menu PROFIL kaya** (`data.yml`): kartu profil rapi (rank/saldo/waktu-main/
  level/ping) dengan divider & ikon; ditambahkan tombol **Profil** di hub `main.yml`
  (permission `umccore.menu.data`, sudah terdaftar di plugin.yml). Warp `crate` baru ditambah.
- Semua 7 YAML lolos parse; `mvn package` hijau → `UMCCore-1.2.8.jar`. **Belum diuji di device
  Bedrock asli** (butuh server Geyser live) — perubahan murni presentasi + placeholder additive,
  logika aksi/permission tag identik dengan v1.2.6.

## Fix dupe: mob stack ambil item drop (v1.2.7)
- **Bug**: mob yang sudah ter-stack (size > 1) memungut item drop di tanah → item jadi held
  equipment si entity representatif. Saat **seluruh stack mati** (mode bukan kill-one), drop
  dikali ×size (`event.getDrops()` ×N) — item pungutan ikut terkali → **duplikasi item**.
- **Kenapa lolos**: `protect-equipped` hanya mencegah mob yang *sudah* ber-equipment untuk
  *bergabung* ke stack; tak mencegah stack yang *sudah ada* memungut item setelahnya.
- **Fix**: listener baru `EntityPickupItemEvent` → batalkan pickup untuk mob ber-size > 1. Stack =
  1 entity mewakili N mob, jadi memungut 1 item lalu dikali N memang salah by design. Mob tunggal
  (size ≤ 1) tetap bisa memungut item seperti vanilla. Sumber dupe ditutup di titik pickup.

## UI dipisah per-platform + renderer Bedrock native (v1.2.6)
- **Masalah**: guide cross-platform lewat Dialog-diterjemahkan-Geyser tampil kacau di Bedrock
  (sempit, warna gelap tak terbaca). **Solusi**: menu dipisah total per platform + renderer Bedrock
  native (bukan translasi).
- **Folder menu dirombak** → `menus/java/` & `menus/bedrock/`. Menu id boleh beda isi antar platform;
  kalau satu sisi tak punya file id itu → fallback ke sisi lain (tak wajib duplikasi). Upgrade dari
  layout lama: `menus/*.yml` flat otomatis dipindah ke `menus/java/`. `MenuLoader.loadAll()` kini
  balikan `Map<Platform, Map<String, MenuDefinition>>`.
- **Renderer Bedrock baru** `ui/bedrock/BedrockFormRenderer` — pakai **Cumulus form via Floodgate**:
  `MENU`→SimpleForm (tombol besar + gambar + paging otomatis), `NOTICE`→SimpleForm 1 tombol,
  `CONFIRM`→ModalForm (ya/tidak), menu ber-`inputs`→CustomForm (label + toggle/slider/dropdown/input,
  tombol pertama jalan saat submit dengan `{input_*}` terisi). Callback dikembalikan ke main-thread.
- **Gambar tombol Bedrock** — field `image:` per-tombol: `head:<nama>` (avatar via mc-heads),
  `url:<png>`, atau `path:<texture pack>`. Head bernama polos otomatis jadi avatar.
- **Routing per-platform** di `MenuService` — deteksi Bedrock via `IntegrationManager.isBedrock(uuid)`
  (Floodgate `isFloodgatePlayer`, soft). Bedrock → form native; Java → Dialog/chest seperti biasa.
  `type:` (AUTO/DIALOG/GUI) kini hanya memengaruhi sisi Java.
- **Kontras warna** — Bedrock render di panel gelap → teks/tag warna gelap (`&4`,`&2`,dark_*) diganti
  hex terang (`#69f0ae`,`#40c4ff`,`#ffd740`,dll). `Text.legacy()` baru: MiniMessage→`§` legacy agar
  gradient/hex tetap tampil berwarna di form Bedrock. Contoh nyata: `bedrock/tagfakultas.yml`.
- **Menu default dibuat ulang untuk KEDUA platform** — main/guide/stats/shortcut/data/warps/tagfakultas
  di `java/` (sudah ada) + `bedrock/` (baru, dioptimalkan untuk sentuh + gambar + kontras). Model:
  `Platform` enum, `MenuButton.image` + `bedrockImage()`, `MenuDefinition.platform`.
- Docs `docs/menus.md` ditulis ulang (tabel platform, section Bedrock forms, panduan kontras).
  `config.yml` `ui:` didokumentasi ulang untuk split folder.

## Guide UNNESMC + command menu + first-join (v1.2.5)
- **guide.yml UNNESMC** — panduan multi-halaman berisi info nyata: identitas (UNNES Minecraft
  Community), gamemode (Skyblock Slimefun, Survival + Custom Enchant, cross-platform Java 25565 /
  Bedrock 19132), cara mulai (/rtp, /warp reso NPC, claim RedProtect /rp wand, angkat mob shift+klik),
  fitur & command (/jobs /vote /sf), ekonomi & gacha (/shop /cshop /bank /ah /bpass /rewards, crate
  /warp crate + /key), Rank Paket Plus Mahasiswa (Rp10rb/bln, benefit lengkap), peraturan, link
  dc.unnesmc.my.id & vote.unnesmc.my.id.
- **Command pembuka menu** — `ui.menu-commands` (mirip open_command DeluxeMenus): daftar
  "cmd,alias: menuId" → registrasi command runtime via CommandMap, dibersihkan saat reload.
  Default: `/guide` → guide, `/tagfakultas` (alias `/fakultas`) → tagfakultas.
- **Auto-open first join** — `ui.open-on-first-join` (default `guide`): buka menu otomatis untuk
  pemain baru (cek `hasPlayedBefore`). Tombol Panduan juga ditambah di `main.yml`.

## Menu super fleksibel: guide/tutorial/help (v1.2.4)
- **Body kaya** — menu kini punya `body:` (di atas tombol): paragraf teks panjang (width diatur),
  baris "gambar" dari ikon item, kepala player bertekstur (avatar / logo base64), + custom-model-data.
  Inti untuk guide/tutorial/help. Semua teks parse PlaceholderAPI per-pemain.
- **Multi-halaman** — `pages:` untuk guide bertahap; tombol Prev/Next otomatis. Tiap page override
  title/body/buttons.
- **Input form** — `inputs:` TEXT/BOOLEAN/NUMBER/SINGLE_OPTION (Dialog). Nilai masuk ke aksi sebagai
  token `{input_<key>}` (disubstitusi sebelum PAPI).
- **Kind dialog** — `kind: MENU|NOTICE|CONFIRM`. NOTICE = info + 1 tombol; CONFIRM = Ya/Tidak.
- **Aksi baru** — `PAGE:next|prev|<n>`, `BACK` (riwayat navigasi per-pemain), `BROADCAST`, `OPEN_URL`,
  `OPEN_MENU:id:page`. Head/custom-model-data juga dipakai chest-GUI fallback.
- Arsitektur: `MenuBody`/`MenuInput` records baru; `IconFactory` (head/base64/CMD) dipakai kedua
  renderer; `DialogMenuRenderer` render body+input+NOTICE/CONFIRM+paging; `MenuService` back-stack.
  Contoh lengkap `menus/guide.yml`. Docs `docs/menus.md` diperbarui.
- **Port DeluxeMenus** — dukungan agar config DM gampang dikonversi: aksi `TITLE` (title;subtitle;
  fade;stay;out), aksi ber-`<delay=TICKS>`, dan `filler` (rentang slot `0-8` dsb) untuk chest
  fallback. `Text.mm` kini auto-konversi kode warna legacy `&`/`&#rrggbb` → MiniMessage (teks tanpa
  `&` tetap MiniMessage murni), jadi teks DM/Essentials bisa ditempel apa adanya.
- **Contoh nyata**: `menus/tagfakultas.yml` — konversi penuh menu Pilih Tag Fakultas UNNESMC
  (9 fakultas, unset perms lain + set + `/tags` + title + `tag set <f>` delay 5s). Toggle lewat
  permission `umccore.menu.tagfakultas`.

## Vote Log ke Discord (v1.2.3)
- **Vote Log** — modul baru `votelog`: dengarkan `VotifierEvent` (NuVotifier/Votifier) lalu kirim
  **embed** ke Discord via DiscordSRV. Embed customizable (color/title/description/fields/footer,
  thumbnail kepala pemain via mc-heads, timestamp). Token `{player}` `{service}` `{address}` + PAPI.
  Soft-depend: idle bila Votifier/DiscordSRV tak ada. Config `votelog.yml`, toggle `modules.votelog`.
- **Anti-duplikat vote** — pada setup proxy→backend, satu vote bisa memicu `VotifierEvent` >1×.
  Dedupe key `username|serviceName` dengan window `dedupe-window-seconds` (default 60, `0`=off).
  Cache in-memory per-server; catatan multi-backend ada di komentar `votelog.yml`.
  Dependency: `com.github.NuVotifier.NuVotifier:nuvotifier-bukkit/api:2.7.2` (jitpack, provided).
  IntegrationManager kini punya `hasVotifier()` (deteksi "Votifier"/"NuVotifier").

## Fix: clearlag membuang umpan pancing (v1.2.2)
- **`FISHING_BOBBER` = Projectile** → ikut terhapus saat `remove.projectiles` (default true) waktu
  pemain sedang memancing. Ditambahkan ke `DEFAULT_BLACKLIST`. Bisa juga di-blacklist manual via
  `protect.blacklist-types: [fishing_bobber]` tanpa update jar.

## Throw mob + charge meter + config auto-write (v1.2.0)
- **Lempar mob** — saat menggendong: tap sneak = drop biasa; tahan sneak = power bar berosilasi
  (full→min→full) di action bar, lepas = lempar sesuai power saat itu. `throw` config (enabled,
  tap-max-ms, min/max-power, charge-cycle-ticks). Bar warna hijau→kuning→merah. require-sneak
  pickup default jadi false (sneak dipakai untuk drop/throw).
- **Config auto-write** — ConfigManager kini MENULIS key baru ke file .yml di disk (bukan cuma
  in-memory), lengkap dengan komentar, saat plugin update. Admin bisa lihat & edit opsi baru
  langsung di file. Log "Updated 'x.yml' with new default options."

## Spawner limit + stack-aware limiter + merge-toward-player (v1.1.2)
- **Limit spawner** — `spawner-limit` (limiter.yml): batasi jumlah mob di sekitar spawner (radius,
  max, count-all-types), dicek sebelum limit chunk. Lawan utama grinder farm menumpuk ratusan mob.
- **Limiter hitung ukuran stack** — `count-stack-size` (default true): stack 100 dihitung 100, bukan
  1, via PDC `stack_size`. Berlaku untuk limit chunk & spawner. Tanpa ini stack lolos limit.
- **Merge toward player** — `merge-toward-player` (default true): stack hasil merge dipindah ke posisi
  terdekat player. Fix grinder: dulu stack "hanyut" ke mob baru di spawner (atas/jauh) saat merge;
  sekarang tetap di titik kill dekat player.

## Preset agresif untuk server berat (v1.1.1)
- Dari analisis spark user (20 player, MSPT 50): penyebab #1 = **mob AI/pathfinding** (Mob,
  PathNavigation, Brain, GoalSelector, PathFinder) → persis yang stacker kurangi. Juga terdeteksi
  hopper/Slimefun cargo (di luar cakupan plugin) & clearlag dobel (fernsehheft).
- **Default dibuat lebih agresif**: mob-stacker merge-radius 5→8, max-stack 100→200, scan 40→60;
  item-stacker radius 4→6, max-stack 0→500, scan 40→60; limiter hostile 30→20, passive 20→15,
  ambient 10→8, total 50→35. Tetap ramah gameplay (mob dekat player tetap normal).
- **docs/performance-tuning.md**: tambah panduan diagnosa spark + preset entity-activation-range
  Paper + peringatan jangan pakai 2 clearlag.

## Limit notif + clearlag countdown menarik (v1.1.0)
- **Notif kena limit** — saat spawn di-block limiter untuk aksi PEMAIN (breeding, spawn egg,
  dispenser egg), pemain terdekat diberi tahu (`limiter.reached`, actionbar/chat) dengan
  scope+count/limit. Spawn natural tidak di-notif (biar tak spam). Config `limiter.yml → notify`.
- **Action bar countdown clearlag** — sekarang LIVE per-detik di action bar (bukan cuma warn-mark).
  `actionbar-countdown-seconds` (default 10). Di-repaint tiap tick supaya menang vs modul action
  bar. Semua pesan MiniMessage → support `<bold>`, `<gradient>`, `<#hex>`, `<rainbow>` dll.
  Default countdown pakai gradient + bold biar menarik.

## ClearLagg type blacklist (v1.0.9)
- **Blacklist tipe di clearlag** — `protect.blacklist-types` + `protect.use-default-blacklist`.
  Default melindungi villager/zombie_villager/trader, golem/allay, boss, mount, sniffer/shulker/
  creaking dari clearlag. Admin bisa tambah tipe sendiri.
- **Konfirmasi**: end_crystal, item_frame, painting, armor_stand, minecart, boat, dll masuk
  kategori OTHER → memang TIDAK PERNAH dihapus clearlag (by design). Yang dihapus hanya kategori
  yang di-toggle: dropped-items, hostile-mobs, passive-mobs, projectiles, experience-orbs.

## Pickup per-mob permission (v1.0.8)
- **Permission per jenis mob** — opsi `per-mob-permission` (pickup.yml). Kalau true, pemain juga
  butuh `umccore.pickup.mob.<type>` (mis. `umccore.pickup.mob.cow`) untuk mob spesifik. Wildcard
  `umccore.pickup.mob.*` untuk semua. Default false (cukup `umccore.pickup.use`). Semua node
  teregistrasi di plugin.yml → otomatis kebaca LuckPerms.

## State-match tambahan + Mob Pickup (v1.0.7)
- **State-match diperluas** — zombie_villager mid-cure (isConverting), collar color (wolf/cat),
  slime/magma size, varian cat/fox/axolotl/frog/wolf, chested pack animal (donkey/mule/llama
  bawa chest). zombie_villager tetap di blacklist (tak bisa stack) sesuai keputusan user.
- **Modul baru: Mob Pickup** (`modules.pickup`) — sneak+klik-kanan (tangan kosong) angkat mob ke
  atas kepala (jadi passenger), sneak+klik lagi (mob/udara) untuk turunkan di depan. Permission
  `umccore.pickup.use` + `umccore.pickup.hostile` untuk mob hostile. Config `pickup.yml`
  (require-sneak, require-empty-hand, allow-passive/hostile, max-health, blacklist). Drop otomatis
  saat quit. Boss/ravager/happy_ghast default di-blacklist.

## Sheep wool regrow (v1.0.6)
- **Bulu sheep tumbuh lagi** — saat sheep sheared di stack makan rumput (`SheepRegrowWoolEvent`),
  tanpa handler si representative jadi woolly mewakili N → cukur dapat wool xN (**eksploit
  duplikasi**) + state salah. Fix: kupas 1 sheep (jadi woolly single), sisanya tetap stack sheared.
  `match-state` mencegah re-merge. Jadi hanya 1 yang berbulu, cukur = 1 wool. Warna & umur disalin.

## Breeding & grow edge cases (v1.0.6)
- **Love mode** — mob yang sedang love mode (diberi item breeding, nunggu pasangan) tidak di-merge
  (`Animals.isLoveMode()` di `canStack`). Tanpa ini, merge menghapus status love → breeding gagal.
- **Bayi hasil breeding** — `EntityBreedEvent` → bayi baru dapat `markNoMerge` (cooldown) supaya
  tidak langsung tertelan stack bayi; pemain lihat bayinya, umur jalan sendiri.
- **Grow (baby→adult)** — `match-age` memisah stack baby dari adult; bayi solo tumbuh sendiri lalu
  merge ke stack adult saat dewasa. Keterbatasan inheren (stack baby tumbuh bareng) didokumentasikan.
- Alur breeding stack: feed → split-on-interact kupas 1 → love mode (terlindungi) → kawin → bayi
  (terlindungi) → induk cooldown → re-merge normal.

## Unstack tool + state-match (v1.0.6)
- **Unstack manual pakai tool** — klik-kanan mob stack sambil pegang item tertentu (default STICK)
  → seluruh stack terurai jadi mob individual (temporary; re-merge setelah cooldown). Config
  `mob-stacker.unstack-tool` (enabled/item/require-sneak/max-per-use), permission
  `umccore.stacker.unstack`. Event di-cancel supaya tak trigger interaksi vanilla.
- **State-match (fix sheared re-merge)** — sheep yang sudah dicukur RE-MERGE dengan stack woolly
  karena merge cuma cek tipe+umur. Tambah `match-state`: cek shear+warna sheep, tamed vs wild,
  varian mooshroom. Jadi mob yang state-nya beda tidak digabung → cukur 1 sheep tak menular ke stack.

## Interaksi mob stack (v1.0.5)
- **Interact mob stack cuma proses 1 mob** (mis. cukur sheep stack → 1 wool lalu stuck) — karena
  stack = 1 entity, vanilla cuma memproses si representative. Fix: **split-on-interact**. Saat
  `PlayerInteractEntityEvent`, kupas 1 mob dari stack (mob yang diklik jadi size 1, sisanya
  spawn jadi stack baru); vanilla lalu jalan normal di mob single itu. Berlaku universal:
  cukur/perah/warnai/breed/leash/nametag. Ada `split-cooldown-seconds` (default 5s) supaya mob
  yang baru dikupas tak langsung ter-merge lagi saat masih diinteraksi. Umur (baby/adult) ikut disalin.
- **happy_ghast** ditambahkan ke default blacklist (rideable, tak boleh stack).

## ClearLagg vs mob stack (v1.0.4)
- **Mob ter-stack tidak ter-clear** — mob stack punya custom name ("{type} x{amount}"), dan
  `protect.custom-named` melindungi semua entity bernama → stack tak pernah kehapus (sama seperti
  bug item stack sebelumnya). Fix: clearlag deteksi label stacker via PDC key `stack_size`
  (helper `isStackLabel`) dan mengecualikannya dari proteksi custom-name. Sekarang stack ikut
  terhapus sesuai toggle kategori (`remove.hostile-mobs`/`passive-mobs`), bukan diproteksi karena
  nama. Item & mob berlabel stacker sama-sama dikecualikan.

## Action bar fix (v1.0.3)
- **Bug warna** — `renderTransition` membungkus SEMUA hasil transisi dengan `perCharHue`,
  padahal `FADE`/`WAVE` sudah punya tag MiniMessage sendiri → tag-nya ke-escape → markup rusak
  (muncul saat default diubah ke FADE). Fix: tiap transisi kembalikan MiniMessage lengkap
  sendiri; typewriter/slide dibungkus gradient statis via `tint()`, fade/wave tidak dibungkus ulang.
- **Kurang jeda / terlalu dinamis** — tambah `easedFrame` (gerak ~2s lalu diam ~1.5s) untuk
  animasi warna, scroll diperlambat (tiap 3 frame), default segment jadi `NONE`.
- **Support hex RGB** — animation `NONE`/`STATIC` mempertahankan warna teks sendiri persis
  (`<#ff8800>`, `<gradient:#a:#b>`, `<rainbow>`). Fallback nilai animasi tak dikenal → `NONE`
  (dulu ke GRADIENT_SHIFT yang menimpa warna user). Contoh config pakai hex.

## Proteksi stack & clearlag (v1.0.2, dari analisis mob 26.2)
- **Default blacklist mob stacker** — villager/zombie_villager/wandering_trader (trade rusak),
  iron_golem/snow_golem/allay, boss (ender_dragon/wither/elder_guardian/warden), mount
  (horse/donkey/mule/llama/trader_llama/camel/strider/pig/skeleton_horse/zombie_horse),
  sniffer/creaking/shulker/ravager. Toggle `use-default-blacklist`.
- **Proteksi stacker tambahan**: mob naik/ditumpangi kendaraan (boat/minecart/jockey), punya
  passenger, ber-equipment (`protect-equipped`), persistent (`protect-persistent`, default
  false agar ternak hasil breeding tetap bisa stack), dan `match-age` (baby ≠ adult).
- **Proteksi clearlag tambahan**: `has-passengers`, `persistent` (termasuk ternak & name-tag),
  `equipped`. (Sebelumnya sudah ada custom-named/tamed/leashed/in-vehicle/armor-stands.)
  Mob di boat / diikat / punya penumpang kini aman dari clearlag.

## Bugfix (post-rilis, dari testing user)
- **Mob stacker: mob hilang, bukan ter-stack** — snapshot list di `scanAll` bisa berisi entity
  yang sudah di-remove (terserap stack lain); loop lalu merge stack hidup KE entity mati →
  dua-duanya lenyap. Fix: cek `isValid()` di scanAll + guard `stack.isValid()`/`other.isValid()`
  di dalam `tryMergeInto`.
- **ClearLagg tidak menghapus item drop ter-stack** — item stacker memberi custom name ("x64"),
  dan `protect.custom-named` melindungi semua entity ber-nama → item stack tak pernah kehapus.
  Fix: proteksi custom-name tidak berlaku untuk entity `Item` (label stacker ≠ penanda "keep").
- **Action bar kurang smooth & pergantian terlalu cepat** — frame 2→1 tick (20fps), kecepatan
  motion warna/scroll/wave/pulse diperlambat, `hold-frames` 60→160 (~8s/segment), transisi
  TYPEWRITER→FADE 12→20 frame (~1s) supaya kalem & tidak mengganggu.
- **Notif clearlag hanya < 1 menit** — warning hanya muncul saat sisa < 60 detik (mark 60+
  diabaikan); default `warn-at-seconds` jadi [30,10,5,3,2,1].
- **ClearLagg membuang umpan/pelampung pancing** — `FISHING_BOBBER` adalah `Projectile`, jadi
  masuk kategori PROJECTILE dan ikut terhapus saat pemain sedang memancing (`remove.projectiles`
  default true). Fix: `fishing_bobber` ditambahkan ke `DEFAULT_BLACKLIST`. Bisa juga di-blacklist
  manual via `protect.blacklist-types: [fishing_bobber]` tanpa update jar.

## Build/CI fixes (post-M6)
- **JDK 25 wajib** (lihat catatan teknis di bawah). pom + kedua workflow diset ke Java 25.
- **maven-shade-plugin dihapus** — semua dependency `provided` (tidak ada yang di-bundle), dan
  shade 3.6.0 tak bisa membaca bytecode Java 25. Artifact final = jar biasa dari jar plugin
  (tak ada lagi `original-*.jar`).

## Sisa / Catatan
- **Testing runtime di server Paper 26.2 asli** belum dilakukan (environment ini tak menjalankan server). Semua milestone lolos compile + package.
- Floodgate/Cumulus form path Bedrock **sudah diimplementasi** (v1.2.6) — UI dipisah per-platform
  (`menus/java` vs `menus/bedrock`), Bedrock render form native. Uji tampilan form di device Bedrock
  asli via Geyser belum dilakukan (butuh server live + client Bedrock).

---

## Catatan / Keputusan Teknis
- **Versi Minecraft 26.2** dikonfirmasi sebagai release stabil terbaru (skema year-based). Dialog API native.
- Build system **Maven** (Gradle tidak terpasang di environment).
- Java compile target **25** — paper-api 26.2 dikompilasi ke bytecode Java 25 (major version 69),
  jadi build **wajib** JDK 25+. CI (GitHub Actions) memakai temurin 25. (Sebelumnya keliru set 21 →
  CI gagal "wrong version 69.0, should be 65.0"; sudah diperbaiki.)
- Action bar: requirement khusus → **setiap pergantian/perubahan harus animatif**, tidak ada hard cut.
