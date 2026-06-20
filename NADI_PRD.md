# Product Requirements Document (PRD) - Nadi

## 1. Ringkasan Produk

**Nadi** adalah aplikasi Android local-first untuk berbagi file dan chat dalam satu ruangan tanpa bergantung pada internet. Nadi ditujukan untuk lingkungan kampus, kelas, seminar, organisasi, dan ruang kerja yang sering mengalami jaringan internet padat atau sinyal seluler lemah.

Nadi bekerja dengan membuat satu HP Android menjadi host lokal. Perangkat lain seperti laptop atau HP teman dapat bergabung melalui browser menggunakan QR/URL lokal. Setelah terhubung, pengguna dapat mengirim file, mengambil file, dan chat secara lokal.

### 1.1 One-liner

Nadi menjaga file dan chat tetap mengalir saat sinyal melemah.

### 1.2 Tagline

**Tetap mengalir, meski sinyal melemah.**

### 1.3 Problem Statement

Di kampus, banyak aktivitas kuliah bergantung pada internet meskipun perangkat berada di ruangan yang sama. Ketika jaringan seluler atau Wi-Fi kampus padat, hal sederhana seperti mengirim PDF dari HP ke laptop, membagikan PPT ke teman, mengirim foto papan tulis, atau menyampaikan chat kelas dapat menjadi lambat atau gagal.

Masalah ini terjadi karena aplikasi umum seperti WhatsApp, email, cloud drive, atau LMS tetap melewati jaringan internet dan server eksternal.

Contoh alur lambat:

```text
HP -> internet/operator -> server cloud -> internet/operator -> laptop
```

Padahal perangkat dekat secara fisik dan sebenarnya dapat berkomunikasi lokal:

```text
HP -> jaringan lokal -> laptop
```

### 1.4 Solusi

Nadi menyediakan jalur lokal:

- Satu HP Android menjadi host.
- Host membuat local room.
- Host menjalankan local web server.
- Laptop/HP client bergabung melalui QR/URL.
- File dan chat berjalan lewat jaringan lokal, bukan internet.

### 1.5 Tujuan Produk

- Mengurangi ketergantungan pada internet untuk transfer file jarak dekat.
- Membantu mahasiswa/dosen tetap produktif saat jaringan kampus padat.
- Membuat file transfer HP-laptop menjadi cepat, sederhana, dan tidak perlu install app di laptop.
- Menyediakan chat lokal sederhana untuk koordinasi satu ruangan.
- Menjaga data tetap lokal dan tidak dikirim ke cloud eksternal.

### 1.6 Non-goals MVP

- Menguatkan sinyal seluler.
- Menggantikan jaringan kampus secara penuh.
- Menjadi mesh network untuk satu gedung/kampus besar.
- Menggantikan WhatsApp secara penuh.
- Cloud storage.
- Akun/login.
- End-to-end encrypted messenger lengkap.
- Support iPhone sebagai host.
- Transfer antar kota/jarak jauh.

---

## 2. Target Pengguna

### 2.1 Primary Persona - Mahasiswa

Kebutuhan:

- Mengirim file dari HP ke laptop saat WhatsApp Web lambat.
- Mengirim foto papan tulis/catatan ke teman.
- Mengambil materi dari teman/dosen tanpa internet.
- Berkoordinasi cepat dalam kelas.

Pain:

- Sinyal buruk ketika kelas ramai.
- Paket data terbatas.
- Wi-Fi kampus lambat atau login captive portal bermasalah.
- File besar gagal dikirim lewat chat/cloud.

### 2.2 Secondary Persona - Ketua Kelas / Asisten Dosen

Kebutuhan:

- Membagikan file ke banyak mahasiswa sekaligus.
- Mengirim pengumuman lokal.
- Mengumpulkan file kecil dari mahasiswa.

Pain:

- Grup WhatsApp ramai dan file tenggelam.
- LMS lambat.
- Semua mahasiswa mengunduh file yang sama dari internet.

### 2.3 Tertiary Persona - Dosen / Panitia Event

Kebutuhan:

- Membagikan materi presentasi.
- Membagikan link lokal/QR untuk peserta.
- Mengumpulkan dokumen dari peserta secara cepat.

Pain:

- Internet venue padat.
- Email/LMS tidak praktis untuk transfer cepat di ruangan.

---

## 3. Core Value Proposition

### 3.1 Functional Value

- Kirim file lokal tanpa internet.
- Join tanpa install app di laptop.
- Chat lokal untuk koordinasi cepat.
- QR join cepat.
- Data tetap berada di perangkat host.

### 3.2 Emotional Value

- Mengurangi panik saat deadline/tugas harus dikirim.
- Memberi rasa kontrol saat jaringan kampus tidak bisa diandalkan.
- Membuat koordinasi kelas terasa lebih lancar dan tenang.

### 3.3 Differentiation

Nadi berbeda dari:

- WhatsApp: tidak bergantung internet/cloud.
- Google Drive: tidak butuh upload/download server.
- LocalSend: lebih spesifik untuk workflow kampus, local room, chat, class hub.
- AirDrop: tidak terbatas ekosistem Apple.
- Bluetooth file transfer: lebih modern, browser-friendly, dan multi-user.

---

## 4. Scope MVP

### 4.1 In Scope

1. Android host membuat local room.
2. QR join untuk client.
3. Browser client membuka web UI dari host.
4. File download dari host ke client.
5. File upload dari client ke host.
6. Chat room lokal sederhana.
7. Daftar client terhubung.
8. Session PIN/token.
9. Riwayat file lokal sederhana.
10. Modern minimal UI.
11. Error/loading/empty states.

### 4.2 Out of Scope MVP

- Multi-host relay.
- Android-to-Android native Nearby Connections.
- iOS native app.
- Desktop native app.
- Cloud backup.
- Login.
- Role management kompleks.
- Enkripsi end-to-end penuh.
- Resume transfer kompleks.
- Background long-running service yang sangat lengkap.

---

## 5. 5 Fitur Utama MVP

### Feature 1 - Local Room Host

#### Deskripsi

Pengguna dapat membuat ruang lokal dari HP Android. Ruang ini menjadi pusat transfer file dan chat.

#### Functional Requirements

- Tombol "Buat Ruang".
- App mencoba membuat Local-only Hotspot.
- App menjalankan local web server.
- App menampilkan status room:
  - Menyiapkan.
  - Siap.
  - Client terhubung.
  - Gagal.
- App menampilkan nama room dan session PIN.
- App menampilkan URL lokal dan QR join.

#### Acceptance Criteria

- User dapat membuat room tanpa internet.
- Jika hotspot/server gagal, error jelas ditampilkan.
- User dapat menutup room.
- Saat room ditutup, server berhenti.

---

### Feature 2 - QR Join dan Browser Client

#### Deskripsi

Client laptop/HP dapat bergabung tanpa install app dengan membuka browser melalui QR/URL.

#### Functional Requirements

- QR berisi URL lokal dan session token/PIN.
- Web client menampilkan halaman Nadi yang responsive.
- Web client meminta PIN jika token tidak valid.
- Web client menampilkan:
  - Nama room.
  - Status koneksi.
  - Upload file.
  - Download file.
  - Chat.

#### Acceptance Criteria

- Laptop yang terhubung ke hotspot host dapat membuka web UI.
- Web UI usable pada desktop dan mobile browser.
- Client tanpa token/PIN tidak bisa masuk.
- Web UI tetap ringan.

---

### Feature 3 - Local File Transfer

#### Deskripsi

Host dan client dapat saling mengirim file secara lokal.

#### Functional Requirements

- Host dapat memilih file untuk dibagikan.
- Client dapat download file dari host.
- Client dapat upload file ke host.
- Tampilkan progress transfer.
- Tampilkan ukuran file.
- Tampilkan status sukses/gagal.
- Simpan file masuk di storage aplikasi atau folder pilihan sesuai permission.

#### Acceptance Criteria

- File kecil dapat dikirim tanpa internet.
- File sedang/besar menampilkan progress.
- Transfer gagal tidak membuat app crash.
- File yang sama tidak menimpa diam-diam tanpa penanganan nama.
- File yang masuk muncul di riwayat.

---

### Feature 4 - Local Chat Room

#### Deskripsi

Client dalam room dapat mengirim pesan teks sederhana untuk koordinasi cepat.

#### Functional Requirements

- Host dan client dapat mengirim pesan.
- Pesan tampil realtime atau near-realtime.
- Pesan memiliki nama pengirim.
- Pesan memiliki waktu lokal.
- Pesan hanya berlaku di room saat ini.

#### Acceptance Criteria

- Pesan pendek terkirim tanpa internet.
- Pesan baru muncul di host dan client.
- Chat tetap ringan walau ada beberapa client.
- Jika WebSocket tidak tersedia, fallback polling bisa dipakai.

---

### Feature 5 - Session Dashboard dan History

#### Deskripsi

Host memiliki dashboard untuk melihat aktivitas room dan file yang pernah dipindahkan.

#### Functional Requirements

- Tampilkan jumlah client.
- Tampilkan daftar file dibagikan.
- Tampilkan daftar file diterima.
- Tampilkan status server dan alamat lokal.
- Tampilkan tombol stop room.
- Tampilkan riwayat transfer lokal.

#### Acceptance Criteria

- Host memahami apa yang sedang terjadi di room.
- Riwayat transfer tersedia setelah room aktif.
- User bisa membuka lokasi file atau membagikan ulang file.

---

## 6. Technical Architecture

### 6.1 Platform

- Android Native.
- Kotlin.
- XML Views.
- Minimum SDK API 26.
- Kotlin DSL.
- Package: `com.danis.nadi`.

### 6.2 Proposed Modules / Packages

```text
com.danis.nadi
  app/
  ui/
    home/
    host/
    history/
    settings/
  network/
    hotspot/
    server/
    websocket/
  file/
  model/
  storage/
  security/
  util/
```

### 6.3 Local Networking

Recommended MVP approach:

- Android host uses `WifiManager.startLocalOnlyHotspot()`.
- Android starts embedded HTTP server on a local port.
- Browser client accesses host URL.
- File upload/download over HTTP.
- Chat over WebSocket or polling.

Fallback approach:

- If Local-only Hotspot is unavailable or unreliable, allow user to connect all devices to the same Wi-Fi and run local server mode.
- Show manual URL and IP address.

### 6.4 Embedded Server

Server responsibilities:

- Serve web client assets.
- Expose room metadata endpoint.
- Expose file list endpoint.
- Handle file upload.
- Handle file download.
- Handle chat send/listen endpoint.
- Validate session token/PIN.

Possible implementation:

- NanoHTTPD for simple HTTP server.
- Ktor server if dependency size and Android compatibility are acceptable.
- Custom `ServerSocket` only if library approach becomes problematic.

For MVP, prefer a simple, stable embedded HTTP server with minimal moving parts.

### 6.5 Chat Transport

Option A - WebSocket:

- Better realtime UX.
- More moving parts.

Option B - HTTP polling:

- Easier MVP.
- Enough for lightweight classroom chat.

Recommendation:

- Start with HTTP polling for MVP.
- Upgrade to WebSocket after file transfer is stable.

### 6.6 File Handling

Host selected files:

- Use Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`).
- Store URI permissions when needed.
- Avoid copying large files unless necessary.

Received files:

- Store inside app-specific external files directory for MVP.
- Later allow user to choose destination folder.

Metadata:

- File ID.
- Display name.
- MIME type.
- Size.
- Source: host/shared/client-upload.
- Created time.
- Local URI/path.
- Transfer status.

### 6.7 Security

MVP security:

- Random room ID.
- Random session token.
- Optional 4-6 digit PIN.
- Token included in QR URL.
- Server rejects requests without valid token/PIN.
- Room is local network only.

Important:

- Do not expose server to public internet intentionally.
- Do not store sensitive files permanently without user awareness.
- Show warning that anyone with room QR/PIN in the local network can join.

Future security:

- Per-client approval.
- Expiring tokens.
- File-level approval.
- Encrypted transfer/session.

### 6.8 Permissions

Likely permissions:

- Nearby/network permissions depending Android version and hotspot APIs.
- Location permission may be required by Wi-Fi APIs on some Android versions.
- Notification permission for transfer status on Android 13+ if background notification is added.
- Storage permissions should be avoided when possible by using Storage Access Framework.

MVP should request permissions only when needed and explain why.

### 6.9 No Cloud Policy

MVP must not depend on:

- Firebase.
- Paid cloud storage.
- Google Maps.
- External server.
- Account/login backend.

All transfer and chat should work locally.

---

## 7. Data Model

### 7.1 RoomSession

| Field | Type | Notes |
| --- | --- | --- |
| sessionId | String | Random local ID |
| roomName | String | Default: Nadi Room |
| hostName | String | Device/user label |
| pin | String | Optional numeric PIN |
| token | String | Secret URL token |
| startedAt | Long | Epoch millis |
| status | String | preparing, active, failed, stopped |
| localUrl | String | Host URL |
| hotspotSsid | String | If available |

### 7.2 ConnectedClient

| Field | Type | Notes |
| --- | --- | --- |
| clientId | String | Random |
| displayName | String | Browser/user-provided |
| joinedAt | Long | Epoch millis |
| lastSeenAt | Long | Epoch millis |
| userAgent | String | Browser info |
| ipAddress | String | Local IP |

### 7.3 TransferItem

| Field | Type | Notes |
| --- | --- | --- |
| transferId | String | Random |
| fileName | String | Display name |
| mimeType | String | File type |
| sizeBytes | Long | File size |
| direction | String | upload/download/shared |
| status | String | pending, running, success, failed |
| progress | Int | 0-100 |
| createdAt | Long | Epoch millis |
| localUri | String | Android URI/path |
| senderName | String | Optional |

### 7.4 ChatMessage

| Field | Type | Notes |
| --- | --- | --- |
| messageId | String | Random |
| senderId | String | Host/client ID |
| senderName | String | Display name |
| text | String | Message text |
| createdAt | Long | Epoch millis |
| status | String | sent/local |

---

## 8. UI/UX Requirements

### 8.1 Design Principles

- The app must be usable immediately on first screen.
- Primary actions must be obvious.
- Avoid technical jargon.
- Use calm premium visuals.
- Make state transitions clear.
- Make failure recoverable.

### 8.2 Visual System

Suggested palette:

```text
Deep Green: #073B32
Nadi Green: #0E7A63
Teal Flow: #2DD4BF
Graphite: #111827
Soft Ink: #4B5563
Mist: #F7FAF9
Surface: #FFFFFF
Line: #DDE7E3
Warning: #B45309
Error: #B91C1C
Success: #047857
```

Typography:

- Clean sans-serif.
- Strong but restrained headings.
- Avoid overly large hero copy in functional screens.

Shape:

- Card radius around 8-12dp.
- Avoid deeply nested cards.
- Use full-width practical sections.

### 8.3 App Screens

#### Home Screen

Purpose:

- Entry point.

Components:

- Brand logo/name.
- Short status copy.
- Primary button: "Buat Ruang".
- Secondary button: "Gabung".
- Recent transfers preview.
- Help/How it works small link.

#### Host Setup Screen

Purpose:

- Prepare room.

Components:

- Room name input.
- Host display name.
- PIN toggle.
- Start room button.
- Permission rationale.

#### Active Room Screen

Purpose:

- Host dashboard.

Components:

- Room status.
- QR code.
- URL copy button.
- Connected clients count.
- Shared files list.
- Uploaded files list.
- Chat preview.
- Stop room button.

#### File Picker / Share Screen

Purpose:

- Add files for client download.

Components:

- Select files button.
- File list.
- Remove file.
- Share status.

#### Chat Screen

Purpose:

- Local chat for room.

Components:

- Message list.
- Input field.
- Send button.
- Client name labels.

#### History Screen

Purpose:

- Local transfer history.

Components:

- File transfer list.
- Search/filter.
- Open file.
- Share again.
- Clear history.

#### Settings Screen

Purpose:

- App preferences and diagnostics.

Components:

- Device name.
- Default room name.
- Advanced local server info.
- Privacy explanation.

### 8.4 Browser Client UI

Browser UI must be simple and responsive.

Pages:

- Join page.
- Room dashboard.
- File upload/download.
- Chat panel.

Browser client copy:

- "Terhubung ke Nadi."
- "Kirim file ke host."
- "Ambil file dari host."
- "Chat lokal."

Browser client should avoid requiring modern heavy frameworks. Plain HTML/CSS/JS is enough for MVP.

---

## 9. Error and Edge States

### 9.1 Hotspot Failed

Possible causes:

- Device/vendor restriction.
- Wi-Fi state conflict.
- Permission missing.
- OS limitation.

User message:

> Ruang lokal belum bisa dibuat di perangkat ini. Coba tutup tethering lain, aktifkan Wi-Fi, lalu coba lagi.

Fallback:

- Same Wi-Fi local server mode.

### 9.2 Client Cannot Join

Possible causes:

- Client not connected to host hotspot.
- Wrong URL.
- Token expired.
- Firewall/browser issue.

User message:

> Pastikan perangkat sudah tersambung ke Wi-Fi Nadi, lalu scan QR lagi.

### 9.3 Transfer Failed

Possible causes:

- Client disconnected.
- File too large.
- Host storage full.
- Browser closed.

Behavior:

- Show failed status.
- Allow retry.
- Do not crash.

### 9.4 Host Leaves App

MVP:

- Keep room while app foreground.
- Warn user that closing room/app may disconnect clients.

Future:

- Foreground service with notification.

### 9.5 Too Many Clients

Behavior:

- Show client count.
- If host struggles, show warning:

> Terlalu banyak perangkat dapat memperlambat transfer. Untuk kelas besar, bagikan file secara bergantian.

---

## 10. Privacy and Safety

### 10.1 Privacy Principles

- No cloud upload by default.
- No account required.
- No file analytics.
- No contact scraping.
- No background location tracking.
- Local room data stays on host.

### 10.2 User-facing Privacy Copy

> Nadi mengirim file lewat jaringan lokal. File tidak diunggah ke cloud Nadi.

### 10.3 Data Retention

MVP:

- Chat history exists while room/session active.
- Transfer history saved locally on host.
- User can clear history.

Future:

- Per-room retention settings.

---

## 11. Implementation Plan

### Phase 0 - Project Setup

- Create Android project:
  - Kotlin.
  - Empty Views Activity.
  - Min SDK API 26.
  - Kotlin DSL.
  - Package `com.danis.nadi`.
- Add app theme and colors.
- Add package structure.
- Add PRD/context docs.
- Ensure build passes.

### Phase 1 - Brand and Shell UI

- Home screen.
- Host setup screen.
- Active room screen skeleton.
- History screen skeleton.
- Modern Nadi visual system.
- No networking yet.

### Phase 2 - Local Server Prototype

- Add embedded HTTP server.
- Serve simple HTML page from Android.
- Show server URL in app.
- Test browser access on same network.

### Phase 3 - Local-only Hotspot

- Implement `startLocalOnlyHotspot`.
- Show hotspot credential.
- Generate join QR.
- Handle failure/fallback state.

### Phase 4 - File Download

- Host selects file.
- Browser lists shared files.
- Browser downloads file.
- Add file metadata model.
- Add transfer status.

### Phase 5 - File Upload

- Browser uploads file to host.
- Host stores file.
- Host shows received files.
- Add progress/status.

### Phase 6 - Chat MVP

- Add local chat endpoint.
- Start with HTTP polling.
- Browser and host can send/read messages.
- Add sender names.

### Phase 7 - Polish and Hardening

- Error states.
- Permission rationale.
- Stop room cleanup.
- Clear history.
- Better responsive browser UI.
- Build/debug/install verification.

### Phase 8 - Future Upgrade Path

- WebSocket realtime chat.
- Foreground service.
- Resume file transfer.
- Nearby Connections Android-to-Android.
- Multi-host/relay.
- Stronger encryption.

---

## 12. Testing Strategy

### 12.1 Automated Tests

- Unit tests:
  - Room token generation.
  - PIN validation.
  - File metadata formatting.
  - MIME/size formatting.
  - Chat message ordering.

- Instrumentation tests:
  - Home screen renders.
  - Host screen state changes.
  - History list renders.

### 12.2 Manual Device Tests

Minimum manual tests:

- App builds and installs.
- Room starts on Android device.
- URL visible.
- Laptop can open browser client.
- Client can download file from host.
- Client can upload file to host.
- Chat message sends from browser to host.
- Chat message sends from host to browser.
- Room stop disconnects client gracefully.
- Permission denied does not crash.

### 12.3 Real-world Scenario Tests

- Test in classroom with 2 clients.
- Test in classroom with 5 clients.
- Test with internet off.
- Test with mobile data off.
- Test file sizes:
  - 100 KB text/PDF.
  - 5 MB PDF/PPT.
  - 50 MB zip/video sample.
- Test host lock screen behavior.

---

## 13. Success Metrics

MVP success:

- User can create room in less than 10 seconds on supported device.
- Laptop can join via browser in less than 30 seconds.
- 5 MB file transfer succeeds without internet.
- Chat message appears within 2 seconds on local network.
- App does not crash when hotspot/server fails.
- User understands what to do from the first screen.

Product success:

- Students use Nadi instead of WhatsApp for HP-to-laptop transfer in bad-signal rooms.
- Class leaders can distribute files without uploading to cloud.
- Users describe Nadi as "the backup when internet dies."

---

## 14. Open Technical Questions

- Which embedded server library is most stable and lightweight for Android Kotlin MVP?
- How consistent is Local-only Hotspot across target Android devices/vendors?
- Can browser clients discover host IP reliably from QR credentials alone?
- Is HTTP polling enough for chat MVP, or should WebSocket be implemented earlier?
- What is the best default storage location for received files?
- How should Nadi handle host app backgrounding/lock screen in MVP?
- How many clients are realistic for one host device before UX degrades?

---

## 15. Future Features

- Android-to-Android native transfer.
- Peer cache.
- Multi-host class mesh.
- Host handover.
- Resume interrupted transfer.
- File request workflow.
- Temporary assignment collection.
- QR attendance/local check-in.
- Offline class board.
- End-to-end encrypted room.
- Desktop helper app.
- iOS client enhancements.

---

## 16. Recommended MVP Definition

The first shippable MVP should be:

> One Android phone creates a Nadi room. A laptop connects to the phone's local network, opens the Nadi browser page, downloads one shared file, uploads one file back, and sends a chat message. All of this works without internet.

Everything else should support this core flow.

