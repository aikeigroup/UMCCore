# PRD — UMCCore

**Product Requirements Document**

| Field | Value |
|---|---|
| Nama Plugin | UMCCore |
| Base Package | `net.aikeigroup.umccore` |
| Platform Target | Paper / Purpur (Bukkit API) |
| Versi Minecraft | **26.2** (target utama, release stabil terbaru) — Dialog API tersedia native |
| Java Version | Java 25 (Minecraft/Paper 26.2 dikompilasi ke bytecode Java 25) |
| Build System | Gradle (Kotlin DSL) atau Maven |
| Tipe | All-in-one Performance + Optimization + UI Core |
| Tanggal | 2026-07-25 |
| Status | Draft v1.0 |

---

## 1. Ringkasan Produk (Overview)

UMCCore adalah plugin **core all-in-one** untuk server Minecraft yang berfokus pada:

1. **Peningkatan performa & penurunan MSPT** (Milliseconds Per Tick) agar server berjalan lancar.
2. **Sistem optimisasi entity/lag** — mob stacker, item stacker, ClearLagg, mob limiter per-chunk, XP mob drop control.
3. **Integrasi Discord** via DiscordSRV — status embed real-time yang di-edit secara berkala (interval), fully customizable.
4. **Sistem UI cross-platform** — menu modal yang aman untuk Java **dan** ramah Bedrock (Geyser) menggunakan **Dialog API** (fitur "display" baru MC 1.21.6+) dengan fallback ke chest-GUI.
5. **Action bar animatif** yang dapat dikustomisasi.
6. **Integrasi ekosistem** — PlaceholderAPI, LuckPerms/Vault, DiscordSRV, Geyser/Floodgate.

Tujuan utama: satu plugin yang menggantikan kombinasi (ClearLagg + StackMob + beberapa plugin GUI + bot status Discord) dengan arsitektur terstruktur, rapi, terdokumentasi, dan aman dari sisi permission.

### 1.1 Tujuan (Goals)

- Menurunkan MSPT dan menaikkan TPS pada server dengan populasi entity/pemain tinggi.
- Menyediakan tooling optimisasi yang bisa di-toggle & dikonfigurasi granular.
- Memberi admin sistem UI fleksibel yang bekerja identik di Java & Bedrock.
- Menyediakan visibilitas status server real-time di Discord.
- Konfigurasi yang self-documented (setiap opsi ada keterangannya).
- Full reload tanpa restart server.

### 1.2 Non-Goals (Di Luar Cakupan v1.0)

- Bukan anti-cheat.
- Bukan economy plugin penuh (hanya hook Vault economy untuk placeholder/menu).
- Bukan world-management / world-edit.
- Tidak melakukan patching NMS eksperimental yang berisiko crash (hanya optimisasi via API resmi + reflection aman).

---

## 2. Target Pengguna (Personas)

| Persona | Kebutuhan |
|---|---|
| **Owner Server** | Server lancar, low lag, tampilan status profesional di Discord. |
| **Admin / Staff** | Konfigurasi mudah, reload cepat, dokumentasi jelas, permission granular. |
| **Player Java** | Menu GUI responsif, action bar informatif. |
| **Player Bedrock (Geyser)** | Menu form native yang nyaman, bukan chest-GUI yang ribet di sentuh. |
| **Developer** | API/placeholder untuk integrasi, config yang mudah di-extend. |

---

## 3. Arsitektur & Struktur

### 3.1 Struktur Package

```
net.aikeigroup.umccore
├── UMCCore.java                    # Main class (JavaPlugin), lifecycle
├── api/                            # API publik untuk plugin lain
│   ├── UMCCoreAPI.java
│   └── events/                     # Custom events (StackMergeEvent, dll)
├── core/
│   ├── ModuleManager.java          # Registry & lifecycle semua modul
│   ├── Module.java                 # Interface tiap modul (enable/disable/reload)
│   ├── ConfigManager.java          # Loader config + migrasi versi
│   ├── MessageManager.java         # Pesan / i18n (messages.yml)
│   └── ReloadService.java          # Full reload orchestrator
├── modules/
│   ├── performance/                # MSPT/TPS optimisasi, entity activation
│   ├── mobstacker/
│   ├── itemstacker/
│   ├── clearlag/
│   ├── moblimiter/                 # Limiter per-chunk
│   ├── mobxp/                      # XP & drop control
│   ├── actionbar/                  # Action bar animatif
│   ├── discord/                    # DiscordSRV status embed
│   ├── staffchat/                  # StaffChat + DiscordSRV bidirectional sync
│   └── ui/                         # Menu / Dialog / GUI system
├── integrations/
│   ├── placeholderapi/             # Expansion + resolver
│   ├── vault/                      # Economy + permission
│   ├── luckperms/
│   ├── discordsrv/
│   └── geyser/                     # Floodgate/Geyser detection
├── command/
│   ├── UMCCommand.java             # /umccore root
│   ├── subcommands/                # reload, menu, stats, dll
│   └── TabCompleter.java
├── ui/
│   ├── MenuService.java            # Router: Dialog vs Chest-GUI
│   ├── dialog/                     # Dialog API (native cross-platform)
│   ├── chest/                      # Fallback chest-GUI (Java lama)
│   └── model/                      # MenuDefinition, Button, Action
└── util/
    ├── Text.java                   # MiniMessage / warna / adventure
    ├── Scheduler.java              # Folia-aware scheduler wrapper
    └── ReflectionUtil.java
```

### 3.2 Prinsip Desain

- **Modular**: Setiap fitur = satu `Module` dengan `onEnable/onDisable/onReload`. Bisa dinyalakan/dimatikan lewat config.
- **Full Reload**: `ReloadService` mematikan semua modul, reload seluruh config, lalu enable ulang — tanpa restart. Task/listener dibersihkan rapi (no leak).
- **Folia-aware** (opsional, defensive): wrapper scheduler agar aman bila di Folia; fallback Bukkit scheduler.
- **Fail-safe integrations**: Semua hook eksternal (DiscordSRV, PAPI, Vault, LuckPerms, Geyser) opsional & di-detect saat runtime. Plugin tetap jalan bila salah satunya tidak ada.
- **Adventure API** untuk semua teks (MiniMessage) → warna & format konsisten.

---

## 4. Modul & Fitur Detail

### 4.1 Performance Core (MSPT / TPS)

**Tujuan:** menurunkan MSPT & menjaga TPS stabil.

Fitur:
- **Live monitoring**: MSPT, TPS (1m/5m/15m), memory, chunk loaded, entity count per world.
- **Entity activation range tuning** (rekomendasi/warning bila konfigurasi Paper tidak optimal — read-only advisor, tidak overwrite paper config kecuali diizinkan).
- **Auto-optimization triggers**: bila MSPT > threshold selama X detik → jalankan aksi (mis. clearlag ringan, kurangi spawn, notifikasi staff).
- **Redstone/hopper throttle** (opsional): batasi tick hopper transfer & redstone bila lag.
- **Async chunk-safe operations** untuk semua scan berat.
- **Report command**: `/umccore perf` menampilkan ringkasan.

Config granular: threshold MSPT, interval sampling, aksi otomatis on/off, per-world override.

### 4.2 Mob Stacker

- Menggabungkan mob sejenis dalam radius → 1 entity mewakili N.
- Konfigurasi: radius merge, max stack size, whitelist/blacklist tipe mob, per-world.
- Display name stack: `&e{type} &7x{amount}` (customizable, placeholder).
- Loot & XP multiplier saat stack di-kill (opsi: kill 1 = drop 1, atau kill all).
- Kompatibel dengan spawner (opsi stack dari spawner).
- Event API: `StackMergeEvent`, `StackKillEvent`.
- Perlindungan: named mob, tamed, boss di-exclude default.

### 4.3 Item Drop Stacker

- Menggabungkan item drop sejenis di ground → kurangi entity item.
- Konfigurasi: radius merge, interval scan, max stack, display hologram amount.
- Merge memperhitungkan metadata (enchant, name) agar tidak menggabungkan item berbeda.

### 4.4 ClearLagg

- Penghapusan entity terjadwal (interval) dengan **peringatan hitung mundur** (broadcast + action bar).
- Whitelist: entity dengan custom name, tamed, leashed, item bernilai, armor stand, vehicle, dll.
- Kategori target: dropped items, hostile mobs, projectiles, exp orbs, dll (per-kategori toggle).
- `/umccore clearlag` manual + counter jumlah entity dihapus.
- Opsi: hanya hapus di chunk tanpa pemain.

### 4.5 Mob Limiter (Per-Chunk / Per-Area)

- Batasi jumlah mob per chunk atau per radius.
- Saat spawn melebihi limit → cancel spawn (config: cancel natural / spawner / semua source).
- Limit terpisah: hostile, passive, ambient, total.
- Per-world override.
- Opsi soft-limit (kurangi spawn rate) vs hard-limit (cancel).

### 4.6 Mob XP & Drop Control

- Atur multiplier / override XP drop per tipe mob.
- Atur drop item (multiplier, tambah/kurang, custom drop table sederhana).
- Kondisi: hanya bila dibunuh player, per-world, per-spawn-reason (anti XP farm dari spawner bila diinginkan).

### 4.7 Action Bar Animatif

- Menampilkan action bar dengan **animasi** (frame-based, scrolling text, gradient bergerak, wave).
- Multi-line rotation (ganti pesan berkala).
- Support **PlaceholderAPI** di dalam frame.
- Prioritas & kondisi: tampilkan action bar tertentu berdasarkan permission/world/region.
- Toggle per-player (`/umccore actionbar toggle`).
- Config: daftar animasi bernama, kecepatan (tick per frame), tipe animasi.

Contoh tipe animasi: `SCROLL`, `TYPEWRITER`, `GRADIENT_SHIFT`, `RAINBOW`, `FRAMES` (custom list).

### 4.8 Discord Status Embed (via DiscordSRV)

**Tujuan:** satu (atau beberapa) embed di channel Discord yang di-**edit** berkala menampilkan status server real-time.

- Menggunakan **DiscordSRV JDA** untuk kirim & edit message (bukan spam pesan baru — edit message yang sama).
- Interval update configurable (mis. tiap 30 detik).
- Isi embed fully customizable via config: title, description, fields, footer, color, thumbnail, author.
- **Placeholder** di dalam embed: `{tps}`, `{mspt}`, `{online}`, `{max}`, `{ram_used}`, `{ram_max}`, `{uptime}`, `{world_entities}`, + PlaceholderAPI (`%server_tps%`, dll).
- **Warna dinamis**: warna embed berubah berdasarkan kondisi (hijau bila TPS >18, kuning, merah).
- Multi-embed: beberapa embed berbeda di channel berbeda (mis. "Server Stats", "Player List").
- State persist: simpan message ID agar tetap edit embed yang sama setelah reload/restart.
- Fallback aman bila DiscordSRV tidak ada / channel invalid → log warning, modul disable.

### 4.8b Vote Log ke Discord (via NuVotifier + DiscordSRV)

**Tujuan:** setiap vote pemain (dari situs vote) diposting sebagai embed ke channel Discord.

- Mendengarkan **`VotifierEvent`** dari **NuVotifier/Votifier** (bukan dari plugin reward — plugin reward hanya konsumen event yang sama).
- Kirim **embed** via DiscordSRV JDA: `channel.sendMessageEmbeds(...)` (pesan baru per vote, bukan edit).
- Embed customizable: `color`, `title`, `description`, `fields`, `footer`, thumbnail (kepala pemain via mc-heads / URL statis), timestamp.
- **Placeholder**: `{player}`, `{service}` (nama situs vote), `{address}` (IP voter), + PlaceholderAPI.
- **Anti-duplikat**: pada setup proxy→backend, satu vote bisa memicu `VotifierEvent` lebih dari sekali. Kunci dedupe `username|serviceName` dengan window configurable (`dedupe-window-seconds`, default 60; `0` = off). Vote sah per situs ≤ 1×/hari sehingga window tak pernah menolak vote asli.
- **Catatan multi-backend**: cache dedupe in-memory & per-server. Jika modul aktif di banyak backend yang menerima broadcast vote yang sama, aktifkan votelog di **satu** server saja atau pakai channel berbeda.
- Soft-depend: bila Votifier atau DiscordSRV tidak ada → log warning, modul idle. Config `votelog.yml`, toggle `modules.votelog`.

### 4.9 UI System — Menu Modal Cross-Platform

**Inti fitur UI.** Tujuan: menu yang **aman di Java** dan **ramah Bedrock (Geyser)**.

#### Strategi Rendering (MenuService router) — **UI dipisah per-platform (v1.2.6):**

Definisi menu **dipisah per platform** (`menus/java/` & `menus/bedrock/`) karena Java & Bedrock
punya primitif UI sangat berbeda; auto-translate Dialog→Bedrock lewat Geyser tampil kacau. Router
mendeteksi platform pemain (Floodgate) lalu render **native** di masing-masing:

1. **Java → Dialog API** (native 26.2) — *primary*. Render sebagai layar Java asli. Fallback
   **chest-GUI** bila Dialog gagal / di-disable (`type: GUI`) / server lama. Mendukung body text,
   input (text/boolean/number/single-option), multi-action, paging.
2. **Bedrock → Cumulus form** (via Floodgate `sendForm`) — *native, bukan translasi*. `MENU`→
   SimpleForm (tombol besar sentuh + gambar tombol `image:` + paging), `NOTICE`→SimpleForm 1 tombol,
   `CONFIRM`→ModalForm (ya/tidak), menu ber-`inputs`→CustomForm (toggle/slider/dropdown/input).
3. **Fallback lintas-platform**: satu id cukup ada di salah satu folder; kalau platform pemain tak
   punya file id itu → pakai definisi platform lain (tak wajib duplikasi).

Skema YAML sama persis di kedua folder; renderer menyesuaikan tiap field ke platform. `type:`
(AUTO/DIALOG/GUI) hanya memengaruhi sisi Java. Kontras warna jadi tanggung jawab admin: folder
`bedrock/` pakai warna terang (panel form gelap), `java/` boleh warna gelap (chest terang).

#### Menu fleksibel (guide/tutorial/help):

Menu bukan sekadar hub tombol — bisa jadi halaman panduan penuh:

- **`kind`**: `MENU` (banyak tombol), `NOTICE` (info + 1 tombol), `CONFIRM` (Ya/Tidak).
- **`body`**: paragraf teks panjang (width diatur) + baris "gambar" berupa ikon item / kepala player bertekstur (avatar atau logo base64) / custom-model-data (resource pack). MC tak punya gambar arbitrer; head & item-icon adalah pendekatan native.
- **`inputs`**: form interaktif `TEXT`/`BOOLEAN`/`NUMBER`/`SINGLE_OPTION`; nilai masuk ke aksi sebagai `{input_<key>}`.
- **`pages`**: guide bertahap; tombol Prev/Next otomatis, tiap page override title/body/buttons.
- **Aksi**: `RUN_COMMAND`, `CONSOLE_COMMAND`, `OPEN_MENU[:page]`, `PAGE`, `BACK`, `TELEPORT`, `MESSAGE`, `BROADCAST`, `OPEN_URL`, `SOUND`, `CLOSE`.
- **PlaceholderAPI** aktif di semua field teks, per-pemain. Contoh lengkap: `menus/guide.yml`.

#### Menu Bawaan (Built-in):

| Menu | Isi |
|---|---|
| **Main Menu** | Hub navigasi ke semua menu lain. |
| **Stats** | TPS, MSPT, ping pemain, playtime, balance (Vault), rank (LuckPerms). |
| **Shortcut** | Tombol aksi cepat (jalankan command, teleport, dll). |
| **Data** | Info profil pemain (stats, achievement ringkas, dll). |
| **Warp List** | Daftar warp → klik untuk teleport (dengan permission per-warp). |

#### Custom Menu (Fleksibel):

- Admin bisa membuat menu sendiri via file YAML di folder `menus/`.
- Setiap menu = definisi: judul, tipe (dialog/gui), daftar tombol.
- Setiap tombol punya: label, icon (untuk chest-GUI), deskripsi, **action** & **permission**.
- **Action types**: `RUN_COMMAND` (as player/console), `OPEN_MENU`, `TELEPORT`, `MESSAGE`, `CLOSE`, `PLAYER_COMMAND`, `SOUND`, chaining multiple actions.
- Placeholder di label/deskripsi (PAPI).
- Kondisi tampil tombol berdasarkan permission (sembunyikan bila tak punya izin).

#### Contoh definisi menu (konsep):

```yaml
# menus/main.yml
title: "<gradient:#00c6ff:#0072ff>Main Menu</gradient>"
type: DIALOG            # DIALOG | GUI | AUTO
rows: 3                 # untuk GUI fallback
buttons:
  - id: stats
    label: "Server Stats"
    description: "Lihat performa server"
    icon: CLOCK          # material untuk GUI
    slot: 11
    permission: umccore.menu.stats
    actions:
      - "OPEN_MENU:stats"
  - id: warps
    label: "Warp List"
    icon: ENDER_PEARL
    slot: 13
    actions:
      - "OPEN_MENU:warps"
```

### 4.10 Integrasi

- **PlaceholderAPI**: 
  - UMCCore menyediakan expansion sendiri (`%umccore_tps%`, `%umccore_mspt%`, `%umccore_stack_count%`, dll).
  - UMCCore juga meng-*resolve* PAPI di semua teks (embed, action bar, menu, messages).
- **Vault**: economy (balance di menu/placeholder) + permission fallback bila LuckPerms tidak ada.
- **LuckPerms**: baca rank/prefix/meta untuk menu & placeholder; permission check.
- **DiscordSRV**: kirim/edit embed & integrasi StaffChat 2 arah (lihat 4.8 & 4.11).
- **Geyser/Floodgate**: deteksi pemain Bedrock untuk routing menu.

### 4.11 StaffChat
- Obrolan khusus staf in-game + dua arah DiscordSRV.
- Mode: **Toggle** (`/sc` tanpa argumen) dan **Direct** (`/sc <pesan>`).
- Notifikasi suara saat pesan staf diterima.
- Format custom MiniMessage + PAPI resolution.

---

## 5. Command & Permission

### 5.1 Struktur Command

Root: `/umccore` (alias: `/umc`), `/staffchat` (alias: `/sc`).

| Command | Deskripsi | Permission |
|---|---|---|
| `/staffchat [pesan]` (alias `/sc`) | Toggle mode staff chat / kirim pesan langsung ke staf & Discord | `umccore.staffchat.use` |
| `/umccore help` | Bantuan | `umccore.command.help` |
| `/umccore reload` | **Full reload** plugin (semua modul + config) | `umccore.command.reload` |
| `/umccore version` | Info versi & integrasi terdeteksi | `umccore.command.version` |
| `/umccore perf` | Ringkasan performa (TPS/MSPT/RAM) | `umccore.command.perf` |
| `/umccore clearlag [type]` | Trigger clearlag manual | `umccore.command.clearlag` |
| `/umccore stack info` | Info mob/item stacker | `umccore.command.stack` |
| `/umccore menu <nama> [player]` | Buka menu | `umccore.command.menu` + `umccore.menu.<nama>` |
| `/umccore actionbar toggle` | Toggle action bar diri sendiri | `umccore.command.actionbar` |
| `/umccore discord update` | Force update embed | `umccore.command.discord` |
| `/umccore staffchat [toggle\|<pesan>]` | Kelola / kirim staff chat | `umccore.staffchat.use` |
| `/umccore module <enable/disable/list> [nama]` | Kelola modul runtime | `umccore.command.module` |
| `/umccore debug` | Info diagnostik | `umccore.command.debug` |

### 5.2 Aturan Permission

- Semua permission ber-prefix `umccore.`.
- Default: command admin → `op`; command pemain (menu, actionbar toggle) → `true` atau granular.
- Node wildcard: `umccore.*` (admin penuh), `umccore.command.*`, `umccore.menu.*`.
- Menu & warp punya permission per-item (`umccore.menu.<nama>`, `umccore.warp.<nama>`).
- Registrasi permission lengkap di `plugin.yml` dengan `description` & `default`.
- **Aman**: tidak ada aksi destruktif tanpa permission; console-command action di menu hanya bisa dibuat admin (file config), bukan runtime oleh player.

### 5.3 Tab Completion

- Tab completion **kontekstual & sesuai permission** (hanya tampilkan sub-command yang pemain punya izin).
- Level 1: sub-command. Level 2+: argumen dinamis (nama menu dari folder `menus/`, nama modul, tipe clearlag, nama player online, dll).
- Tidak menampilkan saran untuk argumen yang tidak relevan.

---

## 6. Konfigurasi

### 6.1 File Config

```
UMCCore/
├── config.yml           # global: modul on/off, integrasi, setting umum
├── messages.yml         # semua pesan (MiniMessage), i18n-ready
├── performance.yml       # threshold MSPT/TPS, auto-optimization
├── stacker.yml          # mob & item stacker
├── clearlag.yml
├── limiter.yml          # mob limiter per-chunk
├── mobxp.yml            # xp & drop control
├── actionbar.yml        # daftar animasi action bar
├── discord.yml          # embed config (multi-embed)
├── votelog.yml          # embed vote log (NuVotifier + DiscordSRV, anti-dupe)
├── menus/               # definisi menu custom — DIPISAH PER PLATFORM (v1.2.6)
│   ├── java/            # ditampilkan ke pemain Java (Dialog / chest-GUI)
│   │   ├── main.yml  stats.yml  shortcut.yml  data.yml  warps.yml
│   │   ├── guide.yml  tagfakultas.yml
│   └── bedrock/         # ditampilkan ke pemain Bedrock (Cumulus form native)
│       ├── main.yml  stats.yml  shortcut.yml  data.yml  warps.yml
│       └── guide.yml  tagfakultas.yml
└── data/                # state persist (discord message id, dll)
```

### 6.2 Standar Konfigurasi

- **Setiap opsi wajib punya komentar keterangan** (apa fungsinya, nilai valid, default, dampak performa).
- Ada `config-version` untuk migrasi otomatis saat update.
- Nilai default aman (sane defaults) — plugin langsung jalan optimal tanpa konfigurasi.
- Dukungan per-world override di modul yang relevan.

Contoh gaya komentar:

```yaml
mob-stacker:
  # Aktifkan penggabungan mob sejenis untuk menurunkan jumlah entity & MSPT.
  enabled: true
  # Radius (block) pencarian mob sejenis untuk digabung. Lebih besar = lebih agresif,
  # tapi scan lebih berat. Rekomendasi: 4-8.
  merge-radius: 5
  # Jumlah maksimum mob dalam satu stack. 0 = tak terbatas (tidak disarankan).
  max-stack-size: 100
```

### 6.3 Full Reload

- `/umccore reload` melakukan **full reload**: unregister semua listener & task, reload seluruh file config, re-init semua modul & integrasi.
- Tidak boleh ada resource leak (task ganda, listener dobel).
- Melaporkan hasil: modul mana yang berhasil reload, error bila ada, waktu eksekusi.
- Aman dipanggil berkali-kali.

---

## 7. Dokumentasi

Wajib disertakan:

1. **README.md** — instalasi, dependency, quick start.
2. **`docs/`** — dokumentasi lengkap:
   - `docs/configuration.md` — penjelasan tiap file config & opsi.
   - `docs/commands.md` — daftar command & permission.
   - `docs/menus.md` — cara membuat menu custom (Dialog & GUI).
   - `docs/discord.md` — setup embed DiscordSRV.
   - `docs/placeholders.md` — daftar placeholder UMCCore + PAPI yang didukung.
   - `docs/api.md` — API untuk developer.
   - `docs/performance-tuning.md` — panduan tuning optimisasi.
3. Komentar in-code untuk API publik (Javadoc).
4. Wiki-ready (struktur markdown yang bisa dipindah ke GitHub Wiki).

---

## 8. Dependency & Integrasi

| Dependency | Tipe | Fungsi |
|---|---|---|
| Paper API 26.2 | required (provided) | Base API |
| PlaceholderAPI | soft | Placeholder resolve & expansion |
| Vault | soft | Economy & permission fallback |
| LuckPerms | soft | Rank/permission |
| DiscordSRV | soft | Status embed |
| Floodgate/Geyser | soft | Deteksi Bedrock, form fallback |
| Adventure/MiniMessage | shaded | Text formatting |

`plugin.yml`: `depend: []`, `softdepend: [PlaceholderAPI, Vault, LuckPerms, DiscordSRV, floodgate]`.

---

## 9. Non-Functional Requirements

- **Performa**: semua scan berat async atau di-batch; tidak menambah MSPT secara signifikan (target overhead < 0.5 ms/tick pada beban normal).
- **Stabilitas**: tidak crash bila dependency hilang; graceful degradation.
- **Keamanan**: validasi permission ketat; tidak ada command injection dari input player; console action hanya dari config admin.
- **Kompatibilitas**: Paper/Purpur; defensive terhadap Folia.
- **Maintainability**: modular, terdokumentasi, config self-explanatory.
- **Observability**: logging berjenjang (INFO/WARN/DEBUG), `/umccore debug`.

---

## 10. Roadmap / Fase Pengembangan

| Fase | Cakupan |
|---|---|
| **M1 — Core** | Main class, ModuleManager, ConfigManager, ReloadService, command root + tab completion, permission, messages. |
| **M2 — Performance & Optimisasi** | Performance monitor, ClearLagg, Mob Limiter, Mob/Item Stacker, Mob XP/Drop. |
| **M3 — UI System** | MenuService (Dialog + GUI fallback), menu bawaan (main/stats/shortcut/data/warp), custom menu loader. |
| **M4 — Integrasi & Presentasi** | PlaceholderAPI expansion, Vault, LuckPerms, Action bar animatif. |
| **M5 — Discord** | DiscordSRV status embed real-time (multi-embed, dynamic color, persist). |
| **M6 — Docs & Polish** | Dokumentasi lengkap, testing, optimisasi akhir, API publik. |

---

## 11. Acceptance Criteria (Ringkas)

- [ ] `/umccore reload` melakukan full reload tanpa restart, tanpa leak.
- [ ] Mob/item stacker menurunkan entity count & terlihat di monitor MSPT.
- [ ] ClearLagg + limiter + mobxp berfungsi dengan config granular & keterangan.
- [ ] Menu bawaan (main/stats/shortcut/data/warp) tampil native di Java & Bedrock (Geyser).
- [ ] Admin bisa membuat menu custom via YAML tanpa coding.
- [ ] Action bar animatif berjalan dengan PAPI.
- [ ] Embed Discord ter-edit real-time sesuai interval, warna dinamis, persist message ID.
- [ ] Placeholder UMCCore & PAPI resolve di semua teks.
- [ ] Permission granular & tab completion sesuai izin.
- [ ] Semua config ada keterangannya; dokumentasi lengkap tersedia.

---

## 12. Risiko & Catatan Teknis

| Risiko | Mitigasi |
|---|---|
| Dialog API di client/versi tertentu belum tersedia atau gagal render | Fallback chest-GUI + Floodgate form; router by-version & by-platform. |
| Edit embed DiscordSRV rate-limit | Interval minimum (mis. ≥ 15s), edit (bukan kirim baru), single JDA call. |
| Reflection NMS untuk optimisasi rentan breaking antar versi | Prioritas API resmi; reflection dibungkus & version-guarded. |
| Overhead scan stacker/limiter | Async, batch, radius terbatas, interval configurable. |
| Folia compatibility | Wrapper scheduler; region-aware; opsional. |
| Konflik dengan plugin optimisasi lain (ClearLagg asli, dll) | Deteksi & warning; modul bisa di-disable per-fitur. |

---

*Dokumen ini adalah PRD (spesifikasi kebutuhan). Implementasi kode akan mengikuti struktur & fase di atas.*
