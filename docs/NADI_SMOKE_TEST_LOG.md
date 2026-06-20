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
- [x] Host share file
- [x] Browser download file
- [x] Browser upload file
- [x] Host menerima file
- [x] Chat browser ke host
- [x] Chat host ke browser
- [x] Regenerate link menutup token lama
- [x] Diagnostics dapat disalin
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
- Real browser upload used multipart POST to /api/upload.
- Upload response returned 200 with status success and progress 100.
- Uploaded file nadi-real-upload-smoke.txt appeared in /sdcard/Android/data/com.danis.nadi/files/received.
- Host-to-browser chat was sent from Android UI and appeared in /api/chat.
- Diagnostics panel and copy button were visible in Android UI.
- Copy diagnostics button was tapped successfully; clipboard content could not be read because this device shell does not implement `cmd clipboard get`.
- Host share via Android file picker selected nadi_home_screenshot.png from DocumentsUI Recent.
- /api/files returned shared file metadata for nadi_home_screenshot.png.
- Browser download endpoint returned 101045 bytes for the host-shared file.
Not yet covered in this run: multi-browser matrix, multi-device matrix.
```
