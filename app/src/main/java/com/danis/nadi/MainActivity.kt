package com.danis.nadi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import com.danis.nadi.network.client.RoomClient
import com.danis.nadi.model.ChatMessage
import java.io.FileInputStream
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray
import com.danis.nadi.ui.HostChatRenderer
import com.danis.nadi.util.QrCodeGenerator
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.danis.nadi.data.db.NadiDatabase
import com.danis.nadi.data.repository.ChatRepository
import com.danis.nadi.data.repository.FileRepository
import com.danis.nadi.ui.viewmodel.HostViewModel
import com.danis.nadi.ui.viewmodel.ClientViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    internal lateinit var controller: RoomController
    internal lateinit var settingsStore: NadiSettingsStore
    internal lateinit var database: NadiDatabase
    internal lateinit var chatRepository: ChatRepository
    internal lateinit var fileRepository: FileRepository
    internal lateinit var hostViewModel: HostViewModel
    internal lateinit var clientViewModel: ClientViewModel
    internal val dashboardHandler = Handler(Looper.getMainLooper())
    internal val historyTimeFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    internal var dashboardPolling = false
    internal var pendingHotspotStart = false
    internal var activeRoomDestinationId = R.id.active_room_tab_room

    internal lateinit var homePanel: LinearLayout
    internal lateinit var mainScrollView: NestedScrollView
    internal lateinit var activeRoomJoinScroll: NestedScrollView
    internal lateinit var activeRoomFileScroll: NestedScrollView
    internal lateinit var activeRoomParticipantsScroll: NestedScrollView
    internal lateinit var activeRoomHistoryScroll: NestedScrollView
    internal lateinit var joinPanel: LinearLayout
    internal lateinit var historyPanel: LinearLayout
    internal lateinit var settingsPanel: LinearLayout
    internal lateinit var setupPanel: LinearLayout
    internal lateinit var activeRoomPanel: LinearLayout
    internal lateinit var activeRoomHeaderSection: LinearLayout
    internal lateinit var activeRoomNavigation: BottomNavigationView
    internal lateinit var activeRoomJoinSection: LinearLayout
    internal lateinit var activeRoomPrivacySection: LinearLayout
    internal lateinit var activeRoomParticipantsSection: LinearLayout
    internal lateinit var activeRoomFileOverviewSection: LinearLayout
    internal lateinit var activeRoomSharedFilesSection: LinearLayout
    internal lateinit var activeRoomReceivedFilesSection: LinearLayout
    internal lateinit var activeRoomChatSection: LinearLayout
    internal lateinit var activeRoomDiagnosticsSection: LinearLayout
    internal lateinit var activeRoomHistorySection: LinearLayout
    internal lateinit var networkModeGroup: RadioGroup
    internal lateinit var defaultNetworkModeGroup: RadioGroup
    internal lateinit var roomNameInput: EditText
    internal lateinit var hostNameInput: EditText
    internal lateinit var roomPinInput: EditText
    internal lateinit var roomUrlInput: EditText
    internal lateinit var defaultHostNameInput: EditText
    internal lateinit var fileRoomStorageText: TextView
    internal lateinit var hostChatInput: EditText
    internal lateinit var openFileRoomButton: MaterialButton
    internal lateinit var activeStatusText: TextView
    internal lateinit var activeRoomNameText: TextView
    internal lateinit var activeRoomCopyText: TextView
    internal lateinit var joinGuideSummaryText: TextView
    internal lateinit var joinStepNetworkText: TextView
    internal lateinit var joinStepOpenText: TextView
    internal lateinit var joinStepIdentityText: TextView
    internal lateinit var joinNetworkDetailText: TextView
    internal lateinit var joinUrlText: TextView
    internal lateinit var activeRoomPinText: TextView
    internal lateinit var fileRoomSummaryText: TextView
    internal lateinit var fileRoomLocationText: TextView
    internal lateinit var sharedFilesList: LinearLayout
    internal lateinit var receivedFilesList: LinearLayout
    internal lateinit var chatMessagesScrollView: NestedScrollView
    internal lateinit var chatMessagesContainer: LinearLayout
    internal lateinit var participantSummaryText: TextView
    internal lateinit var clientListContainer: LinearLayout
    internal lateinit var diagnosticsText: TextView
    internal lateinit var historyListText: TextView
    internal lateinit var activeRoomHistoryList: LinearLayout
    internal lateinit var recentEmptyText: TextView
    internal lateinit var networkModeHelpText: TextView
    internal lateinit var wifiQrTitleText: TextView
    internal lateinit var qrImage: ImageView
    internal lateinit var wifiQrImage: ImageView
    private var chatKeyboardCompactMode = false
    internal lateinit var hostChatRenderer: HostChatRenderer

    // Native Client Room properties
    internal var roomClient: RoomClient? = null
    internal var clientRoomUrl: String? = null
    internal val clientChatMessages = ArrayList<ChatMessage>()
    internal val clientTransfersMap = HashMap<String, TransferItem>()
    internal lateinit var clientChatRenderer: HostChatRenderer
    internal var clientPendingAttachmentUri: Uri? = null
    internal val clientPollHandler = Handler(Looper.getMainLooper())
    internal var clientPollRunnable: Runnable? = null
    internal var activeClientRoomDestinationId = R.id.client_tab_files

    internal lateinit var joinIdentityPanel: LinearLayout
    internal lateinit var clientNimInput: EditText
    internal lateinit var clientNameInput: EditText
    internal lateinit var clientJoinButton: MaterialButton
    internal lateinit var clientJoinCancelButton: MaterialButton
    internal lateinit var activeClientRoomPanel: LinearLayout
    internal lateinit var clientTabFilesScroll: NestedScrollView
    internal lateinit var clientUploadFileButton: MaterialButton
    internal lateinit var clientUploadProgress: ProgressBar
    internal lateinit var clientUploadStatusText: TextView
    internal lateinit var clientSharedFilesList: LinearLayout
    internal lateinit var clientTabChatLayout: LinearLayout
    internal lateinit var clientChatScrollView: NestedScrollView
    internal lateinit var clientChatMessagesContainer: LinearLayout
    internal lateinit var clientChatInput: EditText
    internal lateinit var clientSendChatButton: MaterialButton
    internal lateinit var clientAttachButton: MaterialButton
    internal lateinit var clientAttachmentStatus: TextView
    internal lateinit var clientTabInfoScroll: NestedScrollView
    internal lateinit var clientInfoStatusText: TextView
    internal lateinit var clientInfoRoomNameText: TextView
    internal lateinit var clientInfoHostNameText: TextView
    internal lateinit var clientInfoSelfIdentityText: TextView
    internal lateinit var clientExitRoomButton: MaterialButton
    internal lateinit var activeClientRoomNavigation: BottomNavigationView

    internal val clientFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        handleClientFileUpload(uri, isAttachment = false)
    }

    internal val clientChatAttachmentPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        handleClientChatAttachmentSelected(uri)
    }

    internal val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        persistReadPermissionIfPossible(uri, result.data)
        controller.createSharedTransfer(uri)
        refreshHostDashboard()
        Toast.makeText(this, "File siap dibagikan.", Toast.LENGTH_SHORT).show()
    }

    internal val fileRoomFolderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        persistTreePermissionIfPossible(uri, result.data)
        settingsStore.save(settingsStore.settings().copy(fileRoomTreeUri = uri.toString()))
        applySettingsToSettingsScreen()
        Toast.makeText(this, "Folder File Room disimpan.", Toast.LENGTH_SHORT).show()
    }

    internal val hostChatAttachmentPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        persistReadPermissionIfPossible(uri, result.data)
        sendHostChatAttachment(uri)
    }

    internal val hotspotPermissionLauncher = registerForActivityResult(
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

    internal val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        Unit
    }

    internal val refreshRunnable = object : Runnable {
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
        
        database = NadiDatabase.getInstance(this)
        chatRepository = ChatRepository(database.chatMessageDao())
        fileRepository = FileRepository(database.sharedFileDao())

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(HostViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return HostViewModel(chatRepository, fileRepository) as T
                }
                if (modelClass.isAssignableFrom(ClientViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return ClientViewModel(chatRepository, fileRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
        hostViewModel = ViewModelProvider(this, factory)[HostViewModel::class.java]
        clientViewModel = ViewModelProvider(this, factory)[ClientViewModel::class.java]
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    joinIdentityPanel.visibility == View.VISIBLE -> {
                        showJoin()
                    }
                    activeClientRoomPanel.visibility == View.VISIBLE -> {
                        confirmExitClientRoom()
                    }
                    activeRoomPanel.visibility == View.VISIBLE -> {
                        stopActiveRoom()
                    }
                    joinPanel.visibility == View.VISIBLE || historyPanel.visibility == View.VISIBLE || settingsPanel.visibility == View.VISIBLE || setupPanel.visibility == View.VISIBLE -> {
                        showHome()
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        bindViews()
        setupHostChatRenderer()
        setupWindowInsets()
        bindActions()
        setupViewModelObservers()
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
        clientPollHandler.removeCallbacksAndMessages(null)
        super.onStop()
    }

    override fun onDestroy() {
        stopDashboardPolling()
        clientPollHandler.removeCallbacksAndMessages(null)
        roomClient?.close()
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
        joinGuideSummaryText = findViewById(R.id.joinGuideSummaryText)
        joinStepNetworkText = findViewById(R.id.joinStepNetworkText)
        joinStepOpenText = findViewById(R.id.joinStepOpenText)
        joinStepIdentityText = findViewById(R.id.joinStepIdentityText)
        joinNetworkDetailText = findViewById(R.id.joinNetworkDetailText)
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
        qrImage = findViewById(R.id.qrImage)
        wifiQrImage = findViewById(R.id.wifiQrImage)

        joinIdentityPanel = findViewById(R.id.joinIdentityPanel)
        clientNimInput = findViewById(R.id.clientNimInput)
        clientNameInput = findViewById(R.id.clientNameInput)
        clientJoinButton = findViewById(R.id.clientJoinButton)
        clientJoinCancelButton = findViewById(R.id.clientJoinCancelButton)
        activeClientRoomPanel = findViewById(R.id.activeClientRoomPanel)
        clientTabFilesScroll = findViewById(R.id.clientTabFilesScroll)
        clientUploadFileButton = findViewById(R.id.clientUploadFileButton)
        clientUploadProgress = findViewById(R.id.clientUploadProgress)
        clientUploadStatusText = findViewById(R.id.clientUploadStatusText)
        clientSharedFilesList = findViewById(R.id.clientSharedFilesList)
        clientTabChatLayout = findViewById(R.id.clientTabChatLayout)
        clientChatScrollView = findViewById(R.id.clientChatScrollView)
        clientChatMessagesContainer = findViewById(R.id.clientChatMessagesContainer)
        clientChatInput = findViewById(R.id.clientChatInput)
        clientSendChatButton = findViewById(R.id.clientSendChatButton)
        clientAttachButton = findViewById(R.id.clientAttachButton)
        clientAttachmentStatus = findViewById(R.id.clientAttachmentStatus)
        clientTabInfoScroll = findViewById(R.id.clientTabInfoScroll)
        clientInfoStatusText = findViewById(R.id.clientInfoStatusText)
        clientInfoRoomNameText = findViewById(R.id.clientInfoRoomNameText)
        clientInfoHostNameText = findViewById(R.id.clientInfoHostNameText)
        clientInfoSelfIdentityText = findViewById(R.id.clientInfoSelfIdentityText)
        clientExitRoomButton = findViewById(R.id.clientExitRoomButton)
        activeClientRoomNavigation = findViewById(R.id.activeClientRoomNavigation)
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

            // Keyboard-aware bottom nav for Client Room
            if (activeClientRoomPanel.visibility == View.VISIBLE) {
                activeClientRoomNavigation.visibility = if (imeVisible) View.GONE else View.VISIBLE
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

        // Native Client listeners
        clientJoinButton.setOnClickListener { submitClientIdentity() }
        clientJoinCancelButton.setOnClickListener { showJoin() }
        clientExitRoomButton.setOnClickListener { confirmExitClientRoom() }
        clientUploadFileButton.setOnClickListener { selectClientUploadFile() }
        clientSendChatButton.setOnClickListener { sendClientChatMessage() }
        clientAttachButton.setOnClickListener { selectClientChatAttachment() }
        activeClientRoomNavigation.setOnItemSelectedListener { item ->
            showActiveClientRoomSection(item.itemId)
            true
        }
        clientChatInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                clientChatScrollView.post {
                    clientChatScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
        clientChatInput.setOnClickListener {
            clientChatScrollView.post {
                clientChatScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
        clientChatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendClientChatMessage()
                true
            } else false
        }
    }

    internal fun setChatKeyboardCompactMode(enabled: Boolean) {
        if (chatKeyboardCompactMode == enabled) return
        chatKeyboardCompactMode = enabled
        activeRoomNavigation.visibleIf(!enabled)
    }

    internal fun currentHostId(): String {
        val roomId = controller.roomManager.currentSession()?.sessionId.orEmpty()
        return if (roomId.isBlank()) "host" else "host-$roomId"
    }
}
