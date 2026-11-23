package com.example

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.AiMode
import com.example.data.model.AiProvider
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiModelOption
import com.example.data.model.OpenAiModelOption
import com.example.data.model.ThemePreference
import com.example.data.model.UiState
import com.example.data.repository.AiChatBackend
import com.example.data.repository.AiRepository
import com.example.data.repository.ChatStorageBackend
import com.example.ui.screens.AssistantWorkspaceScreen
import com.example.ui.theme.XproAiTheme
import com.example.ui.viewmodel.WorkspaceViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ChatReplyReliabilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeChatStorage(
        private val hangWrites: Boolean = false,
        private val failWrites: Boolean = false
    ) : ChatStorageBackend {
        private var counter = 0
        val streams = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()
        val storedMessages = mutableListOf<ChatMessage>()

        override fun newConversationId(): String = "conv_${++counter}"

        override fun newMessageId(conversationId: String): String = "msg_${++counter}"

        override fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> {
            return streams.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        }

        override suspend fun createConversationWithId(
            conversationId: String,
            title: String,
            mode: AiMode,
            provider: AiProvider,
            initialPreview: String
        ): Result<String> {
            if (hangWrites) CompletableDeferred<Unit>().await()
            if (failWrites) return Result.failure(IllegalStateException("Firestore unavailable"))
            streams.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
            return Result.success(conversationId)
        }

        override suspend fun appendMessage(
            conversationId: String,
            role: String,
            content: String,
            provider: AiProvider,
            mode: AiMode,
            newTitleIfFirst: String?,
            currentMessageCount: Long,
            messageId: String?
        ): Result<Unit> {
            if (hangWrites) CompletableDeferred<Unit>().await()
            if (failWrites) return Result.failure(IllegalStateException("Firestore write failed"))
            val id = messageId ?: newMessageId(conversationId)
            val msg = ChatMessage(
                id = id,
                userId = "u1",
                role = role,
                content = content,
                provider = provider.name,
                mode = mode.name
            )
            storedMessages.add(msg)
            val flow = streams.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
            flow.value = flow.value + msg
            return Result.success(Unit)
        }
    }

    private class FakeAiBackend(
        private val handler: suspend (String, List<ChatMessage>) -> List<String>
    ) : AiChatBackend {
        var callCount = 0
        var lastReceivedHistory: List<ChatMessage> = emptyList()

        override fun streamReply(
            prompt: String,
            history: List<ChatMessage>,
            mode: AiMode,
            provider: AiProvider,
            geminiModel: GeminiModelOption,
            openAiModel: OpenAiModelOption,
            attachmentBitmap: Bitmap?,
            sourceLang: String,
            targetLang: String,
            writingTone: String,
            studyFormat: String
        ): Flow<String> = flow {
            callCount++
            lastReceivedHistory = history
            val chunks = handler(prompt, history)
            for (c in chunks) {
                emit(c)
            }
        }
    }

    @Test
    fun `sanitizeChatHistory drops leading model and trailing user and collapses same-role turns`() {
        val raw = listOf(
            ChatMessage(id = "0", role = "ASSISTANT", content = "Orphan welcome"),
            ChatMessage(id = "1", role = "USER", content = "First question"),
            ChatMessage(id = "2", role = "USER", content = "Follow up before reply"),
            ChatMessage(id = "3", role = "ASSISTANT", content = "   "),
            ChatMessage(id = "4", role = "ASSISTANT", content = "Answer one"),
            ChatMessage(id = "5", role = "USER", content = "Failed earlier turn")
        )

        val sanitized = AiRepository.sanitizeChatHistory(raw, maxTurns = 14)

        assertEquals(2, sanitized.size)
        assertTrue(sanitized[0].isFromUser)
        assertEquals("First question\n\nFollow up before reply", sanitized[0].content)
        assertFalse(sanitized[1].isFromUser)
        assertEquals("Answer one", sanitized[1].content)
    }

    @Test
    fun `sanitizeChatHistory maxTurns slice preserves alternating user-start and model-end`() {
        val raw = listOf(
            ChatMessage(id = "1", role = "USER", content = "U1"),
            ChatMessage(id = "2", role = "ASSISTANT", content = "A1"),
            ChatMessage(id = "3", role = "USER", content = "U2"),
            ChatMessage(id = "4", role = "ASSISTANT", content = "A2"),
            ChatMessage(id = "5", role = "USER", content = "U3"),
            ChatMessage(id = "6", role = "ASSISTANT", content = "A3")
        )

        val sanitized = AiRepository.sanitizeChatHistory(raw, maxTurns = 3)
        assertEquals(2, sanitized.size)
        assertEquals("U3", sanitized[0].content)
        assertEquals("A3", sanitized[1].content)
    }

    @Test
    fun `sendMessage replies immediately even when Firestore writes hang or fail`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hangingStorage = FakeChatStorage(hangWrites = true)
            val ai = FakeAiBackend { prompt, _ ->
                listOf("Hello", "Hello! Reply to: $prompt")
            }
            val vm = WorkspaceViewModel(hangingStorage, ai, "u1")

            val collectorJob = backgroundScope.launch { vm.messagesState.collect {} }

            vm.sendMessage("Explain black holes")

            val messages = (vm.messagesState.value as UiState.Success).data
            assertEquals(2, messages.size)
            assertTrue(messages[0].isFromUser)
            assertEquals("Explain black holes", messages[0].content)
            assertFalse(messages[1].isFromUser)
            assertEquals("Hello! Reply to: Explain black holes", messages[1].content)
            assertFalse(vm.state.value.isGenerating)
            assertNull(vm.state.value.errorMessage)
            collectorJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed reply surfaces retry action and retry succeeds without duplicating user message`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val storage = FakeChatStorage(failWrites = true)
            var shouldFail = true
            val ai = FakeAiBackend { prompt, _ ->
                if (shouldFail) {
                    throw IllegalStateException("Transient network error")
                }
                listOf("Recovered reply for $prompt")
            }
            val vm = WorkspaceViewModel(storage, ai, "u1")
            val collectorJob = backgroundScope.launch { vm.messagesState.collect {} }

            vm.sendMessage("Help me debug")

            assertEquals("Transient network error", vm.state.value.errorMessage)
            assertTrue(vm.state.value.canRetryLastPrompt)
            assertFalse(vm.state.value.isGenerating)

            val afterFail = (vm.messagesState.value as UiState.Success).data
            assertEquals(1, afterFail.size)
            assertEquals("Help me debug", afterFail[0].content)

            shouldFail = false
            vm.retryLastFailedMessage()

            val afterRetry = (vm.messagesState.value as UiState.Success).data
            assertEquals(2, afterRetry.size)
            assertEquals("Help me debug", afterRetry[0].content)
            assertEquals("Recovered reply for Help me debug", afterRetry[1].content)
            assertNull(vm.state.value.errorMessage)
            assertFalse(vm.state.value.canRetryLastPrompt)
            collectorJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `cancelGeneration stops stream and keeps partial reply visible`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val storage = FakeChatStorage()
            val ai = object : AiChatBackend {
                override fun streamReply(
                    prompt: String,
                    history: List<ChatMessage>,
                    mode: AiMode,
                    provider: AiProvider,
                    geminiModel: GeminiModelOption,
                    openAiModel: OpenAiModelOption,
                    attachmentBitmap: Bitmap?,
                    sourceLang: String,
                    targetLang: String,
                    writingTone: String,
                    studyFormat: String
                ): Flow<String> = flow {
                    emit("Partial answer so far")
                    delay(10_000L)
                }
            }
            val vm = WorkspaceViewModel(storage, ai, "u1")
            val collectorJob = backgroundScope.launch { vm.messagesState.collect {} }

            vm.sendMessage("Long essay")
            assertTrue(vm.state.value.isGenerating)
            assertEquals("Partial answer so far", vm.state.value.streamingText)

            vm.cancelGeneration()

            assertFalse(vm.state.value.isGenerating)
            assertEquals("", vm.state.value.streamingText)
            val msgs = (vm.messagesState.value as UiState.Success).data
            assertEquals(2, msgs.size)
            assertEquals("Partial answer so far", msgs[1].content)
            collectorJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `workspace screen error banner shows retry button that triggers reply recovery`() {
        val storage = FakeChatStorage(failWrites = true)
        var failFirst = true
        val ai = FakeAiBackend { prompt, _ ->
            if (failFirst) throw IllegalStateException("Temporary connection issue")
            listOf("Assistant reply: $prompt")
        }
        val vm = WorkspaceViewModel(storage, ai, "u1")

        composeRule.setContent {
            XproAiTheme(themePreference = ThemePreference.DARK) {
                AssistantWorkspaceScreen(
                    viewModel = vm,
                    autoSpeakVoice = false
                )
            }
        }
        composeRule.waitForIdle()

        vm.sendMessage("Test chat prompt")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("workspace_error_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("retry_message_button").assertIsDisplayed()

        failFirst = false
        composeRule.onNodeWithTag("retry_message_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("workspace_error_banner").assertDoesNotExist()
        composeRule.onNodeWithText("Assistant reply: Test chat prompt").assertIsDisplayed()
    }
}
