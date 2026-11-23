package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.data.model.AiMode
import com.example.data.model.AiProvider
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiModelOption
import com.example.data.model.ImageAspectOption
import com.example.data.model.OpenAiModelOption
import com.example.data.remote.OpenAiChatMessage
import com.example.data.remote.OpenAiChatRequest
import com.example.data.remote.OpenAiClient
import com.example.data.remote.OpenAiImageRequest
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

interface AiChatBackend {
    fun streamReply(
        prompt: String,
        history: List<ChatMessage>,
        mode: AiMode,
        provider: AiProvider,
        geminiModel: GeminiModelOption,
        openAiModel: OpenAiModelOption,
        attachmentBitmap: Bitmap? = null,
        sourceLang: String = "Auto-detect",
        targetLang: String = "Spanish",
        writingTone: String = "Professional",
        studyFormat: String = "Clear Explanation"
    ): Flow<String>
}

class AiRepository : AiChatBackend {

    fun systemInstructionFor(
        mode: AiMode,
        sourceLang: String = "Auto-detect",
        targetLang: String = "Spanish",
        writingTone: String = "Professional",
        studyFormat: String = "Clear Explanation"
    ): String {
        return when (mode) {
            AiMode.CHAT -> "You are XPRO AI, a futuristic, hyper-intelligent, concise, and helpful personal AI assistant. Provide clear, well-structured answers."
            AiMode.VOICE -> "You are XPRO AI in live Voice Assistant mode. Keep answers conversational, crisp, natural to listen to aloud, and free of complex markdown tables or clutter."
            AiMode.CODING -> "You are XPRO AI Code Copilot, an elite staff software engineer. Provide production-grade code in clean fenced markdown blocks (e.g. ```kotlin), explain architecture concisely, and highlight edge cases or performance wins."
            AiMode.TRANSLATOR -> "You are XPRO AI Neural Translator. Translate the user's text from $sourceLang into $targetLang in a $writingTone tone. Output the clean translation first, followed by brief pronunciation or cultural nuance notes if helpful."
            AiMode.WRITING -> "You are XPRO AI Writing Pro. Craft or refine text in a $writingTone tone with impeccable clarity, flow, and impact. Use clean structure and offer sharp, publication-ready prose."
            AiMode.MATH -> "You are XPRO AI Math & STEM Solver. Solve problems step-by-step, clearly stating formulas, intermediate algebraic/calculus steps, and the final verified answer. If an image is provided, read the problem carefully from the image first."
            AiMode.STUDY -> "You are XPRO AI Study Lab. Teach using the '$studyFormat' format. Make concepts memorable, accurate, and interactive—for quizzes include numbered questions and an answer key at the bottom; for flashcards provide clear Front / Back pairs."
        }
    }

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
        val systemPrompt = systemInstructionFor(
            mode = mode,
            sourceLang = sourceLang,
            targetLang = targetLang,
            writingTone = writingTone,
            studyFormat = studyFormat
        )
        val sanitizedHistory = sanitizeChatHistory(history, maxTurns = 14)

        if (provider == AiProvider.OPENAI) {
            if (!OpenAiClient.isConfigured()) {
                throw IllegalStateException(
                    "OpenAI API key is not configured. Add OPENAI_API_KEY in the AI Studio Secrets panel, or switch provider to Google Gemini (no key required)."
                )
            }
            val openAiMessages = buildList {
                add(OpenAiChatMessage(role = "system", content = systemPrompt))
                sanitizedHistory.takeLast(12).forEach { msg ->
                    add(
                        OpenAiChatMessage(
                            role = if (msg.isFromUser) "user" else "assistant",
                            content = msg.content
                        )
                    )
                }
                val userPrompt = if (attachmentBitmap != null) {
                    "$prompt\n[Note: Image attachment was provided; switch to Gemini for multimodal vision analysis.]"
                } else prompt
                add(OpenAiChatMessage(role = "user", content = userPrompt))
            }

            val response = withTimeout(60_000L) {
                OpenAiClient.api.createChatCompletion(
                    authorization = OpenAiClient.bearerHeader(),
                    request = OpenAiChatRequest(
                        model = openAiModel.modelId,
                        messages = openAiMessages
                    )
                )
            }
            val text = response.choices.firstOrNull()?.message?.content
            if (text.isNullOrBlank()) {
                throw IllegalStateException("OpenAI returned an empty response.")
            }
            val chunkSize = (text.length / 40).coerceAtLeast(16)
            var index = 0
            while (index < text.length) {
                var end = (index + chunkSize).coerceAtMost(text.length)
                if (end < text.length) {
                    val nextSpace = text.indexOf(' ', end)
                    if (nextSpace in end..(end + 10)) {
                        end = nextSpace + 1
                    }
                }
                emit(text.substring(0, end))
                index = end
                if (index < text.length) {
                    delay(14L)
                }
            }
            return@flow
        }

        // Google Gemini via Firebase AI Logic (no API key needed; App Check attested)
        val preferredModelId = if (mode == AiMode.MATH && geminiModel == GeminiModelOption.FLASH_LITE_3_5) {
            GeminiModelOption.FLASH_3_8.modelId
        } else {
            geminiModel.modelId
        }

        val emitted = try {
            streamFromGeminiModel(
                modelName = preferredModelId,
                systemPrompt = systemPrompt,
                prompt = prompt,
                sanitizedHistory = sanitizedHistory,
                attachmentBitmap = attachmentBitmap,
                emitChunk = { emit(it) }
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // If Gemini 4 Argon (which requires Firebase Blaze billing) fails due to billing/quota/model availability,
            // automatically fall back to Gemini 3.8 Flash (no billing required).
            if (preferredModelId == GeminiModelOption.ARGON_4.modelId && shouldFallbackToDefaultModel(e)) {
                streamFromGeminiModel(
                    modelName = GeminiModelOption.FLASH_3_8.modelId,
                    systemPrompt = systemPrompt,
                    prompt = prompt,
                    sanitizedHistory = sanitizedHistory,
                    attachmentBitmap = attachmentBitmap,
                    emitChunk = { emit(it) }
                )
            } else {
                throw IllegalStateException(formatReadableAiError(e), e)
            }
        }

        if (emitted.isBlank()) {
            throw IllegalStateException("Gemini returned an empty response. Please try rephrasing your prompt.")
        }
    }

    private suspend fun streamFromGeminiModel(
        modelName: String,
        systemPrompt: String,
        prompt: String,
        sanitizedHistory: List<ChatMessage>,
        attachmentBitmap: Bitmap?,
        emitChunk: suspend (String) -> Unit
    ): String {
        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            systemInstruction = content { text(systemPrompt) }
        )

        val accumulated = StringBuilder()
        var blockReason: String? = null

        if (attachmentBitmap != null) {
            val multimodalContent = content {
                image(attachmentBitmap)
                text(prompt.ifBlank { "Analyze this image and help solve or explain it." })
            }
            withTimeout(60_000L) {
                model.generateContentStream(multimodalContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emitChunk(accumulated.toString())
                    } else if (blockReason == null) {
                        blockReason = chunk.candidates.firstOrNull()?.finishReason?.name
                    }
                }
            }
        } else if (sanitizedHistory.isEmpty()) {
            withTimeout(60_000L) {
                model.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emitChunk(accumulated.toString())
                    } else if (blockReason == null) {
                        blockReason = chunk.candidates.firstOrNull()?.finishReason?.name
                    }
                }
            }
        } else {
            val chatHistory = sanitizedHistory.map { msg ->
                content(role = if (msg.isFromUser) "user" else "model") {
                    text(msg.content)
                }
            }
            try {
                val chat = model.startChat(history = chatHistory)
                withTimeout(60_000L) {
                    chat.sendMessageStream(prompt).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emitChunk(accumulated.toString())
                        } else if (blockReason == null) {
                            blockReason = chunk.candidates.firstOrNull()?.finishReason?.name
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // If startChat fails before streaming any tokens due to history/role constraints,
                // retry as a stateless generateContentStream call with inline conversation context.
                if (accumulated.isEmpty() && !isNetworkOrAuthFailure(e)) {
                    val contextualPrompt = buildContextualPrompt(sanitizedHistory, prompt)
                    withTimeout(60_000L) {
                        model.generateContentStream(contextualPrompt).collect { chunk ->
                            val piece = chunk.text.orEmpty()
                            if (piece.isNotEmpty()) {
                                accumulated.append(piece)
                                emitChunk(accumulated.toString())
                            } else if (blockReason == null) {
                                blockReason = chunk.candidates.firstOrNull()?.finishReason?.name
                            }
                        }
                    }
                } else {
                    throw e
                }
            }
        }

        if (accumulated.isBlank() && !blockReason.isNullOrBlank() && blockReason != "STOP") {
            throw IllegalStateException("Response stopped ($blockReason). Please adjust your prompt and try again.")
        }

        return accumulated.toString()
    }

    companion object {
        /**
         * Ensures chat history sent to Gemini's startChat API is non-blank, strictly alternates
         * between "user" and "model", starts with "user", and ends with "model" (since the
         * new user prompt is about to be appended by sendMessageStream).
         */
        fun sanitizeChatHistory(
            history: List<ChatMessage>,
            maxTurns: Int = 14
        ): List<ChatMessage> {
            val collapsed = mutableListOf<ChatMessage>()
            for (msg in history) {
                val trimmed = msg.content.trim()
                if (trimmed.isEmpty()) continue
                val cleanMsg = if (trimmed == msg.content) msg else msg.copy(content = trimmed)
                val last = collapsed.lastOrNull()
                if (last != null && last.isFromUser == cleanMsg.isFromUser) {
                    collapsed[collapsed.lastIndex] = last.copy(
                        content = "${last.content}\n\n${cleanMsg.content}"
                    )
                } else {
                    collapsed.add(cleanMsg)
                }
            }

            while (collapsed.isNotEmpty() && !collapsed.first().isFromUser) {
                collapsed.removeAt(0)
            }
            while (collapsed.isNotEmpty() && collapsed.last().isFromUser) {
                collapsed.removeAt(collapsed.lastIndex)
            }

            if (collapsed.isEmpty() || maxTurns <= 1) {
                return emptyList()
            }

            val cappedMax = if (maxTurns % 2 == 0) maxTurns else maxTurns - 1
            val sliced = if (collapsed.size > cappedMax) {
                collapsed.takeLast(cappedMax)
            } else {
                collapsed
            }

            val mutableSlice = sliced.toMutableList()
            while (mutableSlice.isNotEmpty() && !mutableSlice.first().isFromUser) {
                mutableSlice.removeAt(0)
            }
            while (mutableSlice.isNotEmpty() && mutableSlice.last().isFromUser) {
                mutableSlice.removeAt(mutableSlice.lastIndex)
            }
            return mutableSlice
        }

        internal fun buildContextualPrompt(history: List<ChatMessage>, currentPrompt: String): String {
            if (history.isEmpty()) return currentPrompt
            val transcript = history.takeLast(6).joinToString("\n") { msg ->
                val speaker = if (msg.isFromUser) "User" else "Assistant"
                "$speaker: ${msg.content.take(1000)}"
            }
            return "Conversation context:\n$transcript\n\nLatest user message:\n$currentPrompt"
        }

        private fun isNetworkOrAuthFailure(error: Throwable): Boolean {
            val msg = generateSequence(error) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" ")
                .lowercase()
            val hasNetworkCause = generateSequence(error) { it.cause }.any {
                it is java.net.UnknownHostException || it is java.net.ConnectException
            }
            return hasNetworkCause || msg.contains("403") || msg.contains("app check") || msg.contains("attestation")
        }

        private fun shouldFallbackToDefaultModel(error: Throwable): Boolean {
            if (isNetworkOrAuthFailure(error)) return false
            val msg = generateSequence(error) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" ")
                .lowercase()
            return msg.contains("billing") ||
                msg.contains("quota") ||
                msg.contains("not found") ||
                msg.contains("unsupported") ||
                msg.contains("permission") ||
                msg.contains("404") ||
                msg.contains("429")
        }

        internal fun formatReadableAiError(error: Throwable): String {
            val causes = generateSequence(error) { it.cause }.toList()
            val fullMsg = causes.mapNotNull { it.message }.joinToString(" | ")
            val lower = fullMsg.lowercase()

            return when {
                causes.any { it is java.net.UnknownHostException || it is java.net.ConnectException } ||
                    lower.contains("unable to resolve host") ||
                    lower.contains("failed to connect") ->
                    "Unable to reach AI servers. Please check your internet connection and tap Retry."

                lower.contains("403") || lower.contains("app check") || lower.contains("attestation") ->
                    "Firebase App Check verification failed (HTTP 403). Please restart the app or check network connectivity."

                causes.any { it is com.google.firebase.ai.type.QuotaExceededException } ||
                    lower.contains("quota") || lower.contains("429") ->
                    "AI model quota reached. Try switching to Gemini 3.5 Flash-Lite or retry shortly."

                fullMsg.isNotBlank() -> fullMsg
                else -> "AI service could not generate a reply. Please tap Retry."
            }
        }
    }

    suspend fun generateImage(
        prompt: String,
        aspectOption: ImageAspectOption,
        provider: AiProvider,
        referenceBitmap: Bitmap? = null
    ): Bitmap {
        if (provider == AiProvider.OPENAI) {
            if (!OpenAiClient.isConfigured()) {
                throw IllegalStateException(
                    "OpenAI API key is not configured. Add OPENAI_API_KEY in the AI Studio Secrets panel, or use Google Gemini Nano Banana 2.1 (no API key required)."
                )
            }
            val size = when (aspectOption) {
                ImageAspectOption.SQUARE_1_1 -> "1024x1024"
                ImageAspectOption.LANDSCAPE_16_9, ImageAspectOption.CLASSIC_4_3 -> "1792x1024"
                ImageAspectOption.PORTRAIT_9_16 -> "1024x1792"
            }
            val response = withTimeout(60_000L) {
                OpenAiClient.api.generateImage(
                    authorization = OpenAiClient.bearerHeader(),
                    request = OpenAiImageRequest(
                        model = "dall-e-3",
                        prompt = prompt,
                        size = size,
                        responseFormat = "b64_json"
                    )
                )
            }
            val b64 = response.data.firstOrNull()?.b64Json
                ?: throw IllegalStateException("DALL-E 3 returned no image data.")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("Failed to decode generated OpenAI image.")
        }

        // Google Gemini Image Generation via Firebase AI Logic ("gemini-nano-banana-2.1")
        val mappedAspect = when (aspectOption) {
            ImageAspectOption.SQUARE_1_1 -> AspectRatio.SQUARE_1x1
            ImageAspectOption.LANDSCAPE_16_9 -> AspectRatio.LANDSCAPE_16x9
            ImageAspectOption.PORTRAIT_9_16 -> AspectRatio.PORTRAIT_9x16
            ImageAspectOption.CLASSIC_4_3 -> AspectRatio.LANDSCAPE_4x3
        }

        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(mappedAspect)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }

        val imageModel = Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )

        val response = withTimeout(60_000L) {
            if (referenceBitmap != null) {
                imageModel.generateContent(
                    content {
                        image(referenceBitmap)
                        text(prompt)
                    }
                )
            } else {
                imageModel.generateContent(prompt)
            }
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        return imagePart?.image
            ?: throw IllegalStateException("No image was returned by Gemini Nano Banana 2.1.")
    }
}
