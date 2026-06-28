package com.danis.nadi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danis.nadi.data.repository.ChatRepository
import com.danis.nadi.data.repository.FileRepository
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.TransferItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ClientViewModel(
    private val chatRepository: ChatRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    private val _sharedFiles = MutableStateFlow<List<TransferItem>>(emptyList())
    val sharedFiles: StateFlow<List<TransferItem>> = _sharedFiles

    val connectionStatus = MutableStateFlow("Terputus")
    val roomName = MutableStateFlow("-")
    val hostName = MutableStateFlow("-")
    val clientCount = MutableStateFlow(0)
    val selfNim = MutableStateFlow("")
    val selfName = MutableStateFlow("")
    val showPinDialog = MutableStateFlow(false)
    val showExitDialog = MutableStateFlow(false)
    var pendingPinCallback: ((String) -> Unit)? = null

    private var activeRoomId: String? = null

    private var chatJob: kotlinx.coroutines.Job? = null
    private var fileJob: kotlinx.coroutines.Job? = null

    fun loadRoomData(roomId: String) {
        activeRoomId = roomId
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            chatRepository.getMessagesForRoom(roomId).collectLatest {
                _chatMessages.value = it
            }
        }
        fileJob?.cancel()
        fileJob = viewModelScope.launch {
            fileRepository.getFilesForRoom(roomId).collectLatest {
                _sharedFiles.value = it
            }
        }
    }

    fun addMessage(message: ChatMessage) {
        val roomId = activeRoomId ?: return
        viewModelScope.launch {
            chatRepository.saveMessage(roomId, message)
        }
    }

    fun addMessages(messages: List<ChatMessage>) {
        val roomId = activeRoomId ?: return
        viewModelScope.launch {
            chatRepository.saveMessages(roomId, messages)
        }
    }

    fun addFile(file: TransferItem) {
        val roomId = activeRoomId ?: return
        viewModelScope.launch {
            fileRepository.saveFile(roomId, file)
        }
    }

    fun addFiles(files: List<TransferItem>) {
        val roomId = activeRoomId ?: return
        viewModelScope.launch {
            fileRepository.saveFiles(roomId, files)
        }
    }

    fun clearRoomData() {
        val roomId = activeRoomId ?: return
        viewModelScope.launch {
            chatRepository.clearMessagesForRoom(roomId)
            fileRepository.clearFilesForRoom(roomId)
        }
    }
}
