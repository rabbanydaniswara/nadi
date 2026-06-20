package com.danis.nadi

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.danis.nadi.file.AndroidFileStore
import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.TransferItem
import com.danis.nadi.network.server.NadiHttpServer
import com.danis.nadi.room.RoomManager
import com.danis.nadi.util.NetworkAddress
import com.danis.nadi.util.QrCodeGenerator
import com.google.android.material.button.MaterialButton
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val roomManager = RoomManager()
    private val fileStore by lazy { AndroidFileStore(applicationContext) }
    private val dashboardHandler = Handler(Looper.getMainLooper())
    private var server: NadiHttpServer? = null
    private var dashboardPolling = false

    private lateinit var homePanel: LinearLayout
    private lateinit var setupPanel: LinearLayout
    private lateinit var activeRoomPanel: LinearLayout
    private lateinit var roomNameInput: EditText
    private lateinit var hostNameInput: EditText
    private lateinit var hostChatInput: EditText
    private lateinit var activeRoomNameText: TextView
    private lateinit var activeRoomCopyText: TextView
    private lateinit var joinUrlText: TextView
    private lateinit var sharedFilesText: TextView
    private lateinit var receivedFilesText: TextView
    private lateinit var chatMessagesText: TextView
    private lateinit var recentEmptyText: TextView
    private lateinit var qrImage: ImageView

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        persistReadPermissionIfPossible(uri, result.data)
        val transfer = fileStore.createSharedTransfer(uri)
        roomManager.addTransfer(transfer)
        refreshHostDashboard()
        Toast.makeText(this, "File siap dibagikan.", Toast.LENGTH_SHORT).show()
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
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        bindActions()
        showHome()
    }

    override fun onDestroy() {
        stopDashboardPolling()
        stopServer()
        roomManager.stopRoom()
        super.onDestroy()
    }

    private fun bindViews() {
        homePanel = findViewById(R.id.homePanel)
        setupPanel = findViewById(R.id.setupPanel)
        activeRoomPanel = findViewById(R.id.activeRoomPanel)
        roomNameInput = findViewById(R.id.roomNameInput)
        hostNameInput = findViewById(R.id.hostNameInput)
        hostChatInput = findViewById(R.id.hostChatInput)
        activeRoomNameText = findViewById(R.id.activeRoomNameText)
        activeRoomCopyText = findViewById(R.id.activeRoomCopyText)
        joinUrlText = findViewById(R.id.joinUrlText)
        sharedFilesText = findViewById(R.id.sharedFilesText)
        receivedFilesText = findViewById(R.id.receivedFilesText)
        chatMessagesText = findViewById(R.id.chatMessagesText)
        recentEmptyText = findViewById(R.id.recentEmptyText)
        qrImage = findViewById(R.id.qrImage)
    }

    private fun bindActions() {
        findViewById<MaterialButton>(R.id.createRoomButton).setOnClickListener {
            showSetup()
        }
        findViewById<MaterialButton>(R.id.joinRoomButton).setOnClickListener {
            Toast.makeText(this, "Scan QR dari host Nadi untuk bergabung.", Toast.LENGTH_SHORT).show()
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
    }

    private fun startLocalRoom() {
        stopDashboardPolling()
        stopServer()
        val preparingSession = roomManager.startPreparing(
            roomName = roomNameInput.text?.toString().orEmpty(),
            hostName = hostNameInput.text?.toString().orEmpty()
        )
        val serverStart = startServerOnAvailablePort()
        if (serverStart == null) {
            roomManager.fail()
            Toast.makeText(
                this,
                "Ruang belum bisa dibuat. Coba tutup aplikasi lain lalu mulai lagi.",
                Toast.LENGTH_LONG
            ).show()
            showSetup()
            return
        }

        server = serverStart.server
        val hostAddress = NetworkAddress.firstLocalIpv4() ?: "127.0.0.1"
        val joinUrl = "http://$hostAddress:${serverStart.port}/?token=${preparingSession.token}"
        val activeSession = roomManager.activate(joinUrl) ?: preparingSession.copy(localUrl = joinUrl)
        renderActiveRoom(activeSession)
        startDashboardPolling()
    }

    private fun startServerOnAvailablePort(): ServerStart? {
        val ports = listOf(8080, 8081, 8082, 8090)
        for (port in ports) {
            val candidate = NadiHttpServer(port, roomManager, fileStore)
            try {
                candidate.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                return ServerStart(candidate, port)
            } catch (_: IOException) {
                candidate.stop()
            }
        }
        return null
    }

    private fun renderActiveRoom(session: RoomSession) {
        val joinUrl = session.localUrl.orEmpty()
        activeRoomNameText.text = session.roomName
        activeRoomCopyText.text = getString(R.string.same_wifi_note)
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
        val hostName = roomManager.currentSession()?.hostName ?: getString(R.string.host_name_default)
        val message = roomManager.addMessage(
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
        val shared = roomManager.sharedFiles()
        val received = roomManager.receivedFiles()
        val messages = roomManager.snapshot().messages.takeLast(8)
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
        val recent = roomManager.recentTransfers()
        recentEmptyText.text = if (recent.isEmpty()) {
            getString(R.string.recent_empty)
        } else {
            recent.joinToString(separator = "\n") { it.displayLine() }
        }
    }

    private fun TransferItem.displayLine(): String {
        return "${fileName}\n${FileSizeFormatter.format(sizeBytes)} - ${status.name.lowercase()}"
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
        stopServer()
        roomManager.stopRoom()
        Toast.makeText(this, "Ruang sudah ditutup.", Toast.LENGTH_SHORT).show()
        showHome()
    }

    private fun stopServer() {
        server?.stop()
        server = null
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
        setupPanel.gone()
        activeRoomPanel.gone()
        refreshHostDashboard()
    }

    private fun showSetup() {
        homePanel.gone()
        setupPanel.visible()
        activeRoomPanel.gone()
    }

    private fun showActiveRoom() {
        homePanel.gone()
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

private data class ServerStart(
    val server: NadiHttpServer,
    val port: Int
)
