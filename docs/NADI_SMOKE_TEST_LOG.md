# Nadi Smoke Test Log

Catatan hasil smoke test manual. Update file ini setiap kali build kandidat diuji.

---

## Latest Local Verification

```text
Date: 2026-06-20
Commit: this commit
Tester: Codex
Host device: 69019f47
Android version: not captured in this run
APK: app/build/outputs/apk/debug/app-debug.apk
```

Automated:

- [x] `./gradlew :app:testDebugUnitTest`
- [x] `./gradlew :app:assembleDebug`

Device launch:

- [x] APK install berhasil
- [x] MainActivity launch berhasil
- [x] Home menampilkan Nadi, Buat Ruang, Lihat Riwayat, Pengaturan

Core flow:

- [x] Same-Wi-Fi room aktif
- [x] Browser client membuka QR/URL
- [x] `/api/room` valid token berhasil
- [x] Invalid token ditolak
- [ ] Host share file
- [ ] Browser download file
- [ ] Browser upload file
- [ ] Host menerima file
- [x] Chat browser ke host
- [ ] Chat host ke browser
- [x] Regenerate link menutup token lama
- [ ] Diagnostics dapat disalin
- [x] Stop room menutup akses

Notes:

```text
Automated gates passed locally.
Device smoke installed and launched app successfully.
Home UI was verified via uiautomator after the Settings addition.
Same-Wi-Fi room started from UI.
Join URL observed: http://192.168.1.5:8080/?token=...
HTTP smoke from host machine:
- /health returned 200.
- /api/room with valid token returned 200.
- Browser shell returned 200.
- Browser shell contained downloadProgress and downloadFile after browser maturity patch.
- Browser-to-host chat POST returned 200.
- Regenerate link via host UI changed the token.
- Old token after regenerate returned 401.
- New token after regenerate returned 200.
- Stop room via host UI closed the server; /health was no longer reachable.
Not yet covered in this run: real file upload/download, host-to-browser chat, diagnostics copy through UI, multi-browser matrix, multi-device matrix.
```
