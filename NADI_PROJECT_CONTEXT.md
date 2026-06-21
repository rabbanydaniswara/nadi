# Nadi Project Context

## Ringkasan Singkat

**Nadi** adalah aplikasi Android local-first untuk membantu mahasiswa tetap bisa berbagi file dan chat dalam satu ruangan saat internet atau sinyal seluler kampus sedang buruk.

Nadi tidak mencoba memperkuat sinyal. Nadi membuat jalur komunikasi lokal antar perangkat dekat, sehingga file dan pesan tidak perlu melewati WhatsApp, cloud, operator, atau jaringan kampus yang sedang padat.

## Problem Utama

Di lingkungan kampus, terutama saat banyak mahasiswa memakai jaringan yang sama, sering terjadi:

- File dari HP ke laptop lambat atau gagal saat dikirim lewat WhatsApp.
- Chat sederhana terlambat terkirim karena internet padat.
- Pengiriman materi, tugas, foto papan tulis, dokumen, atau project file menjadi terhambat.
- Dosen/ketua kelas sulit menyebarkan file cepat ketika LMS, WhatsApp Web, atau email lambat.
- Aktivitas kuliah bergantung pada internet walaupun perangkat sebenarnya berdekatan secara fisik.

## Solusi Produk

Nadi membuat satu perangkat Android menjadi **host lokal**.

Host:

- Menyalakan jaringan lokal, idealnya melalui Android Local-only Hotspot.
- Menjalankan local web server di dalam aplikasi.
- Menampilkan QR untuk bergabung.

Client:

- Laptop atau HP lain tersambung ke jaringan lokal host.
- Client membuka browser melalui URL/QR.
- Client dapat mengirim file, mengambil file, dan chat di room lokal.

Alur sederhana:

```text
HP Host Nadi
  -> Local-only Hotspot
  -> Local Web Server
  -> Laptop/HP client via browser
  -> file/chat lokal tanpa internet
```

## Brand

- **Nama:** Nadi
- **Makna:** Aliran hidup/komunikasi yang tetap berjalan saat jaringan utama melemah.
- **Tagline utama:** Tetap mengalir, meski sinyal melemah.
- **Positioning:** Jalur lokal untuk file dan chat saat internet tidak bisa diandalkan.

## Brand Personality

Nadi harus terasa:

- Modern.
- Minimalis.
- Premium.
- Tenang.
- Aman.
- Filosofis.
- Tidak terlalu teknis di permukaan.

Nadi bukan aplikasi "darurat" yang terlihat kasar. Nadi harus terasa seperti alat produktivitas yang elegan, cepat, dan dapat dipercaya.

## UI/UX Direction

Visual direction:

- Deep green sebagai warna utama.
- Graphite/dark ink untuk teks utama.
- Soft white/off-white untuk background.
- Teal/cyan tipis sebagai aksen aliran/koneksi.
- Bentuk visual: titik perangkat, garis nadi, gelombang, koneksi melingkar.
- Kartu dan panel rapi, radius moderat, tidak berlebihan.
- Tampilan pertama langsung usable, bukan landing page panjang.

UX principles:

- User harus langsung paham: "Buat Ruang" atau "Gabung".
- Hindari istilah teknis seperti IP address, port, socket, server, kecuali di advanced/debug view.
- QR join harus menjadi flow utama.
- Status koneksi harus jelas: siap, menunggu client, client terhubung, transfer berjalan, transfer selesai.
- Saat gagal, beri alasan manusiawi dan langkah lanjut.

## Target MVP

MVP berfokus pada **Android host + browser client**.

Fitur utama:

1. Host membuat local room.
2. Client join melalui QR atau URL lokal.
3. File transfer dari HP host ke laptop/client.
4. File transfer dari laptop/client ke HP host.
5. Chat room lokal sederhana.

Fitur pendukung:

- Daftar perangkat/client yang terhubung.
- Riwayat file lokal.
- PIN/session token untuk keamanan dasar.
- Status koneksi dan transfer.
- Web UI ringan untuk browser client.

## Tech Decision

Project Android:

- Language: Kotlin.
- Template: Empty Views Activity.
- UI: XML Views untuk MVP.
- Minimum SDK: API 26 Android 8.0.
- Build config: Kotlin DSL.
- Package: `com.danis.nadi`.

Alasan:

- Android Local-only Hotspot tersedia mulai API 26.
- Kotlin lebih enak untuk async/networking.
- Views/XML lebih stabil dan cepat untuk MVP teknis.
- Tidak perlu Firebase atau cloud pada MVP.

## Teknologi yang Perlu Dipertimbangkan

Core:

- `WifiManager.startLocalOnlyHotspot()` untuk host jaringan lokal.
- Embedded HTTP server di Android, misalnya NanoHTTPD atau Ktor embedded jika sesuai.
- WebSocket untuk chat realtime, atau polling HTTP untuk versi awal.
- QR generator untuk join.
- Android Storage Access Framework / file picker.
- Local persistence ringan untuk metadata file dan chat.

Possible later:

- Nearby Connections untuk Android-to-Android.
- Wi-Fi Direct untuk mode P2P khusus.
- Multi-host/relay.
- Resume transfer.
- Peer cache.
- Encryption tambahan.

## Batasan Penting

Nadi MVP bukan:

- Pengganti Wi-Fi kampus satu gedung.
- Mesh network besar.
- Penguat sinyal.
- Cloud storage.
- WhatsApp replacement penuh.

Batasan MVP:

- Semua pengguna perlu berada dekat dengan host.
- Jika host mati/keluar, room berhenti.
- Kapasitas bergantung perangkat host.
- iPhone lebih realistis sebagai client browser, bukan host.
- Beberapa device/vendor Android mungkin membatasi hotspot lokal.

## Prinsip Implementasi

- Offline-first.
- No paid cloud services.
- No Firebase untuk MVP.
- No account/login untuk MVP awal.
- Local network only.
- File tidak dikirim ke server eksternal.
- Chat/file session bersifat lokal dan ephemeral, kecuali user menyimpan riwayat.

## Prioritas Implementasi Pertama

1. Buat PRD dan struktur project.
2. Buat UI home: "Buat Ruang" dan "Gabung".
3. Implement local room state.
4. Implement local HTTP server dengan halaman browser sederhana.
5. Implement QR join.
6. Implement upload/download file via browser.
7. Implement chat room lokal.
8. Polish UI/UX dan error states.
9. Build/debug otomatis.

## Catatan Untuk Codex Thread Baru

Saat membuka project Nadi di thread Codex baru, mulai dengan:

1. Baca `NADI_PROJECT_CONTEXT.md`.
2. Baca `NADI_PRD.md`.
3. Audit project Android yang sudah dibuat.
4. Implementasi bertahap, jangan langsung semua fitur sekaligus.
5. Jalankan build/debug otomatis.

## Scope QA Untuk Codex

Untuk pekerjaan Codex, QA cukup dilakukan pada lingkungan pengetesan yang tersedia di workspace/Codex:

- Gunakan browser yang tersedia di aplikasi/lingkungan Codex.
- Tidak perlu mengejar dukungan iOS atau Safari.
- Tidak perlu melakukan matrix vendor Android luas; pengujian lintas vendor akan dilakukan manual oleh owner proyek.
- Signing produksi, feedback tester, dan testing akhir rilis juga ditangani oleh owner proyek.

Jika build, unit test, dan smoke test pada lingkungan test Codex sudah lulus untuk core flow Android host + browser client, hasil itu cukup sebagai gate pekerjaan Codex.

## Arah Pengembangan Lanjutan

Owner proyek ingin Nadi berkembang dari MVP file/chat sederhana menjadi ruang lokal yang lebih cocok untuk perkuliahan:

- Rencana UI/UX pemisahan fitur utama dicatat di `docs/NADI_UI_UX_SEPARATION_PLAN.md`.
- Arah UI berikutnya harus memisahkan menu aktif menjadi `Ruang`, `File`, `Chat`, `Peserta`, dan `Riwayat`, bukan satu dashboard panjang yang mencampur semua konteks.
- Chat dibuat lebih mirip WhatsApp: pesan berurutan, identitas pengirim jelas, dan mendukung lampiran gambar/dokumen di dalam chat.
- Gambar di chat harus bisa tampil sebagai preview; dokumen/ZIP/file lain tampil sebagai kartu file yang perlu didownload dulu sebelum dibuka.
- Identitas client harus lebih formal untuk konteks kelas, minimal `NIM` dan `Nama`.
- Identitas tidak boleh mudah berubah-ubah selama room berjalan agar pesan tidak anonim dan tidak mudah disangkal.
- Pengiriman file utama dipisahkan dari chat. Chat boleh punya lampiran, tetapi tetap ada area khusus untuk berbagi/mengumpulkan file room.
- File Room utama sebaiknya tersimpan di folder publik yang mudah ditemukan, default `Download/Nadi/<Room>/received/`, bukan hanya di `Android/data/...`.
- Download attachment chat disimpan terpisah, default `Download/Nadi/<Room>/chat-downloads/`.
- Settings perlu menyediakan lokasi penyimpanan File Room dan host punya tombol pintasan untuk membuka folder File Room.
- Cara perangkat lain terkoneksi ke host/hotspot harus dibuat semudah mungkin dengan QR/instruksi yang jelas.
- Keamanan dibuat cukup dan praktis untuk jaringan lokal: token/PIN, validasi identitas lokal, batas ukuran/jenis file, sanitasi nama file, dan kontrol host.

Catatan penting: validasi identitas yang benar-benar resmi tidak mungkin dilakukan tanpa database kampus/server eksternal. Untuk Nadi local-first, validasi yang realistis adalah kombinasi format NIM, daftar peserta/roster lokal opsional, persetujuan host, dan identitas yang dikunci selama sesi room.

## Perkembangan Terkini (Antigravity Updates)

### 1. Fitur & Perbaikan yang Sudah Selesai (Completed)
- **Overhaul UI Chat ala WhatsApp:**
  - Layout obrolan dengan warna latar belakang creme `#EFE6DD`.
  - Balon chat (`messageBubble`) hijau terang (`#DCF8C6`) untuk host/pengirim dan putih untuk guest dengan ujung tail melengkung.
  - Composer input chat dimodelkan melengkung (*pill* style) dengan tombol kirim bertipe lingkaran (FAB-style).
  - Penghapusan nama pengirim "Anda" (Host) pada pesan keluar agar obrolan bersih.
- **Visualisasi Lampiran Gambar:** Gambar tidak lagi terpotong kasar (*no center-crop*) karena menggunakan `ScaleType.FIT_CENTER` yang dibalut `MaterialCardView` bersudut bulat dengan border outline tipis `1dp`.
- **Kartu Lampiran Dokumen:** File non-gambar ditampilkan dalam layout kartu horizontal khusus yang memuat ikon berkas kertas (`ic_document.xml`), nama berkas tebal, format berkas, serta ukuran berkas terformat (contoh: `TXT - 51 B`).
- **Fix Keyboard Overlap:** Layout area input chat telah dipindahkan dan dikonfigurasi agar selalu "lengket" di bagian bawah layar secara dinamis, sehingga saat keyboard muncul input box terdorong ke atas dengan mulus tanpa menutupi elemen lain.
- **Fix Launch Crash:** Memperbaiki crash `ClassCastException` di `MainActivity.kt` karena perbedaan casting tombol attach dan send setelah migrasi ke `ImageButton` dan `FloatingActionButton`.
- **Optimisasi Preview Gambar Web:** Local server memberi `Cache-Control: public, max-age=31536000, immutable` dan `ETag` untuk request preview gambar chat (`preview=1`), serta mengembalikan `304 Not Modified` jika cache browser masih valid.
- **Incremental Chat Rendering Host:** Host menyimpan state `renderedChatMessageIds` dan `lastRenderedChatRoomId`, sehingga `renderChatMessages` hanya menambahkan bubble baru dan tidak menghapus/mendekode ulang seluruh gambar pada setiap refresh dashboard.

### 2. Rencana Kerja Optimisasi Berikutnya (Planned & Researched)
- Uji beban manual pada room dengan banyak gambar/lampiran untuk menentukan apakah perlu thumbnail terkompresi khusus, bukan langsung file asli.
- Evaluasi virtualized chat list jika jumlah pesan dinaikkan jauh di atas batas room saat ini.
