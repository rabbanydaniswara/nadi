# Nadi UI/UX Separation Plan

Dokumen ini adalah rencana khusus untuk memperbaiki UI/UX Nadi agar fitur utama tidak terasa campur aduk. Fokusnya adalah pemisahan yang jelas antara room control, file room, chat, peserta, dan riwayat sebelum implementasi UI berikutnya dilakukan.

Dokumen ini melengkapi:

- [NADI_PROJECT_CONTEXT.md](../NADI_PROJECT_CONTEXT.md)
- [NADI_PRD.md](../NADI_PRD.md)
- [NADI_PRODUCT_MATURITY_PLAN.md](NADI_PRODUCT_MATURITY_PLAN.md)
- [NADI_IMPLEMENTATION_BLUEPRINT.md](NADI_IMPLEMENTATION_BLUEPRINT.md)

---

## 1. Problem yang Harus Diselesaikan

Implementasi saat ini sudah memiliki fondasi teknis untuk identitas, chat attachment, dan file room. Namun pengalaman produknya belum memenuhi target karena:

- Chat belum benar-benar terasa seperti chat modern.
- File Room sudah terpisah secara backend, tetapi belum terasa sebagai menu mandiri.
- Host dashboard masih mencampur terlalu banyak konteks dalam satu layar panjang.
- Browser client masih terasa seperti halaman utilitas, bukan pengalaman room yang rapi.
- Pengguna belum mendapat batas mental yang jelas: "ini untuk ngobrol", "ini untuk file kelas", "ini untuk peserta", "ini untuk kontrol room".

Target tahap berikutnya:

> Nadi harus terasa seperti ruang kelas lokal yang punya menu jelas: Room, File Room, Chat, Peserta, dan Riwayat. Chat boleh punya lampiran, tetapi File Room tetap menjadi tempat utama untuk materi dan pengumpulan file.

---

## 2. Research Basis

Riset cepat dari pola UI/UX modern menghasilkan beberapa prinsip yang relevan untuk Nadi:

1. **Information architecture lebih dulu daripada komponen navigasi.**
   NN/g menjelaskan bahwa information architecture adalah struktur dan hubungan konten/fungsi, sedangkan navigation hanyalah cara UI memberi akses ke struktur itu. Untuk Nadi, ini berarti kita harus memutuskan pemisahan domain fitur dulu, baru memilih bottom nav atau tabs.
   Source: [NN/g - IA vs Navigation](https://www.nngroup.com/articles/ia-vs-navigation/)

2. **Bottom navigation cocok untuk 3 sampai 5 top-level destination.**
   Material Design 3 menyatakan navigation bar dipakai untuk berpindah antar view utama pada layar kecil dan cocok untuk 3-5 destination yang sama pentingnya. Nadi punya kandidat top-level yang pas: Room, File, Chat, Peserta, Riwayat.
   Source: [Material Design 3 - Navigation bar](https://m3.material.io/components/navigation-bar/overview)

3. **Navigation rail cocok untuk layar lebih lebar.**
   Android Developers merekomendasikan navigation rail untuk destination utama pada layar besar/tablet/desktop layout. Ini relevan untuk browser client desktop atau jika Android host nanti mendukung landscape/tablet lebih baik.
   Source: [Android Developers - Navigation rail](https://developer.android.com/develop/ui/compose/components/navigation-rail)

4. **Tabs dipakai untuk panel sejenis di dalam satu konteks, bukan menggantikan struktur utama.**
   NN/g menekankan tabs sebagai cara melihat satu panel dari beberapa pilihan dalam konteks yang sama. Untuk Nadi, tabs cocok di dalam File Room, misalnya "Dibagikan" dan "Diterima", bukan untuk mencampur File Room dan Chat.
   Source: [NN/g - Tabs, Used Right](https://www.nngroup.com/articles/tabs-used-right/)

5. **List harus mudah dipindai.**
   Material Design 3 list guidance menekankan list sebagai indeks vertikal yang mudah discan. File Room dan Peserta harus memakai row/list yang padat dan informatif, bukan kartu besar berulang yang membuat layar cepat panjang.
   Source: [Material Design 3 - Lists](https://m3.material.io/components/lists/guidelines)

6. **Badge cocok untuk count/status kecil, bukan informasi utama.**
   Material Design 3 badges cocok untuk menunjukkan count/status pada navigation item atau icon. Untuk Nadi: badge dapat menunjukkan jumlah file masuk, pesan baru, atau peserta menunggu approval.
   Source: [Material Design 3 - Badges](https://m3.material.io/components/badges)

7. **Edge-to-edge dan insets harus diperhatikan.**
   Android modern makin mendorong edge-to-edge. Jika Nadi memakai bottom navigation, layout harus menjaga konten dan tombol agar tidak tertutup system bars.
   Source: [Android Developers - Edge-to-edge in views](https://developer.android.com/develop/ui/views/layout/edge-to-edge)

---

## 3. Information Architecture Baru

### 3.1 Android Host App

Gunakan dua mode navigasi:

#### Saat tidak ada room aktif

Utamakan home sederhana:

- Home
- Riwayat
- Pengaturan

Bottom navigation boleh dipakai jika implementasi shell sudah siap, tetapi home awal tetap harus fokus pada satu primary action: **Buat Ruang**.

#### Saat room aktif

Gunakan bottom navigation dengan 5 menu utama:

```text
Ruang | File | Chat | Peserta | Riwayat
```

Makna tiap menu:

- **Ruang**
  Kontrol room: status aktif, QR join, Wi-Fi/hotspot info, salin instruksi, stop room, diagnostics collapsed.

- **File**
  File Room utama: file yang dibagikan host, file yang dikirim client ke host, progress transfer, open folder.

- **Chat**
  Chat lokal: bubble percakapan, input pesan, lampiran chat, preview gambar/dokumen kecil, timestamp.

- **Peserta**
  Identitas client: NIM, Nama, status terhubung, last seen, approval/roster nanti.

- **Riwayat**
  Aktivitas lokal per room: file diterima/dibagikan, ringkasan sesi, akses ke file lama.

Pengaturan tidak perlu menjadi menu utama saat room aktif. Letakkan di top app bar overflow atau tombol kecil di Home.

### 3.2 Browser Client

Browser client harus lebih sederhana daripada host. Setelah identitas valid, gunakan 3 area utama:

```text
File Room | Chat | Info
```

Makna:

- **File Room**
  Ambil file dari host dan kirim file ke host. Ini area utama untuk materi/tugas.

- **Chat**
  Percakapan lokal dengan lampiran kecil. Lampiran tetap terlihat sebagai bagian pesan.

- **Info**
  Identitas peserta, room name, status koneksi, cara reconnect, privacy/local-first note.

Pada layar desktop, browser client boleh memakai layout dua kolom:

- Kolom utama: File Room atau Chat.
- Kolom samping: Info room dan status koneksi.

Pada mobile browser, gunakan bottom navigation atau top segmented tabs sederhana.

---

## 4. Separation Rules

Aturan ini harus dijaga saat implementasi UI:

1. **File Room dan Chat tidak boleh berbagi daftar utama.**
   File yang dikirim lewat File Room muncul di File menu. Lampiran chat muncul di bubble chat.

2. **Lampiran chat bukan File Room utama.**
   Lampiran chat disimpan di `chat-attachments` dan ditampilkan di Chat. Jika nanti perlu, tambahkan aksi eksplisit "Simpan ke File Room".

3. **Dashboard Ruang hanya boleh preview ringkas.**
   Menu Ruang boleh menampilkan count: jumlah peserta, file masuk, pesan baru. Detailnya harus diarahkan ke menu masing-masing.

4. **Peserta menjadi sumber identitas.**
   NIM/Nama ditampilkan konsisten di Chat, File, dan Peserta. Perubahan identitas tidak boleh tersedia sebagai aksi ringan.

5. **Riwayat tidak boleh menjadi tempat kerja utama.**
   Riwayat untuk melihat aktivitas lampau. File aktif tetap di File Room.

6. **Diagnostics selalu collapsed.**
   IP, port, token, dan server detail tidak tampil sebagai informasi utama.

7. **File Room disimpan di folder publik yang mudah ditemukan.**
   Default target berikutnya adalah `Download/Nadi/<Room>/received/`, bukan `Android/data/...`, agar file yang memang dikirim antar perangkat mudah ditemukan di file manager.

8. **Chat download dipisahkan dari File Room.**
   File/dokumen dari chat yang perlu didownload masuk ke `Download/Nadi/<Room>/chat-downloads/`, bukan ke `received/`.

---

## 5. Target UI Konseptual

### 5.1 Host: Menu Ruang

Tujuan:

- Host langsung tahu room aktif.
- QR dan instruksi join terlihat jelas.
- Stop room aman tetapi mudah ditemukan.

Komponen:

- Top status strip: `Aktif`, mode jaringan, jumlah peserta.
- QR room besar.
- Jika hotspot aktif: QR Wi-Fi + SSID/password.
- Tombol:
  - Salin instruksi join.
  - Salin URL.
  - Stop room.
- Compact stats:
  - Peserta.
  - File masuk.
  - Pesan baru.
- Diagnostics collapsed.

### 5.2 Host: Menu File

Tujuan:

- File Room terasa sebagai fitur terpisah dari chat.
- Host bisa membagikan materi dan melihat file masuk tanpa membuka chat.

Struktur:

- Primary action: `Tambah file untuk dibagikan`.
- Secondary action: `Buka folder room`.
- Storage action: `Atur folder penyimpanan`.
- Tabs internal:
  - `Dibagikan`
  - `Diterima`
- List row tiap file:
  - Icon tipe file.
  - Nama file.
  - Ukuran.
  - Pengirim/NIM untuk file diterima.
  - Waktu.
  - Status/progress.
  - Action: buka/share ulang/hapus metadata nanti.

Empty state:

> Belum ada file di room ini. Tambahkan materi atau tunggu peserta mengirim file.

### 5.3 Host: Menu Chat

Tujuan:

- Chat terasa seperti percakapan modern, tetapi tetap ringan.

Komponen:

- Message list bubble:
  - Bubble kanan untuk host.
  - Bubble kiri untuk peserta.
  - Nama/NIM di atas bubble untuk peserta.
  - Timestamp kecil.
  - Attachment chip/card di dalam bubble.
- Input bar sticky bawah:
  - Tombol lampiran.
  - Text input.
  - Send button icon.
- Empty state:

> Belum ada pesan. Kirim pengumuman lokal untuk peserta room.

Rules:

- Gambar di chat tampil sebagai preview di bubble.
- Dokumen, ZIP, dan file lain tampil sebagai file card dengan nama, ukuran, icon tipe file, dan tombol `Download`.
- Setelah file chat berhasil didownload, tombol berubah menjadi `Buka`.
- File chat yang didownload masuk ke folder `chat-downloads`.
- Attachment chat tidak masuk list File Room.
- Batas ukuran dan tipe file tetap dipakai.

### 5.4 Host: Menu Peserta

Tujuan:

- Identitas peserta jelas dan bisa dipercaya.

Komponen:

- Count peserta aktif.
- List peserta:
  - Nama.
  - NIM.
  - Status: aktif, baru saja aktif, terputus.
  - Device/browser singkat.
- Future section:
  - Menunggu persetujuan.
  - Roster lokal.
  - Remove client.

Empty state:

> Belum ada peserta. Bagikan QR room agar peserta bisa bergabung.

### 5.5 Host: Menu Riwayat

Tujuan:

- Melihat aktivitas lokal tanpa mencampur flow aktif.

Komponen:

- Session summary per room.
- Filter:
  - Semua.
  - File.
  - Chat attachment.
- File history row dengan status path tersedia/hilang.

### 5.6 Browser: File Room

Tujuan:

- Client langsung tahu ini tempat mengirim/mengambil file, bukan chat.

Komponen:

- Header room + identity chip.
- Section `Ambil file dari host`.
- Section `Kirim file ke host`.
- Upload progress.
- File list padat.

### 5.7 Browser: Chat

Tujuan:

- Client merasa sedang chat di room kelas.

Komponen:

- Bubble chat.
- Sender identity.
- Input bar.
- Attachment action.
- Error state jika identity belum valid.

### 5.8 Browser: Info

Tujuan:

- Tempat informasi koneksi dan identitas, bukan bercampur di File/Chat.

Komponen:

- NIM/Nama aktif.
- Room name dan host.
- Status koneksi.
- Local-first privacy note.
- Cara reconnect.

---

## 6. Storage Direction

### 6.1 Rekomendasi Default

Berdasarkan batasan scoped storage Android modern, default penyimpanan File Room yang direkomendasikan adalah:

```text
Download/
  Nadi/
    <room-name-or-room-id>/
      received/
      chat-downloads/
```

Makna folder:

- `received/`: file utama yang dikirim lewat File Room.
- `chat-downloads/`: file dari chat yang user pilih untuk download.

Catatan:

- Root langsung seperti `/Nadi/` tidak dijadikan target utama karena tidak stabil di Android modern tanpa izin khusus yang berat.
- `Android/data/...` tetap boleh dipakai hanya sebagai fallback internal, cache sementara, atau storage private aplikasi.
- Metadata tetap disimpan internal agar Nadi tahu room, pengirim, NIM/Nama, waktu, dan status.

### 6.2 Setting Folder Penyimpanan

Tambahkan setting:

```text
Lokasi penyimpanan File Room
```

Behavior:

- Default: `Download/Nadi/`.
- User dapat memilih folder lain melalui Storage Access Framework bila ingin, misalnya `Documents/Nadi/`.
- Nadi menyimpan izin folder pilihan user jika diberikan.
- Jika folder pilihan tidak tersedia, Nadi kembali ke default/fallback dan menampilkan pesan jelas.

Copy yang disarankan:

> File Room disimpan di folder yang mudah ditemukan. Kamu bisa mengganti lokasi penyimpanan kapan saja.

### 6.3 Tombol Buka Folder

Tambahkan tombol di menu File:

```text
Buka folder File Room
```

Behavior:

- Membuka folder room aktif jika file manager mendukung.
- Jika tidak bisa dibuka langsung, salin path/lokasi folder dan tampilkan toast/snackbar.
- Tampilkan lokasi saat ini dalam teks kecil, misalnya `Download/Nadi/Kelas Algoritma/`.

### 6.4 Chat Media Behavior

Target perilaku chat mendekati messenger modern:

- Gambar:
  - Upload sebagai attachment chat.
  - Tampilkan preview gambar di bubble.
  - Tap membuka preview lebih besar.
- Dokumen/file lain:
  - Tampilkan sebagai kartu file.
  - User harus download dulu.
  - Setelah download, tombol berubah menjadi `Buka`.
- File chat tetap berbeda dari File Room.
- Optional later: action `Simpan ke File Room`.

---

## 7. Visual Direction

Nadi tetap memakai karakter:

- Clean.
- Modern.
- Minimalist.
- Tenang.
- Premium ringan.
- Tidak terlalu teknis.

Guidelines:

- Gunakan background `Mist` untuk canvas dan surface putih untuk panel utama.
- Hindari nested card.
- Gunakan list row untuk banyak item, bukan kartu besar berulang.
- Radius 8dp untuk komponen umum.
- Spacing konsisten: 4, 8, 12, 16, 24, 32.
- Icon harus fungsional: room, file, chat, people, history.
- Badge hanya untuk count kecil, misalnya pesan baru atau file masuk.
- Primary action hanya satu per screen.
- Tombol destructive seperti `Stop room` harus punya confirmation ringan.

---

## 8. Implementation Plan Berikutnya

### Phase UIX-0: Planning Lock

Status:

- Dokumen ini dibuat sebagai source of truth sementara untuk redesign separation.

Exit gate:

- Owner setuju dengan struktur menu: `Ruang`, `File`, `Chat`, `Peserta`, `Riwayat`.

### Phase UIX-1: Navigation Shell

Objective:

- Membuat struktur menu host yang jelas tanpa mengubah behavior server.

Scope:

- Tambah bottom navigation pada active room.
- Pecah active room menjadi 5 container/screen state.
- Pindahkan konten existing ke menu yang sesuai.
- Pengaturan tetap di luar active bottom nav.

Acceptance:

- Tidak ada lagi satu dashboard panjang yang mencampur semua fitur.
- Semua fitur lama tetap dapat diakses.
- Build dan unit test lulus.

### Phase UIX-2: File Room Redesign

Objective:

- Membuat File Room terasa benar-benar terpisah dari chat.

Scope:

- UI khusus File Room host.
- UI khusus File Room browser.
- Tabs internal `Dibagikan` dan `Diterima`.
- Tombol `Buka folder room`.
- Setting `Lokasi penyimpanan File Room`.
- Default storage `Download/Nadi/<Room>/received/`.
- File row dengan sender identity.
- Pastikan chat attachments tidak muncul sebagai File Room utama.

Acceptance:

- User dapat menjelaskan bahwa file tugas/materi masuk File Room.
- Lampiran chat tidak tampil di daftar File Room utama.
- File Room utama mudah ditemukan lewat file manager.

### Phase UIX-3: Chat Redesign

Objective:

- Membuat chat terasa seperti messenger modern ringan.

Scope:

- Bubble left/right.
- Timestamp.
- Sender identity NIM/Nama.
- Preview gambar di bubble.
- Attachment card untuk dokumen/file lain.
- Download dulu sebelum file chat bisa dibuka.
- Simpan download chat ke `Download/Nadi/<Room>/chat-downloads/`.
- Sticky input bar.
- Empty/error states.

Acceptance:

- Chat terlihat sebagai percakapan, bukan daftar log.
- File attachment terlihat sebagai bagian pesan.
- File Room tetap tidak tercampur.
- Setelah attachment chat didownload, user bisa membukanya dari chat.

### Phase UIX-4: Peserta and Identity Screen

Objective:

- Menjadikan identitas peserta jelas dan dapat diawasi host.

Scope:

- Menu Peserta.
- List NIM/Nama/status.
- Identity locked copy.
- Future placeholder untuk roster/approval tanpa mengaktifkan fitur besar dulu.

Acceptance:

- Host bisa melihat siapa yang terkoneksi dan identitasnya.
- Tidak ada flow ringan untuk mengganti identitas diam-diam.

### Phase UIX-5: Browser Client Separation

Objective:

- Browser client tidak lagi terasa sebagai satu halaman campur.

Scope:

- Navigation `File Room`, `Chat`, `Info`.
- Identity gate tetap sebelum akses penuh.
- File Room dan Chat punya layout sendiri.
- Responsive desktop/mobile.

Acceptance:

- Client bisa berpindah jelas antara File Room dan Chat.
- Tidak perlu tutorial untuk memahami fungsi tiap area.

### Phase UIX-6: QA and Documentation

Objective:

- Memastikan redesign tidak merusak flow MVP.

Scope:

- `testDebugUnitTest`.
- `assembleDebug`.
- Device launch smoke.
- Browser/client smoke sesuai lingkungan yang tersedia.
- Update smoke test log.

Acceptance:

- Create room, join, identity, upload File Room, chat, chat attachment, and open folder tetap berjalan.

---

## 9. Non-Goals Untuk Tahap UIX

Jangan dikerjakan dulu pada tahap ini:

- Migrasi ke Jetpack Compose.
- WebSocket chat.
- Cloud account/login.
- Roster resmi kampus.
- E2EE penuh.
- Release signing.
- iOS/Safari support.
- Matrix vendor Android luas.

Tahap ini hanya fokus membuat struktur UI/UX jelas dan tidak campur aduk.

---

## 10. Decision Summary

Rekomendasi utama:

1. Pakai bottom navigation untuk active host room.
2. Menu host aktif: `Ruang`, `File`, `Chat`, `Peserta`, `Riwayat`.
3. Browser client: `File Room`, `Chat`, `Info`.
4. File Room dan Chat harus punya storage, list, dan mental model yang berbeda.
5. Chat attachment tetap di Chat, bukan di File Room.
6. Dashboard Ruang menjadi kontrol dan ringkasan, bukan tempat semua detail.
7. Default storage File Room: `Download/Nadi/<Room>/received/`.
8. Download attachment chat: `Download/Nadi/<Room>/chat-downloads/`.
9. Tambahkan setting folder penyimpanan File Room dan tombol buka folder File Room.
10. Implementasi berikutnya harus mulai dari navigation shell, baru File Room/storage, baru Chat.
