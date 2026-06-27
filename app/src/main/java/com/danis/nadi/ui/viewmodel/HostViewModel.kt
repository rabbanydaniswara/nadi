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

class HostViewModel(
    private val chatRepository: ChatRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    private val _sharedFiles = MutableStateFlow<List<TransferItem>>(emptyList())
    val sharedFiles: StateFlow<List<TransferItem>> = _sharedFiles

    val connectedClients = MutableStateFlow<List<com.danis.nadi.model.ConnectedClient>>(emptyList())
    val diagnosticsText = MutableStateFlow("")
    val chatAttachmentStats = MutableStateFlow<com.danis.nadi.room.ChatAttachmentStorageStats?>(null)

    private var activeRoomId: String? = null

    fun loadRoomData(roomId: String) {
        activeRoomId = roomId
        viewModelScope.launch {
            chatRepository.getMessagesForRoom(roomId).collectLatest {
                _chatMessages.value = it
            }
        }
        viewModelScope.launch {
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
