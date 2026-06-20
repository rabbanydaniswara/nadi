package com.danis.nadi.network.server

import com.danis.nadi.model.RoomSession
import com.danis.nadi.room.RoomManager
import fi.iki.elonen.NanoHTTPD

class NadiHttpServer(
    port: Int,
    private val roomManager: RoomManager
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/health" -> {
                text(Response.Status.OK, "Nadi room server is running.")
            }
            session.method == Method.GET && session.uri == "/api/room" -> {
                roomMetadata(session.parameters["token"]?.firstOrNull())
            }
            session.method == Method.GET && (session.uri == "/" || session.uri == "/index.html") -> {
                html(Response.Status.OK, browserClientHtml())
            }
            else -> {
                json(Response.Status.NOT_FOUND, """{"error":"not_found"}""")
            }
        }
    }

    private fun roomMetadata(token: String?): Response {
        if (!roomManager.validateToken(token)) {
            return json(Response.Status.UNAUTHORIZED, """{"error":"invalid_token"}""")
        }
        val snapshot = roomManager.snapshot()
        val room = snapshot.session ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"room_not_found"}"""
        )
        return json(Response.Status.OK, room.toJson(snapshot.clients.size))
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
                  --deep: #073B32;
                  --green: #0E7A63;
                  --teal: #2DD4BF;
                  --ink: #111827;
                  --soft: #4B5563;
                  --mist: #F7FAF9;
                  --line: #DDE7E3;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  min-height: 100vh;
                  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  background: var(--mist);
                  color: var(--ink);
                }
                main {
                  width: min(920px, 100%);
                  margin: 0 auto;
                  padding: 24px;
                }
                .hero {
                  background: var(--deep);
                  color: white;
                  border-radius: 14px;
                  padding: 24px;
                }
                .eyebrow {
                  display: inline-flex;
                  padding: 6px 10px;
                  border-radius: 999px;
                  background: rgba(45, 212, 191, 0.16);
                  color: #A7F3D0;
                  font-size: 13px;
                  font-weight: 700;
                  letter-spacing: .02em;
                }
                h1 { margin: 18px 0 8px; font-size: clamp(30px, 5vw, 48px); line-height: 1; }
                p { line-height: 1.6; color: var(--soft); }
                .hero p { color: #DDE7E3; max-width: 620px; }
                .grid {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
                  gap: 14px;
                  margin-top: 16px;
                }
                .card {
                  background: white;
                  border: 1px solid var(--line);
                  border-radius: 12px;
                  padding: 18px;
                }
                .label {
                  margin: 0 0 6px;
                  color: var(--soft);
                  font-size: 13px;
                  font-weight: 700;
                  text-transform: uppercase;
                  letter-spacing: .04em;
                }
                .value { margin: 0; color: var(--ink); font-size: 20px; font-weight: 800; }
                .locked {
                  display: none;
                  margin-top: 16px;
                  border-color: #F3C7C7;
                  background: #FFF7F7;
                }
                code {
                  word-break: break-all;
                  color: var(--green);
                  font-weight: 700;
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
                  <article class="card">
                    <p class="label">Status</p>
                    <p id="status" class="value">Memeriksa...</p>
                  </article>
                  <article class="card">
                    <p class="label">Host</p>
                    <p id="hostName" class="value">-</p>
                  </article>
                  <article class="card">
                    <p class="label">Perangkat</p>
                    <p id="clientCount" class="value">0 terhubung</p>
                  </article>
                </section>
                <section class="grid">
                  <article class="card">
                    <p class="label">Ambil file</p>
                    <p>Daftar file download akan muncul di tahap berikutnya.</p>
                  </article>
                  <article class="card">
                    <p class="label">Kirim file</p>
                    <p>Upload dari browser akan diaktifkan setelah endpoint transfer selesai.</p>
                  </article>
                  <article class="card">
                    <p class="label">Chat lokal</p>
                    <p>Chat polling akan ditambahkan setelah flow file stabil.</p>
                  </article>
                </section>
                <p>URL room: <code id="currentUrl"></code></p>
              </main>
              <script>
                const params = new URLSearchParams(window.location.search);
                const token = params.get("token") || "";
                document.getElementById("currentUrl").textContent = window.location.href;
                async function refreshRoom() {
                  if (!token) {
                    showLocked();
                    return;
                  }
                  try {
                    const response = await fetch("/api/room?token=" + encodeURIComponent(token));
                    if (!response.ok) {
                      showLocked();
                      return;
                    }
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
                function showLocked() {
                  document.getElementById("locked").style.display = "block";
                  document.getElementById("status").textContent = "Terkunci";
                }
                refreshRoom();
                window.setInterval(refreshRoom, 4000);
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun text(status: Response.IStatus, body: String): Response {
        return newFixedLengthResponse(status, MIME_PLAINTEXT, body)
    }

    private fun html(status: Response.IStatus, body: String): Response {
        return newFixedLengthResponse(status, "text/html; charset=utf-8", body)
    }

    private fun json(status: Response.IStatus, body: String): Response {
        return newFixedLengthResponse(status, "application/json; charset=utf-8", body)
    }
}
