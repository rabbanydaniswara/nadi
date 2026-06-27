package com.danis.nadi.ui.compose

import android.content.ClipboardManager
import android.content.Context
import com.danis.nadi.openJoinedRoom
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danis.nadi.MainActivity
import com.danis.nadi.ui.theme.NadiBackground
import com.danis.nadi.ui.theme.NadiGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinScreen(activity: MainActivity) {
    val scrollState = rememberScrollState()
    var roomUrl by remember { mutableStateOf("") }

    val showPinDialog by activity.clientViewModel.showPinDialog.collectAsState()
    var pinValue by remember { mutableStateOf("") }

    Scaffold(
        containerColor = NadiBackground,
        topBar = {
            TopAppBar(
                title = { Text("Gabung ke Room", fontWeight = FontWeight.Bold) },
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
                text = "Masukkan Tautan Room",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NadiGreen
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Masukkan alamat URL room Nadi dari Host (dari scan QR atau link yang dibagikan).",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = roomUrl,
                onValueChange = { roomUrl = it },
                label = { Text("URL Room") },
                placeholder = { Text("http://192.168.1.X:8080/?token=...") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clipboard.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(activity)
                            ?.toString()
                            .orEmpty()
                            .trim()
                        if (text.isBlank()) {
                            Toast.makeText(activity, "Clipboard kosong.", Toast.LENGTH_SHORT).show()
                        } else {
                            roomUrl = text
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tempel Tautan", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        activity.openJoinedRoom(roomUrl)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NadiGreen)
                ) {
                    Text("Masuk", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { activity.clientViewModel.showPinDialog.value = false },
            title = { Text("PIN Diperlukan", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Room ini dilindungi oleh PIN. Silakan masukkan PIN room untuk melanjutkan:")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinValue,
                        onValueChange = { if (it.length <= 8) pinValue = it },
                        label = { Text("PIN Room") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pin = pinValue.trim()
                        if (pin.isNotEmpty()) {
                            activity.clientViewModel.showPinDialog.value = false
                            activity.clientViewModel.pendingPinCallback?.invoke(pin)
                            pinValue = ""
                        } else {
                            Toast.makeText(activity, "PIN tidak boleh kosong", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Masuk", color = NadiGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        activity.clientViewModel.showPinDialog.value = false
                        pinValue = ""
                    }
                ) {
                    Text("Batal")
                }
            }
        )
    }
}
