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
                .file-row, .message { border-top: 1px solid var(--line); padding: 12px 0; }
                .file-row:first-child, .message:first-child { border-top: 0; }
                .muted { color: var(--soft); }
                .error { color: var(--error); font-weight: 800; }
                .success { color: var(--success); font-weight: 800; }
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
                <section class="grid">
                  <article class="card"><p class="label">Status</p><p id="status" class="value">Memeriksa...</p></article>
                  <article class="card"><p class="label">Host</p><p id="hostName" class="value">-</p></article>
                  <article class="card"><p class="label">Perangkat</p><p id="clientCount" class="value">0 terhubung</p></article>
                </section>
                <section class="grid">
                  <article class="card">
                    <h2>Ambil file</h2>
                    <div id="files"><p class="muted">Belum ada file dari host.</p></div>
                  </article>
                  <article class="card">
                    <h2>Kirim file ke host</h2>
                    <input id="uploadInput" type="file">
                    <button id="uploadButton" style="margin-top:10px">Kirim file</button>
                    <p id="uploadStatus" class="muted"></p>
                    <progress id="uploadProgress" value="0" max="100" style="display:none"></progress>
                  </article>
                  <article class="card">
                    <h2>Chat lokal</h2>
                    <div id="messages"><p class="muted">Belum ada pesan.</p></div>
                    <input id="senderName" type="text" style="margin-top:10px" placeholder="Nama kamu">
                    <input id="chatInput" type="text" style="margin-top:8px" placeholder="Tulis pesan">
                    <button id="sendButton" style="margin-top:10px">Kirim</button>
                  </article>
                </section>
                <p>URL room: <code id="currentUrl"></code></p>
              </main>
              <script>
                const params = new URLSearchParams(window.location.search);
                const token = params.get("token") || "";
                const clientNameKey = "nadiClientName";
                const senderName = document.getElementById("senderName");
                senderName.value = localStorage.getItem(clientNameKey) || "Browser";
                let latestMessageAt = 0;
                let seenMessages = new Set();
                document.getElementById("currentUrl").textContent = window.location.href;

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
                async function refreshRoom() {
                  if (!token) { showLocked(); return; }
                  try {
                    const response = await fetch("/api/room?token=" + encodeURIComponent(token) + "&name=" + encodeURIComponent(senderName.value));
                    if (!response.ok) { showLocked(); return; }
                    const room = await response.json();
                    document.getElementById("roomName").textContent = room.roomName;
                    document.getElementById("roomCopy").textContent = "Ruang lokal dari " + room.hostName + " siap dipakai di jaringan yang sama.";
                    document.getElementById("status").textContent = room.status === "active" ? "Siap" : room.status;
                    document.getElementById("hostName").textContent = room.hostName;
                    document.getElementById("clientCount").textContent = room.clientCount + " terhubung";
                    document.getElementById("locked").style.display = "none";
                    document.querySelectorAll("button").forEach(button => button.disabled = false);
                  } catch (error) {
                    document.getElementById("status").textContent = "Terputus";
                    document.getElementById("roomCopy").textContent = "Koneksi lokal ke host terputus. Pastikan perangkat masih berada di jaringan yang sama.";
                  }
                }
                async function refreshFiles() {
                  if (!token) return;
                  const response = await fetch("/api/files?token=" + encodeURIComponent(token));
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
                    const response = await fetch("/api/download/" + encodeURIComponent(id) + "?token=" + encodeURIComponent(token));
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
                  const response = await fetch("/api/chat?token=" + encodeURIComponent(token) + "&after=" + latestMessageAt);
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
                    div.innerHTML = `<strong>${'$'}{esc(message.senderName)}</strong><p>${'$'}{esc(message.text)}</p>`;
                    holder.appendChild(div);
                  }
                  if (!seenMessages.size) holder.innerHTML = `<p class="muted">Belum ada pesan.</p>`;
                }
                async function sendChat() {
                  const input = document.getElementById("chatInput");
                  const text = input.value.trim();
                  if (!text) return;
                  localStorage.setItem(clientNameKey, senderName.value || "Browser");
                  const body = new URLSearchParams({ senderName: senderName.value || "Browser", text });
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
                function showLocked() {
                  document.getElementById("locked").style.display = "block";
                  document.getElementById("status").textContent = "Terkunci";
                  document.getElementById("roomName").textContent = "Akses room ditutup";
                  document.getElementById("roomCopy").textContent = "Link ini tidak lagi valid. Minta QR atau URL terbaru dari host Nadi.";
                  document.querySelectorAll("button").forEach(button => button.disabled = true);
                }
                document.getElementById("uploadButton").addEventListener("click", uploadFile);
                document.getElementById("sendButton").addEventListener("click", sendChat);
                document.getElementById("chatInput").addEventListener("keydown", event => { if (event.key === "Enter") sendChat(); });
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
