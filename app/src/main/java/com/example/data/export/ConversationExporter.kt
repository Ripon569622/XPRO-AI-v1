package com.example.data.export

import android.content.Context
import android.net.Uri
import com.example.data.model.AiMode
import com.example.data.model.AiProvider
import com.example.data.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConversationExporter {

    fun buildSuggestedFilename(title: String, mode: AiMode): String {
        val sanitizedTitle = title
            .trim()
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
            .replace(Regex("\\s+"), "_")
            .take(36)
            .ifBlank { mode.name.lowercase() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "XPRO_AI_${sanitizedTitle}_$stamp.txt"
    }

    fun formatAsPlainText(
        title: String,
        mode: AiMode,
        provider: AiProvider,
        messages: List<ChatMessage>
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val exportedAt = dateFormat.format(Date())

        return buildString {
            appendLine("====================================================")
            appendLine("XPRO AI — Conversation Export")
            appendLine("====================================================")
            appendLine("Title:    $title")
            appendLine("Mode:     ${mode.displayName}")
            appendLine("Provider: ${provider.displayName}")
            appendLine("Exported: $exportedAt")
            appendLine("Messages: ${messages.size}")
            appendLine("====================================================")
            appendLine()

            if (messages.isEmpty()) {
                appendLine("(No messages in this conversation)")
            } else {
                messages.forEachIndexed { index, msg ->
                    val speaker = if (msg.isFromUser) "USER" else "XPRO AI (${msg.aiProvider.displayName})"
                    val timeLabel = msg.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Pending"
                    appendLine("[${index + 1}] $speaker • $timeLabel")
                    appendLine("----------------------------------------------------")
                    appendLine(msg.content.trim())
                    appendLine()
                }
            }
        }
    }

    fun writeTextToUri(context: Context, uri: Uri, content: String): Result<Unit> {
        return try {
            val output = context.contentResolver.openOutputStream(uri)
                ?: return Result.failure(IllegalStateException("Unable to open destination file stream."))
            output.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
