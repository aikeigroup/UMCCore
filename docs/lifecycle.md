# Lifecycle Recorder — deteksi stop / restart / crash

Modul `lifecycle` menulis satu file JSON tepat sebelum server benar-benar mati,
dan mendeteksi crash sesi sebelumnya saat server menyala lagi.

- Toggle: `modules.lifecycle` di `config.yml`
- Konfigurasi: `lifecycle.yml`
- Lokasi report: `plugins/UMCCore/lifecycle/`
  - `shutdown-<waktu>.json` — server mati (stop/restart/sinyal/api/watchdog)
  - `crash-<waktu>.json` — sesi sebelumnya mati tidak bersih (ditulis saat boot)
- Heartbeat internal: `plugins/UMCCore/data/lifecycle-heartbeat.yml`

## Cara kerja (ringkas, jujur soal batas fisik)

Tidak ada event Bukkit "server stopping/crashing", dan tidak ada kode yang bisa
jalan saat `kill -9` / segfault / mati listrik. Modul menggabungkan 4 sinyal:

1. **Listener command** — mencatat siapa menjalankan `/stop`//`/restart` (console /
   nama+UUID player) + plugin pemicu (bila di-dispatch plugin).
2. **`onDisable` + `Bukkit.isStopping()`** — titik tulis utama; `/umccore reload`
   tidak dianggap shutdown.
3. **JVM shutdown hook** — jaring pengaman terakhir; tetap menulis walau UMCCore
   di-disable pertama.
4. **Heartbeat** — file yang dihapus hanya saat stop bersih; masih ada di boot
   berikutnya → crash.

Analisa forensik (`forensics: true`) memeriksa thread JVM saat mati untuk menamai
penyebab spesifik: `WATCHDOG_HANG`, `EXTERNAL_SIGNAL`, `API_SHUTDOWN`, `COMMAND`.

---

## Contoh report — semua kondisi

### 1. STOP oleh console (`/stop` diketik di console)

```json
{
  "event": "SHUTDOWN",
  "classification": "STOP",
  "triggered-by": {
    "type": "console",
    "name": "CONSOLE",
    "command": "/stop",
    "initiating-plugin": null,
    "via": "command typed by console"
  },
  "cause": "COMMAND",
  "cause-confidence": "high",
  "cause-detail": "A stop/restart command was seen just before shutdown; see triggered-by.",
  "source": "onDisable (server stopping)",
  "stamped-at": "2026-07-28T18:02:11",
  "started-at": "2026-07-28T17:00:35",
  "uptime-seconds": 3696,
  "tps": "20.00",
  "mspt": "18.42",
  "ram-used-mb": 3120,
  "ram-max-mb": 6144,
  "loaded-chunks": 4210,
  "entities": 2103,
  "online-count": 0,
  "online-players": [],
  "evidence": {
    "killing-thread": "Server thread",
    "ran-in-shutdown-hook": false,
    "watchdog-thread-present": true,
    "watchdog-appears-firing": false,
    "shutdown-hook-threads": [],
    "server-thread-state": "RUNNABLE",
    "server-thread-stack": [
      "net.minecraft.server.MinecraftServer.stopServer(MinecraftServer.java:...)",
      "net.minecraft.server.dedicated.DedicatedServer.stopServer(...)"
    ]
  }
}
```

### 2. RESTART oleh player (`/restart` diketik pemain)

```json
{
  "event": "SHUTDOWN",
  "classification": "RESTART",
  "triggered-by": {
    "type": "player",
    "name": "Voxience (b4d49f5c-4268-3850-994b-8094f013e3c0)",
    "command": "/restart",
    "initiating-plugin": null,
    "via": "command typed by player"
  },
  "cause": "COMMAND",
  "cause-confidence": "high",
  "cause-detail": "A stop/restart command was seen just before shutdown; see triggered-by.",
  "source": "onDisable (server stopping)",
  "stamped-at": "2026-07-28T18:10:44",
  "started-at": "2026-07-28T17:00:35",
  "uptime-seconds": 4209,
  "tps": "19.98",
  "mspt": "21.03",
  "ram-used-mb": 3402,
  "ram-max-mb": 6144,
  "loaded-chunks": 4390,
  "entities": 2240,
  "online-count": 3,
  "online-players": [
    "Voxience (b4d49f5c-4268-3850-994b-8094f013e3c0)",
    "imlq (6b5fb44e-2158-3497-adad-dc45a463f313)",
    "sq3n (2ce8a72f-8de3-3264-88b2-cae72b7b0f93)"
  ]
}
```

### 3. RESTART di-dispatch oleh plugin (auto-restart / watchdog plugin)

Plugin memanggil `Bukkit.dispatchCommand(console, "restart")`. Nama plugin
ditemukan dengan menelusuri stack saat command berjalan.

```json
{
  "event": "SHUTDOWN",
  "classification": "RESTART",
  "triggered-by": {
    "type": "plugin",
    "name": "CONSOLE",
    "command": "/restart",
    "initiating-plugin": "UltimateAutoRestart",
    "via": "command dispatched by plugin 'UltimateAutoRestart'"
  },
  "cause": "COMMAND",
  "cause-confidence": "high",
  "cause-detail": "A stop/restart command was seen just before shutdown; see triggered-by.",
  "source": "onDisable (server stopping)",
  "stamped-at": "2026-07-28T22:00:00",
  "started-at": "2026-07-28T18:00:00",
  "uptime-seconds": 14400,
  "tps": "20.00",
  "mspt": "15.88",
  "ram-used-mb": 2980,
  "ram-max-mb": 6144,
  "loaded-chunks": 3980,
  "entities": 1875,
  "online-count": 0,
  "online-players": []
}
```

### 4. EXTERNAL_SIGNAL — SIGTERM dari panel / systemd / docker (tombol Stop/Restart host)

Tidak ada command; shutdown didorong oleh shutdown-hook JVM; watchdog tidak
firing. Inilah kasus JSON yang kamu kirim sebelumnya.

```json
{
  "event": "SHUTDOWN",
  "classification": "EXTERNAL_SIGNAL",
  "triggered-by": {
    "type": "os-signal",
    "name": "external SIGTERM (hosting panel Stop/Restart, scheduled restart, systemctl/docker stop, atau graceful kill)",
    "command": null,
    "via": "no stop/restart command seen within attribution window"
  },
  "cause": "EXTERNAL_SIGNAL",
  "cause-confidence": "high",
  "cause-detail": "Shutdown was driven by a JVM shutdown hook with no stop command and no watchdog — i.e. an OS signal (SIGTERM) from the host: the hosting panel's Stop/Restart button, a scheduled restart, systemctl stop, docker stop, or a graceful kill. NOT a crash (a hard crash/kill -9 leaves no report at all and is flagged UNCLEAN_SHUTDOWN next boot).",
  "source": "jvm-shutdown-hook (final safety net)",
  "stamped-at": "2026-07-28T17:45:22",
  "started-at": "2026-07-28T17:00:35",
  "uptime-seconds": 2686,
  "tps": "20.00",
  "mspt": "31.15",
  "ram-used-mb": 3350,
  "ram-max-mb": 6144,
  "loaded-chunks": 4639,
  "entities": 2585,
  "online-count": 12,
  "online-players": [
    "Voxience (b4d49f5c-4268-3850-994b-8094f013e3c0)",
    "imlq (6b5fb44e-2158-3497-adad-dc45a463f313)"
  ],
  "evidence": {
    "killing-thread": "UMCCore-lifecycle-shutdown",
    "ran-in-shutdown-hook": true,
    "os-signal-detected": true,
    "signal-handler-threads": ["SIGTERM handler"],
    "server-watchdog-present": false,
    "server-watchdog-firing": false,
    "shutdown-hook-threads": ["SIGTERM handler"],
    "server-thread-state": "RUNNABLE",
    "server-thread-stack": [
      "net.minecraft.world.entity.ai.Brain.tick(Brain.java:388)",
      "net.minecraft.server.level.ServerLevel.tickNonPassenger(...)"
    ]
  }
}
```

### 5. WATCHDOG_HANG — tick nyangkut, Paper paksa stop

Thread `Watchdog` aktif di jalur halt; stack `Server thread` yang beku
menunjukkan di mana macet (contoh di bawah: sebuah plugin memblokir main thread).

```json
{
  "event": "SHUTDOWN",
  "classification": "WATCHDOG_HANG",
  "triggered-by": {
    "type": "watchdog",
    "name": "Paper Watchdog (tick exceeded timeout -> force stop)",
    "command": null,
    "via": "no stop/restart command seen within attribution window"
  },
  "cause": "WATCHDOG_HANG",
  "cause-confidence": "high",
  "cause-detail": "Paper's watchdog force-killed the server: a single tick exceeded the timeout (a hang/deadlock/GC stall on the main thread). The captured server-thread-stack shows where it was frozen.",
  "source": "jvm-shutdown-hook (final safety net)",
  "stamped-at": "2026-07-28T19:30:07",
  "started-at": "2026-07-28T18:00:00",
  "uptime-seconds": 5407,
  "tps": "2.31",
  "mspt": "740.66",
  "ram-used-mb": 5980,
  "ram-max-mb": 6144,
  "loaded-chunks": 5210,
  "entities": 8123,
  "online-count": 20,
  "online-players": ["...20 pemain..."],
  "evidence": {
    "killing-thread": "Paper Watchdog Thread",
    "ran-in-shutdown-hook": false,
    "watchdog-thread-present": true,
    "watchdog-appears-firing": true,
    "shutdown-hook-threads": [],
    "os-signal-detected": false,
    "signal-handler-threads": [],
    "server-watchdog-present": true,
    "server-watchdog-firing": true,
    "server-watchdog-stack": [
      "org.bukkit.craftbukkit.util.ServerShutdownThread...",
      "org.spigotmc.WatchdogThread.run(WatchdogThread.java:...)"
    ],
    "server-thread-state": "BLOCKED",
    "server-thread-stack": [
      "com.example.LaggyPlugin.onEntityTick(LaggyPlugin.java:123)",
      "net.minecraft.world.level.Level.tickEntity(...)",
      "net.minecraft.server.MinecraftServer.tickServer(...)"
    ]
  }
}
```

> Perhatikan `tps: 2.31` & `mspt: 740` di heartbeat terakhir — bukti tambahan
> bahwa server memang sedang lag berat sebelum watchdog memaksa berhenti.

### 6. API_SHUTDOWN — plugin memanggil `Bukkit.shutdown()` langsung

Jalan di main thread, tanpa command, tanpa watchdog.

```json
{
  "event": "SHUTDOWN",
  "classification": "API_SHUTDOWN",
  "triggered-by": {
    "type": "plugin-api",
    "name": "a plugin calling Bukkit.shutdown() (no command)",
    "command": null,
    "via": "no stop/restart command seen within attribution window"
  },
  "cause": "API_OR_MAIN_THREAD",
  "cause-confidence": "medium",
  "cause-detail": "Shutdown ran on the main server thread without a command — most likely a plugin calling Bukkit.shutdown()/Server.shutdown() programmatically.",
  "source": "onDisable (server stopping)",
  "stamped-at": "2026-07-28T20:15:00",
  "started-at": "2026-07-28T20:00:00",
  "uptime-seconds": 900,
  "tps": "20.00",
  "mspt": "17.10",
  "ram-used-mb": 1980,
  "ram-max-mb": 6144,
  "loaded-chunks": 2100,
  "entities": 940,
  "online-count": 1,
  "online-players": ["MasZero (7d23a60f-2c26-3c49-ba74-800933e30c1d)"]
}
```

### 7. CRASH — mati tidak bersih (kill -9 / OOM / panel force-kill / listrik)

Tidak ada file `shutdown-*.json` saat kejadian (memang mustahil). Terdeteksi di
**boot berikutnya** dari heartbeat yang tertinggal, ditulis sebagai `crash-*.json`.

```json
{
  "event": "CRASH",
  "classification": "UNCLEAN_SHUTDOWN",
  "detail": "Previous session left a live heartbeat and never shut down cleanly. Cause is one of: crash, kill -9 / OOM-killer, host/panel hard restart, or power loss.",
  "detected-at": "2026-07-28T21:03:12",
  "approx-death-at": "2026-07-28T20:58:47",
  "approx-uptime-seconds": 3527,
  "last-tps": "19.94",
  "last-mspt": "22.40",
  "last-online-count": 8,
  "last-online-players": [
    "gugug (ea28da3c-35e9-391b-bcf0-f04f024787d7)",
    "Bubik111 (6539e668-5cb5-390d-9b60-87e7c85a9636)"
  ],
  "last-ram-used-mb": 6010,
  "last-ram-max-mb": 6144,
  "last-heartbeat-file": "lifecycle-heartbeat.yml"
}
```

> `approx-death-at` = detak heartbeat terakhir (presisi ≈ `heartbeat-interval-seconds`).
> `last-ram-used-mb` mepet ke `last-ram-max-mb` sering menjadi indikasi **OOM**.

---

## Peta klasifikasi → penyebab

| `classification` | `cause` | Arti | Real-time? |
|---|---|---|---|
| `STOP` / `RESTART` | `COMMAND` | Command console/player/plugin | ✅ |
| `EXTERNAL_SIGNAL` | `EXTERNAL_SIGNAL` | SIGTERM panel/systemd/docker/kill halus | ✅ (via hook) |
| `WATCHDOG_HANG` | `WATCHDOG_HANG` | Tick nyangkut → Paper paksa stop | ✅ (via hook) |
| `API_SHUTDOWN` | `API_OR_MAIN_THREAD` | Plugin panggil `Bukkit.shutdown()` | ✅ |
| `EXTERNAL_OR_UNKNOWN` | — | Forensik tidak yakin (jarang) | ✅ |
| `UNCLEAN_SHUTDOWN` (event `CRASH`) | — | Crash/kill -9/OOM/listrik | ❌ (boot berikutnya) |

## Cara membaca `evidence` (membuktikan OS vs server Minecraft)

Untuk memastikan sebuah shutdown datang dari **host/OS** dan bukan dari server
Minecraft, baca field ini:

| Field | Arti bila bernilai... |
|---|---|
| `os-signal-detected: true` | **Bukti terkuat**: ada thread handler sinyal OS (mis. `SIGTERM handler`). Proses disuruh berhenti oleh host — panel/systemd/docker. Ini BUKAN dari game. |
| `signal-handler-threads` | Nama thread sinyal yang tertangkap (mis. `["SIGTERM handler"]`). |
| `server-watchdog-firing: true` | Paper server-watchdog memaksa stop → **dari server Minecraft** (tick nyangkut). Lihat `server-thread-stack` yang beku. |
| `server-thread-state` | `RUNNABLE` + `tps` tinggi = server **sehat** saat mati (menguatkan penyebab eksternal). `BLOCKED`/`WAITING` + `tps` rendah = server **macet**. |
| `ran-in-shutdown-hook: true` | Ditulis oleh shutdown-hook JVM (mati via sinyal/API), bukan `/stop` di main-thread. |

> **Penting — bukan semua "watchdog" itu Paper Watchdog.** Library lain membawa
> thread bernama "watchdog" (mis. `okio.AsyncTimeout$Watchdog` di dalam DiscordSRV,
> untuk timeout jaringan). Sejak v1.3.2 modul HANYA menghitung watchdog Paper/Spigot
> asli (dikenali dari stack `org.spigotmc.WatchdogThread`/`ServerShutdownThread`),
> sehingga `server-watchdog-firing` tidak lagi bisa keliru oleh watchdog DiscordSRV.

**Contoh nyata (SIGTERM dari panel):** `os-signal-detected: true`,
`signal-handler-threads: ["SIGTERM handler"]`, `server-watchdog-firing: false`,
`server-thread-state: RUNNABLE`, `tps: 20.00` → **100% dari hosting/OS**, server
Minecraft sehat. Bukan crash, bukan lag, bukan command.

## Opsi konfigurasi terkait (`lifecycle.yml`)

- `forensics` — aktifkan analisa penyebab spesifik. Default `true`.
- `forensics-evidence` — sertakan bukti thread mentah. Default `true`.
- `heartbeat-interval-seconds` — presisi perkiraan waktu crash. Default `5`.
- `attribution-window-seconds` — jendela mengaitkan command ke shutdown. Default `30`.
- `keep-reports` — jumlah file terbaru yang disimpan. Default `50`.
- `discord.enabled` / `discord.channel` — notifikasi embed saat shutdown/crash.
```
