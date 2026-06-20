# Nadi QA and Release Checklist

Dokumen ini adalah gate praktis sebelum Nadi dibagikan ke tester atau dipakai di skenario nyata. Checklist ini melengkapi [NADI_PRODUCT_MATURITY_PLAN.md](NADI_PRODUCT_MATURITY_PLAN.md).

---

## 1. Automated Gates

Wajib lulus sebelum build dibagikan:

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- GitHub Actions `Android CI` lulus pada branch/PR.

Jika salah satu gagal, build tidak boleh dianggap release candidate.

---

## 2. Core Smoke Test

Skenario inti yang wajib dicatat:

- App berhasil install.
- Home screen tampil.
- Host dapat membuat room mode satu Wi-Fi.
- QR/URL muncul.
- Browser client membuka halaman Nadi.
- Invalid token ditolak.
- Browser dapat melihat metadata room.
- Host dapat membagikan file.
- Browser dapat download file host.
- Browser dapat upload file ke host.
- Host melihat file masuk.
- Browser mengirim chat ke host.
- Host mengirim chat ke browser.
- Tombol perbarui link membuat QR/URL baru.
- Link lama tidak bisa mengakses API room.
- Tombol tutup room menghentikan akses browser.

---

## 3. Local-only Hotspot Test

Jalankan pada perangkat Android target:

- Permission diminta hanya saat mode hotspot dipilih.
- Jika permission ditolak, app fallback ke satu Wi-Fi.
- Jika hotspot aktif, SSID/password tampil.
- Client tersambung ke hotspot host.
- Client membuka QR/URL hotspot.
- Upload, download, dan chat berjalan di hotspot.
- Diagnostics menampilkan mode, port, alamat lokal, dan jumlah client.

Catat vendor, model, Android version, dan hasil.

---

## 4. Lifecycle Test

Skenario:

- Room aktif lalu host menekan Home.
- Room aktif lalu host mengunci layar.
- Browser tetap bisa membuka `/api/room` selama foreground service aktif.
- Notification room aktif muncul.
- Stop dari notification menutup room.
- App dibuka kembali dan active room state tampil.

Jika ada vendor yang mematikan service, catat sebagai known issue beserta mitigasi.

---

## 5. File Transfer Matrix

Uji minimal:

- 10 KB `.txt`
- 1 MB gambar
- 5 MB PDF
- 50 MB ZIP/PDF/video pendek
- Nama file dengan spasi
- Nama file duplikat
- Ekstensi tidak umum

Expected:

- Tidak crash.
- Progress upload browser tampil.
- File masuk tidak overwrite diam-diam.
- History lokal menampilkan transfer.
- Diagnostics tetap bisa disalin setelah transfer.

---

## 6. Browser Matrix

Minimal:

- Chrome desktop
- Edge desktop
- Chrome Android

Tambahan jika tersedia:

- Firefox desktop
- Safari/iOS

Expected:

- Layout tidak clipping.
- Upload button dan progress bekerja.
- Download link bekerja.
- Chat polling berjalan.
- Saat link lama invalid, browser menampilkan state akses tidak valid.

---

## 7. Privacy and Safety Review

Pastikan:

- Copy privasi room tampil di active room.
- User tahu QR/URL dapat dipakai siapa pun di jaringan lokal yang sama.
- Regenerate link tersedia dan copy menjelaskan link lama ditutup.
- Clear history hanya menghapus metadata, bukan diam-diam menghapus file user.
- Tidak ada dependency cloud untuk core flow.

---

## 8. Release Candidate Notes

Setiap build kandidat harus mencatat:

- Commit SHA.
- Version name/code.
- Device host yang dipakai.
- Network mode yang lulus.
- Browser yang lulus.
- File terbesar yang berhasil.
- Known issues.
- Keputusan: lanjut beta, ulangi QA, atau blokir.

Template:

```text
Build:
Commit:
Tanggal:
Host device:
Android version:
Mode lulus:
Browser lulus:
File terbesar:
Known issues:
Decision:
```
