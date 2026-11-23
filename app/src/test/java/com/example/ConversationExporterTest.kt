package com.example

import com.example.data.export.ConversationExporter
import com.example.data.model.AiMode
import com.example.data.model.AiProvider
import com.example.data.model.ChatMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationExporterTest {

    @Test
    fun `suggested filename sanitizes special characters and ends with txt`() {
        val name = ConversationExporter.buildSuggestedFilename("Kotlin: Coroutines & Flow?!", AiMode.CODING)
        assertTrue(name.startsWith("XPRO_AI_Kotlin_Coroutines_Flow_"))
        assertTrue(name.endsWith(".txt"))
    }

    @Test
    fun `formatAsPlainText includes header and ordered messages`() {
        val messages = listOf(
            ChatMessage(id = "1", role = "USER", content = "What is Compose?", provider = AiProvider.GEMINI.name),
            ChatMessage(id = "2", role = "ASSISTANT", content = "Jetpack Compose is Android's modern declarative UI toolkit.", provider = AiProvider.GEMINI.name)
        )
        val output = ConversationExporter.formatAsPlainText(
            title = "Compose Intro",
            mode = AiMode.CHAT,
            provider = AiProvider.GEMINI,
            messages = messages
        )
        assertTrue(output.contains("XPRO AI — Conversation Export"))
        assertTrue(output.contains("Title:    Compose Intro"))
        assertTrue(output.contains("[1] USER"))
        assertTrue(output.contains("What is Compose?"))
        assertTrue(output.contains("[2] XPRO AI (Google Gemini)"))
        assertTrue(output.contains("Jetpack Compose is Android's modern declarative UI toolkit."))
    }
}
