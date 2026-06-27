package com.danis.nadi.ui.compose

import android.content.Context
import com.danis.nadi.submitClientIdentity
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danis.nadi.MainActivity
import com.danis.nadi.ui.theme.NadiBackground
import com.danis.nadi.ui.theme.NadiGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientJoinIdentityScreen(activity: MainActivity) {
    val scrollState = rememberScrollState()

    val prefs = remember { activity.getSharedPreferences("nadi_client_prefs", Context.MODE_PRIVATE) }
    var nim by remember { mutableStateOf(prefs.getString("client_nim", "").orEmpty()) }
    var name by remember { mutableStateOf(prefs.getString("client_name", "").orEmpty()) }

    Scaffold(
        containerColor = NadiBackground,
        topBar = {
            TopAppBar(
                title = { Text("Identitas Klien", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { activity.currentScreenState.value = Screen.Join }) {
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
                text = "Identitas Peserta Room",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NadiGreen
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Identitas ini (NIM dan Nama) akan melekat pada riwayat obrolan dan berkas yang Anda kirimkan ke room.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = nim,
                onValueChange = { nim = it.trim().replace("\\s".toRegex(), "") },
                label = { Text("Nomor Induk Mahasiswa (NIM)") },
                placeholder = { Text("Contoh: 2310114001") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama Lengkap") },
                placeholder = { Text("Contoh: Daniswara Rabbany") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { activity.currentScreenState.value = Screen.Join },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Batal")
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        activity.submitClientIdentity(nim, name)
                    },
                    modifier = Modifier.weight(2.5f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NadiGreen)
                ) {
                    Text("Masuk ke Room", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
