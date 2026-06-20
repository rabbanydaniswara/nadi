# Nadi Implementation Blueprint

Dokumen ini adalah peta perencanaan utama untuk membangun Nadi secara bertahap, disiplin, dan sesuai PRD. Gunakan dokumen ini bersama:

- [NADI_PROJECT_CONTEXT.md](../NADI_PROJECT_CONTEXT.md)
- [NADI_PRD.md](../NADI_PRD.md)

Tujuan dokumen ini bukan mengganti PRD, tetapi menerjemahkan PRD menjadi strategi eksekusi teknis yang dapat diikuti Codex atau developer manusia dari satu sesi kerja ke sesi berikutnya.

---

## 1. North Star

### 1.1 Definisi MVP yang benar

Nadi MVP dianggap berhasil ketika:

> Satu HP Android membuat room lokal. Laptop atau HP lain terhubung ke jaringan lokal tersebut, membuka halaman Nadi lewat browser, mengunduh satu file dari host, mengunggah satu file ke host, dan mengirim satu pesan chat. Semua berjalan tanpa internet.

Semua keputusan implementasi harus mendekatkan project ke flow ini.

### 1.2 Hal yang tidak boleh dikorbankan

- Local-first: file dan chat tidak bergantung pada cloud.
- Browser client: laptop tidak perlu install aplikasi.
- Android host: perangkat Android adalah pusat room MVP.
- UX jelas: user awam harus tahu apa yang sedang terjadi.
- Error recoverable: kegagalan hotspot/server/transfer tidak boleh terasa buntu.
- Scope tajam: jangan membangun mesh, akun, cloud, enkripsi kompleks, atau realtime berat sebelum MVP inti selesai.

### 1.3 Prinsip produk

- Nadi bukan penguat sinyal.
- Nadi adalah jalur lokal saat internet tidak bisa diandalkan.
- Nadi harus terasa tenang, modern, dan dapat dipercaya.
- Permukaan UI tidak boleh terlalu teknis.
- Detail teknis seperti IP, port, token, dan server boleh muncul di mode bantuan/diagnostik, bukan sebagai pengalaman utama.

---

## 2. Current State Audit

Audit awal pada 2026-06-20:

- Project Android dasar sudah ada.
- Package: `com.danis.nadi`.
- Min SDK: 26.
- UI: XML Views.
- App masih template awal dengan `MainActivity` dan layout "Hello World".
- Dokumen `NADI_PRD.md` dan `NADI_PROJECT_CONTEXT.md` sudah tersedia.
- Belum ada struktur package domain Nadi.
- Belum ada room state, embedded server, hotspot, QR, file transfer, chat, atau history.
- Repo lokal saat audit tidak memiliki metadata `.git`, jadi perubahan perlu diverifikasi lewat file system dan build, bukan git status.

Implikasi:

- Fase pertama harus membangun fondasi project dan shell UI.
- Jangan mulai dari hotspot atau transfer file sebelum domain model, navigation sederhana, dan state room dasar tersedia.
- Karena belum ada arsitektur mapan, setiap abstraksi awal harus kecil, mudah diganti, dan sesuai PRD.

---

## 3. Execution Strategy

### 3.1 Urutan besar

Bangun dari yang paling mudah diverifikasi menuju yang paling bergantung perangkat:

1. Brand, shell UI, dan state dummy.
2. Domain model dan in-memory room manager.
3. Embedded HTTP server yang dapat melayani browser client.
4. Browser UI ringan dari server Android.
5. Token/PIN dan room metadata endpoint.
6. Shared file list dan download.
7. Upload file dari browser ke host.
8. Chat via HTTP polling.
9. Local-only Hotspot dan QR join.
10. History, polish, error states, dan hardening.

Alasan urutan:

- Server dan browser client dapat dites pada same-Wi-Fi sebelum hotspot selesai.
- File transfer dapat dikembangkan tanpa bergantung penuh pada Local-only Hotspot.
- Hotspot adalah area paling rawan vendor/device, jadi perlu fallback path yang sudah matang.

### 3.2 Cara memilih implementasi

Pilih solusi yang:

- Stabil di Android API 26+.
- Mudah dibaca dan dipelihara.
- Tidak menambah cloud/backend.
- Tidak membuat UI tergantung pada networking nyata terlalu awal.
- Dapat diverifikasi dengan unit test, instrumentation test, atau manual test yang jelas.

Hindari solusi yang:

- Membuat server terlalu kompleks sebelum file transfer stabil.
- Mengunci project ke eksperimen vendor tertentu.
- Menambah dependency besar tanpa kebutuhan MVP yang kuat.
- Membuat chat realtime lebih penting daripada transfer file.

---

## 4. Target Architecture

### 4.1 Package map

Target struktur package:

```text
com.danis.nadi
  ui/
    home/
    hostsetup/
    room/
    history/
    settings/
  model/
  room/
  network/
    hotspot/
    server/
  file/
  storage/
  security/
  util/
```

Makna package:

- `ui`: Activity/Fragment/ViewModel/adapter untuk layar.
- `model`: data class murni untuk `RoomSession`, `ConnectedClient`, `TransferItem`, `ChatMessage`.
- `room`: state machine room, orchestration, in-memory session.
- `network.hotspot`: wrapper Android Local-only Hotspot dan fallback same-Wi-Fi.
- `network.server`: embedded HTTP server, route handler, browser asset serving.
- `file`: SAF, metadata file, display name, MIME, size formatting.
- `storage`: app-specific file storage dan persistence ringan metadata.
- `security`: token, PIN, validation.
- `util`: helper kecil lintas package.

### 4.2 Dependency direction

Dependency harus mengalir seperti ini:

```text
ui -> room -> network/server
ui -> room -> network/hotspot
ui -> file/storage
network/server -> room/file/security
room -> model/security
file/storage -> model
```

Aturan:

- `model` tidak boleh bergantung pada Android UI.
- `security` harus bebas UI.
- `network.server` tidak boleh memanggil UI langsung.
- UI mengamati state, bukan menjadi sumber business logic server.
- Server route memanggil service/manager kecil, bukan memodifikasi Activity.

### 4.3 State ownership

Untuk MVP, gunakan satu sumber kebenaran runtime:

- `RoomController` atau `RoomManager` untuk room aktif.
- State awal boleh in-memory.
- History transfer dapat disimpan lokal setelah fitur transfer stabil.

State utama:

- Current `RoomSession`.
- List `ConnectedClient`.
- List `TransferItem` shared/received.
- List `ChatMessage`.
- Server status.
- Hotspot status.

### 4.4 Threading and async

Prinsip:

- UI thread hanya untuk render dan interaksi.
- Server dan file IO berjalan di background.
- Gunakan coroutine bila sudah ada lifecycle yang jelas.
- Untuk HTTP server callback berbasis thread, bridge ke manager dengan sinkronisasi sederhana.

Risiko:

- Race antara stop room dan request transfer.
- File stream bocor saat client disconnect.
- Activity lifecycle menghentikan state tanpa cleanup server.

Mitigasi:

- Buat API eksplisit `startRoom()`, `stopRoom()`, `addSharedFile()`, `receiveUpload()`, `sendMessage()`.
- Pastikan `stopRoom()` idempotent.
- Tutup stream dengan `use`.
- Simpan state server di object yang tidak tergantung view instance.

---

## 5. Feature Breakdown

### 5.1 Feature A: Brand and App Shell

Goal:

- Aplikasi langsung terasa seperti Nadi, bukan template Android.
- User melihat aksi utama: "Buat Ruang" dan "Gabung".

Deliverables:

- Colors sesuai PRD.
- Strings dasar.
- Home screen usable.
- Host setup skeleton.
- Active room skeleton.
- History skeleton.

Acceptance gate:

- App build sukses.
- Launch screen bukan "Hello World".
- Ada CTA "Buat Ruang".
- Ada state empty/recent transfer sederhana.
- Tidak ada istilah teknis berlebihan di home.

Implementation notes:

- Tetap XML Views.
- Boleh mulai single Activity dengan beberapa section/view state.
- Jangan membuat navigation graph kompleks jika belum perlu.

### 5.2 Feature B: Domain Models and Room State

Goal:

- Semua fitur berbicara dengan model yang konsisten.

Deliverables:

- `RoomSession`
- `ConnectedClient`
- `TransferItem`
- `ChatMessage`
- Enum/sealed status secukupnya.
- Generator token/session ID.
- PIN validation.
- In-memory `RoomManager`.

Acceptance gate:

- Unit test untuk token random, PIN validation, dan ordering chat.
- Room dapat berpindah status `preparing -> active -> stopped`.
- Stop room aman dipanggil lebih dari sekali.

Implementation notes:

- Gunakan `data class`.
- Untuk MVP, `Long` epoch millis cukup.
- Hindari database sampai ada kebutuhan history nyata.

### 5.3 Feature C: Embedded Server Prototype

Goal:

- Android host dapat melayani halaman browser sederhana pada local network.

Deliverables:

- Dependency embedded HTTP server dipilih.
- Server start/stop dari app.
- Endpoint health.
- Endpoint room metadata.
- Static browser HTML/CSS/JS sederhana.

Candidate:

- NanoHTTPD sebagai pilihan awal karena ringan dan cocok untuk HTTP sederhana.
- Ktor hanya jika kebutuhan routing/streaming menjadi lebih kompleks dan ukuran dependency diterima.

Acceptance gate:

- Server start pada port lokal.
- App menampilkan URL.
- Browser pada jaringan yang sama dapat membuka halaman Nadi.
- Stop room menghentikan server.
- Request tanpa token valid ditolak untuk endpoint room.

Suggested endpoints:

```text
GET  /health
GET  /?token=...
GET  /api/room?token=...
GET  /api/files?token=...
GET  /api/chat?token=...&after=...
POST /api/chat?token=...
POST /api/upload?token=...
GET  /api/download/{fileId}?token=...
```

### 5.4 Feature D: Browser Client MVP

Goal:

- Client browser dapat join, melihat room, upload/download, dan chat.

Deliverables:

- Plain HTML/CSS/JS served from Android.
- Responsive layout desktop/mobile.
- Token read from URL.
- Friendly locked/error state if token invalid.
- Polling for room metadata, file list, and chat.

Acceptance gate:

- Browser UI usable pada desktop width dan mobile width.
- Tidak butuh framework berat.
- JS failure menampilkan pesan sederhana.
- Polling tidak terlalu agresif.

Polling recommendation:

- Room metadata: 3-5 seconds.
- Chat: 1-2 seconds while page active.
- File list: 3-5 seconds or after upload.

### 5.5 Feature E: File Download

Goal:

- Host memilih file dan client browser dapat mengunduhnya.

Deliverables:

- SAF file picker.
- Shared file metadata.
- Browser file list.
- Download endpoint streaming file.
- Display file name, size, MIME/status.

Acceptance gate:

- File 100 KB dan 5 MB dapat diunduh.
- Nama file tampil benar.
- MIME type dikirim jika tersedia.
- File ID tidak mengekspos path mentah.
- Transfer gagal tidak crash.

Implementation notes:

- Simpan URI permission jika perlu.
- Jangan copy file besar hanya untuk share jika stream dari URI cukup.
- Gunakan unique file ID.

### 5.6 Feature F: File Upload

Goal:

- Browser client dapat mengirim file ke host Android.

Deliverables:

- Multipart upload endpoint.
- Store upload di app-specific external files directory.
- Metadata masuk ke received list/history.
- Browser menampilkan status upload.
- Host dashboard menampilkan file diterima.

Acceptance gate:

- Upload 100 KB dan 5 MB sukses.
- Nama konflik tidak overwrite diam-diam.
- Storage full/error ditangani dengan status gagal.
- Browser mendapat response jelas.

File naming rule:

- Jika nama sudah ada, gunakan suffix seperti `filename (1).ext`.
- Metadata tetap menyimpan display name asli bila berguna.

### 5.7 Feature G: Local Chat via Polling

Goal:

- Host dan browser client bisa bertukar pesan pendek.

Deliverables:

- Chat send endpoint.
- Chat list endpoint dengan cursor `after`.
- Host chat UI atau preview aktif.
- Browser chat panel.
- Sender name dan waktu lokal.

Acceptance gate:

- Pesan browser muncul di host.
- Pesan host muncul di browser.
- Pesan baru muncul dalam 2 detik pada local network.
- Empty chat state jelas.
- Input kosong tidak terkirim.

Implementation notes:

- Batasi panjang pesan, misalnya 1000 karakter.
- Escape render HTML di browser.
- Keep in-memory untuk MVP, persist hanya jika dibutuhkan.

### 5.8 Feature H: Local-only Hotspot and QR Join

Goal:

- Host membuat jaringan lokal dan QR join yang praktis.

Deliverables:

- Wrapper `WifiManager.startLocalOnlyHotspot()`.
- Runtime permission flow jika diperlukan.
- Status preparing/active/failed.
- QR berisi URL join dengan token.
- Fallback same-Wi-Fi local server mode.

Acceptance gate:

- Pada device supported, hotspot starts.
- SSID/password ditampilkan jika API mengizinkan.
- QR dapat discan client.
- Jika hotspot gagal, app menampilkan langkah lanjut manusiawi.
- Same-Wi-Fi mode tetap bisa dipakai untuk debugging dan device unsupported.

Important risk:

- Local-only Hotspot behavior bervariasi antar vendor Android.
- Browser client perlu tahu cara connect ke hotspot sebelum URL berguna.
- Host IP pada Local-only Hotspot mungkin perlu dideteksi dari network interface.

Mitigation:

- Implement same-Wi-Fi server first.
- Buat diagnostic view untuk IP/port.
- Jangan menyembunyikan fallback.

### 5.9 Feature I: History and Storage

Goal:

- Host punya riwayat transfer lokal sederhana.

Deliverables:

- Transfer history list.
- Clear history.
- Open received file if possible.
- Share again flow later.

Acceptance gate:

- Transfer sukses masuk history.
- History tetap ada setelah app restart jika persistence sudah diterapkan.
- User bisa menghapus history.
- Chat tetap ephemeral kecuali ada keputusan lain.

Implementation notes:

- Untuk MVP awal, mulai in-memory.
- Naik ke DataStore/Room database setelah schema metadata stabil.
- Jangan simpan isi chat permanen tanpa kebutuhan jelas.

### 5.10 Feature J: Polish and Hardening

Goal:

- MVP tidak hanya berfungsi, tetapi terasa aman dipakai.

Deliverables:

- Loading/empty/error states.
- Permission rationale.
- Stop room cleanup.
- Friendly client disconnect state.
- Basic transfer progress.
- Visual consistency.

Acceptance gate:

- Denied permission tidak crash.
- Server failed tidak crash.
- Upload/download failed memberi status jelas.
- User tahu room sedang aktif atau sudah berhenti.
- App build dan manual smoke test lulus.

---

## 6. Roadmap by Phase

### Phase 0: Foundation Audit and Hygiene

Status target:

- Baseline project dipahami.
- Dokumen planning ada.
- Build baseline diketahui.

Tasks:

- Audit Gradle, manifest, MainActivity, layout.
- Jalankan build baseline.
- Catat blocker environment.

Exit gate:

- `./gradlew.bat assembleDebug` berhasil atau kegagalan tercatat jelas dengan penyebab.

### Phase 1: Brand Shell UI

Tasks:

- Tambah color palette.
- Tambah strings brand.
- Ubah home screen dari template ke Nadi home.
- Buat visual CTA "Buat Ruang" dan "Gabung".
- Tambah recent transfer empty state.

Exit gate:

- Launch screen usable.
- Build lulus.
- Screenshot/manual inspection menunjukkan UI bukan template.

### Phase 2: Room State Skeleton

Tasks:

- Tambah model domain.
- Tambah token/PIN generator.
- Tambah room manager in-memory.
- Hubungkan tombol "Buat Ruang" ke state preparing/active dummy.
- Tampilkan active room skeleton.

Exit gate:

- Unit test domain lulus.
- UI dapat masuk/keluar active room tanpa server nyata.

### Phase 3: Same-Wi-Fi Server Prototype

Tasks:

- Pilih dan tambah HTTP server dependency.
- Implement start/stop server.
- Serve `/health` dan halaman browser statis.
- Tampilkan local URL.

Exit gate:

- Browser pada jaringan yang sama bisa membuka halaman.
- Stop room menghentikan server.
- Build lulus.

### Phase 4: Tokenized Browser Room

Tasks:

- Tambah session token.
- QR generator.
- Room metadata endpoint.
- Browser page membaca token.
- Invalid token state.

Exit gate:

- URL token valid membuka room.
- Endpoint protected menolak token salah.
- QR tampil di active room screen.

### Phase 5: Download Flow

Tasks:

- SAF file picker.
- Shared file metadata.
- File list endpoint.
- Download endpoint.
- Browser download UI.

Exit gate:

- Client download file host sukses untuk 100 KB dan 5 MB.
- File error tidak crash.

### Phase 6: Upload Flow

Tasks:

- Multipart upload.
- Store received files.
- Received list host.
- Browser upload progress/status.

Exit gate:

- Client upload file sukses untuk 100 KB dan 5 MB.
- File masuk daftar host.
- Conflict name handled.

### Phase 7: Chat MVP

Tasks:

- Chat message model wiring.
- Send/list endpoint.
- Browser polling chat.
- Host chat preview/input.

Exit gate:

- Host dan browser saling kirim pesan.
- Pesan muncul dalam 2 detik di local network.

### Phase 8: Local-only Hotspot

Tasks:

- Implement hotspot wrapper.
- Permission rationale.
- Status hotspot.
- QR join final dengan URL server.
- Fallback same-Wi-Fi mode.

Exit gate:

- Device supported dapat membuat hotspot.
- Device unsupported punya fallback jelas.
- Client join lewat QR/manual URL.

### Phase 9: History and Release Hardening

Tasks:

- Persistence transfer metadata.
- History screen.
- Clear history.
- Error state polish.
- Manual scenario tests.

Exit gate:

- End-to-end MVP flow lulus tanpa internet.
- App tidak crash pada kegagalan umum.

---

## 7. Technical Decision Records

Gunakan format ini untuk keputusan besar. Tambahkan entry baru setiap kali ada tradeoff yang berdampak panjang.

### TDR-001: UI stack

- Decision: XML Views untuk MVP.
- Reason: sesuai PRD, stabil, cepat untuk Android native MVP.
- Consequence: jangan pindah ke Compose tanpa alasan kuat.

### TDR-002: Chat transport

- Decision: HTTP polling dulu.
- Reason: lebih sederhana dan cukup untuk chat kelas ringan.
- Consequence: WebSocket ditunda sampai file transfer stabil.

### TDR-003: Browser client

- Decision: plain HTML/CSS/JS served by Android.
- Reason: ringan, cepat, tidak perlu build pipeline web.
- Consequence: browser asset harus tetap kecil dan mudah di-embed.

### TDR-004: Network mode

- Decision: same-Wi-Fi server prototype sebelum Local-only Hotspot.
- Reason: mengurangi risiko vendor Android dan mempercepat validasi server/file/chat.
- Consequence: UI harus punya fallback mode yang tidak terasa seperti debug kasar.

### TDR-005: Storage

- Decision: app-specific external files untuk upload diterima pada MVP.
- Reason: permission lebih sederhana dan sesuai Android modern.
- Consequence: "open file" dan "share again" perlu Content URI/FileProvider nanti.

---

## 8. Verification Matrix

### 8.1 Build gates

Run after each meaningful phase:

```powershell
.\gradlew.bat assembleDebug
```

Run when unit tests are added:

```powershell
.\gradlew.bat testDebugUnitTest
```

Run when instrumentation tests are meaningful:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

### 8.2 Automated tests to add

Unit tests:

- Token length and uniqueness sanity.
- PIN validation accepts configured PIN.
- PIN validation rejects wrong PIN.
- File size formatting.
- File name conflict resolver.
- Chat ordering by `createdAt`.
- Room stop idempotency.

Instrumentation/UI tests:

- Home renders CTA.
- Start room dummy state renders active room.
- Stop room returns to home or stopped state.
- History empty state renders.

### 8.3 Manual tests

Same-Wi-Fi server:

- Host starts room.
- Laptop opens URL.
- Invalid token rejected.
- Browser reload keeps room page.
- Stop room makes page unreachable or shows stopped state.

Download:

- Download 100 KB file.
- Download 5 MB file.
- Download file with spaces in name.
- Download after host removes file should fail gracefully.

Upload:

- Upload 100 KB file.
- Upload 5 MB file.
- Upload duplicate file name.
- Upload with browser closed mid-transfer.

Chat:

- Browser sends message to host.
- Host sends message to browser.
- Empty message blocked.
- Long message handled safely.

Hotspot:

- Room starts with mobile data off.
- Client connects to host network.
- Client opens QR URL.
- Hotspot failure shows fallback.

---

## 9. Risk Register

### Risk 1: Local-only Hotspot inconsistency

Impact:

- Core "host creates local network" can fail on some devices.

Mitigation:

- Build same-Wi-Fi mode first.
- Provide clear fallback copy.
- Keep hotspot wrapper isolated.
- Test on multiple Android vendors as soon as possible.

### Risk 2: Host IP discovery

Impact:

- QR URL may point to wrong address.

Mitigation:

- Implement robust local IP detection.
- Provide manual URL list in advanced diagnostics.
- Test on hotspot and same-Wi-Fi separately.

### Risk 3: File streaming memory pressure

Impact:

- Large files can crash app if loaded into memory.

Mitigation:

- Stream files in chunks.
- Never read whole upload/download into memory.
- Test 50 MB before calling transfer stable.

### Risk 4: Android storage permission complexity

Impact:

- User cannot find or open uploaded files.

Mitigation:

- Use app-specific external files for MVP.
- Add clear UI copy for where received files live.
- Add FileProvider/open/share flow later.

### Risk 5: Browser compatibility

Impact:

- Client UI may fail on older browsers or mobile browsers.

Mitigation:

- Plain HTML/CSS/JS.
- Avoid heavy APIs.
- Test Chrome desktop/mobile and at least one Chromium-based Android browser.

### Risk 6: Scope creep

Impact:

- MVP stalls before local transfer works.

Mitigation:

- Enforce phase exit gates.
- Defer mesh, accounts, E2EE, WebSocket, resume transfer, foreground service until core flow works.

---

## 10. UX Copy Standards

### 10.1 Preferred language

Use words like:

- Ruang
- Terhubung
- Menunggu perangkat
- Kirim file
- Ambil file
- Chat lokal
- Jaringan lokal

Avoid on primary screens:

- Server
- Port
- Socket
- IP address
- HTTP
- Token

### 10.2 Error copy examples

Hotspot failed:

> Ruang lokal belum bisa dibuat di perangkat ini. Coba tutup tethering lain, aktifkan Wi-Fi, lalu coba lagi.

Client cannot join:

> Pastikan perangkat sudah tersambung ke Wi-Fi Nadi, lalu scan QR lagi.

Transfer failed:

> File belum terkirim. Periksa koneksi lokal, lalu coba lagi.

Server stopped:

> Ruang sudah ditutup. Perangkat lain tidak dapat mengakses file atau chat lagi.

Permission denied:

> Nadi perlu izin ini untuk membuat ruang lokal di perangkatmu. Kamu bisa mencoba mode satu Wi-Fi jika izin tidak diberikan.

---

## 11. Codex Operating Protocol for Nadi

Gunakan bagian ini sebagai "skill ringan" setiap kali Codex bekerja di repo Nadi.

### 11.1 Start of every implementation session

1. Baca `NADI_PROJECT_CONTEXT.md`.
2. Baca `NADI_PRD.md`.
3. Baca dokumen ini.
4. Audit current files with `rg --files`.
5. Tentukan fase roadmap aktif.
6. Kerjakan scope terkecil yang melewati gate fase tersebut.
7. Jalankan build/test yang relevan.
8. Catat perubahan dan sisa risiko.

### 11.2 Planning discipline

Before editing:

- Identify the phase.
- Identify the deliverable.
- Identify the acceptance gate.
- Identify files likely touched.
- Avoid unrelated refactor.

During editing:

- Keep package boundaries clean.
- Prefer small domain classes over Activity logic.
- Add tests when touching pure logic.
- Do not introduce cloud/backend dependency.

Before final response:

- Verify current state with command output or file inspection.
- Mention any tests not run.
- Mention next best phase if useful.

### 11.3 Scope guardrails

Do not implement these before MVP core flow works:

- Firebase.
- Account/login.
- Cloud sync.
- Mesh network.
- End-to-end encrypted messenger.
- WebSocket chat unless polling is proven insufficient.
- Foreground service unless host background behavior becomes the active phase.
- Native desktop client.

### 11.4 Preferred next action selection

If the app still shows template UI:

- Work on Phase 1.

If the shell exists but no room state:

- Work on Phase 2.

If room state exists but browser cannot open local page:

- Work on Phase 3.

If browser page exists but no token/QR:

- Work on Phase 4.

If tokenized browser works but files cannot move:

- Work on Phase 5 or Phase 6.

If file transfer works but chat does not:

- Work on Phase 7.

If same-Wi-Fi works but hotspot does not:

- Work on Phase 8.

If end-to-end works but feels rough:

- Work on Phase 9.

---

## 12. Definition of Done

### 12.1 Phase done

A phase is done only when:

- Its deliverables exist in current files.
- Its exit gate has direct evidence.
- Build/test status is known.
- Known failures are documented.
- The next phase can start without guessing hidden state.

### 12.2 MVP done

MVP is done only when all are true:

- Android host can start a Nadi room.
- Client browser can join with QR/URL.
- Client can download a host-shared file.
- Client can upload a file to host.
- Host and client can exchange chat messages.
- The flow works without internet.
- Invalid token/PIN is rejected.
- Stop room cleans up server state.
- Common errors are visible and recoverable.
- Build passes.
- Manual scenario test is documented.

---

## 13. Immediate Next Plan

Given the current project state, the recommended next work is:

1. Run baseline `assembleDebug`.
2. Implement Phase 1 Brand Shell UI.
3. Add Nadi colors and strings.
4. Replace `Hello World` home with Nadi home.
5. Keep UI functional but not overbuilt.
6. Run `assembleDebug` again.

Expected first useful milestone:

> Opening the app shows Nadi branding, the tagline, clear "Buat Ruang" and "Gabung" actions, and a calm empty recent transfer section.

---

## 14. Maintenance Rules for This Blueprint

- Update this document after major architecture decisions.
- Add TDR entries for decisions that affect future phases.
- Keep roadmap gates stricter than casual TODOs.
- Remove obsolete assumptions after implementation proves them wrong.
- Do not let this document become a changelog; it is a planning map.

