package com.danis.nadi.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danis.nadi.MainActivity
import com.danis.nadi.confirmExitClientRoom
import com.danis.nadi.stopActiveRoom

@Composable
fun MainScreen(activity: MainActivity) {
    val currentScreen = activity.currentScreenState.value

    BackHandler {
        when (currentScreen) {
            is Screen.ClientJoinIdentity -> activity.currentScreenState.value = Screen.Join
            is Screen.ClientDashboard -> activity.confirmExitClientRoom()
            is Screen.HostDashboard -> activity.stopActiveRoom()
            is Screen.Join, is Screen.Setup, is Screen.History, is Screen.Settings -> activity.currentScreenState.value = Screen.Home
            is Screen.Home -> activity.finish()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
            when (screen) {
                is Screen.Home -> HomeScreen(activity)
                is Screen.Setup -> SetupScreen(activity)
                is Screen.Join -> JoinScreen(activity)
                is Screen.HostDashboard -> HostDashboardScreen(activity)
                is Screen.ClientJoinIdentity -> ClientJoinIdentityScreen(activity)
                is Screen.ClientDashboard -> ClientDashboardScreen(activity)
                is Screen.History -> HistoryScreen(activity)
                is Screen.Settings -> SettingsScreen(activity)
            }
        }
    }
}
