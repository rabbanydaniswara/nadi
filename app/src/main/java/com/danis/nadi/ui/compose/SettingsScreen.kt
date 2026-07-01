package com.danis.nadi.ui.compose

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danis.nadi.MainActivity
import com.danis.nadi.clearChatAttachments
import com.danis.nadi.openFileRoomFolderPicker
import com.danis.nadi.room.NetworkMode
import com.danis.nadi.saveSettings
import com.danis.nadi.ui.theme.NadiBackground
import com.danis.nadi.ui.theme.NadiGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(activity: MainActivity) {
    val scrollState = rememberScrollState()

    var settings = remember { activity.settingsStore.settings() }
    var defaultHostName by remember { mutableStateOf(settings.defaultHostName) }
    var defaultNetworkMode by remember { mutableStateOf(settings.defaultNetworkMode) }
    var fileRoomTreeUri by remember { mutableStateOf(settings.fileRoomTreeUri) }

    Scaffold(
        containerColor = NadiBackground,
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { activity.currentScreenState.value = Screen.Home }) {
                        Text("←", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NadiGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            Text(
                text = "Pengaturan Umum",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = NadiGreen
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = defaultHostName,
                onValueChange = { defaultHostName = it },
                label = { Text("Nama Host Standar") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Mode Jaringan Standar",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Same Wifi
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (defaultNetworkMode == NetworkMode.SAME_WIFI),
                        onClick = { defaultNetworkMode = NetworkMode.SAME_WIFI }
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (defaultNetworkMode == NetworkMode.SAME_WIFI),
                        onClick = { defaultNetworkMode = NetworkMode.SAME_WIFI },
                        colors = RadioButtonDefaults.colors(selectedColor = NadiGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Satu Jaringan Wi-Fi", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hotspot
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (defaultNetworkMode == NetworkMode.HOTSPOT),
                        onClick = { defaultNetworkMode = NetworkMode.HOTSPOT }
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (defaultNetworkMode == NetworkMode.HOTSPOT),
                        onClick = { defaultNetworkMode = NetworkMode.HOTSPOT },
                        colors = RadioButtonDefaults.colors(selectedColor = NadiGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aktifkan Hotspot Mandiri", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Penyimpanan File Room",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = NadiGreen
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fileRoomTreeUri?.let { "Folder kustom: $it" } ?: "Menggunakan folder bawaan (Download/Nadi)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { activity.openFileRoomFolderPicker() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Pilih Folder")
                }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(
                    onClick = {
                        activity.settingsStore.save(activity.settingsStore.settings().copy(fileRoomTreeUri = null))
                        fileRoomTreeUri = null
                        Toast.makeText(activity, "Lokasi kembali ke default.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset Default")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = { activity.clearChatAttachments() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Hapus Semua Lampiran Chat", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = {
                    activity.saveSettings(defaultHostName, defaultNetworkMode)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NadiGreen)
            ) {
                Text("Simpan Pengaturan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Aplikasi Nadi v1.0\nPembuat: Daniswara Rabbany (NIM: 231011402591)\nUniversitas Pamulang (UNPAM)\nTugas Akhir Mobile Programming",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 16.sp
            )
        }
    }
}
