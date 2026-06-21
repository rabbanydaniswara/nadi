# Nadi Smoke Test Log

Catatan hasil smoke test manual. Update file ini setiap kali build kandidat diuji.

---

## UIX Separation Final Verification

```text
Date: 2026-06-21
Implementation commit: 9d2d8f4
Documentation commit: this commit
Tester: Codex
Host device: 69019f47
Network mode: Same-Wi-Fi
APK: app/build/outputs/apk/debug/app-debug.apk
```

Automated:

- [x] `./gradlew.bat :app:testDebugUnitTest`
- [x] `./gradlew.bat :app:assembleDebug`
- [x] `git diff --check`

Device and local room smoke:

- [x] APK install and launch succeeded on the test device.
- [x] Local room started from Android UI in Same-Wi-Fi mode.
- [x] Browser client HTML over forwarded local URL returned the separated client UI.
- [x] Browser client shell contained `File Room`, `Chat`, `Info`, `chat-image`, and the non-image download instruction.
- [x] `/api/identity` accepted `NIM` and `Nama` for the test client.
- [x] `/api/chat-attachment` accepted a PNG attachment.
- [x] `/api/chat-attachment` accepted a TXT attachment.
- [x] Chat attachment image metadata resolved to `image/png`.
- [x] Chat attachment document metadata resolved to `text/plain`.
- [x] Image preview endpoint returned `Content-Disposition: inline`.
- [x] Document download endpoint returned `Content-Disposition: attachment`.
- [x] Chat attachment files appeared in `Download/Nadi/<Room>/chat-downloads/`.

Evidence:

```text
SessionId: rspU6aPp8h2U
PageHasChatImage: True
PageHasDownloadInstruction: True
ImageMime: image/png
TextMime: text/plain
ImageDirection: chat_attachment
TextDirection: chat_attachment
PreviewDisposition: inline; filename="stage8-preview-085714.png"
PreviewContentType: image/png
DownloadDisposition: attachment; filename="stage8-document-085714.txt"
Storage: /sdcard/Download/Nadi/rspU6aPp8h2U/chat-downloads/
Files: stage8-document-085714.txt, stage8-preview-085714.png
```

Scope note:

```text
QA mengikuti batasan owner proyek: cukup lingkungan test Codex/device yang tersedia.
Tidak dilakukan Safari/iOS, matrix vendor Android luas, signing produksi, feedback tester, atau final release testing.
```

---

## Identity, Chat Attachment, and File Room Verification

```text
Date: 2026-06-21
Commit: this commit
Tester: Codex
Host device: 69019f47
APK: app/build/outputs/apk/debug/app-debug.apk
```

Automated:

- [x] `./gradlew :app:testDebugUnitTest`
- [x] `./gradlew :app:assembleDebug`

Device and local room smoke:

- [x] APK install and launch succeeded on the test device
- [x] Final debug APK reinstall and launch succeeded after the last UI polish
- [x] Local room started from Android UI
- [x] Hotspot mode became active and showed hotspot SSID/password plus Wi-Fi QR
- [x] Host UI showed one connected identified client after browser/API smoke
- [x] `/api/chat` rejected an unregistered client with `403 identity_required`
- [x] `/api/identity` accepted NIM and Nama, then locked the client identity for room use
- [x] `/api/chat` accepted a text message from the identified client
- [x] `/api/upload` accepted a separate File Room upload from the identified client
- [x] `/api/chat-attachment` accepted a small allowed attachment and linked it to chat metadata

Notes:

```text
QA was kept to the available test environment, as requested.
The Codex in-app browser runtime could not be started in this session because the runtime returned a sandbox metadata error before any page interaction.
The same room page was still checked through the forwarded local URL and returned HTTP 200 with the expected identity, NIM/Nama, File Room, and chat attachment UI text.
```

---

## Latest Local Verification

```text
Date: 2026-06-20
Commit: this commit
Tester: Codex
Host device: 69019f47
Android version: 14 (Xiaomi 2203129G)
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

Browser matrix:

- [x] Chrome desktop renders browser client
- [x] Edge desktop renders browser client
- [ ] Firefox desktop renders browser client
- [x] Chrome Android renders browser client
- [x] Firefox Android renders browser client
- [ ] Safari/iOS renders browser client

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
- Chrome desktop headless DOM smoke returned room name, ready state, upload section, download section, chat section, and downloadProgress.
- Edge desktop headless DOM smoke returned room name, ready state, upload section, download section, chat section, and downloadProgress.
- Firefox headless screenshot automation was attempted but did not produce stable evidence in this environment.
- Chrome Android opened the active room URL via ADB VIEW intent. UI dump returned Nadi Room, Terhubung ke Nadi, Siap, Ambil file, Kirim file ke host, and Chat lokal.
- Firefox Android opened the active room URL via ADB VIEW intent. After closing the Firefox Translations sheet, UI dump returned Nadi Room, Terhubung ke Nadi, Siap, Ambil file, Kirim file ke host, and 2 terhubung.
Not yet covered in this run: Firefox desktop manual/browser evidence, Safari/iOS browser evidence, multi-device/vendor matrix.
```
