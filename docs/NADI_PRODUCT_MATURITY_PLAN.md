# Nadi Product Maturity Plan

Dokumen ini adalah rencana pematangan Nadi dari MVP teknis yang sudah berjalan menuju produk Android local-first yang stabil, rapi, enak dipakai, aman secara lokal, mudah dirawat, dan siap diuji oleh pengguna nyata.

Dokumen ini melengkapi:

- [NADI_PRD.md](../NADI_PRD.md)
- [NADI_PROJECT_CONTEXT.md](../NADI_PROJECT_CONTEXT.md)
- [NADI_IMPLEMENTATION_BLUEPRINT.md](NADI_IMPLEMENTATION_BLUEPRINT.md)
- [NADI_QA_RELEASE_CHECKLIST.md](NADI_QA_RELEASE_CHECKLIST.md)
- [NADI_BETA_NOTES.md](NADI_BETA_NOTES.md)
- [NADI_SMOKE_TEST_LOG.md](NADI_SMOKE_TEST_LOG.md)

Blueprint implementasi menjawab "bagaimana MVP dibangun". Dokumen ini menjawab "bagaimana MVP dipoles sampai layak menjadi produk matang".

---

## 0. Codex QA Scope

Untuk pekerjaan Codex, definisi "matang" dibatasi pada lingkungan test yang tersedia di workspace/Codex. Codex tidak perlu mengejar QA lintas vendor Android, Safari/iOS, signing produksi, feedback tester, atau testing akhir rilis.

Gate Codex yang cukup:

- Build/debug dan unit test lokal lulus.
- Core flow Android host + browser client lulus pada lingkungan test yang tersedia.
- Browser test memakai browser yang tersedia di aplikasi/lingkungan Codex.
- Dokumentasi smoke test diperbarui bila ada evidence baru.

Area berikut dimiliki owner proyek dan tidak menjadi blocker pekerjaan Codex:

- Matrix vendor Android luas.
- iOS/Safari.
- Release signing.
- Feedback tester.
- Beta/public release testing akhir.

---

## 1. Executive Summary

Nadi sudah berada di titik MVP teknis: Android dapat menjadi host room lokal, client dapat bergabung lewat browser, file dapat diunduh dan diunggah, chat lokal berjalan, QR/URL tersedia, serta mode same-Wi-Fi dan Local-only Hotspot sudah terbukti pada perangkat nyata.

Namun status ini belum sama dengan produk matang. MVP saat ini membuktikan jalur utama bekerja, tetapi masih membutuhkan pematangan pada arsitektur, tampilan, lifecycle Android, reliabilitas jaringan lokal, persistensi data, keamanan lokal, observability, dan QA pada lingkungan test yang disepakati.

Prioritas utama setelah MVP bukan langsung menambah fitur besar. Prioritas yang benar adalah memperkuat fondasi, memecah tanggung jawab codebase, memperjelas UI state, membuat pengalaman host dan browser terasa bersih, lalu menutup risiko nyata: app masuk background, hotspot tidak konsisten, file besar, state hilang, dan perilaku berbeda antar vendor Android.

---

## 2. Current Verified Baseline

Baseline berikut menjadi titik awal perencanaan:

- App Android Kotlin dengan XML Views.
- Package utama: `com.danis.nadi`.
- Embedded HTTP server menggunakan NanoHTTPD.
- Android host dapat membuat room lokal.
- Client browser dapat membuka halaman Nadi melalui QR/URL lokal.
- Token room dipakai untuk akses endpoint browser.
- File host ke client berjalan melalui download endpoint.
- File client ke host berjalan melalui upload endpoint.
- Chat host dan browser berjalan melalui HTTP polling.
- Same-Wi-Fi mode berjalan.
- Local-only Hotspot mode berjalan pada perangkat uji, termasuk pemilihan URL host hotspot yang benar.
- Unit test dasar tersedia untuk token, PIN, room manager, formatter, file resolver, dan server.
- Build debug dan unit test debug pernah lulus.

Baseline ini harus dipertahankan. Setiap refactor atau redesign wajib menjaga flow MVP tetap hidup:

> Buat room lokal, client join lewat browser, download file, upload file, kirim chat, semua tanpa internet.

---

## 3. Product Maturity Definition

Nadi dianggap matang ketika memenuhi delapan kualitas berikut:

1. **Reliable**
   Room tetap stabil selama sesi nyata, termasuk ketika layar terkunci, app berpindah ke background, jaringan lokal berubah, atau transfer berlangsung lama.

2. **Understandable**
   Pengguna awam paham apa yang harus dilakukan tanpa membaca instruksi panjang. Status jaringan, QR, file, chat, dan error harus jelas.

3. **Clean**
   UI terasa modern, ringan, fokus, dan tidak penuh teks teknis. Dashboard aktif harus scannable.

4. **Maintainable**
   Codebase tidak bergantung pada satu Activity besar. Server, room state, file handling, UI rendering, dan hotspot lifecycle memiliki batas tanggung jawab yang jelas.

5. **Local-first**
   Tidak ada cloud dependency untuk core flow. Semua transfer dan chat berjalan lokal.

6. **Safe**
   User tahu siapa saja yang bisa join, file apa yang dibagikan, di mana file masuk disimpan, dan bagaimana room dihentikan.

7. **Tested**
   Ada automated test untuk domain dan server, instrumentation test untuk flow penting bila tersedia, serta manual QA pada lingkungan test Codex. Matrix lintas vendor/browser eksternal dilakukan owner proyek.

8. **Releasable**
   App memiliki checklist teknis, versioning/changelog bila relevan, crash/diagnostic strategy lokal, dan dokumen operasional. Signing produksi dan keputusan rilis akhir dilakukan owner proyek.

---

## 4. Current Gap Analysis

### 4.1 Codebase

Kondisi saat ini:

- `MainActivity` memegang banyak tanggung jawab: UI state, room creation, server start/stop, hotspot permission, dashboard polling, file picker, chat, copy URL, dan render list.
- `NadiHttpServer` sudah fungsional, tetapi browser client masih sangat dekat dengan server implementation.
- Room state masih runtime/in-memory.
- Belum ada layer use case/interactor yang memisahkan keputusan produk dari UI dan server.
- Belum ada persistence untuk history dan metadata.
- Belum ada service khusus untuk menjaga room saat app background.

Risiko:

- Sulit mengubah UI tanpa menyentuh logic room.
- Regression mudah terjadi saat menambah fitur.
- State sesi hilang ketika Activity recreate atau proses dibunuh.
- Flow server/hotspot sulit dites secara terisolasi.

### 4.2 UI/UX

Kondisi saat ini:

- UI MVP sudah cukup untuk membuktikan flow.
- Active room screen masih cenderung linear dan tekstual.
- File list, received files, dan chat masih belum terasa seperti dashboard produk matang.
- Browser client perlu peningkatan visual, empty state, loading state, dan error state.

Risiko:

- Pengguna awam bingung membedakan mode same-Wi-Fi dan hotspot.
- Pengguna tidak tahu apakah room benar-benar aktif.
- Pengguna tidak tahu tindakan berikutnya saat client gagal join.
- Produk terasa prototype meskipun fungsi inti berjalan.

### 4.3 Android Lifecycle

Kondisi saat ini:

- Room hidup selama Activity/server berjalan.
- Belum ada foreground service untuk sesi aktif.
- Belum ada strategi eksplisit untuk lock screen, app switch, low memory, atau process death.

Risiko:

- Transfer gagal saat host membuka app lain.
- Hotspot/server berhenti tanpa copy yang jelas.
- Dashboard tidak sinkron setelah Activity recreate.

### 4.4 Networking

Kondisi saat ini:

- Same-Wi-Fi dan Local-only Hotspot tersedia.
- Host IP discovery sudah diperbaiki untuk memilih interface hotspot yang tepat.
- Client join bergantung pada QR/URL.

Risiko:

- Vendor Android bisa memberi perilaku hotspot berbeda.
- Captive portal Wi-Fi kampus bisa mengganggu same-Wi-Fi mode.
- Port conflict, firewall, dan interface switching perlu feedback yang lebih baik.

### 4.5 File Transfer

Kondisi saat ini:

- Upload/download file sudah berjalan.
- File masuk disimpan di app-specific storage.
- Host dapat memilih file untuk dibagikan.

Risiko:

- Progress transfer belum kuat untuk file sedang/besar.
- Resume transfer belum ada.
- Batal transfer, retry, duplicate name policy, dan storage cleanup belum matang.
- MIME type dan filename edge case perlu terus dijaga.

### 4.6 Chat

Kondisi saat ini:

- Chat lokal via polling sudah cukup untuk MVP.

Risiko:

- Polling bisa boros dan terasa lambat saat client bertambah.
- Belum ada limit retention chat per sesi.
- Belum ada desain message state dan empty state yang matang.

### 4.7 Security and Privacy

Kondisi saat ini:

- Token digunakan pada URL.
- Scope masih jaringan lokal.

Risiko:

- Siapa pun yang melihat QR/URL bisa mencoba masuk.
- Token di URL bisa tersimpan di browser history.
- Belum ada user-facing privacy control untuk retensi file/chat.
- Belum ada room lock, regenerate token, atau remove client.

### 4.8 QA and Release

Kondisi saat ini:

- Unit test dasar ada.
- Smoke test manual perangkat nyata sudah dilakukan.

Risiko:

- Belum ada regression suite untuk flow end-to-end.
- Matrix vendor Android luas dan testing rilis akhir berada di luar scope Codex dan akan dilakukan owner proyek.
- Belum ada CI/release gate formal.

---

## 5. Target Architecture

Target arsitektur tetap sederhana, tetapi lebih terstruktur. Tidak perlu langsung multi-module. Mulai dari package yang bersih dalam modul `app`.

### 5.1 Proposed Package Map

```text
com.danis.nadi
  app/
    NadiApplication.kt
    AppDispatchers.kt
  core/
    model/
    time/
    result/
    validation/
  security/
    TokenGenerator.kt
    PinValidator.kt
    RoomAccessPolicy.kt
  room/
    RoomController.kt
    RoomManager.kt
    RoomSessionStore.kt
    RoomLifecycleService.kt
  file/
    FileStore.kt
    AndroidFileStore.kt
    FileRepository.kt
    FileTransferTracker.kt
    FileNameResolver.kt
    FileSizeFormatter.kt
  network/
    server/
      NadiHttpServer.kt
      ServerController.kt
      BrowserClientAssets.kt
      ApiResponse.kt
    hotspot/
      LocalHotspotManager.kt
      HotspotDiagnostics.kt
    address/
      NetworkAddress.kt
      InterfaceSelector.kt
  history/
    HistoryRepository.kt
    RoomHistoryDao.kt
    TransferHistoryDao.kt
  ui/
    home/
    setup/
    active/
    history/
    settings/
    components/
  diagnostics/
    DiagnosticEvent.kt
    DiagnosticLogStore.kt
```

### 5.2 Architecture Rules

- UI tidak langsung mengatur detail HTTP endpoint.
- Server tidak langsung memutuskan copy UI.
- File storage tidak tahu tentang visual dashboard.
- Hotspot manager hanya mengurus hotspot dan state teknisnya.
- Room controller mengorkestrasi start/stop room, server, hotspot, dan lifecycle.
- Persistence menyimpan metadata, bukan file besar secara tidak perlu.
- Browser client assets dipisah dari string besar di Kotlin.

### 5.3 Room Lifecycle Target

Room aktif harus punya state machine eksplisit:

```text
Idle
  -> Preparing
  -> StartingNetwork
  -> StartingServer
  -> Active
  -> Stopping
  -> Stopped
  -> Failed
```

State ini dipakai oleh:

- Android UI.
- Foreground service notification.
- Server health metadata.
- Diagnostic log.
- Test assertions.

### 5.4 Data Persistence Target

Gunakan dua jenis storage:

- **DataStore** untuk preferensi ringan: default host name, last network mode, privacy setting, onboarding dismissed.
- **Room/SQLite** untuk history: room sessions, transfers, chat summary, client count, timestamps, file metadata.

File fisik tetap dikelola dengan app-specific storage atau SAF URI sesuai sumber file.

---

## 6. UI/UX Maturity Direction

### 6.1 Product Feel

Nadi harus terasa seperti alat kampus yang tenang, cepat, dan jelas. Visual bukan sekadar cantik, tetapi membantu pengguna memahami status room lokal.

Karakter UI:

- Clean.
- Hangat.
- Tidak terlalu teknis.
- Cepat discan.
- Aman dan meyakinkan.
- Cocok untuk layar HP kecil.

### 6.2 Visual System

Rencana visual:

- Gunakan Material Components yang sudah ada, tetapi lebih konsisten.
- Radius kartu maksimal 8dp kecuali komponen brand khusus.
- Hindari nested card berlebihan.
- Gunakan spacing scale: 4, 8, 12, 16, 24, 32.
- Gunakan hierarchy typography yang sederhana:
  - Screen title.
  - Section title.
  - Body.
  - Caption/status.
- Warna utama harus mencerminkan Nadi sebagai "aliran lokal": segar, modern, tidak terlalu gelap, dan tidak terasa template.
- Status color harus jelas:
  - Aktif.
  - Menunggu.
  - Error.
  - Transfer berjalan.
  - Transfer selesai.

### 6.3 Host App Screens

#### Home

Tujuan:

- Memulai room secepat mungkin.
- Menjelaskan nilai produk tanpa copy panjang.
- Menampilkan riwayat ringkas bila sudah ada.

Komponen target:

- Header brand Nadi.
- Primary action: Buat room.
- Secondary action: Riwayat.
- Recent sessions.
- Status local-first singkat.

Acceptance:

- Dalam 3 detik user tahu tombol utama.
- Tidak ada istilah teknis di tampilan utama.

#### Setup Room

Tujuan:

- User memilih nama room, nama host, dan mode jaringan.
- Copy harus membantu memilih mode.

Komponen target:

- Room name input.
- Host name input.
- Segmented control untuk mode:
  - Hotspot lokal.
  - Wi-Fi yang sama.
- Permission explanation inline hanya saat dibutuhkan.
- Start button dengan loading state.

Acceptance:

- User paham kapan memilih hotspot dan kapan memilih Wi-Fi sama.
- Permission denial memberi jalan keluar.

#### Active Room Dashboard

Tujuan:

- Menjadi pusat kendali host.
- QR, URL, client, transfer, dan chat terlihat tanpa terasa penuh.

Struktur target:

- Status bar ringkas: Aktif, mode jaringan, jumlah client.
- QR card jelas dan besar.
- Join URL dengan copy action.
- Quick actions:
  - Tambah file.
  - Salin link.
  - Stop room.
- Transfer area:
  - Dibagikan ke client.
  - Diterima dari client.
  - Progress/retry/error state.
- Chat preview dengan entry field.
- Diagnostics collapsed untuk detail teknis.

Acceptance:

- User tahu room aktif hanya dengan melihat bagian atas.
- QR dapat discan dari jarak meja kelas.
- Stop room tidak mudah kepencet tanpa konfirmasi ringan.

#### History

Tujuan:

- User dapat melihat file yang pernah diterima/dibagikan.
- History tidak membuat produk terasa seperti cloud.

Komponen target:

- Daftar sesi.
- Filter: semua, diterima, dibagikan.
- Open file.
- Share again.
- Delete metadata.
- Clear local history.

Acceptance:

- User tahu data tetap lokal.
- File yang hilang dari storage ditandai dengan jelas.

#### Settings

Tujuan:

- Menampung preferensi dan informasi lokal-first.

Komponen target:

- Default host name.
- Default network mode.
- Storage location information.
- Privacy/data retention controls.
- About Nadi.
- Diagnostics export untuk debugging.

### 6.4 Browser Client

Browser client harus terasa seperti bagian dari Nadi, bukan halaman debug.

Target:

- Header room name dan status connected.
- Upload panel dengan drag/select file.
- Download list dengan file size dan action jelas.
- Chat panel ringan.
- Empty state yang ramah.
- Error state untuk token salah, room mati, upload gagal, dan network putus.
- Responsif untuk laptop dan HP.

Acceptance:

- Client bisa upload/download/chat tanpa tutorial.
- Browser refresh tidak membuat user tersesat.
- Halaman tetap ringan tanpa framework besar.

---

## 7. Roadmap From MVP to Mature Product

### Phase A: Stabilization and Refactor Foundation

Objective:

Membuat fondasi codebase lebih aman untuk pengembangan lanjutan tanpa mengubah behavior utama.

Scope:

- Pecah tanggung jawab `MainActivity`.
- Introduce `RoomController` untuk start/stop room.
- Introduce state model untuk UI active room.
- Pisahkan browser client HTML/CSS/JS dari string Kotlin besar.
- Rapikan response helper server.
- Tambah regression unit test untuk endpoint utama.

Deliverables:

- `MainActivity` lebih tipis.
- Room start/stop flow tetap sama.
- Browser client assets lebih mudah diedit.
- Unit test server mencakup room, files, upload, download, chat, invalid token.

Acceptance gate:

- `assembleDebug` lulus.
- `testDebugUnitTest` lulus.
- Same-Wi-Fi smoke tetap lulus.
- Hotspot smoke tetap lulus minimal pada perangkat utama.

### Phase B: UI/UX Redesign Host App

Objective:

Mengubah tampilan MVP menjadi dashboard produk yang clean dan percaya diri.

Scope:

- Redesign Home.
- Redesign Setup Room.
- Redesign Active Room Dashboard.
- Buat komponen reusable untuk status, section, transfer row, empty state, dan error state.
- Perbaiki copy.
- Perbaiki layout kecil dan landscape dasar.

Deliverables:

- Layout XML baru atau refactor layout existing.
- Style/color/type scale konsisten.
- Empty/loading/error states jelas.

Acceptance gate:

- Tidak ada text clipping pada layar kecil.
- Semua action utama dapat dijangkau.
- QR tetap besar dan mudah discan.
- UX tetap berfungsi tanpa internet.

### Phase C: Persistence and History

Objective:

Menyimpan metadata penting agar produk terasa berguna setelah sesi selesai.

Scope:

- DataStore untuk preferensi.
- Room/SQLite untuk session history dan transfer metadata.
- History screen.
- Data retention controls.
- Migration-safe schema awal.

Deliverables:

- Riwayat room lokal.
- Riwayat file diterima/dibagikan.
- Preferensi host name dan mode jaringan tersimpan.
- Clear history.

Acceptance gate:

- App restart tidak menghapus history.
- File yang tidak lagi tersedia ditandai.
- Tidak ada file user yang dihapus diam-diam.

### Phase D: Transfer Reliability and Progress

Objective:

Membuat transfer file terasa dapat dipercaya untuk penggunaan kelas nyata.

Scope:

- Progress indicator untuk upload/download.
- Transfer status: queued, running, completed, failed, canceled.
- Retry untuk transfer gagal bila memungkinkan.
- Duplicate filename policy.
- File size limit dan copy error yang jelas.
- Cleanup file upload temporary.

Deliverables:

- Transfer tracker.
- UI progress di host dan browser.
- Better server error response.

Acceptance gate:

- File 5 MB, 50 MB, dan 100 MB diuji.
- Upload gagal tidak meninggalkan state palsu.
- User melihat progress dan hasil transfer.

### Phase E: Foreground Service and Lifecycle Hardening

Objective:

Menjaga room tetap hidup saat host memakai HP secara normal.

Scope:

- Foreground service untuk active room.
- Notification dengan status room dan stop action.
- Activity reconnect ke session aktif.
- Handling rotation/recreate.
- Handling app background dan screen lock.

Deliverables:

- `RoomLifecycleService`.
- Notification channel.
- UI reconnect state.
- Lifecycle test plan.

Acceptance gate:

- Room tetap aktif saat screen lock selama durasi uji.
- Transfer tidak langsung gagal saat host membuka app lain.
- Stop room dari notification bekerja.

### Phase F: Network and Hotspot Hardening

Objective:

Mengurangi kegagalan join di lingkungan nyata.

Scope:

- Interface selector yang lebih eksplisit.
- Diagnostics untuk IP, interface, port, hotspot state.
- Fallback copy untuk same-Wi-Fi.
- Port retry policy.
- Client join troubleshooting page.

Deliverables:

- Network diagnostics panel.
- Better error messages.
- Diagnostics dan QA pada lingkungan test Codex.

Acceptance gate:

- Pengujian multi-vendor Android ditangani owner proyek.
- Same-Wi-Fi dan hotspot punya instruksi berbeda yang jelas.
- Port conflict ditangani.

### Phase G: Browser Client Polish

Objective:

Membuat browser client terasa siap dipakai oleh mahasiswa/dosen tanpa install app.

Scope:

- Responsive layout laptop dan HP.
- Upload progress.
- Download state.
- Chat layout lebih rapi.
- Reconnect/room stopped state.
- Token invalid page yang jelas.

Deliverables:

- Browser UI assets terpisah.
- JS client lebih modular.
- Browser smoke tests manual.

Acceptance gate:

- Browser check memakai browser yang tersedia di aplikasi/lingkungan Codex. Safari/iOS bukan target.
- Refresh browser tetap dapat memuat state room.
- Error state tidak blank.

### Phase H: Security and Privacy Hardening

Objective:

Membuat akses lokal lebih aman tanpa mengorbankan kemudahan join.

Scope:

- Room token regeneration.
- Optional PIN confirmation.
- Visible client list.
- Remove/kick client bila feasible.
- Room lock.
- Privacy copy di settings dan active room.
- Token handling review.

Deliverables:

- Room access policy.
- User-facing privacy surface.
- Security test checklist.

Acceptance gate:

- Invalid token selalu ditolak.
- User tahu siapa saja yang pernah join.
- User bisa menghentikan akses dengan stop room atau regenerate token.

### Phase I: Testing, CI, and Release Engineering

Objective:

Membuat kualitas terjaga saat fitur bertambah.

Scope:

- CI untuk build dan unit test.
- Lint/static checks.
- Instrumentation test untuk flow utama.
- Manual QA template.
- Release signing plan dikelola owner proyek.
- Versioning dan changelog.

Deliverables:

- GitHub Actions workflow.
- QA checklist.
- Release checklist.
- Version naming policy.

Acceptance gate:

- Pull request tidak boleh merge bila build/test gagal.
- Release candidate punya checklist lengkap.
- Smoke test wajib terdokumentasi.

### Phase J: Beta Readiness

Objective:

Menyiapkan Nadi untuk digunakan oleh pengguna nyata terbatas.

Scope:

- Beta feedback form/process dikelola owner proyek.
- Known issues page.
- In-app diagnostic export.
- Performance pass.
- Copy polish final.
- App icon/adaptive icon bila belum matang.

Deliverables:

- Beta APK/AAB.
- Beta notes.
- Feedback triage board.

Acceptance gate:

- Minimal 5 sampai 10 user mencoba skenario kelas kecil.
- Tidak ada blocker pada create room, join, upload, download, chat.
- Known issues punya mitigasi.

---

## 8. Prioritized Backlog

### P0: Must Fix Before Serious Beta

- Pecah tanggung jawab `MainActivity`.
- Buat `RoomController` dan state machine room.
- Foreground service untuk active room.
- Persistence untuk metadata history dasar.
- Progress transfer minimal.
- Better error states untuk hotspot/join/upload.
- Browser client dipisah dari server Kotlin string.
- Regression tests endpoint server.
- Manual QA pada mode jaringan yang tersedia di lingkungan test Codex.

### P1: Should Fix for Product Confidence

- Active dashboard redesign.
- Browser client redesign.
- Client list dan last seen.
- Room stopped/reconnect browser state.
- Transfer retry/cancel.
- Duplicate filename policy yang jelas.
- Diagnostics export.
- CI build/test.
- Release checklist.

### P2: Nice Later

- WebSocket chat.
- Resume transfer.
- File request workflow.
- Room lock/PIN advanced.
- Wi-Fi Direct exploration.
- QR attendance/local check-in.
- Offline class board.
- Multi-host discovery.

---

## 9. Testing Strategy

### 9.1 Automated Tests

Unit tests:

- Token generation and validation.
- PIN validation.
- Room state transitions.
- Transfer metadata.
- File name resolution.
- File size formatting.
- Server endpoint routing.
- Invalid token rejection.
- JSON response contract.

Integration-style local tests:

- Start room state.
- Add shared file.
- Upload metadata.
- Chat ordering.
- Client touch/last seen.

Instrumentation tests:

- Home to setup to active room.
- Stop room.
- Permission denied copy path.
- Activity recreation with active session.

### 9.2 Manual QA Matrix

Devices:

- Device Android yang tersedia di lingkungan test Codex.
- Matrix Android versi/vendor tambahan dilakukan owner proyek.

Network modes:

- Local-only Hotspot.
- Same Wi-Fi home router.
- Same Wi-Fi campus-like network.
- No internet.
- Internet on but unstable.
- Captive portal Wi-Fi.

Browsers:

- Browser yang tersedia di aplikasi/lingkungan Codex.
- Safari/iOS bukan target proyek ini.

File sizes:

- 10 KB.
- 1 MB.
- 5 MB.
- 50 MB.
- 100 MB.
- File name with spaces.
- File name with duplicate.
- PDF, image, ZIP, unknown extension.

Scenarios:

- Host locks screen during room.
- Host switches app during transfer.
- Client refreshes browser.
- Client joins after room already has files.
- Token invalid.
- Room stopped while client page open.
- Hotspot permission denied.
- Port conflict.
- Storage low.

### 9.3 Performance Targets

Initial targets:

- Room creation visible feedback: under 1 second.
- Room active under 10 seconds on supported device.
- Browser first load after scan: under 5 seconds on local network.
- Chat message visible: under 2 seconds with polling.
- 5 MB file transfer succeeds consistently.
- 50 MB file transfer succeeds without app crash.
- No ANR during upload/download.

---

## 10. Release Readiness Checklist

Before beta:

- Build debug and release variant succeed.
- Unit tests pass.
- Manual same-Wi-Fi smoke passes.
- Manual hotspot smoke passes.
- Browser upload/download/chat passes.
- App background/lock behavior documented.
- Known limitations documented.
- Privacy copy present.
- Version name updated.
- Changelog written.

Before public release:

- Signed release configured.
- Crash-free beta sessions acceptable.
- QA matrix target device dikelola owner proyek.
- History and data retention behavior clear.
- Release notes explain local-first limitation.
- No cloud dependency in core flow.
- No debug-only endpoint exposed unnecessarily.

---

## 11. Risk Register

### Risk 1: Local-only Hotspot differs by vendor

Impact:

- Client cannot join even when app says room active.

Mitigation:

- Keep same-Wi-Fi fallback.
- Show hotspot SSID/password/status clearly.
- Add diagnostics.
- Maintain vendor QA notes.

### Risk 2: Android kills background server

Impact:

- Transfer fails mid-session.

Mitigation:

- Foreground service.
- Notification stop action.
- Lifecycle reconnect.
- Battery optimization guidance only if truly needed.

### Risk 3: Large file memory pressure

Impact:

- App crash or server stalls.

Mitigation:

- Stream file, avoid loading whole file.
- Add file size tests.
- Add transfer progress and cleanup.

### Risk 4: UI becomes too technical

Impact:

- Users do not trust or understand the app.

Mitigation:

- Keep diagnostics collapsed.
- Use user-facing copy.
- Test with non-developer users.

### Risk 5: Scope creep before beta

Impact:

- MVP core regresses while advanced features half-finished.

Mitigation:

- Follow P0/P1/P2.
- No WebSocket/resume/mesh until lifecycle, persistence, UI, and QA are stable.

### Risk 6: Security expectations misunderstood

Impact:

- User assumes transfer is private beyond local room guarantees.

Mitigation:

- Clear local-first privacy copy.
- Explain that anyone with QR/link on local network can join.
- Provide stop/regenerate/lock controls over time.

---

## 12. Definition of Done

### MVP Hardening Done

- Current MVP flow still works.
- MainActivity responsibility reduced.
- Room lifecycle state is explicit.
- Browser assets are editable outside server logic.
- Server endpoint regression tests pass.
- Manual same-Wi-Fi and hotspot smoke pass.

### Product Polish Done

- Home, setup, active room, history, and browser UI feel consistent.
- Empty, loading, success, and error states exist.
- QR and URL join are obvious.
- File transfer progress is visible.
- Chat is usable without visual clutter.

### Beta Done

- Foreground service keeps active room reliable.
- History and preferences persist.
- QA pada lingkungan Codex punya evidence; matrix vendor luas ditangani owner proyek.
- CI build/test exists.
- Known issues are documented.
- Beta users can complete create/join/upload/download/chat without guidance.

### Production Ready

- Release checklist passes.
- User privacy and data retention are clear.
- App memiliki versioning yang jelas; release signing produksi ditangani owner proyek.
- Regression suite protects the core flow.
- Product copy and visual polish feel finished.
- No critical blocker remains in P0.

---

## 13. Recommended Next Sprint

The next sprint should be:

**Sprint 1: Stabilization and Product Shell**

Goals:

- Keep current MVP behavior working.
- Extract `RoomController`.
- Introduce room lifecycle state.
- Move browser assets out of server string.
- Add endpoint regression tests.
- Start Active Room dashboard redesign.

Why this order:

- It protects the working MVP before visual and product expansion.
- It makes future UI polish safer.
- It creates a clean place for foreground service, history, and diagnostics later.

Suggested sprint output:

- One refactor PR for architecture foundation.
- One UI PR for active room dashboard polish.
- One test PR for server and room lifecycle regression.

---

## 14. Planning Maintenance Rules

- Update this document when a phase is completed or deliberately changed.
- Do not add advanced features to P0 unless they protect the core MVP flow.
- Keep PRD as product truth, blueprint as implementation path, and this document as maturity roadmap.
- Every sprint should name which maturity pillar it improves.
- Every release candidate should reference the release readiness checklist above.
