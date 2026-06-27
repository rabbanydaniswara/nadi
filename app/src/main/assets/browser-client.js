const params = new URLSearchParams(window.location.search);
const token = params.get("token") || "";
const pinKey = "nadiRoomPin:" + window.location.host + window.location.pathname;
const clientIdKey = "nadiClientId";
const clientNimKey = "nadiClientNim";
const clientNameKey = "nadiClientName";
const maxFileRoomUploadBytes = 100 * 1024 * 1024;
const maxChatAttachmentBytes = 10 * 1024 * 1024;
const nimInput = document.getElementById("nimInput");
const nameInput = document.getElementById("nameInput");
const pinInput = document.getElementById("pinInput");
const pinStatus = document.getElementById("pinStatus");
const identityStatus = document.getElementById("identityStatus");
const identitySummary = document.getElementById("identitySummary");
const attachmentStatus = document.getElementById("attachmentStatus");
nimInput.value = localStorage.getItem(clientNimKey) || "";
nameInput.value = localStorage.getItem(clientNameKey) || "";
let accessPin = localStorage.getItem(pinKey) || "";
let preferPinAccess = false;
pinInput.value = accessPin;
let latestMessageAt = 0;
let seenMessages = new Set();
let messageSignatures = {};
let forceChatScrollToBottom = false;
let activeClientTab = "files";
let chatSocket = null;
let chatPollingTimer = null;
let chatReconnectTimer = null;
let chatKeepAliveTimer = null;
let chatConnectionStatusText = "";
let chatReconnectAttempts = 0;
const chatReconnectDelayMs = 2500;
const chatKeepAliveIntervalMs = 3000;
document.getElementById("currentUrl").textContent = window.location.href;

function switchClientTab(tab) {
  activeClientTab = tab;
  document.querySelectorAll(".tab-button").forEach(button => {
    button.classList.toggle("active", button.dataset.clientTab === tab);
  });
  document.getElementById("clientFilesSection").classList.toggle("hidden", tab !== "files");
  document.getElementById("clientChatSection").classList.toggle("hidden", tab !== "chat");
  document.getElementById("clientInfoSection").classList.toggle("hidden", tab !== "info");
  if (tab === "files") refreshFiles();
  if (tab === "chat") {
    refreshChat();
    requestAnimationFrame(scrollMessagesToBottom);
  }
}

function messagesHolder() {
  return document.getElementById("messages");
}

function isMessagesNearBottom() {
  const holder = messagesHolder();
  return holder.scrollHeight - holder.scrollTop - holder.clientHeight <= 48;
}

function scrollMessagesToBottom() {
  const holder = messagesHolder();
  holder.scrollTop = holder.scrollHeight;
}

function openImageLightbox(src, name) {
  const lightbox = document.getElementById("imageLightbox");
  const image = document.getElementById("lightboxImage");
  const caption = document.getElementById("lightboxCaption");
  image.src = src;
  image.alt = name || "Preview gambar";
  caption.textContent = name || "";
  lightbox.classList.remove("hidden");
}

function closeImageLightbox() {
  const lightbox = document.getElementById("imageLightbox");
  const image = document.getElementById("lightboxImage");
  lightbox.classList.add("hidden");
  image.removeAttribute("src");
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
function hasAccessCredential() {
  return Boolean(token || accessPin);
}
function accessQuery() {
  if ((preferPinAccess || !token) && accessPin) return "pin=" + encodeURIComponent(accessPin);
  return "token=" + encodeURIComponent(token);
}
function clientQuery() {
  return "clientId=" + encodeURIComponent(clientId());
}
function setRoomButtonsDisabled(disabled) {
  document.querySelectorAll("button").forEach(button => {
    if (button.id === "savePinButton") return;
    button.disabled = disabled;
  });
}
function showPinPrompt(message) {
  document.getElementById("pinCard").classList.remove("hidden");
  pinStatus.textContent = message || "Masukkan PIN dari host Nadi untuk membuka room.";
  pinStatus.className = "muted";
  setRoomButtonsDisabled(true);
}
function hidePinPrompt() {
  document.getElementById("pinCard").classList.add("hidden");
  pinStatus.textContent = "";
}
function updateIdentityUi(locked) {
  const nim = localStorage.getItem(clientNimKey) || "";
  const name = localStorage.getItem(clientNameKey) || "";
  const complete = Boolean(nim && name) && !locked;
  document.getElementById("identityCard").style.display = complete ? "none" : "block";
  identitySummary.textContent = complete ? (nim + " - " + name) : "";
  identitySummary.className = complete ? "identity-summary" : "identity-summary hidden";
  document.getElementById("activeIdentityText").textContent = complete ? (nim + " - " + name) : "Belum masuk";
  document.getElementById("saveIdentityButton").disabled = false;
  document.querySelectorAll("button").forEach(button => {
    if (button.id !== "saveIdentityButton" && button.id !== "savePinButton") button.disabled = !complete;
  });
}
function savePin() {
  const value = pinInput.value.trim();
  if (!/^\d{4,8}$/.test(value)) {
    pinStatus.textContent = "PIN harus 4-8 digit angka.";
    pinStatus.className = "error";
    return;
  }
  accessPin = value;
  preferPinAccess = true;
  localStorage.setItem(pinKey, value);
  pinStatus.textContent = "Memeriksa PIN...";
  pinStatus.className = "muted";
  refreshRoom(); refreshFiles(); restartChatRealtime();
}
async function registerIdentity() {
  if (!hasAccessCredential()) { showPinPrompt(); return; }
  const nim = nimInput.value.trim().replace(/\s+/g, "");
  const name = nameInput.value.trim().replace(/\s+/g, " ");
  if (nim.length < 3 || name.length < 2) {
    identityStatus.textContent = "Isi NIM dan nama lengkap terlebih dahulu.";
    identityStatus.className = "error";
    return;
  }
  const body = new URLSearchParams({ clientId: clientId(), nim, name });
  const response = await fetch("/api/identity?" + accessQuery(), {
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
  refreshRoom(); refreshFiles(); restartChatRealtime();
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
function formatTime(timestamp) {
  const date = new Date(Number(timestamp) || Date.now());
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  return hours + ":" + minutes;
}
function statusLabel(status) {
  switch (String(status || "").toLowerCase()) {
    case "success": return "Tersedia";
    case "downloaded": return "Diunduh";
    case "expired": return "Kedaluwarsa";
    case "failed": return "Gagal";
    case "running": return "Berjalan";
    case "pending": return "Menunggu";
    default: return "";
  }
}
function friendlyError(errorCode, fallback) {
  switch (String(errorCode || "")) {
    case "file_too_large": return "File terlalu besar untuk File Room.";
    case "attachment_not_allowed": return "Lampiran ditolak. Gunakan gambar, dokumen, teks, atau zip maksimal 10 MB.";
    case "chat_attachment_storage_full": return "Storage lampiran chat room sudah penuh. Minta host membersihkan lampiran lama.";
    case "file_expired": return "Lampiran sudah kedaluwarsa dan dibersihkan dari host.";
    case "identity_required": return "Isi NIM dan Nama dulu sebelum memakai fitur ini.";
    case "invalid_token": return "Akses room tidak valid. Minta QR/link terbaru atau masukkan PIN.";
    case "unsafe_file_name": return "Nama file tidak aman. Ubah nama file lalu coba lagi.";
    default: return fallback;
  }
}
async function responseErrorMessage(response, fallback) {
  try {
    const payload = await response.clone().json();
    return friendlyError(payload.error, fallback);
  } catch (error) {
    return fallback;
  }
}
function xhrErrorMessage(xhr, fallback) {
  try {
    const payload = JSON.parse(xhr.responseText || "{}");
    return friendlyError(payload.error, fallback);
  } catch (error) {
    return fallback;
  }
}
function safeId(value) {
  return String(value ?? "").replace(/[^a-zA-Z0-9_-]/g, "_");
}
function attachmentUrl(id, preview = false) {
  const previewQuery = preview ? "&preview=1" : "";
  return "/api/download/" + encodeURIComponent(id) + "?" + accessQuery() + "&" + clientQuery() + previewQuery;
}
function messageSignature(message) {
  return [
    message.text || "",
    message.attachmentTransferId || "",
    message.attachmentFileName || "",
    message.attachmentStatus || ""
  ].join("|");
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
  const status = statusLabel(message.attachmentStatus);
  const meta = [formatBytes(size), status].filter(Boolean).join(" - ");
  const url = attachmentUrl(id, true);
  const expired = String(message.attachmentStatus || "").toLowerCase() === "expired";
  const statusBadge = status ? `<span class="attachment-status${expired ? " expired" : ""}">${esc(status)}</span>` : "";
  const downloadButton = expired
    ? `<p id="${esc(statusId)}" class="muted">Lampiran sudah kedaluwarsa.</p>`
    : `<button type="button" class="chatAttachmentDownload" data-id="${esc(id)}" data-name="${esc(name)}" data-status="${esc(statusId)}">Download</button><p id="${esc(statusId)}" class="muted"></p>`;
  if (isImageAttachment(message)) {
    const preview = expired
      ? `<strong>${esc(name)}</strong>`
      : `<button type="button" class="imagePreviewButton image-preview-button" data-src="${esc(url)}" data-name="${esc(name)}"><img class="chat-image" src="${esc(url)}" alt="Preview ${esc(name)}" loading="lazy"></button>`;
    return `<div class="attachment-card${expired ? " expired" : ""} image-only-card">${preview}</div>`;
  }
  const cardClass = expired ? "attachment-card expired" : "attachment-card chatAttachmentCard";
  const role = expired ? "" : ` role="button" tabindex="0"`;
  const helper = expired ? "Lampiran sudah kedaluwarsa." : "Ketuk untuk download, lalu buka dari hasil download browser.";
  return `<div class="${cardClass}"${role} data-id="${esc(id)}" data-name="${esc(name)}" data-status="${esc(statusId)}"><strong>${esc(name)}</strong><p>${meta} - ${helper}</p>${statusBadge}<div class="attachment-actions">${downloadButton}</div></div>`;
}
async function refreshRoom() {
  if (!hasAccessCredential()) { showPinPrompt(); return; }
  try {
    const response = await fetch("/api/room?" + accessQuery() + "&" + clientQuery());
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
    document.getElementById("infoRoomName").textContent = room.roomName;
    document.getElementById("infoHostName").textContent = "Host: " + room.hostName;
    document.getElementById("infoConnection").textContent = "Status: " + (room.status === "active" ? "Siap" : room.status);
    if (room.chatAttachmentStorage) {
      const storage = room.chatAttachmentStorage;
      document.getElementById("chatStorageText").textContent =
        "Lampiran chat: " + storage.availableCount + " aktif, " +
        formatBytes(storage.totalBytes) + " / " + formatBytes(storage.maxBytes) + ".";
    }
    document.getElementById("locked").style.display = "none";
    hidePinPrompt();
    updateIdentityUi(room.identityRequired);
  } catch (error) {
    document.getElementById("status").textContent = "Terputus";
    document.getElementById("roomCopy").textContent = "Koneksi lokal ke host terputus. Pastikan perangkat masih berada di jaringan yang sama.";
    document.getElementById("infoConnection").textContent = "Status: terputus dari host.";
    setChatRealtimeStatus("Terputus dari host", "offline");
  }
}
async function refreshFiles() {
  if (!hasAccessCredential()) return;
  if (!hasIdentity()) {
    document.getElementById("files").innerHTML = `<div class="file-empty">Isi identitas dulu untuk melihat File Room.</div>`;
    return;
  }
  const response = await fetch("/api/files?" + accessQuery() + "&" + clientQuery());
  if (!response.ok) return;
  const payload = await response.json();
  const files = payload.files || [];
  document.getElementById("files").innerHTML = files.length
    ? files.map(file => {
        const rowId = safeId(file.transferId);
        return `<div class="file-row"><strong>${esc(file.fileName)}</strong><p class="muted">${formatBytes(file.sizeBytes)} - ${esc(file.status)}</p><button type="button" class="downloadButton" data-id="${esc(file.transferId)}" data-name="${esc(file.fileName)}">Download</button><p id="downloadStatus-${rowId}" class="muted"></p><progress id="downloadProgress-${rowId}" value="0" max="100" style="display:none"></progress></div>`;
      }).join("")
    : `<div class="file-empty">Belum ada file dari host.</div>`;
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
    const response = await fetch("/api/download/" + encodeURIComponent(id) + "?" + accessQuery() + "&" + clientQuery());
    if (!response.ok) throw new Error(await responseErrorMessage(response, "Download gagal. Pastikan room masih aktif."));
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
    status.textContent = error.message || "Download gagal. Pastikan room masih aktif.";
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
    if (!response.ok) throw new Error(await responseErrorMessage(response, "Download lampiran gagal. Pastikan room masih aktif."));
    const blob = await response.blob();
    saveBlob(blob, name || "lampiran-nadi");
    status.textContent = "Download selesai. Buka file dari folder download browser.";
    status.className = "success";
  } catch (error) {
    status.textContent = error.message || "Download gagal. Pastikan room masih aktif.";
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
  const file = input.files[0];
  if (file.size > maxFileRoomUploadBytes) {
    status.textContent = "File Room maksimal 100 MB per kiriman.";
    status.className = "error";
    return;
  }
  const data = new FormData();
  data.append("clientId", clientId());
  data.append("file", file, file.name);
  status.textContent = "Mengirim 0%...";
  progress.style.display = "block";
  progress.value = 0;
  const xhr = new XMLHttpRequest();
  xhr.open("POST", "/api/upload?" + accessQuery());
  xhr.upload.onprogress = event => {
    if (!event.lengthComputable) return;
    const percent = Math.round((event.loaded / event.total) * 100);
    progress.value = percent;
    status.textContent = "Mengirim " + percent + "%...";
  };
  xhr.onload = () => {
    const ok = xhr.status >= 200 && xhr.status < 300;
    status.textContent = ok ? "File terkirim ke host." : xhrErrorMessage(xhr, "File belum terkirim.");
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
function setChatRealtimeStatus(text, mode) {
  const status = document.getElementById("chatRealtimeStatus");
  if (!status) return;
  const nextClass = "realtime-status" + (mode ? " " + mode : "");
  if (chatConnectionStatusText === text && status.className === nextClass) return;
  chatConnectionStatusText = text;
  status.textContent = text;
  status.className = nextClass;
}
function chatWebSocketUrl() {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return protocol + "//" + window.location.host + "/ws/chat?" + accessQuery() + "&" + clientQuery();
}
function stopChatPolling() {
  if (chatPollingTimer) {
    window.clearInterval(chatPollingTimer);
    chatPollingTimer = null;
  }
}
function startChatPolling(intervalMs = 3000, showStatus = true) {
  if (!hasAccessCredential() || !hasIdentity()) {
    setChatRealtimeStatus("Menunggu identitas", "fallback");
    return;
  }
  if (!chatPollingTimer) {
    chatPollingTimer = window.setInterval(refreshChat, intervalMs);
  }
  if (showStatus) {
    setChatRealtimeStatus("Mode cadangan polling aktif", "fallback");
  }
}
function closeChatSocket() {
  if (chatReconnectTimer) {
    window.clearTimeout(chatReconnectTimer);
    chatReconnectTimer = null;
  }
  if (chatKeepAliveTimer) {
    window.clearInterval(chatKeepAliveTimer);
    chatKeepAliveTimer = null;
  }
  if (chatSocket) {
    const socket = chatSocket;
    chatSocket = null;
    socket.onopen = null;
    socket.onmessage = null;
    socket.onclose = null;
    socket.onerror = null;
    try { socket.close(); } catch (error) {}
  }
}
function scheduleChatReconnect() {
  if (chatReconnectTimer || !hasAccessCredential() || !hasIdentity()) return;
  chatReconnectAttempts += 1;
  if (chatReconnectAttempts >= 2) {
    setChatRealtimeStatus("Mode cadangan polling aktif", "fallback");
  } else {
    setChatRealtimeStatus("Realtime terputus, mencoba lagi...", "reconnecting");
  }
  chatReconnectTimer = window.setTimeout(() => {
    chatReconnectTimer = null;
    connectChatSocket();
  }, chatReconnectDelayMs);
}
let lastSocketActivityTime = 0;
function connectChatSocket() {
  if (!hasAccessCredential() || !hasIdentity()) {
    closeChatSocket();
    stopChatPolling();
    setChatRealtimeStatus("Menunggu identitas", "fallback");
    return;
  }
  if (!("WebSocket" in window)) {
    startChatPolling(1500);
    return;
  }
  if (chatSocket && (chatSocket.readyState === WebSocket.CONNECTING || chatSocket.readyState === WebSocket.OPEN)) return;
  if (chatReconnectAttempts === 0) {
    setChatRealtimeStatus("Menghubungkan realtime...", "");
  }
  try {
    chatSocket = new WebSocket(chatWebSocketUrl());
  } catch (error) {
    startChatPolling(1500);
    return;
  }
  chatSocket.onopen = () => {
    chatReconnectAttempts = 0;
    stopChatPolling();
    setChatRealtimeStatus("Realtime aktif", "active");
    refreshChat();
    lastSocketActivityTime = Date.now();
    if (chatKeepAliveTimer) window.clearInterval(chatKeepAliveTimer);
    chatKeepAliveTimer = window.setInterval(() => {
      if (chatSocket && chatSocket.readyState === WebSocket.OPEN) {
        if (Date.now() - lastSocketActivityTime > 8000) {
          // Zombie connection detected
          chatSocket.close();
        } else {
          chatSocket.send("ping");
        }
      }
    }, chatKeepAliveIntervalMs);
  };
  chatSocket.onmessage = event => {
    lastSocketActivityTime = Date.now();
    try {
      const payload = JSON.parse(event.data);
      if (payload.type === "chat_messages") {
        appendChatMessages(payload.messages || []);
      }
    } catch (error) {}
  };
  chatSocket.onclose = () => {
    if (chatKeepAliveTimer) {
      window.clearInterval(chatKeepAliveTimer);
      chatKeepAliveTimer = null;
    }
    chatSocket = null;
    startChatPolling(2000, false);
    scheduleChatReconnect();
  };
  chatSocket.onerror = () => {
    setChatRealtimeStatus("Realtime bermasalah, menunggu koneksi ulang...", "reconnecting");
  };
}
function restartChatRealtime() {
  closeChatSocket();
  stopChatPolling();
  chatReconnectAttempts = 0;
  refreshChat();
  connectChatSocket();
}
function appendChatMessages(messages) {
  const holder = messagesHolder();
  const shouldScrollToBottom = forceChatScrollToBottom || isMessagesNearBottom();
  forceChatScrollToBottom = false;
  const changedExisting = (messages || []).some(message =>
    seenMessages.has(message.messageId) && messageSignatures[message.messageId] !== messageSignature(message)
  );
  if (changedExisting) {
    holder.innerHTML = "";
    seenMessages.clear();
    messageSignatures = {};
    latestMessageAt = 0;
  }
  if (!seenMessages.size) holder.innerHTML = "";
  let appended = 0;
  for (const message of messages || []) {
    if (seenMessages.has(message.messageId)) continue;
    seenMessages.add(message.messageId);
    messageSignatures[message.messageId] = messageSignature(message);
    latestMessageAt = Math.max(latestMessageAt, message.createdAt);
    const div = document.createElement("div");
    div.className = "message" + (message.senderId === clientId() ? " mine" : "");
    const attachment = attachmentMarkup(message);
    const messageText = message.text ? `<p>${esc(message.text)}</p>` : "";
    div.innerHTML = `<strong>${esc(message.senderName)}</strong>${messageText}${attachment}<div class="message-meta">${formatTime(message.createdAt)}</div>`;
    holder.appendChild(div);
    appended += 1;
    div.querySelectorAll(".chatAttachmentDownload").forEach(button => {
      button.addEventListener("click", event => {
        event.stopPropagation();
        downloadChatAttachment(button.dataset.id, button.dataset.name, button.dataset.status);
      });
    });
    div.querySelectorAll(".chatAttachmentCard").forEach(card => {
      const download = () => downloadChatAttachment(card.dataset.id, card.dataset.name, card.dataset.status);
      card.addEventListener("click", download);
      card.addEventListener("keydown", event => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          download();
        }
      });
    });
    div.querySelectorAll(".imagePreviewButton").forEach(button => {
      button.addEventListener("click", () => openImageLightbox(button.dataset.src, button.dataset.name));
    });
  }
  if (!seenMessages.size) holder.innerHTML = `<div class="empty-state"><strong>Belum ada pesan</strong><span>Percakapan room akan muncul di sini.</span></div>`;
  if (shouldScrollToBottom && appended > 0) requestAnimationFrame(scrollMessagesToBottom);
}
async function refreshChat() {
  if (!hasAccessCredential()) return;
  if (!hasIdentity()) return;
  const response = await fetch("/api/chat?" + accessQuery() + "&" + clientQuery() + "&after=" + latestMessageAt);
  if (!response.ok) return;
  const payload = await response.json();
  appendChatMessages(payload.messages || []);
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
  const response = await fetch("/api/chat?" + accessQuery(), {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body
  });
  if (response.ok) {
    input.value = "";
    forceChatScrollToBottom = true;
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
  const file = input.files[0];
  if (file.size > maxChatAttachmentBytes) {
    attachmentStatus.textContent = "Lampiran chat maksimal 10 MB.";
    attachmentStatus.className = "error";
    return;
  }
  data.append("clientId", clientId());
  data.append("text", document.getElementById("chatInput").value.trim());
  data.append("file", file, file.name);
  attachmentStatus.textContent = "Mengirim lampiran...";
  attachmentStatus.className = "muted";
  const xhr = new XMLHttpRequest();
  xhr.open("POST", "/api/chat-attachment?" + accessQuery());
  xhr.onload = () => {
    const ok = xhr.status >= 200 && xhr.status < 300;
    attachmentStatus.textContent = ok ? "Lampiran terkirim di chat." : xhrErrorMessage(xhr, "Lampiran ditolak. Gunakan gambar/dokumen kecil.");
    attachmentStatus.className = ok ? "success" : "error";
    if (ok) {
      input.value = "";
      document.getElementById("chatInput").value = "";
      forceChatScrollToBottom = true;
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
  document.getElementById("roomCopy").textContent = "Link ini tidak lagi valid. Minta QR terbaru atau masukkan PIN room dari host Nadi.";
  setChatRealtimeStatus("Akses room terkunci", "offline");
  showPinPrompt("PIN belum cocok, atau link lama sudah ditutup.");
}
document.getElementById("savePinButton").addEventListener("click", savePin);
document.getElementById("saveIdentityButton").addEventListener("click", registerIdentity);
document.getElementById("uploadButton").addEventListener("click", uploadFile);
document.getElementById("sendButton").addEventListener("click", sendChat);
document.getElementById("sendAttachmentButton").addEventListener("click", sendChatAttachment);
document.getElementById("chatInput").addEventListener("keydown", event => { if (event.key === "Enter") sendChat(); });
document.getElementById("closeImageLightbox").addEventListener("click", closeImageLightbox);
document.getElementById("imageLightbox").addEventListener("click", event => {
  if (event.target.id === "imageLightbox") closeImageLightbox();
});
document.addEventListener("keydown", event => {
  if (event.key === "Escape") closeImageLightbox();
});
document.querySelectorAll(".tab-button").forEach(button => {
  button.addEventListener("click", () => switchClientTab(button.dataset.clientTab));
});
switchClientTab("files");
refreshRoom(); refreshFiles(); restartChatRealtime();
window.setInterval(refreshRoom, 6000);
window.setInterval(() => { if (activeClientTab === "files") refreshFiles(); }, 6000);
window.addEventListener("online", () => {
  setChatRealtimeStatus("Menghubungkan ulang...", "reconnecting");
  connectChatSocket();
});
window.addEventListener("offline", () => {
  setChatRealtimeStatus("Perangkat offline", "offline");
});
document.addEventListener("visibilitychange", () => {
  if (!document.hidden) {
    refreshRoom(); refreshFiles(); restartChatRealtime();
  }
});
