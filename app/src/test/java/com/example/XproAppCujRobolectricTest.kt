package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AiMode
import com.example.data.model.AiProvider
import com.example.data.model.Conversation
import com.example.data.model.GeminiModelOption
import com.example.data.model.ThemePreference
import com.example.data.model.UiState
import com.example.data.repository.AiRepository
import com.example.ui.components.FormattedMessageBubble
import com.example.ui.components.TypingIndicatorBubble
import com.example.ui.components.VoiceDictationBanner
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.theme.XproAiTheme
import com.example.util.BitmapUtils
import com.example.util.VoiceInputHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class XproAppCujRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `typing indicator bubble renders provider and animated typing label`() {
        composeRule.setContent {
            XproAiTheme(themePreference = ThemePreference.DARK) {
                TypingIndicatorBubble(providerLabel = AiProvider.GEMINI.displayName)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("typing_indicator_bubble").assertIsDisplayed()
        composeRule.onNodeWithText("XPRO AI • Google Gemini").assertIsDisplayed()
        composeRule.onNodeWithText("typing...").assertIsDisplayed()
    }

    @Test
    fun `streaming message bubble shows streaming badge and live unclosed code block`() {
        val partialReply = "Here is a snippet:\n```kotlin\nfun greet() = \"Hi\""
        composeRule.setContent {
            XproAiTheme(themePreference = ThemePreference.DARK) {
                FormattedMessageBubble(
                    content = partialReply,
                    isFromUser = false,
                    providerLabel = "Google Gemini",
                    isStreaming = true
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1000L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("streaming_indicator_badge").assertIsDisplayed()
        composeRule.onNodeWithText("KOTLIN").assertIsDisplayed()
        composeRule.onNodeWithTag("copy_response_button").assertDoesNotExist()
    }

    @Test
    fun `completed assistant bubble shows copy share and speak actions`() {
        composeRule.setContent {
            XproAiTheme(themePreference = ThemePreference.LIGHT) {
                FormattedMessageBubble(
                    content = "Quantum entanglement links particle states instantaneously.",
                    isFromUser = false,
                    providerLabel = "Google Gemini",
                    isStreaming = false,
                    onSpeakClick = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("speak_response_button").assertIsDisplayed()
        composeRule.onNodeWithTag("copy_response_button").assertIsDisplayed()
        composeRule.onNodeWithTag("share_response_button").assertIsDisplayed()
    }

    @Test
    fun `dashboard displays all ai modules and launches selected mode`() {
        var launchedMode: AiMode? = null
        var openedStudio = false

        composeRule.setContent {
            XproAiTheme(themePreference = ThemePreference.DARK) {
                DashboardScreen(
                    userName = "Alex Mercer",
                    conversationsState = UiState.Success(emptyList()),
                    activeProvider = AiProvider.GEMINI,
                    onLaunchMode = { launchedMode = it },
                    onOpenImageStudio = { openedStudio = true },
                    onOpenConversation = {},
                    onViewAllHistory = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Welcome back, Alex").assertIsDisplayed()
        composeRule.onNodeWithTag("tile_coding").performClick()
        assertEquals(AiMode.CODING, launchedMode)

        composeRule.onNodeWithTag("tile_image").performClick()
        assertTrue(openedStudio)
    }

    @Test
    fun `history screen search and mode filter work accurately`() {
        val conv1 = Conversation(
            id = "c1",
            userId = "u1",
            title = "Kotlin Coroutines Guide",
            mode = AiMode.CODING.name,
            provider = AiProvider.GEMINI.name,
            lastMessagePreview = "Use StateFlow and viewModelScope",
            messageCount = 4
        )
        val conv2 = Conversation(
            id = "c2",
            userId = "u1",
            title = "French Travel Phrases",
            mode = AiMode.TRANSLATOR.name,
            provider = AiProvider.GEMINI.name,
            lastMessagePreview = "Bonjour, je voudrais...",
            messageCount = 2
        )

        composeRule.setContent {
            XproAiTheme(themePreference = ThemePreference.DARK) {
                HistoryScreen(
                    conversationsState = UiState.Success(listOf(conv1, conv2)),
                    onOpenConversation = {},
                    onTogglePin = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onFetchMessagesForExport = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history_item_c1").assertIsDisplayed()
        composeRule.onNodeWithTag("history_item_c2").assertIsDisplayed()

        composeRule.onNodeWithTag("history_search_input").performTextInput("Coroutines")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history_item_c1").assertIsDisplayed()
        composeRule.onNodeWithTag("history_item_c2").assertDoesNotExist()
    }

    @Test
    fun `ai repository provides distinct instructions and valid gemini model ids`() {
        val repo = AiRepository()
        val instructions = AiMode.entries.map { repo.systemInstructionFor(it) }.toSet()
        assertEquals(AiMode.entries.size, instructions.size)
        assertEquals("gemini-3.8-flash", GeminiModelOption.FLASH_3_8.modelId)
        assertEquals("gemini-3.5-flash-lite", GeminiModelOption.FLASH_LITE_3_5.modelId)
        assertEquals("gemini-4-argon", GeminiModelOption.ARGON_4.modelId)
    }

    @Test
    fun `openai request fails gracefully with clear configuration message when key absent`() = runTest {
        val repo = AiRepository()
        var failure: Throwable? = null
        try {
            repo.streamReply(
                prompt = "Hello",
                history = emptyList(),
                mode = AiMode.CHAT,
                provider = AiProvider.OPENAI,
                geminiModel = GeminiModelOption.FLASH_3_8,
                openAiModel = com.example.data.model.OpenAiModelOption.GPT_4O_MINI
            ).first()
        } catch (t: Throwable) {
            failure = t
        }
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("OPENAI_API_KEY") == true)
    }

    @Test
    fun `decodeSoftwareBitmap scales large image and avoids hardware bitmap config`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = File(context.cacheDir, "test_large_img.png")
        val largeBitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        FileOutputStream(tempFile).use { out ->
            largeBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        largeBitmap.recycle()

        val decoded = BitmapUtils.decodeSoftwareBitmap(context, Uri.fromFile(tempFile), maxDimension = 1024)
        assertTrue(decoded.width <= 1024)
        assertTrue(decoded.height <= 1024)
        assertNotEquals(Bitmap.Config.HARDWARE, decoded.config)
        tempFile.delete()
    }

    @Test
    fun `voice dictation banner displays status and fires stop or cancel actions`() {
        var stopped = false
        var cancelled = false
        composeRule.setContent {
            XproAiTheme(themePreference = ThemePreference.DARK) {
                VoiceDictationBanner(
                    rmsLevel = 0.7f,
                    languageLabel = "English",
                    onStop = { stopped = true },
                    onCancel = { cancelled = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("voice_dictation_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Listening... Speak your query").assertIsDisplayed()
        composeRule.onNodeWithText("Voice-to-text (English)").assertIsDisplayed()

        composeRule.onNodeWithTag("stop_voice_button").performClick()
        composeRule.onNodeWithTag("cancel_voice_button").performClick()
        assertTrue(stopped)
        assertTrue(cancelled)
    }

    @Test
    fun `voice input helper merges transcripts normalizes rms and maps translator languages`() {
        assertEquals("Explain quantum mechanics", VoiceInputHelper.mergeVoiceTranscript("Explain", "quantum mechanics"))
        assertEquals("Hello world", VoiceInputHelper.mergeVoiceTranscript("", "  Hello world  "))
        assertEquals("es-ES", VoiceInputHelper.languageTagForSpeech("Spanish"))
        assertEquals("ja-JP", VoiceInputHelper.languageTagForSpeech("Japanese"))
        assertTrue(VoiceInputHelper.normalizeRmsDb(-5f) >= 0.15f)
        assertTrue(VoiceInputHelper.normalizeRmsDb(20f) <= 1f)
    }
}

