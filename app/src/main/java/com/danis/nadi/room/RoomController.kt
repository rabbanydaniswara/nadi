package com.danis.nadi.room

import android.content.Context
import android.net.Uri
import com.danis.nadi.diagnostics.DiagnosticSnapshot
import com.danis.nadi.file.AndroidFileStore
import com.danis.nadi.history.NadiHistoryStore
import com.danis.nadi.history.TransferHistoryItem
import com.danis.nadi.history.toHistoryItem
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.TransferItem
import com.danis.nadi.network.server.BrowserClientAsset
import com.danis.nadi.network.server.BrowserClientAssets
import com.danis.nadi.network.hotspot.LocalHotspotManager
import com.danis.nadi.network.server.NadiHttpServer
import com.danis.nadi.network.server.ServerFileRules
import com.danis.nadi.settings.NadiSettingsStore
import com.danis.nadi.util.NetworkAddress
import java.io.IOException

class RoomController(context: Context) {
    private val appContext = context.applicationContext
    val roomManager = RoomManager()
    private val settingsStore = NadiSettingsStore(appContext)
    val fileStore = AndroidFileStore(appContext) { settingsStore.settings().fileRoomTreeUri }
    val hotspotManager = LocalHotspotManager(appContext)

    private val historyStore = NadiHistoryStore(appContext)
    private var server: NadiHttpServer? = null
    private var serverPort: Int? = null

    var lifecycleState: RoomLifecycleState = RoomLifecycleState.IDLE
        private set
    var activeNetworkMode: NetworkMode = NetworkMode.SAME_WIFI
        private set
    var hotspotSsid: String? = null
        private set
    var hotspotPassword: String? = null
        private set

    fun prepareRoom(roomName: String, hostName: String, mode: NetworkMode, pin: String? = null): RoomStartResult {
        stopDashboardRuntime()
        activeNetworkMode = mode
        hotspotSsid = null
        hotspotPassword = null
        lifecycleState = RoomLifecycleState.PREPARING
        val session = roomManager.startPreparing(roomName = roomName, hostName = hostName, pin = pin)
        lifecycleState = RoomLifecycleState.STARTING_SERVER
        val start = startServerOnAvailablePort()
        return if (!start) {
            lifecycleState = RoomLifecycleState.FAILED
            roomManager.fail()
            RoomStartResult.Failed("Ruang belum bisa dibuat. Coba tutup aplikasi lain lalu mulai lagi.")
        } else {
            RoomStartResult.Prepared(session)
        }
    }

    fun activatePreparedRoom(
        preparingSession: RoomSession,
        mode: NetworkMode,
        ssid: String?,
        password: String?
    ): ActiveRoom {
        activeNetworkMode = mode
        hotspotSsid = ssid
        hotspotPassword = password
        lifecycleState = if (mode == NetworkMode.HOTSPOT) {
            RoomLifecycleState.STARTING_NETWORK
        } else {
            RoomLifecycleState.STARTING_SERVER
        }
        val joinUrl = buildJoinUrl(preparingSession.token, mode)
        val activeSession = roomManager.activate(joinUrl, ssid) ?: preparingSession.copy(localUrl = joinUrl)
        lifecycleState = RoomLifecycleState.ACTIVE
        return ActiveRoom(activeSession, mode, ssid, password)
    }

    fun regenerateAccessLink(): ActiveRoom? {
        if (lifecycleState != RoomLifecycleState.ACTIVE) return null
        val refreshed = roomManager.regenerateAccess { token ->
            buildJoinUrl(token, activeNetworkMode)
        } ?: return null
        return ActiveRoom(refreshed, activeNetworkMode, hotspotSsid, hotspotPassword)
    }

    fun createSharedTransfer(uri: Uri): TransferItem {
        val transfer = fileStore.createSharedTransfer(uri)
        roomManager.addTransfer(transfer)
        persistRecentTransfers()
        return transfer
    }

    fun persistRecentTransfers() {
        historyStore.saveRecentTransfers(roomManager.recentTransfers(20).map { it.toHistoryItem() })
    }

    fun recentHistory(): List<TransferHistoryItem> = historyStore.recentTransfers()

    fun clearHistory() {
        historyStore.clear()
    }

    fun cleanupExpiredChatAttachments(nowMillis: Long = System.currentTimeMillis()) {
        val cutoff = nowMillis - ServerFileRules.CHAT_ATTACHMENT_TTL_MILLIS
        roomManager.expireChatAttachmentsOlderThan(cutoff).forEach { transfer ->
            fileStore.deleteStoredFile(transfer)
        }
    }

    fun clearChatAttachments(): Int {
        val expired = roomManager.expireAllChatAttachments()
        expired.forEach { transfer ->
            fileStore.deleteStoredFile(transfer)
        }
        persistRecentTransfers()
        return expired.size
    }

    fun currentRoomFolderPath(folderName: String = "received"): String? {
        val roomId = roomManager.currentSession()?.sessionId ?: return null
        return fileStore.roomFolderLabel(roomId, folderName)
    }

    fun currentRoomFolderUri(folderName: String = "received"): Uri? {
        val roomId = roomManager.currentSession()?.sessionId ?: return null
        return fileStore.roomFolderUri(roomId, folderName)
    }

    fun diagnostics(): DiagnosticSnapshot {
        val snapshot = roomManager.snapshot()
        return DiagnosticSnapshot(
            lifecycleState = lifecycleState,
            networkMode = activeNetworkMode,
            serverPort = serverPort,
            joinUrl = snapshot.session?.localUrl,
            hotspotSsid = hotspotSsid,
            hotspotActive = activeNetworkMode == NetworkMode.HOTSPOT && hotspotSsid != null,
            clientCount = snapshot.clients.size,
            sharedFileCount = snapshot.transfers.count { it.direction == com.danis.nadi.model.TransferDirection.SHARED },
            receivedFileCount = snapshot.transfers.count { it.direction == com.danis.nadi.model.TransferDirection.UPLOAD },
            messageCount = snapshot.messages.size,
            localAddress = if (activeNetworkMode == NetworkMode.HOTSPOT) {
                NetworkAddress.localOnlyHotspotIpv4() ?: NetworkAddress.firstLocalIpv4()
            } else {
                NetworkAddress.firstLocalIpv4()
            }
        )
    }

    fun stopActiveRoom() {
        lifecycleState = RoomLifecycleState.STOPPING
        roomManager.expireAllChatAttachments().forEach { transfer ->
            fileStore.deleteStoredFile(transfer)
        }
        stopDashboardRuntime()
        roomManager.stopRoom()
        lifecycleState = RoomLifecycleState.STOPPED
    }

    fun currentActiveRoom(): ActiveRoom? {
        val session = roomManager.currentSession()?.takeIf { it.localUrl != null } ?: return null
        if (lifecycleState != RoomLifecycleState.ACTIVE) return null
        return ActiveRoom(session, activeNetworkMode, hotspotSsid, hotspotPassword)
    }

    private fun stopDashboardRuntime() {
        hotspotManager.stop()
        server?.stop()
        server = null
        serverPort = null
    }

    private fun startServerOnAvailablePort(): Boolean {
        val browserClientAssets = loadBrowserClientAssets()
        for (port in PORT_CANDIDATES) {
            val candidate = NadiHttpServer(
                port = port,
                roomManager = roomManager,
                fileStore = fileStore,
                browserClientHtml = {
                    browserClientAssets[BrowserClientAssets.HTML_FILE_NAME]?.content ?: BrowserClientAssets.html()
                },
                browserClientAsset = { fileName ->
                    browserClientAssets[fileName] ?: BrowserClientAssets.asset(fileName)
                }
            )
            try {
                candidate.start(NadiHttpServer.ROOM_SERVER_READ_TIMEOUT_MILLIS, false)
                server = candidate
                serverPort = port
                return true
            } catch (_: IOException) {
                candidate.stop()
            }
        }
        return false
    }

    private fun loadBrowserClientAssets(): Map<String, BrowserClientAsset> {
        return BrowserClientAssets.ASSET_FILE_NAMES.mapNotNull { fileName ->
            val content = runCatching {
                appContext.assets.open(fileName)
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrNull()
            content?.let {
                fileName to BrowserClientAsset(
                    fileName = fileName,
                    mimeType = BrowserClientAssets.mimeType(fileName),
                    content = it
                )
            }
        }
            .toMap()
    }

    private fun buildJoinUrl(token: String, mode: NetworkMode): String {
        val port = serverPort ?: DEFAULT_PORT
        val hostAddress = if (mode == NetworkMode.HOTSPOT) {
            NetworkAddress.localOnlyHotspotIpv4() ?: NetworkAddress.firstLocalIpv4()
        } else {
            NetworkAddress.firstLocalIpv4()
        } ?: LOOPBACK_ADDRESS
        return "http://$hostAddress:$port/?token=$token"
    }

    private companion object {
        const val DEFAULT_PORT = 8080
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        val PORT_CANDIDATES = listOf(8080, 8081, 8082, 8090)
    }
}

sealed class RoomStartResult {
    data class Prepared(val session: RoomSession) : RoomStartResult()
    data class Failed(val message: String) : RoomStartResult()
}

data class ActiveRoom(
    val session: RoomSession,
    val mode: NetworkMode,
    val hotspotSsid: String?,
    val hotspotPassword: String?
)
