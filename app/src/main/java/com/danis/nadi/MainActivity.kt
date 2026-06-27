package com.danis.nadi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.danis.nadi.data.db.NadiDatabase
import com.danis.nadi.data.repository.ChatRepository
import com.danis.nadi.data.repository.FileRepository
import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.history.TransferHistoryItem
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.danis.nadi.network.client.RoomClient
import com.danis.nadi.room.NetworkMode
import com.danis.nadi.room.RoomController
import com.danis.nadi.room.RoomLifecycleService
import com.danis.nadi.room.RoomRuntime
import com.danis.nadi.settings.NadiSettingsStore
import com.danis.nadi.settings.NadiSettings
import com.danis.nadi.ui.compose.MainScreen
import com.danis.nadi.ui.compose.Screen
import com.danis.nadi.ui.viewmodel.ClientViewModel
import com.danis.nadi.ui.viewmodel.HostViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
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

    // Pending startup settings state
    var pendingRoomName = ""
    var pendingHostName = ""
    var pendingRoomPin = ""
    var pendingNetworkMode = NetworkMode.SAME_WIFI

    val currentScreenState = mutableStateOf<Screen>(Screen.Home)

    // Native Client Room properties
    internal var roomClient: RoomClient? = null
    internal var clientRoomUrl: String? = null
    internal val clientTransfersMap = HashMap<String, TransferItem>()
    internal var clientPendingAttachmentUri: Uri? = null
    internal val clientPollHandler = Handler(Looper.getMainLooper())
    internal var clientPollRunnable: Runnable? = null

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
        Toast.makeText(this, "Folder File Room disimpan.", Toast.LENGTH_SHORT).show()
    }

    internal val hostChatAttachmentPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        persistReadPermissionIfPossible(uri, result.data)
        sendHostChatAttachment(uri, "")
    }

    internal val hotspotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = requiredHotspotPermissions().all { result[it] == true || hasPermission(it) }
        if (pendingHotspotStart && granted) {
            pendingHotspotStart = false
            startLocalRoomWithMode(pendingRoomName, pendingHostName, pendingRoomPin, NetworkMode.HOTSPOT)
        } else if (pendingHotspotStart) {
            pendingHotspotStart = false
            Toast.makeText(
                this,
                "Izin belum diberikan. Nadi memakai mode satu Wi-Fi.",
                Toast.LENGTH_LONG
            ).show()
            startLocalRoomWithMode(pendingRoomName, pendingHostName, pendingRoomPin, NetworkMode.SAME_WIFI)
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

        val activeRoom = controller.currentActiveRoom()
        if (activeRoom != null) {
            val session = activeRoom.session
            hostViewModel.loadRoomData(session.sessionId)
            currentScreenState.value = Screen.HostDashboard
            startDashboardPolling()
        } else {
            currentScreenState.value = Screen.Home
        }

        setContent {
            MainScreen(activity = this)
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

    internal fun currentHostId(): String {
        val roomId = controller.roomManager.currentSession()?.sessionId.orEmpty()
        return if (roomId.isBlank()) "host" else "host-$roomId"
    }

    fun showHome() {
        currentScreenState.value = Screen.Home
    }

    fun showSetup() {
        currentScreenState.value = Screen.Setup
    }

    fun showJoin() {
        currentScreenState.value = Screen.Join
    }
}

fun MainActivity.refreshHostDashboard() {
    val activeRoom = controller.currentActiveRoom() ?: return
    val shared = controller.roomManager.sharedFiles()
    val received = controller.roomManager.receivedFiles()
    val messages = controller.roomManager.snapshot().messages

    val allFiles = shared + received
    if (hostViewModel.chatMessages.value.size != messages.size || hostViewModel.chatMessages.value != messages) {
        hostViewModel.addMessages(messages)
    }
    if (hostViewModel.sharedFiles.value != allFiles) {
        hostViewModel.addFiles(allFiles)
    }
}

fun MainActivity.saveSettings(hostName: String, mode: NetworkMode) {
    settingsStore.save(
        NadiSettings(
            defaultHostName = hostName,
            defaultNetworkMode = mode,
            fileRoomTreeUri = settingsStore.settings().fileRoomTreeUri
        )
    )
    Toast.makeText(this, "Pengaturan disimpan.", Toast.LENGTH_SHORT).show()
    currentScreenState.value = Screen.Home
}

fun TransferItem.displayLine(): String {
    return "${direction.label()} - ${fileName}\n${FileSizeFormatter.format(sizeBytes)} - ${status.label(progress)}"
}

fun TransferHistoryItem.displayLine(): String {
    return "${direction.label()} - ${fileName}\n${FileSizeFormatter.format(sizeBytes)} - ${status.label(progress)} - tersimpan lokal"
}

fun TransferDirection.label(): String {
    return when (this) {
        TransferDirection.SHARED -> "Dibagikan"
        TransferDirection.UPLOAD -> "Diterima"
        TransferDirection.DOWNLOAD -> "Diunduh"
        TransferDirection.CHAT_ATTACHMENT -> "Lampiran chat"
    }
}

fun TransferStatus.label(progress: Int): String {
    return when (this) {
        TransferStatus.PENDING -> "Menunggu"
        TransferStatus.RUNNING -> "Berjalan $progress%"
        TransferStatus.SUCCESS -> "Tersedia"
        TransferStatus.DOWNLOADED -> "Diunduh"
        TransferStatus.EXPIRED -> "Kedaluwarsa"
        TransferStatus.FAILED -> "Gagal"
    }
}


