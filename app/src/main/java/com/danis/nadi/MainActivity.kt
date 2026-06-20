package com.danis.nadi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.danis.nadi.model.RoomSession
import com.danis.nadi.network.server.NadiHttpServer
import com.danis.nadi.room.RoomManager
import com.danis.nadi.util.NetworkAddress
import com.danis.nadi.util.QrCodeGenerator
import com.google.android.material.button.MaterialButton
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val roomManager = RoomManager()
    private var server: NadiHttpServer? = null

    private lateinit var homePanel: LinearLayout
    private lateinit var setupPanel: LinearLayout
    private lateinit var activeRoomPanel: LinearLayout
    private lateinit var roomNameInput: EditText
    private lateinit var hostNameInput: EditText
    private lateinit var activeRoomNameText: TextView
    private lateinit var activeRoomCopyText: TextView
    private lateinit var joinUrlText: TextView
    private lateinit var qrImage: ImageView

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
        activeRoomNameText = findViewById(R.id.activeRoomNameText)
        activeRoomCopyText = findViewById(R.id.activeRoomCopyText)
        joinUrlText = findViewById(R.id.joinUrlText)
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
    }

    private fun startLocalRoom() {
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
    }

    private fun startServerOnAvailablePort(): ServerStart? {
        val ports = listOf(8080, 8081, 8082, 8090)
        for (port in ports) {
            val candidate = NadiHttpServer(port, roomManager)
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
        activeRoomCopyText.text = "Bagikan QR atau URL ini ke perangkat di jaringan Wi-Fi yang sama."
        joinUrlText.text = joinUrl
        if (joinUrl.isNotBlank()) {
            val qrSize = (220 * resources.displayMetrics.density).toInt()
            qrImage.setImageBitmap(QrCodeGenerator.generate(joinUrl, qrSize))
        } else {
            qrImage.setImageDrawable(null)
        }
        showActiveRoom()
    }

    private fun stopActiveRoom() {
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
