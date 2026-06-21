package com.danis.nadi.network.server

object BrowserClientAssets {
    fun html(): String {
        return """
            <!doctype html>
            <html lang="id">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Nadi Room</title>
              <style>
                :root {
                  color-scheme: light;
                  --deep: #073B32; --green: #0E7A63; --teal: #2DD4BF;
                  --ink: #111827; --soft: #4B5563; --mist: #F7FAF9;
                  --line: #DDE7E3; --error: #B91C1C; --success: #047857;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0; min-height: 100vh;
                  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  background: var(--mist); color: var(--ink);
                }
                main { width: min(1040px, 100%); margin: 0 auto; padding: 18px; }
                .hero { background: var(--deep); color: white; border-radius: 8px; padding: 22px; }
                .eyebrow {
                  display: inline-flex; padding: 6px 10px; border-radius: 999px;
                  background: rgba(45, 212, 191, 0.16); color: #A7F3D0;
                  font-size: 13px; font-weight: 800;
                }
                h1 { margin: 16px 0 8px; font-size: 34px; line-height: 1.05; }
                h2 { margin: 0 0 12px; font-size: 19px; }
                p { line-height: 1.55; color: var(--soft); }
                .hero p { color: #DDE7E3; max-width: 660px; }
                .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; margin-top: 14px; }
                .card { background: white; border: 1px solid var(--line); border-radius: 8px; padding: 16px; }
                .client-nav {
                  display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 14px;
                  background: white; border: 1px solid var(--line); border-radius: 8px; padding: 8px;
                }
                .tab-button { background: transparent; color: var(--soft); border: 1px solid transparent; }
                .tab-button.active { background: var(--green); color: white; }
                .client-section { margin-top: 14px; }
                .label { margin: 0 0 6px; color: var(--soft); font-size: 12px; font-weight: 800; text-transform: uppercase; letter-spacing: .04em; }
                .value { margin: 0; color: var(--ink); font-size: 20px; font-weight: 800; }
                .locked { display: none; margin-top: 14px; border-color: #F3C7C7; background: #FFF7F7; }
                button, input[type=file], input[type=text] { width: 100%; min-height: 44px; font: inherit; }
                button {
                  border: 0; border-radius: 8px; padding: 10px 14px;
                  color: white; background: var(--green); font-weight: 800;
                }
                input[type=text] { border: 1px solid var(--line); border-radius: 8px; padding: 10px 12px; }
                progress { width: 100%; height: 10px; accent-color: var(--green); }
                .file-row { border-top: 1px solid var(--line); padding: 12px 0; }
                .file-row:first-child { border-top: 0; }
                .message {
                  width: fit-content; max-width: 100%; margin-top: 10px; padding: 10px 12px;
                  border: 1px solid var(--line); border-radius: 8px 8px 8px 2px; background: var(--mist);
                }
                .message strong { display: block; color: var(--green); font-size: 13px; }
                .message p { margin: 6px 0 0; color: var(--ink); }
                .attachment-card {
                  margin-top: 10px; padding: 10px; border: 1px solid var(--line);
                  border-radius: 8px; background: white;
                }
                .attachment-card p { color: var(--soft); }
                .attachment-actions { display: grid; gap: 8px; margin-top: 8px; }
                .chat-image {
                  display: block; width: min(360px, 100%); max-height: 260px;
                  object-fit: contain; border-radius: 8px; background: #EEF6F3;
                }
                .identity { margin-top: 14px; }
                .identity-summary { margin-top: 10px; font-weight: 800; color: var(--green); }
                .muted { color: var(--soft); }
                .error { color: var(--error); font-weight: 800; }
                .success { color: var(--success); font-weight: 800; }
                .hidden { display: none; }
                button:disabled { opacity: .55; cursor: not-allowed; }
                a { color: var(--green); font-weight: 800; }
                code { word-break: break-all; color: var(--green); font-weight: 700; }
                @media (max-width: 560px) {
                  main { padding: 12px; }
                  h1 { font-size: 29px; }
                  .hero, .card { padding: 14px; }
                }
              </style>
            </head>
            <body>
              <main>
                <section class="hero">
                  <span class="eyebrow">Terhubung ke Nadi</span>
                  <h1 id="roomName">Membuka ruang...</h1>
                  <p id="roomCopy">Nadi menyiapkan jalur lokal untuk file dan chat di jaringan yang sama.</p>
                </section>
                <section id="locked" class="card locked">
                  <p class="label">Akses belum valid</p>
                  <p>Scan QR dari host Nadi atau buka URL yang dibagikan dari ruang aktif.</p>
                </section>
                <section id="identityCard" class="card identity">
                  <p class="label">Identitas peserta</p>
                  <h2>Masuk dengan NIM dan Nama</h2>
                  <p class="muted">Identitas ini akan melekat pada chat dan file selama room berjalan.</p>
                  <input id="nimInput" type="text" inputmode="text" autocomplete="off" placeholder="NIM">
                  <input id="nameInput" type="text" autocomplete="name" style="margin-top:8px" placeholder="Nama lengkap">
                  <button id="saveIdentityButton" style="margin-top:10px">Masuk ke room</button>
                  <p id="identityStatus" class="muted"></p>
                  <p id="identitySummary" class="identity-summary hidden"></p>
                </section>
                <section class="grid">
                  <article class="card"><p class="label">Status</p><p id="status" class="value">Memeriksa...</p></article>
                  <article class="card"><p class="label">Host</p><p id="hostName" class="value">-</p></article>
                  <article class="card"><p class="label">Perangkat</p><p id="clientCount" class="value">0 terhubung</p></article>
                </section>
                <nav class="client-nav" aria-label="Menu room">
                  <button type="button" class="tab-button active" data-client-tab="files">File Room</button>
                  <button type="button" class="tab-button" data-client-tab="chat">Chat</button>
                  <button type="button" class="tab-button" data-client-tab="info">Info</button>
                </nav>
                <section id="clientFilesSection" class="client-section grid">
                  <article class="card">
                    <h2>File Room: ambil file</h2>
                    <div id="files"><p class="muted">Belum ada file dari host.</p></div>
                  </article>
                  <article class="card">
                    <h2>File Room: kirim file</h2>
                    <input id="uploadInput" type="file">
                    <button id="uploadButton" style="margin-top:10px">Kirim file</button>
                    <p id="uploadStatus" class="muted"></p>
                    <progress id="uploadProgress" value="0" max="100" style="display:none"></progress>
                  </article>
                </section>
                <section id="clientChatSection" class="client-section hidden">
                  <article class="card">
                    <h2>Chat lokal</h2>
                    <div id="messages"><p class="muted">Belum ada pesan.</p></div>
                    <input id="chatInput" type="text" style="margin-top:10px" placeholder="Tulis pesan">
                    <button id="sendButton" style="margin-top:10px">Kirim</button>
                    <input id="chatAttachmentInput" type="file" style="margin-top:10px" accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.txt,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.zip">
                    <button id="sendAttachmentButton" style="margin-top:10px">Kirim lampiran chat</button>
                    <p id="attachmentStatus" class="muted"></p>
                  </article>
                </section>
                <section id="clientInfoSection" class="client-section grid hidden">
                  <article class="card">
                    <p class="label">Identitas</p>
                    <p id="activeIdentityText" class="value">Belum masuk</p>
                    <p class="muted">Identitas NIM dan Nama melekat pada chat serta file selama room berjalan.</p>
                  </article>
                  <article class="card">
                    <p class="label">Room</p>
                    <p id="infoRoomName" class="value">-</p>
                    <p id="infoHostName" class="muted">Host: -</p>
                    <p id="infoConnection" class="muted">Koneksi lokal sedang diperiksa.</p>
                  </article>
                  <article class="card">
                    <p class="label">Local-first</p>
                    <p class="muted">File Room dan Chat berjalan di jaringan lokal host. File utama ada di File Room, lampiran percakapan tetap berada di Chat.</p>
                  </article>
                </section>
                <p>URL room: <code id="currentUrl"></code></p>
              </main>
              <script>
                const params = new URLSearchParams(window.location.search);
                const token = params.get("token") || "";
                const clientIdKey = "nadiClientId";
                const clientNimKey = "nadiClientNim";
                const clientNameKey = "nadiClientName";
                const nimInput = document.getElementById("nimInput");
                const nameInput = document.getElementById("nameInput");
                const identityStatus = document.getElementById("identityStatus");
                const identitySummary = document.getElementById("identitySummary");
                const attachmentStatus = document.getElementById("attachmentStatus");
                nimInput.value = localStorage.getItem(clientNimKey) || "";
                nameInput.value = localStorage.getItem(clientNameKey) || "";
                let latestMessageAt = 0;
                let seenMessages = new Set();
                document.getElementById("currentUrl").textContent = window.location.href;

                function switchClientTab(tab) {
                  document.querySelectorAll(".tab-button").forEach(button => {
                    button.classList.toggle("active", button.dataset.clientTab === tab);
                  });
                  document.getElementById("clientFilesSection").classList.toggle("hidden", tab !== "files");
                  document.getElementById("clientChatSection").classList.toggle("hidden", tab !== "chat");
                  document.getElementById("clientInfoSection").classList.toggle("hidden", tab !== "info");
                }

                function clientId() {
                  let id = localStorage.getItem(clientIdKey) || "";
                  if (!id) {
                    id = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : ("client-" + Date.now() + "-" + Math.random().toString(16).slice(2));
                    localStorage.setItem(clientIdKey, id);
                  }
                  return id;
                }
                function hasIdentity() {
                  return Boolean(localStorage.getItem(clientNimKey) && localStorage.getItem(clientNameKey));
                }
                function clientQuery() {
                  return "clientId=" + encodeURIComponent(clientId());
                }
                function updateIdentityUi(locked) {
                  const nim = localStorage.getItem(clientNimKey) || "";
                  const name = localStorage.getItem(clientNameKey) || "";
                  const complete = Boolean(nim && name) && !locked;
                  document.getElementById("identityCard").style.display = complete ? "none" : "block";
                  identitySummary.textContent = complete ? (nim + " - " + name) : "";
                  identitySummary.className = complete ? "identity-summary" : "identity-summary hidden";
                  document.getElementById("activeIdentityText").textContent = complete ? (nim + " - " + name) : "Belum masuk";
                  document.querySelectorAll("button").forEach(button => {
                    if (button.id !== "saveIdentityButton") button.disabled = !complete;
                  });
                }
                async function registerIdentity() {
                  if (!token) { showLocked(); return; }
                  const nim = nimInput.value.trim().replace(/\s+/g, "");
                  const name = nameInput.value.trim().replace(/\s+/g, " ");
                  if (nim.length < 3 || name.length < 2) {
                    identityStatus.textContent = "Isi NIM dan nama lengkap terlebih dahulu.";
                    identityStatus.className = "error";
                    return;
                  }
                  const body = new URLSearchParams({ clientId: clientId(), nim, name });
                  const response = await fetch("/api/identity?token=" + encodeURIComponent(token), {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body
                  });
                  if (!response.ok) {
                    identityStatus.textContent = "Identitas belum valid. Periksa NIM dan nama.";
                    identityStatus.className = "error";
                    return;
                  }
                  const payload = await response.json();
                  localStorage.setItem(clientNimKey, payload.client.nim);
                  localStorage.setItem(clientNameKey, payload.client.name);
                  identityStatus.textContent = "Identitas tersimpan untuk room ini.";
                  identityStatus.className = "success";
                  updateIdentityUi(false);
                  refreshRoom(); refreshFiles(); refreshChat();
                }
                function esc(value) {
                  return String(value ?? "").replace(/[&<>"']/g, ch => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#039;" }[ch]));
                }
                function formatBytes(bytes) {
                  if (bytes < 0) return "-";
                  if (bytes < 1024) return bytes + " B";
                  const units = ["KB", "MB", "GB"];
                  let value = bytes / 1024;
                  let index = 0;
                  while (value >= 1024 && index < units.length - 1) { value /= 1024; index++; }
                  return value.toFixed(1) + " " + units[index];
                }
                function safeId(value) {
                  return String(value ?? "").replace(/[^a-zA-Z0-9_-]/g, "_");
                }
                function attachmentUrl(id, preview = false) {
                  const previewQuery = preview ? "&preview=1" : "";
                  return "/api/download/" + encodeURIComponent(id) + "?token=" + encodeURIComponent(token) + "&" + clientQuery() + previewQuery;
                }
                function isImageAttachment(message) {
                  const mime = String(message.attachmentMimeType || "").toLowerCase();
                  const name = String(message.attachmentFileName || "").toLowerCase();
                  return mime.startsWith("image/") || /\.(jpg|jpeg|png|gif|webp)$/.test(name);
                }
                function attachmentMarkup(message) {
                  if (!message.attachmentTransferId) return "";
                  const id = message.attachmentTransferId;
                  const statusId = "chatAttachmentStatus-" + safeId(id);
                  const name = message.attachmentFileName || "Lampiran chat";
                  const size = Number(message.attachmentSizeBytes || -1);
                  const meta = formatBytes(size);
                  const url = attachmentUrl(id, true);
                  const downloadButton = `<button type="button" class="chatAttachmentDownload" data-id="${'$'}{esc(id)}" data-name="${'$'}{esc(name)}" data-status="${'$'}{esc(statusId)}">Download</button><p id="${'$'}{esc(statusId)}" class="muted"></p>`;
                  if (isImageAttachment(message)) {
                    return `<div class="attachment-card"><img class="chat-image" src="${'$'}{esc(url)}" alt="Preview ${'$'}{esc(name)}" loading="lazy"><p class="muted">${'$'}{esc(name)} - ${'$'}{meta}</p><div class="attachment-actions">${'$'}{downloadButton}</div></div>`;
                  }
                  return `<div class="attachment-card"><strong>${'$'}{esc(name)}</strong><p>${'$'}{meta} - Download dulu untuk membuka file ini.</p><div class="attachment-actions">${'$'}{downloadButton}</div></div>`;
                }
                async function refreshRoom() {
                  if (!token) { showLocked(); return; }
                  try {
                    const response = await fetch("/api/room?token=" + encodeURIComponent(token) + "&" + clientQuery());
                    if (!response.ok) { showLocked(); return; }
                    const room = await response.json();
                    document.getElementById("roomName").textContent = room.roomName;
                    document.getElementById("roomCopy").textContent = "Ruang lokal dari " + room.hostName + " siap dipakai di jaringan yang sama.";
                    document.getElementById("status").textContent = room.status === "active" ? "Siap" : room.status;
                    document.getElementById("hostName").textContent = room.hostName;
                    document.getElementById("clientCount").textContent = room.clientCount + " terhubung";
                    document.getElementById("infoRoomName").textContent = room.roomName;
                    document.getElementById("infoHostName").textContent = "Host: " + room.hostName;
                    document.getElementById("infoConnection").textContent = "Status: " + (room.status === "active" ? "Siap" : room.status);
                    document.getElementById("locked").style.display = "none";
                    updateIdentityUi(room.identityRequired);
                  } catch (error) {
                    document.getElementById("status").textContent = "Terputus";
                    document.getElementById("roomCopy").textContent = "Koneksi lokal ke host terputus. Pastikan perangkat masih berada di jaringan yang sama.";
                  }
                }
                async function refreshFiles() {
                  if (!token) return;
                  if (!hasIdentity()) {
                    document.getElementById("files").innerHTML = `<p class="muted">Isi identitas dulu untuk melihat file room.</p>`;
                    return;
                  }
                  const response = await fetch("/api/files?token=" + encodeURIComponent(token) + "&" + clientQuery());
                  if (!response.ok) return;
                  const payload = await response.json();
                  const files = payload.files || [];
                  document.getElementById("files").innerHTML = files.length
                    ? files.map(file => {
                        const rowId = safeId(file.transferId);
                        return `<div class="file-row"><strong>${'$'}{esc(file.fileName)}</strong><p class="muted">${'$'}{formatBytes(file.sizeBytes)} - ${'$'}{esc(file.status)}</p><button type="button" class="downloadButton" data-id="${'$'}{esc(file.transferId)}" data-name="${'$'}{esc(file.fileName)}">Download</button><p id="downloadStatus-${'$'}{rowId}" class="muted"></p><progress id="downloadProgress-${'$'}{rowId}" value="0" max="100" style="display:none"></progress></div>`;
                      }).join("")
                    : `<p class="muted">Belum ada file dari host.</p>`;
                  document.querySelectorAll(".downloadButton").forEach(button => {
                    button.addEventListener("click", () => downloadFile(button.dataset.id, button.dataset.name));
                  });
                }
                async function downloadFile(id, name) {
                  const rowId = safeId(id);
                  const status = document.getElementById("downloadStatus-" + rowId);
                  const progress = document.getElementById("downloadProgress-" + rowId);
                  if (!id || !status || !progress) return;
                  status.textContent = "Menyiapkan download...";
                  status.className = "muted";
                  progress.style.display = "block";
                  progress.value = 0;
                  try {
                    const response = await fetch("/api/download/" + encodeURIComponent(id) + "?token=" + encodeURIComponent(token) + "&" + clientQuery());
                    if (!response.ok) throw new Error("download_failed");
                    const total = Number(response.headers.get("content-length") || 0);
                    const reader = response.body && response.body.getReader ? response.body.getReader() : null;
                    if (!reader) {
                      const blob = await response.blob();
                      saveBlob(blob, name || "nadi-download");
                      progress.value = 100;
                      status.textContent = "Download selesai.";
                      status.className = "success";
                      return;
                    }
                    const chunks = [];
                    let received = 0;
                    while (true) {
                      const result = await reader.read();
                      if (result.done) break;
                      chunks.push(result.value);
                      received += result.value.length;
                      if (total > 0) {
                        const percent = Math.round((received / total) * 100);
                        progress.value = percent;
                        status.textContent = "Download " + percent + "%...";
                      } else {
                        status.textContent = "Download " + formatBytes(received) + "...";
                      }
                    }
                    const blob = new Blob(chunks);
                    saveBlob(blob, name || "nadi-download");
                    progress.value = 100;
                    status.textContent = "Download selesai.";
                    status.className = "success";
                  } catch (error) {
                    status.textContent = "Download gagal. Pastikan room masih aktif.";
                    status.className = "error";
                  }
                }
                async function downloadChatAttachment(id, name, statusId) {
                  const status = document.getElementById(statusId);
                  if (!id || !status) return;
                  status.textContent = "Menyiapkan download...";
                  status.className = "muted";
                  try {
                    const response = await fetch(attachmentUrl(id));
                    if (!response.ok) throw new Error("download_failed");
                    const blob = await response.blob();
                    saveBlob(blob, name || "lampiran-nadi");
                    status.textContent = "Download selesai. Buka file dari folder download browser.";
                    status.className = "success";
                  } catch (error) {
                    status.textContent = "Download gagal. Pastikan room masih aktif.";
                    status.className = "error";
                  }
                }
                function saveBlob(blob, fileName) {
                  const url = URL.createObjectURL(blob);
                  const link = document.createElement("a");
                  link.href = url;
                  link.download = fileName;
                  document.body.appendChild(link);
                  link.click();
                  link.remove();
                  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
                }
                function uploadFile() {
                  const input = document.getElementById("uploadInput");
                  const status = document.getElementById("uploadStatus");
                  const progress = document.getElementById("uploadProgress");
                  if (!input.files.length) { status.textContent = "Pilih file dulu."; return; }
                  const data = new FormData();
                  data.append("clientId", clientId());
                  data.append("file", input.files[0], input.files[0].name);
                  status.textContent = "Mengirim 0%...";
                  progress.style.display = "block";
                  progress.value = 0;
                  const xhr = new XMLHttpRequest();
                  xhr.open("POST", "/api/upload?token=" + encodeURIComponent(token));
                  xhr.upload.onprogress = event => {
                    if (!event.lengthComputable) return;
                    const percent = Math.round((event.loaded / event.total) * 100);
                    progress.value = percent;
                    status.textContent = "Mengirim " + percent + "%...";
                  };
                  xhr.onload = () => {
                    const ok = xhr.status >= 200 && xhr.status < 300;
                    status.textContent = ok ? "File terkirim ke host." : "File belum terkirim.";
                    status.className = ok ? "success" : "error";
                    progress.value = ok ? 100 : progress.value;
                    input.value = "";
                    refreshFiles();
                  };
                  xhr.onerror = () => {
                    status.textContent = "Jaringan lokal terputus saat mengirim file.";
                    status.className = "error";
                  };
                  xhr.send(data);
                }
                async function refreshChat() {
                  if (!token) return;
                  if (!hasIdentity()) return;
                  const response = await fetch("/api/chat?token=" + encodeURIComponent(token) + "&" + clientQuery() + "&after=" + latestMessageAt);
                  if (!response.ok) return;
                  const payload = await response.json();
                  const holder = document.getElementById("messages");
                  if (!seenMessages.size) holder.innerHTML = "";
                  for (const message of payload.messages || []) {
                    if (seenMessages.has(message.messageId)) continue;
                    seenMessages.add(message.messageId);
                    latestMessageAt = Math.max(latestMessageAt, message.createdAt);
                    const div = document.createElement("div");
                    div.className = "message";
                    const attachment = attachmentMarkup(message);
                    div.innerHTML = `<strong>${'$'}{esc(message.senderName)}</strong><p>${'$'}{esc(message.text)}</p>${'$'}{attachment}`;
                    holder.appendChild(div);
                    div.querySelectorAll(".chatAttachmentDownload").forEach(button => {
                      button.addEventListener("click", () => downloadChatAttachment(button.dataset.id, button.dataset.name, button.dataset.status));
                    });
                  }
                  if (!seenMessages.size) holder.innerHTML = `<p class="muted">Belum ada pesan.</p>`;
                }
                async function sendChat() {
                  const input = document.getElementById("chatInput");
                  const text = input.value.trim();
                  if (!text) return;
                  if (!hasIdentity()) {
                    identityStatus.textContent = "Isi identitas dulu sebelum chat.";
                    identityStatus.className = "error";
                    updateIdentityUi(true);
                    return;
                  }
                  const body = new URLSearchParams({ clientId: clientId(), text });
                  const response = await fetch("/api/chat?token=" + encodeURIComponent(token), {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body
                  });
                  if (response.ok) {
                    input.value = "";
                    refreshChat();
                  }
                }
                function sendChatAttachment() {
                  const input = document.getElementById("chatAttachmentInput");
                  if (!hasIdentity()) {
                    attachmentStatus.textContent = "Isi identitas dulu sebelum mengirim lampiran.";
                    attachmentStatus.className = "error";
                    updateIdentityUi(true);
                    return;
                  }
                  if (!input.files.length) {
                    attachmentStatus.textContent = "Pilih file lampiran dulu.";
                    attachmentStatus.className = "error";
                    return;
                  }
                  const data = new FormData();
                  data.append("clientId", clientId());
                  data.append("text", document.getElementById("chatInput").value.trim());
                  data.append("file", input.files[0], input.files[0].name);
                  attachmentStatus.textContent = "Mengirim lampiran...";
                  attachmentStatus.className = "muted";
                  const xhr = new XMLHttpRequest();
                  xhr.open("POST", "/api/chat-attachment?token=" + encodeURIComponent(token));
                  xhr.onload = () => {
                    const ok = xhr.status >= 200 && xhr.status < 300;
                    attachmentStatus.textContent = ok ? "Lampiran terkirim di chat." : "Lampiran ditolak. Gunakan gambar/dokumen kecil.";
                    attachmentStatus.className = ok ? "success" : "error";
                    if (ok) {
                      input.value = "";
                      document.getElementById("chatInput").value = "";
                      refreshChat();
                    }
                  };
                  xhr.onerror = () => {
                    attachmentStatus.textContent = "Jaringan lokal terputus saat mengirim lampiran.";
                    attachmentStatus.className = "error";
                  };
                  xhr.send(data);
                }
                function showLocked() {
                  document.getElementById("locked").style.display = "block";
                  document.getElementById("status").textContent = "Terkunci";
                  document.getElementById("roomName").textContent = "Akses room ditutup";
                  document.getElementById("roomCopy").textContent = "Link ini tidak lagi valid. Minta QR atau URL terbaru dari host Nadi.";
                  document.querySelectorAll("button").forEach(button => button.disabled = true);
                }
                document.getElementById("saveIdentityButton").addEventListener("click", registerIdentity);
                document.getElementById("uploadButton").addEventListener("click", uploadFile);
                document.getElementById("sendButton").addEventListener("click", sendChat);
                document.getElementById("sendAttachmentButton").addEventListener("click", sendChatAttachment);
                document.getElementById("chatInput").addEventListener("keydown", event => { if (event.key === "Enter") sendChat(); });
                document.querySelectorAll(".tab-button").forEach(button => {
                  button.addEventListener("click", () => switchClientTab(button.dataset.clientTab));
                });
                switchClientTab("files");
                refreshRoom(); refreshFiles(); refreshChat();
                window.setInterval(refreshRoom, 4000);
                window.setInterval(refreshFiles, 4000);
                window.setInterval(refreshChat, 1500);
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
