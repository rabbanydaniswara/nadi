# Nadi Smoke Test Log

Catatan hasil smoke test manual. Update file ini setiap kali build kandidat diuji.

---

## Latest Local Verification

```text
Date:
Commit:
Tester:
Host device:
Android version:
APK:
```

Automated:

- [ ] `./gradlew :app:testDebugUnitTest`
- [ ] `./gradlew :app:assembleDebug`

Device launch:

- [ ] APK install berhasil
- [ ] MainActivity launch berhasil
- [ ] Home menampilkan Nadi, Buat Ruang, Lihat Riwayat, Pengaturan

Core flow:

- [ ] Same-Wi-Fi room aktif
- [ ] Browser client membuka QR/URL
- [ ] `/api/room` valid token berhasil
- [ ] Invalid token ditolak
- [ ] Host share file
- [ ] Browser download file
- [ ] Browser upload file
- [ ] Host menerima file
- [ ] Chat browser ke host
- [ ] Chat host ke browser
- [ ] Regenerate link menutup token lama
- [ ] Diagnostics dapat disalin
- [ ] Stop room menutup akses

Notes:

```text

```
