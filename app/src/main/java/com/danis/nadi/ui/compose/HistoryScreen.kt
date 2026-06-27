package com.danis.nadi.ui.compose

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.danis.nadi.displayLine
import com.danis.nadi.ui.theme.NadiBackground
import com.danis.nadi.ui.theme.NadiGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(activity: MainActivity) {
    val scrollState = rememberScrollState()
    var historyListText by remember {
        mutableStateOf(
            activity.controller.recentHistory().let { history ->
                if (history.isEmpty()) {
                    "Belum ada riwayat transfer lokal."
                } else {
                    history.joinToString(separator = "\n\n") { it.displayLine() }
                }
            }
        )
    }

    Scaffold(
        containerColor = NadiBackground,
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Lokal", fontWeight = FontWeight.Bold) },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Semua Transfer Masa Lalu",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = historyListText,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedButton(
                onClick = {
                    activity.controller.clearHistory()
                    activity.hostViewModel.clearRoomData()
                    activity.clientViewModel.clearRoomData()
                    historyListText = "Belum ada riwayat transfer lokal."
                    Toast.makeText(activity, "Riwayat lokal dihapus.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Hapus Riwayat Lokal", fontWeight = FontWeight.Bold)
            }
        }
    }
}
