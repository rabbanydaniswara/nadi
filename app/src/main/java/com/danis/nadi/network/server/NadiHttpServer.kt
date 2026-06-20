package com.danis.nadi.network.server

import com.danis.nadi.file.FileStore
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.room.RoomManager
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder

class NadiHttpServer(
    port: Int,
    private val roomManager: RoomManager,
    private val fileStore: FileStore
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/health" -> {
                    text(Response.Status.OK, "Nadi room server is running.")
                }
                session.method == Method.GET && (session.uri == "/" || session.uri == "/index.html") -> {
                    html(Response.Status.OK, browserClientHtml())
                }
                session.method == Method.GET && session.uri == "/api/room" -> {
                    roomMetadata(session)
                }
                session.method == Method.GET && session.uri == "/api/files" -> {
                    fileList(session)
                }
                session.method == Method.GET && session.uri.startsWith("/api/download/") -> {
                    downloadFile(session)
                }
                session.method == Method.POST && session.uri == "/api/upload" -> {
                    uploadFile(session)
                }
                session.method == Method.GET && session.uri == "/api/chat" -> {
                    listChat(session)
                }
                session.method == Method.POST && session.uri == "/api/chat" -> {
                    sendChat(session)
                }
                else -> {
                    json(Response.Status.NOT_FOUND, """{"error":"not_found"}""")
                }
            }
        } catch (_: Exception) {
            json(Response.Status.INTERNAL_ERROR, """{"error":"server_error"}""")
        }
    }

    private fun roomMetadata(session: IHTTPSession): Response {
        val token = session.parameters["token"]?.firstOrNull()
        if (!roomManager.validateToken(token)) return invalidToken()
        roomManager.touchClient(
            displayName = session.parameters["name"]?.firstOrNull().orEmpty(),
            userAgent = session.headers["user-agent"].orEmpty(),
            ipAddress = session.remoteIpAddress.orEmpty()
        )

        val snapshot = roomManager.snapshot()
        val room = snapshot.session ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"room_not_found"}"""
        )
        return json(Response.Status.OK, room.toJson(snapshot.clients.size))
    }

    private fun fileList(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        val shared = roomManager.sharedFiles()
        return json(Response.Status.OK, """{"files":${shared.toFileJsonArray()}}""")
    }

    private fun downloadFile(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        val id = session.uri.removePrefix("/api/download/").decodeUrl()
        val transfer = roomManager.transferById(id) ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"file_not_found"}"""
        )
        if (transfer.direction != TransferDirection.SHARED) {
            return json(Response.Status.NOT_FOUND, """{"error":"file_not_shared"}""")
        }
        val payload = fileStore.openForDownload(transfer) ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"file_unavailable"}"""
        )
        val response = if (payload.sizeBytes >= 0) {
            newFixedLengthResponse(
                Response.Status.OK,
                payload.mimeType,
                payload.inputStream,
                payload.sizeBytes
            )
        } else {
            newChunkedResponse(Response.Status.OK, payload.mimeType, payload.inputStream)
        }
        response.addHeader("Content-Disposition", "attachment; filename=\"${payload.fileName.headerSafe()}\"")
        return response
    }

    private fun uploadFile(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val tempPath = files["file"] ?: files.values.firstOrNull()
        if (tempPath.isNullOrBlank()) {
            return json(Response.Status.BAD_REQUEST, """{"error":"file_required"}""")
        }
        val originalName = session.parameters["file"]?.firstOrNull()
            ?: session.parameters["filename"]?.firstOrNull()
            ?: "upload.bin"
        val mimeType = session.headers["content-type"]?.substringAfter("type=", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
        val transfer = FileInputStream(File(tempPath)).use { input ->
            fileStore.saveUpload(originalName, mimeType, input)
        }
        roomManager.addTransfer(transfer)
        return json(Response.Status.OK, """{"file":${transfer.toJson()}}""")
    }

    private fun listChat(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        val after = session.parameters["after"]?.firstOrNull()?.toLongOrNull() ?: 0L
        val messages = roomManager.messagesAfter(after)
        return json(Response.Status.OK, """{"messages":${messages.toMessageJsonArray()}}""")
    }

    private fun sendChat(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        session.parseBody(mutableMapOf())
        val senderName = session.parameters["senderName"]?.firstOrNull() ?: "Browser"
        val text = session.parameters["text"]?.firstOrNull().orEmpty()
        val message = roomManager.addMessage(
            senderId = "browser-${session.remoteIpAddress.orEmpty()}",
            senderName = senderName,
            text = text
        ) ?: return json(Response.Status.BAD_REQUEST, """{"error":"message_required"}""")
        return json(Response.Status.OK, """{"message":${message.toJson()}}""")
    }

    private fun RoomSession.toJson(clientCount: Int): String {
        return buildString {
            append("{")
            append("\"sessionId\":\"").append(sessionId.escapeJson()).append("\",")
            append("\"roomName\":\"").append(roomName.escapeJson()).append("\",")
            append("\"hostName\":\"").append(hostName.escapeJson()).append("\",")
            append("\"status\":\"").append(status.name.lowercase()).append("\",")
            append("\"localUrl\":\"").append((localUrl ?: "").escapeJson()).append("\",")
            append("\"clientCount\":").append(clientCount).append(",")
            append("\"startedAt\":").append(startedAt)
            append("}")
        }
    }

    private fun List<TransferItem>.toFileJsonArray(): String = joinToString(prefix = "[", postfix = "]") { it.toJson() }

    private fun TransferItem.toJson(): String {
        return buildString {
            append("{")
            append("\"transferId\":\"").append(transferId.escapeJson()).append("\",")
            append("\"fileName\":\"").append(fileName.escapeJson()).append("\",")
            append("\"mimeType\":\"").append((mimeType ?: "").escapeJson()).append("\",")
            append("\"sizeBytes\":").append(sizeBytes).append(",")
            append("\"direction\":\"").append(direction.name.lowercase()).append("\",")
            append("\"status\":\"").append(status.name.lowercase()).append("\",")
            append("\"progress\":").append(progress).append(",")
            append("\"createdAt\":").append(createdAt).append(",")
            append("\"senderName\":\"").append((senderName ?: "").escapeJson()).append("\"")
            append("}")
        }
    }

    private fun List<ChatMessage>.toMessageJsonArray(): String = joinToString(prefix = "[", postfix = "]") { it.toJson() }

    private fun ChatMessage.toJson(): String {
        return buildString {
            append("{")
            append("\"messageId\":\"").append(messageId.escapeJson()).append("\",")
            append("\"senderId\":\"").append(senderId.escapeJson()).append("\",")
            append("\"senderName\":\"").append(senderName.escapeJson()).append("\",")
            append("\"text\":\"").append(text.escapeJson()).append("\",")
            append("\"createdAt\":").append(createdAt).append(",")
            append("\"status\":\"").append(status.escapeJson()).append("\"")
            append("}")
        }
    }

    private fun browserClientHtml(): String {
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
                  --line: #DDE7E3; --error: #B91C1C;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0; min-height: 100vh;
                  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  background: var(--mist); color: var(--ink);
                }
                main { width: min(960px, 100%); margin: 0 auto; padding: 20px; }
                .hero { background: var(--deep); color: white; border-radius: 14px; padding: 22px; }
                .eyebrow {
                  display: inline-flex; padding: 6px 10px; border-radius: 999px;
                  background: rgba(45, 212, 191, 0.16); color: #A7F3D0;
                  font-size: 13px; font-weight: 700;
                }
                h1 { margin: 16px 0 8px; font-size: clamp(30px, 5vw, 46px); line-height: 1; }
                h2 { margin: 0 0 12px; font-size: 20px; }
                p { line-height: 1.6; color: var(--soft); }
                .hero p { color: #DDE7E3; max-width: 640px; }
                .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: 14px; margin-top: 16px; }
                .card { background: white; border: 1px solid var(--line); border-radius: 12px; padding: 16px; }
                .label { margin: 0 0 6px; color: var(--soft); font-size: 12px; font-weight: 800; text-transform: uppercase; letter-spacing: .04em; }
                .value { margin: 0; color: var(--ink); font-size: 20px; font-weight: 800; }
                .locked { display: none; margin-top: 16px; border-color: #F3C7C7; background: #FFF7F7; }
                button, input[type=file], input[type=text] { width: 100%; min-height: 44px; font: inherit; }
                button {
                  border: 0; border-radius: 10px; padding: 10px 14px;
                  color: white; background: var(--green); font-weight: 800;
                }
                input[type=text] { border: 1px solid var(--line); border-radius: 10px; padding: 10px 12px; }
                .file-row, .message {
                  border-top: 1px solid var(--line); padding: 12px 0;
                }
                .file-row:first-child, .message:first-child { border-top: 0; }
                .muted { color: var(--soft); }
                .error { color: var(--error); font-weight: 700; }
                a { color: var(--green); font-weight: 800; }
                code { word-break: break-all; color: var(--green); font-weight: 700; }
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
                  } catch (error) {
                    document.getElementById("status").textContent = "Terputus";
                  }
                }
                async function refreshFiles() {
                  if (!token) return;
                  const response = await fetch("/api/files?token=" + encodeURIComponent(token));
                  if (!response.ok) return;
                  const payload = await response.json();
                  const files = payload.files || [];
                  document.getElementById("files").innerHTML = files.length
                    ? files.map(file => `<div class="file-row"><strong>${'$'}{esc(file.fileName)}</strong><p class="muted">${'$'}{formatBytes(file.sizeBytes)}</p><a href="/api/download/${'$'}{encodeURIComponent(file.transferId)}?token=${'$'}{encodeURIComponent(token)}">Download</a></div>`).join("")
                    : `<p class="muted">Belum ada file dari host.</p>`;
                }
                async function uploadFile() {
                  const input = document.getElementById("uploadInput");
                  const status = document.getElementById("uploadStatus");
                  if (!input.files.length) { status.textContent = "Pilih file dulu."; return; }
                  const data = new FormData();
                  data.append("file", input.files[0], input.files[0].name);
                  status.textContent = "Mengirim...";
                  const response = await fetch("/api/upload?token=" + encodeURIComponent(token), { method: "POST", body: data });
                  status.textContent = response.ok ? "File terkirim ke host." : "File belum terkirim.";
                  input.value = "";
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

    private fun invalidToken(): Response = json(Response.Status.UNAUTHORIZED, """{"error":"invalid_token"}""")

    private fun text(status: Response.IStatus, body: String): Response {
        return newFixedLengthResponse(status, MIME_PLAINTEXT, body)
    }

    private fun html(status: Response.IStatus, body: String): Response {
        return newFixedLengthResponse(status, "text/html; charset=utf-8", body)
    }

    private fun json(status: Response.IStatus, body: String): Response {
        return newFixedLengthResponse(status, "application/json; charset=utf-8", body)
    }

    private fun String.escapeJson(): String {
        return buildString(length) {
            this@escapeJson.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private fun String.decodeUrl(): String = URLDecoder.decode(this, "UTF-8")

    private fun String.headerSafe(): String = replace("\"", "'").replace("\r", "").replace("\n", "")
}
