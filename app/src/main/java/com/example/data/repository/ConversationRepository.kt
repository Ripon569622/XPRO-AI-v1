package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AiMode
import com.example.data.model.AiProvider
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

interface ChatStorageBackend {
    fun newConversationId(): String
    fun newMessageId(conversationId: String): String
    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>>
    suspend fun createConversationWithId(
        conversationId: String,
        title: String,
        mode: AiMode,
        provider: AiProvider,
        initialPreview: String = ""
    ): Result<String>
    suspend fun appendMessage(
        conversationId: String,
        role: String,
        content: String,
        provider: AiProvider,
        mode: AiMode,
        newTitleIfFirst: String? = null,
        currentMessageCount: Long = 0L,
        messageId: String? = null
    ): Result<Unit>
}

class ConversationRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : ChatStorageBackend {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    override fun newConversationId(): String {
        val uid = auth.currentUser?.uid
        return if (!uid.isNullOrBlank()) {
            db.collection("users").document(uid).collection("conversations").document().id
        } else {
            UUID.randomUUID().toString()
        }
    }

    override fun newMessageId(conversationId: String): String {
        val uid = auth.currentUser?.uid
        return if (!uid.isNullOrBlank() && conversationId.isNotBlank()) {
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages").document().id
        } else {
            UUID.randomUUID().toString()
        }
    }

    suspend fun syncUserProfile(user: FirebaseUser): Result<Unit> {
        val docPath = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val safeName = (user.displayName?.takeIf { it.isNotBlank() } ?: "XPRO User").take(100)
            val safeEmail = user.email?.takeIf { it.isNotBlank() }?.take(200)
            val safePhoto = user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.take(500)

            if (!snapshot.exists()) {
                val createMap = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createMap).await()
            } else {
                val updateMap = mapOf(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val path = "users/$userId/conversations"
        emitAll(
            db.collection("users").document(userId).collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<Conversation> { it.pinned }
                            .thenByDescending { it.updatedAt?.seconds ?: 0L }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$userId/conversations/$conversationId/messages"
        emitAll(
            db.collection("users").document(userId)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getMessagesOnce(conversationId: String): Result<List<ChatMessage>> {
        val path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
            val snapshot = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .await()
            val list = snapshot.toObjects(
                ChatMessage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun createConversation(
        title: String,
        mode: AiMode,
        provider: AiProvider,
        initialPreview: String = ""
    ): Result<String> {
        return createConversationWithId(
            conversationId = newConversationId(),
            title = title,
            mode = mode,
            provider = provider,
            initialPreview = initialPreview
        )
    }

    override suspend fun createConversationWithId(
        conversationId: String,
        title: String,
        mode: AiMode,
        provider: AiProvider,
        initialPreview: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
            val safeTitle = title.trim().ifBlank { "${mode.displayName} Session" }.take(120)
            val safePreview = initialPreview.trim().take(300)
            val docRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "mode" to mode.name,
                "provider" to provider.name,
                "lastMessagePreview" to safePreview,
                "pinned" to false,
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
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
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
            val safeContent = content.trim().ifBlank { "(empty)" }.take(32000)
            val convRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
            val msgRef = if (!messageId.isNullOrBlank()) {
                convRef.collection("messages").document(messageId)
            } else {
                convRef.collection("messages").document()
            }
            docPath = msgRef.path

            val msgPayload = mapOf(
                "userId" to uid,
                "role" to if (role == "USER") "USER" else "ASSISTANT",
                "content" to safeContent,
                "provider" to provider.name,
                "mode" to mode.name,
                "createdAt" to FieldValue.serverTimestamp()
            )
            msgRef.set(msgPayload).await()

            val convSnap = try {
                convRef.get().await()
            } catch (_: Exception) {
                runCatching { convRef.get(Source.CACHE).await() }.getOrNull()
            }
            val preview = safeContent.replace('\n', ' ').take(280)
            if (convSnap != null && !convSnap.exists()) {
                val fallbackTitle = (newTitleIfFirst?.trim()?.takeIf { it.isNotBlank() } ?: "${mode.displayName} Session").take(120)
                convRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to fallbackTitle,
                        "mode" to mode.name,
                        "provider" to provider.name,
                        "lastMessagePreview" to preview,
                        "pinned" to false,
                        "messageCount" to (currentMessageCount + 1L).coerceAtLeast(1L).coerceAtMost(10000L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val existingTitle = convSnap?.getString("title") ?: "${mode.displayName} Session"
                val updatedTitle = (newTitleIfFirst?.trim()?.takeIf { it.isNotBlank() } ?: existingTitle).take(120)
                val updatedCount = ((convSnap?.getLong("messageCount") ?: currentMessageCount) + 1L).coerceAtMost(10000L)

                convRef.update(
                    mapOf(
                        "lastMessagePreview" to preview,
                        "messageCount" to updatedCount,
                        "provider" to provider.name,
                        "mode" to mode.name,
                        "title" to updatedTitle,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateConversationMeta(
        conversationId: String,
        title: String,
        pinned: Boolean
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
            val safeTitle = title.trim().ifBlank { "Untitled Session" }.take(120)
            val docRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to safeTitle,
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
            val convRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
            docPath = convRef.path

            val messagesSnap = convRef.collection("messages").get().await()
            messagesSnap.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
