# Nadi Beta Notes

Dokumen ini merangkum kesiapan beta Nadi dan hal yang harus dipahami tester sebelum memakai aplikasi di skenario nyata.

---

## Ownership Note

Codex hanya bertanggung jawab pada build, unit test, dan smoke test di lingkungan test yang tersedia. Matrix vendor Android luas, iOS/Safari, signing produksi, feedback tester, dan testing akhir beta/public release ditangani owner proyek.

---

## Build Identity

Update setiap kali membuat build kandidat:

```text
Version name:
Version code:
Commit SHA:
Build type:
Tanggal build:
```

---

## What Works

Core flow yang sudah menjadi target beta:

- Android host membuat room lokal.
- Client bergabung lewat QR/URL browser.
- Same-Wi-Fi mode.
- Local-only Hotspot mode dengan fallback.
- Host membagikan file ke browser.
- Browser upload file ke host.
- Chat lokal host dan browser.
- Riwayat metadata transfer lokal.
- Regenerate link untuk menutup akses link lama.
- Diagnostics lokal dapat disalin.
- Foreground service menjaga room aktif.

---

## Tester Instructions

1. Install APK debug/beta.
2. Buka Nadi dan buat room.
3. Pilih mode jaringan sesuai kondisi:
   - Hotspot lokal jika tidak ada Wi-Fi stabil.
   - Satu Wi-Fi jika semua perangkat berada di jaringan yang sama.
4. Scan QR dari laptop atau HP lain.
5. Coba download satu file dari host.
6. Coba upload satu file dari browser.
7. Coba chat dua arah.
8. Tekan `Perbarui Link`, lalu pastikan browser lama tidak bisa akses lagi.
9. Salin diagnostics jika ada error.
10. Catat device, Android version, browser, mode jaringan, ukuran file, dan hasil.

---

## Known Issues

Known issues yang masih perlu dipantau:

- Local-only Hotspot dapat berbeda perilaku antar vendor Android.
- Beberapa Wi-Fi kampus/captive portal bisa membatasi akses antar perangkat.
- Foreground service bisa terkena kebijakan battery optimization vendor tertentu.
- Progress download browser belum sejelas progress upload.
- Resume transfer belum tersedia.
- WebSocket chat belum digunakan; chat masih memakai polling ringan.
- Release signing produksi belum dikonfigurasi karena keystore final belum dibuat.

---

## Feedback Format

```text
Nama tester:
Device host:
Android version:
Mode jaringan:
Browser client:
Ukuran file:
Langkah yang dicoba:
Hasil:
Error/copy diagnostics:
Screenshot/video:
```

---

## Beta Exit Criteria

Beta kecil untuk owner proyek dianggap cukup untuk lanjut polish release bila:

- Minimal 5 tester menyelesaikan create room, join, upload, download, dan chat.
- Minimal 3 perangkat/vendor Android diuji.
- Minimal 3 browser diuji.
- Tidak ada crash pada core flow.
- Known issue yang tersisa punya mitigasi atau copy yang jelas.
- QA checklist di [NADI_QA_RELEASE_CHECKLIST.md](NADI_QA_RELEASE_CHECKLIST.md) sudah terisi untuk build kandidat.
