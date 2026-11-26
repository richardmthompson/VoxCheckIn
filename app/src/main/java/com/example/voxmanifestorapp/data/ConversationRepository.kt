package com.example.voxmanifestorapp.data

import android.util.Log
import com.example.voxmanifestorapp.ui.agent.ConversationEntry
import com.example.voxmanifestorapp.ui.agent.ConversationPhase
import com.example.voxmanifestorapp.ui.agent.Speaker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Data class containing all information needed to display a session card in the UI */
data class SessionInfo(
    val sessionId: String,
    val startTimestamp: Long,
    val sessionName: String?, // ← NEW: Session name for display
    val entryCount: Int,
    val dateTimeFormatted: String,
    val isCurrentSession: Boolean
) {
    companion object {
        private val dateFormatter =
            SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())

        fun fromMetadata(
            metadata: SessionMetadata,
            entryCount: Int,
            currentSessionId: String,
            sessionName: String? = null // ← NEW: Session name passed separately
        ): SessionInfo {
            val date = Date(metadata.startTimestamp)
            return SessionInfo(
                sessionId = metadata.sessionId,
                startTimestamp = metadata.startTimestamp,
                sessionName = sessionName, // ← Use passed session name
                entryCount = entryCount,
                dateTimeFormatted = dateFormatter.format(date),
                isCurrentSession = metadata.sessionId == currentSessionId
            )
        }
    }
}

/**
 * Repository for managing conversation logs. Provides an abstraction layer over the Room DAO for
 * conversation log operations.
 */
class ConversationRepository(private val conversationLogDao: ConversationLogDao) {


    private val TAG = "Conversation"

    // Mutex lock timeout for deadlock prevention
    private val MUTEX_TIMEOUT_MS = 5000L

    // Flag to track if we're in initialization process
    private var isInitializing = false

    /** Checks if a session has been initialized. */
    fun isSessionInitialized(currentSessionId: String): Boolean = currentSessionId != "-1"

    /**
     * Logs a new conversation entry to persistent storage. Now includes metadata support for theme
     * persistence.
     */
    suspend fun logConversation(
        currentSessionId: String,
        speaker: Speaker,
        content: String,
        wishId: Int = -1,
        phase: ConversationPhase = ConversationPhase.CHECK_IN,
        isVisible: Boolean = true,
        metadata: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap() // ← UPDATED PARAMETER TYPE
    ): Long {
        // Make sure we're initialized before logging
        if (!isSessionInitialized(currentSessionId)) {
            Log.d(TAG, "Session not initialized, initializing before logging conversation")
            initializeSessionCounterSimple()
        }

        val entry =
            ConversationLogEntry(
                sessionId = currentSessionId,
                timestamp = System.currentTimeMillis(),
                wishId = wishId,
                phase = phase.name,
                speaker = speaker.name,
                content = content,
                isVisible = isVisible,
                metadata = metadata // ← STORE METADATA DIRECTLY - TypeConverter handles serialization
            )

        return try {
            val result = conversationLogDao.insertLogEntry(entry)
            Log.d(TAG, "Successfully logged conversation entry with metadata: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error logging conversation: ${e.message}", e)
            -1L // Return -1 to indicate error
        }
    }

    /**
     * Starts a new conversation session
     */
    suspend fun startNewSession(): String {
        Log.d(TAG, "Starting new session...")

        val currentSessionId = initializeSessionCounterSimple()
        return currentSessionId

    }

    /**
     * Simplified initialization that doesn't use mutex to avoid potential deadlocks. This is a
     * fallback approach for handling initialization.
     */
    private suspend fun initializeSessionCounterSimple(): String {
        try {
            Log.d(TAG, "Running simplified session initialization")
            if (!isInitializing) {
                isInitializing = true

                Log.d(TAG, "Getting max session ID from database")
                val maxId =
                    try {
                        conversationLogDao.getMaxSessionId() ?: 0
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting max session ID: ${e.message}", e)
                        0 // Fallback to 0 if database query fails
                    }

                val newId = maxId + 1
                val currentSessionId = newId.toString()
                Log.d(TAG, "Set session ID to: $currentSessionId")

                isInitializing = false
                return currentSessionId
            } else {
                // return error code because it's already initializing
                return "-1"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Simplified initialization failed: ${e.message}", e)
            // Ensure we always have a valid session ID
            Log.d(TAG, "Using emergency fallback session ID: 0")
            isInitializing = false
            return "0"
        }
    }


    /** Gets all conversation entries for a specific session as a Flow. */
    fun getSessionEntriesFlow(sessionId: String): Flow<List<ConversationEntry>> {
        return conversationLogDao.getSessionEntries(sessionId).map { entries ->
            entries.map { it.toConversationEntry() }
        }
    }

    /** Retrieves recent conversation history for a specific wish. */
    suspend fun getRecentWishHistory(wishId: Int, limit: Int = 50): List<ConversationEntry> {
        return conversationLogDao.getRecentEntriesForWish(wishId, limit).map {
            it.toConversationEntry()
        }
    }

    /**
     * Retrieves history from the most recent session for a specific wish. Useful for continuity
     * between sessions.
     */
    suspend fun getMostRecentWishSessionHistory(wishId: Int): List<ConversationEntry> {
        return conversationLogDao.getEntriesFromMostRecentWishSession(wishId).map {
            it.toConversationEntry()
        }
    }

    /**
     * Retrieves history specific to the INITIAL_ASSESSMENT phase. Crucial for enhancing the Initial
     * Assessment experience.
     */
    suspend fun getRecentInitialAssessmentHistory(limit: Int = 20): List<ConversationEntry> {
        return conversationLogDao.getRecentInitialAssessmentEntries(limit).map {
            it.toConversationEntry()
        }
    }

    /**
     * Retrieves all session metadata with entry counts for the conversation history UI. Returns a
     * list of SessionInfo objects sorted by start time (most recent first).
     */
    suspend fun getAllSessionsInfo(currentSessionId: String): List<SessionInfo> {
        val metadata = conversationLogDao.getAllSessionsWithStartTime()

        return metadata.map { sessionMetadata ->
            val entryCount = conversationLogDao.getEntryCountForSession(sessionMetadata.sessionId)
            val sessionName = getSessionNameFromMetadata(sessionMetadata.sessionId)
            // Log.d(TAG, "Session ${sessionMetadata.sessionId}: entryCount=$entryCount,
            // sessionName='$sessionName'")
            SessionInfo.fromMetadata(sessionMetadata, entryCount, currentSessionId, sessionName)
        }
    }

    /**
     * Gets the total count of conversation sessions in the database. Used to determine if this is a
     * first-time or returning user.
     *
     * @return The number of distinct sessions in the database
     */
    suspend fun getSessionCount(): Int {
        return try {
            conversationLogDao.getSessionCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting session count: ${e.message}", e)
            0 // Return 0 on error to indicate no previous sessions
        }
    }

    /**
     * Flow that emits the session count whenever it changes in the database.
     * Automatically updates when a new session gets its first entry.
     * Used to reactively trigger session list updates in the UI.
     *
     * @return Flow emitting the current count of distinct sessions
     */
    fun getSessionCountFlow(): Flow<Int> {
        return conversationLogDao.getSessionCountFlow()
    }

    /**
     * Updates the session name for a specific session by creating a special SessionName entry. Called
     * when themes are synthesized and a session name is generated.
     *
     * @param sessionId The session ID to update
     * @param sessionName The generated session name (should be < 7 words)
     */
    suspend fun updateSessionName(sessionId: String, sessionName: String) {
        try {
            Log.d(
                "ConversationRepository",
                "Creating session name entry for session $sessionId: '$sessionName'"
            )

            // Create a special SessionName entry with the session name as content
            val sessionNameEntry =
                ConversationLogEntry(
                    sessionId = sessionId,
                    timestamp = System.currentTimeMillis(),
                    wishId = -1, // No specific wish for session-level info
                    phase = "SESSION_METADATA",
                    speaker = "SessionName",
                    content = sessionName,
                    isVisible = false, // Don't show in conversation UI
                    metadata = null // No metadata needed for session name entry
                )

            Log.d("ConversationRepository", "Inserting session name entry: $sessionName")
            conversationLogDao.insertLogEntry(sessionNameEntry)
            Log.d(
                "ConversationRepository",
                "Successfully created session name entry: '$sessionName'"
            )
        } catch (e: Exception) {
            Log.e("ConversationRepository", "Error creating session name entry: ${e.message}", e)
        }
    }

    /** Extracts session name from SessionName entries for a given session using optimized query. */
    private suspend fun getSessionNameFromMetadata(sessionId: String): String? {
        return try {
            // Use optimized query that only loads the session name without triggering TypeConverter
            conversationLogDao.getSessionNameOnly(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting session name for session $sessionId: ${e.message}", e)
            null
        }
    }

    /**
     * Retrieves the theme summary for the current session for playback purposes.
     * Searches for conversation entries with messageType = "themeSummary" metadata.
     */
    suspend fun getThemeSummaryForCurrentSession(currentSessionId: String): String? {
        return try {
            conversationLogDao.getThemeSummaryForSession(currentSessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting theme summary for session $currentSessionId: ${e.message}", e)
            null
        }
    }
}
