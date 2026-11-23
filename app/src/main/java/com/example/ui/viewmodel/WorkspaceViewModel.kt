package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AiMode
import com.example.data.model.AiProvider
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiModelOption
import com.example.data.model.OpenAiModelOption
import com.example.data.model.UiState
import com.example.data.repository.AiChatBackend
import com.example.data.repository.AiRepository
import com.example.data.repository.ChatStorageBackend
import com.example.data.repository.ConversationRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class WorkspaceUiState(
    val activeConversationId: String? = null,
    val mode: AiMode = AiMode.CHAT,
    val provider: AiProvider = AiProvider.GEMINI,
    val geminiModel: GeminiModelOption = GeminiModelOption.FLASH_3_8,
    val openAiModel: OpenAiModelOption = OpenAiModelOption.GPT_4O_MINI,
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val errorMessage: String? = null,
    val canRetryLastPrompt: Boolean = false,
    val attachmentBitmap: Bitmap? = null,
    val sourceLang: String = "Auto-detect",
    val targetLang: String = "Spanish",
    val writingTone: String = "Professional",
    val studyFormat: String = "Clear Explanation"
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModel(
    private val conversationRepository: ChatStorageBackend,
    private val aiRepository: AiChatBackend,
    private val currentUserId: String
) : ViewModel() {

    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    private val _activeConvIdFlow = MutableStateFlow<String?>(null)
    private val _localMessages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())

    private val _voiceReplyEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val voiceReplyEvents: SharedFlow<String> = _voiceReplyEvents.asSharedFlow()

    private val persistMutex = Mutex()
    private var generationJob: Job? = null
    private var lastFailedPrompt: String? = null
    private var lastFailedSpeakWhenDone: Boolean = false
    private var lastFailedBitmap: Bitmap? = null

    val messagesState: StateFlow<UiState<List<ChatMessage>>> =
        _activeConvIdFlow.flatMapLatest { convId ->
            if (convId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                val remoteFlow = conversationRepository.observeMessages(currentUserId, convId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        Log.w(TAG, "Error observing messages", root)
                        emit(UiState.Error(root.localizedMessage ?: "Failed to load messages"))
                    }

                combine(remoteFlow, _localMessages) { remoteState, localMap ->
                    val localForConv = localMap[convId].orEmpty()
                    when (remoteState) {
                        is UiState.Success -> {
                            UiState.Success(mergeMessages(remoteState.data, localForConv))
                        }

                        is UiState.Error -> {
                            if (localForConv.isNotEmpty()) {
                                UiState.Success(localForConv)
                            } else {
                                remoteState
                            }
                        }

                        is UiState.Loading -> {
                            if (localForConv.isNotEmpty()) {
                                UiState.Success(localForConv)
                            } else {
                                remoteState
                            }
                        }
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Success(emptyList())
        )

    private fun mergeMessages(
        remote: List<ChatMessage>,
        local: List<ChatMessage>
    ): List<ChatMessage> {
        if (local.isEmpty()) return remote
        if (remote.isEmpty()) return local
        val remoteIds = remote.map { it.id }.filter { it.isNotBlank() }.toSet()
        val pendingLocal = local.filter { it.id !in remoteIds }
        return remote + pendingLocal
    }

    private fun currentMessagesFor(convId: String?): List<ChatMessage> {
        if (convId.isNullOrBlank()) return emptyList()
        val remote = (messagesState.value as? UiState.Success)?.data.orEmpty()
        val local = _localMessages.value[convId].orEmpty()
        return mergeMessages(remote, local)
    }

    private fun appendLocalMessage(convId: String, message: ChatMessage) {
        _localMessages.update { map ->
            val currentList = map[convId].orEmpty()
            if (currentList.any { it.id == message.id }) {
                map
            } else {
                map + (convId to (currentList + message))
            }
        }
    }

    private fun persistInBackground(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            persistMutex.withLock {
                try {
                    withTimeoutOrNull(12_000L) {
                        block()
                    } ?: Log.w(TAG, "Firestore sync timed out for $label (queued locally)")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore sync failed for $label", e)
                }
            }
        }
    }

    fun startNewSession(mode: AiMode) {
        generationJob?.cancel()
        generationJob = null
        lastFailedPrompt = null
        lastFailedBitmap = null
        _activeConvIdFlow.value = null
        _state.update {
            it.copy(
                activeConversationId = null,
                mode = mode,
                isGenerating = false,
                streamingText = "",
                errorMessage = null,
                canRetryLastPrompt = false,
                attachmentBitmap = null
            )
        }
    }

    fun openConversation(conversationId: String, mode: AiMode, provider: AiProvider) {
        generationJob?.cancel()
        generationJob = null
        lastFailedPrompt = null
        lastFailedBitmap = null
        _activeConvIdFlow.value = conversationId
        _state.update {
            it.copy(
                activeConversationId = conversationId,
                mode = mode,
                provider = provider,
                isGenerating = false,
                streamingText = "",
                errorMessage = null,
                canRetryLastPrompt = false,
                attachmentBitmap = null
            )
        }
    }

    fun handleDeletedConversation(deletedId: String) {
        _localMessages.update { it - deletedId }
        if (_state.value.activeConversationId == deletedId) {
            startNewSession(_state.value.mode)
        }
    }

    fun applyDefaultPreferences(
        provider: AiProvider,
        geminiModel: GeminiModelOption,
        openAiModel: OpenAiModelOption
    ) {
        _state.update { current ->
            current.copy(
                provider = if (current.activeConversationId == null) provider else current.provider,
                geminiModel = geminiModel,
                openAiModel = openAiModel
            )
        }
    }

    fun setMode(mode: AiMode) {
        _state.update { it.copy(mode = mode, errorMessage = null, canRetryLastPrompt = false) }
    }

    fun setProvider(provider: AiProvider) {
        _state.update { it.copy(provider = provider, errorMessage = null, canRetryLastPrompt = false) }
    }

    fun setGeminiModel(model: GeminiModelOption) {
        _state.update { it.copy(geminiModel = model, errorMessage = null, canRetryLastPrompt = false) }
    }

    fun setOpenAiModel(model: OpenAiModelOption) {
        _state.update { it.copy(openAiModel = model, errorMessage = null, canRetryLastPrompt = false) }
    }

    fun setTranslatorLanguages(source: String, target: String) {
        _state.update { it.copy(sourceLang = source, targetLang = target) }
    }

    fun swapTranslatorLanguages() {
        _state.update { current ->
            val nextSource = if (current.targetLang == "Auto-detect") "English" else current.targetLang
            val nextTarget = if (current.sourceLang == "Auto-detect") "English" else current.sourceLang
            current.copy(sourceLang = nextSource, targetLang = nextTarget)
        }
    }

    fun setWritingTone(tone: String) {
        _state.update { it.copy(writingTone = tone) }
    }

    fun setStudyFormat(format: String) {
        _state.update { it.copy(studyFormat = format) }
    }

    fun setAttachment(bitmap: Bitmap?) {
        _state.update { it.copy(attachmentBitmap = bitmap) }
    }

    fun clearError() {
        lastFailedPrompt = null
        lastFailedBitmap = null
        _state.update { it.copy(errorMessage = null, canRetryLastPrompt = false) }
    }

    fun cancelGeneration() {
        val current = _state.value
        if (!current.isGenerating) return
        val convId = current.activeConversationId
        val partial = current.streamingText.trim()
        generationJob?.cancel()
        generationJob = null

        if (!convId.isNullOrBlank() && partial.isNotEmpty()) {
            val assistantMsgId = conversationRepository.newMessageId(convId)
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                userId = currentUserId,
                role = "ASSISTANT",
                content = partial,
                provider = current.provider.name,
                mode = current.mode.name,
                createdAt = Timestamp.now()
            )
            appendLocalMessage(convId, assistantMsg)
            persistInBackground("cancelled_partial_reply") {
                conversationRepository.appendMessage(
                    conversationId = convId,
                    role = "ASSISTANT",
                    content = partial,
                    provider = current.provider,
                    mode = current.mode,
                    currentMessageCount = currentMessagesFor(convId).size.toLong(),
                    messageId = assistantMsgId
                )
            }
        }

        _state.update { it.copy(isGenerating = false, streamingText = "") }
    }

    fun retryLastFailedMessage() {
        val prompt = lastFailedPrompt ?: return
        val speak = lastFailedSpeakWhenDone
        val bmp = lastFailedBitmap
        sendMessageInternal(
            rawPrompt = prompt,
            speakWhenDone = speak,
            overrideBitmap = bmp,
            isRetry = true
        )
    }

    fun sendMessage(rawPrompt: String, speakWhenDone: Boolean = false) {
        sendMessageInternal(
            rawPrompt = rawPrompt,
            speakWhenDone = speakWhenDone,
            overrideBitmap = null,
            isRetry = false
        )
    }

    private fun sendMessageInternal(
        rawPrompt: String,
        speakWhenDone: Boolean,
        overrideBitmap: Bitmap?,
        isRetry: Boolean
    ) {
        val current = _state.value
        val prompt = rawPrompt.trim()
        val attachedBitmap = overrideBitmap ?: current.attachmentBitmap
        if (prompt.isEmpty() && attachedBitmap == null) return
        if (current.isGenerating) return

        val effectivePrompt = prompt.ifEmpty { "Analyze this attached image." }
        val isFirstTurn = current.activeConversationId == null
        val convId = current.activeConversationId ?: conversationRepository.newConversationId()

        val existingHistory = currentMessagesFor(convId)
        val userMsgLabel = if (attachedBitmap != null) "[Image Attached] $effectivePrompt" else effectivePrompt
        val alreadyHasTrailingPrompt = isRetry &&
            existingHistory.lastOrNull()?.let { it.isFromUser && it.content == userMsgLabel } == true

        if (isFirstTurn) {
            _activeConvIdFlow.value = convId
        }

        val userMsgId = conversationRepository.newMessageId(convId)
        if (!alreadyHasTrailingPrompt) {
            val localUserMsg = ChatMessage(
                id = userMsgId,
                userId = currentUserId,
                role = "USER",
                content = userMsgLabel,
                provider = current.provider.name,
                mode = current.mode.name,
                createdAt = Timestamp.now()
            )
            appendLocalMessage(convId, localUserMsg)
        }

        lastFailedPrompt = effectivePrompt
        lastFailedSpeakWhenDone = speakWhenDone
        lastFailedBitmap = attachedBitmap

        generationJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    activeConversationId = convId,
                    isGenerating = true,
                    streamingText = "",
                    errorMessage = null,
                    canRetryLastPrompt = false,
                    attachmentBitmap = null
                )
            }

            // Persist conversation + user message asynchronously so Firestore latency or
            // transient offline conditions NEVER block the AI model from replying.
            if (isFirstTurn) {
                val title = effectivePrompt.replace('\n', ' ').take(48)
                persistInBackground("create_conversation") {
                    conversationRepository.createConversationWithId(
                        conversationId = convId,
                        title = title,
                        mode = current.mode,
                        provider = current.provider,
                        initialPreview = effectivePrompt
                    )
                }
            }

            if (!alreadyHasTrailingPrompt) {
                val titleIfFirst = if (isFirstTurn) effectivePrompt.replace('\n', ' ').take(48) else null
                persistInBackground("append_user_message") {
                    conversationRepository.appendMessage(
                        conversationId = convId,
                        role = "USER",
                        content = userMsgLabel,
                        provider = current.provider,
                        mode = current.mode,
                        newTitleIfFirst = titleIfFirst,
                        currentMessageCount = existingHistory.size.toLong(),
                        messageId = userMsgId
                    )
                }
            }

            try {
                var latestReply = ""
                aiRepository.streamReply(
                    prompt = effectivePrompt,
                    history = existingHistory,
                    mode = current.mode,
                    provider = current.provider,
                    geminiModel = current.geminiModel,
                    openAiModel = current.openAiModel,
                    attachmentBitmap = attachedBitmap,
                    sourceLang = current.sourceLang,
                    targetLang = current.targetLang,
                    writingTone = current.writingTone,
                    studyFormat = current.studyFormat
                ).collect { chunkAccumulated ->
                    latestReply = chunkAccumulated
                    _state.update { it.copy(streamingText = chunkAccumulated) }
                }

                if (latestReply.isNotBlank()) {
                    val assistantMsgId = conversationRepository.newMessageId(convId)
                    val assistantMsg = ChatMessage(
                        id = assistantMsgId,
                        userId = currentUserId,
                        role = "ASSISTANT",
                        content = latestReply,
                        provider = current.provider.name,
                        mode = current.mode.name,
                        createdAt = Timestamp.now()
                    )
                    // Insert locally BEFORE clearing streamingText so the reply never disappears.
                    appendLocalMessage(convId, assistantMsg)
                    lastFailedPrompt = null
                    lastFailedBitmap = null

                    persistInBackground("append_assistant_message") {
                        conversationRepository.appendMessage(
                            conversationId = convId,
                            role = "ASSISTANT",
                            content = latestReply,
                            provider = current.provider,
                            mode = current.mode,
                            currentMessageCount = (existingHistory.size + 1).toLong(),
                            messageId = assistantMsgId
                        )
                    }

                    if (speakWhenDone || current.mode == AiMode.VOICE) {
                        _voiceReplyEvents.tryEmit(latestReply)
                    }
                } else {
                    _state.update {
                        it.copy(
                            errorMessage = "Model returned an empty response. Tap Retry to try again.",
                            canRetryLastPrompt = true
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI request timed out", e)
                _state.update {
                    it.copy(
                        errorMessage = "Request timed out after 60 seconds. Check your connection and tap Retry.",
                        canRetryLastPrompt = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI workspace generation failed", e)
                _state.update {
                    it.copy(
                        errorMessage = e.localizedMessage ?: "AI generation failed. Tap Retry to try again.",
                        canRetryLastPrompt = true
                    )
                }
            } finally {
                _state.update { it.copy(isGenerating = false, streamingText = "") }
            }
        }
    }

    companion object {
        private const val TAG = "WorkspaceVM"

        fun provideFactory(
            db: FirebaseFirestore,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WorkspaceViewModel(
                    conversationRepository = ConversationRepository(db),
                    aiRepository = AiRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
