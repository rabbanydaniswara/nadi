package com.danis.nadi

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.history.TransferHistoryItem
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.danis.nadi.network.hotspot.HotspotState
import com.danis.nadi.room.ActiveRoom
import com.danis.nadi.room.NetworkMode
import com.danis.nadi.room.RoomController
import com.danis.nadi.room.RoomLifecycleState
import com.danis.nadi.room.RoomLifecycleService
import com.danis.nadi.room.RoomRuntime
import com.danis.nadi.room.RoomStartResult
import com.danis.nadi.util.QrCodeGenerator
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private lateinit var controller: RoomController
    private val dashboardHandler = Handler(Looper.getMainLooper())
    private var dashboardPolling = false
    private var pendingHotspotStart = false

    private lateinit var homePanel: LinearLayout
    private lateinit var historyPanel: LinearLayout
    private lateinit var setupPanel: LinearLayout
    private lateinit var activeRoomPanel: LinearLayout
    private lateinit var networkModeGroup: RadioGroup
    private lateinit var roomNameInput: EditText
    private lateinit var hostNameInput: EditText
    private lateinit var hostChatInput: EditText
    private lateinit var activeStatusText: TextView
    private lateinit var activeRoomNameText: TextView
    private lateinit var activeRoomCopyText: TextView
    private lateinit var joinUrlText: TextView
    private lateinit var sharedFilesText: TextView
    private lateinit var receivedFilesText: TextView
    private lateinit var chatMessagesText: TextView
    private lateinit var historyListText: TextView
    private lateinit var recentEmptyText: TextView
    private lateinit var networkModeHelpText: TextView
    private lateinit var qrImage: ImageView

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        persistReadPermissionIfPossible(uri, result.data)
        controller.createSharedTransfer(uri)
        refreshHostDashboard()
        Toast.makeText(this, "File siap dibagikan.", Toast.LENGTH_SHORT).show()
    }

    private val hotspotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = requiredHotspotPermissions().all { result[it] == true || hasPermission(it) }
        if (pendingHotspotStart && granted) {
            pendingHotspotStart = false
            startLocalRoomWithMode(NetworkMode.HOTSPOT)
        } else if (pendingHotspotStart) {
            pendingHotspotStart = false
            Toast.makeText(
                this,
                "Izin belum diberikan. Nadi memakai mode satu Wi-Fi.",
                Toast.LENGTH_LONG
            ).show()
            startLocalRoomWithMode(NetworkMode.SAME_WIFI)
        }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (dashboardPolling) {
                refreshHostDashboard()
                dashboardHandler.postDelayed(this, 1500L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = RoomRuntime.controller(applicationContext)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        bindActions()
        val activeRoom = controller.currentActiveRoom()
        if (activeRoom != null) {
            renderActiveRoom(activeRoom)
            startDashboardPolling()
        } else {
            showHome()
        }
    }

    override fun onDestroy() {
        stopDashboardPolling()
        super.onDestroy()
    }

    private fun bindViews() {
        homePanel = findViewById(R.id.homePanel)
        historyPanel = findViewById(R.id.historyPanel)
        setupPanel = findViewById(R.id.setupPanel)
        activeRoomPanel = findViewById(R.id.activeRoomPanel)
        networkModeGroup = findViewById(R.id.networkModeGroup)
        roomNameInput = findViewById(R.id.roomNameInput)
        hostNameInput = findViewById(R.id.hostNameInput)
        hostChatInput = findViewById(R.id.hostChatInput)
        activeStatusText = findViewById(R.id.activeStatusText)
        activeRoomNameText = findViewById(R.id.activeRoomNameText)
        activeRoomCopyText = findViewById(R.id.activeRoomCopyText)
        joinUrlText = findViewById(R.id.joinUrlText)
        sharedFilesText = findViewById(R.id.sharedFilesText)
        receivedFilesText = findViewById(R.id.receivedFilesText)
        chatMessagesText = findViewById(R.id.chatMessagesText)
        historyListText = findViewById(R.id.historyListText)
        recentEmptyText = findViewById(R.id.recentEmptyText)
        networkModeHelpText = findViewById(R.id.networkModeHelpText)
        qrImage = findViewById(R.id.qrImage)
    }

    private fun bindActions() {
        findViewById<MaterialButton>(R.id.createRoomButton).setOnClickListener {
            showSetup()
        }
        findViewById<MaterialButton>(R.id.joinRoomButton).setOnClickListener {
            showHistory()
        }
        findViewById<MaterialButton>(R.id.historyBackButton).setOnClickListener {
            showHome()
        }
        findViewById<MaterialButton>(R.id.clearHistoryButton).setOnClickListener {
            controller.clearHistory()
            refreshHostDashboard()
            refreshHistoryScreen()
            Toast.makeText(this, "Riwayat lokal dihapus.", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.setupBackButton).setOnClickListener {
            showHome()
        }
        findViewById<MaterialButton>(R.id.startLocalRoomButton).setOnClickListener {
            startLocalRoom()
        }
        findViewById<MaterialButton>(R.id.copyUrlButton).setOnClickListener {
            copyJoinUrl()
        }
        findViewById<MaterialButton>(R.id.stopRoomButton).setOnClickListener {
            stopActiveRoom()
        }
        findViewById<MaterialButton>(R.id.addSharedFileButton).setOnClickListener {
            openFilePicker()
        }
        findViewById<MaterialButton>(R.id.sendHostMessageButton).setOnClickListener {
            sendHostMessage()
        }
        networkModeGroup.setOnCheckedChangeListener { _, checkedId ->
            networkModeHelpText.text = if (checkedId == R.id.hotspotModeRadio) {
                getString(R.string.hotspot_permission_reason)
            } else {
                getString(R.string.same_wifi_note)
            }
        }
    }

    private fun startLocalRoom() {
        val mode = if (networkModeGroup.checkedRadioButtonId == R.id.hotspotModeRadio) {
            NetworkMode.HOTSPOT
        } else {
            NetworkMode.SAME_WIFI
        }
        if (mode == NetworkMode.HOTSPOT && !hasHotspotPermissions()) {
            pendingHotspotStart = true
            hotspotPermissionLauncher.launch(requiredHotspotPermissions())
            return
        }
        startLocalRoomWithMode(mode)
    }

    private fun startLocalRoomWithMode(mode: NetworkMode) {
        stopDashboardPolling()
        val startResult = controller.prepareRoom(
            roomName = roomNameInput.text?.toString().orEmpty(),
            hostName = hostNameInput.text?.toString().orEmpty(),
            mode = mode
        )
        when (startResult) {
            is RoomStartResult.Failed -> {
                Toast.makeText(this, startResult.message, Toast.LENGTH_LONG).show()
                showSetup()
            }
            is RoomStartResult.Prepared -> {
                if (mode == NetworkMode.HOTSPOT) {
                    startHotspotThenActivate(startResult.session)
                } else {
                    activateRoom(startResult.session, NetworkMode.SAME_WIFI, null, null)
                }
            }
        }
    }

    private fun startHotspotThenActivate(preparingSession: RoomSession) {
        activeRoomCopyText.text = "Menyiapkan hotspot lokal..."
        controller.hotspotManager.start { state ->
            when (state) {
                HotspotState.Idle -> Unit
                HotspotState.Starting -> {
                    Toast.makeText(this, "Menyiapkan hotspot lokal...", Toast.LENGTH_SHORT).show()
                }
                is HotspotState.Active -> {
                    dashboardHandler.postDelayed(
                        {
                            activateRoom(
                                preparingSession = preparingSession,
                                mode = NetworkMode.HOTSPOT,
                                hotspotSsid = state.ssid,
                                hotspotPassword = state.password
                            )
                        },
                        HOTSPOT_ADDRESS_SETTLE_DELAY_MS
                    )
                }
                is HotspotState.Failed -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    activateRoom(
                        preparingSession = preparingSession,
                        mode = NetworkMode.SAME_WIFI_FALLBACK,
                        hotspotSsid = null,
                        hotspotPassword = null
                    )
                }
            }
        }
    }

    private fun activateRoom(
        preparingSession: RoomSession,
        mode: NetworkMode,
        hotspotSsid: String?,
        hotspotPassword: String?
    ) {
        val activeRoom = controller.activatePreparedRoom(
            preparingSession = preparingSession,
            mode = mode,
            ssid = hotspotSsid,
            password = hotspotPassword
        )
        RoomLifecycleService.start(this)
        renderActiveRoom(activeRoom)
        startDashboardPolling()
    }

    private fun renderActiveRoom(activeRoom: ActiveRoom) {
        val session = activeRoom.session
        val joinUrl = session.localUrl.orEmpty()
        val clientCount = controller.roomManager.snapshot().clients.size
        activeStatusText.text = buildStatusLine(activeRoom.mode, clientCount)
        activeRoomNameText.text = session.roomName
        activeRoomCopyText.text = when (activeRoom.mode) {
            NetworkMode.HOTSPOT -> buildString {
                append(getString(R.string.hotspot_note))
                if (!activeRoom.hotspotSsid.isNullOrBlank()) append("\nWi-Fi: ").append(activeRoom.hotspotSsid)
                if (!activeRoom.hotspotPassword.isNullOrBlank()) append("\nPassword: ").append(activeRoom.hotspotPassword)
            }
            NetworkMode.SAME_WIFI_FALLBACK -> getString(R.string.hotspot_fallback_note)
            NetworkMode.SAME_WIFI -> getString(R.string.same_wifi_note)
        }
        joinUrlText.text = joinUrl
        if (joinUrl.isNotBlank()) {
            val qrSize = (220 * resources.displayMetrics.density).toInt()
            qrImage.setImageBitmap(QrCodeGenerator.generate(joinUrl, qrSize))
        } else {
            qrImage.setImageDrawable(null)
        }
        refreshHostDashboard()
        showActiveRoom()
    }

    private fun buildStatusLine(mode: NetworkMode, clientCount: Int): String {
        val modeLabel = when (mode) {
            NetworkMode.HOTSPOT -> "Hotspot lokal"
            NetworkMode.SAME_WIFI_FALLBACK -> "Satu Wi-Fi fallback"
            NetworkMode.SAME_WIFI -> "Satu Wi-Fi"
        }
        return "Aktif - $modeLabel - $clientCount perangkat"
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        filePicker.launch(intent)
    }

    private fun persistReadPermissionIfPossible(uri: Uri, data: Intent?) {
        val flags = data?.flags ?: return
        val takeFlags = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (takeFlags != 0) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun sendHostMessage() {
        val text = hostChatInput.text?.toString().orEmpty()
        val hostName = controller.roomManager.currentSession()?.hostName ?: getString(R.string.host_name_default)
        val message = controller.roomManager.addMessage(
            senderId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "host",
            senderName = hostName,
            text = text
        )
        if (message == null) {
            Toast.makeText(this, "Pesan masih kosong.", Toast.LENGTH_SHORT).show()
            return
        }
        hostChatInput.text?.clear()
        refreshHostDashboard()
    }

    private fun refreshHostDashboard() {
        val snapshot = controller.roomManager.snapshot()
        val shared = controller.roomManager.sharedFiles()
        val received = controller.roomManager.receivedFiles()
        val messages = snapshot.messages.takeLast(8)
        if (controller.lifecycleState == RoomLifecycleState.ACTIVE) {
            controller.persistRecentTransfers()
            activeStatusText.text = buildStatusLine(controller.activeNetworkMode, snapshot.clients.size)
        }
        sharedFilesText.text = if (shared.isEmpty()) {
            getString(R.string.shared_files_empty)
        } else {
            shared.joinToString(separator = "\n\n") { it.displayLine() }
        }
        receivedFilesText.text = if (received.isEmpty()) {
            getString(R.string.received_files_empty)
        } else {
            received.joinToString(separator = "\n\n") { it.displayLine() }
        }
        chatMessagesText.text = if (messages.isEmpty()) {
            getString(R.string.chat_empty)
        } else {
            messages.joinToString(separator = "\n\n") { "${it.senderName}: ${it.text}" }
        }
        val recent = controller.roomManager.recentTransfers()
        val history = controller.recentHistory()
        recentEmptyText.text = if (recent.isNotEmpty()) {
            recent.joinToString(separator = "\n") { it.displayLine() }
        } else if (history.isNotEmpty()) {
            history.take(5).joinToString(separator = "\n") { it.displayLine() }
        } else {
            getString(R.string.recent_empty)
        }
    }

    private fun refreshHistoryScreen() {
        val history = controller.recentHistory()
        historyListText.text = if (history.isEmpty()) {
            getString(R.string.history_empty)
        } else {
            history.joinToString(separator = "\n\n") { it.displayLine() }
        }
    }

    private fun TransferItem.displayLine(): String {
        return "${direction.label()} - ${fileName}\n${FileSizeFormatter.format(sizeBytes)} - ${status.label(progress)}"
    }

    private fun TransferHistoryItem.displayLine(): String {
        return "${direction.label()} - ${fileName}\n${FileSizeFormatter.format(sizeBytes)} - ${status.label(progress)} - tersimpan lokal"
    }

    private fun TransferDirection.label(): String {
        return when (this) {
            TransferDirection.SHARED -> "Dibagikan"
            TransferDirection.UPLOAD -> "Diterima"
            TransferDirection.DOWNLOAD -> "Diunduh"
        }
    }

    private fun TransferStatus.label(progress: Int): String {
        return when (this) {
            TransferStatus.PENDING -> "Menunggu"
            TransferStatus.RUNNING -> "Berjalan $progress%"
            TransferStatus.SUCCESS -> "Selesai"
            TransferStatus.FAILED -> "Gagal"
        }
    }

    private fun startDashboardPolling() {
        dashboardPolling = true
        dashboardHandler.removeCallbacks(refreshRunnable)
        dashboardHandler.post(refreshRunnable)
    }

    private fun stopDashboardPolling() {
        dashboardPolling = false
        dashboardHandler.removeCallbacks(refreshRunnable)
    }

    private fun stopActiveRoom() {
        stopDashboardPolling()
        controller.stopActiveRoom()
        RoomLifecycleService.stop(this)
        Toast.makeText(this, "Ruang sudah ditutup.", Toast.LENGTH_SHORT).show()
        showHome()
    }

    private fun hasHotspotPermissions(): Boolean {
        return requiredHotspotPermissions().all { hasPermission(it) }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requiredHotspotPermissions(): Array<String> {
        return buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()
    }

    private fun copyJoinUrl() {
        val url = joinUrlText.text?.toString().orEmpty()
        if (url.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nadi room URL", url))
        Toast.makeText(this, "URL ruang disalin.", Toast.LENGTH_SHORT).show()
    }

    private fun showHome() {
        homePanel.visible()
        historyPanel.gone()
        setupPanel.gone()
        activeRoomPanel.gone()
        refreshHostDashboard()
    }

    private fun showHistory() {
        homePanel.gone()
        historyPanel.visible()
        setupPanel.gone()
        activeRoomPanel.gone()
        refreshHistoryScreen()
    }

    private fun showSetup() {
        homePanel.gone()
        historyPanel.gone()
        setupPanel.visible()
        activeRoomPanel.gone()
    }

    private fun showActiveRoom() {
        homePanel.gone()
        historyPanel.gone()
        setupPanel.gone()
        activeRoomPanel.visible()
    }

    private fun View.visible() {
        visibility = View.VISIBLE
    }

    private fun View.gone() {
        visibility = View.GONE
    }
}

private const val HOTSPOT_ADDRESS_SETTLE_DELAY_MS = 1500L
