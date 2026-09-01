# CLAUDE.md — Panduan Kerja untuk UMCCore

> Baca file ini di awal setiap sesi. Ini mengatur cara mengerjakan project UMCCore.

## Alur Wajib Setiap Sesi

1. **Baca dulu** `PRD.md` (spesifikasi) dan `PROGRESS.md` (status terkini) sebelum
   mulai coding, agar tahu sudah sampai mana.
2. **Update `PROGRESS.md` setiap ada perubahan** — ubah status item (⬜ → 🚧 → ✅),
   tambah catatan bila perlu. Perbarui juga field `Last updated`.
3. Jika ada perubahan cakupan/fitur, **update `PRD.md`** agar tetap sinkron.
4. Gunakan sistem task (TaskCreate/TaskUpdate) untuk pekerjaan multi-langkah dalam sesi.

## Aturan Produk (jangan dilanggar)

- **Setiap fitur harus bisa di-disable via `config.yml`** (`modules.<nama>: false`).
  Setiap modul mengimplementasikan `Module` dan dicek toggle-nya oleh `ModuleManager`.
- **Action bar harus benar-benar full animasi**: setiap frame di-redraw, dan **setiap
  pergantian segment/perubahan teks juga animatif** (transisi seperti typewriter/slide/
  fade/wave) — tidak boleh ada hard cut instan.
- **Full reload**: `/umccore reload` mematikan semua modul, reload seluruh config, lalu
  enable ulang — tanpa restart, tanpa leak (task/listener wajib bersih di `onDisable`).
- **Setiap opsi config wajib ada komentar keterangannya** (fungsi, nilai valid, dampak).
- **Permission aman & tab completion sesuai izin** (hanya tampilkan yang boleh dipakai).
- Semua integrasi (PAPI, Vault, LuckPerms, DiscordSRV, Floodgate) bersifat **soft** —
  plugin tetap jalan bila salah satunya tidak ada.

## Teknis

- Target **Paper 26.2**, **Java 25** (paper-api 26.2 = bytecode Java 25/major 69), build **Maven**.
- Package root: `net.aikeigroup.umccore`.
- Teks pakai **MiniMessage** via `util/Text`.
- Build: `mvn package` → `target/UMCCore-<version>.jar`.
- Struktur & fase lengkap ada di `PRD.md`.

## Git

- Remote: `git@github.com:aikeigroup/UMCCore.git`.
- **Setiap perubahan agak besar (mis. selesai satu milestone/modul), commit & push.**
- Pesan commit ringkas & jelas (mis. `M1: core skeleton (modules, config, reload, command)`).

## Milestone (ringkas)

M1 Core ✅ · M2 Performance/Optimisasi ✅ · M3 UI System ✅ · M4 Integrasi & Action Bar ✅ ·
M5 Discord ✅ · M6 Docs & Polish ✅. Tambahan: GitHub Actions (build+release) ✅ & self
auto-update ✅. Detail per-item di `PROGRESS.md`.

Sisa: testing runtime di server Paper 26.2 asli (belum dijalankan — butuh server live).

## Rilis (otomatis)

- **Rilis otomatis dari `pom.xml`**: bump `<version>` → commit → push ke `main`. Workflow
  `.github/workflows/release.yml` baca versi dari pom; kalau belum ada release-nya, build +
  buat tag `vX.Y.Z` + GitHub Release + attach jar. Kalau versi sama → tidak rilis ulang.
- Jadi **jangan lupa naikkan versi di `pom.xml`** setiap mau rilis, kalau tidak tak ada rilis baru.
- Bisa juga rilis manual via tab Actions (workflow_dispatch).
- Self-updater membaca GitHub Releases repo `aikeigroup/UMCCore`.
