package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.Conversation
import com.example.data.model.UiState
import com.example.data.repository.ConversationRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: ConversationRepository,
    private val currentUserId: String
) : ViewModel() {

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId)
            .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing conversations", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load chat history"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun syncProfile(user: FirebaseUser?) {
        if (user == null) return
        viewModelScope.launch {
            repository.syncUserProfile(user)
        }
    }

    fun togglePin(conversation: Conversation) {
        viewModelScope.launch {
            repository.updateConversationMeta(
                conversationId = conversation.id,
                title = conversation.title,
                pinned = !conversation.pinned
            ).onFailure {
                _actionError.value = it.localizedMessage ?: "Failed to update conversation"
            }
        }
    }

    fun renameConversation(conversation: Conversation, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.updateConversationMeta(
                conversationId = conversation.id,
                title = newTitle.trim(),
                pinned = conversation.pinned
            ).onFailure {
                _actionError.value = it.localizedMessage ?: "Failed to rename conversation"
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId).onFailure {
                _actionError.value = it.localizedMessage ?: "Failed to delete conversation"
            }
        }
    }

    fun fetchMessagesForExport(
        conversation: Conversation,
        onLoaded: (List<com.example.data.model.ChatMessage>) -> Unit
    ) {
        viewModelScope.launch {
            repository.getMessagesOnce(conversation.id)
                .onSuccess { messages -> onLoaded(messages) }
                .onFailure {
                    _actionError.value = it.localizedMessage ?: "Failed to load messages for export"
                }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    companion object {
        private const val TAG = "DashboardVM"

        fun provideFactory(
            db: FirebaseFirestore,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    repository = ConversationRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
