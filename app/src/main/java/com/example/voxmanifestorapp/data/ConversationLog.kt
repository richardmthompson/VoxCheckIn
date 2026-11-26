package com.example.voxmanifestorapp.data

import android.util.Log
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.voxmanifestorapp.ui.agent.ConversationEntry
import com.example.voxmanifestorapp.ui.agent.ConversationPhase
import com.example.voxmanifestorapp.ui.agent.Speaker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Room entity for persistent storage of conversation history.
 * This provides a dual-layer memory system, storing both raw conversations
 * and metadata for structured retrieval.
 */
@Entity(
    tableName = "conversation_logs",
    indices = [
        Index(value = ["sessionId"], name = "index_conversation_logs_sessionId"),
        Index(value = ["wishId"], name = "index_conversation_logs_wishId")
    ]
)
data class ConversationLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Session grouping and sorting
    val sessionId: String,          // UUID for grouping conversation turns into sessions
    val timestamp: Long,            // For sorting and recency tracking

    // Content metadata
    val wishId: Int,                // Associated wish (-1 if general/no specific wish)
    val phase: String,              // The conversation phase (maps to ConversationPhase)
    val speaker: String,            // "Agent" or "User"
    val content: String,            // The actual text

    // Visibility control
    val isVisible: Boolean,         // Whether to show in UI history

    // NEW: Metadata storage for themes and other structured data
    val metadata: Map<String, JsonElement>? = null    // Structured metadata using JsonElement
)

/**
 * Converts a ConversationLogEntry to a less detailed ConversationEntry
 * for in-memory representation in the UI.
 * Metadata is now handled by the TypeConverter, so this function is simplified.
 */
fun ConversationLogEntry.toConversationEntry(): ConversationEntry {
    val conversationPhase = try {
        ConversationPhase.valueOf(phase)
    } catch (e: IllegalArgumentException) {
        ConversationPhase.CHECK_IN // Fallback to a default phase
    }

    // Convert JsonElement metadata to String metadata for ConversationEntry
    val stringMetadata = metadata?.mapValues { (_, jsonElement) ->
        when (jsonElement) {
            is JsonPrimitive -> jsonElement.content
            else -> jsonElement.toString() // For JsonArray, JsonObject, etc.
        }
    } ?: emptyMap()

    return ConversationEntry(
        speaker = when (speaker) {
            "Agent" -> Speaker.Agent
            "User" -> Speaker.User
            "SessionName" -> Speaker.SessionName
            else -> Speaker.User  // Fallback
        },
        content = content,
        timestamp = timestamp,
        phase = conversationPhase,
        isVisible = isVisible,
        metadata = stringMetadata
    )
}

/**
 * Simple data class to hold metadata about a conversation session.
 * Used for the conversation history session cards.
 */
data class SessionMetadata(
    val sessionId: String,
    val startTimestamp: Long
) 