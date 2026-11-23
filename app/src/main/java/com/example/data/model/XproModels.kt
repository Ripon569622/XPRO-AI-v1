package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AiMode(
    val displayName: String,
    val subtitle: String,
    val placeholder: String
) {
    CHAT(
        displayName = "AI Chat",
        subtitle = "Intelligent multi-turn assistant",
        placeholder = "Ask XPRO AI anything..."
    ),
    VOICE(
        displayName = "Voice AI",
        subtitle = "Speak & listen hands-free",
        placeholder = "Tap the orb to speak, or type here..."
    ),
    CODING(
        displayName = "Code Copilot",
        subtitle = "Architecture, debugging & clean code",
        placeholder = "Describe code to write, debug, or optimize..."
    ),
    TRANSLATOR(
        displayName = "Translator",
        subtitle = "Real-time neural translation",
        placeholder = "Enter text to translate..."
    ),
    WRITING(
        displayName = "Writing Pro",
        subtitle = "Essays, emails, copy & polish",
        placeholder = "Topic, draft, or instructions for writing..."
    ),
    MATH(
        displayName = "Math Solver",
        subtitle = "Step-by-step STEM derivations",
        placeholder = "Enter equation, calculus, or attach a problem photo..."
    ),
    STUDY(
        displayName = "Study Lab",
        subtitle = "Flashcards, quizzes & summaries",
        placeholder = "Paste notes or enter a topic to master..."
    );

    companion object {
        fun fromString(raw: String?): AiMode =
            entries.firstOrNull { it.name == raw } ?: CHAT
    }
}

enum class AiProvider(val displayName: String, val badge: String) {
    GEMINI("Google Gemini", "Firebase AI Logic"),
    OPENAI("OpenAI", "GPT-4o & DALL-E 3");

    companion object {
        fun fromString(raw: String?): AiProvider =
            entries.firstOrNull { it.name == raw } ?: GEMINI
    }
}

enum class GeminiModelOption(
    val modelId: String,
    val displayName: String,
    val description: String
) {
    FLASH_3_8("gemini-3.8-flash", "Gemini 3.8 Flash", "Fast, versatile frontier intelligence"),
    FLASH_LITE_3_5("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Ultra-low latency everyday tasks"),
    ARGON_4("gemini-4-argon", "Gemini 4 Argon", "Deep STEM, coding & complex reasoning");

    companion object {
        fun fromModelId(id: String?): GeminiModelOption =
            entries.firstOrNull { it.modelId == id } ?: FLASH_3_8
    }
}

enum class OpenAiModelOption(
    val modelId: String,
    val displayName: String,
    val description: String
) {
    GPT_4O_MINI("gpt-4o-mini", "GPT-4o Mini", "Fast & efficient OpenAI model"),
    GPT_4O("gpt-4o", "GPT-4o", "Flagship OpenAI reasoning model");

    companion object {
        fun fromModelId(id: String?): OpenAiModelOption =
            entries.firstOrNull { it.modelId == id } ?: GPT_4O_MINI
    }
}

enum class ThemePreference(val label: String) {
    DARK("Dark Void"),
    LIGHT("Light Prism"),
    SYSTEM("System")
}

enum class ImageAspectOption(val label: String) {
    SQUARE_1_1("1:1"),
    LANDSCAPE_16_9("16:9"),
    PORTRAIT_9_16("9:16"),
    CLASSIC_4_3("4:3")
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Conversation",
    val mode: String = AiMode.CHAT.name,
    val provider: String = AiProvider.GEMINI.name,
    val lastMessagePreview: String = "",
    val pinned: Boolean = false,
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val aiMode: AiMode get() = AiMode.fromString(mode)
    val aiProvider: AiProvider get() = AiProvider.fromString(provider)
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "USER",
    val content: String = "",
    val provider: String = AiProvider.GEMINI.name,
    val mode: String = AiMode.CHAT.name,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val isFromUser: Boolean get() = role == "USER"
    val aiProvider: AiProvider get() = AiProvider.fromString(provider)
    val aiMode: AiMode get() = AiMode.fromString(mode)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
