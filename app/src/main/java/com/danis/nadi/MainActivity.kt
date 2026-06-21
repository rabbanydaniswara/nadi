package com.danis.nadi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.core.widget.NestedScrollView
import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.history.TransferHistoryItem
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.ConnectedClient
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
import com.danis.nadi.settings.NadiSettings
import com.danis.nadi.settings.NadiSettingsStore
import com.danis.nadi.util.QrCodeGenerator
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var controller: RoomController
    private lateinit var settingsStore: NadiSettingsStore
    private val dashboardHandler = Handler(Looper.getMainLooper())
    private val chatTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var dashboardPolling = false
    private var pendingHotspotStart = false
    private var activeRoomDestinationId = R.id.active_room_tab_room

    private lateinit var homePanel: LinearLayout
    private lateinit var mainScrollView: NestedScrollView
    private lateinit var activeRoomJoinScroll: NestedScrollView
    private lateinit var activeRoomFileScroll: NestedScrollView
    private lateinit var activeRoomParticipantsScroll: NestedScrollView
    private lateinit var activeRoomHistoryScroll: NestedScrollView
    private lateinit var joinPanel: LinearLayout
    private lateinit var joinWebPanel: LinearLayout
    private lateinit var historyPanel: LinearLayout
    private lateinit var settingsPanel: LinearLayout
    private lateinit var setupPanel: LinearLayout
    private lateinit var activeRoomPanel: LinearLayout
    private lateinit var activeRoomHeaderSection: LinearLayout
    private lateinit var activeRoomNavigation: BottomNavigationView
    private lateinit var activeRoomJoinSection: LinearLayout
    private lateinit var activeRoomPrivacySection: LinearLayout
    private lateinit var activeRoomParticipantsSection: LinearLayout
    private lateinit var activeRoomFileOverviewSection: LinearLayout
    private lateinit var activeRoomSharedFilesSection: LinearLayout
    private lateinit var activeRoomReceivedFilesSection: LinearLayout
    private lateinit var activeRoomChatSection: LinearLayout
    private lateinit var activeRoomDiagnosticsSection: LinearLayout
    private lateinit var activeRoomHistorySection: LinearLayout
    private lateinit var networkModeGroup: RadioGroup
    private lateinit var defaultNetworkModeGroup: RadioGroup
    private lateinit var roomNameInput: EditText
    private lateinit var hostNameInput: EditText
    private lateinit var roomUrlInput: EditText
    private lateinit var defaultHostNameInput: EditText
    private lateinit var fileRoomStorageText: TextView
    private lateinit var hostChatInput: EditText
    private lateinit var openFileRoomButton: MaterialButton
    private lateinit var activeStatusText: TextView
    private lateinit var activeRoomNameText: TextView
    private lateinit var activeRoomCopyText: TextView
    private lateinit var joinUrlText: TextView
    private lateinit var fileRoomSummaryText: TextView
    private lateinit var fileRoomLocationText: TextView
    private lateinit var sharedFilesText: TextView
    private lateinit var receivedFilesText: TextView
    private lateinit var chatMessagesScrollView: NestedScrollView
    private lateinit var chatMessagesContainer: LinearLayout
    private lateinit var participantSummaryText: TextView
    private lateinit var clientListText: TextView
    private lateinit var diagnosticsText: TextView
    private lateinit var historyListText: TextView
    private lateinit var activeRoomHistoryText: TextView
    private lateinit var recentEmptyText: TextView
    private lateinit var networkModeHelpText: TextView
    private lateinit var wifiQrTitleText: TextView
    private lateinit var joinedRoomUrlText: TextView
    private lateinit var qrImage: ImageView
    private lateinit var wifiQrImage: ImageView
    private lateinit var joinWebView: WebView
    private var webFileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var chatKeyboardCompactMode = false
    private var forceChatScrollToBottom = false
    private var lastRenderedChatRoomId: String? = null
    private val renderedChatMessageIds = linkedSetOf<String>()

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        persistReadPermissionIfPossible(uri, result.data)
        controller.createSharedTransfer(uri)
        refreshHostDashboard()
        Toast.makeText(this, "File siap dibagikan.", Toast.LENGTH_SHORT).show()
    }

    private val fileRoomFolderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        persistTreePermissionIfPossible(uri, result.data)
        settingsStore.save(settingsStore.settings().copy(fileRoomTreeUri = uri.toString()))
        applySettingsToSettingsScreen()
        Toast.makeText(this, "Folder File Room disimpan.", Toast.LENGTH_SHORT).show()
    }

    private val hostChatAttachmentPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        persistReadPermissionIfPossible(uri, result.data)
        sendHostChatAttachment(uri)
    }

    private val webFileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        webFileChooserCallback?.onReceiveValue(uris)
        webFileChooserCallback = null
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        Unit
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
        settingsStore = NadiSettingsStore(applicationContext)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        bindViews()
        setupWindowInsets()
        setupJoinWebView()
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
        mainScrollView = findViewById(R.id.mainScrollView)
        activeRoomJoinScroll = findViewById(R.id.activeRoomJoinScroll)
        activeRoomFileScroll = findViewById(R.id.activeRoomFileScroll)
        activeRoomParticipantsScroll = findViewById(R.id.activeRoomParticipantsScroll)
        activeRoomHistoryScroll = findViewById(R.id.activeRoomHistoryScroll)
        homePanel = findViewById(R.id.homePanel)
        joinPanel = findViewById(R.id.joinPanel)
        joinWebPanel = findViewById(R.id.joinWebPanel)
        historyPanel = findViewById(R.id.historyPanel)
        settingsPanel = findViewById(R.id.settingsPanel)
        setupPanel = findViewById(R.id.setupPanel)
        activeRoomPanel = findViewById(R.id.activeRoomPanel)
        activeRoomHeaderSection = findViewById(R.id.activeRoomHeaderSection)
        activeRoomNavigation = findViewById(R.id.activeRoomNavigation)
        activeRoomJoinSection = findViewById(R.id.activeRoomJoinSection)
        activeRoomPrivacySection = findViewById(R.id.activeRoomPrivacySection)
        activeRoomParticipantsSection = findViewById(R.id.activeRoomParticipantsSection)
        activeRoomFileOverviewSection = findViewById(R.id.activeRoomFileOverviewSection)
        activeRoomSharedFilesSection = findViewById(R.id.activeRoomSharedFilesSection)
        activeRoomReceivedFilesSection = findViewById(R.id.activeRoomReceivedFilesSection)
        activeRoomChatSection = findViewById(R.id.activeRoomChatSection)
        activeRoomDiagnosticsSection = findViewById(R.id.activeRoomDiagnosticsSection)
        activeRoomHistorySection = findViewById(R.id.activeRoomHistorySection)
        networkModeGroup = findViewById(R.id.networkModeGroup)
        defaultNetworkModeGroup = findViewById(R.id.defaultNetworkModeGroup)
        roomNameInput = findViewById(R.id.roomNameInput)
        hostNameInput = findViewById(R.id.hostNameInput)
        roomUrlInput = findViewById(R.id.roomUrlInput)
        defaultHostNameInput = findViewById(R.id.defaultHostNameInput)
        fileRoomStorageText = findViewById(R.id.fileRoomStorageText)
        hostChatInput = findViewById(R.id.hostChatInput)
        openFileRoomButton = findViewById(R.id.openFileRoomButton)
        activeStatusText = findViewById(R.id.activeStatusText)
        activeRoomNameText = findViewById(R.id.activeRoomNameText)
        activeRoomCopyText = findViewById(R.id.activeRoomCopyText)
        joinUrlText = findViewById(R.id.joinUrlText)
        fileRoomSummaryText = findViewById(R.id.fileRoomSummaryText)
        fileRoomLocationText = findViewById(R.id.fileRoomLocationText)
        sharedFilesText = findViewById(R.id.sharedFilesText)
        receivedFilesText = findViewById(R.id.receivedFilesText)
        chatMessagesScrollView = findViewById(R.id.chatMessagesScrollView)
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer)
        participantSummaryText = findViewById(R.id.participantSummaryText)
        clientListText = findViewById(R.id.clientListText)
        diagnosticsText = findViewById(R.id.diagnosticsText)
        historyListText = findViewById(R.id.historyListText)
        activeRoomHistoryText = findViewById(R.id.activeRoomHistoryText)
        recentEmptyText = findViewById(R.id.recentEmptyText)
        networkModeHelpText = findViewById(R.id.networkModeHelpText)
        wifiQrTitleText = findViewById(R.id.wifiQrTitleText)
        joinedRoomUrlText = findViewById(R.id.joinedRoomUrlText)
        qrImage = findViewById(R.id.qrImage)
        wifiQrImage = findViewById(R.id.wifiQrImage)
        joinWebView = findViewById(R.id.joinWebView)
    }

    private fun setupWindowInsets() {
        val rootLayout = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomPadding = maxOf(systemBars.bottom, ime.bottom)
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding)

            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val compactChat = imeVisible && hostChatInput.hasFocus() && activeRoomDestinationId == R.id.active_room_tab_chat

            setChatKeyboardCompactMode(compactChat)
            if (compactChat) {
                chatMessagesScrollView.post {
                    chatMessagesScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
            insets
        }
    }

    private fun bindActions() {
        findViewById<MaterialButton>(R.id.createRoomButton).setOnClickListener {
            showSetup()
        }
        findViewById<MaterialButton>(R.id.joinRoomButton).setOnClickListener {
            showJoin()
        }
        findViewById<MaterialButton>(R.id.homeHistoryButton).setOnClickListener {
            showHistory()
        }
        findViewById<MaterialButton>(R.id.joinBackButton).setOnClickListener {
            showHome()
        }
        findViewById<MaterialButton>(R.id.openRoomButton).setOnClickListener {
            openJoinedRoom()
        }
        findViewById<MaterialButton>(R.id.pasteRoomUrlButton).setOnClickListener {
            pasteRoomUrl()
        }
        findViewById<MaterialButton>(R.id.closeJoinedRoomButton).setOnClickListener {
            closeJoinedRoom()
        }
        findViewById<MaterialButton>(R.id.settingsButton).setOnClickListener {
            showSettings()
        }
        findViewById<MaterialButton>(R.id.historyBackButton).setOnClickListener {
            showHome()
        }
        findViewById<MaterialButton>(R.id.settingsBackButton).setOnClickListener {
            showHome()
        }
        findViewById<MaterialButton>(R.id.saveSettingsButton).setOnClickListener {
            saveSettings()
        }
        findViewById<MaterialButton>(R.id.chooseFileRoomFolderButton).setOnClickListener {
            openFileRoomFolderPicker()
        }
        findViewById<MaterialButton>(R.id.resetFileRoomFolderButton).setOnClickListener {
            settingsStore.save(settingsStore.settings().copy(fileRoomTreeUri = null))
            applySettingsToSettingsScreen()
            Toast.makeText(this, "Lokasi File Room kembali ke Download/Nadi.", Toast.LENGTH_SHORT).show()
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
        findViewById<MaterialButton>(R.id.copyJoinInstructionsButton).setOnClickListener {
            copyJoinInstructions()
        }
        openFileRoomButton.setOnClickListener {
            openFileRoomLocation()
        }
        findViewById<MaterialButton>(R.id.regenerateLinkButton).setOnClickListener {
            regenerateJoinLink()
        }
        findViewById<MaterialButton>(R.id.copyDiagnosticsButton).setOnClickListener {
            copyDiagnostics()
        }
        findViewById<MaterialButton>(R.id.stopRoomButton).setOnClickListener {
            stopActiveRoom()
        }
        findViewById<MaterialButton>(R.id.addSharedFileButton).setOnClickListener {
            openFilePicker()
        }
        findViewById<View>(R.id.attachHostChatFileButton).setOnClickListener {
            openHostChatAttachmentPicker()
        }
        findViewById<View>(R.id.sendHostMessageButton).setOnClickListener {
            sendHostMessage()
        }
        hostChatInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                chatMessagesScrollView.post {
                    chatMessagesScrollView.fullScroll(View.FOCUS_DOWN)
                }
            } else {
                setChatKeyboardCompactMode(false)
            }
        }
        hostChatInput.setOnClickListener {
            chatMessagesScrollView.post {
                chatMessagesScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
        activeRoomNavigation.setOnItemSelectedListener { item ->
            activeRoomDestinationId = item.itemId
            showActiveRoomSection(item.itemId)
            true
        }
        networkModeGroup.setOnCheckedChangeListener { _, checkedId ->
            networkModeHelpText.text = if (checkedId == R.id.hotspotModeRadio) {
                getString(R.string.hotspot_permission_reason)
            } else {
                getString(R.string.same_wifi_note)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupJoinWebView() {
        joinWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            allowFileAccess = false
            allowContentAccess = true
        }
        joinWebView.webViewClient = WebViewClient()
        joinWebView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                webFileChooserCallback?.onReceiveValue(null)
                webFileChooserCallback = filePathCallback
                return runCatching {
                    webFileChooserLauncher.launch(fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    })
                }.isSuccess
            }
        }
    }

    private fun applySettingsToSetup() {
        val settings = settingsStore.settings()
        hostNameInput.setText(settings.defaultHostName)
        val checkedId = when (settings.defaultNetworkMode) {
            NetworkMode.HOTSPOT -> R.id.hotspotModeRadio
            NetworkMode.SAME_WIFI,
            NetworkMode.SAME_WIFI_FALLBACK -> R.id.sameWifiModeRadio
        }
        networkModeGroup.check(checkedId)
        networkModeHelpText.text = if (checkedId == R.id.hotspotModeRadio) {
            getString(R.string.hotspot_permission_reason)
        } else {
            getString(R.string.same_wifi_note)
        }
    }

    private fun applySettingsToSettingsScreen() {
        val settings = settingsStore.settings()
        defaultHostNameInput.setText(settings.defaultHostName)
        defaultNetworkModeGroup.check(
            if (settings.defaultNetworkMode == NetworkMode.HOTSPOT) {
                R.id.defaultHotspotModeRadio
            } else {
                R.id.defaultSameWifiModeRadio
            }
        )
        fileRoomStorageText.text = settings.fileRoomTreeUri?.let {
            getString(R.string.file_room_storage_custom, it)
        } ?: getString(R.string.file_room_storage_default)
    }

    private fun saveSettings() {
        val mode = if (defaultNetworkModeGroup.checkedRadioButtonId == R.id.defaultHotspotModeRadio) {
            NetworkMode.HOTSPOT
        } else {
            NetworkMode.SAME_WIFI
        }
        settingsStore.save(
            NadiSettings(
                defaultHostName = defaultHostNameInput.text?.toString().orEmpty(),
                defaultNetworkMode = mode,
                fileRoomTreeUri = settingsStore.settings().fileRoomTreeUri
            )
        )
        applySettingsToSetup()
        Toast.makeText(this, "Pengaturan disimpan.", Toast.LENGTH_SHORT).show()
        showHome()
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
        requestNotificationPermissionIfNeeded()
        RoomLifecycleService.start(this)
        activeRoomDestinationId = R.id.active_room_tab_room
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
        renderWifiQr(activeRoom)
        refreshHostDashboard()
        showActiveRoom()
    }

    private fun renderWifiQr(activeRoom: ActiveRoom) {
        val ssid = activeRoom.hotspotSsid.orEmpty()
        if (activeRoom.mode != NetworkMode.HOTSPOT || ssid.isBlank()) {
            wifiQrTitleText.gone()
            wifiQrImage.gone()
            wifiQrImage.setImageDrawable(null)
            return
        }
        val password = activeRoom.hotspotPassword.orEmpty()
        val qrSize = (190 * resources.displayMetrics.density).toInt()
        wifiQrImage.setImageBitmap(QrCodeGenerator.generate(buildWifiQrPayload(ssid, password), qrSize))
        wifiQrTitleText.visible()
        wifiQrImage.visible()
    }

    private fun buildWifiQrPayload(ssid: String, password: String): String {
        return "WIFI:T:WPA;S:${ssid.escapeWifiQr()};P:${password.escapeWifiQr()};;"
    }

    private fun String.escapeWifiQr(): String {
        return replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")
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

    private fun openHostChatAttachmentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "image/jpeg",
                    "image/png",
                    "image/gif",
                    "image/webp",
                    "application/pdf",
                    "text/plain",
                    "application/zip",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        hostChatAttachmentPicker.launch(intent)
    }

    private fun sendHostChatAttachment(uri: Uri) {
        val session = controller.roomManager.currentSession()
        if (session == null) {
            Toast.makeText(this, "Room belum aktif.", Toast.LENGTH_SHORT).show()
            return
        }
        val metadata = querySelectedFile(uri)
        if (!metadata.fileName.isAllowedChatAttachmentName()) {
            Toast.makeText(this, "Lampiran chat hanya untuk gambar, dokumen, teks, atau zip kecil.", Toast.LENGTH_LONG).show()
            return
        }
        if (metadata.sizeBytes > MAX_CHAT_ATTACHMENT_BYTES) {
            Toast.makeText(this, "Lampiran chat maksimal 10 MB.", Toast.LENGTH_LONG).show()
            return
        }
        val hostName = session.hostName.ifBlank { getString(R.string.host_name_default) }
        val transfer = contentResolver.openInputStream(uri)?.use { input ->
            controller.fileStore.saveRoomFile(
                fileName = metadata.fileName,
                mimeType = metadata.mimeType,
                inputStream = input,
                roomId = session.sessionId,
                folderName = CHAT_DOWNLOADS_FOLDER,
                direction = TransferDirection.CHAT_ATTACHMENT,
                senderName = hostName
            )
        }
        if (transfer == null) {
            Toast.makeText(this, "Lampiran belum bisa dibaca.", Toast.LENGTH_SHORT).show()
            return
        }
        controller.roomManager.addTransfer(transfer)
        controller.roomManager.addMessage(
            senderId = currentHostId(),
            senderName = hostName,
            text = hostChatInput.text?.toString().orEmpty(),
            attachment = transfer
        )
        hostChatInput.text?.clear()
        controller.persistRecentTransfers()
        forceChatScrollToBottom = true
        refreshHostDashboard()
        Toast.makeText(this, "Lampiran chat terkirim.", Toast.LENGTH_SHORT).show()
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

    private fun persistTreePermissionIfPossible(uri: Uri, data: Intent?) {
        val flags = data?.flags ?: return
        val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (takeFlags != 0) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
        }
    }

    private fun querySelectedFile(uri: Uri): SelectedFile {
        var displayName = "lampiran-nadi"
        var sizeBytes = -1L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex)?.ifBlank { displayName } ?: displayName
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }
        return SelectedFile(
            fileName = displayName,
            mimeType = contentResolver.getType(uri) ?: displayName.inferredMimeType(),
            sizeBytes = sizeBytes
        )
    }

    private fun String.isAllowedChatAttachmentName(): Boolean {
        val extension = substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in ALLOWED_CHAT_ATTACHMENT_EXTENSIONS
    }

    private fun String.inferredMimeType(): String {
        return when (substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun openFileRoomFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        fileRoomFolderPicker.launch(intent)
    }

    private fun sendHostMessage() {
        val text = hostChatInput.text?.toString().orEmpty()
        val hostName = controller.roomManager.currentSession()?.hostName ?: getString(R.string.host_name_default)
        val message = controller.roomManager.addMessage(
            senderId = currentHostId(),
            senderName = hostName,
            text = text
        )
        if (message == null) {
            Toast.makeText(this, "Pesan masih kosong.", Toast.LENGTH_SHORT).show()
            return
        }
        hostChatInput.text?.clear()
        forceChatScrollToBottom = true
        refreshHostDashboard()
    }

    private fun setChatKeyboardCompactMode(enabled: Boolean) {
        if (chatKeyboardCompactMode == enabled) return
        chatKeyboardCompactMode = enabled
        activeRoomNavigation.visibleIf(!enabled)
    }

    private fun refreshHostDashboard() {
        val snapshot = controller.roomManager.snapshot()
        val shared = controller.roomManager.sharedFiles()
        val received = controller.roomManager.receivedFiles()
        val messages = snapshot.messages
        if (controller.lifecycleState == RoomLifecycleState.ACTIVE) {
            controller.persistRecentTransfers()
            activeStatusText.text = buildStatusLine(controller.activeNetworkMode, snapshot.clients.size)
            diagnosticsText.text = controller.diagnostics().toDisplayText()
        }
        fileRoomSummaryText.text = getString(
            R.string.file_room_summary,
            shared.size,
            received.size
        )
        fileRoomLocationText.text = controller.currentRoomFolderPath()
            ?.let { getString(R.string.file_room_location, it) }
            ?: getString(R.string.file_room_location_pending)
        participantSummaryText.text = getString(R.string.participants_summary, snapshot.clients.size)
        clientListText.text = if (snapshot.clients.isEmpty()) {
            getString(R.string.participants_empty)
        } else {
            snapshot.clients.joinToString(separator = "\n\n") { it.participantLine() }
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
        renderChatMessages(messages)
        val recent = controller.roomManager.recentTransfers()
        val history = controller.recentHistory()
        activeRoomHistoryText.text = if (recent.isNotEmpty()) {
            recent.joinToString(separator = "\n\n") { it.displayLine() }
        } else if (history.isNotEmpty()) {
            history.take(8).joinToString(separator = "\n\n") { it.displayLine() }
        } else {
            getString(R.string.history_empty)
        }
        recentEmptyText.text = if (recent.isNotEmpty()) {
            recent.joinToString(separator = "\n") { it.displayLine() }
        } else if (history.isNotEmpty()) {
            history.take(5).joinToString(separator = "\n") { it.displayLine() }
        } else {
            getString(R.string.recent_empty)
        }
    }

    private fun ConnectedClient.displayLine(): String {
        val identity = if (nim.isNotBlank() || name.isNotBlank()) {
            "${nim.ifBlank { "-" }} - ${name.ifBlank { displayName }}"
        } else {
            displayName
        }
        return "$identity\n${ipAddress.ifBlank { "-" }} - ${userAgent.shortUserAgent()}"
    }

    private fun ConnectedClient.participantLine(): String {
        val identityLine = if (nim.isNotBlank() || name.isNotBlank()) {
            "${nim.ifBlank { "-" }} - ${name.ifBlank { displayName }}"
        } else {
            displayName
        }
        val statusLine = getString(R.string.participant_status_active)
        val deviceLine = "${ipAddress.ifBlank { "-" }} - ${userAgent.shortUserAgent()}"
        return "$identityLine\n$statusLine\n$deviceLine"
    }

    private fun renderChatMessages(messages: List<ChatMessage>) {
        val shouldScrollToBottom = forceChatScrollToBottom || isChatMessagesWindowNearBottom()
        forceChatScrollToBottom = false
        val roomId = controller.roomManager.currentSession()?.sessionId
        val currentMessageIds = messages.mapTo(mutableSetOf()) { it.messageId }
        val shouldRebuild = roomId != lastRenderedChatRoomId || !currentMessageIds.containsAll(renderedChatMessageIds)
        if (shouldRebuild) {
            chatMessagesContainer.removeAllViews()
            renderedChatMessageIds.clear()
            lastRenderedChatRoomId = roomId
        }
        if (messages.isEmpty()) {
            if (chatMessagesContainer.childCount == 0) {
                chatMessagesContainer.addView(
                    TextView(this).apply {
                        text = getString(R.string.chat_empty)
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_soft_ink))
                        textSize = 14f
                    }
                )
            }
            chatMessagesScrollView.post { chatMessagesScrollView.scrollTo(0, 0) }
            return
        }

        val newMessages = messages.filterNot { it.messageId in renderedChatMessageIds }
        if (newMessages.isEmpty()) return
        if (renderedChatMessageIds.isEmpty() && chatMessagesContainer.childCount > 0) {
            chatMessagesContainer.removeAllViews()
        }
        val hostId = currentHostId()
        val hostName = controller.roomManager.currentSession()?.hostName.orEmpty()
        newMessages.forEach { message ->
            val isHost = message.senderId == hostId || message.senderName == hostName
            val attachment = message.attachmentTransferId?.let { controller.roomManager.transferById(it) }
            chatMessagesContainer.addView(messageBubble(message, attachment, isHost))
            renderedChatMessageIds.add(message.messageId)
        }
        if (shouldScrollToBottom) {
            chatMessagesScrollView.post { chatMessagesScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun isChatMessagesWindowNearBottom(): Boolean {
        if (chatMessagesScrollView.childCount == 0) return true
        val content = chatMessagesScrollView.getChildAt(0)
        val distanceToBottom = content.bottom - (chatMessagesScrollView.scrollY + chatMessagesScrollView.height)
        return distanceToBottom <= 48.dp()
    }

    private fun messageBubble(message: ChatMessage, attachment: TransferItem?, isHost: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (isHost) Gravity.END else Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
            }
        }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            background = chatBubbleBackground(isHost)
            elevation = 2.dp().toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isHost) Gravity.END else Gravity.START
            }
        }

        // 1. Nama pengirim (hanya untuk guest)
        if (!isHost) {
            bubble.addView(TextView(this).apply {
                text = message.senderName
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_green))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                maxWidth = chatBubbleMaxWidth()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(12.dp(), 8.dp(), 12.dp(), 2.dp())
                }
            })
        }

        // 2. Lampiran Gambar (jika ada)
        val isImage = attachment?.isPreviewableImage() == true
        if (isImage) {
            val imageUri = attachment!!.previewUri()
            if (imageUri != null) {
                val card = com.google.android.material.card.MaterialCardView(this).apply {
                    radius = 12.dp().toFloat()
                    elevation = 0f
                    strokeWidth = 1.dp()
                    strokeColor = android.graphics.Color.parseColor(if (isHost) "#C0E8AA" else "#E5E5E5")
                    setCardBackgroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT))
                    clipToOutline = true

                    layoutParams = LinearLayout.LayoutParams(
                        chatBubbleMaxWidth(),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
                    }
                }

                val imageView = ImageView(this).apply {
                    adjustViewBounds = true
                    maxHeight = 240.dp()
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = attachment.fileName
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    runCatching { setImageURI(imageUri) }
                }
                card.addView(imageView)
                bubble.addView(card)
            }
        }

        // 3. Teks Pesan
        if (!message.text.isNullOrBlank()) {
            bubble.addView(TextView(this).apply {
                text = message.text
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_graphite))
                textSize = 15f
                maxWidth = chatBubbleMaxWidth()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    val top = if (isImage) 4.dp() else if (isHost) 8.dp() else 2.dp()
                    setMargins(12.dp(), top, 12.dp(), 2.dp())
                }
            })
        }

        // 4. Kartu Lampiran Berkas Non-Gambar
        val hasFile = !message.attachmentFileName.isNullOrBlank()
        if (hasFile && !isImage) {
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = 8.dp().toFloat()
                elevation = 0f
                strokeWidth = 0
                setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(if (isHost) "#CFE9BA" else "#F0F2F5")
                ))

                layoutParams = LinearLayout.LayoutParams(
                    chatBubbleMaxWidth(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(6.dp(), 6.dp(), 6.dp(), 4.dp())
                }
            }

            val cardContent = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val fileIcon = ImageView(this).apply {
                setImageResource(R.drawable.ic_document)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(if (isHost) "#075E54" else "#65676B")
                )
                layoutParams = LinearLayout.LayoutParams(32.dp(), 32.dp()).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    rightMargin = 8.dp()
                }
            }

            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val fileNameText = TextView(this).apply {
                text = message.attachmentFileName
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(android.graphics.Color.BLACK)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val fileMetaText = TextView(this).apply {
                val sizeStr = attachment?.let { FileSizeFormatter.format(it.sizeBytes) }.orEmpty()
                val extStr = message.attachmentFileName.orEmpty()
                    .substringAfterLast('.', missingDelimiterValue = "")
                    .uppercase()
                text = listOf(extStr, sizeStr).filter { it.isNotBlank() }.joinToString(" - ")
                textSize = 11f
                setTextColor(android.graphics.Color.parseColor("#65676B"))
            }

            textLayout.addView(fileNameText)
            textLayout.addView(fileMetaText)

            cardContent.addView(fileIcon)
            cardContent.addView(textLayout)
            card.addView(cardContent)
            bubble.addView(card)
        }

        // 5. Waktu / Timestamp
        bubble.addView(TextView(this).apply {
            text = chatTimeFormat.format(Date(message.createdAt))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_soft_ink))
            textSize = 10f
            gravity = Gravity.END
            maxWidth = chatBubbleMaxWidth()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                val top = if (hasFile && !isImage) 2.dp() else 4.dp()
                setMargins(12.dp(), top, 12.dp(), 6.dp())
            }
        })

        row.addView(bubble)
        return row
    }

    private fun TransferItem.isPreviewableImage(): Boolean {
        return mimeType.orEmpty().lowercase().startsWith("image/") || fileName.isImageFileName()
    }

    private fun TransferItem.previewUri(): Uri? {
        val value = localUri?.takeIf { it.isNotBlank() } ?: return null
        return if (value.startsWith("content://") || value.startsWith("file://")) {
            Uri.parse(value)
        } else {
            Uri.fromFile(File(value))
        }
    }

    private fun String.isImageFileName(): Boolean {
        return substringAfterLast('.', missingDelimiterValue = "").lowercase() in IMAGE_ATTACHMENT_EXTENSIONS
    }

    private fun chatBubbleBackground(isHost: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            val radius = 16.dp().toFloat()
            val smallRadius = 4.dp().toFloat()
            if (isHost) {
                cornerRadii = floatArrayOf(radius, radius, smallRadius, smallRadius, radius, radius, radius, radius)
                setColor(android.graphics.Color.parseColor("#DCF8C6"))
            } else {
                cornerRadii = floatArrayOf(smallRadius, smallRadius, radius, radius, radius, radius, radius, radius)
                setColor(android.graphics.Color.WHITE)
            }
        }
    }

    private fun chatBubbleMaxWidth(): Int {
        return (resources.displayMetrics.widthPixels * 0.72f).toInt()
    }

    private fun currentHostId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "host"
    }

    private fun String.shortUserAgent(): String {
        return trim()
            .ifBlank { "Browser" }
            .replace(Regex("""\s+"""), " ")
            .take(80)
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
            TransferDirection.CHAT_ATTACHMENT -> "Lampiran chat"
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (!hasPermission(permission)) {
            notificationPermissionLauncher.launch(permission)
        }
    }

    private fun copyJoinUrl() {
        val url = joinUrlText.text?.toString().orEmpty()
        if (url.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nadi room URL", url))
        Toast.makeText(this, "URL ruang disalin.", Toast.LENGTH_SHORT).show()
    }

    private fun pasteRoomUrl() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            .orEmpty()
            .trim()
        if (text.isBlank()) {
            Toast.makeText(this, "Clipboard belum berisi URL room.", Toast.LENGTH_SHORT).show()
            return
        }
        roomUrlInput.setText(text)
        roomUrlInput.setSelection(text.length)
    }

    private fun openJoinedRoom() {
        val url = roomUrlInput.text?.toString().orEmpty().trim()
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val valid = uri != null &&
            (uri.scheme == "http" || uri.scheme == "https") &&
            uri.host?.isNotBlank() == true
        if (!valid) {
            Toast.makeText(this, "Masukkan URL room Nadi yang valid.", Toast.LENGTH_SHORT).show()
            return
        }
        joinedRoomUrlText.text = url
        showJoinedRoom()
        joinWebView.loadUrl(url)
    }

    private fun closeJoinedRoom() {
        joinWebView.stopLoading()
        joinWebView.loadUrl("about:blank")
        showHome()
    }

    private fun copyJoinInstructions() {
        val instructions = buildJoinInstructions()
        if (instructions.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Instruksi join Nadi", instructions))
        Toast.makeText(this, "Instruksi join disalin.", Toast.LENGTH_SHORT).show()
    }

    private fun buildJoinInstructions(): String {
        val session = controller.roomManager.currentSession() ?: return ""
        val url = session.localUrl.orEmpty()
        return buildString {
            append("Gabung ke room Nadi: ").append(session.roomName).append("\n")
            when (controller.activeNetworkMode) {
                NetworkMode.HOTSPOT -> {
                    append("1. Sambungkan perangkat ke hotspot Nadi.\n")
                    controller.hotspotSsid?.takeIf { it.isNotBlank() }?.let {
                        append("Wi-Fi: ").append(it).append("\n")
                    }
                    controller.hotspotPassword?.takeIf { it.isNotBlank() }?.let {
                        append("Password: ").append(it).append("\n")
                    }
                    append("2. Buka URL atau scan QR room.\n")
                }
                NetworkMode.SAME_WIFI,
                NetworkMode.SAME_WIFI_FALLBACK -> {
                    append("1. Pastikan perangkat berada di Wi-Fi yang sama dengan host.\n")
                    append("2. Buka URL atau scan QR room.\n")
                }
            }
            append("URL: ").append(url).append("\n")
            append("Masukkan NIM dan Nama saat halaman Nadi terbuka.")
        }
    }

    private fun openFileRoomLocation() {
        val path = controller.currentRoomFolderPath() ?: return
        if (!path.startsWith("/")) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Lokasi File Room Nadi", path))
            Toast.makeText(this, "Lokasi file room disalin: $path", Toast.LENGTH_LONG).show()
            return
        }
        val folder = File(path)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val opened = runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(folder), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }.isSuccess
        if (!opened) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Lokasi File Room Nadi", path))
            Toast.makeText(this, "Lokasi file room disalin: $path", Toast.LENGTH_LONG).show()
        }
    }

    private fun regenerateJoinLink() {
        val activeRoom = controller.regenerateAccessLink()
        if (activeRoom == null) {
            Toast.makeText(this, "Link belum bisa diperbarui.", Toast.LENGTH_SHORT).show()
            return
        }
        renderActiveRoom(activeRoom)
        Toast.makeText(this, "Link baru dibuat. Link lama sudah ditutup.", Toast.LENGTH_LONG).show()
    }

    private fun copyDiagnostics() {
        val diagnostics = controller.diagnostics().toDisplayText()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nadi diagnostics", diagnostics))
        Toast.makeText(this, "Diagnostics disalin.", Toast.LENGTH_SHORT).show()
    }

    private fun showHome() {
        mainScrollView.visible()
        homePanel.visible()
        joinPanel.gone()
        joinWebPanel.gone()
        historyPanel.gone()
        settingsPanel.gone()
        setupPanel.gone()
        activeRoomPanel.gone()
        refreshHostDashboard()
    }

    private fun showJoin() {
        mainScrollView.visible()
        homePanel.gone()
        joinPanel.visible()
        joinWebPanel.gone()
        historyPanel.gone()
        settingsPanel.gone()
        setupPanel.gone()
        activeRoomPanel.gone()
    }

    private fun showJoinedRoom() {
        mainScrollView.visible()
        homePanel.gone()
        joinPanel.gone()
        joinWebPanel.visible()
        historyPanel.gone()
        settingsPanel.gone()
        setupPanel.gone()
        activeRoomPanel.gone()
    }

    private fun showHistory() {
        mainScrollView.visible()
        homePanel.gone()
        joinPanel.gone()
        joinWebPanel.gone()
        historyPanel.visible()
        settingsPanel.gone()
        setupPanel.gone()
        activeRoomPanel.gone()
        refreshHistoryScreen()
    }

    private fun showSettings() {
        mainScrollView.visible()
        homePanel.gone()
        joinPanel.gone()
        joinWebPanel.gone()
        historyPanel.gone()
        settingsPanel.visible()
        setupPanel.gone()
        activeRoomPanel.gone()
        applySettingsToSettingsScreen()
    }

    private fun showSetup() {
        mainScrollView.visible()
        homePanel.gone()
        joinPanel.gone()
        joinWebPanel.gone()
        historyPanel.gone()
        settingsPanel.gone()
        setupPanel.visible()
        activeRoomPanel.gone()
        applySettingsToSetup()
    }

    private fun showActiveRoom() {
        mainScrollView.gone()
        activeRoomPanel.visible()
        activeRoomNavigation.visible()
        activeRoomNavigation.selectedItemId = activeRoomDestinationId
        showActiveRoomSection(activeRoomDestinationId)
    }

    private fun showActiveRoomSection(destinationId: Int) {
        if (destinationId != R.id.active_room_tab_chat) {
            setChatKeyboardCompactMode(false)
        }
        val showRoom = destinationId == R.id.active_room_tab_room
        val showFiles = destinationId == R.id.active_room_tab_files
        val showChat = destinationId == R.id.active_room_tab_chat
        val showParticipants = destinationId == R.id.active_room_tab_participants
        val showHistory = destinationId == R.id.active_room_tab_history

        activeRoomJoinScroll.visibleIf(showRoom)
        activeRoomFileScroll.visibleIf(showFiles)
        activeRoomChatSection.visibleIf(showChat)
        activeRoomParticipantsScroll.visibleIf(showParticipants)
        activeRoomHistoryScroll.visibleIf(showHistory)

        activeRoomJoinSection.visible()
        activeRoomPrivacySection.visible()
        activeRoomDiagnosticsSection.visible()
        activeRoomFileOverviewSection.visible()
        openFileRoomButton.visible()
        activeRoomSharedFilesSection.visible()
        activeRoomReceivedFilesSection.visible()
        activeRoomParticipantsSection.visible()
        activeRoomHistorySection.visible()

        if (showChat) {
            chatMessagesScrollView.post {
                chatMessagesScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun View.visible() {
        visibility = View.VISIBLE
    }

    private fun View.gone() {
        visibility = View.GONE
    }

    private fun View.visibleIf(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}

private data class SelectedFile(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

private const val HOTSPOT_ADDRESS_SETTLE_DELAY_MS = 1500L
private const val CHAT_DOWNLOADS_FOLDER = "chat-downloads"
private const val MAX_CHAT_ATTACHMENT_BYTES = 10L * 1024L * 1024L
private val IMAGE_ATTACHMENT_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
private val ALLOWED_CHAT_ATTACHMENT_EXTENSIONS = setOf(
    "jpg",
    "jpeg",
    "png",
    "gif",
    "webp",
    "pdf",
    "txt",
    "doc",
    "docx",
    "ppt",
    "pptx",
    "xls",
    "xlsx",
    "zip"
)
