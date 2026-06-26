# Nadi

> **Tetap mengalir, meski sinyal melemah.**

[![Download APK & AAB](https://img.shields.io/badge/Download-APK%20%26%20AAB-forestgreen?style=for-the-badge&logo=android)](https://github.com/rabbanydaniswara/nadi/releases/latest)

Nadi adalah aplikasi Android untuk berbagi file (*file sharing*) dan berkomunikasi (*chat*) dalam satu ruangan secara lokal tanpa memerlukan koneksi internet sama sekali. Aplikasi ini dirancang khusus untuk skenario seperti kelas, rapat, atau kegiatan lapangan di daerah yang minim sinyal atau saat kuota internet terbatas.

## Bagaimana Cara Kerjanya?

1. **Host** membuka ruang lokal lewat aplikasi Nadi di Android (bisa menggunakan Wi-Fi yang sama atau membuat hotspot lokal langsung dari HP).
2. Nadi akan menjalankan web server lokal di perangkat Host dan menampilkan kode QR serta Link ruangan.
3. **Client / Peserta** cukup memindai kode QR tersebut. Mereka bisa masuk menggunakan aplikasi native Nadi atau langsung menggunakan browser bawaan HP mereka (tanpa perlu menginstal aplikasi).
4. Anggota ruangan bisa saling berkirim file (*File Room*), *chatting* secara real-time, dan mengunduh materi yang disediakan oleh Host.

---

## Fitur Utama

- **Offline-First (Tanpa Internet)**: Menggunakan mode Satu Wi-Fi atau Local Hotspot. Data mengalir langsung antarperangkat secara lokal.
- **Akses Fleksibel**: Peserta bisa bergabung via aplikasi Native Android atau browser (Chrome, Safari, dll.).
- **File Room**: Manajemen berkas lokal terpisah untuk materi, pengumpulan tugas, dan dokumen penting.
- **Chat Real-time**: Komunikasi instan dengan dukungan lampiran file (*attachment*) berbasis WebSocket.
- **Tanpa Cloud**: Semua riwayat transfer dan data obrolan disimpan lokal di perangkat masing-masing demi keamanan dan kecepatan maksimal.

---

## Persyaratan Sistem & Build

Aplikasi ini dibangun menggunakan Kotlin untuk sisi Android Native dan HTML/JS Vanilla untuk antarmuka web klien.

### Cara Build Aplikasi:
1. Clone repositori ini:
   ```bash
   git clone https://github.com/rabbanydaniswara/nadi.git
   ```
2. Buka proyek menggunakan **Android Studio** terbaru.
3. Lakukan Gradle Sync dan build proyek secara langsung.
4. Untuk membuat file rilis APK/AAB:
   ```bash
   ./gradlew assembleRelease
   ```
