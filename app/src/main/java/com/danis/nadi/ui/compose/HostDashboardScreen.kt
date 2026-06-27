package com.danis.nadi.ui.compose

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.danis.nadi.startDashboardPolling
import com.danis.nadi.stopDashboardPolling
import com.danis.nadi.buildWifiQrPayload
import com.danis.nadi.openChatAttachment
import com.danis.nadi.network.server.ServerFileRules
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.danis.nadi.MainActivity
import com.danis.nadi.buildWifiQrPayload
import com.danis.nadi.clearChatAttachments
import com.danis.nadi.copyDiagnostics
import com.danis.nadi.copyJoinInstructions
import com.danis.nadi.copyJoinUrl
import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.ConnectedClient
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.openChatAttachmentsLocation
import com.danis.nadi.openFilePicker
import com.danis.nadi.openFileRoomLocation
import com.danis.nadi.openHostChatAttachmentPicker
import com.danis.nadi.regenerateJoinLink
import com.danis.nadi.room.NetworkMode
import com.danis.nadi.sendHostMessage
import com.danis.nadi.stopActiveRoom
import com.danis.nadi.ui.theme.NadiBackground
import com.danis.nadi.ui.theme.NadiGreen
import com.danis.nadi.ui.theme.NadiGreenLight
import com.danis.nadi.ui.theme.NadiMineGreen
import com.danis.nadi.util.QrCodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostDashboardScreen(activity: MainActivity) {
    var selectedTab by remember { mutableStateOf(0) }
    val session = activity.controller.roomManager.currentSession()
    val activeRoom = activity.controller.currentActiveRoom()

    // Start/Stop polling automatically during the screen lifecycle
    DisposableEffect(Unit) {
        activity.startDashboardPolling()
        onDispose {
            activity.stopDashboardPolling()
        }
    }

    if (session == null || activeRoom == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Room tidak aktif.")
        }
        return
    }

    Scaffold(
        containerColor = NadiBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session.roomName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Milik Host: ${session.hostName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                },
                actions = {
                    TextButtonRed(text = "Tutup Room") {
                        activity.stopActiveRoom()
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val tabs = listOf("Room", "Files", "Peserta", "Chat", "Diag")
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = (selectedTab == index),
                        onClick = { selectedTab = index },
                        label = { Text(label, fontSize = 10.sp) },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.Home
                                    1 -> Icons.Default.List
                                    2 -> Icons.Default.Person
                                    3 -> Icons.Default.Email
                                    else -> Icons.Default.Settings
                                },
                                contentDescription = label
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NadiGreen,
                            selectedTextColor = NadiGreen,
                            indicatorColor = NadiGreenLight
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> RoomTab(activity, activeRoom)
                1 -> FilesTab(activity)
                2 -> ParticipantsTab(activity)
                3 -> ChatTab(activity)
                4 -> DiagTab(activity)
            }
        }
    }
}

@Composable
fun TextButtonRed(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.padding(end = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RoomTab(activity: MainActivity, activeRoom: com.danis.nadi.room.ActiveRoom) {
    val scrollState = rememberScrollState()
    val localUrl = activeRoom.session.localUrl.orEmpty()

    val qrBitmap = remember(localUrl) {
        if (localUrl.isNotBlank()) QrCodeGenerator.generate(localUrl, 380) else null
    }

    val wifiPayload = remember(activeRoom) {
        activity.buildWifiQrPayload(activeRoom.hotspotSsid.orEmpty(), activeRoom.hotspotPassword.orEmpty())
    }
    val wifiQrBitmap = remember(wifiPayload) {
        if (activeRoom.mode == NetworkMode.HOTSPOT && activeRoom.hotspotSsid?.isNotBlank() == true) {
            QrCodeGenerator.generate(wifiPayload, 340)
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Scan QR Code untuk Gabung", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                qrBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(180.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(localUrl, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = NadiGreen)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "PIN Room: ${activeRoom.session.pin.orEmpty().ifBlank { "-" }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        if (activeRoom.mode == NetworkMode.HOTSPOT && !activeRoom.hotspotSsid.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!activeRoom.hotspotPassword.isNullOrBlank()) {
                        Text("Scan untuk Koneksi Wi-Fi Hotspot", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        wifiQrBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Wifi QR Code",
                                modifier = Modifier.size(160.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("SSID: ${activeRoom.hotspotSsid}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Sandi: ${activeRoom.hotspotPassword}", fontSize = 12.sp)
                    } else {
                        Text("Koneksi Hotspot Pribadi Aktif", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Silakan hubungkan perangkat klien/komputer ke Hotspot Pribadi (Tethering) HP Anda terlebih dahulu.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Panduan Bergabung:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                if (activeRoom.mode == NetworkMode.HOTSPOT) {
                    Text("1. Hubungkan perangkat ke Wi-Fi hotspot ini.", fontSize = 12.sp)
                } else {
                    Text("1. Hubungkan perangkat ke Wi-Fi yang sama dengan host.", fontSize = 12.sp)
                }
                Text("2. Buka URL di browser atau scan QR code.", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { activity.copyJoinUrl() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NadiGreen)
            ) {
                Text("Salin URL", fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { activity.regenerateJoinLink() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NadiGreen)
            ) {
                Text("Reset Link", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FilesTab(activity: MainActivity) {
    val files by activity.hostViewModel.sharedFiles.collectAsState()
    val scrollPath = activity.controller.currentRoomFolderPath().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("File Room Aktif", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(
                onClick = { activity.openFilePicker() },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NadiGreen)
            ) {
                Text("+ Bagikan File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Folder Penyimpanan: $scrollPath",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (files.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Belum ada file yang dibagikan di room ini.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                }
            } else {
                items(files, key = { it.transferId }) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.fileName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "${FileSizeFormatter.format(file.sizeBytes)} - ${file.senderName ?: "Host"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = { activity.openFileRoomLocation() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Buka Folder Penyimpanan File", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ParticipantsTab(activity: MainActivity) {
    val clients by activity.hostViewModel.connectedClients.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Peserta Terhubung (${clients.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (clients.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Menunggu peserta terhubung...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                }
            } else {
                items(clients, key = { it.clientId }) { client ->
                    Card(
                         modifier = Modifier.fillMaxWidth(),
                         shape = RoundedCornerShape(10.dp),
                         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Device")
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                val label = if (client.nim.isNotBlank() || client.name.isNotBlank()) {
                                    "${client.nim.ifBlank { "-" }} - ${client.name.ifBlank { client.displayName }}"
                                } else {
                                    client.displayName
                                }
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("IP: ${client.ipAddress} | User-Agent: ${client.userAgent}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatTab(activity: MainActivity) {
    val messages by activity.hostViewModel.chatMessages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Scroll to bottom when a new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.messageId }) { msg ->
                val isMine = msg.senderId.startsWith("host")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMine) NadiMineGreen else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(msg.senderName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NadiGreen)
                            Spacer(modifier = Modifier.height(2.dp))
                            if (!msg.text.isNullOrBlank()) {
                                Text(msg.text, fontSize = 14.sp)
                            }
                            if (msg.attachmentTransferId != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                AttachmentBubble(msg, activity)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { activity.openHostChatAttachmentPicker() }) {
                Icon(Icons.Default.Add, contentDescription = "Lampiran")
            }
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Tulis pesan") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (inputText.trim().isNotEmpty()) {
                        activity.sendHostMessage(inputText.trim())
                        inputText = ""
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NadiGreen)
            ) {
                Text("Kirim", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AttachmentBubble(msg: ChatMessage, activity: MainActivity) {
    var showLightbox by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val isImage = msg.attachmentMimeType?.startsWith("image/") == true ||
                  msg.attachmentFileName?.lowercase()?.let {
                      it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp")
                  } == true

    val transfer = remember(msg.attachmentTransferId) {
        msg.attachmentTransferId?.let { activity.controller.roomManager.transferById(it) }
    }

    if (showLightbox && isImage) {
        val uri = transfer?.localUri?.let { android.net.Uri.parse(it) }
        if (uri != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showLightbox = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f))
                        .clickable { showLightbox = false },
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.AsyncImage(
                        model = uri,
                        contentDescription = msg.attachmentFileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isImage) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isImage) {
                    showLightbox = true
                } else {
                    if (transfer != null) {
                        activity.openChatAttachment(transfer)
                    }
                }
            }
    ) {
        if (isImage) {
            val uri = transfer?.localUri?.let { android.net.Uri.parse(it) }
            if (uri != null) {
                coil.compose.AsyncImage(
                    model = uri,
                    contentDescription = msg.attachmentFileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.surfaceVariant).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Share, contentDescription = "Image")
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Share, contentDescription = "Attachment")
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(msg.attachmentFileName ?: "Lampiran", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "${FileSizeFormatter.format(msg.attachmentSizeBytes)} - ${msg.attachmentStatus}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun DiagTab(activity: MainActivity) {
    val diagText by activity.hostViewModel.diagnosticsText.collectAsState()
    val chatStatsOpt by activity.hostViewModel.chatAttachmentStats.collectAsState()
    val chatStats = chatStatsOpt ?: remember { activity.controller.roomManager.chatAttachmentStorageStats() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Diagnostik & Stats", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Kapasitas Lampiran Chat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Aktif: ${chatStats.availableCount} berkas\n" +
                    "Ukuran: ${FileSizeFormatter.format(chatStats.totalBytes)} / ${FileSizeFormatter.format(ServerFileRules.MAX_CHAT_ATTACHMENT_STORAGE_BYTES)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    OutlinedButton(
                        onClick = { activity.openChatAttachmentsLocation() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Buka Folder", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            activity.clearChatAttachments()
                            Toast.makeText(activity, "Lampiran chat dibersihkan.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Bersihkan", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Informasi Sistem", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(diagText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { activity.copyDiagnostics() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NadiGreen)
                ) {
                    Text("Salin Informasi Diagnostik", fontSize = 12.sp)
                }
            }
        }
    }
}
