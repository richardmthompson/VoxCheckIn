package com.example.voxmanifestorapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ConversationLogEntry operations.
 * Handles persistent storage and retrieval of conversation history.
 */
@Dao
interface ConversationLogDao {
    /**
     * Inserts a new conversation log entry.
     */
    @Insert
    suspend fun insertLogEntry(entry: ConversationLogEntry): Long
    
    /**
     * Retrieves all entries for a specific session.
     */
    @Query("SELECT * FROM conversation_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getSessionEntries(sessionId: String): Flow<List<ConversationLogEntry>>
    
    /**
     * Retrieves the most recent entries for a specific wish.
     */
    @Query("SELECT * FROM conversation_logs WHERE wishId = :wishId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEntriesForWish(wishId: Int, limit: Int = 50): List<ConversationLogEntry>
    
    /**
     * Retrieves conversations across a date range.
     * Useful for summarizing recent activity.
     */
    @Query("SELECT * FROM conversation_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getEntriesBetweenDates(startTime: Long, endTime: Long): List<ConversationLogEntry>
    
    /**
     * Retrieves the most recent session for a specific wish.
     */
    @Query("""
        SELECT DISTINCT sessionId FROM conversation_logs 
        WHERE wishId = :wishId 
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun getMostRecentSessionForWish(wishId: Int): String?
    
    /**
     * Retrieves all entries from the most recent session for a specific wish.
     */
    @Query("""
        SELECT * FROM conversation_logs
        WHERE sessionId = (
            SELECT sessionId FROM conversation_logs 
            WHERE wishId = :wishId 
            ORDER BY timestamp DESC 
            LIMIT 1
        )
        ORDER BY timestamp ASC
    """)
    suspend fun getEntriesFromMostRecentWishSession(wishId: Int): List<ConversationLogEntry>
    
    /**
     * Retrieves the most recent Initial Assessment entries.
     */
    @Query("""
        SELECT * FROM conversation_logs
        WHERE phase = 'INITIAL_ASSESSMENT'
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentInitialAssessmentEntries(limit: Int = 20): List<ConversationLogEntry>
    
    /**
     * Gets a count of sessions for a specific wish.
     */
    @Query("SELECT COUNT(DISTINCT sessionId) FROM conversation_logs WHERE wishId = :wishId")
    suspend fun getSessionCountForWish(wishId: Int): Int
    
    /**
     * Retrieves the maximum session ID value to support sequential session IDs.
     * Returns null if no session exists yet.
     */
    @Query("SELECT MAX(CAST(sessionId AS INTEGER)) FROM conversation_logs")
    suspend fun getMaxSessionId(): Int?
    
    /**
     * Gets all distinct session IDs with their start timestamps.
     * This is used for the conversation history overview.
     */
    @Query("""
        SELECT sessionId, MIN(timestamp) as startTimestamp
        FROM conversation_logs
        GROUP BY sessionId
        ORDER BY startTimestamp DESC
    """)
    suspend fun getAllSessionsWithStartTime(): List<SessionMetadata>
    
    /**
     * Updates the metadata for all entries in a session.
     * Used to add session names and other session-level metadata.
     */
    @Query("UPDATE conversation_logs SET metadata = :metadata WHERE sessionId = :sessionId")
    suspend fun updateSessionMetadata(sessionId: String, metadata: String)
    
    /**
     * Gets the count of entries for a specific session.
     */
    @Query("SELECT COUNT(*) FROM conversation_logs WHERE sessionId = :sessionId")
    suspend fun getEntryCountForSession(sessionId: String): Int
    
    /**
     * Gets the total count of distinct sessions in the database.
     * Used to determine if the user is a first-time or returning user.
     */
    @Query("SELECT COUNT(DISTINCT sessionId) FROM conversation_logs")
    suspend fun getSessionCount(): Int

    /**
     * Flow-based version that emits whenever the session count changes.
     * Automatically updates when a new session gets its first entry.
     * Used to reactively update the session list in the UI.
     */
    @Query("SELECT COUNT(DISTINCT sessionId) FROM conversation_logs")
    fun getSessionCountFlow(): Flow<Int>

    /**
     * Gets the session name for a specific session without loading metadata.
     * This avoids triggering TypeConverter on all session entries.
     * Only selects the content field from SessionName entries.
     */
    @Query("SELECT content FROM conversation_logs WHERE sessionId = :sessionId AND speaker = 'SessionName' LIMIT 1")
    suspend fun getSessionNameOnly(sessionId: String): String?

    /**
     * Gets the theme summary content for a specific session.
     * Searches for entries with messageType = "themeSummary" in metadata.
     * Note: This requires the TypeConverter to parse metadata, but should be rare usage.
     */
    @Query("SELECT content FROM conversation_logs WHERE sessionId = :sessionId AND speaker = 'Agent' AND isVisible = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getThemeSummaryForSession(sessionId: String): String?
} 