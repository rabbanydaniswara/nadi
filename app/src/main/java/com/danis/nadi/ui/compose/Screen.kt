package com.danis.nadi.ui.compose

sealed class Screen {
    object Home : Screen()
    object Setup : Screen()
    object Join : Screen()
    object HostDashboard : Screen()
    object ClientJoinIdentity : Screen()
    object ClientDashboard : Screen()
    object History : Screen()
    object Settings : Screen()
}
