package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UploadedFile(
    val name: String,
    val content: String,
    val type: String
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ChatRepository(db.chatDao())

    val userProfile: StateFlow<UserProfile?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allSessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    // Dynamically retrieve messages whenever the current session ID shifts
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<ChatMessage>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentAttachedFile = MutableStateFlow<UploadedFile?>(null)
    val currentAttachedFile: StateFlow<UploadedFile?> = _currentAttachedFile.asStateFlow()

    private val _isGeneratingResponse = MutableStateFlow(false)
    val isGeneratingResponse: StateFlow<Boolean> = _isGeneratingResponse.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureProfileExists()
            // Auto select or create a first session if none exists
            repository.allSessions.collectLatest { sessions ->
                if (sessions.isNotEmpty() && _currentSessionId.value == null) {
                    _currentSessionId.value = sessions.first().id
                }
            }
        }
    }

    fun selectSession(sessionId: Long) {
        _currentSessionId.value = sessionId
    }

    fun createSession(title: String = "New Session", academicLevel: String = "High School", studyMode: String = "Standard") {
        viewModelScope.launch {
            val newId = repository.createNewSession(title, academicLevel, studyMode)
            _currentSessionId.value = newId
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = allSessions.value.firstOrNull { it.id != sessionId }?.id
            }
        }
    }

    fun updateSessionTitle(sessionId: Long, title: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, title)
        }
    }

    fun updateSessionPinned(sessionId: Long, isPinned: Boolean) {
        viewModelScope.launch {
            repository.updateSessionPinned(sessionId, isPinned)
        }
    }

    fun updateSessionStudyMode(sessionId: Long, studyMode: String) {
        viewModelScope.launch {
            repository.updateSessionStudyMode(sessionId, studyMode)
        }
    }

    fun updateProfile(fullName: String, email: String, academicLevel: String) {
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfile()
            repository.updateUserProfile(
                current.copy(
                    fullName = fullName,
                    email = email,
                    academicLevel = academicLevel
                )
            )
        }
    }

    fun attachFile(name: String, content: String, type: String) {
        _currentAttachedFile.value = UploadedFile(name, content, type)
    }

    fun clearAttachedFile() {
        _currentAttachedFile.value = null
    }

    fun sendMessage(text: String) {
        val sessionId = _currentSessionId.value ?: return
        if (text.trim().isEmpty() && _currentAttachedFile.value == null) return

        viewModelScope.launch {
            val file = _currentAttachedFile.value
            _isGeneratingResponse.value = true

            // Insert user message to room
            val userMsg = ChatMessage(
                sessionId = sessionId,
                role = "user",
                content = text,
                attachedFileName = file?.name,
                attachedFileContent = file?.content
            )
            repository.insertMessage(userMsg)

            // Auto-rename chat from context if it is still named "New Session"
            val session = allSessions.value.find { it.id == sessionId }
            if (session != null && (session.title == "New Session" || session.title == "New Chat")) {
                val words = text.split(" ")
                val shortTitle = if (words.size > 3) {
                    words.take(3).joinToString(" ") + "..."
                } else {
                    text
                }
                repository.updateSessionTitle(sessionId, shortTitle)
            }

            // Clear attached file display
            _currentAttachedFile.value = null

            // Fire request to Gemini and capture answer
            repository.getAIResponse(sessionId, text, file?.content)

            _isGeneratingResponse.value = false
        }
    }
}
