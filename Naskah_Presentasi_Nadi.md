# NASKAH VOICEOVER VIDEO PRESENTASI TUGAS AKHIR
**Aplikasi Nadi: Sistem Berbagi Berkas dan Obrolan Diskusi Berbasis Jaringan Lokal**
*Ditujukan untuk: Tugas Akhir Mata Kuliah Mobile Programming*

---

## 1. INTRO / PEMBUKAAN (Formal & Akademis)

**[VISUAL]**
*Tampilan wajah Anda di depan kamera secara formal dan sopan (menggunakan pakaian rapi/almamater jika diperlukan), atau rekaman layar yang menampilkan judul proyek: "Presentasi Tugas Akhir Mobile Programming - Aplikasi Nadi". Sertakan teks di layar: Nama: Daniswara Rabbany, NIM: 231011402591, dan Nama Kampus: Universitas Pamulang (UNPAM).*

**[AUDIO]**
> *"Selamat pagi/siang rekan-rekan dan Dosen Pengampu mata kuliah Mobile Programming.*
> 
> *Perkenalkan, nama saya Daniswara Rabbany dengan NIM 231011402591. Pada kesempatan kali ini, saya akan mempresentasikan serta mendemonstrasikan proyek tugas akhir saya yang berjudul **Nadi**, sebuah aplikasi berbagi berkas dan ruang obrolan diskusi berbasis jaringan lokal tanpa memerlukan koneksi internet."*

---

## 2. LATAR BELAKANG MASALAH & SOLUSI

**[VISUAL]**
*Tampilkan slide presentasi atau poin-poin teks ringkas mengenai latar belakang permasalahan jaringan.*

**[AUDIO]**
> *"Latar belakang pembuatan aplikasi ini didasari oleh permasalahan nyata yang sering terjadi di lingkungan kampus kami, Universitas Pamulang (UNPAM).*
> 
> *Di UNPAM, sangat sulit untuk mendapatkan sinyal seluler yang bagus dan internet yang stabil. Hal ini disebabkan oleh kepadatan pengguna (user congestion) di mana ribuan mahasiswa menggunakan jaringan seluler secara bersamaan di dalam area kampus. Akibatnya, kecepatan transfer data internet menjadi sangat lambat.*
> 
> *Sebelum adanya aplikasi Nadi, proses pengiriman berkas kuliah—baik dari handphone ke laptop pribadi maupun antarteman sekelas—harus menggunakan media aplikasi pesan instan publik seperti WhatsApp. Metode ini sepenuhnya bergantung pada kuota dan kestabilan koneksi internet. Karena masalah sinyal yang terganggu di kampus, proses berbagi berkas sering kali gagal, menghambat efisiensi belajar-mengajar.*
> 
> *Oleh karena itu, saya merancang aplikasi **Nadi** sebagai solusi alternatif. Nadi memungkinkan proses pengiriman file dan diskusi teks tetap dapat dilakukan secara cepat dan andal secara offline, dengan memanfaatkan infrastruktur jaringan lokal seperti Wi-Fi kampus atau hotspot portabel nirkabel dari perangkat Host."*

---

## 3. DEMO FUNGSI APLIKASI (Langkah demi Langkah)

### Langkah 1: Host Menginisialisasi Ruang Diskusi
**[VISUAL]**
*Tampilan rekaman layar handphone Android (Host). Perlihatkan proses pengisian Nama Ruang, Nama Pengajar/Host, penentuan PIN keamanan, lalu ketuk tombol "Mulai Ruang". Tunjukkan alamat URL lokal yang dihasilkan oleh aplikasi.*

**[AUDIO]**
> *"Kita masuk ke sesi demonstrasi sistem. Pertama, di sisi Host (perangkat Android), pengajar atau moderator membuka aplikasi Nadi dan mengisi detail ruang seperti Nama Ruang dan Nama Host. Setelah menekan tombol 'Mulai Ruang', aplikasi akan secara otomatis mengaktifkan server HTTP lokal mini di dalam perangkat Android dan menampilkan alamat IP lokal beserta PIN akses keamanan."*

---

### Langkah 2: Klien Bergabung ke Ruang Diskusi
**[VISUAL]**
*Tampilan layar laptop Klien (membuka web browser) atau handphone Android Klien (membuka aplikasi Nadi). Masukkan alamat IP lokal, Nama, NIM, dan PIN keamanan.*

**[AUDIO]**
> *"Kedua, di sisi Klien atau peserta. Peserta yang menggunakan laptop cukup membuka peramban web (browser) tanpa perlu menginstal aplikasi tambahan. Peserta memasukkan alamat IP yang disediakan oleh Host, mengisi Nama, NIM, serta PIN keamanan untuk otentikasi. Sistem akan langsung mengarahkan pengguna ke halaman dashboard diskusi."*

---

### Langkah 3: Pengujian Obrolan Teks Instan
**[VISUAL]**
*Tampilan layar terbagi (Split Screen): Kiri menampilkan antarmuka browser Klien, kanan menampilkan antarmuka aplikasi Host. Tunjukkan pengiriman chat dari Klien ke Host secara real-time.*

**[AUDIO]**
> *"Ketiga, pengujian fitur obrolan teks. Saat Klien mengirimkan pesan teks atau melampirkan file gambar, pesan tersebut akan terkirim secara instan ke server Host menggunakan protokol komunikasi dua arah WebSockets. Antarmuka obrolan juga menampilkan pratinjau gambar dengan tata letak yang rapi dan bebas dari masalah pesan ganda."*

---

### Langkah 4: Pengujian Berbagi File
**[VISUAL]**
*Perlihatkan Host mengunggah sebuah berkas dokumen (misal materi .pdf). Setelah itu, perlihatkan Klien mengunduh berkas tersebut dari dashboard web.*

**[AUDIO]**
> *"Keempat, pengujian transfer berkas. Host dapat membagikan berkas materi kuliah secara langsung melalui tab file. Berkas tersebut akan segera muncul di dashboard Klien dan dapat langsung diunduh dengan kecepatan transfer jaringan lokal yang stabil, tanpa menggunakan kuota internet seluler."*

---

### Langkah 5: Penutupan Ruang dan Manajemen Memori
**[VISUAL]**
*Host menekan tombol "Tutup Ruang". Tunjukkan indikator bahwa ruangan telah selesai dan memori dibersihkan.*

**[AUDIO]**
> *"Terakhir, setelah sesi pembelajaran selesai, Host dapat menutup ruangan. Sistem Nadi akan secara otomatis menghapus seluruh cache transfer file dan membersihkan alokasi memori RAM pada perangkat Android untuk memastikan kinerja perangkat tetap optimal."*

---

## 4. OUTRO / PENUTUP (Formal & Akademis)

**[VISUAL]**
*Kembali ke tampilan kamera wajah Anda (Talking Head) atau slide penutup presentasi tugas akhir dengan teks: "Terima Kasih".*

**[AUDIO]**
> *"Kesimpulannya, aplikasi Nadi berhasil diimplementasikan sebagai alternatif solusi transfer data dan sarana komunikasi mandiri di area dengan kendala sinyal internet seperti kampus UNPAM.*
> 
> *Proyek ini dikembangkan dengan mematuhi prinsip pemrograman mobile modern, baik dari sisi arsitektur kode maupun antarmuka pengguna.*
> 
> *Demikian presentasi tugas akhir mata kuliah Mobile Programming dari saya. Mohon maaf apabila ada kekurangan dalam penyampaian maupun pengerjaan proyek ini. Terima kasih atas perhatian dan waktu yang telah diberikan. Selamat pagi/siang."*
