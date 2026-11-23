package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import com.example.data.export.ConversationExporter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiMode
import com.example.data.model.UiState
import com.example.ui.components.FormattedMessageBubble
import com.example.ui.components.GlowingCard
import com.example.ui.components.ProviderModelSelector
import com.example.ui.components.TypingIndicatorBubble
import com.example.ui.components.VoiceDictationBanner
import com.example.ui.theme.LocalXproColors
import com.example.ui.viewmodel.WorkspaceViewModel
import com.example.util.BitmapUtils
import com.example.util.VoiceInputHelper
import kotlinx.coroutines.launch
import java.util.Locale

private val SUPPORTED_LANGUAGES = listOf(
    "Auto-detect", "English", "Spanish", "French", "German",
    "Japanese", "Chinese", "Arabic", "Hindi", "Korean", "Portuguese", "Italian"
)

private val WRITING_TONES = listOf(
    "Professional", "Persuasive", "Academic", "Casual", "Executive Summary", "Grammar Polish"
)

private val STUDY_FORMATS = listOf(
    "Clear Explanation", "Flashcards", "Practice Quiz", "Mnemonics", "Key Takeaways"
)

private val CODING_PRESETS = listOf(
    "Kotlin Compose" to "Write clean Jetpack Compose Kotlin code for: ",
    "Python" to "Write clean, well-typed Python code for: ",
    "TypeScript" to "Write modern TypeScript code for: ",
    "Debug" to "Find the bug and fix this code:\n",
    "Optimize" to "Refactor and optimize this code:\n"
)

@Composable
fun AssistantWorkspaceScreen(
    viewModel: WorkspaceViewModel,
    autoSpeakVoice: Boolean
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val xproColors = LocalXproColors.current

    var inputText by remember { mutableStateOf("") }
    var baseTextBeforeDictation by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceRmsLevel by remember { mutableFloatStateOf(0.25f) }
    var isSpeaking by remember { mutableStateOf(false) }

    val latestState by rememberUpdatedState(state)
    val latestBaseText by rememberUpdatedState(baseTextBeforeDictation)

    // Text-to-Speech setup
    var ttsReady by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val tts = remember(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.getDefault()
                instance?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { isSpeaking = true }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post { isSpeaking = false }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { isSpeaking = false }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        mainHandler.post { isSpeaking = false }
                    }
                })
                ttsReady = true
            }
        }
        instance
    }

    val speakText: (String) -> Unit = { text ->
        if (ttsReady) {
            val cleanForSpeech = text
                .replace(Regex("```[\\s\\S]*?```"), "Code block omitted.")
                .replace("*", "")
                .replace("#", "")
                .take(3500)
            isSpeaking = true
            tts.speak(cleanForSpeech, TextToSpeech.QUEUE_FLUSH, null, "xpro_tts_${System.currentTimeMillis()}")
        }
    }

    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.voiceReplyEvents.collect { reply ->
            if (autoSpeakVoice) {
                speakText(reply)
            }
        }
    }

    val speechLanguageTag = remember(state.mode, state.sourceLang) {
        if (state.mode == AiMode.TRANSLATOR) {
            VoiceInputHelper.languageTagForSpeech(state.sourceLang)
        } else {
            Locale.getDefault().toLanguageTag()
        }
    }

    // Fallback system speech activity when inline SpeechRecognizer service is unavailable
    val speechActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val merged = VoiceInputHelper.mergeVoiceTranscript(latestBaseText, spoken)
                if (latestState.mode == AiMode.VOICE) {
                    viewModel.sendMessage(merged, speakWhenDone = true)
                    inputText = ""
                } else {
                    inputText = merged
                }
            }
        }
    }

    // Inline SpeechRecognizer setup
    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                voiceRmsLevel = 0.25f
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                voiceRmsLevel = VoiceInputHelper.normalizeRmsDb(rmsdB)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                voiceRmsLevel = 0.25f
            }
            override fun onError(error: Int) {
                isListening = false
                voiceRmsLevel = 0.25f
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    val readableError = VoiceInputHelper.readableSpeechError(error)
                    scope.launch {
                        snackbarHostState.showSnackbar(readableError)
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                voiceRmsLevel = 0.25f
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    val merged = VoiceInputHelper.mergeVoiceTranscript(latestBaseText, spoken)
                    if (latestState.mode == AiMode.VOICE) {
                        viewModel.sendMessage(merged, speakWhenDone = true)
                        inputText = ""
                    } else {
                        inputText = merged
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    inputText = VoiceInputHelper.mergeVoiceTranscript(latestBaseText, partial)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val startVoiceCapture: () -> Unit = {
        if (isSpeaking) {
            tts.stop()
            isSpeaking = false
        }
        baseTextBeforeDictation = inputText
        val intent = VoiceInputHelper.buildRecognizeSpeechIntent(
            languageTag = speechLanguageTag,
            promptHint = "Speak your query to XPRO AI"
        )
        if (speechRecognizer != null) {
            isListening = true
            speechRecognizer.startListening(intent)
        } else {
            try {
                speechActivityLauncher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                scope.launch {
                    snackbarHostState.showSnackbar("Speech recognition service unavailable on this device.")
                }
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required for Voice-to-Text input.")
            }
        }
    }

    val toggleVoiceInput: () -> Unit = {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                startVoiceCapture()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                BitmapUtils.decodeSoftwareBitmap(context, uri)
            }.onSuccess { bmp ->
                viewModel.setAttachment(bmp)
            }.onFailure { err ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        err.localizedMessage ?: "Could not load selected image."
                    )
                }
            }
        }
    }

    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()

    var pendingExportText by remember { mutableStateOf<String?>(null) }
    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingExportText
        pendingExportText = null
        if (uri != null && content != null) {
            val result = ConversationExporter.writeTextToUri(context, uri, content)
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (result.isSuccess) "Conversation exported as text file"
                    else result.exceptionOrNull()?.localizedMessage ?: "Export failed"
                )
            }
        }
    }

    LaunchedEffect(messages.size, state.isGenerating) {
        val totalItems = messages.size + if (state.streamingText.isNotEmpty() || state.isGenerating) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    LaunchedEffect(state.streamingText.length) {
        if (state.streamingText.isNotEmpty()) {
            val totalItems = messages.size + 1
            listState.scrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
            ) {
                // Header: Mode Switcher + New Thread + Provider/Model Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.mode.displayName,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = state.mode.subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSpeaking) {
                                TextButton(
                                    onClick = {
                                        tts.stop()
                                        isSpeaking = false
                                    },
                                    modifier = Modifier.testTag("stop_tts_button")
                                ) {
                                    Icon(Icons.Filled.Stop, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Stop Audio")
                                }
                            }
                            if (messages.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        val title = messages.firstOrNull { it.isFromUser }?.content
                                            ?.replace('\n', ' ')
                                            ?.take(48)
                                            ?: "${state.mode.displayName} Session"
                                        pendingExportText = ConversationExporter.formatAsPlainText(
                                            title = title,
                                            mode = state.mode,
                                            provider = state.provider,
                                            messages = messages
                                        )
                                        exportTxtLauncher.launch(
                                            ConversationExporter.buildSuggestedFilename(title, state.mode)
                                        )
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("export_chat_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "Export conversation as text file",
                                        tint = xproColors.neonGlow
                                    )
                                }
                            }
                            Surface(
                                onClick = { viewModel.startNewSession(state.mode) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("new_session_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                                    Text("New", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    // Mode Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiMode.entries.forEach { modeItem ->
                            FilterChip(
                                selected = state.mode == modeItem,
                                onClick = { viewModel.setMode(modeItem) },
                                label = { Text(modeItem.displayName) },
                                modifier = Modifier.testTag("mode_chip_${modeItem.name.lowercase()}")
                            )
                        }
                    }

                    // Provider & Model Bar
                    ProviderModelSelector(
                        provider = state.provider,
                        geminiModel = state.geminiModel,
                        openAiModel = state.openAiModel,
                        onProviderChange = viewModel::setProvider,
                        onGeminiModelChange = viewModel::setGeminiModel,
                        onOpenAiModelChange = viewModel::setOpenAiModel
                    )

                    // Mode-specific interactive controls
                    when (state.mode) {
                        AiMode.TRANSLATOR -> TranslatorControls(
                            sourceLang = state.sourceLang,
                            targetLang = state.targetLang,
                            onSelectSource = { viewModel.setTranslatorLanguages(it, state.targetLang) },
                            onSelectTarget = { viewModel.setTranslatorLanguages(state.sourceLang, it) },
                            onSwap = viewModel::swapTranslatorLanguages
                        )

                        AiMode.WRITING -> PresetChipBar(
                            options = WRITING_TONES,
                            selected = state.writingTone,
                            onSelect = viewModel::setWritingTone
                        )

                        AiMode.STUDY -> PresetChipBar(
                            options = STUDY_FORMATS,
                            selected = state.studyFormat,
                            onSelect = viewModel::setStudyFormat
                        )

                        AiMode.CODING -> Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CODING_PRESETS.forEach { (label, prefix) ->
                                Surface(
                                    onClick = { inputText = prefix },
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, xproColors.cardBorder),
                                    modifier = Modifier.testTag("coding_preset_${label.lowercase().replace(" ", "_")}")
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        AiMode.VOICE -> GlowingCard(
                            accentBorder = isListening,
                            onClick = toggleVoiceInput,
                            modifier = Modifier.testTag("voice_assistant_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = if (isListening) xproColors.neonGlow else xproColors.violetGlow
                                    )
                                    Column {
                                        Text(
                                            text = if (isListening) "Listening to your voice... (Tap to stop)" else "Voice Assistant Ready (Tap to speak)",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Speak naturally — replies are spoken aloud automatically",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        else -> Unit
                    }
                }

                // Message Stream
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty() && !state.isGenerating && state.streamingText.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = xproColors.neonGlow.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, xproColors.neonGlow.copy(alpha = 0.45f)),
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (state.mode == AiMode.VOICE) Icons.Filled.Mic else Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null,
                                            tint = xproColors.neonGlow,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${state.mode.displayName} Ready",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = state.mode.placeholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                FormattedMessageBubble(
                                    content = msg.content,
                                    isFromUser = msg.isFromUser,
                                    providerLabel = msg.aiProvider.displayName,
                                    onSpeakClick = if (!msg.isFromUser) {
                                        { speakText(msg.content) }
                                    } else null,
                                    onCopied = {
                                        scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                                    }
                                )
                            }

                            if (state.streamingText.isNotEmpty()) {
                                item(key = "streaming_bubble") {
                                    FormattedMessageBubble(
                                        content = state.streamingText,
                                        isFromUser = false,
                                        providerLabel = state.provider.displayName,
                                        isStreaming = true
                                    )
                                }
                            } else if (state.isGenerating) {
                                item(key = "typing_indicator") {
                                    TypingIndicatorBubble(
                                        providerLabel = state.provider.displayName
                                    )
                                }
                            }
                        }
                    }
                }

                // Error Banner
                AnimatedVisibility(visible = state.errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .testTag("workspace_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = state.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.canRetryLastPrompt && !state.isGenerating) {
                                TextButton(
                                    onClick = viewModel::retryLastFailedMessage,
                                    modifier = Modifier.testTag("retry_message_button")
                                ) {
                                    Text(
                                        text = "Retry",
                                        color = xproColors.neonGlow,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            IconButton(
                                onClick = viewModel::clearError,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss error")
                            }
                        }
                    }
                }

                // Image Attachment Preview
                if (state.attachmentBitmap != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            bitmap = state.attachmentBitmap!!.asImageBitmap(),
                            contentDescription = "Attached image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Text(
                            text = "Image attached for vision analysis",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.setAttachment(null) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove image")
                        }
                    }
                }

                // Live Voice-to-Text Dictation Banner
                AnimatedVisibility(visible = isListening) {
                    VoiceDictationBanner(
                        rmsLevel = voiceRmsLevel,
                        languageLabel = if (state.mode == AiMode.TRANSLATOR) state.sourceLang else Locale.getDefault().displayLanguage.ifBlank { "Auto" },
                        onStop = {
                            speechRecognizer?.stopListening()
                            isListening = false
                        },
                        onCancel = {
                            speechRecognizer?.cancel()
                            isListening = false
                            inputText = baseTextBeforeDictation
                        }
                    )
                }

                // Bottom Composer Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, xproColors.cardBorder),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("attach_image_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = "Attach image",
                                tint = xproColors.neonGlow
                            )
                        }

                        IconButton(
                            onClick = toggleVoiceInput,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(
                                    if (isListening) xproColors.neonGlow.copy(alpha = 0.24f)
                                    else Color.Transparent
                                )
                                .testTag("voice_mic_button")
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (isListening) "Stop listening" else "Voice-to-text input",
                                tint = if (isListening) xproColors.neonGlow else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text(state.mode.placeholder, maxLines = 1) },
                            maxLines = 4,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("workspace_prompt_input")
                        )

                        Surface(
                            onClick = {
                                if (state.isGenerating) {
                                    viewModel.cancelGeneration()
                                } else if (inputText.isNotBlank() || state.attachmentBitmap != null) {
                                    val msg = inputText
                                    inputText = ""
                                    viewModel.sendMessage(msg, speakWhenDone = state.mode == AiMode.VOICE)
                                }
                            },
                            shape = CircleShape,
                            color = if (state.isGenerating) xproColors.violetGlow.copy(alpha = 0.5f) else xproColors.neonGlow,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_message_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (state.isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(34.dp),
                                        color = xproColors.neonGlow,
                                        strokeWidth = 2.dp
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Stop,
                                        contentDescription = "Stop generating",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send prompt",
                                        tint = Color(0xFF06040C),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslatorControls(
    sourceLang: String,
    targetLang: String,
    onSelectSource: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onSwap: () -> Unit
) {
    var sourceOpen by remember { mutableStateOf(false) }
    var targetOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FilterChip(
                selected = true,
                onClick = { sourceOpen = true },
                label = { Text("From: $sourceLang") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("translator_source_chip")
            )
            DropdownMenu(expanded = sourceOpen, onDismissRequest = { sourceOpen = false }) {
                SUPPORTED_LANGUAGES.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang) },
                        onClick = {
                            onSelectSource(lang)
                            sourceOpen = false
                        }
                    )
                }
            }
        }

        IconButton(
            onClick = onSwap,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("translator_swap_button")
        ) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap languages")
        }

        Box(modifier = Modifier.weight(1f)) {
            FilterChip(
                selected = true,
                onClick = { targetOpen = true },
                label = { Text("To: $targetLang") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("translator_target_chip")
            )
            DropdownMenu(expanded = targetOpen, onDismissRequest = { targetOpen = false }) {
                SUPPORTED_LANGUAGES.filter { it != "Auto-detect" }.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang) },
                        onClick = {
                            onSelectTarget(lang)
                            targetOpen = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChipBar(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option, fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal) },
                modifier = Modifier.testTag("preset_chip_${option.lowercase().replace(" ", "_")}")
            )
        }
    }
}
