package com.example.util

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

object VoiceInputHelper {
    private val LANGUAGE_TAGS = mapOf(
        "English" to "en-US",
        "Spanish" to "es-ES",
        "French" to "fr-FR",
        "German" to "de-DE",
        "Japanese" to "ja-JP",
        "Chinese" to "zh-CN",
        "Arabic" to "ar-SA",
        "Hindi" to "hi-IN",
        "Korean" to "ko-KR",
        "Portuguese" to "pt-BR",
        "Italian" to "it-IT"
    )

    fun mergeVoiceTranscript(baseText: String, spokenText: String): String {
        val cleanSpoken = spokenText.trim()
        if (cleanSpoken.isEmpty()) return baseText
        val cleanBase = baseText.trimEnd()
        if (cleanBase.isEmpty()) return cleanSpoken
        return "$cleanBase $cleanSpoken"
    }

    fun languageTagForSpeech(
        sourceLang: String,
        fallbackLocale: Locale = Locale.getDefault()
    ): String {
        if (sourceLang.equals("Auto-detect", ignoreCase = true) || sourceLang.isBlank()) {
            return fallbackLocale.toLanguageTag()
        }
        return LANGUAGE_TAGS[sourceLang] ?: fallbackLocale.toLanguageTag()
    }

    fun normalizeRmsDb(rmsdB: Float): Float {
        // Android SpeechRecognizer typically reports -2f dB (silence) to ~10f dB (loud speech).
        return ((rmsdB + 2f) / 12f).coerceIn(0.15f, 1f)
    }

    fun readableSpeechError(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the mic and try again."
            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Check permissions and try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice input."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network unavailable for speech recognition."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Wait a moment and retry."
            SpeechRecognizer.ERROR_CLIENT -> "Voice recognition cancelled."
            else -> "Voice input stopped. Tap mic to try again."
        }
    }

    fun buildRecognizeSpeechIntent(
        languageTag: String,
        promptHint: String = "Speak your query to XPRO AI"
    ): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptHint)
        }
    }
}
