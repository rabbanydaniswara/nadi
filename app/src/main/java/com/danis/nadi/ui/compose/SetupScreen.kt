package com.danis.nadi.ui.compose

import com.danis.nadi.startLocalRoom
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danis.nadi.MainActivity
import com.danis.nadi.R
import com.danis.nadi.room.NetworkMode
import com.danis.nadi.ui.theme.NadiBackground
import com.danis.nadi.ui.theme.NadiGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(activity: MainActivity) {
    val scrollState = rememberScrollState()
    val settings = remember { activity.settingsStore.settings() }

    var roomName by remember { mutableStateOf("") }
    var hostName by remember { mutableStateOf(settings.defaultHostName.ifBlank { "Host" }) }
    var roomPin by remember { mutableStateOf("") }
    var networkMode by remember { mutableStateOf(settings.defaultNetworkMode) }

    Scaffold(
        containerColor = NadiBackground,
        topBar = {
            TopAppBar(
                title = { Text("Buat Room Baru", fontWeight = FontWeight.Bold) },
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
                text = "Konfigurasi Room",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NadiGreen
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = roomName,
                onValueChange = { roomName = it },
                label = { Text("Nama Room") },
                placeholder = { Text("Contoh: Kelas Antropologi A") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = hostName,
                onValueChange = { hostName = it },
                label = { Text("Nama Pengajar / Host") },
                placeholder = { Text("Contoh: Budi Santoso") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = roomPin,
                onValueChange = { if (it.length <= 8) roomPin = it },
                label = { Text("PIN Keamanan (Opsional)") },
                placeholder = { Text("PIN 4-8 digit angka") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Mode Jaringan Lokal",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Same Wifi Radio option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (networkMode == NetworkMode.SAME_WIFI),
                        onClick = { networkMode = NetworkMode.SAME_WIFI }
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (networkMode == NetworkMode.SAME_WIFI),
                        onClick = { networkMode = NetworkMode.SAME_WIFI },
                        colors = RadioButtonDefaults.colors(selectedColor = NadiGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Satu Jaringan Wi-Fi", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Peserta harus terhubung di Wi-Fi/AP yang sama dengan Anda.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hotspot Radio option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (networkMode == NetworkMode.HOTSPOT),
                        onClick = { networkMode = NetworkMode.HOTSPOT }
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (networkMode == NetworkMode.HOTSPOT),
                        onClick = { networkMode = NetworkMode.HOTSPOT },
                        colors = RadioButtonDefaults.colors(selectedColor = NadiGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Aktifkan Hotspot Mandiri", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Aplikasi otomatis membuat Hotspot lokal dan peserta terhubung ke Hotspot Anda.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val finalRoomName = roomName.ifBlank { "Ruang Tanpa Nama" }
                    activity.startLocalRoom(finalRoomName, hostName, roomPin, networkMode)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NadiGreen)
            ) {
                Text("Mulai Room", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
