package com.danis.nadi

import android.content.Context
import android.view.View
import android.widget.LinearLayout

fun MainActivity.showHome() {
    joinIdentityPanel.gone()
    activeClientRoomPanel.gone()
    mainScrollView.visible()
    homePanel.visible()
    joinPanel.gone()
    historyPanel.gone()
    settingsPanel.gone()
    setupPanel.gone()
    activeRoomPanel.gone()
    refreshHostDashboard()
}

fun MainActivity.showJoin() {
    joinIdentityPanel.gone()
    activeClientRoomPanel.gone()
    mainScrollView.visible()
    homePanel.gone()
    joinPanel.visible()
    historyPanel.gone()
    settingsPanel.gone()
    setupPanel.gone()
    activeRoomPanel.gone()
}

fun MainActivity.showHistory() {
    joinIdentityPanel.gone()
    activeClientRoomPanel.gone()
    mainScrollView.visible()
    homePanel.gone()
    joinPanel.gone()
    historyPanel.visible()
    settingsPanel.gone()
    setupPanel.gone()
    activeRoomPanel.gone()
    refreshHistoryScreen()
}

fun MainActivity.showSettings() {
    joinIdentityPanel.gone()
    activeClientRoomPanel.gone()
    mainScrollView.visible()
    homePanel.gone()
    joinPanel.gone()
    historyPanel.gone()
    settingsPanel.visible()
    setupPanel.gone()
    activeRoomPanel.gone()
    applySettingsToSettingsScreen()
}

fun MainActivity.showSetup() {
    joinIdentityPanel.gone()
    activeClientRoomPanel.gone()
    mainScrollView.visible()
    homePanel.gone()
    joinPanel.gone()
    historyPanel.gone()
    settingsPanel.gone()
    setupPanel.visible()
    activeRoomPanel.gone()
    applySettingsToSetup()
}

fun MainActivity.showActiveRoom() {
    joinIdentityPanel.gone()
    activeClientRoomPanel.gone()
    mainScrollView.gone()
    activeRoomPanel.visible()
    activeRoomNavigation.visible()
    activeRoomNavigation.selectedItemId = activeRoomDestinationId
    showActiveRoomSection(activeRoomDestinationId)
}

fun MainActivity.showActiveRoomSection(destinationId: Int) {
    if (destinationId != R.id.active_room_tab_chat) {
        setChatKeyboardCompactMode(false)
    }
    val showRoom = destinationId == R.id.active_room_tab_room
    val showFiles = destinationId == R.id.active_room_tab_files
    val showChat = destinationId == R.id.active_room_tab_chat
    val showParticipants = destinationId == R.id.active_room_tab_participants
    val showHistory = destinationId == R.id.active_room_tab_history

    activeRoomJoinScroll.visibleIf(showRoom)
    activeRoomFileScroll.visibleIf(showFiles)
    activeRoomChatSection.visibleIf(showChat)
    activeRoomParticipantsScroll.visibleIf(showParticipants)
    activeRoomHistoryScroll.visibleIf(showHistory)

    activeRoomJoinSection.visible()
    activeRoomPrivacySection.visible()
    activeRoomDiagnosticsSection.visible()
    activeRoomFileOverviewSection.visible()
    openFileRoomButton.visible()
    activeRoomSharedFilesSection.visible()
    activeRoomReceivedFilesSection.visible()
    activeRoomParticipantsSection.visible()
    activeRoomHistorySection.visible()

    if (showChat) {
        chatMessagesScrollView.post {
            chatMessagesScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}

fun MainActivity.showActiveClientRoom() {
    mainScrollView.gone()
    homePanel.gone()
    joinPanel.gone()
    historyPanel.gone()
    settingsPanel.gone()
    setupPanel.gone()
    activeRoomPanel.gone()
    joinIdentityPanel.gone()

    activeClientRoomPanel.visible()
    activeClientRoomNavigation.visible()
    activeClientRoomNavigation.selectedItemId = R.id.client_tab_files
    showActiveClientRoomSection(R.id.client_tab_files)
}

fun MainActivity.showActiveClientRoomSection(destinationId: Int) {
    activeClientRoomDestinationId = destinationId
    clientTabFilesScroll.gone()
    clientTabChatLayout.gone()
    clientTabInfoScroll.gone()
    when (destinationId) {
        R.id.client_tab_files -> {
            clientTabFilesScroll.visible()
        }
        R.id.client_tab_chat -> {
            clientTabChatLayout.visible()
            clientChatInput.requestFocus()
            clientChatScrollView.post {
                clientChatScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
        R.id.client_tab_info -> {
            clientTabInfoScroll.visible()
        }
    }
}

fun MainActivity.showJoinIdentityScreen() {
    mainScrollView.gone()
    homePanel.gone()
    joinPanel.gone()
    historyPanel.gone()
    settingsPanel.gone()
    setupPanel.gone()
    activeRoomPanel.gone()
    activeClientRoomPanel.gone()

    val prefs = getSharedPreferences("nadi_client_prefs", Context.MODE_PRIVATE)
    clientNimInput.setText(prefs.getString("client_nim", ""))
    clientNameInput.setText(prefs.getString("client_name", ""))

    joinIdentityPanel.visible()
}

// UI Visibility helpers
internal fun View.visible() {
    visibility = View.VISIBLE
}

internal fun View.gone() {
    visibility = View.GONE
}

internal fun View.visibleIf(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

internal fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}

internal fun MainActivity.baseInfoCard(hasTopMargin: Boolean): com.google.android.material.card.MaterialCardView {
    return com.google.android.material.card.MaterialCardView(this).apply {
        radius = 8.dp().toFloat()
        elevation = 0f
        strokeWidth = 1.dp()
        strokeColor = androidx.core.content.ContextCompat.getColor(this@baseInfoCard, R.color.nadi_line)
        setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(this@baseInfoCard, R.color.nadi_mist)
        ))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            if (hasTopMargin) topMargin = 8.dp()
        }
    }
}

internal fun MainActivity.simpleStateCard(text: String): View {
    val card = baseInfoCard(false)
    card.addView(android.widget.TextView(this).apply {
        this.text = text
        setTextColor(androidx.core.content.ContextCompat.getColor(this@simpleStateCard, R.color.nadi_soft_ink))
        textSize = 14f
        gravity = android.view.Gravity.CENTER
        setPadding(14.dp(), 18.dp(), 14.dp(), 18.dp())
    })
    return card
}
