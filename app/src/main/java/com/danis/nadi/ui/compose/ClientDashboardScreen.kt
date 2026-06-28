package com.danis.nadi.ui.compose

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.danis.nadi.MainActivity
import com.danis.nadi.closeClientRoom
import com.danis.nadi.confirmExitClientRoom
import com.danis.nadi.downloadClientSharedFile
import com.danis.nadi.ensureClientAttachmentTransfer
import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.openClientChatAttachment
import com.danis.nadi.getUriMetadata
import com.danis.nadi.selectClientChatAttachment
import com.danis.nadi.selectClientUploadFile
import com.danis.nadi.sendClientChatMessage
import com.danis.nadi.ui.theme.NadiBackground
import com.danis.nadi.ui.theme.NadiGreen
import com.danis.nadi.ui.theme.NadiGreenLight
import com.danis.nadi.ui.theme.NadiMineGreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDashboardScreen(activity: MainActivity) {
    var selectedTab by remember { mutableStateOf(0) }

    val roomName by activity.clientViewModel.roomName.collectAsState()
    val hostName by activity.clientViewModel.hostName.collectAsState()
    val clientCount by activity.clientViewModel.clientCount.collectAsState()

    val showExitDialog by activity.clientViewModel.showExitDialog.collectAsState()
    if (showExitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { activity.clientViewModel.showExitDialog.value = false },
            title = { Text("Keluar dari Room") },
            text = { Text("Apakah Anda yakin ingin keluar dari room ini?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { activity.closeClientRoom() }
                ) {
                    Text("Keluar")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { activity.clientViewModel.showExitDialog.value = false }
                ) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        containerColor = NadiBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(roomName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Host: $hostName | Peserta: $clientCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                },
                actions = {
                    TextButtonRed(text = "Keluar Room") {
                        activity.confirmExitClientRoom()
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
                val tabs = listOf("Files", "Chat", "Info")
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = (selectedTab == index),
                        onClick = { selectedTab = index },
                        label = { Text(label, fontSize = 10.sp) },
                        icon = {
                            androidx.compose.material3.Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.List
                                    1 -> Icons.Default.Email
                                    else -> Icons.Default.Info
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
                0 -> ClientFilesTab(activity)
                1 -> ClientChatTab(activity)
                2 -> ClientInfoTab(activity)
            }
        }
    }
}

@Composable
fun ClientFilesTab(activity: MainActivity) {
    val files by activity.clientViewModel.sharedFiles.collectAsState()

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
            Text("File dari Host", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(
                onClick = { activity.selectClientUploadFile() },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NadiGreen)
            ) {
                Text("Kirim File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (files.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Belum ada file di File Room.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    activity.downloadClientSharedFile(
                                        file.transferId,
                                        file.fileName,
                                        file.mimeType,
                                        file.senderName ?: "Host"
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NadiGreen)
                            ) {
                                Text("Unduh", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClientChatTab(activity: MainActivity) {
    val messages by activity.clientViewModel.chatMessages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var lastProcessedSize by remember { mutableStateOf(0) }
    LaunchedEffect(messages.size) {
        if (messages.size > lastProcessedSize) {
            for (i in lastProcessedSize until messages.size) {
                activity.ensureClientAttachmentTransfer(messages[i])
            }
            lastProcessedSize = messages.size
        }
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val clientId = remember { activity.getSharedPreferences("nadi_client_prefs", Context.MODE_PRIVATE).getString("client_id", "").orEmpty() }

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
                val isMine = msg.senderId == clientId
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
                                ClientAttachmentBubble(msg, activity)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (activity.clientPendingAttachmentUri.value != null) {
            val (name, _) = activity.getUriMetadata(activity.clientPendingAttachmentUri.value!!)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(Icons.Default.Info, contentDescription = "Lampiran", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Terpilih: $name", fontSize = 12.sp, color = NadiGreen)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { activity.clientPendingAttachmentUri.value = null }, modifier = Modifier.size(24.dp)) {
                    androidx.compose.material3.Icon(Icons.Default.Close, contentDescription = "Batal", modifier = Modifier.size(16.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { activity.selectClientChatAttachment() }) {
                androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = "Lampiran")
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
                    val trimmed = inputText.trim()
                    if (trimmed.isNotEmpty() || activity.clientPendingAttachmentUri.value != null) {
                        // In the original client code, clientSendChatButton click calls sendClientChatMessage
                        // which reads from clientChatInput. Let's parameterize it in MainActivity+ClientChat or
                        // simply write to activity's property first and trigger it.
                        // Wait! Let's check how sendClientChatMessage is defined. We will parameterize it next!
                        activity.sendClientChatMessage(trimmed)
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
fun ClientAttachmentBubble(msg: ChatMessage, activity: MainActivity) {
    val transfer = activity.clientTransfersMap[msg.attachmentTransferId]
    val status = transfer?.status?.name ?: msg.attachmentStatus

    var showLightbox by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val isImage = msg.attachmentMimeType?.startsWith("image/") == true ||
                  msg.attachmentFileName?.lowercase()?.let {
                      it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp")
                  } == true

    val previewUrl = msg.attachmentTransferId?.let { id ->
        activity.roomClient?.buildUrl("/api/download/$id")?.let { "$it&preview=1" }
    }
    val imageUri: Any? = transfer?.localUri?.let { android.net.Uri.parse(it) } ?: previewUrl

    if (showLightbox && isImage) {
        if (imageUri != null) {
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
                        model = imageUri,
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
                        activity.openClientChatAttachment(transfer)
                    }
                }
            }
    ) {
        if (isImage) {
            if (imageUri != null) {
                coil.compose.AsyncImage(
                    model = imageUri,
                    contentDescription = msg.attachmentFileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.surfaceVariant).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Default.Share, contentDescription = "Image")
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(Icons.Default.Share, contentDescription = "Attachment")
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(msg.attachmentFileName ?: "Lampiran", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "${FileSizeFormatter.format(msg.attachmentSizeBytes)} - $status",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun ClientInfoTab(activity: MainActivity) {
    val status by activity.clientViewModel.connectionStatus.collectAsState()
    val selfNim by activity.clientViewModel.selfNim.collectAsState()
    val selfName by activity.clientViewModel.selfName.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Informasi Koneksi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Status Koneksi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(status, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (status == "Terhubung") NadiGreen else MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Identitas Anda", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("NIM: $selfNim", fontSize = 12.sp)
                Text("Nama: $selfName", fontSize = 12.sp)
            }
        }
    }
}
