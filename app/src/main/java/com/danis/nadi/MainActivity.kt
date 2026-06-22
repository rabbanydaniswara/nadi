package com.danis.nadi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.history.TransferHistoryItem
import com.danis.nadi.model.ConnectedClient
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.danis.nadi.network.hotspot.HotspotState
import com.danis.nadi.network.server.ServerFileRules
import com.danis.nadi.room.ActiveRoom
import com.danis.nadi.room.NetworkMode
import com.danis.nadi.room.RoomController
import com.danis.nadi.room.RoomLifecycleState
import com.danis.nadi.room.RoomLifecycleService
import com.danis.nadi.room.RoomRuntime
import com.danis.nadi.room.RoomStartResult
import com.danis.nadi.settings.NadiSettings
import com.danis.nadi.settings.NadiSettingsStore
import com.danis.nadi.ui.HostChatRenderer
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
    private val historyTimeFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
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
    private lateinit var roomPinInput: EditText
    private lateinit var roomUrlInput: EditText
    private lateinit var defaultHostNameInput: EditText
    private lateinit var fileRoomStorageText: TextView
    private lateinit var hostChatInput: EditText
    private lateinit var openFileRoomButton: MaterialButton
    private lateinit var activeStatusText: TextView
    private lateinit var activeRoomNameText: TextView
    private lateinit var activeRoomCopyText: TextView
    private lateinit var joinUrlText: TextView
    private lateinit var activeRoomPinText: TextView
    private lateinit var fileRoomSummaryText: TextView
    private lateinit var fileRoomLocationText: TextView
    private lateinit var sharedFilesList: LinearLayout
    private lateinit var receivedFilesList: LinearLayout
    private lateinit var chatMessagesScrollView: NestedScrollView
    private lateinit var chatMessagesContainer: LinearLayout
    private lateinit var participantSummaryText: TextView
    private lateinit var clientListContainer: LinearLayout
    private lateinit var diagnosticsText: TextView
    private lateinit var historyListText: TextView
    private lateinit var activeRoomHistoryList: LinearLayout
    private lateinit var recentEmptyText: TextView
    private lateinit var networkModeHelpText: TextView
    private lateinit var wifiQrTitleText: TextView
    private lateinit var joinedRoomUrlText: TextView
    private lateinit var qrImage: ImageView
    private lateinit var wifiQrImage: ImageView
    private lateinit var joinWebView: WebView
    private var webFileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var chatKeyboardCompactMode = false
    private lateinit var hostChatRenderer: HostChatRenderer

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
        setupHostChatRenderer()
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

    override fun onStart() {
        super.onStart()
        if (controller.currentActiveRoom() != null) {
            startDashboardPolling()
        }
    }

    override fun onStop() {
        stopDashboardPolling()
        super.onStop()
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
        roomPinInput = findViewById(R.id.roomPinInput)
        roomUrlInput = findViewById(R.id.roomUrlInput)
        defaultHostNameInput = findViewById(R.id.defaultHostNameInput)
        fileRoomStorageText = findViewById(R.id.fileRoomStorageText)
        hostChatInput = findViewById(R.id.hostChatInput)
        openFileRoomButton = findViewById(R.id.openFileRoomButton)
        activeStatusText = findViewById(R.id.activeStatusText)
        activeRoomNameText = findViewById(R.id.activeRoomNameText)
        activeRoomCopyText = findViewById(R.id.activeRoomCopyText)
        joinUrlText = findViewById(R.id.joinUrlText)
        activeRoomPinText = findViewById(R.id.activeRoomPinText)
        fileRoomSummaryText = findViewById(R.id.fileRoomSummaryText)
        fileRoomLocationText = findViewById(R.id.fileRoomLocationText)
        sharedFilesList = findViewById(R.id.sharedFilesList)
        receivedFilesList = findViewById(R.id.receivedFilesList)
        chatMessagesScrollView = findViewById(R.id.chatMessagesScrollView)
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer)
        participantSummaryText = findViewById(R.id.participantSummaryText)
        clientListContainer = findViewById(R.id.clientListContainer)
        diagnosticsText = findViewById(R.id.diagnosticsText)
        historyListText = findViewById(R.id.historyListText)
        activeRoomHistoryList = findViewById(R.id.activeRoomHistoryList)
        recentEmptyText = findViewById(R.id.recentEmptyText)
        networkModeHelpText = findViewById(R.id.networkModeHelpText)
        wifiQrTitleText = findViewById(R.id.wifiQrTitleText)
        joinedRoomUrlText = findViewById(R.id.joinedRoomUrlText)
        qrImage = findViewById(R.id.qrImage)
        wifiQrImage = findViewById(R.id.wifiQrImage)
        joinWebView = findViewById(R.id.joinWebView)
    }

    private fun setupHostChatRenderer() {
        hostChatRenderer = HostChatRenderer(
            context = this,
            scrollView = chatMessagesScrollView,
            container = chatMessagesContainer,
            hostIdProvider = ::currentHostId,
            hostNameProvider = { controller.roomManager.currentSession()?.hostName.orEmpty() },
            roomIdProvider = { controller.roomManager.currentSession()?.sessionId },
            attachmentProvider = { transferId -> controller.roomManager.transferById(transferId) },
            onPreviewImage = ::showImageAttachmentPreview,
            onOpenAttachment = ::openChatAttachment
        )
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
        findViewById<MaterialButton>(R.id.openChatAttachmentsFolderButton).setOnClickListener {
            openChatAttachmentsLocation()
        }
        findViewById<MaterialButton>(R.id.clearChatAttachmentsButton).setOnClickListener {
            clearChatAttachments()
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
        val roomPin = roomPinInput.text?.toString().orEmpty().trim()
        if (roomPin.isNotBlank() && !roomPin.isValidRoomPin()) {
            Toast.makeText(this, getString(R.string.room_pin_invalid), Toast.LENGTH_LONG).show()
            return
        }
        val startResult = controller.prepareRoom(
            roomName = roomNameInput.text?.toString().orEmpty(),
            hostName = hostNameInput.text?.toString().orEmpty(),
            mode = mode,
            pin = roomPin
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
                    if (state.ssid.isNullOrBlank()) {
                        Toast.makeText(this, getString(R.string.hotspot_ssid_unavailable_toast), Toast.LENGTH_LONG).show()
                    }
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
                if (activeRoom.hotspotSsid.isNullOrBlank()) {
                    append(getString(R.string.hotspot_ssid_unavailable_note))
                } else {
                    append(getString(R.string.hotspot_note))
                    append("\nWi-Fi: ").append(activeRoom.hotspotSsid)
                }
                if (!activeRoom.hotspotPassword.isNullOrBlank()) append("\nPassword: ").append(activeRoom.hotspotPassword)
            }
            NetworkMode.SAME_WIFI_FALLBACK -> getString(R.string.hotspot_fallback_note)
            NetworkMode.SAME_WIFI -> getString(R.string.same_wifi_note)
        }
        joinUrlText.text = joinUrl
        activeRoomPinText.text = getString(R.string.room_pin_active, session.pin.orEmpty())
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
        if (!ServerFileRules.isAllowedChatAttachmentName(metadata.fileName)) {
            Toast.makeText(this, "Lampiran chat hanya untuk gambar, dokumen, teks, atau zip kecil.", Toast.LENGTH_LONG).show()
            return
        }
        if (metadata.sizeBytes !in 0..ServerFileRules.MAX_CHAT_ATTACHMENT_BYTES) {
            Toast.makeText(this, "Lampiran chat maksimal 10 MB.", Toast.LENGTH_LONG).show()
            return
        }
        val chatStats = controller.roomManager.chatAttachmentStorageStats()
        if (chatStats.totalBytes + metadata.sizeBytes > ServerFileRules.MAX_CHAT_ATTACHMENT_STORAGE_BYTES) {
            Toast.makeText(this, "Storage lampiran chat room sudah penuh.", Toast.LENGTH_LONG).show()
            return
        }
        val hostName = session.hostName.ifBlank { getString(R.string.host_name_default) }
        val transfer = contentResolver.openInputStream(uri)?.use { input ->
            controller.fileStore.saveRoomFile(
                fileName = metadata.fileName,
                mimeType = metadata.mimeType,
                inputStream = input,
                roomId = session.sessionId,
                folderName = ServerFileRules.CHAT_DOWNLOADS_FOLDER,
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
        hostChatRenderer.forceScrollToBottom = true
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
                if (takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (takeFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
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
        hostChatRenderer.forceScrollToBottom = true
        refreshHostDashboard()
    }

    private fun setChatKeyboardCompactMode(enabled: Boolean) {
        if (chatKeyboardCompactMode == enabled) return
        chatKeyboardCompactMode = enabled
        activeRoomNavigation.visibleIf(!enabled)
    }

    private fun refreshHostDashboard() {
        if (controller.lifecycleState == RoomLifecycleState.ACTIVE) {
            controller.cleanupExpiredChatAttachments()
        }
        val snapshot = controller.roomManager.snapshot()
        val shared = controller.roomManager.sharedFiles()
        val received = controller.roomManager.receivedFiles()
        val chatStorage = controller.roomManager.chatAttachmentStorageStats()
        val messages = snapshot.messages
        if (controller.lifecycleState == RoomLifecycleState.ACTIVE) {
            controller.persistRecentTransfers()
            activeStatusText.text = buildStatusLine(controller.activeNetworkMode, snapshot.clients.size)
            diagnosticsText.text = controller.diagnostics().toDisplayText()
        }
        fileRoomSummaryText.text = buildString {
            append(getString(R.string.file_room_summary, shared.size, received.size))
            append("\n")
            append("Lampiran chat: ")
            append(chatStorage.availableCount)
            append(" aktif")
            if (chatStorage.expiredCount > 0) {
                append(", ")
                append(chatStorage.expiredCount)
                append(" kedaluwarsa")
            }
            append(" - ")
            append(FileSizeFormatter.format(chatStorage.totalBytes))
            append(" / ")
            append(FileSizeFormatter.format(ServerFileRules.MAX_CHAT_ATTACHMENT_STORAGE_BYTES))
        }
        fileRoomLocationText.text = controller.currentRoomFolderPath()
            ?.let { getString(R.string.file_room_location, it) }
            ?: getString(R.string.file_room_location_pending)
        participantSummaryText.text = getString(R.string.participants_summary, snapshot.clients.size)
        renderParticipantList(snapshot.clients)
        renderTransferList(sharedFilesList, shared, getString(R.string.shared_files_empty))
        renderTransferList(receivedFilesList, received, getString(R.string.received_files_empty))
        hostChatRenderer.render(messages)
        val recent = controller.roomManager.recentTransfers()
        val history = controller.recentHistory()
        renderActiveHistoryList(recent, history)
        recentEmptyText.text = if (recent.isNotEmpty()) {
            recent.joinToString(separator = "\n") { it.displayLine() }
        } else if (history.isNotEmpty()) {
            history.take(5).joinToString(separator = "\n") { it.displayLine() }
        } else {
            getString(R.string.recent_empty)
        }
    }

    private fun ConnectedClient.identityLine(): String {
        return if (nim.isNotBlank() || name.isNotBlank()) {
            "${nim.ifBlank { "-" }} - ${name.ifBlank { displayName }}"
        } else {
            displayName
        }
    }

    private fun ConnectedClient.deviceLine(): String {
        return "${ipAddress.ifBlank { "-" }} - ${userAgent.shortUserAgent()}"
    }

    private fun renderParticipantList(clients: List<ConnectedClient>) {
        clientListContainer.removeAllViews()
        if (clients.isEmpty()) {
            clientListContainer.addView(simpleStateCard(getString(R.string.participants_empty)))
            return
        }
        clients.forEachIndexed { index, client ->
            clientListContainer.addView(participantCard(client, index > 0))
        }
    }

    private fun participantCard(client: ConnectedClient, hasTopMargin: Boolean): View {
        val card = baseInfoCard(hasTopMargin)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_participants)
            imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.nadi_green)
            )
            layoutParams = LinearLayout.LayoutParams(34.dp(), 34.dp()).apply {
                marginEnd = 10.dp()
            }
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(TextView(this).apply {
            text = client.identityLine()
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_graphite))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        textColumn.addView(TextView(this).apply {
            text = client.deviceLine()
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_soft_ink))
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2.dp()
            }
        })
        textColumn.addView(TextView(this).apply {
            text = getString(R.string.participant_status_active)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_success))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2.dp()
            }
        })
        row.addView(icon)
        row.addView(textColumn)
        card.addView(row)
        return card
    }

    private fun renderActiveHistoryList(recent: List<TransferItem>, history: List<TransferHistoryItem>) {
        activeRoomHistoryList.removeAllViews()
        when {
            recent.isNotEmpty() -> recent.forEachIndexed { index, item ->
                activeRoomHistoryList.addView(historyCard(
                    fileName = item.fileName,
                    meta = listOf(
                        item.direction.label(),
                        FileSizeFormatter.format(item.sizeBytes),
                        item.status.label(item.progress)
                    ).joinToString(" - "),
                    time = historyTimeFormat.format(Date(item.createdAt)),
                    senderName = item.senderName,
                    hasTopMargin = index > 0
                ))
            }
            history.isNotEmpty() -> history.take(8).forEachIndexed { index, item ->
                activeRoomHistoryList.addView(historyCard(
                    fileName = item.fileName,
                    meta = listOf(
                        item.direction.label(),
                        FileSizeFormatter.format(item.sizeBytes),
                        item.status.label(item.progress)
                    ).joinToString(" - "),
                    time = historyTimeFormat.format(Date(item.createdAt)),
                    senderName = item.senderName,
                    hasTopMargin = index > 0
                ))
            }
            else -> activeRoomHistoryList.addView(simpleStateCard(getString(R.string.history_empty)))
        }
    }

    private fun historyCard(
        fileName: String,
        meta: String,
        time: String,
        senderName: String?,
        hasTopMargin: Boolean
    ): View {
        val card = baseInfoCard(hasTopMargin)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_history)
            imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.nadi_green)
            )
            layoutParams = LinearLayout.LayoutParams(34.dp(), 34.dp()).apply {
                marginEnd = 10.dp()
            }
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(TextView(this).apply {
            text = fileName
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_graphite))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        textColumn.addView(TextView(this).apply {
            text = meta
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_soft_ink))
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2.dp()
            }
        })
        textColumn.addView(TextView(this).apply {
            text = listOfNotNull(senderName?.takeIf { it.isNotBlank() }, time).joinToString(" - ")
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_green))
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2.dp()
            }
        })
        row.addView(icon)
        row.addView(textColumn)
        card.addView(row)
        return card
    }

    private fun simpleStateCard(text: String): View {
        val card = baseInfoCard(false)
        card.addView(TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_soft_ink))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(14.dp(), 18.dp(), 14.dp(), 18.dp())
        })
        return card
    }

    private fun baseInfoCard(hasTopMargin: Boolean): com.google.android.material.card.MaterialCardView {
        return com.google.android.material.card.MaterialCardView(this).apply {
            radius = 8.dp().toFloat()
            elevation = 0f
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.nadi_line)
            setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.nadi_mist)
            ))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (hasTopMargin) topMargin = 8.dp()
            }
        }
    }

    private fun renderTransferList(container: LinearLayout, items: List<TransferItem>, emptyText: String) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(transferEmptyState(emptyText))
            return
        }
        items.forEachIndexed { index, item ->
            container.addView(transferRow(item, index > 0))
        }
    }

    private fun transferEmptyState(text: String): View {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = 8.dp().toFloat()
            elevation = 0f
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.nadi_line)
            setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.nadi_mist)
            ))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_soft_ink))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(14.dp(), 18.dp(), 14.dp(), 18.dp())
        })
        return card
    }

    private fun transferRow(item: TransferItem, hasTopMargin: Boolean): View {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = 8.dp().toFloat()
            elevation = 0f
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.nadi_line)
            setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.nadi_mist)
            ))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (hasTopMargin) topMargin = 8.dp()
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_document)
            imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.nadi_green)
            )
            layoutParams = LinearLayout.LayoutParams(34.dp(), 34.dp()).apply {
                marginEnd = 10.dp()
            }
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(this).apply {
            text = item.fileName
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_graphite))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val meta = TextView(this).apply {
            text = listOf(
                item.direction.label(),
                FileSizeFormatter.format(item.sizeBytes),
                item.status.label(item.progress)
            ).joinToString(" - ")
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_soft_ink))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2.dp()
            }
        }
        val sender = item.senderName?.takeIf { it.isNotBlank() }?.let { senderName ->
            TextView(this).apply {
                text = senderName
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nadi_green))
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 2.dp()
                }
            }
        }
        textColumn.addView(title)
        textColumn.addView(meta)
        sender?.let(textColumn::addView)
        row.addView(icon)
        row.addView(textColumn)
        card.addView(row)
        return card
    }

    private fun showImageAttachmentPreview(attachment: TransferItem) {
        val imageUri = attachment.previewUri() ?: return
        Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            val frame = FrameLayout(this@MainActivity).apply {
                setBackgroundColor(Color.BLACK)
                setOnClickListener { dismiss() }
            }
            val imageView = ImageView(this@MainActivity).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = attachment.fileName
                setImageURI(imageUri)
                setOnClickListener { }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(12.dp(), 56.dp(), 12.dp(), 56.dp())
                    gravity = Gravity.CENTER
                }
            }
            val closeText = TextView(this@MainActivity).apply {
                text = "Tutup"
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(16.dp(), 10.dp(), 16.dp(), 10.dp())
                setOnClickListener { dismiss() }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    setMargins(0, 18.dp(), 14.dp(), 0)
                }
            }
            val titleText = TextView(this@MainActivity).apply {
                text = attachment.fileName
                setTextColor(Color.WHITE)
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(16.dp(), 10.dp(), 96.dp(), 10.dp())
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    setMargins(0, 18.dp(), 0, 0)
                }
            }
            frame.addView(imageView)
            frame.addView(titleText)
            frame.addView(closeText)
            setContentView(frame)
            show()
            window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            window?.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun openChatAttachment(attachment: TransferItem) {
        val uri = attachment.openableUri() ?: run {
            Toast.makeText(this, "File lampiran belum tersedia.", Toast.LENGTH_SHORT).show()
            return
        }
        val opened = runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, attachment.mimeType ?: attachment.fileName.inferredMimeType())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Buka lampiran"))
        }.isSuccess
        if (!opened) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini.", Toast.LENGTH_LONG).show()
        }
    }

    private fun TransferItem.previewUri(): Uri? {
        val value = localUri?.takeIf { it.isNotBlank() } ?: return null
        return if (value.startsWith("content://") || value.startsWith("file://")) {
            Uri.parse(value)
        } else {
            Uri.fromFile(File(value))
        }
    }

    private fun TransferItem.openableUri(): Uri? {
        val value = localUri?.takeIf { it.isNotBlank() } ?: return null
        return when {
            value.startsWith("content://") || value.startsWith("file://") -> Uri.parse(value)
            else -> {
                val file = File(value).takeIf { it.exists() } ?: return null
                FileProvider.getUriForFile(this@MainActivity, "${packageName}.fileprovider", file)
            }
        }
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
            TransferStatus.SUCCESS -> "Tersedia"
            TransferStatus.DOWNLOADED -> "Diunduh"
            TransferStatus.EXPIRED -> "Kedaluwarsa"
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
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
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
            session.pin?.takeIf { it.isNotBlank() }?.let {
                append("PIN room: ").append(it).append("\n")
            }
            append("Masukkan NIM dan Nama saat halaman Nadi terbuka.")
        }
    }

    private fun openFileRoomLocation() {
        val path = controller.currentRoomFolderPath() ?: return
        val uri = controller.currentRoomFolderUri()
        openFolderLocation(path, uri)
    }

    private fun openChatAttachmentsLocation() {
        val path = controller.currentRoomFolderPath(ServerFileRules.CHAT_DOWNLOADS_FOLDER) ?: return
        val uri = controller.currentRoomFolderUri(ServerFileRules.CHAT_DOWNLOADS_FOLDER)
        openFolderLocation(path, uri)
    }

    private fun clearChatAttachments() {
        val cleared = controller.clearChatAttachments()
        refreshHostDashboard()
        val message = if (cleared > 0) {
            getString(R.string.chat_attachments_cleared, cleared)
        } else {
            getString(R.string.chat_attachments_empty)
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun openFolderLocation(path: String, folderUri: Uri?) {
        if (!path.startsWith("/") && folderUri == null) {
            Toast.makeText(this, getString(R.string.folder_open_unsupported), Toast.LENGTH_LONG).show()
            return
        }
        val folder = path.takeIf { it.startsWith("/") }?.let(::File)
        if (folder != null && !folder.exists()) {
            folder.mkdirs()
        }
        val opened = folderOpenIntents(folder, folderUri ?: folder?.externalStorageDocumentUri()).any { intent ->
            runCatching {
                startActivity(intent)
            }.isSuccess
        }
        if (!opened) {
            Toast.makeText(this, getString(R.string.folder_open_unsupported), Toast.LENGTH_LONG).show()
        }
    }

    private fun folderOpenIntents(folder: File?, folderUri: Uri?): List<Intent> {
        val contentUri = folder?.let {
            runCatching {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", it)
            }.getOrNull()
        }
        val intents = mutableListOf<Intent>()
        intents.addAll(vendorFileManagerIntents(folder, contentUri ?: folderUri))
        intents.addAll(documentsUiFolderIntents(folder, folderUri))
        return intents
    }

    private fun vendorFileManagerIntents(folder: File?, uri: Uri?): List<Intent> {
        val path = folder?.absolutePath
        val targets = listOf(
            "com.mi.android.globalFileexplorer",
            "com.android.fileexplorer",
            "com.sec.android.app.myfiles",
            "com.google.android.apps.nbu.files",
            "com.oplus.filemanager",
            "com.coloros.filemanager",
            "com.vivo.filemanager",
            "com.huawei.hidisk"
        ).filter { it.isPackageInstalled() }

        return targets.flatMap { packageName ->
            buildList {
                if (packageName in setOf("com.mi.android.globalFileexplorer", "com.android.fileexplorer")) {
                    add(
                        Intent(MIUI_FILE_MANAGER_OPEN_ACTION).apply {
                            setPackage(packageName)
                            uri?.let { data = it }
                            path?.let { putExtra(MIUI_EXPLORER_PATH_EXTRA, it) }
                            addCategory(Intent.CATEGORY_DEFAULT)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                    )
                    add(
                        Intent(MIUI_FILE_MANAGER_HOME_ACTION).apply {
                            setPackage(packageName)
                            path?.let { putExtra(MIUI_EXPLORER_PATH_EXTRA, it) }
                            addCategory(Intent.CATEGORY_DEFAULT)
                        }
                    )
                }
                if (uri != null) {
                    add(
                        Intent(Intent.ACTION_VIEW).apply {
                            setPackage(packageName)
                            data = uri
                            path?.let { putExtra(OPENINTENTS_EXTRA_ABSOLUTE_PATH, it) }
                            addCategory(Intent.CATEGORY_DEFAULT)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                    )
                }
            }
        }
    }

    private fun documentsUiFolderIntents(folder: File?, folderUri: Uri?): List<Intent> {
        val intents = mutableListOf<Intent>()
        folderUri?.let { uri ->
            intents.add(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage(DOCUMENTS_UI_PACKAGE)
                    setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addCategory(Intent.CATEGORY_DEFAULT)
                    folder?.let { putExtra(OPENINTENTS_EXTRA_ABSOLUTE_PATH, it.absolutePath) }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            )
            intents.add(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage(DOCUMENTS_UI_PACKAGE)
                    data = uri
                    addCategory(Intent.CATEGORY_DEFAULT)
                    folder?.let { putExtra(OPENINTENTS_EXTRA_ABSOLUTE_PATH, it.absolutePath) }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            )
        }
        return intents
    }

    private fun String.isPackageInstalled(): Boolean {
        return runCatching {
            packageManager.getPackageInfo(this, 0)
        }.isSuccess
    }

    private fun File.externalStorageDocumentUri(): Uri? {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath.trimEnd(File.separatorChar)
        val folderPath = absolutePath.trimEnd(File.separatorChar)
        if (!folderPath.startsWith(rootPath)) return null
        val relativePath = folderPath
            .removePrefix(rootPath)
            .trimStart(File.separatorChar)
            .replace(File.separatorChar, '/')
        val documentId = if (relativePath.isBlank()) {
            "primary:"
        } else {
            "primary:$relativePath"
        }
        return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId)
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
        mainScrollView.gone()
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
        joinWebPanel.gone()
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

    private fun String.isValidRoomPin(): Boolean = matches(Regex("^\\d{4,8}$"))
}

private data class SelectedFile(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

private const val HOTSPOT_ADDRESS_SETTLE_DELAY_MS = 1500L
private const val OPENINTENTS_EXTRA_ABSOLUTE_PATH = "org.openintents.extra.ABSOLUTE_PATH"
private const val MIUI_FILE_MANAGER_OPEN_ACTION = "miui.intent.action.OPEN"
private const val MIUI_FILE_MANAGER_HOME_ACTION = "com.android.fileexplorer.export.VIEW_HOME"
private const val MIUI_EXPLORER_PATH_EXTRA = "explorer_path"
private const val DOCUMENTS_UI_PACKAGE = "com.google.android.documentsui"
